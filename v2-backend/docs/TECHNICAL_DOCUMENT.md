# MW Planner - Technical Document

## Document Information

- Version: 1.0
- Status: Active
- Scope: Technical specifications for planned and current behaviour

---

## 1. Management controller

The **Management controller** exposes operational endpoints for syncing master data (countries, states, districts) from an external source into the planner database. These endpoints are intended for admin/scheduled use, not regular application users.

### 1.1 Location and security

- **Class:** `com.mw.planner.controller.config.ManagementController`
- **Base path:** `POST /api/v1/management`
- **Security:** HTTP Basic authentication. Only users with role `SYSTEM_ADMIN` or `GLOBAL_ADMIN` can call these endpoints. Credentials are configured under `mw-planner.management.credentials` (username/password) and loaded into an in-memory `UserDetailsService` in `SecurityConfiguration`. The path `/api/v1/management/**` is included in `AUTH_ADMIN` and requires one of these roles.
- **OpenAPI:** Tag "Management Operations"; `@SecurityRequirement(name = "basicAuth")` so Swagger UI prompts for Basic auth when calling these endpoints.

### 1.2 Endpoints

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/management/sync/countries` | Fetches country data from the external API and synchronizes it with the planner database (create/update). | `200` – `CountrySyncResponseDTO` (syncedCount, updatedCount, createdCount, message) |
| `POST` | `/api/v1/management/sync/states` | Starts an **async** job to fetch state data for all countries and sync to the database. Returns immediately. | `202` – `SyncResponseDTO` (message, type: "STATE") |
| `POST` | `/api/v1/management/sync/districts` | Starts an **async** job to fetch district data for all states and sync to the database. Returns immediately. | `202` – `SyncResponseDTO` (message, type: "DISTRICT") |

### 1.3 Behaviour summary

- **Countries:** Synchronous. Controller calls `CountryService.syncCountriesFromExternalApi()`; the service fetches from the external API (currently MW Master Data API), then upserts into `CountryRepository`.
- **States:** Asynchronous. Controller calls `StateService.syncAllStatesAsync()`; the service runs in a separate thread (e.g. `@Async`), fetches all states from the external API, then bulk-saves to `StateRepository`.
- **Districts:** Asynchronous. Controller calls `DistrictService.syncAllDistrictsAsync()`; the service runs async, iterates over all states, and for each state fetches districts from the external API (with rate limiting, e.g. semaphore), then saves to `DistrictRepository`.

### 1.4 Code references

- **Controller:** `src/main/java/com/mw/planner/controller/config/ManagementController.java`
- **Security:** `src/main/java/com/mw/planner/security/SecurityConfiguration.java` (AUTH_ADMIN, UserDetailsService)
- **Config:** `src/main/resources/application.yaml` → `mw-planner.management.credentials`

---

## 2. Country, State and District sync – replacement with mw-account API

### 2.1 Current implementation

Country, state and district sync is implemented as follows.

- **Data source:** External API referred to in the code and config as **MW Master Data API** (base URL and paths configured under `mw-planner.master-data` in `application.yaml`).
- **Config:** `mw-planner.master-data`:
  - `base-url` (e.g. `https://mw-api-gtwy-pprd.movingwalls.com/master/api/v2/rest`)
  - `endpoints.country` (e.g. `/country`), `endpoints.state` (e.g. `/state`), `endpoints.district` (e.g. `/district`)
  - `defaults.page-size`, `defaults.page-number` for query params
- **Client:** `MwMasterDataService` uses `RestTemplate` and `MwPlannerProperties.getMasterData()` to build full URLs:
  - Countries: `getFullCountryUrl()` → GET with `?page=...&size=...`
  - States: `getFullStateUrl()` → GET with `?size=...`
  - Districts: `getFullDistrictUrl(stateId)` → GET with `?stateIds=...&size=...`
- **DTOs:** `MwCountryDTO`, `MwStateDTO`, `MwDistrictDTO` – structure matches the current Master Data API response (e.g. countryId, name, latitude, longitude, etc.).
- **Services:**
  - **CountryService:** `syncCountriesFromExternalApi()` – calls `mwMasterDataService.fetchCountriesFromMasterDataApi()`, maps to `Country` domain, upserts via `CountryRepository`, returns `CountrySyncResponseDTO`.
  - **StateService:** `syncAllStatesAsync()` – calls `mwMasterDataService.fetchStatesFromMasterDataApi()`, maps to `State`, bulk save via `StateRepository`.
  - **DistrictService:** `syncAllDistrictsAsync()` – for each state, calls `mwMasterDataService.fetchDistrictsFromMasterDataApi(stateId)`, maps to `District`, save via `DistrictRepository` (with virtual threads and semaphore for concurrency/rate limiting).

The **Management controller** (see §1) does not change: it still exposes `POST /api/v1/management/sync/countries`, `sync/states`, `sync/districts` and delegates to these three services. Only the **source of data** (and the client used to fetch it) is to be replaced.

