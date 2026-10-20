# Aureon CRM: status and feature matrix

What the first release does, what tests show it, and what it leaves out. The rows follow the
CRM brief's scope (§4 A-J, §5 data model, §6 API and integration), plus languages, themes and
demo data. A row's **Status**:

- **Implemented**: built and used by the app.
- **Partial**: part of the bullet is built; **Notes** says which part is missing.
- **Deferred**: wanted, not in this release.
- **Not planned**: a non-goal of the first release ([README](README.md#non-goals-first-release)).

**Tested by** names only tests that exist and passed in the runs below; a dash means no test
covers the row (Notes says how it was checked, if at all). API tests are classes under
`src/test/java/.../boilerplate/` (`*Test` unit, `*IT` integration against PostgreSQL 17). Web
tests are in [aureon-crm-web](https://github.com/neszaDev/aureon-crm-web/tree/feat/crm):
Playwright specs under `tests/e2e/` (`crm/` for the CRM's), always against the real API, and
Vitest tests next to the code (`*.test.ts`, `*.test.tsx`).

Security and authorization: [security.md](security.md). Going to production:
[production-checklist.md](production-checklist.md).

## Verification

The runs behind every "Tested by" cell (Phase 4 final verification, 2026-10-21, on an 8 GB Mac):

| Repo | What ran | Result |
|---|---|---|
| API, `feat/crm` at `a6d6894` | `./mvnw verify` in a fresh copy (no `target/`, no `env/.env`): Spotless, compile with `-Werror`, unit and integration tests (Testcontainers PostgreSQL 17) | 163 unit, 409 integration, 0 failures, errors or skips. Later commits change docs only |
| API | `MigrationsFromEmptyIT`, and a Compose stack on a brand-new database (`DEMO_DATA=false`) | 21 migrations applied, schema validated, a restart applies none; register, sign in, create a workspace and a company over HTTP |
| Web, `feat/crm` at `4f924f3` | `make verify`: lint, types, knip, i18n keys, Vitest (unit and browser), production build | 1090 of 1090 tests in 174 files; build 194/194 pages |
| Web | Playwright, Chromium, one worker, against a production build (`next start`) and the API above | 231 passed, twice; 0 flaky, skipped or failed |
| Web, final fix round at `43b3357` | `make verify` and the same Playwright run twice, after the Phase 4 final review's fixes | 1099 of 1099; 231 passed, twice |
| Web | Lighthouse 12.8.2, mobile, `/`: the performance score of three runs | At `4f924f3`: English 92 / 92 / 92 (accessibility, best practices, SEO 100), Thai 90 / 91 / 91. At `43b3357`, after the header changes: English 91 / 92 / 92; Thai not re-run |

`tests/e2e/crm/Journey.e2e.ts` walks the brief's 10-step journey (§8) as one test with a step per
item; it passed in both runs, and so did the per-area specs named below.

**Not run:** CI on GitHub (see [CI](../ci.md#the-crm-branch-featcrm)); visual regression (no
baselines committed); `make smoke` and the production images (JSON logs, non-root user,
Prometheus); real HTTPS, HSTS and `Secure` cookies in a browser; more than one instance of either
service; the web's Docker standalone server (`node server.js`; the e2e runs use `next start`); a
dependency advisory audit (CI's audit jobs haven't run on this branch).

## A. Authentication and workspace management

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Sign in and sign out | Implemented | `AuthFlowIT`; `Auth.e2e.ts`, `crm/Journey.e2e.ts` (step 1) | The boilerplate's session, unchanged |
| Session refresh with rotating refresh tokens | Implemented | `RefreshReuseIT`, `RefreshConcurrencyIT`, `TokenBindingIT`, `RefreshTokenServiceTest`; `Auth.e2e.ts`, `proxy.test.ts`, `Security.e2e.ts` | Refresh families with reuse detection ([security.md](security.md#sessions)) |
| Protected routes and API endpoints | Implemented | `SecurityIT`, `WorkspaceEndpointsGuardIT`; `Auth.e2e.ts`, `Security.e2e.ts`, `proxy.security.test.ts` | |
| Profile and change password | Implemented | `ProfileIT`, `AuthServiceTest`; `crm/Settings.e2e.ts` ('changes your display name and password') | A password change revokes the other sessions |
| Create and manage workspaces | Implemented | `WorkspaceIT`; `Workspace.e2e.ts`, `crm/Settings.e2e.ts` | Workspaces are never deleted (non-goal) |
| Invite or provision users securely | Implemented | `InvitationIT`, `RegistrationDisabledIT`; `crm/Invitation.e2e.ts`, `crm/Settings.e2e.ts` | Links only: single use, 7 days, for one email, stored hashed. **No email is sent**: the inviter shares the link |
| Role-based access control | Implemented | `WorkspaceRoleTest`, `WorkspaceAccessTest`, `PrivilegeEscalationProbeIT`; `crm/Pipeline.e2e.ts`, `crm/Journey.e2e.ts` (step 9) | [permissions.md](permissions.md) |
| Membership and active-workspace selection | Implemented | `MemberIT`, `WorkspaceIT`; `Workspace.e2e.ts` ('switches between workspaces from the rail') | The workspace id is in the URL |
| Access-denied and session-expired behaviour | Implemented | `Workspace.e2e.ts`, `Auth.e2e.ts` ('signs out a deleted account without a redirect loop'), `crm/Settings.e2e.ts` (an action with an expired session), `crm/Pipeline.e2e.ts` (a forged move's 403 as a translated toast) | A non-member gets "Workspace not found" (404, so ids can't be probed) |
| Roles: owner, admin, manager, sales rep | Implemented | `WorkspaceRoleTest`, `MemberIT` | |
| Permissions defined explicitly, enforced by the API | Implemented | `PrivilegeEscalationProbeIT`, `CompanyIT`, `ContactIT`, `LeadIT`, `DealIT`, `TaskIT`, `ActivityIT`; `Security.e2e.ts` (replayed actions) | The web hides what a role can't do; the API decides |
| No workspace data for non-members, on every query and change | Implemented | `TenantIsolationIT`, `CrossWorkspaceIdProbeIT`, `WorkspaceEndpointsGuardIT`, `ReportIT`, `DealSignaturesIT`; `Security.e2e.ts`, `crm/Journey.e2e.ts` (step 9) | `@TenantId`, explicit binds in native SQL, composite foreign keys |
| No unsafe registration, invitation or provisioning shortcuts | Implemented | `InvitationIT` (email must match), `RegistrationDisabledIT`, `RateLimitIT` | Open sign-up is on by default (rate-limited per IP); `REGISTRATION_ENABLED=false` makes the API invite-only. Emails are not verified |
| Password recovery | Not planned | - | Needs email delivery; not built rather than built unsafely |

## B. Dashboard

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Total customers and companies | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` ('shows the numbers and the period the API answered with') | Companies and contacts |
| Open leads | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` | `NEW`, `CONTACTED`, `QUALIFIED` |
| Active deals | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` | Deals in an open stage |
| Deals won and lost in a period | Implemented | `DashboardIT`, `ReportIT`, `PeriodTest`; `crm/Dashboard.e2e.ts` | By close date in the workspace's time zone |
| Pipeline value | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` | Per currency, plus the probability-weighted amount |
| Recent activities | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` | |
| Upcoming follow-up tasks | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` ('shows a rep only their own deals and follow-ups, with the create actions') | The caller's |
| Overdue tasks | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` ('completes an overdue task from the follow-ups, and the counts follow') | The workspace's count and the caller's list |
| Pipeline by stage | Implemented | `DashboardIT`; `crm/Dashboard.e2e.ts` | |
| Recent changes and quick actions | Implemented | `DashboardIT`, `GetStarted.test.tsx`; `crm/Dashboard.e2e.ts` ('starts an empty workspace with its first records', the create actions) | |
| Values from persisted data | Implemented | `DashboardIT`; `crm/Journey.e2e.ts` (step 8) | Computed per request; nothing stored |
| Loading, empty and failure states | Implemented | Empty: `DashboardIT`, `crm/Dashboard.e2e.ts` | Loading (`loading.tsx`) and the workspace's error page: no named test |
| Period filter | Implemented | `PeriodTest`, `PeriodQuery.test.ts`, `PeriodPicker.test.tsx`; `crm/Dashboard.e2e.ts` | Presets or a custom range up to 366 days |

## C. Companies

| Item | Status | Tested by | Notes |
|---|---|---|---|
| List and detail pages | Implemented | `CompanyIT`; `crm/Records.e2e.ts` | |
| Create, view, edit, archive | Implemented | `CompanyIT`, `CompanyForm.test.tsx`; `crm/Records.e2e.ts` | And restore; never deleted |
| Name, industry, website, phone, address, owner, notes | Implemented | `CompanyIT`, `CompanyValidation.test.ts` | |
| Custom business-relevant fields | Partial | `CompanyIT` | One fixed extra field (employee count); fields defined by users are a non-goal |
| Search, filters, sorting, pagination, empty states | Implemented | `CompanyIT`, `BoundTextFilterIT`, `ListEmpty.test.tsx`; `crm/Records.e2e.ts` ('searches, sorts, filters and pages 25 companies, and finds those of a member who left'), `crm/Journey.e2e.ts` (step 10) | |
| Associated contacts and deals | Implemented | `ContactIT`, `DealIT` (the counts and `companyId` filters); `crm/Records.e2e.ts` (the contact on the company's page) | The page's deal list has no e2e assertion of its own |
| Activity history | Implemented | `ActivityIT`, `AuditEventIT`; `crm/Records.e2e.ts`, `crm/Audit.e2e.ts` | Timeline, plus each record's change history |
| Duplicate warnings | Implemented | `CompanyIT`; `crm/Records.e2e.ts` | A warning; saving stays possible |

## D. Contacts

| Item | Status | Tested by | Notes |
|---|---|---|---|
| List and details | Implemented | `ContactIT`; `crm/Records.e2e.ts` | |
| Create, view, edit, archive, restore | Implemented | `ContactIT`, `ContactForm.test.tsx` | |
| Name, email, phone, job title, company, owner, source, tags, notes | Implemented | `ContactIT`, `ContactValidation.test.ts`, `TagInput.test.tsx` | Up to 20 tags, lower case, one Unicode form |
| Search by name, email, phone or company | Implemented | `ContactIT`, `BoundTextFilterIT` | |
| Filters, sorting, pagination | Implemented | `ContactIT`, `UnassignedFilterIT`, `ListQuery.test.ts` | The list's e2e coverage is the shared list kit's, on companies |
| Associated deals, tasks, activity timeline | Implemented | `DealIT`, `TaskIT`, `ActivityIT`; `crm/Records.e2e.ts` | |
| Duplicate warnings and validation | Implemented | `ContactIT`, `ContactForm.test.tsx` | Same email, or the same phone digits (at least 6) |
| From a contact to its company and deals | Implemented | `DealIT` (the `contactId` filter); `crm/Records.e2e.ts` (the contact links its company) | The contact page's deal list has no e2e assertion of its own |
| No data without workspace access | Implemented | `CrossWorkspaceIdProbeIT`, `TenantIsolationIT` | |

## E. Leads

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Create and manage leads | Implemented | `LeadIT`, `LeadForm.test.tsx`; `crm/Records.e2e.ts` | |
| Source and owner | Implemented | `LeadIT` | |
| Qualification status | Implemented | `LeadStatusTest`, `LeadIT`, `LeadLifecycle.test.tsx`, `UnqualifyDialog.test.tsx`; `crm/Records.e2e.ts` | [Lifecycle](README.md#lead); database checks since V21 |
| Priority and follow-up date | Implemented | `LeadIT`, `ReminderIT` | |
| Notes and activities | Implemented | `LeadIT`, `ActivityIT` | |
| Search, filters, sorting, pagination | Implemented | `LeadIT`, `StatusFilter.test.tsx` | |
| Lead detail view | Implemented | `crm/Records.e2e.ts` | |
| Convert into company, contact and deal | Implemented | `LeadConversionIT`, `ConvertDialog.test.tsx`; `crm/Records.e2e.ts`, `crm/Journey.e2e.ts` (step 5) | |
| One transaction; no duplicates on retry or failure | Implemented | `LeadConversionIT` (rollback, retry, concurrent conversions); `crm/Records.e2e.ts` (a double click makes one deal) | |
| Lifecycle statuses documented | Implemented | - | [README](README.md#lead) |

## F. Sales pipeline and deals

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Deal list and detail pages | Implemented | `DealIT`; `crm/Pipeline.e2e.ts` | |
| Kanban board by stage | Implemented | `DealBoardIT`, `DealSignaturesIT`, `DealBoard.test.tsx`; `crm/Pipeline.e2e.ts` | Up to 50 cards per column, with the full count and totals |
| Create and edit deals | Implemented | `DealIT`, `DealForm.test.tsx` | |
| Linked to contacts and companies | Implemented | `DealIT`, `RecordLinksIT` | |
| Amount, currency, close date, probability, owner, source, notes | Implemented | `DealIT`, `DealValidation.test.ts` | |
| Configurable stages, documented defaults | Implemented | `PipelineStageIT`, `StageList.test.tsx`; `crm/Settings.e2e.ts` | [Defaults](README.md#deal); one pipeline per workspace |
| Drag and drop between stages | Implemented | `crm/Pipeline.e2e.ts` (pointer and keyboard), `crm/Journey.e2e.ts` (step 6) | Plus a "Move to…" menu |
| Stage changes persisted | Implemented | `DealIT`; `crm/Journey.e2e.ts` (each move reloaded) | |
| List view | Implemented | `DealIT`; `crm/Pipeline.e2e.ts` ('filters the deal list by its lead source') | |
| Filters by owner, stage, date, status | Implemented | `DealIT`, `UnassignedFilterIT` | |
| Won and lost, with reasons | Implemented | `DealIT`, `DealTest`, `MoveDealDialog.test.tsx`; `crm/Pipeline.e2e.ts` ('moves a deal to Lost only with a reason') | A reason is recorded for lost deals only |
| Pipeline value and sales reporting | Implemented | `DashboardIT`, `ReportIT`; `crm/Reports.e2e.ts` | |
| Money without floating point | Implemented | `MoneyTest`, `MoneyTotalTest`, `OpenApiIT`, `DealIT` | `NUMERIC(15,2)`, `BigDecimal`, strings in JSON; no currency conversion (non-goal) |
| Stage moves validated by the API | Implemented | `DealIT` | |
| A failed write never looks successful | Implemented | `DealBoard.test.tsx`; `crm/Pipeline.e2e.ts` ('gives a sales rep no way to move another rep’s deal, and refuses a forged move') | The card moves only after the API confirms |

## G. Tasks and activities

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Tasks assigned to users | Implemented | `TaskIT` | |
| Title, description, due date, priority, status, assignee | Implemented | `TaskIT`, `TaskTest`, `TaskDialog.test.tsx` | |
| Linked to contacts, companies, leads or deals | Implemented | `TaskIT` | |
| In-app follow-up reminders | Implemented | `ReminderIT`, `RemindersBell.test.tsx`; `crm/TopBar.e2e.ts`, `crm/Journey.e2e.ts` (step 7) | |
| Overdue, upcoming, completed, assigned-to-me views | Implemented | `TaskIT` (the filters behind the views); `crm/Records.e2e.ts` (the completed view) | |
| Activity timeline | Implemented | `ActivityIT`, `ActivityTimeline.test.tsx`; `crm/Records.e2e.ts` | A company's includes its contacts' and deals' |
| Call, email, meeting, note | Implemented | `ActivityIT`, `ActivityComposer.test.tsx` | Logged by hand; nothing is sent or synced |
| Create, edit, complete, cancel | Implemented | `TaskIT`, `TaskRow.test.tsx`; `crm/Records.e2e.ts` | Tasks are never deleted |
| Email or external calendar reminders | Not planned | - | In-app only, and documented as such |

## H. Search, filtering, pagination and export

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Server-side search | Implemented | `ListHelpersIT`, `BoundTextFilterIT`; `crm/Records.e2e.ts`, `crm/TopBar.e2e.ts` (⌘K) | Case-insensitive substring; `pg_trgm` is the upgrade path ([data-model.md](data-model.md#records)) |
| Filters | Implemented | `CompanyIT`, `ContactIT`, `LeadIT`, `DealIT`, `TaskIT`, `ActivityIT`, `UnassignedFilterIT` | |
| Sorting | Implemented | `ListHelpersIT`, `ListParamsTest`, `OrderingTest`, `SortHeader.test.tsx` | |
| Pagination | Implemented | `ListParamsTest`, `QueryInputProbeIT`; `crm/Records.e2e.ts` | |
| Query parameters and sort fields validated | Implemented | `QueryInputProbeIT`, `NulTextParameterIT`, `ListQuery.test.ts`; `Security.e2e.ts` ('keeps a list working whatever its URL asks for, and exports it the same way') | |
| Stable pagination | Implemented | `ListHelpersIT` | The id breaks ties; offset paging, so a row added meanwhile can shift a page |
| Empty results and invalid filters | Implemented | `InvalidFilterNotice.test.tsx`, `ListEmpty.test.tsx`; `crm/Records.e2e.ts` ('renders the defaults for a hand-edited URL', 'offers a reset, not an error page, when the backend refuses the search') | |
| CSV export: authorized, workspace-scoped, escaped | Implemented | `ExportIT`, `ExportAcceptIT`, `CsvWriterTest`, `RecordRefsTest`, `route.test.ts`; `crm/Audit.e2e.ts`, `crm/Journey.e2e.ts` (step 10) | Companies, contacts, leads, deals; at most 10,000 rows; every export audited |
| No whole table loaded into the frontend | Implemented | `crm/Records.e2e.ts` (pages of 20 over 25 companies) | |

## I. Reports

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Deal counts and value by stage | Implemented | `ReportIT`, `PipelineReport.test.tsx`; `crm/Reports.e2e.ts` | |
| Won and lost over time | Implemented | `ReportIT`, `WonLostReport.test.tsx`; `crm/Reports.e2e.ts`, `crm/Journey.e2e.ts` (step 8) | By month in the workspace's time zone |
| Conversion funnel by stage | Implemented | `ReportIT`, `FunnelReport.test.tsx`; `crm/Reports.e2e.ts` | Says when the stage history starts after the period |
| Activities completed by user | Implemented | `ReportIT`, `ActivityReport.test.tsx`; `crm/Reports.e2e.ts` | |
| Upcoming and overdue follow-ups | Implemented | `ReportIT`, `FollowUpReport.test.tsx`; `crm/Reports.e2e.ts` | |
| Pipeline value by owner | Implemented | `ReportIT`; `crm/Reports.e2e.ts` | |
| Documented aggregation rules | Implemented | `ReportIT` | [api.md](api.md#dashboard-and-reports); never summed across currencies |

## J. Settings and administration

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Workspace profile | Implemented | `WorkspaceIT`; `crm/Settings.e2e.ts` | |
| Team member management | Implemented | `MemberIT`, `MembersTable.test.tsx`; `crm/Settings.e2e.ts` | |
| Roles and permissions | Partial | `WorkspaceRoleTest`, `MemberIT`; `crm/Settings.e2e.ts` | Members get one of four roles, and the matrix is shown read-only. Editing it, or custom roles: not planned |
| Pipeline stage configuration | Implemented | `PipelineStageIT`, `StageList.test.tsx`; `crm/Settings.e2e.ts` | |
| Lead sources and other reference data | Partial | `LeadSourceIT`, `SourceList.test.tsx`; `crm/Settings.e2e.ts` | Lead sources are a managed list; industries, tags and lost reasons are free text |
| Profile settings | Implemented | `ProfileIT`, `ProfileForm.test.tsx`, `PasswordForm.test.tsx`; `crm/Settings.e2e.ts` | |
| Default currency and time zone | Implemented | `WorkspaceIT`, `CurrenciesTest`, `PeriodTest`, `ReminderIT`; `crm/Settings.e2e.ts` | |
| Audit history | Implemented | `AuditTrailIT`, `AuditEventIT`, `AuditCoverageProbeIT`; `crm/Audit.e2e.ts` | Append-only, kept forever |
| Authorization on admin endpoints | Implemented | `PrivilegeEscalationProbeIT`, `UserAdminIT`, `WorkspaceEndpointsGuardIT` | |
| Confirmation and permission for consequential actions | Implemented | `ConfirmAction.test.tsx`, `PrivilegeEscalationProbeIT`; `crm/Settings.e2e.ts` ('keeps the last owner, showing why') | |

## Data model (§5)

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Users, memberships, workspaces, records, stages, tasks, activities, audit events, reference data | Implemented | `MigrationsFromEmptyIT`, `BoilerplateApplicationIT` | [data-model.md](data-model.md) |
| Tags | Implemented | `ContactIT` | A `text[]` on contacts with a GIN index, no tag table |
| Primary and foreign keys | Implemented | `ContactIT`, `ActivityIT`, `RecordLinksIT` | Composite `(workspace_id, id)` foreign keys |
| Indexes for search and relationships | Implemented | - | Measured with 10,000 deals, not tested ([database.md](../database.md#query-plans-at-scale)) |
| Unique constraints, nullability, checks | Implemented | `ErrorCodeIT`, `LeadIT`, `PipelineStageIT`, `ActivityIT` | |
| Timestamps | Implemented | `TimestampPrecisionTest`, `TaskTest` | `timestamptz`, microseconds |
| Explicit tenant boundary | Implemented | `TenantIsolationIT`, `CrossWorkspaceIdProbeIT` | |
| Transactions for multi-step operations | Implemented | `LeadConversionIT`, `AuditTrailIT` | |
| Flyway only, no ORM schema changes | Implemented | `MigrationsFromEmptyIT` | Hibernate `ddl-auto: validate` |
| Existing migrations kept | Implemented | `MigrationsFromEmptyIT` | V1-V5 are the boilerplate's, unchanged |
| Archival and deletion policy | Implemented | `CompanyIT`, `ContactIT`, `LeadIT`, `DealIT`, `ActivityIT` | [README](README.md#records-archive-never-delete) |
| No accidental cascade deletes | Implemented | `PipelineStageIT`, `LeadSourceIT`, `MemberIT`, `CompanyIT`, `ContactIT`, `LeadIT`, `DealIT`, `TaskIT`, `ActivityIT` | `RESTRICT`; a removed member's records become unassigned |
| No stored derived totals | Implemented | `DashboardIT`, `ReportIT` | |
| Seed data without real customer data | Implemented | `DemoDataSeederIT` | Fictional, `example.com` |

## API and frontend integration (§6)

| Item | Status | Tested by | Notes |
|---|---|---|---|
| Endpoints, schemas, validation and permissions documented | Implemented | - | [api.md](api.md), [permissions.md](permissions.md); compared with the running spec on 2026-10-21: every path and method matches |
| Consistent error responses | Implemented | `ErrorBodyProbeIT`, `ErrorCodeIT`, `ErrorHandlingIT`, `GlobalExceptionHandlerTest` | `ApiError` with a stable `code` |
| Accurate OpenAPI | Implemented | `OpenApiIT` | |
| Frontend types generated from OpenAPI | Implemented | - | `pnpm api:check` against the final API on 2026-10-21 (Phase 5): no difference. Not part of the runs above; CI runs it in its E2E job |
| Backend errors shown as useful feedback | Implemented | `ApiError.test.ts`, `ActionFailure.test.ts`; `crm/Pipeline.e2e.ts`, `crm/Records.e2e.ts` | Translated by `code` |
| Loading and pending states, no repeated submits | Implemented | `CompanyForm.test.tsx`, `ContactForm.test.tsx`, `ConvertDialog.test.tsx`; `crm/Records.e2e.ts` (a double click converts once) | |
| Stale data | Implemented | `CompanyIT`, `ContactIT`, `LeadIT`, `DealIT`, `TaskIT`, `ActivityIT` (409), `ErrorCodeIT`; `crm/Records.e2e.ts`, `crm/Settings.e2e.ts` (the reload message) | |

## Languages, themes, accessibility, demo data

| Item | Status | Tested by | Notes |
|---|---|---|---|
| English, French, Thai | Implemented | `make verify`'s i18n check; `I18n.e2e.ts`, the CRM specs' "fits…" tests at 390 px in Thai (all but Audit and Journey) | French and Thai were written without a native speaker's review |
| Light and dark themes | Implemented | `crm/Settings.e2e.ts` ('switches to dark and to Thai from the account menu'), `Landing.e2e.ts` | |
| Accessibility | Implemented | axe (WCAG 2.1 A and AA) in the CRM specs; `Showcase.e2e.ts` for the demo pages | Automated checks only. Known violations are listed for 15 demo pages, none for the CRM |
| Phones (390 px) | Implemented | The CRM specs' "fits…" tests (all but Audit and Journey) | |
| Visual regression | Deferred | - | No baselines committed: CI renders them on Linux, and CI hasn't run |
| Demo data | Implemented | `DemoDataSeederIT`, `DemoDataSeederTest`, `ProdProfileRuntimeProbeIT` | Opt-in, `local` and `dev` profiles only, never under `prod` ([Try it](README.md#try-it)) |

## Known limitations

- **Refresh sharing is per process.** The web server shares one refresh between requests that
  arrive together only within one process; with several web instances, a request on another one
  loses the rotation and relies on the API's 30 s reuse grace ([security.md](security.md#sessions)).
- **Rate limits are in memory, per instance.** Each API instance counts on its own and a restart
  resets them; run one API instance ([checklist](production-checklist.md#scaling)).
- **No `script-src` in the CSP.** It is no backstop against an XSS: a nonce would make every
  page dynamic, including the static public pages.
- **Next buffers up to 11 MB before the 413.** The web's body gate answers after Next has read
  the body, so the reverse proxy's body cap is required.
- **The files page's exemption is per path.** `/dashboard/files` (the boilerplate's upload demo)
  parses up to 11 MB from anyone, signed in or not; every other path refuses a body over 1 MiB,
  except those starting with `/_next/static` or `/_next/image`, which the gate skips
  ([follow-up 2](#follow-ups)).
- **No JSON body size cap on the API.** A signed-in user can send a large JSON body; keep the API
  off the internet.
- **No email.** Invitations are links the inviter shares; there is no password recovery and no
  email verification.
- **No external calendar or email reminders**: reminders are in the app only.
- **Visual baselines are not committed**, so visual regression has never run.
- **CI's E2E job needs a published API image**, and this project publishes none; CI has not run
  on `feat/crm` at all ([CI](../ci.md#the-crm-branch-featcrm)).
- **A Radix warning in development** when a radio is clicked while a picker is open (React's
  `flushSync` message); harmless, development only.
- **Lighthouse in Thai** scores 90-91 on mobile: 0 to 1 point of margin over 90.
- **A database 500 logs no stack frames**: its exception classes and SQLSTATE only, so no value
  reaches the logs (the S-2 trade-off; [security.md](security.md#phase-4-findings)).
- **One API instance.** Uploads live on local disk (`FILES_DIR`) and the limits above are per
  instance. No per-user upload quota or workspace cap; account events go to the logs only
  ([deployment.md](../deployment.md#operating-it)).
- **The audit trail has no retention period**: an export's filters, its search text included,
  stay in it.
- **Link previews of the public pages** take their `og:image` host from `APP_URL` at build; the
  web Dockerfile takes no build argument for it, so a stock image points them at
  `http://localhost:3000`.
- Not in the first release (non-goals): fields defined by users, several pipelines, currency
  conversion, file attachments on records, telephony, billing, deleting a workspace.

## Follow-ups

In order of priority:

1. **Move uploads to an auth-gated streaming route handler and set server actions back to 1 MB**:
   ends the anonymous 11 MB parse on the files page and Next's 11 MB buffering on every path.
2. **Exclude `_next/static/` and `_next/image$` from the body gate, not every path starting with
   them**: the prefixes are matched loosely, so a future `[locale]` page whose name starts with
   `static` or `image` (`/_next/static-x`) would skip the gate.
3. **Run CI**: open pull requests from `feat/crm` (an owner's decision) and enable API image
   publishing, so the web's E2E, contract and image-scan jobs and both repos' dependency audits
   run on GitHub.
4. **Commit the visual baselines** from that first CI run, so visual regression starts checking.
5. **Shared state before scaling out**: rate limits, the web's refresh sharing and uploads in
   shared storage, so more than one instance of each service is safe.
6. **Email delivery** (invitations, password recovery, email verification): needs a mail service,
   which the brief keeps optional.
7. **A `script-src` CSP** with nonces or hashes, so an XSS has a second line of defence.
8. **A JSON body size cap in the API**: defence in depth if the API is ever reachable from
   outside.
9. **Stack frames for database 500s without their messages**: easier diagnosis, still no values
   in the logs.
10. **Upload quota, workspace caps, an `account_events` table, one refresh row per session**: the
    known gaps in [deployment.md](../deployment.md#operating-it).
11. **A numeric check on the ids server actions put in API paths** (finding I-3), through one
    shared path helper.
12. **`pg_trgm` indexes** for substring search once a workspace outgrows SMB scale.
13. **A native speaker's review** of the French and Thai copy.
