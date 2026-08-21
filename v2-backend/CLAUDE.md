# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MW-Planner is a media planning and campaign management microservice for MovingWalls' OOH (Out-of-Home) advertising platform. It handles campaign lifecycle management, inventory scheduling, approval workflows, pricing negotiation, and integrations with external services (IAM, ADS, Inventory API, Measurement API, Recommendation Engine).

## Tech Stack

- **Language:** Java 24 (virtual threads enabled — `spring.threads.virtual.enabled: true`)
- **Framework:** Spring Boot 3.5.5
- **Database:** MongoDB (Spring Data MongoDB with auditing via `@EnableMongoAuditing`)
- **Cache:** Redis (Spring Cache, `GenericJackson2JsonRedisSerializer` with default typing, cluster-safe batch strategy)
- **Messaging:** RabbitMQ (Spring AMQP, manual ack, fanout exchange, idempotency via Redis SHA-256 key with 24h TTL)
- **Object Storage:** AWS S3 / MinIO (AWS SDK v2 via `CloudStorageService` interface + `S3StorageServiceImpl`)
- **Auth:** OAuth2 Resource Server with JWT (JWKS from `{iam.serviceUrl}/.well-known/jwks.json`); PKCE flow in `IamAuthorizeService`
- **HTTP clients:** Plain `RestTemplate` (JDK `HttpClient` backend, 30s connect / 60s read)
- **Observability:** Prometheus metrics (`MetricsService`), OpenTelemetry agent, Logstash JSON logging
- **API Docs:** SpringDoc OpenAPI (Swagger UI at `/swagger-ui/**`, spec at `/v3/api-docs/**`)
- **Build:** Gradle 8.14.3, Java 24 toolchain, semantic versioning via git-semver-plugin

## Build & Run Commands

```bash
# Build (Spotless Google Java Format auto-applied before compile)
./gradlew clean build

# Run locally (requires docker compose services from local-setup/)
./gradlew bootRun

# Unit tests only
./gradlew test

# Run a single test class
./gradlew test --tests "com.mw.planner.service.AgencyServiceTest"

# Run a single test method
./gradlew test --tests "com.mw.planner.service.AgencyServiceTest.getAgency_ShouldReturnAgency"

# Integration tests only (Testcontainers — requires Docker)
./gradlew integrationtest

# Run a single integration test class
./gradlew integrationtest --tests "com.mw.planner.repository.AgencyRepositoryIntegrationTest"

# Full check (unit + integration, in that order)
./gradlew check

# Format code (Google Java Format — also runs automatically before compile)
./gradlew spotlessApply

# Check formatting without applying
./gradlew spotlessCheck

# SonarQube analysis
./gradlew sonarqube
```

> Note: `compileJava.dependsOn 'spotlessApply'` means every compile run auto-formats. If you see unexpected diffs after compile, that is Spotless fixing formatting — commit those changes.

## Local Development

```bash
# Start infrastructure services
cd local-setup && docker compose up -d
```

| Service       | Port(s)    | Credentials                       |
|---------------|------------|-----------------------------------|
| MongoDB       | 27017      | DB: `mw-planner` (no auth)        |
| Mongo Express | 8081       | admin / admin                     |
| Redis         | 6379       | no auth                           |
| RabbitMQ      | 5672/15672 | admin / admin123                  |
| MinIO         | 9000/9001  | admin / admin123                  |
| Prometheus    | 9090       | no auth                           |
| Grafana       | 13000      | admin / admin                     |

Application port: **10000** | Actuator/Management port: **8100**

## Project Structure

