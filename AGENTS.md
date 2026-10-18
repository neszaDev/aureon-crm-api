# AGENTS.md

Instructions for AI coding agents (and a quick reference for humans) working in this repository.
Keep this file the single source of truth; `CLAUDE.md` only imports it.

## What this is

Aureon CRM API: the backend of a multi-tenant CRM (Spring Boot 3.5 / Java 21 / PostgreSQL).
Every business is a **workspace** with members, roles and invitation links; its data is
isolated per request by a tenant boundary, changes are written to an append-only audit trail,
and the CRM features (pipeline stages, lead sources, companies, contacts, leads, deals, tasks and
activities so far) live under `/api/v1/workspaces/{workspaceId}/...`. The CRM specification, data
model, permission matrix and API contract are in [docs/crm/](docs/crm/README.md).

The repo was built from `spring-postgres-boilerplate`, so its accounts (JWT access tokens +
rotating refresh tokens, sign-in rate limits, admin user management), owner-scoped file uploads,
`test-results` example, Flyway migrations, Actuator/Prometheus and Docker setup are still here.
The Java package stays `com.example.boilerplate`. Architecture overview:
[docs/architecture.md](docs/architecture.md). Why the repo is set up this way:
[docs/plans/0001-repository-hardening.md](docs/plans/0001-repository-hardening.md).

Deeper guides, read the relevant one before changing that area:
[configuration](docs/configuration.md) · [database/migrations](docs/database.md) ·
[testing](docs/testing.md) · [CI](docs/ci.md) · [API](docs/api.md) · [deployment](docs/deployment.md)
· [CRM](docs/crm/README.md) (spec, data model, permissions, API contract)

## Layout

```
src/main/java/com/example/boilerplate/
  auth/        register/login/refresh/logout, JwtService, RefreshToken*, AuthProperties
  user/        User entity, /api/v1/users/me, admin user management, AdminBootstrap
  workspace/   workspaces, members, roles + permissions, WorkspaceContext, the access
               interceptor and tenant resolver; invitation/ (links, preview, accept, register)
  audit/       append-only audit trail (AuditTrail, AuditEvent, AuditChanges) and its read API
               (AuditEventController/Service: workspace-bound queries only)
  crm/         CRM features, one subpackage per area; settings/ = pipeline stages, lead sources;
               company/ = the first record type, the pattern for the others; contact/ adds
               tags and a sort on a joined path; lead/ adds a status lifecycle, money with
               a currency, two specification-ordered sorts and the conversion (one transaction
               under the lead's row lock, through the other services' create methods); deal/ adds stage moves with a
               history and the board (grouped totals per currency through the DealTotals
               repository fragment); task/ adds actions with their own audit events, edit rights for the
               assignee or the creator, a computed overdue flag and the reminders (which reuse the
               task and lead lists); activity/ adds the one record that is deleted (its audit event
               keeps the last state), an author instead of an owner with author-only edit rights,
               and the company filled in from the contact or deal; export/ = the CSV export of
               the four record lists (Exporter: permission, row cap, audit; CsvWriter, RecordRefs);
               insight/ = the dashboard and reports (Period: from/to days in the workspace zone;
               tenant-filtered JPQL aggregates, the funnel's native SQL in ReportQueries binding
               the workspace itself; the lists through the record services' mappers);
               demo/ = the opt-in demo workspace of the local and dev profiles (DemoDataSeeder:
               through the services as its members, then 21 of its 24 deals back-dated by SQL);
               common/ = what every record type shares (CrmRecord, ListParams, SortField,
               TextSearch, RecordLinks, LinkRefs, ...)
  testresult/  owner-scoped CRUD + summary (/api/v1/test-results)
  ratelimit/   in-memory sign-in/registration limits (Bucket4j), used by AuthService and
               InvitationService
  file/        owner-scoped uploads (/api/v1/files), FileStorage + LocalFileStorage
  security/    SecurityConfig (access rules), JwtAuthenticationFilter, CORS
  common/      AuditableEntity, WorkspaceOwnedEntity, GlobalExceptionHandler + ApiError,
               request-id filter, RequestPaths, validation constraints, Tokens, Texts
docker/        Dockerfile (local/build/runtime stages), compose.yml + override
infra/         Azure: main.bicep (one environment of both apps) + deploy.sh, docs/deployment.md
env/           .env.example (tracked); local .env (git-ignored), read by compose via make
scripts/       smoke-test.sh (prod-profile container check, CI + `make smoke`),
               ensure-docker.sh (make targets: start Docker Desktop if needed)
src/main/resources/
  application*.yml        profiles: local, dev, test, prod
  db/migration/V*__*.sql  Flyway, the only schema authority
src/test/java/.../
  **/*Test.java  unit tests (Surefire): no Spring context, no Docker
  **/*IT.java    integration tests (Failsafe): extend support/AbstractIntegrationTest, or
                 support/WorkspaceTestSupport for workspace endpoints
```

