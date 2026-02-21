# CodeOps-Courier — Codebase Audit

**Audit Date:** 2026-02-21T01:14:37Z
**Branch:** main
**Commit:** 4acf54b9f93dd0814b0de8823586e7a75b23d97a CC-001: CodeOps-Courier project skeleton
**Auditor:** Claude Code (Automated)
**Purpose:** Zero-context reference for AI-assisted development
**Audit File:** CodeOps-Courier-Audit.md
**Scorecard:** CodeOps-Courier-Scorecard.md
**OpenAPI Spec:** CodeOps-Courier-OpenAPI.yaml

> This audit is the single source of truth for the CodeOps-Courier codebase.
> The OpenAPI spec (CodeOps-Courier-OpenAPI.yaml) is the source of truth for all endpoints, DTOs, and API contracts.
> An AI reading this audit + the OpenAPI spec should be able to generate accurate code
> changes, new features, tests, and fixes without filesystem access.

---

## 1. Project Identity

```
Project Name:         CodeOps-Courier
Repository URL:       https://github.com/aallard/CodeOps-Courier.git
Primary Language:     Java / Spring Boot 3.3.0
Java Version:         21 (running on Java 25)
Build Tool:           Maven (spring-boot-starter-parent 3.3.0)
Current Branch:       main
Latest Commit Hash:   4acf54b9f93dd0814b0de8823586e7a75b23d97a
Latest Commit Msg:    CC-001: CodeOps-Courier project skeleton
Audit Timestamp:      2026-02-21T01:14:37Z
```

---

## 2. Directory Structure

```
./CONVENTIONS.md
./docker-compose.yml
./Dockerfile
./pom.xml
./README.md
./src/main/java/com/codeops/courier/CourierApplication.java
./src/main/java/com/codeops/courier/config/AppConstants.java
./src/main/java/com/codeops/courier/config/AsyncConfig.java
./src/main/java/com/codeops/courier/config/CorsConfig.java
./src/main/java/com/codeops/courier/config/DataSeeder.java
./src/main/java/com/codeops/courier/config/JwtProperties.java
./src/main/java/com/codeops/courier/config/LoggingInterceptor.java
./src/main/java/com/codeops/courier/config/OpenApiConfig.java
./src/main/java/com/codeops/courier/config/RequestCorrelationFilter.java
./src/main/java/com/codeops/courier/config/RestTemplateConfig.java
./src/main/java/com/codeops/courier/config/ServiceUrlProperties.java
./src/main/java/com/codeops/courier/config/WebMvcConfig.java
./src/main/java/com/codeops/courier/controller/HealthController.java
./src/main/java/com/codeops/courier/dto/response/PageResponse.java
./src/main/java/com/codeops/courier/entity/BaseEntity.java
./src/main/java/com/codeops/courier/exception/AuthorizationException.java
./src/main/java/com/codeops/courier/exception/CourierException.java
./src/main/java/com/codeops/courier/exception/ErrorResponse.java
./src/main/java/com/codeops/courier/exception/GlobalExceptionHandler.java
./src/main/java/com/codeops/courier/exception/NotFoundException.java
./src/main/java/com/codeops/courier/exception/ValidationException.java
./src/main/java/com/codeops/courier/security/JwtAuthFilter.java
./src/main/java/com/codeops/courier/security/JwtTokenValidator.java
./src/main/java/com/codeops/courier/security/RateLimitFilter.java
./src/main/java/com/codeops/courier/security/SecurityConfig.java
./src/main/java/com/codeops/courier/security/SecurityUtils.java
./src/main/resources/application.yml
./src/main/resources/application-dev.yml
./src/main/resources/application-integration.yml
./src/main/resources/application-prod.yml
./src/main/resources/application-test.yml
./src/main/resources/logback-spring.xml
./src/test/java/com/codeops/courier/config/AppConstantsTest.java
./src/test/java/com/codeops/courier/config/LoggingInterceptorTest.java
./src/test/java/com/codeops/courier/config/RequestCorrelationFilterTest.java
./src/test/java/com/codeops/courier/controller/HealthControllerTest.java
./src/test/java/com/codeops/courier/dto/PageResponseTest.java
./src/test/java/com/codeops/courier/entity/BaseEntityTest.java
./src/test/java/com/codeops/courier/exception/CourierExceptionTest.java
./src/test/java/com/codeops/courier/exception/GlobalExceptionHandlerTest.java
./src/test/java/com/codeops/courier/security/JwtAuthFilterTest.java
./src/test/java/com/codeops/courier/security/JwtTokenValidatorTest.java
./src/test/java/com/codeops/courier/security/RateLimitFilterTest.java
./src/test/java/com/codeops/courier/security/SecurityConfigTest.java
./src/test/java/com/codeops/courier/security/SecurityUtilsTest.java
./src/test/resources/application-test.yml
```