```
src/main/java/com/mw/planner/
├── config/          # Spring configs: Security, Cache, RabbitMQ, AWS, OpenAPI, VirtualThread, AppConfig, AuditingConfig
├── constants/       # Application constants
├── controller/      # REST controllers (base path: /api/v1/*)
│   ├── config/      # ConfigController, ManagementController
│   └── proxy/       # ProxyController (downstream forwarding)
├── domain/          # MongoDB @Document entities (extend BaseEntity<T>)
├── dto/             # Request/Response DTOs (never expose domain entities in API responses)
│   ├── ads/         # ADS service DTOs
│   ├── recommendation/  # Recommendation DTOs
│   └── sales/       # Sales/dashboard DTOs
├── enums/           # Business enums including ErrorCode
├── exception/       # BaseException, GlobalExceptionHandler, domain sub-packages
├── rabbitmq/        # RabbitMQ consumers (MessageConsumer interface, InventoryMessageConsumer)
├── repository/      # MongoDB repositories
│   ├── impl/        # Custom repository implementations (MongoTemplate aggregations, bulk ops)
│   └── projection/  # MongoDB projections
├── security/        # SecurityConfiguration, JwtAuthFilter, CustomJwtAuthenticationConverter, IamAuthorizeService, JwtUtil
├── service/         # Business logic services
│   ├── config/      # ConfigService, DefaultConfigurationService
│   ├── dashboard/   # Dashboard widget services
│   ├── iam/         # IamUserServiceApiClient, IamCompanyApiClient
│   ├── inventory/   # InventoryApiClient
│   ├── proxy/       # ProxyService (HTTP forwarding)
│   ├── recommendation/ # RecommendationService, RecommendationEngineApiClient
│   └── storage/     # CloudStorageService interface + S3StorageServiceImpl
├── util/            # GeoJsonPoint serializers/deserializers, NaN handling
└── validation/      # Custom JSR-380 validators
```

## Key API Endpoints

| Path | Controller | Auth |
|------|-----------|------|
| `/api/v1/campaigns` | CampaignController | `planner:plans:*` permissions |
| `/api/v1/campaign-inventory` | CampaignInventoryController | JWT |
| `/api/v1/campaign-approval-workflow` | CampaignApprovalWorkflowController | JWT |
| `/api/v1/inventories` | InventoryController | JWT |
| `/api/v1/agencies` | AgencyController | JWT |
| `/api/v1/price-management` | PriceManagementController | JWT |
| `/api/v1/recommendations` | RecommendationController | JWT |
| `/api/v1/dashboards` | DashboardController | JWT |
| `/api/v1/users` | UserController | JWT |
| `/api/v1/companies` | CompanyController | JWT |
| `/api/v1/auth` | AuthController | public (OAuth2 callback) |
| `/api/v1/management/**` | ManagementController | HTTP Basic (`SYSTEM_ADMIN` or `GLOBAL_ADMIN` role) |
| `/api/v1/proxy/**` | ProxyController | JWT |
| `/api/v1/public-access/inventories` | PublicAccessController | public (no auth) |

### Security Whitelist (no auth required)
`/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs/**`, `/swagger-ui/**`, `/api/v1/auth/**`, `/api/v1/public-access/inventories`

## Core Architectural Patterns

### BaseEntity

All MongoDB documents extend `BaseEntity<T>` for auditing. The type parameter `T` is the ID type (almost always `String`).

```java
// BaseEntity provides: id, createdBy, lastModifiedBy, createdAt, updatedAt
// Population is automatic via AuditingConfig -> AuditorAware -> SecurityContextHolder
@Document(collection = "campaigns")
public class Campaign extends BaseEntity<String> { ... }
```

### Exception Handling

Every custom exception must:
1. Extend `BaseException` (which holds an `ErrorCode` and varargs `args` for i18n interpolation)
2. Use an `ErrorCode` enum entry that maps a code string (e.g. `"ERR_7001"`) to a message key
3. Have a corresponding entry in `src/main/resources/i18n/messages.properties` (and `messages_ja.properties`)
4. `GlobalExceptionHandler` resolves the message via `MessageService.getMessage(key, locale, args)` and maps the error code to an HTTP status via `mapErrorCodeToHttpStatus()`

```java
// 1. Exception class
public class AgencyNotFoundException extends BaseException {
  public AgencyNotFoundException(String id) {
    super(ErrorCode.AGENCY_NOT_FOUND, "Agency not found with ID: " + id);
  }
}

// 2. ErrorCode entry (grouped by domain range)
AGENCY_NOT_FOUND("ERR_7001", "error.agency_not_found"),

// 3. messages.properties entry
error.agency_not_found=Agency not found

// 4. Add the numeric suffix to mapErrorCodeToHttpStatus() in GlobalExceptionHandler if needed
```

