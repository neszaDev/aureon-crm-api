# Testing

| Kind | Naming | Runner | Needs | Run |
|---|---|---|---|---|
| Unit | `*Test` | Surefire (`test` phase) | nothing | `make test` |
| Integration | `*IT` | Failsafe (`integration-test` / `verify`) | Docker | `make verify` |

The naming rule matters: Surefire is configured to exclude `*IT`, and Failsafe only runs
`*IT`. A class named `FooIntegrationTest` would run as a unit test and fail without Docker.

## Unit tests

Plain JUnit 5 + Mockito + AssertJ. No Spring context. Construct the class under test directly,
as in `auth/JwtServiceTest` and `auth/RefreshTokenServiceTest`. To test configuration binding,
use `ApplicationContextRunner` (see `auth/AuthPropertiesTest`). The workspace layer has its own:
`workspace/WorkspaceRoleTest` (permissions per role), `WorkspaceAccessTest` (permission and
ownership checks, `runAs`), `WorkspaceAccessInterceptorTest` (the context is cleared after a
request, an exception or async handling) and `audit/AuditChangesTest` (audit diffs). The CRM adds
`crm/common/` (`CrmRecordTest` archive and owner, `CurrenciesTest`, `ListParamsTest` paging and
sort orders, `MoneyTest` and `MoneyTotalTest` scale and string form, `OrderingTest` nulls last,
`TextSearchTest` wildcard escaping), `crm/lead/LeadStatusTest` (the status transition table),
`crm/deal/DealTest` (status from the stage kind, closing time), `crm/task/TaskTest` (task
transitions, overdue, microsecond times), `common/entity/TimestampPrecisionTest` and
`common/exception/GlobalExceptionHandlerTest` (database data exceptions are the client's 400).

## Integration tests

Extend `support/AbstractIntegrationTest`. It provides:

- the full application context with the `test` profile and the real security filter chain
- `MockMvc` (`mvc`) and `ObjectMapper` (`json`)
- a real `postgres:17-alpine` from `support/PostgresTestcontainersConfig`, connected via
  `@ServiceConnection`, with Flyway migrations applied
- helpers: `uniqueEmail()`, `register(email)`, `login(email, password)`,
  `registerAndGetAccessToken()`, `adminAccessToken()`, `bearer(token)`
- rate limits **off** (`application-test.yml`), since every IT registers from one address
- an admin account (`ADMIN_EMAIL`), created by `AdminBootstrap` from `application-test.yml`;
  never change or delete it in a test

Tests of workspace endpoints extend `support/WorkspaceTestSupport` (itself an
`AbstractIntegrationTest`), which adds `tokenFor(email)` (register, get the access token),
`createWorkspace(token, name)` (the token's user becomes the owner), `addMember(workspaceId, email,
role)` (straight into the database; invitations are the real way in), `send(token, request[,
body])` and `body(result)`. Give each test its own users and workspaces, like any other IT.

All IT classes share **one** Spring context and one database, and nothing is cleaned between
tests. Isolate tests with fresh users (`uniqueEmail()`), and never assert on global counts.

What the existing ITs cover:

| Class | Covers |
|---|---|
| `BoilerplateApplicationIT` | Migrations applied, schema validates |
| `auth/AuthFlowIT` | Register → login → refresh rotation → logout, error paths |
| `security/SecurityIT` | Public vs protected endpoints, `X-Request-Id` |
| `testresult/TestResultIT` | CRUD, pagination, summary, owner isolation |
| `ratelimit/RateLimitIT` | Limits on a real Tomcat (own context, limits on): 429 + `Retry-After`, `X-Forwarded-For` |
| `file/FileIT` | Upload, download headers, owner isolation, type/size rejection, blob cleanup |
| `user/ProfileIT` | Display name set/clear/limit, password change (sessions rotated, wrong password is a 400 field error) |
| `auth/RegistrationDisabledIT` | `REGISTRATION_ENABLED=false`: register is 403, existing accounts still sign in (own context) |
| `user/UserAdminIT` | Admin-only access, search, role/email changes, session revocation, delete |
| `common/ErrorHandlingIT` | Client errors map to 4xx with the `ApiError` shape |
| `workspace/WorkspaceIT` | Create (caller becomes owner), list, read, update, non-member 404, role change applies at once |
| `workspace/MemberIT` | Member list, role changes, removal, leaving, owner rules, last-owner protection |
| `workspace/invitation/InvitationIT` | Invite, preview, accept as an existing account, register through the link, revoke, expiry, owner rules, concurrent accepts, tokens stored hashed and kept out of logs |
| `workspace/TenantIsolationIT` | `@TenantId`: another workspace's ids are 404, queries without a workspace context return nothing, inserts without one fail |
| `workspace/WorkspaceEndpointsGuardIT` | Takes every `/api/v1/workspaces/{workspaceId}/...` endpoint from the running application: 404 for a non-member, 401 without a token |
| `audit/AuditTrailIT` | Audit events written with the change, append-only (the database refuses `UPDATE`/`DELETE`), refuses to run outside a transaction |
| `crm/settings/PipelineStageIT` | Default stages, add/rename/reorder/delete rules, permissions, versions, validation |
| `crm/settings/LeadSourceIT` | Default sources, create/rename/archive/delete, duplicate names, permissions, versions |
| `workspace/MemberDirectoryIT` | Owner, assignee and author names: only members of the current workspace resolve |
| `crm/common/RecordLinksIT` | Record links: the workspace's pass, any other (or unknown) id is a 400 on its field |
| `crm/common/ListHelpersIT` | The list helpers on real queries: literal search, sorting with the id tie-breaker in both directions, nulls last |
| `crm/company/CompanyIT` | Create/get/update, validation, search, filters, sorting, paging, archive/restore, duplicate warning, owner rules, a removed member's companies become unassigned, stale version (409), other workspace (404) |
| `crm/contact/ContactIT` | The same for contacts, plus tags (normalized, filtered, listed), sorting by name and company, duplicates by email and phone, company links, the company's contact count |
| `crm/lead/LeadIT` | Create/update, validation, estimated value and currency, the status lifecycle and its rules, follow-up date, filters, search, sorting (nulls last, priority), archive/restore, owner rules, versions |
| `crm/lead/LeadConversionIT` | Conversion into company, contact and deal in one transaction (rollback on a bad deal), links to existing records, the owner of the new records, retry returns the first result, concurrent conversions create once, an archived qualified lead converts, a converted lead is read-only, permissions |
| `crm/deal/DealIT` | Create/update, money as strings, default stage and currency, moves with the stage history, won/lost rules, filters, sorting, archive/restore, stage and source in use, rep rules, links and stage of another workspace |
| `crm/deal/DealBoardIT` | The pipeline board: a column per stage, totals per currency, the card cap with the full count, archived deals left out |
| `crm/task/TaskIT` | Create/assign/update, assignee-or-creator edit rights, complete/reopen/cancel with their audit events, the overdue flag, sorting (due date nulls last, priority), filters, links, a removed member's tasks become unassigned, no delete |
| `crm/task/ReminderIT` | The caller's overdue and due-soon tasks and follow-up leads: ordering, the cap of 20, the workspace time zone |
| `crm/activity/ActivityIT` | Log/edit/delete with the audited last state, the company filled in from the contact or deal (create and update), a link required (400 `links`, and the database check), author-only edits, an author who left, timelines and filters (type, links, author, `from` inclusive / `to` exclusive), links of another workspace (400, foreign keys), archived records linkable |

`WorkspaceEndpointsGuardIT` finds a new workspace controller's endpoints itself and fails if one
answers a non-member with anything but 404. If it fails, fix the endpoint, not the test. Only its
minimum endpoint count (`hasSizeGreaterThanOrEqualTo`) is raised by hand, to the number registered
(62 after the activities), so a scan that silently misses endpoints can't pass.

## Reports

After `make verify`:

- `target/surefire-reports/`, `target/failsafe-reports/`: per-class results
- `target/site/jacoco/index.html`: coverage for unit + integration tests combined

CI uploads them as the `unit-test-reports` and `integration-test-reports` artifacts (each
with the JaCoCo report of its own tests). They are never committed.

## Troubleshooting

- **`Could not find a valid Docker environment`**: start Docker. `make verify` does this for you
  on macOS (`scripts/ensure-docker.sh`); plain `./mvnw verify` doesn't. Testcontainers needs
  1.21.4 or newer for Docker Engine 29 (managed by Spring Boot 3.5.16+).
- **`No qualifying bean of type 'UserMapper'` locally but not in CI**: the VS Code Java
  extension rebuilds `target/classes` while Maven runs, and races the MapStruct-generated
  class. Re-run without `clean`, or run from a fresh clone. It is not a code problem.
- **Port 5432 already allocated** (`make watch` / `make up`): set `POSTGRES_PORT` in `env/.env`.
  Tests are unaffected, because Testcontainers picks a random port.
