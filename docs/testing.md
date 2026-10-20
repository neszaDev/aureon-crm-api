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
`TextSearchTest` wildcard escaping, `NoInlineLiteralTest` no `cb.literal` in `src/main`,
`common/text/TextsTest` stripping and the NUL check of a text parameter), `crm/lead/LeadStatusTest` (the status transition table),
`crm/deal/DealTest` (status from the stage kind, closing time), `crm/task/TaskTest` (task
transitions, overdue, microsecond times), `crm/export/CsvWriterTest` (quoting, `;` too, formula protection also after leading blanks,
CRLF), `ExportPropertiesTest` (`max-rows` 1 to 30000) and `RecordRefsTest` (`D-0042`), `crm/insight/PeriodTest` (defaults and bounds in the
workspace zone, the 366-day cap, date parsing), `crm/demo/DemoDataSeederTest` (the opt-in gate,
the password check that never shows the value, a login with another password left out),
`common/entity/TimestampPrecisionTest` and
`common/exception/GlobalExceptionHandlerTest` (database data exceptions are the client's 400, the
codes of unique indexes, stale updates and deadlocks).

## Integration tests

Extend `support/AbstractIntegrationTest`. It provides:

- the full application context with the `test` profile and the real security filter chain
- `MockMvc` (`mvc`) and `ObjectMapper` (`json`)
- a real `postgres:17-alpine` from `support/PostgresTestcontainersConfig`, connected via
  `@ServiceConnection`, with Flyway migrations applied
- helpers: `uniqueEmail()`, `register(email)`, `login(email, password)`,
  `registerAndGetAccessToken()`, `adminAccessToken()`, `bearer(token)`
- rate limits **off** (`application-test.yml`), since every IT registers from one address
- an export cap of **3 rows** (`app.export.max-rows`), so a fourth row reaches it
- `support/TestClock`, the application's `Clock` (read by the dashboard, the reports and the
  export): the system time until a test pins it (`clock.pin(instant)`; unpin it in `@AfterEach`)
- `app.demo.*` for `crm/demo/DemoDataSeederIT`, which builds the seeder (a `local`/`dev` bean, never
  started here) from the context and runs it
- an admin account (`ADMIN_EMAIL`), created by `AdminBootstrap` from `application-test.yml`;
  never change or delete it in a test