**ErrorCode number ranges:**
| Range | Domain |
|-------|--------|
| 1000–1999 | Global / Auth |
| 2000–2999 | Inventory |
| 3000–3999 | Campaign |
| 4000–4999 | Creative |
| 5000–5999 | User |
| 6000–6999 | Company |
| 7000–7999 | Agency |
| 8000–8999 | Demographics |
| 9000–9999 | Campaign Inventory Schedules |
| 10000–10999 | CSV Upload |
| 11000–11999 | Country |
| 12000–12999 | Master Data API |
| 13000–13999 | ADS Integration |
| 14000–14999 | Storage |
| 15000–15999 | Approval Workflow / Proposal |
| 16000–16999 | Public Access Token |
| 17000–17999 | Custom Fee |
| 20000+ | Brand Library (BrandBaseException, separate handler) |

### Custom Repository Pattern

When a query requires `MongoTemplate` (aggregations, bulk operations, projections), use:
1. `*RepositoryCustom` interface — declare the method signatures
2. `*RepositoryImpl` or `repository/impl/*RepositoryImpl` — implement with `MongoTemplate`, annotated `@Repository`
3. Standard `*Repository` extends both `MongoRepository<T, ID>` and `*RepositoryCustom`

Spring Data resolves the impl automatically by naming convention (`*Impl` suffix).

```java
// Interface
public interface CampaignRepositoryCustom {
  Page<Campaign> findCampaignsWithFilters(CampaignFilterDTO filter, Pageable pageable);
}

// Implementation
@Repository
@RequiredArgsConstructor
public class CampaignRepositoryImpl implements CampaignRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  // use PageableExecutionUtils.getPage() for paginated results
}

// Combined repository
public interface CampaignRepository
    extends MongoRepository<Campaign, String>, CampaignRepositoryCustom { ... }
```

### External HTTP Clients

All outbound HTTP uses a shared `RestTemplate` bean (defined in `AppConfig`; JDK `HttpClient` backend, 30s connect / 60s read). Clients live in `service/{domain}/` and follow this pattern:

- Inject `MwPlannerProperties` for URLs — never hardcode them
- Wrap calls in try/catch for `HttpClientErrorException`, `HttpServerErrorException`, `RestClientException`, then rethrow as domain `BaseException` subclasses
- Use `MwPlannerProperties.{Section}.getFull*Url()` helper methods for URL construction (avoids manual string concatenation with placeholders)

Outbound ADS calls are audited via `AdServerRequestLogService`, which persists request/response records to the `adserver_request_logs` MongoDB collection (sensitive headers — `authorization`, `x-api-key`, `api-key`, `auth-token`, `bearer` — are masked). Logging is error-resilient and never disrupts the business call.

### MwPlannerProperties (application config)

All custom configuration lives under the `mw-planner:` YAML prefix and is bound to `MwPlannerProperties` (`@ConfigurationProperties(prefix = "mw-planner")`). When adding new config:
1. Add a nested `@Data` static class to `MwPlannerProperties`
2. Add corresponding YAML keys to `application.yaml` and environment-specific overrides
3. Add URL helper methods (`getFull*Url()`) for multi-part URL construction

### API Response Envelope

All controller responses wrap data in `ApiResponse<T>`:
```java
ApiResponse.success(data)          // { success: true, data: ... }
ApiResponse.error(errorResponse)   // { success: false, error: { code, message, path } }
```
`@JsonInclude(NON_NULL)` suppresses null fields. Never return raw domain entities — always map to DTOs in the service layer.

### JWT / Security

- `SecurityConfiguration` sets up OAuth2 Resource Server validating JWTs via JWKS from `{iam.serviceUrl}/.well-known/jwks.json`
- `CustomJwtAuthenticationConverter` extracts permissions from the JWT `subscriptions[productId].permissions` claim and registers them as `GrantedAuthority` with `ROLE_` prefix, enabling `@PreAuthorize("hasRole('planner:plans:create')")`
- `JwtAuthFilter` pre-initializes `IamUserContext` into a request-scoped cache before the OAuth2 filter chain runs (so downstream services get user data without a second IAM call)
- Management endpoints (`/api/v1/management/**`) use HTTP Basic with a single in-memory user (`SYSTEM_ADMIN` role), credentials from `mw-planner.management.credentials`

