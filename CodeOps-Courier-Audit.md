# CodeOps-Courier Codebase Audit

**Generated:** 2026-02-22
**Commit:** `8ed00ac2860115f48a0f568597ec46e96c3b2ea2`
**Branch:** `main`
**Remote:** `https://github.com/AI-CodeOps/CodeOps-Courier.git`

---

## 1. Project Identity

| Field | Value |
|-------|-------|
| Name | CodeOps-Courier |
| Type | Spring Boot REST API |
| Port | 8099 |
| API Prefix | `/api/v1/courier` |
| Java Version | 21 (target) / 25 (runtime) |
| Spring Boot | 3.3.0 |
| Build Tool | Maven (with wrapper) |
| Database | PostgreSQL 16 (port 5438, database `codeops_courier`) |
| ORM | Hibernate (JPA) with `ddl-auto: update` (dev) / `validate` (prod) |
| Auth | JWT (HMAC-SHA, stateless, shared secret with CodeOps-Server) |

---

## 2. Source Statistics

| Metric | Count |
|--------|-------|
| Java files (main) | 176 |
| Java files (test) | 46 |
| Lines of code (main) | 14,899 |
| Lines of code (test) | 14,075 |
| Entities | 17 + 1 abstract base |
| Enums | 7 |
| Repositories | 18 |
| Services | 22 |
| Controllers | 13 |
| Mappers | 13 |
| Request DTOs | 31 |
| Response DTOs | 29 |
| Config classes | 12 |
| Exception classes | 6 |
| Test files | 46 |
| Test methods (@Test) | 680 |

---

## 3. Entry Point

**File:** `src/main/java/com/codeops/courier/CourierApplication.java`

```java
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class})
public class CourierApplication { ... }
```

---

## 4. Dependency Manifest

| Dependency | Version | Purpose |
|------------|---------|---------|
| spring-boot-starter-web | 3.3.0 | REST API framework |
| spring-boot-starter-data-jpa | 3.3.0 | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 | Security framework |
| spring-boot-starter-validation | 3.3.0 | Bean validation (Jakarta) |
| postgresql | runtime | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token handling |
| lombok | 1.18.42 | Code generation (getters, builders) |
| mapstruct / mapstruct-processor | 1.5.5 | DTO-entity mapping |
| jackson-datatype-jsr310 | (managed) | Java 8+ date/time serialization |
| jackson-dataformat-yaml | (managed) | YAML parsing (OpenAPI import/export) |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | Swagger UI / OpenAPI docs |
| logstash-logback-encoder | 7.4 | JSON structured logging (prod) |
| graalvm-js / graalvm-js-scriptengine | 24.1.1 | JavaScript sandbox for scripts |
| polyglot | 24.1.1 | GraalVM polyglot engine |
| testcontainers-postgresql | 1.19.8 | Integration test containers |
| h2 | test | In-memory database for unit tests |
| mockito-core | 5.21.0 | Mocking framework |
| byte-buddy | 1.18.4 | Mockito bytecode generation (Java 25 compat) |
| jacoco-maven-plugin | 0.8.14 | Code coverage |

---

## 5. Infrastructure

### 5.1 Docker Compose

**File:** `docker-compose.yml`

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| courier-db | postgres:16-alpine | 127.0.0.1:5438:5432 | PostgreSQL (database: `codeops_courier`, user: `codeops`) |

### 5.2 Dockerfile

**File:** `Dockerfile`

- Base: `eclipse-temurin:21-jre-alpine`
- Non-root user: `appuser`
- Exposed port: 8099
- Entry: `java -jar app.jar`

### 5.3 Application Profiles

| Profile | Database | DDL | Purpose |
|---------|----------|-----|---------|
| dev (default) | PostgreSQL localhost:5438 | update | Local development |
| prod | env var `DATABASE_URL` | validate | Production (Flyway enabled) |
| test | H2 in-memory (PostgreSQL mode) | create-drop | Unit tests |
| integration | PostgreSQL (Testcontainers) | create-drop | Integration tests |