### 2.2 Target: use mw-account API

The requirement is to replace the current Master Data API with the **existing mw-account API** as the source for country, state and district data. The Management controller and the sync endpoints remain; the change is in **which API is called** and **how it is configured**.

### 2.3 How to change the sync to use mw-account

1. **Confirm mw-account API contract**  
   Obtain from mw-account API documentation or code:
   - Base URL (and whether it is the same as `mw-planner.account.base-url` or a different service).
   - Exact paths for:
     - listing/fetching **countries** (e.g. pagination, query params),
     - listing/fetching **states** (e.g. all states or by country),
     - listing/fetching **districts** (e.g. by state ID(s)).
   - Response schema for each (JSON shape and field names).

2. **Introduce an mw-account client**  
   Choose one of:
   - **Option A – New client class:** Create a dedicated client (e.g. `MwAccountLocationApiClient` or `MwAccountMasterDataClient`) that calls the mw-account country/state/district endpoints. Use a dedicated config block (e.g. `mw-planner.account-api` or reuse `mw-planner.account`) for base URL and paths so they can differ from the current master-data config.
   - **Option B – Replace MwMasterDataService:** Change `MwMasterDataService` to call mw-account instead of the current Master Data API: point it at mw-account base URL and paths, and adapt request/response (see below). Config can be moved from `mw-planner.master-data` to something like `mw-planner.account.locations` or a new `mw-planner.account-api`.

   Use the same HTTP client approach as today (e.g. `RestTemplate`) or a shared WebClient if the project standard is WebClient. Handle auth (e.g. service token or API key) as required by mw-account.

3. **Map mw-account responses to planner model**  
   - If mw-account returns DTOs that match (or are close to) `MwCountryDTO`, `MwStateDTO`, `MwDistrictDTO`, use them directly or with thin mappers.
   - If the response shape differs, add small mapper methods or adapter DTOs that map mw-account JSON into the existing DTOs (or into `Country` / `State` / `District` domain entities) so that **CountryService, StateService and DistrictService** keep their current logic (fetch list → map → upsert). No change to repository or domain model is strictly required if the mapping layer produces the same inputs as today.

4. **Wire the new client into the three services**  
   - **CountryService:** Replace (or delegate to) the new client instead of `MwMasterDataService.fetchCountriesFromMasterDataApi()`. Signature can stay “fetch list of country DTOs”; only the implementation (and thus the data source) changes.
   - **StateService:** Same for `fetchStatesFromMasterDataApi()`.
   - **DistrictService:** Same for `fetchDistrictsFromMasterDataApi(stateId)` (ensure mw-account supports fetching by state, or adapt in the client, e.g. by state ID or state code).

5. **Configuration**  
   - Add or reuse config properties for mw-account base URL and country/state/district paths (e.g. under `mw-planner.account` or `mw-planner.account-api`).  
   - Remove or deprecate `mw-planner.master-data` usage for sync once the cutover is done.  
   - Keep environment-specific values (e.g. staging vs production mw-account URL) in the appropriate `application-*.yaml` files.

6. **Testing and rollout**  
   - Unit tests: mock the new client (or the new implementation of `MwMasterDataService`) in `CountryService`, `StateService`, `DistrictService` so sync logic is unchanged from the tests’ perspective; only the data source is swapped.
   - Integration test: if there is a test that hits the real external API, point it at mw-account (or a stub that mimics mw-account’s response) and run the same sync flows.
   - Run `POST /api/v1/management/sync/countries`, then `sync/states`, then `sync/districts` (with admin Basic auth) and verify DB state and counts.

### 2.4 Summary table

| Layer | Current | Change to use mw-account |
|-------|--------|---------------------------|
| **Management controller** | No change | No change – same endpoints and auth. |
| **CountryService / StateService / DistrictService** | Call `MwMasterDataService.fetch*FromMasterDataApi()` | Call new mw-account client (or refactored `MwMasterDataService`) that uses mw-account URLs and response mapping. |
| **Client** | `MwMasterDataService` + `mw-planner.master-data` | New client or same class configured for mw-account; response mapped to existing DTOs/entities. |
| **Config** | `mw-planner.master-data` (base-url, endpoints, defaults) | New or extended config for mw-account (base URL + country/state/district paths); retire master-data for sync. |
| **DTOs / domain** | `MwCountryDTO`, `MwStateDTO`, `MwDistrictDTO` → `Country`, `State`, `District` | Keep; add mappers only if mw-account response shape differs. |

### 2.5 Code references (current)

