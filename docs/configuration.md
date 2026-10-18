# Configuration

All configuration comes from environment variables. Defaults live in
`src/main/resources/application.yml`, and each profile file overrides only what differs.

## Profiles

| Profile | Used by | Differences |
|---|---|---|
| `local` | `make watch` | DevTools restart, SQL logging, DEBUG for `com.example.boilerplate` |
| `dev` | `make up` | Packaged image, SQL logging, DEBUG |
| `test` | integration tests | WARN logging; the database comes from Testcontainers |
| `prod` | deployments, `make smoke`, the runtime image by default | JSON logs to stdout, no defaults for secrets, only health/info/prometheus exposed, no API docs or Swagger UI unless `SPRINGDOC_ENABLED=true` |

Select one with `SPRING_PROFILES_ACTIVE`. Compose sets it for you.

## Application variables

| Variable | Default | Required in prod | Notes |
|---|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/boilerplate` | yes | JDBC URL |
| `DB_USERNAME` | `boilerplate` | yes | |
| `DB_PASSWORD` | `boilerplate` | yes | |
| `DB_POOL_SIZE` | `10` | no | Hikari maximum pool size |
| `DB_POOL_MIN_IDLE` | `2` | no | Hikari minimum idle connections |
| `JWT_SECRET` | `local`, `dev`, `test`: a published placeholder; otherwise none | yes | At least **32 bytes** (HS256). Checked at startup; the value is never logged. Generate one with `openssl rand -hex 32`. The app refuses to start with a placeholder from this repository (the profiles' default or `env/.env.example`'s) unless `local`, `dev` or `test` is active and `prod` is not. |
| `JWT_ACCESS_TOKEN_TTL` | `PT15M` | no | ISO-8601 duration, must be positive |
| `REFRESH_TOKEN_TTL` | `P30D` | no | ISO-8601 duration, must be positive |
| `REFRESH_REUSE_GRACE` | `PT30S` | no | `app.auth.refresh-reuse-grace`: a used refresh token presented again within this is a parallel refresh (401 for that request); later it revokes the whole session ([API](api.md#refresh-tokens-and-logout)). Zero or more |
| `SESSION_MAX_AGE` | `P90D` | no | `app.auth.session-max-age`: how long after the sign-in a session's refresh tokens can rotate; then the user signs in again. Must be positive |
| `SPRINGDOC_ENABLED` | `false` (`prod` only) | no | `true` turns `/v3/api-docs` and Swagger UI back on under `prod`; the other profiles always have them |
| `REGISTRATION_ENABLED` | `true` | no | `false` makes the API invite-only: `POST /auth/register` answers 403 (`Registration is disabled`). Existing accounts still sign in. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | yes | Comma-separated; must not be empty |
| `APP_VERSION` | `dev` | recommended | Shown at `/actuator/info` |
| `FILES_DIR` | `data/files` | recommended | Upload storage; relative to the working directory (`/app` in the image, which has a volume there in compose). Must be writable. One instance only: use shared storage before scaling out. |
| `FILES_MAX_SIZE` | `10MB` | no | Largest upload (`KB`/`MB`); also sets Spring's multipart limits |
| `FILES_ALLOWED_TYPES` | `image/png,image/jpeg,image/gif,image/webp,application/pdf,text/plain,text/csv` | no | Comma-separated media types. Don't add `text/html` or `image/svg+xml` unless downloads stay attachments. |
| `RATE_LIMIT_ENABLED` | `true` | no | Turn sign-in/registration limits off (the `test` profile does) |
| `RATE_LIMIT_LOGIN_PER_IP` | `20` | no | Sign-in attempts per client IP per minute |
| `RATE_LIMIT_LOGIN_FAILURES_PER_EMAIL` | `10` | no | Failed sign-ins per email per 15 minutes |
| `RATE_LIMIT_REGISTER_PER_IP` | `10` | no | Registrations per client IP per hour |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | private + loopback ranges | if the proxy has a public IP | Regex of proxies whose `X-Forwarded-For` is trusted (see below) |
| `ADMIN_EMAIL` | none | no | First admin account, created at startup if missing (see below) |
| `ADMIN_PASSWORD` | none | with `ADMIN_EMAIL` | 12–72 characters. Only used when the account is created; changing it later does nothing. |
| `DEMO_DATA` | `false` | no (`local` and `dev` only) | `true` seeds the demo workspace once at startup ([Try it](crm/README.md#try-it)); never with the `prod` profile, even next to `dev` |
| `DEMO_PASSWORD` | `demo-password` | no (`local` and `dev` only) | Password of the four demo logins, 6–72 characters; only used when they are created and never logged. A demo login that already exists with another password keeps it (a warning names the email) |

`app.security.*`, `app.auth.*`, `app.cors.*`, `app.admin.*`, `app.files.*`, `app.rate-limit.*` and
`app.export.*` bind to validated records (`auth/AuthProperties`, `auth/SessionProperties`,
`security/CorsProperties`,
`user/AdminProperties`, `file/FileProperties`, `ratelimit/RateLimitProperties`,
`crm/export/ExportProperties`: `max-rows`, default 10000, 1 to 30000, the most rows one CSV export
may have; the upper bound keeps the export's `IN` lookups of the rows' links under PgJDBC's 32,767
bind parameters; the `test` profile sets 3). An invalid value
stops the app at startup with the property name. `app.demo.*` (`crm/demo/DemoProperties`:
`enabled`, `password`) comes from `DEMO_DATA` and `DEMO_PASSWORD` in the `local` and `dev`
profiles, the only ones with the seeder; `application.yml` has `enabled: false` and no password
(the `test` profile sets both for `DemoDataSeederIT`).

### Logging

- Account security events are one INFO line each from `common/security/AccountEvents`, with the
  acting and the affected user's ids and the changed field names, never the values:
  `account.update` and `account.delete` (an admin's change), `password.change`,
  `sign-in.lockout` (the failed sign-in that blocks an email; no actor) and `refresh.reuse` (a
  replayed refresh token that revoked a session). Under `prod` they are JSON fields too. That
  logger stays at INFO whatever the root level. A table of account events is a follow-up.
- Keep framework DEBUG logging (`org.springframework.security`, `org.springframework.web`) off
  in prod: it logs request URIs, and an invitation link's URI carries its token. Request and
  response records leave passwords and tokens out of their `toString()`.
- A client's `X-Request-Id` is kept only when it matches `[A-Za-z0-9._-]{1,64}`; anything else
  gets a generated id.

### Rate limits

Limits live in memory (`ratelimit/RateLimiter`), so each instance counts on its own and a
restart resets them. They key on the client IP, which Tomcat takes from `X-Forwarded-For` only
when the connection comes from a trusted proxy (`server.forward-headers-strategy: native`). By
default that's any private or loopback address, which covers the Next.js server and most load
balancers. If the API is reachable directly from untrusted machines on a private network, or
your proxy has a public IP, set `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to exactly your
proxies. Without a trusted proxy in front, every client is its own IP and the header is
ignored.

