# CodeOps-Courier — Quality Scorecard

**Audit Date:** 2026-02-21T01:14:37Z
**Branch:** main
**Commit:** 4acf54b9f93dd0814b0de8823586e7a75b23d97a

---

## Security (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| SEC-01 Auth on all mutation endpoints | 2 | N/A — no mutation endpoints exist yet. Skeleton only has health. |
| SEC-02 No hardcoded secrets in source | 2 | 0 hardcoded secrets. Dev defaults use `${ENV_VAR:default}` pattern. |
| SEC-03 Input validation on all request DTOs | 2 | N/A — no request DTOs exist yet. |
| SEC-04 CORS not using wildcards | 2 | Explicit origin list from config property. No wildcards. |
| SEC-05 Encryption key not hardcoded | 1 | Dev profile has fallback default. Prod requires env var (no default). |
| SEC-06 Security headers configured | 2 | CSP, HSTS (1yr, includeSubDomains), X-Frame-Options DENY, X-Content-Type-Options. |
| SEC-07 Rate limiting present | 2 | RateLimitFilter: 100 req/60s per IP on `/api/v1/courier/**`. |
| SEC-08 SSRF protection on outbound URLs | 0 | **No SSRF protection.** RestTemplate exists but no URL validation. |
| SEC-09 Token revocation / logout | 0 | **No token blacklist or revocation mechanism.** |
| SEC-10 Password complexity enforcement | 2 | N/A — Courier validates JWTs only, no user registration. |

**Security Score: 15 / 20 (75%)**

---

## Data Integrity (8 checks, max 16)

| Check | Score | Notes |
|---|---|---|
| DAT-01 All enum fields use @Enumerated(STRING) | 2 | N/A — no enum fields on entities. |
| DAT-02 Database indexes on FK columns | 2 | N/A — no FK columns (no concrete entities). |
| DAT-03 Nullable constraints on required fields | 2 | BaseEntity: id, createdAt, updatedAt all `nullable=false`. |
| DAT-04 Optimistic locking (@Version) | 0 | **No @Version field on BaseEntity.** |
| DAT-05 No unbounded queries | 2 | N/A — no repositories. |
| DAT-06 No in-memory filtering of DB results | 2 | N/A — no service code. |
| DAT-07 Proper relationship mapping | 2 | N/A — no relationships. |
| DAT-08 Audit timestamps on entities | 2 | BaseEntity has @PrePersist/@PreUpdate with createdAt/updatedAt. |

**Data Integrity Score: 14 / 16 (87.5%)**

---

## API Quality (8 checks, max 16)

| Check | Score | Notes |
|---|---|---|
| API-01 Consistent error responses (GlobalExceptionHandler) | 2 | 15 exception handlers, all return `ErrorResponse(status, message)`. |
| API-02 Error messages sanitized | 2 | 500s return generic "An internal error occurred". Stack traces logged server-side only. |
| API-03 Audit logging on mutations | 2 | N/A — no mutation endpoints. |
| API-04 Pagination on list endpoints | 1 | `PageResponse<T>` helper ready but no list endpoints exist. |
| API-05 Correct HTTP status codes | 2 | Health returns 200 OK with `ResponseEntity.ok()`. |
| API-06 OpenAPI / Swagger documented | 2 | Springdoc configured, Swagger UI accessible, bearerAuth scheme defined. |
| API-07 Consistent DTO naming | 1 | `ErrorResponse`, `PageResponse` exist. No request/response DTOs yet. |
| API-08 File upload validation | 2 | N/A — no file upload endpoints. |

**API Quality Score: 14 / 16 (87.5%)**

---

