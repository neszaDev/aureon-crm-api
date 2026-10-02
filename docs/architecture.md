# Architecture

## Request flow

```mermaid
flowchart LR
  C[Client] --> RID[RequestCorrelationFilter<br/>X-Request-Id + MDC]
  RID --> JWT[JwtAuthenticationFilter<br/>Bearer token → principal]
  JWT --> SEC{SecurityConfig<br/>permitAll or authenticated}
  SEC -->|no valid token| E401[401 ApiError<br/>ApiAuthenticationEntryPoint]
  SEC --> CTRL[Controller<br/>@Valid, DTO records]
  CTRL --> SVC[Service<br/>@Transactional, rules]
  SVC --> REPO[Spring Data repository]
  REPO --> PG[(PostgreSQL<br/>schema by Flyway)]
  CTRL -. exceptions .-> GEH[GlobalExceptionHandler<br/>ApiError JSON]
```

## Feature layout

The code is organized by feature. `auth`, `user`, `testresult` and `file` each own their controller,
service, repository, entity and `dto/` records. Cross-cutting code lives in `security`
(filter chain, JWT filter, CORS) and `common` (base entity, error handling, request-id
filter).

- **Controllers** only deserialize, validate (`@Valid`) and return API contracts. They never
  return JPA entities.
- **Services** own transactions and business rules.
- **Owner scoping** (`testresult`, `file`): every query filters by the authenticated user's
  email, and another user's row is reported as 404, so its existence isn't revealed.
- **Cross-feature events**: `user` publishes `UserDeletionEvent` and `UserAccessChangedEvent`
  inside its transaction. `file` deletes the user's stored bytes after commit; `auth` revokes
  refresh tokens. `user` doesn't depend on either.
- **Files** (`file`): metadata in `stored_files`, bytes behind the `FileStorage` interface
  (`LocalFileStorage`: one file per random key in `FILES_DIR`). Bytes are deleted only after
  the database change commits.

## Security

- **Access tokens** are HS256 JWTs (`auth/JwtService`) with the email as subject and a `role`
  claim, and are short-lived (15 min by default). An invalid or missing token leaves the
  request anonymous, and `ApiAuthenticationEntryPoint` answers protected paths with 401 and an
  `ApiError` body. A valid token whose user was deleted also gets 401.
- **Refresh tokens** are opaque 256-bit random values. Only their SHA-256 hash is stored.
  Each refresh rotates the token (the old one is deleted under a row lock), and logout deletes
  it.
- **Roles**: `/api/v1/users/**` (except `/me`) needs `ROLE_ADMIN` from the token, and
  `UserAdminService` re-checks it in the database. `ApiAccessDeniedHandler` answers 403 with an
  `ApiError` body.
- For a production deployment, supply `JWT_SECRET` from a secret manager. Replace this module
  with an OIDC resource server when centralized identity or SSO is needed.

## Persistence

`AuditableEntity` supplies database-assigned IDs, UTC audit timestamps and optimistic-locking
versions. Flyway is the sole schema authority: JPA validates the schema and never alters it.
See [docs/database.md](database.md).

## Configuration and errors

- `app.security.*` and `app.cors.*` bind to validated `@ConfigurationProperties` records, so
  bad configuration fails at startup rather than on first use. See
  [docs/configuration.md](configuration.md).
- `GlobalExceptionHandler` maps domain exceptions and client mistakes to 4xx responses with
  the `ApiError` shape. Only genuinely unexpected errors become a 500, and those are logged
  with the method and path.

## Operations

Actuator supplies health (with the `/actuator/health/liveness` and `/readiness` probes) and
info, both public for orchestrators. Production exposure excludes the general metrics endpoint.
`/actuator/prometheus` is public so scrapers need no token: **restrict it at the network or
ingress level** (don't route it from the internet). The prod profile logs JSON to stdout via
logstash-logback-encoder. Every request carries an `X-Request-Id`, also placed in the logging
MDC.

## Testing strategy

Unit tests (`*Test`) cover logic in isolation. Integration tests (`*IT`) run the full context
against real PostgreSQL via Testcontainers and cover HTTP contracts, security rules,
persistence and migrations. CI additionally starts the runtime image with the prod profile
(`scripts/smoke-test.sh`). See [docs/testing.md](testing.md).