## Commands

| Task | Command |
|---|---|
| Format | `make fmt` (`./mvnw spotless:apply`) |
| Format check + compile | `make lint` |
| Unit tests | `make test` (`./mvnw test`) |
| Everything CI runs | `make verify` (`./mvnw verify`, needs Docker) |
| Run locally, hot reload | `make watch` |
| Run packaged image | `make up` / `make down` |

Always use `./mvnw`, never a system `mvn`. Run `make` to list every target.

## Definition of done

1. `./mvnw verify` passes: Spotless, `-Xlint:all -Werror`, unit tests, ITs against real Postgres.
2. New or changed behaviour is covered by a test: a `*Test` for logic, a `*IT` for HTTP,
   security, persistence or migrations.
3. `git status` shows only intended files. Never commit `target/`, logs, dumps, `env/.env`, zips,
   jars or patches.
4. Commit messages follow Conventional Commits (`fix(auth): ...`). The hooks in `.githooks/`
   check this once `make setup` has been run.

## Conventions

- **Package by feature.** A feature owns its controller, service, repository, entity and `dto/`.
- **Controller → service → repository.** Controllers validate (`@Valid`) and map. Services own
  `@Transactional` and business rules. Entities are never returned from controllers; use `dto/`
  records (or MapStruct for larger mappings).
- **Errors.** Throw `NotFoundException` / `ConflictException` (or add a specific exception)
  and map it in `GlobalExceptionHandler`. Responses always use the `ApiError` shape. Client
  mistakes must never produce a 500. A rule violation carries an `ErrorCode` (`ConflictException`,
  `ForbiddenException`, optionally `FieldValidationException`) that clients translate; a new code
  goes into the table in docs/crm/api.md (Conventions) and `ErrorCodeIT`.
- **Owner scoping.** Queries for user-owned data filter by the authenticated email
  (`findByIdAndOwnerEmail`). Another user's row is a 404, not a 403.
- **Workspace endpoints** live under `/api/v1/workspaces/{workspaceId}/...`. `WorkspaceAccessInterceptor`
  resolves the caller's membership (no membership: 404) into `WorkspaceContext`; the scope of every
  query comes from that context (Hibernate's `@TenantId` on entities that extend
  `WorkspaceOwnedEntity`), never from the path variable the controller receives or from a request
  body. Services check permissions with `WorkspaceAccess.require(Permission)` and owners or
  assignees with `ownerForCreate` / `ownerForUpdate` (the rule is in docs/crm/api.md, Conventions).
- **Not tenant-filtered:** `Workspace`, `WorkspaceMember`, `Invitation` and `AuditEvent` don't
  extend `WorkspaceOwnedEntity`. Inside a workspace route every query on them, including a JPQL
  join from a tenant-owned entity, must bind `WorkspaceContext.workspaceId()` explicitly. Outside
  one (an invitation link, the caller's own workspace list) the token hash or the email scopes it.
- **Audit changes:** an update's event stores `AuditChanges.recordDiff(before, after)` of the
  response DTOs (it drops `version`, `createdAt`, `updatedAt` and the components marked
  `@Derived`: what follows from other fields, such as `fullName`, `overdue`, a count, `archived`
  next to `archivedAt`) and nothing when that is empty; a
  create stores empty changes and the record's label; every delete (an activity, a pipeline
  stage, a lead source) stores `recordDiff(last, Map.of())`, the record's last state (every field
  as a `from`, `to` null), because nothing else keeps what was there. Entities assign money
  through `Money.scale2`, so `1250` from a request and `1250.00` from `numeric(15,2)` are not a
  change.
- **Record lists** use `JpaSpecificationExecutor.findAll(spec, ListParams.pageable(...))` and
  `TextSearch.pattern` with `cb.like(cb.lower(path), cb.lower(TextSearch.bound(cb, pattern)),
  '\\')`. Request text is always bound, never `cb.literal` (Hibernate writes that into the SQL;
  `NoInlineLiteralTest` fails on one). A column that must list nulls last in both directions adds
  `Ordering.nullsLast` to the spec and uses `ListParams.unsorted`: Spring Data's Criteria sorting
  throws on `Sort.Order.nullsLast()`; `Ordering.byPriority` (HIGH, MEDIUM, LOW) works the same way.
  Currency fields of requests take no `@IsoCurrency` (case-sensitive); `Currencies.resolve` checks.
  A `SortField` lists its `keys` (several for one sort, e.g. last then first name; a to-one path
  such as `company.name` is left-joined); a name key is `Key.ignoringCase` (Alpine's PostgreSQL
  collation puts upper case first). `ListParams` adds the id tie-breaker.
