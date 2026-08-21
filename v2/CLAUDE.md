# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**MW Planner** is an Out-of-Home (OOH) advertising campaign management platform. It supports media agencies, media owners, advertisers, and resellers with campaign lifecycle management, inventory scheduling, proposal generation, and role-based approval workflows.

## Commands

```bash
# Development
yarn dev              # Start dev server on port 4700

# Build
yarn build            # TypeScript check + Vite build

# Testing
yarn test             # Run all tests (Vitest)
yarn test:ui          # Vitest UI mode
yarn test:coverage    # Run tests with coverage report

# Linting & Formatting
yarn lint             # ESLint (zero warnings allowed)
yarn lint:fix         # ESLint with auto-fix
yarn prettier:check   # Check formatting
yarn prettier:write   # Auto-format src files
```

**Run a single test file:**

```bash
yarn test src/utils/__tests__/campaign.utils.test.ts
```

Pre-commit hooks (Husky) run `lint:fix` and `prettier:write` on staged files automatically.

## Path Aliases

TypeScript and Vite are configured with these aliases (defined in `tsconfig.app.json`):

| Alias         | Maps to          |
| ------------- | ---------------- |
| `@services`   | `src/services`   |
| `@pages`      | `src/pages`      |
| `@components` | `src/components` |
| `@config`     | `src/config`     |
| `@api`        | `src/api`        |
| `@utils`      | `src/utils`      |
| `@hooks`      | `src/hooks`      |
| `@schemas`    | `src/schemas`    |
| `@constants`  | `src/constants`  |
| `@store`      | `src/store.ts`   |

## Architecture

### Provider Chain

`StrictMode → ErrorBoundary → BrowserRouter → Redux Provider → TolgeeContextProvider → AppRoutes`

`main.tsx` exports a `bootstrap()` function that only mounts the React root when `!import.meta.env.VITEST`, enabling clean test environments.

### State Management

Redux Toolkit with two patterns in `src/store.ts`:

1. **Traditional slices** for local UI state: `auth`, `profile`, `campaign`, `campaignDetails`, `brand`, `stepper`, `sidebar`, `configurationMetadata`, `mapMarkerLocations`, `campaignsUI`
2. **RTK Query API slices** for server state with automatic caching: `authApi`, `userApi`, `campaignApi`, `companyApi`, `campaignDetailsApi`, `brandApi`, `iamBrandApi`, `inventoryApi`, `inventoryManagementApi`, `publicAccessApi`, `dashboardApi`, `accountApi`, `accountUserApi`, `agencyApi`, `configurationMetadataAPI`

Several slice files export **two** `createApi` instances bound to different base URLs (see API Layer): `brandApi`/`iamBrandApi`, `campaignApi`/`companyApi`, `accountApi`/`accountUserApi`. Each must be registered separately in `src/store.ts` (reducer + middleware).

Use typed hooks from `src/hooks/redux.ts`: `useAppDispatch()` and `useAppSelector()`.

### API Layer

- **`src/api/axiosInstance.ts`** — Axios instance with JWT Bearer token injection, automatic token refresh on 401, and redirect to login on expiry.
- **`src/api/axiosBaseQuery.ts`** — RTK Query base query wrapping Axios. Supports two backends (`BACKEND_URL` and `BACKEND_URL_PROXY`). Shows toast errors for all failures except error code `ERR_9010`.
- **`src/services/`** — RTK Query API slices organized by domain (auth, campaign, inventory, brand, etc.).

| Key                       | Base URL  | Use for                                           |
| ------------------------- | --------- | ------------------------------------------------- |
| `"BACKEND_URL"` (default) | `/api/v1` | Core backend endpoints                            |
| `"BACKEND_URL_PROXY"`     | `/proxy/` | Proxied services (e.g. IAM, inventory management) |

A `createApi` instance is bound to one base URL. If an endpoint needs the other base URL, create a separate `createApi` with the appropriate key and register it in `src/store.ts`.

### Getting the Current Company ID

The user profile is stored at `state.profile.profile` in Redux. The standard pattern to resolve the active company ID (used across multiple components):

```ts
const user = useAppSelector((state) => state.profile.profile);
const companyId = user?.activeCompanyId || user?.memberships?.[0]?.company_id;
```

### Routing & Auth

- `src/routes/AppRoutes.tsx` — All routes. Pages are lazy-loaded via `React.lazy()`.
- `src/routes/ProtectedRoute.tsx` — Guards routes by `isAuthenticated` state and RBAC roles (admin, advertiser, agency, internal).
- Tokens stored in localStorage under key `"auth_token"`.

