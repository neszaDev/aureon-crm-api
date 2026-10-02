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

`app.security.*`, `app.cors.*`, `app.admin.*`, `app.files.*` and `app.rate-limit.*` bind to
validated records (`auth/AuthProperties`, `security/CorsProperties`, `user/AdminProperties`,
`file/FileProperties`, `ratelimit/RateLimitProperties`). An invalid value
stops the app at startup with the property name.

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
| `JWT_SECRET` | none | Required by compose for the `local` and `dev` profiles |
| `REFRESH_TOKEN_TTL`, `CORS_ALLOWED_ORIGINS`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `RATE_LIMIT_*` | as above | Passed through to the app container |

When you add a variable, update `application*.yml`, this page and `env/.env.example` together.
Never commit real values.
