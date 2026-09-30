# Spring Boot + PostgreSQL Boilerplate

A production-oriented REST API starter: **Spring Boot 3.5, Java 21, PostgreSQL 17**, Flyway
migrations, JWT access tokens with rotating refresh tokens, Actuator + Prometheus, JSON logs in
production, and a multi-stage non-root Docker image. It includes an owner-scoped `test-results`
CRUD feature as a worked example.

## Prerequisites

- **JDK 21**. Maven is not needed; use the bundled `./mvnw`.
- **Docker** (Desktop or Engine 29+), for the local stack and for integration tests.
- `make` (preinstalled on macOS and Linux).

## Quick start

```sh
make setup   # enable git hooks, create .env from .env.example
make watch   # Postgres + app with hot reload on http://localhost:8080
```

Then open Swagger UI at `http://localhost:8080/swagger-ui/index.html`. Health is at
`/actuator/health`. For a first request, see the [API guide](docs/api.md).

If port 5432 is already taken by another database, set `POSTGRES_PORT=5433` in `.env`.

## Commands

| Command | What it does |
|---|---|
| `make fmt` | Format all sources (Spotless / google-java-format) |
| `make lint` | Formatting check + compile with `-Xlint -Werror` |
| `make test` | Unit tests (`*Test`, no Docker) |
| `make verify` | Everything CI's `verify` job runs, including integration tests (needs Docker) |
| `make smoke` | Build the runtime image and start it with the prod profile, as CI does |
| `make watch` / `make up` | Run locally with hot reload / run the packaged image |
| `make down` / `make db-reset` | Stop containers / also delete the local database |

Run `make` to list them all. Each target is a one-line alias for `./mvnw` or `docker compose`.

## Project layout

```
src/main/java/.../boilerplate/  feature packages: auth, user, testresult (+ security, common)
src/main/resources/             application*.yml profiles, db/migration (Flyway), logback
src/test/java/                  *Test = unit, *IT = integration (Testcontainers PostgreSQL)
docker/                         Dockerfile (+ its .dockerignore) and compose files
scripts/                        smoke-test.sh (prod-profile container check)
docs/                           guides (below) and the repository plan
.githooks/                      pre-commit (format) and commit-msg (Conventional Commits)
```

## Documentation

| Guide | Covers |
|---|---|
| [Architecture](docs/architecture.md) | Request flow, feature layout, security model, persistence |
| [API guide](docs/api.md) | Endpoints, error format, curl walkthrough |
| [Configuration](docs/configuration.md) | Every environment variable and profile |
| [Database](docs/database.md) | Flyway migrations: adding, naming, rules |
| [Testing](docs/testing.md) | Unit vs integration tests, Testcontainers, coverage |
| [CI](docs/ci.md) | Pipeline, reproducing it locally, Dependabot, branch protection |
| [Contributing](.github/CONTRIBUTING.md) | Branches, commits, PR checklist |
| [AGENTS.md](AGENTS.md) | Rules for AI coding agents (also a good checklist for humans) |
| [Repository plan](docs/plans/0001-repository-hardening.md) | Why the repo is set up this way |

## Deploying

The runtime image is built with `docker build -f docker/Dockerfile --target runtime .`. Run it
with `SPRING_PROFILES_ACTIVE=prod` and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET` and `CORS_ALLOWED_ORIGINS`; the prod profile has no defaults for them. Pass
`APP_VERSION` (shown at `/actuator/info`) and tag releases with semantic versions
(`v1.2.0`). `make smoke` is the pre-deployment check. See
[Configuration](docs/configuration.md).