### 5.4 Logging

**File:** `src/main/resources/logback-spring.xml`

| Profile | Format | Level |
|---------|--------|-------|
| dev | Human-readable console | DEBUG |
| prod | JSON (LogstashEncoder) | INFO |
| test | Console | WARN |

### 5.5 External Service URLs

| Property | Default (dev) | Purpose |
|----------|---------------|---------|
| `codeops.services.server-url` | `http://localhost:8095` | CodeOps-Server |
| `codeops.services.registry-url` | `http://localhost:8096` | Registry service |
| `codeops.services.vault-url` | `http://localhost:8097` | Vault service |
| `codeops.services.logger-url` | `http://localhost:8098` | Logger service |

---

## 6. Security Architecture

### 6.1 Filter Chain Order

1. **RequestCorrelationFilter** (`@Order(HIGHEST_PRECEDENCE)`) — X-Correlation-ID generation/reuse, MDC setup
2. **RateLimitFilter** — 100 req/60s per IP sliding window, 429 response
3. **JwtAuthFilter** — Bearer token extraction, HMAC-SHA validation, SecurityContext setup

### 6.2 Public Endpoints

- `GET /api/v1/courier/health`
- `GET /swagger-ui.html`, `/swagger-ui/**`
- `GET /v3/api-docs/**`, `/v3/api-docs.yaml`

### 6.3 Security Headers

- CSP: `default-src 'self'; frame-ancestors 'none'`
- HSTS: max-age 31536000s, include subdomains
- Frame Options: DENY
- Session: STATELESS

### 6.4 CORS Configuration

- Origins: configurable via `codeops.cors.allowed-origins` (default: `http://localhost:3000`)
- Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
- Headers: Authorization, Content-Type, X-Team-ID, X-Correlation-ID
- Credentials: enabled
- Preflight cache: 3600s

### 6.5 JWT Claims

| Claim | Type | Usage |
|-------|------|-------|
| sub | UUID | User ID (principal) |
| email | String | User email (credentials) |
| roles | List\<String\> | Mapped to `ROLE_*` authorities |
| teamIds | List\<UUID\> | Available team memberships |
| teamRoles | Map\<UUID, String\> | Per-team role assignments |

---

## 7. Entity Layer

### 7.1 Base Entity

**File:** `entity/BaseEntity.java` — `@MappedSuperclass`

All entities extend BaseEntity providing:
- `id` (UUID, `@GeneratedValue(strategy = GenerationType.UUID)`)
- `createdAt` (Instant, `@PrePersist`)
- `updatedAt` (Instant, `@PrePersist` + `@PreUpdate`)

### 7.2 Entities

| Entity | Table | Key Relationships | Unique Constraints |
|--------|-------|-------------------|--------------------|
| Collection | `collections` | → Folder (1:N), → EnvironmentVariable (1:N) | {team_id, name} |
| Folder | `folders` | → Collection (N:1), → Folder self-ref (N:1), → Request (1:N) | — |
| Request | `requests` | → Folder (N:1), → Headers/Params/Body/Auth/Scripts (1:N/1:1) | — |
| RequestHeader | `request_headers` | → Request (N:1) | — |
| RequestParam | `request_params` | → Request (N:1) | — |
| RequestBody | `request_bodies` | → Request (1:1) | — |
| RequestAuth | `request_auths` | → Request (1:1) | — |
| RequestScript | `request_scripts` | → Request (N:1) | {request_id, script_type} |
| Environment | `environments` | → EnvironmentVariable (1:N) | {team_id, name} |
| EnvironmentVariable | `environment_variables` | → Environment (N:1), → Collection (N:1) | — |
| GlobalVariable | `global_variables` | — | {team_id, variable_key} |
| CollectionShare | `collection_shares` | → Collection (N:1) | {collection_id, shared_with_user_id} |
| Fork | `forks` | → Collection source (N:1), → Collection forked (1:1) | — |
| MergeRequest | `merge_requests` | → Fork (N:1), → Collection target (N:1) | — |
| RunResult | `run_results` | → RunIteration (1:N) | — |
| RunIteration | `run_iterations` | → RunResult (N:1) | — |
| RequestHistory | `request_history` | — (denormalized) | — |
| CodeSnippetTemplate | `code_snippet_templates` | — | {language} |