Single-module Maven project. Source code at `src/main/java/com/codeops/courier/`. Packages: `config`, `controller`, `dto/response`, `entity`, `exception`, `security`. This is a skeleton — no domain entities, repositories, or services exist yet beyond infrastructure.

---

## 3. Build & Dependency Manifest

**Build file:** `pom.xml`

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | 3.3.0 (parent) | REST API framework |
| spring-boot-starter-data-jpa | 3.3.0 (parent) | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 (parent) | Security framework |
| spring-boot-starter-validation | 3.3.0 (parent) | Bean validation (Jakarta) |
| postgresql | runtime (parent) | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token parsing/validation |
| lombok | 1.18.42 | Annotation-based boilerplate reduction |
| mapstruct | 1.5.5.Final | DTO ↔ entity mapping |
| jackson-datatype-jsr310 | parent | Java 8+ date/time serialization |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | Swagger UI + OpenAPI spec generation |
| logstash-logback-encoder | 7.4 | JSON structured logging (prod) |
| spring-boot-starter-test | 3.3.0 (parent) | Test framework |
| spring-security-test | parent | Security test support |
| testcontainers postgresql | 1.19.8 | PostgreSQL in integration tests |
| testcontainers junit-jupiter | 1.19.8 | Testcontainers JUnit 5 integration |
| h2 | parent (test) | In-memory database for unit tests |
| mockito | 5.21.0 (override) | Java 25 compatibility |
| byte-buddy | 1.18.4 (override) | Java 25 compatibility |

**Build plugins:** maven-compiler-plugin (source/target 21, Lombok + MapStruct annotation processors), maven-surefire-plugin (--add-opens for Java 25), jacoco-maven-plugin (0.8.14, prepare-agent + report), spring-boot-maven-plugin.

```
Build:   mvn clean package -DskipTests
Test:    mvn test
Run:     mvn spring-boot:run
Package: mvn clean package
```

---

## 4. Configuration & Infrastructure Summary

- **`application.yml`** — `src/main/resources/application.yml`. Default profile: `dev`. Server port: 8099. `jpa.open-in-view: false`.
- **`application-dev.yml`** — `src/main/resources/application-dev.yml`. PostgreSQL at `localhost:5438/codeops_courier`, credentials `${DB_USERNAME:codeops}/${DB_PASSWORD:codeops}`. `ddl-auto: update`, Flyway disabled, `show-sql: true`. JWT secret has dev default. CORS origins: `localhost:3000,3200,5173`. Service URLs: 8095–8098. Logging: DEBUG for app/hibernate/security/web.
- **`application-prod.yml`** — `src/main/resources/application-prod.yml`. All config from env vars (no defaults). `ddl-auto: validate`, Flyway enabled. Logging: INFO/WARN.
- **`application-test.yml`** — `src/main/resources/application-test.yml`. H2 in-memory with PostgreSQL mode. `ddl-auto: create-drop`, Flyway disabled. Fixed test JWT secret. Logging: WARN.
- **`application-integration.yml`** — `src/main/resources/application-integration.yml`. PostgreSQL driver, `ddl-auto: create-drop`, Flyway disabled. Fixed integration JWT secret. Logging: WARN.
- **`logback-spring.xml`** — `src/main/resources/logback-spring.xml`. Profiles: `dev` (ANSI console with MDC), `prod` (JSON via LogstashEncoder), `test` (WARN-only console), `default` (fallback console).
- **`docker-compose.yml`** — PostgreSQL 16 Alpine on `127.0.0.1:5438`, container `codeops-courier-db`, DB `codeops_courier`, user/pass `codeops/codeops`. Volume: `codeops-courier-data`.
- **`Dockerfile`** — `eclipse-temurin:21-jre-alpine`, non-root `appuser`, EXPOSE 8099.
- **`.env`** — Does not exist. No `.env.example`. `.gitignore` exists.