### Forms

- **React Hook Form** + **Zod** schemas (in `src/schemas/`) via `@hookform/resolvers`.
- Schemas are organized by domain: `src/schemas/campaigns/`, `src/schemas/auth/`, etc.

### i18n

- **Tolgee** with English (`en`) and Japanese (`ja`).
- Namespaces: `common`, `campaigns`, `dashboard`, `inventories`, `creatives`, `proposals`, `settings`, `signals`, `statements`, `tags`, `brands`, `price`.
- Translation files: `src/assets/i18n/{namespace}/{language}.json`.
- Language persisted in localStorage key `"mw-planner-language"`.
- Use `useTranslate("namespace")` hook from `@tolgee/react` in components.

### Styling

- **Tailwind CSS v4** via `@tailwindcss/vite` (not postcss)
- Design tokens as CSS custom properties using `hsl(var(--color))` pattern
- Dark mode via `.dark` class
- Use `clsx` for conditional classes; `src/utils/tailwindMerge.ts` for merging

### Key Feature Areas

- **`src/pages/campaigns/`** — Campaign listing, creation wizard (multi-step via stepper service), campaign detail view, inventory selection, media plan, and price management.
- **`src/pages/inventories/`** — Inventory management with Mapbox GL map views and drawing tools.
- **`src/pages/statements/`** — Statement management with splitting capabilities.
- **`src/pages/dashboard/`** — Role-specific dashboards with configurable widgets.

### Component Structure

- `src/components/ui/` — Low-level primitives (buttons, inputs, modals, etc.)
- `src/components/common/` — Shared feature-agnostic components
- `src/components/campaigns/` — Campaign-specific reusable components
- `src/layout/` — App shell (Header, Sidebar, Footer, Layout wrapper)

### Key Directories

```
src/
  api/           # Axios instance + RTK Query base query
  components/
    ui/          # Generic reusable UI primitives
    common/      # Domain-specific shared components (charts, etc.)
  config/        # Environment variables (VITE_*) and Tolgee init
  constants/     # Domain constants
  hooks/         # Custom React hooks
  layout/        # App shell: Header, Sidebar, Footer
  pages/         # Route-level page components
  routes/        # Route definitions, ProtectedRoute, public routes
  schemas/       # Zod schemas
  services/      # RTK slices + API definitions, one dir per domain
  test/          # Test setup and shared test utilities
  types/         # Shared TypeScript types per domain
  utils/         # Pure utility functions (date, currency, export, etc.)
```

### Testing

- Vitest with `jsdom` environment. Setup in `src/test/setupTests.ts`.
- Tests co-located in `__tests__/` subdirectories alongside source files.
- `@testing-library/react` + `@testing-library/user-event` + `@testing-library/jest-dom`.
- `src/test/test-utils.tsx` exports `renderWithRouter` and `runSimplePageTests` helpers.

### ESLint Rules to Know

- `_`-prefixed variables/params are exempt from `no-unused-vars`
- `import/order` is enforced: builtin → external → internal → parent/sibling, alphabetized, with newlines between groups
- `react/react-in-jsx-scope` is off — no need to import React in JSX files

---

## Campaign Creation Wizard

**Entry:** `/campaigns/create` or `/campaigns/edit/:campaignId` → `CreateCampaignPage` → `CampaignWrapper`

### Steps

| #   | Name             | Required       | Key Component                   |
| --- | ---------------- | -------------- | ------------------------------- |
| 1   | Campaign Details | Yes            | `CreateCampaignForm.tsx`        |
| 2   | Budget & Goals   | Yes            | `BudgetAndGoalPage.tsx`         |
| 3   | Targeting        | No (skippable) | `TargetingForm.tsx`             |
| 4   | Inventories      | No             | `InventoryPageForm.tsx`         |
| 5   | Optimization     | No (skippable) | `optimization/Optimization.tsx` |

Steps 3–5 are only accessible after Steps 1 and 2 are completed. Step 5 also requires Step 4.

### Stepper Architecture

- **`stepperSlice`** manages step state: `steps[]`, `currentStepId`, `progress`, `isEditMode`, `inventoryFilters`
- **`campaignSlice`** manages campaign data: `campaignId`, `campaignData`, `isCreating`, `isEditMode`
- `CampaignWrapper` communicates with step components via **refs**: calls `validateStep()` and `submitForm()` on child refs before navigating
- Current step persisted in localStorage key `campaign_stepper_step` as `{ campaignId, step }` for page-refresh recovery

### Autosave