### 7.3 Enums

| Enum | Values |
|------|--------|
| AuthType | NO_AUTH, API_KEY, BEARER_TOKEN, BASIC_AUTH, OAUTH2_AUTHORIZATION_CODE, OAUTH2_CLIENT_CREDENTIALS, OAUTH2_IMPLICIT, OAUTH2_PASSWORD, JWT_BEARER, INHERIT_FROM_PARENT |
| BodyType | NONE, FORM_DATA, X_WWW_FORM_URLENCODED, RAW_JSON, RAW_XML, RAW_HTML, RAW_TEXT, RAW_YAML, BINARY, GRAPHQL |
| CodeLanguage | CURL, PYTHON_REQUESTS, JAVASCRIPT_FETCH, JAVASCRIPT_AXIOS, JAVA_HTTP_CLIENT, JAVA_OKHTTP, CSHARP_HTTP_CLIENT, GO, RUBY, PHP, SWIFT, KOTLIN |
| HttpMethod | GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS |
| RunStatus | PENDING, RUNNING, COMPLETED, FAILED, CANCELLED |
| ScriptType | PRE_REQUEST, POST_RESPONSE |
| SharePermission | VIEWER, EDITOR, ADMIN |

---

## 8. Repository Layer

18 repositories extending `JpaRepository<Entity, UUID>`. All 99 custom methods are derived queries (no `@Query` annotations). 5 paginated methods. 10 delete methods. 13 exists/count methods.

| Repository | Entity | Custom Methods | Notable |
|------------|--------|----------------|---------|
| CollectionRepository | Collection | 8 | Paginated, search by name |
| FolderRepository | Folder | 5 | Null parent check for root folders |
| RequestRepository | Request | 2 | Ordered by sortOrder |
| RequestHeaderRepository | RequestHeader | 2 | — |
| RequestParamRepository | RequestParam | 2 | — |
| RequestBodyRepository | RequestBody | 2 | — |
| RequestAuthRepository | RequestAuth | 2 | — |
| RequestScriptRepository | RequestScript | 3 | By scriptType |
| EnvironmentRepository | Environment | 5 | Active environment lookup |
| EnvironmentVariableRepository | EnvironmentVariable | 5 | Enabled-only filter |
| GlobalVariableRepository | GlobalVariable | 4 | Upsert by key |
| CollectionShareRepository | CollectionShare | 5 | Delete by composite |
| ForkRepository | Fork | 4 | Exists check for duplicate forks |
| MergeRequestRepository | MergeRequest | 4 | Filter by status |
| RunResultRepository | RunResult | 4 | Paginated, by status |
| RunIterationRepository | RunIteration | 1 | Ordered by iteration number |
| RequestHistoryRepository | RequestHistory | 8 | Temporal queries, 3 paginated |
| CodeSnippetTemplateRepository | CodeSnippetTemplate | 3 | By language |

---

## 9. Service Layer

22 services with ~180 public methods. All annotated with `@Service`, `@Slf4j`, `@RequiredArgsConstructor`.

### 9.1 Core CRUD Services

| Service | Methods | Key Responsibilities |
|---------|---------|---------------------|
| CollectionService | 8 | CRUD + duplicate + search; team validation |
| FolderService | 9 | CRUD + tree + reorder + move; circular reference detection |
| RequestService | 14 | CRUD + components (headers/params/body/auth/scripts) + reorder + move + duplicate |
| EnvironmentService | 9 | CRUD + activate/deactivate + clone + variable management; secret masking |
| VariableService | 11 | Global variables CRUD + variable resolution across scopes (Global < Collection < Environment < Local) |