### Caching

Redis cache is configured globally with TTL from `mw-planner.cache.ttl-seconds` (default 3600s). Cache uses `GenericJackson2JsonRedisSerializer` with Jackson default typing (`@class` property written in JSON).

Active caches: `agencies`, `agencyName`, `campaigns`, `campaignInventorySchedules`, `companies`, `company-lookup`, `countries`, `countryNames`, `customFees`, `demographics`, `districts`, `iamUsers`, `iamUserContext`, `inventories`, `inventoryCountsByCountry`, `inventoryCountsByCountryAndClassification`, `states`

When using `@CacheEvict(allEntries = true)` on a Redis Cluster, `RedisClusterSafeBatchStrategy` deletes keys one-by-one to avoid CROSSSLOT errors.

### RabbitMQ Consumer (Inventory)

`InventoryMessageConsumer` listens on `${rabbitmq.inventory.queue.name}` (fanout exchange). Idempotency is enforced by hashing `{id}:{inventoryId}:{operation}:{occurredAt}` with SHA-256 and storing the key in Redis with a 24h TTL (`inventory:message:{hash}`). Messages with `operation = "refresh"` (→ `InventoryProcessingService.processInventoryMessage()`) and `operation = "delete"` (→ `deleteInventoryByExternalId()`) are processed; all other operations are acked and skipped. On error the exception is re-thrown to trigger RabbitMQ retry (3 attempts, exponential backoff configured in `application.yaml`).

Every received message is also persisted to the `inventory_message_logs` MongoDB collection via `InventoryMessageLogService` for observability (raw payload, parsed `inventoryId`, `MessageConsumeStatus` enum = `RECEIVED`/`PROCESSED`/`SKIPPED`/`DUPLICATE`/`FAILED`, error, timestamps). The write happens in a `finally` block and is wrapped so it can never throw into the consumer path — idempotency, ack, and retry behavior are unchanged. Growth is capped by a hard-coded 3-day TTL index on `receivedAt`, created at startup by `InventoryMessageLogIndexInitializer` (in `config/`), which also creates an `inventoryId` index.

### Virtual Threads

`VirtualThreadService` wraps `CompletableFuture.runAsync/supplyAsync` with named executors (`virtualThreadTaskExecutor`, `districtSyncTaskExecutor`) configured in `VirtualThreadConfig`. Use it for fire-and-forget async work or parallelizing slow I/O (external API calls, heavy aggregations).

### Scheduled Tasks

`CampaignStatusScheduler` runs on a cron from `${mw-planner.scheduler.campaign-status-update.cron}` to transition campaign statuses (PLANNED → ACTIVE, ACTIVE → COMPLETED, COMPLETED → ARCHIVED). Uses bulk MongoDB update via `CampaignRepositoryCustom.bulkUpdateStatus()`.

## Testing

### Unit Tests (`src/test/java`)

- `@ExtendWith(MockitoExtension.class)` — no Spring context loaded
- Use `@InjectMocks` for the class under test, `@Mock` for all dependencies
- Controller tests use `MockMvc` with `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler(messageService, metricsService, userService)).build()` — `GlobalExceptionHandler` must be added to get proper error response testing
- `objectMapper.findAndRegisterModules()` is needed for Java time serialization in controller tests
- The test JVM passes `-javaagent:{mockito-core}` (configured in `build.gradle`) — do not remove this from the Gradle task

```java
@ExtendWith(MockitoExtension.class)
class AgencyControllerTest {
  @Mock private AgencyService agencyService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;
  @InjectMocks private AgencyController agencyController;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(agencyController)
        .setControllerAdvice(new GlobalExceptionHandler(messageService, metricsService, userService))
        .build();
  }
}
```

### Integration Tests (`src/integrationtest/java`)

