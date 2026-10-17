# Aureon CRM API

The backend of **Aureon CRM**, a multi-tenant CRM for small and medium-sized businesses, on
**Spring Boot 3.5, Java 21 and PostgreSQL 17**. The web app is the sibling repository
[aureon-crm-web](https://github.com/neszaDev/aureon-crm-web).

Every business is a **workspace**. People join a workspace through an invitation link and get a
role in it (owner, admin, manager or sales rep); one account can belong to several workspaces.
A workspace's data is isolated from every other: each request is checked for membership, and the
database queries are filtered by workspace (Hibernate `@TenantId`) and backed by composite foreign
keys. Changes are written to an append-only audit trail. On top of that sit the CRM features:
pipeline stages and lead sources today, and companies, contacts, leads, deals, tasks, activities,
a dashboard and reports as the contract in [docs/crm/](docs/crm/README.md) is built out. That
folder holds the specification, data model, permission matrix and REST contract.

It was built from [spring-postgres-boilerplate](https://github.com/neszaDev/spring-postgres-boilerplate),
so it also has JWT access tokens with rotating refresh tokens, sign-in rate limits, admin user
management, owner-scoped file uploads, Flyway migrations, Actuator + Prometheus, JSON logs in
production and a multi-stage non-root Docker image (the `test-results` CRUD is the boilerplate's
worked example).

## Prerequisites

- **JDK 21**. Maven is not needed; use the bundled `./mvnw`.
- **Docker** (Desktop or Engine 29+), for the local stack and for integration tests.
- `make` (preinstalled on macOS and Linux).

## Quick start

```sh
make setup   # enable git hooks, create env/.env from env/.env.example
make watch   # Postgres + app with hot reload on http://localhost:8080
```

Then open Swagger UI at `http://localhost:8080/swagger-ui/index.html`. Health is at
`/actuator/health`. For a first request, see the [API guide](docs/api.md); the CRM endpoints are
in the [CRM API contract](docs/crm/api.md). To run the web app against it, follow
[aureon-crm-web](https://github.com/neszaDev/aureon-crm-web).

If port 5432 or 8080 is already taken by another project, set `POSTGRES_PORT=5433` or
`APP_PORT=8081` in `env/.env` (and point the frontend at the new API port).

## Commands

| Command | What it does |
|---|---|
| `make fmt` | Format all sources (Spotless / google-java-format) |
| `make lint` | Formatting check + compile with `-Xlint -Werror` |
| `make test` | Unit tests (`*Test`, no Docker) |
| `make verify` | Everything CI's lint, unit and integration jobs run (needs Docker) |
| `make smoke` | Build the runtime image and start it with the prod profile, as CI does |
| `make watch` / `make up` | Run locally with hot reload / run the packaged image |
| `make down` / `make db-reset` | Stop containers / also delete the local database |

Run `make` to list them all. Each target is a one-line alias for `./mvnw` or `docker compose`.
Targets that need Docker check it first; on macOS they start Docker Desktop if it isn't running.

## Project layout

```
src/main/java/.../boilerplate/  feature packages: auth, user, workspace, audit, crm (+ file, testresult,
                                security, common)
src/main/resources/             application*.yml profiles, db/migration (Flyway), logback
src/test/java/                  *Test = unit, *IT = integration (Testcontainers PostgreSQL)
docker/                         Dockerfile (+ its .dockerignore) and compose files
scripts/                        smoke-test.sh (prod-profile container check)
docs/                           guides (below) and the repository plan
env/                            .env.example (tracked) and your local .env (git-ignored)
.githooks/                      pre-commit (format) and commit-msg (Conventional Commits)
```

## Documentation

| Guide | Covers |
|---|---|
| [CRM](docs/crm/README.md) | Product specification, [data model](docs/crm/data-model.md), [permissions](docs/crm/permissions.md), [API contract](docs/crm/api.md) |
| [Architecture](docs/architecture.md) | Request flow, feature layout, workspaces and tenancy, security model, persistence |
| [API guide](docs/api.md) | Errors, accounts and sign-in, files, curl walkthrough (CRM endpoints: see above) |
| [Configuration](docs/configuration.md) | Every environment variable and profile |
| [Database](docs/database.md) | Flyway migrations: adding, naming, rules |
| [Testing](docs/testing.md) | Unit vs integration tests, Testcontainers, coverage |
| [CI/CD](docs/ci.md) | Branches, environments, image publishing, releasing, Dependabot |
| [Contributing](.github/CONTRIBUTING.md) | Branches, commits, PR checklist |
| [AGENTS.md](AGENTS.md) | Rules for AI coding agents (also a good checklist for humans) |
| [Repository plan](docs/plans/0001-repository-hardening.md) | Why the repo is set up this way |

## Deploying

CI/CD publishes the tested runtime image to GitHub Container Registry: `:dev` from the `dev`
branch, and `:latest` / `:1.2.3` from `main` and version tags after an approval. See
[CI/CD](docs/ci.md). To build it yourself: `docker build -f docker/Dockerfile --target runtime .`. Run it
with `SPRING_PROFILES_ACTIVE=prod` and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET` and `CORS_ALLOWED_ORIGINS`; the prod profile has no defaults for them. Pass
`APP_VERSION` (shown at `/actuator/info`) and tag releases with semantic versions
(`v1.2.0`). `make smoke` is the pre-deployment check. See
[Configuration](docs/configuration.md).

## Make it yours

The Java package is still `com.example.boilerplate` (and the Maven `groupId` `com.example`) on
purpose: both are placeholders, and renaming them here would only add churn to every merge from
the boilerplate. When you build your own product from this repository, rename the package and the
`groupId`, and the database name and user (`docker/compose.yml`, `env/.env.example`), in a commit
of their own.

## Pulling changes from the boilerplate

This repository was split off from spring-postgres-boilerplate and keeps it as a Git remote, so
fixes and dependency updates made there can be merged in:

```sh
git remote add boilerplate https://github.com/neszaDev/spring-postgres-boilerplate.git   # once
git fetch boilerplate
git merge boilerplate/main     # or: git cherry-pick <commit> for one fix
```

Expect conflicts in `pom.xml`, `application.yml`, the docs and the compose file, which the CRM
changed; resolve them keeping the CRM's names (`aureon-crm-api`) and run `make verify` before
pushing.