## Code Quality (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| CQ-01 No getReferenceById | 2 | 0 occurrences. |
| CQ-02 Consistent exception hierarchy | 2 | `CourierException` → `NotFoundException`, `ValidationException`, `AuthorizationException`. |
| CQ-03 No TODO/FIXME/HACK | 2 | 0 occurrences in source. |
| CQ-04 Constants centralized | 2 | `AppConstants` with API_PREFIX, page sizes, rate limit params, service name. |
| CQ-05 Async exception handling | 2 | `AsyncConfig` implements `AsyncConfigurer` with `AsyncUncaughtExceptionHandler`. |
| CQ-06 RestTemplate injected (not new'd) | 2 | Bean-configured via `RestTemplateBuilder`. No `new RestTemplate()`. |
| CQ-07 Logging present | 2 | 13 Logger/Slf4j declarations across source. All security, config, and exception classes logged. |
| CQ-08 No raw exception messages to clients | 2 | 0 occurrences of `ex.getMessage()` in controllers. |
| CQ-09 Doc comments on classes | 2 | All 26 source classes have Javadoc (DTOs/entities excluded per conventions). |
| CQ-10 Doc comments on public methods | 2 | All public methods in controllers, security, and config classes documented. |

**Code Quality Score: 20 / 20 (100%)**

---

## Test Quality (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| TST-01 Unit test files | 2 | 13 test files. |
| TST-02 Integration test files | 0 | **0 integration test files.** Testcontainers dependency unused. |
| TST-03 Real database in ITs | 0 | **No Testcontainers usage.** |
| TST-04 Source-to-test ratio | 2 | 13 test files for 6 testable source categories (controller, security, config, exception, entity, dto). |
| TST-05 Code coverage >= 80% | 1 | 79.6% (just under 80% threshold). |
| TST-06 Test config exists | 2 | `application-test.yml` in both `src/main` and `src/test`. |
| TST-07 Security tests | 1 | 3 security-related assertions (SecurityConfigTest). No `@WithMockUser`. |
| TST-08 Auth flow e2e | 0 | **No end-to-end auth flow tests.** |
| TST-09 DB state verification in ITs | 0 | **No integration tests.** |
| TST-10 Total @Test methods | 2 | 53 test methods. |

**Test Quality Score: 10 / 20 (50%)**

---

## Infrastructure (6 checks, max 12)

| Check | Score | Notes |
|---|---|---|
| INF-01 Non-root Dockerfile | 2 | `addgroup` + `adduser` + `USER appuser`. |
| INF-02 DB ports localhost only | 2 | `127.0.0.1:5438:5432` — not exposed to 0.0.0.0. |
| INF-03 Env vars for prod secrets | 2 | 9 `${...}` references in prod config (no defaults). |
| INF-04 Health check endpoint | 2 | Custom `/api/v1/courier/health` returning JSON. |
| INF-05 Structured logging | 2 | LogstashEncoder configured for prod profile. |
| INF-06 CI/CD config | 0 | **No CI/CD pipeline configuration.** |

**Infrastructure Score: 10 / 12 (83.3%)**

---

## Scorecard Summary

```
Category             | Score | Max |    %
Security             |   15  |  20 |  75%
Data Integrity       |   14  |  16 |  88%
API Quality          |   14  |  16 |  88%
Code Quality         |   20  |  20 | 100%
Test Quality         |   10  |  20 |  50%
Infrastructure       |   10  |  12 |  83%
OVERALL              |   83  | 104 |  80%

Grade: B (70-84%)
```

### Categories Below 60%

**Test Quality (50%)** — Failing checks:
- **TST-02 (0):** No integration test files
- **TST-03 (0):** No Testcontainers usage (dependency present but unused)
- **TST-08 (0):** No end-to-end auth flow tests
- **TST-09 (0):** No DB state verification in integration tests

### BLOCKING ISSUES (Score 0)

- **SEC-08:** No SSRF protection on outbound URLs — relevant when Courier starts making HTTP calls to user-provided URLs for API testing
- **SEC-09:** No token revocation/logout mechanism
- **TST-02/03/08/09:** No integration tests
- **INF-06:** No CI/CD pipeline
- **DAT-04:** No optimistic locking (@Version) on BaseEntity

*Note: Many zero scores are expected for a skeleton project. SEC-08 and SEC-09 should be addressed before the service handles user-provided URLs or requires session management.*