- `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` — full application context
- `TestcontainersConfiguration` starts real MongoDB 8.0, Redis 7.2, RabbitMQ 3.13, MinIO via `@ServiceConnection` (auto-wires connection properties)
- MinIO does not support `@ServiceConnection`; its endpoint is registered manually via `DynamicPropertyRegistry`
- `src/integrationtest/resources/application.yaml` overrides config for the test context (separate RabbitMQ exchange/queue names, MinIO credentials)
- Repository integration tests extend `BaseRepositoryIntegrationTest<T, ID>` which provides common CRUD, pagination, and search assertions; subclasses only implement `setupTestData()` and a few abstract accessors

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgencyRepositoryIntegrationTest extends BaseRepositoryIntegrationTest<Agency, String> { ... }
```

### Test Infrastructure

- Testcontainers: MongoDB 8.0, Redis 7.2 (via `com.redis:testcontainers-redis`), RabbitMQ 3.13, MinIO
- MockServer (`org.mock-server:mockserver-netty:5.15.0`) for external API mocking in integration tests
- JaCoCo XML report auto-generated after unit test run (required by SonarQube)
- SonarQube exclusions: `config`, `constants`, `dto`, `domain`, `enums`, `exception`, `MwPlannerApplication.java`

## Code Style & Conventions

- **Google Java Format** enforced via Spotless; runs automatically on `compileJava` — never skip it
- **Lombok:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Slf4j`, `@RequiredArgsConstructor` throughout
- **No MapStruct** — DTOs are mapped manually in the service layer
- **ObjectMapper configuration** (`AppConfig`): `FAIL_ON_UNKNOWN_PROPERTIES=false`, `NON_NULL` serialization, NaN allowed, custom `GeoJsonPoint` serializer/deserializer registered
- **i18n:** `src/main/resources/i18n/messages.properties` (English) and `messages_ja.properties` (Japanese); locale resolved from `IamUserContext.getLocale()`
- **Commit style:** conventional commits — `feat(scope): description`, `fix: description`, `refactor: description`; semver bump driven by prefix pattern (`feat:` → minor, `fix:` → patch, `!:` → major)

## External Service Integrations

| Service | Config prefix | Client class |
|---------|--------------|-------------|
| IAM (OAuth2, user/company) | `mw-planner.iam` | `IamUserServiceApiClient`, `IamCompanyApiClient`, `IamAuthorizeService` |
| Master Data (countries, states, districts) | `mw-planner.master-data` | `MwMasterDataService` |
| Inventory API | `mw-planner.proxy.applications.inventory-api` | `InventoryApiClient` |
| Measurement API (reach & frequency) | `mw-planner.measure` | `MwMeasureService` |
| ADS Service (campaign submission) | `mw-planner.ads` | `MWAdsService` |
| Recommendation Engine | `mw-planner.recommendation-engine` | `RecommendationEngineApiClient` |

## CI/CD & Deployment

- **CI:** GitLab CI (`.gitlab-ci.yml`) dispatches to `.ci/staging.yml` or `.ci/k8s.yml`
- **Docker:** Multi-stage build (Gradle builder → Eclipse Temurin JRE 24), OpenTelemetry agent included
- **Versioning:** Semantic versioning from git tags via git-semver-plugin; `defaultPreRelease = "SNAPSHOT"`
- `application.yaml` is the base / staging config; environment-specific YAMLs (`application-mw-stg.yaml`, `application-mw-prd.yaml`) override it at deploy time
- The `lib/mw-brand-lib-0.1.0-SNAPSHOT.jar` is a local file dependency; it is not in any Maven repo

## Branching Strategy

- **Normal features / bugfixes:** branch from `k8s/stg`, merge into `k8s/stg`
- **QA sign-off promotion:** merge `k8s/stg` → `k8s/prod`
- **Hotfixes:** raise directly against `k8s/prod`, then back-merge into `k8s/stg` to keep branches in sync
- Never merge unreleased work to `k8s/prod` without QA sign-off (except approved hotfixes)

## Important Notes

- `application.yaml` contains default/staging config; environment-specific overrides are applied at deployment
- Virtual threads are enabled (`spring.threads.virtual.enabled: true`)
- RabbitMQ consumer uses manual acknowledgment with idempotency via Redis (24h TTL)
- The `mw-brand-lib-0.1.0-SNAPSHOT.jar` is a local library dependency in `lib/`
- Proxy service forwards requests to downstream apps with API key auth header override
- Campaign approval workflow has 3 stages: AGENCY -> INTERNAL -> MEDIA_OWNER
- Scheduled task runs daily at midnight to update campaign statuses (activate/complete/archive)