**Connection map:**
```
Database: PostgreSQL, localhost, 5438, codeops_courier
Cache: None
Message Broker: None
External APIs: None (RestTemplate configured but unused)
Cloud Services: None
```

**CI/CD:** None detected.

---

## 5. Startup & Runtime Behavior

- **Entry point:** `com.codeops.courier.CourierApplication` — `@SpringBootApplication @EnableScheduling @EnableConfigurationProperties({JwtProperties, ServiceUrlProperties})`
- **@PostConstruct:** `JwtTokenValidator.validateSecret()` — validates JWT secret is >= 32 characters. Fails fast if misconfigured.
- **CommandLineRunner:** `DataSeeder` (dev profile only) — currently a no-op logging "No seed data configured yet."
- **Scheduled tasks:** `@EnableScheduling` present but no `@Scheduled` methods exist yet.
- **Health check:** `GET /api/v1/courier/health` → `{"status":"UP","service":"codeops-courier","timestamp":"..."}` (200 OK, no auth required)
- **Startup time:** ~1.2 seconds

---

## 6. Entity / Data Model Layer

### BaseEntity.java (MappedSuperclass — not a table)

```
=== BaseEntity.java ===
Table: N/A (@MappedSuperclass)
Primary Key: id: UUID (GenerationType.UUID, nullable=false, updatable=false)

Fields:
  - id: UUID [@Id @GeneratedValue(strategy=UUID) @Column(nullable=false, updatable=false)]
  - createdAt: Instant [@Column(name="created_at", nullable=false, updatable=false)]
  - updatedAt: Instant [@Column(name="updated_at", nullable=false)]

Relationships: None
Indexes: None
Validation: None
Auditing: @PrePersist sets createdAt+updatedAt (single Instant.now()), @PreUpdate sets updatedAt
```

**No concrete entities exist yet.** This is a skeleton project. All future entities will extend BaseEntity.

**Entity Relationship Summary:** None — no concrete entities.

---

## 7. Enum Definitions

No enums defined.

---

## 8. Repository Layer

No repositories defined.

---

## 9. Service Layer

No service classes defined. Domain services will be added in future tasks.

---

## 10. Security Architecture

### Authentication Flow
- **Method:** JWT validation-only (never issues tokens). Tokens issued by CodeOps-Server.
- **Algorithm:** HMAC-SHA256 via jjwt 0.12.6
- **Token claims extracted:** `sub` (userId UUID), `email`, `roles` (List<String>), `teamIds` (List<UUID>), `teamRoles` (Map<UUID,String>)
- **Filter chain flow:** Request → RequestCorrelationFilter → RateLimitFilter → JwtAuthFilter → SecurityConfig authorization
- **On valid token:** `SecurityContextHolder` populated with `UsernamePasswordAuthenticationToken(userId:UUID, email:String, ROLE_* authorities)`
- **On invalid/missing token:** Request proceeds unauthenticated; authorization rules reject if endpoint requires auth
- **Token revocation:** Not implemented (no blacklist)

### Authorization Model
- **Roles:** Extracted from JWT `roles` claim. Mapped to Spring authorities with `ROLE_` prefix.
- **Admin check:** `SecurityUtils.isAdmin()` — returns true for `ROLE_ADMIN` or `ROLE_OWNER`
- **Method security:** `@EnableMethodSecurity` active. Convention: `@PreAuthorize("hasRole('ADMIN')")` (never just `hasAuthority`).
- **No mutation endpoints exist yet**, so no `@PreAuthorize` annotations are in use.

### Security Filter Chain (order as registered)
1. `DisableEncodeUrlFilter` (Spring default)
2. `WebAsyncManagerIntegrationFilter` (Spring default)
3. `SecurityContextHolderFilter` (Spring default)
4. `HeaderWriterFilter` (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)
5. `CorsFilter`
6. `LogoutFilter`
7. **`JwtAuthFilter`** — Extracts Bearer token, validates, sets SecurityContext
8. **`RateLimitFilter`** — Per-IP rate limiting on `/api/v1/courier/**`
9. **`RequestCorrelationFilter`** — MDC correlation ID
10. `RequestCacheAwareFilter`
11. `SecurityContextHolderAwareRequestFilter`
12. `AnonymousAuthenticationFilter`
13. `SessionManagementFilter`
14. `ExceptionTranslationFilter`
15. `AuthorizationFilter`

