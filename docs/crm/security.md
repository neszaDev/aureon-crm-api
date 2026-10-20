# Aureon CRM: security and authorization notes

How the CRM protects sessions and each workspace's data, which tests show it, what was not
verified, and what the Phase 4 security review found. The review (2026-10-20) attacked both
services as an outsider, a signed-in user, a member of another workspace and a lower role would,
and committed what it tried as tests: 11 probe classes in this repository
(`src/test/java/.../security/probe/`) and, in the web repository, `tests/e2e/Security.e2e.ts` and
`src/proxy.security.test.ts`. Every test named here passed in the runs listed in
[status.md](status.md#verification) (web test names as of `43b3357`, after the final fix round).

## Model

### Sessions

**API** ([architecture](../architecture.md#security), [refresh tokens](../api.md#refresh-tokens-and-logout)):

- Access tokens are HS256 JWTs, 15 minutes by default, naming the user by id (`uid`) and the
  user's token version (`ver`). Every request loads the user by id and takes the role from the
  database, so a changed email, role or password (each increments the version) or a deleted
  account makes the token worthless at once.
- Refresh tokens are 256-bit random values, stored only as SHA-256 hashes. A sign-in starts a
  **family**; each refresh rotates the token under a row lock and keeps the old one as used. The
  used token presented again within `REFRESH_REUSE_GRACE` (**30 s**) is a parallel refresh: that
  request gets 401 and nothing else happens. Later, it is a replay: the whole family is revoked
  and a `refresh.reuse` line is logged. A family stops rotating after `SESSION_MAX_AGE` (90 days)
  from the sign-in; successful rotations are rate-limited per user. A password change revokes
  every other session.

**Web** ([session design](https://github.com/neszaDev/aureon-crm-web/blob/feat/crm/docs/architecture.md#session-design)):

- The browser never talks to the API and never sees a token: the Next.js server keeps both in
  httpOnly, `SameSite=Lax` cookies, `Secure` when `APP_URL` is `https://`.
- Requests that need a refresh at the same moment (two tabs, a prefetch, the reminders bell)
  share **one** refresh per server process. A successful refresh is kept for **2 s**, so for 2 s
  after a rotation a request carrying the old refresh token to the same process receives the new
  pair, where the API's grace would answer 401. That window is the accepted price of not sending
  concurrent tabs to the sign-in page. A failed refresh is not kept. With several web instances,
  a request on another instance loses the rotation and gets the API's 401, which doesn't revoke
  the session.
- A backend 401 anywhere else ends the session (both cookies deleted, `/sign-in?expired=1`).
  Another site can't sign anyone out by linking there: a cross-site or same-site request ends a
  session only when the API refuses its token.

### Workspace tenancy

Enforced in the API, whatever the frontend shows ([permissions.md](permissions.md#enforcement)):

- Every CRM endpoint sits under `/api/v1/workspaces/{workspaceId}`. An interceptor looks up the
  caller's membership by user id; without one the answer is **404**, as for a workspace that
  doesn't exist, so ids can't be probed. A system `ADMIN` has no way into a workspace it isn't a
  member of.
- Hibernate's `@TenantId` on every workspace-owned entity adds `workspace_id = <current>` to every
  query, loading by id included, and sets it on insert.
- SQL that Hibernate doesn't write (the reports, the contact tags, the deals' stage signatures)
  binds the workspace id explicitly, and each has a cross-workspace test (`ReportIT`, `ContactIT`,
  `DealSignaturesIT`).
- Composite foreign keys `(workspace_id, x_id)` make a link to another workspace's record
  impossible to store ([data-model.md](data-model.md#the-tenant-boundary)).

### Roles and permissions

Four roles (owner, admin, manager, sales rep) and seven permissions, fixed in code
(`WorkspaceRole`): the matrix and the rules beyond it are in [permissions.md](permissions.md). The
role is read from the database on every request, so a role change or a removal applies to the
next request. The web hides what the caller's role can't do; the API refuses it regardless (403
with a `code`).

### The web server's own controls

- **Server actions** are POSTs that Next.js accepts only when `Origin` matches the host (CSRF);
  the reverse proxy must pass `Host` on ([checklist](production-checklist.md#edge-reverse-proxy-and-tls)).
- **The CSV export route** refuses `HEAD` and any request the browser marks cross-site or
  same-site, so a link on another site can't export (and audit) in the user's name.
- **Redirects** after sign-in go only to `/app/**` and `/invite/**`.
- **Security headers** on every response but the build's static assets: `X-Frame-Options:
  SAMEORIGIN`, a CSP of `frame-ancestors 'self'; base-uri 'self'; object-src 'none'; form-action
  'self'`, `nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` (`no-referrer` on
  invitation pages) and a `Permissions-Policy`. File downloads keep their own sandbox CSP. No
  `script-src` and no HSTS (see the [findings](#phase-4-findings)).
- **Request bodies**: on every path but the files page, a POST over 1 MiB is refused with 413 (one
  without a stated length with 411) before a server action parses it
  ([request bodies](https://github.com/neszaDev/aureon-crm-web/blob/feat/crm/docs/architecture.md#request-bodies)).
  The gate also skips paths starting with `/_next/static` or `/_next/image`
  ([follow-up 2](status.md#follow-ups)).
- **Invitation tokens** are stored hashed, kept out of both services' own logs, and invitation
  pages send no `Referer`.

## Verified, by test

The brief's §7 list. API tests are classes in this repository; web tests are in
[aureon-crm-web](https://github.com/neszaDev/aureon-crm-web/tree/feat/crm).

| Requirement | API | Web |
|---|---|---|
| Authentication and refresh stay compatible with the boilerplate | `AuthFlowIT`, `RefreshReuseIT`, `RefreshConcurrencyIT`, `TokenBindingIT`, `SecurityIT`, `JwtSecretStartupTest`; `AccessTokenProbeIT` (`alg: none`, a wrong key, expired, another version, a tampered payload, a refresh token as bearer: all refused; a signed `role: ADMIN` claim still gets 403); `DummyHashTest` (an unknown email costs a full BCrypt) | `Auth.e2e.ts` (the session lifecycle, refresh on the CRM and the demos, five requests sharing one refresh, a deleted account, a cross-site link to the session route); `Security.e2e.ts` ('gives a just-rotated refresh token the same new pair, and nothing later'); `proxy.test.ts` (the 2 s bound); `proxy.security.test.ts` (`Secure`, httpOnly, `SameSite=Lax` cookies over https) |
| Passwords and secrets are never logged | `SecretLoggingProbeIT` (register, login, refresh, replay, password change, an admin's email change, invite, register through an invitation, logout; at prod INFO, dev DEBUG with SQL, and framework DEBUG); `AccountEventLogIT`, `SecretRedactionTest`, `InvitationIT` | Checked by a run, not a committed test: the production server's log after the full suite and the probes held no JWT, password, token parameter, `Bearer` or invitation token |
| Protected endpoints enforce permissions in the backend | `PrivilegeEscalationProbeIT.rolesWithoutAPermissionAreRefusedAndChangeNothing` (71 cases: 403 with a code, no audit event, nothing changed); `WorkspaceEndpointsGuardIT` | `Security.e2e.ts` (server-action calls replayed by lower roles: "don't let a sales rep give themselves a workspace role", "don't let a user make themselves an admin of the app"; 'send a sales rep none of what their role hides, such as pending invitations') |
| No access to another workspace's records by changing an id | `CrossWorkspaceIdProbeIT` (35 id-bearing endpoints 404, 40 body links 400 on the field, 55 reads hold nothing of the other workspace, which stays unchanged); `TenantIsolationIT`, `ReportIT`, `FileIT`, `TestResultIT` | `Security.e2e.ts` ("can't reach another workspace by changing the id they send", `.` and `..` included); `crm/Pipeline.e2e.ts`; `crm/Journey.e2e.ts` (step 9) |
| Admin-only operations stay admin-only | `PrivilegeEscalationProbeIT` (`accountManagementIsForSystemAdminsOnly`, `theSystemAdminHasNoWayIntoAWorkspace`, `aDemotedOrRemovedMembersTokenLosesTheRightsAtOnce`); `UserAdminIT`, `MemberIT`, `InvitationIT` | `Security.e2e.ts`, `Users.e2e.ts` |
| DTO validation and safe database access | `MassAssignmentProbeIT` (ids, workspace, version, timestamps, archive, status, conversion and closing fields, creator, role in a body change nothing; a rep can't name another owner); `NoInlineLiteralTest`, `BoundTextFilterIT`, `RecordLinksIT` | (the API's) |
| Pagination and filter inputs can't produce unsafe queries | `QueryInputProbeIT` (bad page, size, sort and direction are 400, an offset past an int on all 10 lists too; SQL and `LIKE` syntax in `q` is plain text); `ListHelpersIT`, `NulTextParameterIT` | `Security.e2e.ts` ('keeps a list working whatever its URL asks for, and exports it the same way') |
| Errors leak no credentials, tokens, SQL or stack traces | `ErrorBodyProbeIT` (18 kinds of error, a real 500 included: the `ApiError` fields only); `HttpHardeningProbeIT.theContainersErrorsAndTraceGiveNothingAway`; `RequestRejectedIT`; `S2DatabaseValuesInLogsProbeIT` (no stored value in the log) | `Security.e2e.ts` ("answer a call that fails on the server without the server's error"; no token in any page, RSC payload, action result or script) |
| CORS, cookies, environment and deployment settings | `HttpHardeningProbeIT` (only the configured origins, never with credentials; no cookie; `nosniff`, `X-Frame-Options: DENY`, `no-store`); `ProdProfileRuntimeProbeIT` (under `prod`: only health, info and prometheus exposed, health without details, no API docs, no default CORS origin, demo data never seeded) | `Security.e2e.ts` ('tell browsers not to frame them, and send the usual security headers', each value; "can't frame a page: its frame shows nothing of the app, and gets no session"); `proxy.security.test.ts` |
| Sensitive operations are audited | `AuditCoverageProbeIT` (every changing workspace and invitation endpoint, and every export, writes its event naming the caller); `AuditTrailIT` (append-only), `AccountEventLogIT` | `crm/Audit.e2e.ts` (an export recorded; none for `HEAD` or a request another site started) |
| Rate limiting and brute-force protection | `RateLimitIT`, `RateLimiterTest`, `AuthServiceTest` | `Auth.e2e.ts` ('slows down repeated failed sign-ins for one email') |
| Security failures are covered by tests | The probes above, each with a positive control; mutation checks in a scratch copy (another workspace's id, a rep given `EXPORT`, an audit event removed, the page-size cap removed) each failed its probe | `Security.e2e.ts` (19 tests), `proxy.security.test.ts`; also open redirects ('never redirects anyone to another site'), CSRF ("refuse a call from another origin, even with the session's cookies"), XSS ('show what users typed as text, never as markup'), the proxy bypass of CVE-2025-29927 ('keeps the CRM, the demos and their routes behind sign-in, whatever the request claims'), files ('serve an uploaded file only as a sandboxed download, and its name as text'), the body gate ('refuse a 10 MB body from someone who is not signed in') |

## Not verified

- The production runtime: JSON logs, the non-root image, Prometheus under `prod` (`make smoke`,
  Docker; not run in Phase 4), and the web's standalone server (`node server.js`).
- Real HTTPS: HSTS and the `Secure` cookie flag in a browser (the flag is unit-tested), TLS to
  the database and `sslmode` (no deployment).
- A reverse proxy in front of the web server, and more than one instance of either service: the
  per-process refresh sharing, the per-instance rate limits, the per-IP thresholds (the local API
  ran with 10,000 per IP).
- Real attacks from a sibling subdomain (a forged `Origin` stood in for one).
- Large JSON bodies sent to the API by a signed-in user (no cap; not probed).
- Sign-in timing: checked by structure (`DummyHashTest`), not measured.
- Framework DEBUG logs: they print invitation tokens in request URIs (documented: keep them off in
  production); malformed-JSON messages at that level were not probed.
- Dependency advisories: CI's audit jobs (Trivy, `pnpm audit`, dependency review) haven't run on
  this branch.
- The Google Maps demo key's referrer restriction (operator configuration).

## Phase 4 findings

| Finding | Severity | Resolution |
|---|---|---|
| **S-1**: a page offset past `Integer.MAX_VALUE` was a 500 on the boilerplate's lists (files, test results, users) | Low | Fixed (`b8491ad`): one shared page × size check (`common/web/Paging`) for every list; `QueryInputProbeIT` checks the offset on all 10 lists |
| **S-2**: stored values reached the logs: PostgreSQL's Detail on constraint violations (an email, a failing row with a password hash), and a 500's whole exception chain | Low | Fixed (`685a699`): the driver's `logServerErrorDetail` is `false`; a database 500 logs its exception classes and SQLSTATE without messages; `S2DatabaseValuesInLogsProbeIT`. Accepted residuals below |
| **W-1**: no security response headers on the web: other sites could frame the sign-in form (clickjacking), no CSP | Medium | Fixed (`9e770ad`, `b5f6dd2`, `693074f`): the headers above on every response; values asserted in `Security.e2e.ts`. No `script-src` and no HSTS in the app: accepted, see below |
| **W-2**: 11 MB server-action bodies parsed for anyone, on every page (for the files page's uploads) | Low | Fixed in part (`61b0c2b`, `c38ab89`): a POST over 1 MiB is refused with 413 on every path but the files page (and those starting with `/_next/static` or `/_next/image`), whatever the first segment. Accepted residuals below |
| **W-3**: the API's per-IP sign-in and registration limits key on the address the web server forwards (the last incoming `X-Forwarded-For` entry), which a client chooses when Next.js is exposed directly | Low (Medium if exposed directly) | A deployment requirement (`be41aca`, `45f4cdc`): the reverse proxy makes the client's address the last entry ([checklist](production-checklist.md#edge-reverse-proxy-and-tls)). The per-email limit holds regardless |
| **I-1**: paths with a dot skip the session proxy | Info | No change: no data exposed; an expired session there ends instead of refreshing. Such paths do get the body gate |
| **I-2**: the export route accepted a `same-site` request while the session route treated it as another site | Info | Fixed (`19574ea`): the export refuses same-site too (`route.test.ts`, `crm/Audit.e2e.ts`) |
| **I-3**: `.` and `..` in an action's path parameters reach the API's path unencoded | Info | No change: all 100 operations were scripted with no harmful collision, and another workspace's id is refused anyway; a numeric check through one shared helper is a [follow-up](status.md#follow-ups) |
| **I-4**: sign-out deletes the cookies without repeating `HttpOnly` and `SameSite` | Info | No change: the cookie expires at once |
| **I-5**: server actions without an `Origin` header are accepted | Info | No change: Next.js's design; browsers send `Origin` on these POSTs |
| **I-6**: a cross-site link can set `NEXT_LOCALE` | Info | No change: it only picks the language |
| **I-7**: `APP_URL` accepts any scheme | Info | No change: operator configuration; the checklist requires `https://` |

The final Phase 4 review then found the W-2 gate bypassable through the first path segment
(`/api/sign-in` renders the sign-in page, whose action ran with a 10 MB body) and the files page's
exemption open to anyone; the first is fixed (`c38ab89`), the second is an accepted residual.

### Accepted residuals

Each was decided with its cost; the [production checklist](production-checklist.md) turns the
deployment ones into requirements.

- **The files page parses up to 11 MB from anyone**: an action runs there without a session and
  answers its own 401 afterwards. **Next.js reads up to 11 MB of any POST before the 413**. The
  reverse proxy's body cap (11 MB on the files page, 1 MB elsewhere) bounds both; without it, a
  directly exposed server can be pushed for memory. Follow-up: uploads through an auth-gated
  streaming route handler, and server actions back to 1 MB.
- **The 2 s refresh-sharing window** (see [Sessions](#sessions)): a thief holding a just-rotated
  refresh token who reaches the same web process within 2 s gets the new pair.
- **No `script-src` in the CSP**: a nonce would make every page dynamic; the CSP is no backstop
  against an XSS (none was found: typed text renders as text across 12 pages).
- **No HSTS from the app**: one image serves any `APP_URL`; the TLS terminator sends it.
- **Hibernate still logs PostgreSQL's main message** on a database error: for SQLSTATE 22008 it
  quotes the client's own input; 22P02 (which can quote a value) is not reachable from an
  endpoint. `logServerErrorDetail=true` in `DB_URL` would bring the Detail lines back: leave it
  out.
- **A database 500 logs no stack frames**, only exception classes and SQLSTATE: harder diagnosis,
  no values in the logs (follow-up: frames without messages).
- **Per-IP limits depend on the reverse proxy** (W-3), and all limits are per instance.

This document records what was tested and what was not. No security certification or compliance
(SOC 2, ISO 27001, GDPR or any other) is claimed for Aureon CRM.