## MongoDB Index Recommendations

### `campaigns` collection (pending creation)
```js
db.campaigns.createIndex({ companyId: 1, status: 1, updatedAt: -1 })   // main list query
db.campaigns.createIndex({ companyAccess: 1, status: 1, updatedAt: -1 }) // $or array branch
db.campaigns.createIndex({ companyId: 1, startDate: 1, endDate: 1 })    // date-overlap queries
db.campaigns.createIndex({ status: 1, startDate: 1 })                   // status scheduler
db.campaigns.createIndex({ status: 1, endDate: 1 })                     // status scheduler
```

### `schedules` collection
- No additional indexes needed — all access is via `_id` (findAllById, deleteByIdIn)

## Key Domain Knowledge

### Spots / loops per hour (`InventoryService.getSpotsPerHour()` / `getLoopsPerHour()`)
```
spotsPerHour = 3600 / spotDuration          // getSpotsPerHour()
loopsPerHour = spotsPerHour / spotsPerLoop  // getLoopsPerHour()
```
Both derive from `inventory.digitalFields`. Used when creating/updating schedules and building Measure API payloads. The combined `(3600 / spotDuration) / spotsPerLoop` (formerly documented as a single `spotsPerHour`) is now `getLoopsPerHour()`.

### `clientPerLoop` in filter API response
`inventoryDetails.operations.clientPerLoop` = `inventory.digitalFields.spotsPerLoop` (field rename only, no calculation).

### Reach & Frequency API flows
- `POST /api/v1/campaign-inventory/reach-and-frequency` — pure proxy to Measure API, no MongoDB access
- `GET /{campaignId}/forecast` — reads all `CampaignInventorySchedules` + `schedules` from DB, builds payload, calls Measure API
- Both recommendation and non-recommendation inventories follow the same forecast flow — no distinction after inventories are saved as `CampaignInventorySchedules`

### Recommendation Engine Integration
- `RecommendationService.enrichWithInternalIdsAndPerformance()` resolves engine `externalId` → planner `internalId` via `inventoryService.findByExternalIdIn()`
- `PaginatedRecommendationResponseDTO.InventoryDetails` now includes `digitalFields` (`InventoryResponseDTO.DigitalFieldsDTO`) populated from the resolved inventory
- `clientPerLoop` / `digitalFields` were not previously available on recommendation inventories

### Custom Fee Logic

Fees have two scopes: company-level (`campaignId = null`) and campaign-level (`campaignId != null`). Two types: `PERCENTAGE` and `VALUE`.

The `isIncludeInMediaPlan` flag controls visibility:
- `false` (hidden): fee is factored into media cost but not shown in pricing UI
- `true` (visible): fee appears as a line item in the media plan

`CustomFeeService` builds a fee context per campaign by combining company + campaign fees, splits into hidden/visible buckets, and applies them during pricing calculation. Mutating campaign-level fees can trigger a campaign approval reset.

### Goal-Based Pricing (Pending — see `docs/TECHNICAL_DOCUMENT.md` §3)

`POST /api/v1/campaign-inventory/{campaignId}/filter` returns a `performance` object per inventory. The **pending requirement** is to expose the rate that matches the campaign's goal:

- `campaign.goals.goalType` is `IMPRESSIONS` or `REACH` → set `performance.cpmRate` from `getCpm(inventory)`, `cpsRate = null`
- All other goal types (or null) → set `performance.cpsRate` from `getSpotRate(inventory)`, `cpmRate = null`
- Optionally add `performance.costUnit` field (`"CPM"` or `"CPS"`) so the UI knows which rate is primary

Key files: `InventoryService.calculatePerformanceMetrics()`, `CampaignInventoryFilterResponseDTO.Performance`, `Campaign.Goals.goalType`.

### Price Negotiation States

Pricing history transitions: `RATE_CARD` → `PROPOSED` → `COUNTERED` → `ACCEPTED`. Any effective price change resets impacted schedule approvals. Campaign status moves to `NEGOTIATING` on renegotiation. Schedule-level approvals tracked in `approvedScheduleIds` and `approvedBy` on `CampaignInventorySchedules`.