- Hook: `src/hooks/useAutosave.ts`
- Triggers on **field blur** in edit mode only
- Endpoint: `PATCH /campaigns/{id}/autosave`
- Debounced; uses singleton `AutosaveStateManager` to deduplicate requests

### Finalize

- "Save as Draft" — keeps status as `DRAFT`
- "Finalize Campaign" — sets status to `PLANNED` via `PUT /campaigns/{id}`, then navigates to `/campaigns`

---

## Inventory Selection System

### Two Inventory Types

**Regular inventories** (`InventoryItem`):

- Selection state: `item.detail.isSelected: boolean`
- Rendered in `InventoryListPanel.tsx`
- Supports Select All via bulk endpoint

**Recommendation inventories** (`InventoryRecommendationItem`):

- Selection state: `item.selectionMode: "AUTO" | "MANUAL" | "NOT_SELECTED"`
  - `AUTO` = AI pre-selected, `MANUAL` = user selected, `NOT_SELECTED` = user deselected
- Rendered in `InventorySmartSuggestionList.tsx`
- No Select All support
- Shows AI component scores (`geoFit`, `audienceFit`, `budgetFit`, etc.)

### API Endpoints

| Operation              | Endpoint                                                     | Payload                                                |
| ---------------------- | ------------------------------------------------------------ | ------------------------------------------------------ |
| Single select/deselect | `POST /campaign-inventory/{id}/select`                       | `{ inventoryId, operationType: "SELECT"\|"DESELECT" }` |
| Bulk select/deselect   | `POST /campaign-inventory/{id}/select-all?operationType=...` | `{ ...all active filters }`                            |
| Fetch forecast         | `GET /campaign-inventory/{id}/forecast`                      | —                                                      |

Both inventory types use the **same single-select endpoint**. Only regular inventory supports bulk.

### Selection Flow

**Regular inventory selection:**

1. Optimistic update: `isSelected = true` immediately
2. `POST /select { operationType: "SELECT" }`
3. On error: revert `isSelected` to previous value
4. Reload forecast data

**Recommendation inventory selection:**

1. No optimistic update
2. `POST /select { operationType: "SELECT" }`
3. On success: `smartSuggestionListRef.current.updateItemSelection(id, true)` → sets `selectionMode = "MANUAL"`
4. Reload forecast data

### isSelecting Flag

`InventoryPageForm` manages `isSelecting: boolean` — set to `true` during any API call, passed to both panels to disable all checkboxes and prevent concurrent selections.

### Selected Count

Displayed as `[selected] / [total]` badge in InventoryListPanel:

- `selected` = `forecastData.totalInventories` (from forecast API, updated after every selection)
- `total` = `totalElements` from paginated inventory response

### Normalization

Both types are rendered via `InventoryDetailCard` using `toInventoryDisplayItem()` from `src/utils/inventory-display.utils.ts`. Type is detected via `"detail" in item` (regular) vs `"inventoryId" in item` (recommendation).

### Reach & Frequency — `spotsPerHour` calculation

`InventoryDetailCard` calls `POST /campaign-inventory/reach-and-frequency` when a card is expanded. The `spotsPerHour` field in the payload is computed as:

```
spotsPerHour = Math.floor(3600 / slotDuration / clientPerLoop)
```

- **Regular inventories**: `slotDuration` and `clientPerLoop` come from `item.operations` (mapped directly in `fromInventoryItem`).
- **Recommendation inventories**: these fields come from `item.inventoryDetails.digitalFields` (`spotDuration` → `slotDuration`, `spotsPerLoop` → `clientPerLoop`), mapped into `operations` inside `fromRecommendationItem`. If `digitalFields` is absent, `operations` is `undefined` and both values fall back to `1` (giving `spotsPerHour = 3600`).

**Rule:** do not set `operations: undefined` unconditionally in `fromRecommendationItem` — the `digitalFields` mapping must be preserved or `spotsPerHour` will always be sent as 3600.

### Known Gotcha — View-switch effect dependency

`InventoryPageForm.tsx` has a `useEffect` (around line 445) that switches the view back to the recommendation flow when a goal/budget is added while the user is on the inventory list. Its dependency array **intentionally excludes `campaignData?.skipRecommendation`**.

Including it caused a bug: after every inventory select/deselect, `onInventorySelectionChange` → `reloadCampaignData()` fetches fresh campaign data and syncs `skipRecommendation` from stale `false` → `true` in Redux. That dep change triggered the effect, which called `saveSkipRecommendationFalse()` and flipped the view back to recommendations — even though the user had explicitly skipped.