### 9.2 Feature Services

| Service | Methods | Key Responsibilities |
|---------|---------|---------------------|
| RequestProxyService | 3 | HTTP proxy execution; redirect handling (max 5 hops); auth resolution; history recording |
| GraphQLService | 4 | Query execution + introspection + validation + formatting |
| CollectionRunnerService | ~7 | Collection run orchestration; iteration tracking; assertion evaluation |
| CodeGenerationService | ~3 | Code snippet generation for 12 languages from templates |
| HistoryService | 9 | Request history CRUD + search + cleanup + pagination |
| ShareService | 9 | Collection sharing with permission hierarchy (VIEWER < EDITOR < ADMIN) |
| ForkService | 4 | Collection forking; duplicate prevention |
| MergeService | 4 | Merge request lifecycle; conflict detection |
| ImportService | 1 | Format routing + auto-detection (Postman/OpenAPI/cURL) |
| ExportService | 3 | Export as Postman v2.1, OpenAPI 3.0.3, or native JSON |
| ScriptEngineService | 2 | GraalVM JavaScript sandbox for pre-request/post-response scripts; pm.* API |

### 9.3 Support Services

| Service | Purpose |
|---------|---------|
| AuthResolverService | Resolves authentication config from request → folder → collection inheritance chain |
| PostmanImporter | Parses Postman v2.1 JSON collections |
| OpenApiImporter | Parses OpenAPI 3.x JSON/YAML specs |
| CurlImporter | Parses cURL commands |
| DataFileParser | Parses CSV/JSON data files for collection runner iterations |
| ScriptContext | POJO holding script execution state (variables, request/response data, assertions) |

---

## 10. Controller Layer

13 controllers. All authenticated endpoints require `@PreAuthorize("hasRole('ADMIN')")` and `@RequestHeader("X-Team-ID")`.

### 10.1 Endpoint Summary

| Controller | Base Path | Endpoints | Auth |
|------------|-----------|-----------|------|
| HealthController | `/health` | 1 GET | Public |
| CollectionController | `/collections` | 12 (5 GET, 1 POST, 1 PUT, 1 DELETE, 3 nested) | ADMIN |
| FolderController | `/folders` | 8 (3 GET, 1 POST, 2 PUT, 1 DELETE, 1 reorder) | ADMIN |
| RequestController | `/requests` | 13 (2 GET, 2 POST, 7 PUT, 1 DELETE, 1 send) | ADMIN |
| EnvironmentController | `/environments` | 10 (4 GET, 1 POST, 3 PUT, 1 DELETE, 1 clone) | ADMIN |
| VariableController | `/variables/global` | 4 (1 GET, 2 POST, 1 DELETE) | ADMIN |
| ProxyController | `/proxy` | 2 (2 POST) | ADMIN |
| HistoryController | `/history` | 7 (5 GET, 2 DELETE) | ADMIN |
| ShareController | (nested under collections) | 5 (2 GET, 1 POST, 1 PUT, 1 DELETE) | ADMIN |
| ImportController | `/import` | 3 (3 POST) | ADMIN |
| GraphQLController | `/graphql` | 4 (4 POST) | ADMIN |
| RunnerController | `/runner` | 7 (4 GET, 2 POST, 1 DELETE) | ADMIN |
| CodeGenerationController | `/codegen` | 3 (1 GET, 2 POST) | ADMIN |
| **Total** | | **79** | |

---

## 11. DTO Layer

### 11.1 Request DTOs (31)

All request DTOs are Java records with Jakarta validation annotations (`@NotBlank`, `@NotNull`, `@NotEmpty`, `@Min`, `@Max`, `@Size`).

