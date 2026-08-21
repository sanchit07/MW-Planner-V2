# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Compile only (fast check)
./gradlew compileJava

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.mw.recommendation.engine.service.ScoringServicePhase15Test"

# Run integration tests (requires Docker for Testcontainers)
./gradlew integrationtest

# Run all checks (unit + integration)
./gradlew check

# Run the app locally (uses application.yaml + application-mw-stg.yaml profile)
./gradlew bootRun

# Format code (Spotless runs automatically on compileJava)
./gradlew spotlessApply
```

`compileJava` depends on `spotlessApply` — every compile auto-formats with Google Java Format. Do not skip this.

Java 24 is required (`toolchain { languageVersion = JavaLanguageVersion.of(24) }`).

Local brand library JAR lives in `lib/` — not from Maven Central.

---

## Architecture

### Request flow

```
POST /campaigns/{id}/recommendations
  → RecommendationController
  → RecommendationService.submitRecommendation()   # hash-check, create RunId, return immediately
  → RecommendationAsyncService.processRecommendationsAsync()   # @Async, virtual threads

POST /runs/{runId}/results
  → RecommendationService.getRecommendationResults()   # polls completed run, returns paginated scored results
```

### Async processing stages (inside `processRecommendationsAsync`)

1. Fetch inventories from MongoDB (geography/venue/classification filters)
2. Batch pre-fetch all supporting data — audience, booking, brand — **before** spawning parallel tasks
3. Score all inventories in parallel using `virtualThreadTaskExecutor` (`creative-vt-*` threads) via `CompletableFuture.runAsync`
4. Batch call Measure API for reach/frequency
5. Auto-select inventories (`applyBudgetAwareAutoSelect`)
6. Bulk insert results; bulk update `selectionMode`
7. Complete run

### Scoring (`ScoringServiceImpl`)

Eight component scores (0–100), combined into `finalScore`:

| Component | Weight | Notes |
|---|---|---|
| `measureFit` | 0.20 | Goal-type specific (see below). Null for AD_PLAYS — weight redistributed. |
| `geoFit` | 0.20 | City/state match or geodesic distance to geofence |
| `availability` | 0.10 | Digital: hourly booking avg. Classic: daily booking avg. |
| `budgetFit` | 0.20 | Estimated cost vs budget ratio → score buckets |
| `audienceFit` | 0.10 | Demographics + segments from `AudienceData` |
| `brandFit` | 0.10 | IAB category exclusions via `BrandService` (local JAR) |
| `qualityFit` | 0.06 | Physical quality indicators on `Inventory` |
| `timeFit` | 0.04 | Peak hour overlap with campaign dates |

`measureFit` by goal type:
- **IMPRESSIONS** — total visitors from `AudienceData` ÷ `goalValue`
- **REACH** — unique visitors from `AudienceData` ÷ `goalValue`
- **SOV** — inventory ad plays ÷ total country-wide ad plays. Total is cached in `totalPossibleAdPlaysCache` (`ConcurrentHashMap` in `ScoringServiceImpl`). The `@Cacheable("totalAdPlays")` Redis config exists but only works if called through Spring proxy — the in-memory map is the active cache.
- **AD_PLAYS** — `measureFit` is null; scoring uses remaining 7 components with redistributed weights

`VariationUtils.applyJitter(score, runId)` applies deterministic per-run jitter to `finalScore` to break ties consistently.

### Auto-selection (`applyBudgetAwareAutoSelect`)

- **No budget, no goal** → no selection
- **With budget** → `executeBudgetAwareSelection`: Round 1 parallel per category → Round 2 budget redistribution → Round 3 remaining budget to top scorers. Goal ceiling only checked for IMPRESSIONS/REACH.
- **No budget, goal = IMPRESSIONS/REACH** → `executeGoalOnlySelection`: greedy pick by score until cumulative metric hits target
- **No budget, goal = SOV** → `executeGoalOnlySovSelection`: greedy pick by score until cumulative SOV hits target
- **No budget, goal = AD_PLAYS** → no selection (clears `selectionMode`)

### Data model (MongoDB collections)

- `recommendation_run` — `RecommendationRun`: status, hash, request snapshot, metadata, `autoSelectedInventoryIds`
- `recommendation_result` — `RecommendationResult`: scores, forecast, cost, `selectionMode` (AUTO/MANUAL/null)
- `run_schedule_recommendation` — `RunScheduleRecommendation`: booking matrix, ad plays, SOT, base price, impressions/reach
- `inventory` — read-only sync from upstream via RabbitMQ
- `audience_data` — read-only sync from upstream via RabbitMQ
- `booking_data` — read-only sync from upstream via RabbitMQ

### RabbitMQ consumers

Three consumers sync data from upstream systems into local MongoDB collections:
- `InventoryMessageConsumer` — upserts `Inventory` documents. Maps the external message via `ExternalInventoryMessageConverter` (`ExternalInventoryMessageDTO` → `Inventory`): parses WKT `POINT`/`LINESTRING` geometry by regex and splits `typeName` (`"Digital > OOH"` → classification/type).
- `AudienceMessageConsumer` — upserts `AudienceData` documents
- `BookingMessageConsumer` — upserts `BookingData` documents (handles digital hourly slots + classic daily slots)

Queue names are configured via `rabbitmq.*.queue.name` properties.

### Inventory browse queries

`POST /campaigns/{id}/browse` and the recommendation inventory fetch build their MongoDB criteria dynamically in `InventoryRepositoryImpl` — venueTypeIds, searchKeywords, `sellingTerm.minDays`/`leadDays`, `priceTypes`, and geo-targeting. This is where filter logic lives; the controller/service layers just pass a `BrowseInventoryRequestDTO`.

### External dependencies

- **Measure API** — reach/frequency enrichment for schedule generation. URL set via `mw-recommendation-engine.measure.api-url`. If blank, enrichment is skipped.
- **Redis** — caches `totalAdPlays` (1h TTL) and `measureReachFrequency` (10min TTL). Cache names prefixed with `mw-recommendation-engine:`.
- **AWS Cognito** — JWT auth via OAuth2 resource server. IAM service URL in `mw-recommendation-engine.iam.service-url`.
- **Brand service** — local JAR (`lib/mw-brand-lib-*.jar`), not external HTTP.

### Profiles

- `application.yaml` — base config, port 10002, virtual threads enabled
- `application-mw-stg.yaml` — staging MongoDB Atlas cluster, staging Redis/RabbitMQ
- `application-mw-prd.yaml` — production MongoDB Atlas, production Redis/RabbitMQ

### Thread executors (`VirtualThreadConfig`)

| Bean | Thread prefix | Used for |
|---|---|---|
| `virtualThreadTaskExecutor` | `creative-vt-` | Parallel inventory scoring |
| `asyncVirtualThreadExecutor` | `async-vt-` | `@Async` methods (default executor) |
| `transcodingTaskExecutor` | `transcoding-vt-` | Transcoding |
| `rabbitmqTaskExecutor` | `rabbitmq-vt-` | RabbitMQ message processing |

All executors use unbounded virtual threads. MongoDB connection pool exhaustion can occur if many concurrent tasks hit DB simultaneously — ensure hot paths use pre-fetched in-memory caches rather than per-task DB queries.

### Pending work (from `docs/TECHNICAL_DOCUMENT.md` §3.5)

Cost calculation is now pricing-model-driven (CPM/Spot/Monthly/Daily/Weekly) in `RecommendationService.calculateCostEstimate`. Remaining: a `costUnit` field (`"CPM"` | `"CPS"`) on `RecommendationResult.CostEstimate` and the API response DTOs is still not added.
