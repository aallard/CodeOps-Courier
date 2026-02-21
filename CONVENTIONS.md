I am an AI-first developer producing code at 500x traditional speed. AI writes 100% of production code. Never give traditional time estimates, never phase work by severity/priority, never suggest "next sprint" or "backlog." Every fix, feature, or task is completed in a single pass — there is no cost/time justification for deferral. When estimating effort, use proven AI-first benchmarks: we've done over 200k lines of code per hour. Traditional software development assumptions do not apply to my workflow.

NEVER assume, infer, or guess about any codebase. You have no filesystem access. Before generating any code, tests, fixes, prompts, or recommendations that touch a codebase, you MUST request: (1) A current comprehensive audit that was produced using our Claude Audit Template for every project/codebase involved, and (2) The OpenAPI.yaml (also created by the audit template) for every service involved, so you understand the full REST API surface. If the work touches multiple projects, request audits and OpenAPI specs for ALL of them — never leave any out. Do not proceed until you have these. When I provide these files, also ask for their filesystem paths so you can reference them in any Claude Code prompts you generate. Both you and Claude Code must work from the same verified source of truth — never from memory, conversation context, or inference.

All code — features, fixes, remediations, anything — ships with tests in the same pass. 100% code coverage is mandatory, not aspirational. This includes both unit and integration tests.  All tests are never a follow-up task.  We never use Flyway during development.  Flyway can cause significant delays when stopping and restarting services, which is typical during development.  We only use Flyway when we move a project into production.  Before that, we use Hibernate.  Likewise, for production we would normally require strong passwords (length, special characters, numbers, etc), but during development, when repeatedly testing, we want minimal requirements to make logins fast and easy.

All code must have documentation comments on every class/module and every public method/function (excluding DTOs, entities, and generated code). Java uses Javadoc, TypeScript/JavaScript uses TSDoc/JSDoc, Dart uses DartDoc, C#/.NET uses XML Doc Comments. Documentation ships in the same pass as the code.   All software projects must have centralized logging.

All prompts to Claude Code must be .md file artifacts.  Every prompt must begin with:   "STOP:  Before writing ANY code, read these files completely: 1. ~/Documents/Github/<project folder>/openapi.yaml— The OpenAPI spec defines every field name, type, enum value, and endpoint path. Your code must match it exactly. 2. ~/Documents/Github/<project folder>/<project name>-Audit.md — The server audit shows actual entity relationships, repository methods, and validation rules. 3. ~/Documents/Github/<project folder>/<project name>-Architecture.md — The architecture spec defines all routes, widgets, services, and the project structure. Do not rely on the descriptions in this prompt alone. If this prompt conflicts with the source files, the source files win."  If any of these files are missing, STOP, and ask for them before proceeding.

Each prompt must end with: "Compile, Run, Test, Commit, Push to Github".  Your prompts will also end with a template that Claude Code will use as a report format to give back the summary of the work it performed during the prompt.  That report template will include the Git commit hash.

Make sure the entire contents of these User Preferences are included in the root directory of every project in a file named CONVENTIONS.md.  If that file exists but does not contain these directions, prepend them to the beginning of the file.  Otherwise create the file and put this in as the content.

---

## CodeOps-Courier Specific Conventions

- **Port:** 8099 (application), 5438 (PostgreSQL)
- **Package:** `com.codeops.courier`
- **API Prefix:** `/api/v1/courier/`
- **JWT:** Validate only — never issue tokens. Shared secret with CodeOps-Server.
- **Security:** `@PreAuthorize("hasRole('ADMIN')")` — never just `hasAuthority` alone.
- **BaseEntity:** All entities extend BaseEntity (UUID PK, createdAt, updatedAt).
- **Exception hierarchy:** CourierException -> NotFoundException, ValidationException, AuthorizationException.
- **Error responses:** `ErrorResponse(int status, String message)` — never expose internals in 500s.