- **Controller:** `src/main/java/com/mw/planner/controller/config/ManagementController.java`
- **Services:** `CountryService`, `StateService`, `DistrictService` (sync methods and use of `MwMasterDataService`)
- **Client:** `src/main/java/com/mw/planner/service/MwMasterDataService.java` (`fetchCountriesFromMasterDataApi`, `fetchStatesFromMasterDataApi`, `fetchDistrictsFromMasterDataApi`)
- **Config:** `MwPlannerProperties.MasterData`, `application.yaml` → `mw-planner.master-data`
- **DTOs:** `MwCountryDTO`, `MwStateDTO`, `MwDistrictDTO`
- **Repositories:** `CountryRepository`, `StateRepository`, `DistrictRepository`

---

## 3. Campaign inventory filter API (goal-based price)

### 3.1 Endpoint

- **Method and path:** `POST /api/v1/campaign-inventory/{campaignId}/filter`
- **Controller:** `CampaignInventoryController.filterCampaignInventories`
- **Service:** `InventoryService.filterInventories` → `convertToFilterResponseDTO` → `calculatePerformanceMetrics`

### 3.2 Current behaviour

- The response includes a `performance` object per inventory (`CampaignInventoryFilterResponseDTO.Performance`).
- `performance` currently exposes **`cpmRate`** only (from `InventoryService.getCpm(inventory)`), plus `estimatedCost`, `perDayCost`, `perDayAdPlays`, `totalAdPlays`, `totalSot`, `plannedSot`, `sov`.
- Campaign is already loaded in this flow via `campaignService.findById(campaignId)` inside `convertToFilterResponseDTO`, so **campaign goal type** (`Campaign.Goals.goalType`) is available when building performance.

### 3.3 Required change: support performance.cpmRate or performance.cpsRate by goalType

**Requirement:** Expose the rate that matches the campaign goal so the UI can show the right metric (CPM or CPS).

- **When `campaign.getGoals().getGoalType()` is `IMPRESSIONS` or `REACH`:**  
  Use **CPM** for the displayed rate. Set **`performance.cpmRate`** from inventory price (e.g. `price.getCpm()`). Optionally set **`performance.cpsRate`** to `null` or omit for clarity, or keep for backward compatibility.

- **For all other goal types** (e.g. `SOV`, `ADPLAYS`, `ATTRIBUTION`, `OTHER`) **or when goal is null:**  
  Use **CPS** (cost per spot) for the displayed rate. Set **`performance.cpsRate`** from inventory price (e.g. `price.getSpot()`). Set **`performance.cpmRate`** to `null` or retain for backward compatibility as needed.

- **Optional:** Add a **`costUnit`** field to `Performance` (e.g. `"CPM"` or `"CPS"`) so the client knows which rate is the primary one for the current goal.

### 3.4 Implementation pointers

| Area | Current | Required change |
|------|--------|------------------|
| **DTO** | `CampaignInventoryFilterResponseDTO.Performance` has `cpmRate` only. | Add **`cpsRate`** (e.g. `Double`). Optionally add **`costUnit`** (`String`: `"CPM"` \| `"CPS"`). |
| **Calculation** | `InventoryService.calculatePerformanceMetrics(inventory, campaign, schedule)` uses `getCpm(inventory)` and builds `Performance.builder().cpmRate(cpmRate)...`. | Pass campaign’s **goal type** into the calculation. If goal is `IMPRESSIONS` or `REACH`: set `cpmRate` from `getCpm(inventory)`, set `cpsRate` to null (or leave unset). Otherwise: set `cpsRate` from `getSpotRate(inventory)`, set `cpmRate` to null (or leave unset). Set `costUnit` to `"CPM"` or `"CPS"` accordingly. When goal is null, retain existing behaviour (e.g. prefer one rate or expose both) and set `costUnit` if added. |
| **API contract** | Response schema documents `performance.cpmRate`. | Document **`performance.cpsRate`** and, if added, **`performance.costUnit`**. Update OpenAPI/Swagger on `CampaignInventoryFilterResponseDTO.Performance`. |

### 3.5 Code references

- **Controller:** `src/main/java/com/mw/planner/controller/CampaignInventoryController.java` (filter endpoint)
- **Service:** `src/main/java/com/mw/planner/service/InventoryService.java`  
  - `convertToFilterResponseDTO(inventory, campaignId)`  
  - `calculatePerformanceMetrics(inventory, campaign, schedule)`  
  - `getCpm(Inventory)`, `getSpotRate(Inventory)`
- **DTO:** `src/main/java/com/mw/planner/dto/CampaignInventoryFilterResponseDTO.java` (inner class `Performance`)
- **Domain:** `src/main/java/com/mw/planner/domain/Campaign.java` (`Goals.goalType`: `IMPRESSIONS`, `REACH`, `SOV`, `ADPLAYS`, etc.)

### 3.6 Alignment with mw-recommendation-engine

This behaviour aligns with the goal-based price and `costUnit` requirement in **mw-recommendation-engine** (see that repo’s technical document §3.5): Impression/Reach → CPM; other goals → CPS; optional `costUnit` on cost/performance objects. Implementing both sides ensures consistent display of CPM vs CPS in planner and recommendation flows.