**Rule:** this effect must only re-run when `goals.goalType` or `budget` changes, not when `skipRecommendation` syncs. Do not add `campaignData?.skipRecommendation` back to its deps.

---

## ModalDrawer — Animation Pattern

`src/components/ui/ModalDrawer.tsx` uses a `mounted` + `visible` two-state pattern. **Do not simplify this back to `if (!isOpen) return null;`.**

### Why

The app mounts 20+ drawers simultaneously (brand, inventory, column customization, filter, etc.). If all are always in the DOM, every one renders a `z-50 fixed inset-0` overlay and all compete for `document.body.style.overflow`. This broke every popup/drawer in the app.

### How it works

- `mounted` controls whether the component is in the DOM at all (unmounts 300 ms after close, allowing slide-out animation to finish)
- `visible` drives the `translate-x-full` / `translate-x-0` CSS transition
- Double `requestAnimationFrame` triggers the slide-in transition after the DOM node is inserted

```tsx
const [mounted, setMounted] = useState(isOpen);
const [visible, setVisible] = useState(isOpen);

useEffect(() => {
  if (isOpen) {
    setMounted(true);
    let raf1: number, raf2: number;
    raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => setVisible(true));
    });
    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
  } else {
    setVisible(false);
    const timer = setTimeout(() => setMounted(false), 300);
    return () => clearTimeout(timer);
  }
}, [isOpen]);

if (!mounted) return null;
```

**Rule:** use `visible` (not `isOpen`) to drive the `translate-x` classes. The container `div` has `onClick={onClose}` for click-outside-to-close; the panel has `onClick={(e) => e.stopPropagation()}` to prevent bubbling.

---

## Targeting Step — Tab Navigation

`TargetingForm.tsx` has three tabs: **Demographics**, **Geofencing**, and **Signals** (disabled).

The `submitForm` method (exposed via `useImperativeHandle`) intercepts the CampaignWrapper "Next" button to implement tab-based navigation:

- **Demographics tab** → pressing Next switches to the Geofencing tab (returns `false` to prevent CampaignWrapper from advancing to the next step)
- **Geofencing tab** → pressing Next proceeds normally to the Inventories step (returns `true`)

This means `submitForm` behaviour depends on `activeTab`. The `activeTab` state is in the dependency array of `useImperativeHandle` — do not remove it or the closure will be stale.

---

## i18n Guidelines

### Namespace-to-page mapping

| Namespace     | Used in                                                                       |
| ------------- | ----------------------------------------------------------------------------- |
| `campaigns`   | All campaign pages, targeting, inventory, optimization, media plan            |
| `price`       | Price management page, `InventoryAvailabilityCalendarView`                    |
| `dashboard`   | Dashboard widgets (BudgetTracker, CampaignOverview, RevenuePerformance, etc.) |
| `common`      | Layout (Header, Sidebar, Footer), shared UI                                   |
| `inventories` | Inventory management pages                                                    |
| `creatives`   | Creatives pages                                                               |
| `proposals`   | Proposal theme pages                                                          |
| `signals`     | Signals configuration                                                         |
| `statements`  | Statements pages                                                              |
| `tags`        | Tags pages                                                                    |
| `brands`      | Brand creation form                                                           |
| `settings`    | Settings pages                                                                |

### Rule — no hardcoded UI strings

All user-visible strings must go through a translation function. Common mistakes to avoid:

- JSX text content: `<p>Some Text</p>` → must be `<p>{t("key")}</p>`
- Prop values: `title="Download"` → must be `title={t("key")}`
- Column `headerName` in AG Grid: must be defined inside the component function so the translation hook result is accessible
- `aria-label`, `placeholder`, `calendarTitle`, `emptyMessage` props are commonly missed

When adding new UI strings, add keys to **both** `en.json` and `ja.json` for the appropriate namespace.

---

## Optimization Step — Budget Distribution

`BudgetAllocationOptimization.tsx` contains the Budget Distribution UI (sliders for Digital/Classic/Retail/Transit budget split). It is currently **not rendered** in `Optimization.tsx`.

To re-enable it, add back to `Optimization.tsx`:

```tsx
// Import
import BudgetAllocationComponent from "./BudgetAllocationOptimization";

// Inside CardContent, before ScheduleOptimizationComponent
<BudgetAllocationComponent
  control={control}
  onFieldChange={handleBudgetAllocationFieldChange}
  budgetFormData={getValues("budgetAllocation")}
  handleBudgetSchedulingFieldMouseUp={handleBudgetSchedulingFieldMouseUp}
/>;
```

All form state, handlers, and autosave logic for it remain intact in `Optimization.tsx`.