Key request DTOs:
- `CreateCollectionRequest(name, description, authType, authConfig)`
- `CreateFolderRequest(collectionId, parentFolderId, name, description, sortOrder)`
- `CreateRequestRequest(folderId, name, description, method, url, sortOrder)`
- `SendRequestProxyRequest(method, url, headers, body, bodyType, authConfig, environmentId, saveToHistory, timeoutMs, followRedirects)`
- `ExecuteGraphQLRequest(url, query, variables, operationName, headers, authConfig, environmentId)`
- `StartCollectionRunRequest(collectionId, environmentId, iterationCount, delayBetweenRequestsMs, dataFile, dataFileContent)`
- `ImportCollectionRequest(format, content)`
- `GenerateCodeRequest(requestId, language, environmentId)`

### 11.2 Response DTOs (29)

All response DTOs are Java records. Key DTOs:
- `ProxyResponse(statusCode, statusText, headers, responseBody, responseTimeMs, responseSizeBytes, contentType, redirectChain, historyId)`
- `GraphQLResponse(httpResponse, schema)`
- `RunResultResponse(id, teamId, collectionId, environmentId, status, totalRequests, passedRequests, failedRequests, totalAssertions, passedAssertions, failedAssertions, totalDurationMs, iterationCount, delayBetweenRequestsMs, dataFilename, startedAt, completedAt, startedByUserId, createdAt)`
- `PageResponse<T>(content, page, size, totalElements, totalPages, last)`

### 11.3 Mapper Layer (13)

MapStruct mappers with `@Mapping(target = "isXxx", source = "xxx")` for boolean fields affected by Lombok's JavaBeans naming:
- `isShared` ← `shared` (CollectionMapper)
- `isActive` ← `active` (EnvironmentMapper)
- `isSecret` ← `secret` (EnvironmentVariableMapper, GlobalVariableMapper)
- `isEnabled` ← `enabled` (EnvironmentVariableMapper, GlobalVariableMapper, RequestHeaderMapper, RequestParamMapper)

---

## 12. Exception Handling

### 12.1 Exception Hierarchy

```
RuntimeException
└── CourierException (base, 500)
    ├── NotFoundException (404)
    ├── ValidationException (400)
    └── AuthorizationException (403)
```

### 12.2 GlobalExceptionHandler (14 handlers)

| Exception | HTTP Status | Response Message |
|-----------|-------------|------------------|
| NotFoundException | 404 | Exception message |
| ValidationException | 400 | Exception message |
| AuthorizationException | 403 | Exception message |
| EntityNotFoundException (JPA) | 404 | "Resource not found" |
| IllegalArgumentException | 400 | "Invalid request" |
| AccessDeniedException (Spring) | 403 | "Access denied" |
| MethodArgumentNotValidException | 400 | Aggregated field errors |
| HttpMessageNotReadableException | 400 | "Malformed request body" |
| NoResourceFoundException | 404 | "Resource not found" |
| MissingServletRequestParameterException | 400 | "Missing required parameter: '{name}'" |
| MethodArgumentTypeMismatchException | 400 | "Invalid value for parameter '{name}'" |
| MissingRequestHeaderException | 400 | "Missing required header: '{name}'" |
| HttpRequestMethodNotSupportedException | 405 | "HTTP method '{method}' is not supported" |
| Exception (catch-all) | 500 | "An internal error occurred" |

---

## 13. Configuration

| Config Class | Purpose |
|--------------|---------|
| SecurityConfig | Filter chain, public paths, security headers, session policy |
| CorsConfig | CORS policy (origins, methods, headers, credentials) |
| HttpClientConfig | Java HttpClient for proxy (30s timeout, HTTP/1.1, no auto-redirect) |
| RestTemplateConfig | RestTemplate for sibling services (5s connect, 10s read) |
| AsyncConfig | Thread pool (5-10 threads, 100 queue, CallerRunsPolicy) |
| OpenApiConfig | Swagger/OpenAPI metadata, JWT bearer auth scheme |
| WebMvcConfig | Registers LoggingInterceptor for `/api/**` |
| LoggingInterceptor | Pre/post request logging with status-based levels |
| RequestCorrelationFilter | X-Correlation-ID management, MDC population |
| DataSeeder | Dev profile seed data (collection, folders, requests, environment, variables) |
| JwtProperties | `codeops.jwt.secret` binding |
| ServiceUrlProperties | External service URLs binding |
| AppConstants | All magic numbers centralized |