End-to-end suites that register many users from one machine should raise
`RATE_LIMIT_REGISTER_PER_IP` and `RATE_LIMIT_LOGIN_PER_IP` instead of turning limits off.

### First admin

With `ADMIN_EMAIL` and `ADMIN_PASSWORD` set, `user/AdminBootstrap` creates that account with the
`ADMIN` role at startup, unless the email is already registered. An existing account is
**never** promoted: there is no email verification, so whoever registered the address first
would become admin. To promote an existing account, use `PATCH /api/v1/users/{id}` as another
admin, or the database:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'me@example.com';
```

## Local Docker variables (`env/.env`)

`make setup` (or the first `make watch` / `make up`) copies `env/.env.example` to `env/.env`,
which is git-ignored. The `make` targets pass it to Compose with `--env-file env/.env`. A bare
`docker compose` won't find it; use `make` or pass the flag yourself.

| Variable | Default | Notes |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `boilerplate` | Used by the Postgres container and passed to the app as `DB_*` |
| `POSTGRES_PORT` | `5432` | Host port only; change it if 5432 is taken |
| `APP_PORT` | `8080` | Host port of the app in `make watch` and `make up` (the container still listens on 8080), e.g. `APP_PORT=8081` when another project uses 8080. Swagger UI and the API are then on that port, and the frontend's API URL must point at it. |
| `JWT_SECRET` | none | Required by compose for the `local` and `dev` profiles |
| `REFRESH_TOKEN_TTL`, `CORS_ALLOWED_ORIGINS`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `RATE_LIMIT_*` | as above | Passed through to the app container |
| `DEMO_DATA`, `DEMO_PASSWORD` | as above | Passed to the `local` and `dev` app containers only |

When you add a variable, update `application*.yml`, this page and `env/.env.example` together.
Never commit real values.