- **Record links** in request bodies (`companyId`, `sourceId`, ...) go through `RecordLinks` before
  saving (400 on that field); a member FK is `(workspace_id, owner_id) REFERENCES workspace_members
  (workspace_id, user_id) ON DELETE SET NULL (owner_id)`, so removing a member unassigns.
- **Native SQL** is not filtered by `@TenantId`: it must bind the workspace id explicitly, needs a
  cross-workspace test, and must never load workspace-owned entities.
- **`WorkspaceContext.runAs(...)`** (seeders, tests) only outside a transaction: Hibernate reads
  the tenant when the session opens, so `runAs` throws inside one.
- **Invitation tokens are never logged**, nor put in an error message or an audit event. Only
  their SHA-256 hash is stored, and a URI under `/api/v1/invitations/{token}` counts as the token.
- **Free text** from requests goes through `Texts.required(field, value)` / `Texts.optional(value)`:
  `@NotBlank` and `strip()` miss Unicode spaces such as U+3000 and U+00A0.
- **Error bodies** use `RequestPaths.safePath(request)` for `ApiError.path`, never the raw URI.
- **Request DTOs** use boxed types with `@NotNull` for required numbers and booleans: a primitive
  would turn an omitted field into `0` or `false` instead of a 400. Money fields take
  `@Schema(type = "string")` like the responses' money.
- **Response DTOs** put `@NotNull` on every component that is never null; with the primitives
  (`OpenApiConfig`) those are `required` in the OpenAPI spec, the rest optional (Jackson leaves
  nulls out), so the frontend's generated types say which fields it can count on.
- **Configuration** binds to validated `@ConfigurationProperties` records (`AuthProperties`,
  `CorsProperties`), not `@Value`. Validation failures must not echo secret values.
- **Formatting** is google-java-format via Spotless. Don't hand-format; run `make fmt`.

## Files that need extra care

| Path | Rule |
|---|---|
| `src/main/resources/db/migration/` | **Applied migrations are immutable.** Add `V{next}__description.sql`; never edit, rename or delete an existing one. `ddl-auto` stays `validate`. |
| `security/SecurityConfig.java`, `auth/` | Any change to access rules or token handling needs an IT (`SecurityIT`, `AuthFlowIT`). |
| `workspace/` (interceptor, `WorkspaceWebConfig`, tenant resolver), `common/entity/WorkspaceOwnedEntity` | The tenant boundary. Keep `WorkspaceEndpointsGuardIT` and `TenantIsolationIT` green; a new workspace controller is picked up by the guard automatically and must pass it unchanged. |
| `audit/`, `V8__audit_events.sql` | The trail is append-only (a trigger refuses `UPDATE`/`DELETE`). Write events with `AuditTrail` inside the transaction of the change, never separately. |
| `application-prod.yml` | No default values for secrets (`DB_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`). |
| `logback-spring.xml` | Prod must emit JSON to stdout. Confirm by starting the jar with `SPRING_PROFILES_ACTIVE=prod`. |
| `docker/` (Dockerfile, compose files) | Keep the runtime image non-root. Check with `make smoke`. Compose must run with `--project-directory .` (the `make` targets do this). |
| `.github/workflows/` | Checks live only in `ci.yml` and image publishing only in `publish-image.yml`; `cd-dev.yml` / `cd-main.yml` just call them. Don't duplicate logic or add debug workflows. |
| `pom.xml` | Every new dependency needs a stated reason in the commit message. Prefer Spring Boot–managed versions. |

## Forbidden shortcuts

- Skipping or weakening checks: `-DskipTests`, `@Disabled`, deleting assertions, loosening
  `-Werror`, or excluding files from Spotless to get a green build.
- Editing an applied Flyway migration, or `ddl-auto` other than `validate`.
- Widening `permitAll()` matchers or catching `JwtException` more broadly to "fix" a 401.
- Reading the workspace from a path variable or request body instead of `WorkspaceContext`, or
  skipping the interceptor for a workspace route.
- Committing secrets. `env/.env` is git-ignored; `env/.env.example` holds placeholders only.
- Force-pushing or committing directly to `dev` or `main`: use a branch and a PR into `dev`.
- Adding a framework or tool because another repo has it.

## Verifying changes

- **Docker must be running** for `*IT` tests (Testcontainers).
- **VS Code / JDT race:** with the VS Code Java extension open, `./mvnw clean verify` in the
  workspace can fail with `No qualifying bean of type 'UserMapper'`. The IDE rebuilds
  `target/classes` during Maven's run. This is not a code bug. Re-run without `clean`, or
  verify in a fresh clone.
- **API changes:** check the running app with `make up`, then Swagger UI at
  `http://localhost:8080/swagger-ui/index.html` (the host port is `APP_PORT`, default 8080).
