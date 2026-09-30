# Configuration

All configuration comes from environment variables. Defaults live in
`src/main/resources/application.yml`, and each profile file overrides only what differs.

## Profiles

| Profile | Used by | Differences |
|---|---|---|
| `local` | `make watch` | DevTools restart, SQL logging, DEBUG for `com.example.boilerplate` |
| `dev` | `make up` | Packaged image, SQL logging, DEBUG |
| `test` | integration tests | WARN logging; the database comes from Testcontainers |
| `prod` | deployments, `make smoke` | JSON logs to stdout, no defaults for secrets, only health/info/prometheus exposed |

Select one with `SPRING_PROFILES_ACTIVE`. Compose sets it for you.

## Application variables

| Variable | Default | Required in prod | Notes |
|---|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/boilerplate` | yes | JDBC URL |
| `DB_USERNAME` | `boilerplate` | yes | |
| `DB_PASSWORD` | `boilerplate` | yes | |
| `DB_POOL_SIZE` | `10` | no | Hikari maximum pool size |
| `DB_POOL_MIN_IDLE` | `2` | no | Hikari minimum idle connections |
| `JWT_SECRET` | dev-only placeholder | yes | At least **32 bytes** (HS256). Checked at startup; the value is never logged. Generate one with `openssl rand -hex 32`. |
| `JWT_ACCESS_TOKEN_TTL` | `PT15M` | no | ISO-8601 duration, must be positive |
| `REFRESH_TOKEN_TTL` | `P30D` | no | ISO-8601 duration, must be positive |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | yes | Comma-separated; must not be empty |
| `APP_VERSION` | `dev` | recommended | Shown at `/actuator/info` |

`app.security.*` and `app.cors.*` bind to validated records (`auth/AuthProperties`,
`security/CorsProperties`). An invalid value stops the app at startup with the property name.

## Local Docker variables (`env/.env`)

`make setup` (or the first `make watch` / `make up`) copies `env/.env.example` to `env/.env`,
which is git-ignored. The `make` targets pass it to Compose with `--env-file env/.env`. A bare
`docker compose` won't find it; use `make` or pass the flag yourself.

| Variable | Default | Notes |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `boilerplate` | Used by the Postgres container and passed to the app as `DB_*` |
| `POSTGRES_PORT` | `5432` | Host port only; change it if 5432 is taken |
| `JWT_SECRET` | none | Required by compose for the `local` and `dev` profiles |
| `REFRESH_TOKEN_TTL`, `CORS_ALLOWED_ORIGINS` | as above | Passed through to the app container |

When you add a variable, update `application*.yml`, this page and `env/.env.example` together.
Never commit real values.