### Public Paths (permitAll)
- `/api/v1/courier/health`
- `/swagger-ui.html`, `/swagger-ui/**`
- `/v3/api-docs/**`, `/v3/api-docs.yaml`

### Protected Paths
- `/api/**` → authenticated
- All other paths → authenticated

### CORS Configuration
- **Origins:** From `codeops.cors.allowed-origins` (dev: `localhost:3000,3200,5173`)
- **Methods:** GET, POST, PUT, DELETE, PATCH, OPTIONS
- **Allowed Headers:** Authorization, Content-Type, X-Team-ID, X-Correlation-ID
- **Exposed Headers:** Authorization
- **Credentials:** true
- **Max Age:** 3600s

### Encryption
- JWT signing: HMAC-SHA256 with shared secret from `codeops.jwt.secret`
- No at-rest encryption

### Password Policy
- N/A — Courier validates JWT tokens only, no user registration

### Rate Limiting
- **Scope:** Per-IP on `/api/v1/courier/**`
- **Strategy:** In-memory ConcurrentHashMap sliding window
- **Limit:** 100 requests per 60-second window
- **IP resolution:** X-Forwarded-For header (first entry), fallback to `remoteAddr`
- **On violation:** 429 JSON response `{"status":429,"message":"Rate limit exceeded. Try again later."}`

---

## 11. Notification / Messaging Layer

No notification, email, or webhook services exist.

---

## 12. Error Handling

**GlobalExceptionHandler** (`@RestControllerAdvice`):

| Exception Type | HTTP Status | Response Body | Client-visible? |
|---|---|---|---|
| `NotFoundException` | 404 | `ErrorResponse(404, ex.message)` | Yes (custom message) |
| `ValidationException` | 400 | `ErrorResponse(400, ex.message)` | Yes (custom message) |
| `AuthorizationException` | 403 | `ErrorResponse(403, ex.message)` | Yes (custom message) |
| `EntityNotFoundException` | 404 | `ErrorResponse(404, "Resource not found")` | Generic |
| `IllegalArgumentException` | 400 | `ErrorResponse(400, "Invalid request")` | Generic |
| `AccessDeniedException` | 403 | `ErrorResponse(403, "Access denied")` | Generic |
| `MethodArgumentNotValidException` | 400 | `ErrorResponse(400, field errors joined)` | Field-level |
| `HttpMessageNotReadableException` | 400 | `ErrorResponse(400, "Malformed request body")` | Generic |
| `NoResourceFoundException` | 404 | `ErrorResponse(404, "Resource not found")` | Generic |
| `MissingServletRequestParameterException` | 400 | `ErrorResponse(400, "Missing required parameter: ...")` | Param name |
| `MethodArgumentTypeMismatchException` | 400 | `ErrorResponse(400, "Invalid value for parameter ...")` | Param name+value |
| `MissingRequestHeaderException` | 400 | `ErrorResponse(400, "Missing required header: ...")` | Header name |
| `HttpRequestMethodNotSupportedException` | 405 | `ErrorResponse(405, "HTTP method ... not supported")` | Method name |
| `CourierException` (base) | 500 | `ErrorResponse(500, "An internal error occurred")` | Generic |
| `Exception` (catch-all) | 500 | `ErrorResponse(500, "An internal error occurred")` | Generic |

**ErrorResponse format:** `record ErrorResponse(int status, String message)` — no timestamp, no path.

Internal exception messages and stack traces are logged server-side (WARN for 4xx, ERROR for 5xx) but never exposed to clients for 500-level errors.

---

## 13. Test Coverage

- **Unit test files:** 13
- **Integration test files:** 0
- **Total @Test methods:** 53 (all unit)
- **JaCoCo coverage:** 79.6%
- **Framework:** JUnit 5 + Mockito 5.21.0 + Spring Boot Test
- **Test DB:** H2 in-memory with PostgreSQL mode (`application-test.yml`)
- **Test config:** `src/test/resources/application-test.yml`
- **Integration tests:** None yet (Testcontainers dependency available but unused)

---

## 14. Cross-Cutting Patterns & Conventions

