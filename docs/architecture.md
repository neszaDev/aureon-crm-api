# Architecture

## Request flow

```mermaid
flowchart LR
  C[Client] --> RID[RequestCorrelationFilter<br/>X-Request-Id + MDC]
  RID --> JWT[JwtAuthenticationFilter<br/>Bearer token → principal]
  JWT --> SEC{SecurityConfig<br/>permitAll or authenticated}
  SEC -->|no valid token| E401[401 ApiError<br/>ApiAuthenticationEntryPoint]
  SEC --> WS{WorkspaceAccessInterceptor<br/>/workspaces/id/... only}
  WS -->|not a member| E404[404 ApiError]
  WS -->|membership into WorkspaceContext| CTRL[Controller<br/>@Valid, DTO records]
  CTRL --> SVC[Service<br/>@Transactional, rules]
  SVC --> REPO[Spring Data repository]
  REPO --> PG[(PostgreSQL<br/>schema by Flyway)]
  CTRL -. exceptions .-> GEH[GlobalExceptionHandler<br/>ApiError JSON]
```

The workspace step applies only to paths under `/api/v1/workspaces/{workspaceId}`; every other
route goes straight to its controller. See [Workspaces and tenancy](#workspaces-and-tenancy).

## Feature layout

The code is organized by feature. `auth`, `user`, `testresult`, `file`, `workspace` and the
areas under `crm` each own their controller, service, repository, entity and `dto/` records.
Cross-cutting code lives in `security` (filter chain, JWT filter, CORS), `audit` (the audit
trail) and `common` (base entities, error handling, request-id filter, validation constraints).

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

## Workspaces and tenancy

A workspace is one business's CRM. `workspace` owns workspaces, members, roles, permissions and
invitations; `crm` holds the CRM features that live inside a workspace (`crm/settings`: pipeline
stages and lead sources). The full model is in [docs/crm/](crm/README.md); the mechanism is:

- **Membership per request.** `WorkspaceAccessInterceptor` (registered by `WorkspaceWebConfig` for
  `/api/v1/workspaces/*` and below) reads the caller's membership of `{workspaceId}` from the
  database and stores it in `WorkspaceContext`, a thread-local cleared when the request ends. No
  membership, an unknown workspace and a non-numeric id are all the same 404, so ids can't be
  probed. Roles are read on every request, so a role change applies at once.
- **Tenant filter.** Entities that extend `WorkspaceOwnedEntity` carry a Hibernate `@TenantId`
  `workspace_id`; `WorkspaceTenantResolver` supplies the current workspace, or `-1` (matches no
  row; an insert fails on the foreign key) when there is none. There is no root tenant. Native SQL
  is not filtered and must bind the workspace id itself.
- **Permissions.** Services call `WorkspaceAccess.require(Permission)`; lacking one is a 403.
  `WorkspaceRole` maps roles to permissions ([matrix](crm/permissions.md)).
- **Invitations.** A link carries a random token whose SHA-256 hash is stored. Preview and
  sign-up through the link are public (`SecurityConfig`), accepting needs a signed-in account
  with the invited email. No workspace context exists there, so those lookups name the
  workspace explicitly, and tokens never reach the logs.
- **Audit trail.** `AuditTrail.record` joins the transaction of the change (it refuses to run
  without one) and writes to `audit_events`, which a database trigger makes append-only.
- **Account deletion.** `WorkspaceUserDeletionGuard` listens to `UserDeletionEvent` and refuses (409)
  to delete the last owner of a workspace. `user` doesn't depend on `workspace`.
- **Tests.** `TenantIsolationIT` proves the tenant filter; `WorkspaceEndpointsGuardIT` takes every
  workspace endpoint from the running application and requires 404 for non-members and 401
  without a token ([testing](testing.md)).

## Security

- **Access tokens** are HS256 JWTs (`auth/JwtService`) bound to the user id (`uid` claim; the
  email stays the subject, with a `role` claim), and are short-lived (15 min by default).
  `JwtAuthenticationFilter` loads the user by id and sets a `CurrentUser(id, email)` principal
  with the role from the database; services look the caller up by that id. An invalid or missing
  token, or one whose user is gone, whose email changed or whose `ver` claim is not the user's
  `token_version` (an email, role or password change increments it), leaves the request anonymous, and
  `ApiAuthenticationEntryPoint` answers protected paths with 401 and an `ApiError` body. A URL
  the firewall rejects is a 400 `ApiError` (`SecurityConfig`'s `RequestRejectedHandler`).
- **Refresh tokens** are opaque 256-bit random values. Only their SHA-256 hash is stored.
  A sign-in starts a family; each refresh rotates the token under a row lock and keeps the old one
  as used. A used token presented again after `app.auth.refresh-reuse-grace` (30 s) revokes the
  family; a family can't rotate after `app.auth.session-max-age` (90 days) or once the user's
  token version moved past the row's; logout deletes the family. Rotations are limited per user;
  expired rows go in one bulk delete on every sign-in and rotation.
- **Rate limits** (`ratelimit/RateLimiter`): Bucket4j buckets in a bounded Caffeine cache, per
  client IP for sign-in and registration, per email for failed sign-ins and per user for
  refreshes. In-memory, so per
  instance. The client IP comes from `X-Forwarded-For` of trusted proxies only.
- **Roles**: `/api/v1/users/**` (except `/me`) needs `ROLE_ADMIN` (the database role), and
  `UserAdminService` re-checks it in the database. `ApiAccessDeniedHandler` answers 403 with an
  `ApiError` body. The account role is separate from workspace roles: a system admin has no
  access to a workspace it isn't a member of.
- For a production deployment, supply `JWT_SECRET` from a secret manager. Replace this module
  with an OIDC resource server when centralized identity or SSO is needed.

## Persistence

`AuditableEntity` supplies database-assigned IDs, UTC audit timestamps and optimistic-locking
versions; `WorkspaceOwnedEntity` adds the tenant column. Flyway is the sole schema authority: JPA
validates the schema and never alters it. See [docs/database.md](database.md).

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
logstash-logback-encoder. Every request carries an `X-Request-Id` (the client's when it is a
plain id of up to 64 characters, otherwise a generated one), also placed in the logging MDC.
Account security events are structured INFO lines (`common/security/AccountEvents`), written
once their transaction has committed (a sign-in lockout at once).

## Testing strategy

Unit tests (`*Test`) cover logic in isolation. Integration tests (`*IT`) run the full context
against real PostgreSQL via Testcontainers and cover HTTP contracts, security rules,
persistence and migrations. CI additionally starts the runtime image with the prod profile
(`scripts/smoke-test.sh`). See [docs/testing.md](testing.md).
