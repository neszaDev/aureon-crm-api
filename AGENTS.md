# AGENTS.md

Instructions for AI coding agents (and a quick reference for humans) working in this repository.
Keep this file the single source of truth; `CLAUDE.md` only imports it.

## What this is

A Spring Boot 3.5 / Java 21 REST API on PostgreSQL: JWT access tokens + rotating refresh
tokens, an owner-scoped `test-results` CRUD example, Flyway migrations, Actuator/Prometheus,
Docker. Architecture overview: [docs/architecture.md](docs/architecture.md). Why the repo is set up
this way: [docs/plans/0001-repository-hardening.md](docs/plans/0001-repository-hardening.md).

Deeper guides, read the relevant one before changing that area:
[configuration](docs/configuration.md) · [database/migrations](docs/database.md) ·
[testing](docs/testing.md) · [CI](docs/ci.md) · [API](docs/api.md)

## Layout

```
src/main/java/com/example/boilerplate/
  auth/        register/login/refresh/logout, JwtService, RefreshToken*, AuthProperties
  user/        User entity, /api/v1/users/me, MapStruct UserMapper
  testresult/  owner-scoped CRUD + summary (/api/v1/test-results)
  security/    SecurityConfig (access rules), JwtAuthenticationFilter, CORS
  common/      AuditableEntity, GlobalExceptionHandler + ApiError, request-id filter
docker/        Dockerfile (local/build/runtime stages), compose.yml + override
env/           .env.example (tracked); local .env (git-ignored), read by compose via make
scripts/       smoke-test.sh (prod-profile container check, used by CI and `make smoke`)
src/main/resources/
  application*.yml        profiles: local, dev, test, prod
  db/migration/V*__*.sql  Flyway, the only schema authority
src/test/java/.../
  **/*Test.java  unit tests (Surefire): no Spring context, no Docker
  **/*IT.java    integration tests (Failsafe): extend support/AbstractIntegrationTest
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
  mistakes must never produce a 500.
- **Owner scoping.** Queries for user-owned data filter by the authenticated email
  (`findByIdAndOwnerEmail`). Another user's row is a 404, not a 403.
- **Configuration** binds to validated `@ConfigurationProperties` records (`AuthProperties`,
  `CorsProperties`), not `@Value`. Validation failures must not echo secret values.
- **Formatting** is google-java-format via Spotless. Don't hand-format; run `make fmt`.

## Files that need extra care

| Path | Rule |
|---|---|
| `src/main/resources/db/migration/` | **Applied migrations are immutable.** Add `V{next}__description.sql`; never edit, rename or delete an existing one. `ddl-auto` stays `validate`. |
| `security/SecurityConfig.java`, `auth/` | Any change to access rules or token handling needs an IT (`SecurityIT`, `AuthFlowIT`). |
| `application-prod.yml` | No default values for secrets (`DB_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`). |
| `logback-spring.xml` | Prod must emit JSON to stdout. Confirm by starting the jar with `SPRING_PROFILES_ACTIVE=prod`. |
| `docker/` (Dockerfile, compose files) | Keep the runtime image non-root. Check with `make smoke`. Compose must run with `--project-directory .` (the `make` targets do this). |
| `.github/workflows/` | One CI workflow. Don't add debug or duplicate workflows. |
| `pom.xml` | Every new dependency needs a stated reason in the commit message. Prefer Spring Boot–managed versions. |

## Forbidden shortcuts

- Skipping or weakening checks: `-DskipTests`, `@Disabled`, deleting assertions, loosening
  `-Werror`, or excluding files from Spotless to get a green build.
- Editing an applied Flyway migration, or `ddl-auto` other than `validate`.
- Widening `permitAll()` matchers or catching `JwtException` more broadly to "fix" a 403.
- Committing secrets. `env/.env` is git-ignored; `env/.env.example` holds placeholders only.
- Force-pushing `main`, or committing directly to `main`: use a branch and PR.
- Adding a framework or tool because another repo has it.

## Verifying changes

- **Docker must be running** for `*IT` tests (Testcontainers).
- **VS Code / JDT race:** with the VS Code Java extension open, `./mvnw clean verify` in the
  workspace can fail with `No qualifying bean of type 'UserMapper'`. The IDE rebuilds
  `target/classes` during Maven's run. This is not a code bug. Re-run without `clean`, or
  verify in a fresh clone.
- **API changes:** check the running app with `make up`, then Swagger UI at
  `http://localhost:8080/swagger-ui/index.html`.