Tests of workspace endpoints extend `support/WorkspaceTestSupport` (itself an
`AbstractIntegrationTest`), which adds `tokenFor(email)` (register, get the access token),
`createWorkspace(token, name)` (the token's user becomes the owner), `addMember(workspaceId, email,
role)` (straight into the database; invitations are the real way in), `membership(workspaceId,
email)` (the `Membership` the interceptor would resolve, for `WorkspaceContext.runAs`),
`send(token, request[, body])` and `body(result)`. Give each test its own users and workspaces, like any other IT.

All IT classes share **one** Spring context and one database, and nothing is cleaned between
tests. Isolate tests with fresh users (`uniqueEmail()`), and never assert on global counts.

What the existing ITs cover:

| Class | Covers |
|---|---|
| `BoilerplateApplicationIT` | Migrations applied, schema validates |
| `auth/AuthFlowIT` | Register → login → refresh rotation → logout, error paths |
| `auth/RefreshConcurrencyIT` | Refresh under concurrency: parallel refreshes of one token (one winner, nothing revoked), a thief refreshing in a loop during a password change (no rotation after it, the thief's access token 401), concurrent clean-ups of expired rows (no 409) |
| `auth/RefreshReuseIT` | Refresh-token families: a replay after the grace revokes the family, within it only that request fails, a family past 90 days (counted from the sign-in) can't rotate |
| `security/SecurityIT` | Public vs protected endpoints, `X-Request-Id` |
| `security/AccountEventLogIT` | Account security events logged with ids and field names, never values (the lockout: `ratelimit/RateLimitIT`) |
| `security/RequestRejectedIT` | Firewall-rejected URLs are a 400 `ApiError` on a real Tomcat |
| `security/TokenBindingIT` | Access tokens bound to the user id and token version: an admin's email swap, a deleted user's re-registered email, a password or role change (also a token issued in the change's second) → 401; a session across refresh |
| `testresult/TestResultIT` | CRUD, pagination, summary, owner isolation |
| `ratelimit/RateLimitIT` | Limits on a real Tomcat (own context, limits on): 429 + `Retry-After`, `X-Forwarded-For`, refreshes per user (only successful rotations count: a drained bucket doesn't stop a replay from revoking the family, parallel losers don't spend it), the lockout's log line |
| `file/FileIT` | Upload, download headers, owner isolation, type/size rejection, blob cleanup |
| `user/ProfileIT` | Display name set/clear/limit, password change (sessions rotated, wrong password is a 400 field error) |
| `auth/RegistrationDisabledIT` | `REGISTRATION_ENABLED=false`: register is 403, existing accounts still sign in (own context) |
| `user/UserAdminIT` | Admin-only access, search, role/email changes, session revocation, delete |
| `common/ErrorHandlingIT` | Client errors map to 4xx with the `ApiError` shape |
| `common/web/OpenApiIT` | The spec has the `ApiError` body with the `code` enum, for the frontend's generated types; request money is a string; never-null response fields are required, nullable ones optional; the `Page*` wrappers require their fields; the four export 200s are `text/csv` strings; the dashboard's, the reports' (`period`) and the audit event's (`actor` optional) owner bucket's (`owner` optional) and activity row's (`user` optional, `total` required) required fields |
| `common/exception/ErrorCodeIT` | Each rule's status and `code` in the error body (one request per code), a duplicate name that loses the race to the unique index, a stage or source delete that loses the race to its foreign key |
| `workspace/WorkspaceIT` | Create (caller becomes owner), list, read, update, non-member 404, role change applies at once |
| `workspace/MemberIT` | Member list, role changes, removal, leaving, owner rules, last-owner protection |
| `workspace/invitation/InvitationIT` | Invite, preview, accept as an existing account, register through the link, revoke, expiry, owner rules, concurrent accepts, tokens stored hashed and kept out of logs |
| `workspace/TenantIsolationIT` | `@TenantId`: another workspace's ids are 404, queries without a workspace context return nothing, inserts without one fail |
| `workspace/WorkspaceEndpointsGuardIT` | Takes every `/api/v1/workspaces/{workspaceId}/...` endpoint from the running application: 404 for a non-member, 401 without a token |
| `audit/AuditTrailIT` | Audit events written with the change, append-only (the database refuses `UPDATE`/`DELETE`), refuses to run outside a transaction |
| `audit/AuditEventIT` | Reading the trail: newest first with the id tie-breaker, the actor (no name once the account is gone, no actor without one), filters and paging, `AUDIT_READ` for the trail and any member for one record's history (each record type; the other types 403, before paging errors), other workspaces' events never listed, bad parameters 400 (page overflow, an unknown type without a `code`) |
| `crm/settings/PipelineStageIT` | Default stages, add/rename/reorder/delete rules, concurrent deletes of the last two open stages, a reorder or a create racing a delete, permissions, versions, validation |
| `crm/settings/LeadSourceIT` | Default sources, create/rename/archive/delete, duplicate names, permissions, versions |
| `workspace/MemberDirectoryIT` | Owner, assignee and author names: only members of the current workspace resolve |
| `crm/common/RecordLinksIT` | Record links: the workspace's pass, any other (or unknown) id is a 400 on its field |
| `crm/common/ListHelpersIT` | The list helpers on real queries: literal search, sorting with the id tie-breaker in both directions, nulls last |
| `crm/common/UnassignedFilterIT` | `unassigned=true` on every list with an owner (or assignee) filter and on the deal board, and its 400 together with an owner |
| `crm/common/NulTextParameterIT` | A NUL character in every text-valued query parameter of the API (`q`, `industry`, `tag`, the duplicate checks, the dates of the dashboard and reports, the admin user search) is a 400 `VALIDATION_FAILED` on that parameter, never a 500; a second test fails when a controller gains a text parameter that is not in the list |
| `crm/common/BoundTextFilterIT` | `q`, `industry` and `tag` with `O'Brien\%_x` on every list that filters on text, its export and the deal board: exactly the right rows (any case, `' \ % _` literal), and the SQL Hibernate logs carries none of the text (it is bound) |
| `crm/company/CompanyIT` | Create/get/update, validation, search, filters, sorting, paging, archive/restore, duplicate warning, owner rules, a removed member's companies become unassigned, stale version (409), other workspace (404) |
| `crm/contact/ContactIT` | The same for contacts, plus tags (normalized, filtered, listed), sorting by name and company, duplicates by email and phone, company links, the company's contact count, the composite foreign keys (company, source, owner of another workspace) |
| `crm/lead/LeadIT` | Create/update, validation, estimated value and currency, the status lifecycle and its rules, follow-up date, filters, search, sorting (nulls last, priority), archive/restore, owner rules, a removed member's leads become unassigned, versions |
| `crm/lead/LeadConversionIT` | Conversion into company, contact and deal in one transaction (rollback on a bad deal), links to existing records, the owner of the new records, the lead's activities and tasks linked to the contact (bulk updates bound to the workspace), retry returns the first result, concurrent conversions create once, an archived qualified lead converts, a converted lead is read-only, permissions |
| `crm/deal/DealIT` | Create/update, money as strings, default stage and currency, moves with the stage history, `stageEnteredAt` (deal, list, board; the query stays in the workspace), won/lost rules, filters (`sourceId`: another workspace's source matches nothing), sorting, archive/restore, stage and source in use, rep rules, links and stage of another workspace |
| `crm/deal/DealSignaturesIT` | The `signatures` of the board's cards and the list's rows: the newest move into a stage signs it (A/B/C/W), a departed member's entry has no `by` (as the history), a deleted stage is not listed, each workspace's board shows its own signers (the native query binds the workspace), the board reads the signatures with one query whatever the number of deals, and the owners and signers of a list page or a board with one member query (Hibernate statistics) |
| `crm/deal/DealBoardIT` | The pipeline board: a column per stage, totals per currency, the card cap with the full count, archived deals left out |
| `crm/task/TaskIT` | Create/assign/update, assignee-or-creator edit rights, complete/reopen/cancel with their audit events (no derived fields), the overdue flag, sorting (due date nulls last, priority), filters (`dueFrom` inclusive / `dueTo` exclusive), links (the company from the contact or deal, the contact of a converted lead), a removed member's tasks become unassigned, no delete |
| `crm/task/ReminderIT` | The caller's overdue and due-soon tasks and follow-up leads: ordering, the cap of 20, the workspace time zone |
| `crm/export/ExportIT` | CSV export of companies, contacts, leads and deals: header and columns, BOM, file name, the same rows in the same order as the list for each filter (`unassigned` too), formula protection, Thai text, `EXPORT` permission (403), other workspaces' rows never exported, the row cap (400 `EXPORT_TOO_LARGE` as JSON also for `Accept: */*`, nothing recorded; a rep over the cap gets 403, not the count), the audit event (a lead export's `statuses` filter key too), `Accept: application/json` gets the CSV and the JSON error, the file named for today in Pacific/Kiritimati by the pinned clock |
| `crm/export/ExportAcceptIT` | On a real Tomcat, for a client accepting only `text/csv`: the over-cap 400 `EXPORT_TOO_LARGE` and a 404 are JSON (not the 401 an error the handler could not write used to turn into), and a CSV under the cap is still sent (the JSON client gets the 400 too) |
| `crm/insight/DashboardIT` | The dashboard: all zeros and empty lists for an empty workspace, every value against stored data (archived records left out, totals and weighted amounts per currency, the deal's probability override, won/lost in the period, the caller's task lists, record changes only), half-up rounding per currency, a deal closed at 23:30 in Bangkok on `to` inside the period, other workspaces never counted (also for a member of both), one instant of the pinned clock for the default period's today and the overdue count and lists, period 400s on `from` / `to` |
| `crm/insight/ReportIT` | The five reports: zeros and empty lists for an empty workspace (any member reads them), the pipeline by stage and by owner (the unassigned bucket of a removed member, the order by largest total then email, closed and archived deals left out), won/lost by month in the workspace zone (a close at 01:00 Bangkok on 1 September, the period's first and last instants, empty months, archived left out), the funnel (highest stage reached, Lost and a deleted stage counting for nothing, the cohort by creation, `historyFrom`), activities by author and completed tasks by assignee in the period (a removed member's, each entry's `total`), follow-ups (workspace-wide, orders, the cap of 20 with the full count, 7 days ahead, today in Pacific/Kiritimati), another workspace's identical data never counted (the funnel's native SQL), the period each period report returns (the default's 29-day span ending today in the workspace zone), the dashboard's won and lost equal the sums over the won-lost months, the pinned clock's today and now for the follow-ups and the default period, period 400s |
| `crm/demo/DemoDataSeederIT` | The demo workspace: the four logins and roles, the record counts and spreads, `example.com` addresses, the back-dated deal history consistent (creation first, moves in order, none in the future, none at one instant, `closedAt` the last move), the stage changes signed by at least three members who are all members of the workspace, mostly the deal's owner and sometimes the manager, a converted lead and its deal named after the company they convert into and the deal minutes to days after the conversion, no activity before its deal, the funnel and won/lost reports agree with it, the default dashboard shows a won and a lost deal, a second run adds nothing; the seeder's profiles (`local` or `dev`, never with `prod`) and no bean in the `test` context |
| `crm/activity/ActivityIT` | Log/edit/delete with the audited last state, the company filled in from the contact or deal (create and update), the contact of a converted lead, a link required (400 `links`, and the database check), author-only edits, an author who left, timelines and filters (type, links, author, `from` inclusive / `to` exclusive), links of another workspace (400, foreign keys), archived records linkable |

`WorkspaceEndpointsGuardIT` finds a new workspace controller's endpoints itself and fails if one
answers a non-member with anything but 404. If it fails, fix the endpoint, not the test. Only its
minimum endpoint count (`hasSizeGreaterThanOrEqualTo`) is raised by hand, to the number registered,
with every new workspace endpoint, so a scan that silently misses endpoints can't pass.

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