- **Package structure:** `com.codeops.courier.{config,controller,dto.response,entity,exception,security}`
- **Base classes:** `BaseEntity` (UUID PK, createdAt, updatedAt)
- **Exception hierarchy:** `CourierException` → `NotFoundException`, `ValidationException`, `AuthorizationException`
- **Error response:** `record ErrorResponse(int status, String message)` — consistent across all handlers
- **Pagination:** `PageResponse<T>` record with `from(Page<T>)` factory method. Fields: content, page, size, totalElements, totalPages, isLast.
- **Constants:** `AppConstants` — API_PREFIX, DEFAULT_PAGE_SIZE (20), MAX_PAGE_SIZE (100), RATE_LIMIT_REQUESTS (100), RATE_LIMIT_WINDOW_SECONDS (60), SERVICE_NAME
- **Naming:** Controller methods are action verbs (`health()`). API prefix: `/api/v1/courier/`.
- **Security pattern:** `@PreAuthorize("hasRole('ADMIN')")` (per CONVENTIONS.md — never just `hasAuthority`)
- **Validation:** Jakarta Bean Validation on DTOs (when they exist). Service-level business rules throw `ValidationException`.
- **Logging:** SLF4J + Logback. `@Slf4j` Lombok annotation on some classes, explicit `LoggerFactory` on others. MDC keys: `correlationId`, `userId`, `teamId`, `requestPath`, `requestMethod`.
- **Documentation comments:** Javadoc on all classes and all public methods (excluding DTOs, entities, generated code per conventions). All 26 source files documented.

---

## 15. Known Issues, TODOs, and Technical Debt

No TODO, FIXME, HACK, or XXX comments found in source code.

---

## 16. OpenAPI Specification

Fetched from running application at `http://localhost:8099/v3/api-docs.yaml` and saved as `CodeOps-Courier-OpenAPI.yaml` (38 lines). Contains 1 endpoint: `GET /api/v1/courier/health`.

---

## 17. DATABASE SCHEMA — Live Audit

Database started and queried. **0 tables in public schema.** This is expected — `BaseEntity` is `@MappedSuperclass` (not a concrete table), and no domain entities exist yet. Hibernate `ddl-auto: update` will create tables when entities are added.

JPA model and database schema are in sync (both empty).

---

## 18. MESSAGE BROKER DETECTION

No message broker (Kafka, RabbitMQ, SQS/SNS) detected in this project.

---

## 19. CACHE DETECTION

No Redis or caching layer detected in this project.

---

## 20. ENVIRONMENT VARIABLE INVENTORY

| Variable | Required | Default | Used By | Purpose |
|---|---|---|---|---|
| `DB_USERNAME` | No | `codeops` | `application-dev.yml` | Database username |
| `DB_PASSWORD` | No | `codeops` | `application-dev.yml` | Database password |
| `JWT_SECRET` | Prod: Yes, Dev: No | Dev: 53-char default | `application-dev/prod.yml` | HMAC-SHA256 signing key |
| `DATABASE_URL` | Prod: Yes | None | `application-prod.yml` | JDBC connection URL |
| `DATABASE_USERNAME` | Prod: Yes | None | `application-prod.yml` | Database username |
| `DATABASE_PASSWORD` | Prod: Yes | None | `application-prod.yml` | Database password |
| `CORS_ALLOWED_ORIGINS` | Prod: Yes | None | `application-prod.yml` | Comma-separated origins |
| `CODEOPS_SERVER_URL` | Prod: Yes | None | `application-prod.yml` | CodeOps-Server base URL |
| `CODEOPS_REGISTRY_URL` | Prod: Yes | None | `application-prod.yml` | CodeOps-Registry base URL |
| `CODEOPS_VAULT_URL` | Prod: Yes | None | `application-prod.yml` | CodeOps-Vault base URL |
| `CODEOPS_LOGGER_URL` | Prod: Yes | None | `application-prod.yml` | CodeOps-Logger base URL |

**Warning:** Dev JWT secret is hardcoded in `application-dev.yml` but only as a dev default (overridden by `JWT_SECRET` env var). Not dangerous — dev profile only.

---

## 21. SERVICE DEPENDENCY MAP

Standalone service with no outbound service-to-service HTTP calls. `RestTemplate` bean is configured (5s connect, 10s read timeout) but not injected or used by any class. `ServiceUrlProperties` defines URLs for CodeOps-Server (8095), Registry (8096), Vault (8097), and Logger (8098) but no client code exists yet.

**Inbound dependencies:** None known. This service will be called by CodeOps-Client (Flutter desktop app) once Courier features are integrated.