---

## 14. Test Inventory

### 14.1 Test Distribution

| Layer | Files | Methods | Pattern |
|-------|-------|---------|---------|
| Controller tests | 13 | 167 | `@WebMvcTest` + `TestSecurityConfig` + `@MockBean` |
| Service tests | 22 | 480 | `@ExtendWith(MockitoExtension)` + `@Mock` + `@InjectMocks` |
| Security tests | 5 | 21 | Unit tests for filters, validators, utils |
| Config tests | 3 | 6 | Unit tests for constants, interceptor, correlation filter |
| Entity tests | 1 | 3 | BaseEntity lifecycle |
| Exception tests | 2 | 19 | Exception hierarchy + handler |
| DTO tests | 1 | 2 | PageResponse conversion |
| **Total** | **46** | **680** | |

### 14.2 Integration Tests

**Count: 0** — `application-integration.yml` and Testcontainers dependency are configured but no `@SpringBootTest` integration tests exist.

---

## 15. Environment Variables

| Variable | Profile | Purpose |
|----------|---------|---------|
| `DATABASE_URL` | prod | PostgreSQL connection string |
| `DATABASE_USERNAME` | prod | Database username |
| `DATABASE_PASSWORD` | prod | Database password |
| `JWT_SECRET` | prod | JWT signing secret (min 32 chars) |
| `CORS_ALLOWED_ORIGINS` | prod | Comma-separated allowed origins |
| `CODEOPS_SERVER_URL` | prod | CodeOps-Server base URL |
| `CODEOPS_REGISTRY_URL` | prod | Registry service base URL |
| `CODEOPS_VAULT_URL` | prod | Vault service base URL |
| `CODEOPS_LOGGER_URL` | prod | Logger service base URL |

---

## 16. Known Issues & Technical Debt

1. **No integration tests** — Testcontainers dependency and profile exist but are unused
2. **Script timeout mismatch** — `AppConstants.SCRIPT_TIMEOUT_SECONDS = 5` vs `ScriptEngineService` uses 10s
3. **In-memory rate limiter** — `ConcurrentHashMap` state lost on restart; not shared across instances
4. **No caching** — Frequently-read collections/environments hit the database on every request
5. **No circuit breaker** — External service calls via RestTemplate have no resilience patterns
6. **No soft-delete** — Hard deletes with no audit trail for deleted resources
7. **No message broker** — No Kafka/RabbitMQ for async operations
8. **MergeRequest.status is String** — Should be an enum for type safety (other status fields use enums)

---

## 17. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     CodeOps-Courier                          │
│                     Port 8099                                │
├─────────────────────────────────────────────────────────────┤
│  Filters: Correlation → RateLimit → JwtAuth                 │
├─────────────────────────────────────────────────────────────┤
│  Controllers (13)                                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │Collection│ │ Folder   │ │ Request  │ │Environmt │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │  Proxy   │ │ History  │ │ GraphQL  │ │  Runner  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │  Share   │ │ Import   │ │ CodeGen  │ │ Variable │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐                                                │
│  │ Health   │ (public)                                       │
│  └──────────┘                                                │
├─────────────────────────────────────────────────────────────┤
│  Services (22) + Mappers (13)                                │
├─────────────────────────────────────────────────────────────┤
│  Repositories (18) — Spring Data JPA (derived queries only)  │
├─────────────────────────────────────────────────────────────┤
│  Entities (17) — BaseEntity (UUID PK, audit timestamps)      │
└───────────────────────┬─────────────────────────────────────┘
                        │
                   ┌────▼────┐
                   │PostgreSQL│
                   │Port 5438 │
                   └──────────┘
```

---

## 18. Scorecard Reference

See `CodeOps-Courier-Scorecard.md` for the detailed quality scorecard.

**Overall Score: 92/100**
