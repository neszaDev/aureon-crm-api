# CRM API contract

The frontend codes against this contract; the OpenAPI spec (`/v3/api-docs`) generated from the
code is the machine-readable version, and the frontend's `schema.d.ts` is generated from it.

## Conventions

- Base path `/api/v1`. Everything below `/workspaces/{workspaceId}` (written `{w}`) needs a member
  of that workspace; see [permissions.md](permissions.md). All endpoints need a Bearer token unless
  marked **public**.
- Ids are 64-bit integers. Instants are ISO-8601 UTC (`2026-10-17T09:30:00Z`); calendar dates are
  `YYYY-MM-DD`.
- **Money** is a string with up to 13 integer and 2 fraction digits (`"1250.00"`), plus `currency`
  (ISO 4217, upper case). Totals are lists of `{ currency, amount }`.
- **Updates** are `PUT` with every editable field (an omitted or null optional field is cleared)
  and the `version` the client last read. A different version means someone else changed the
  record: **409**, reload and retry. State changes (archive, convert, move, complete, ...) are
  `POST` actions; those that act on a version take it in the body too.
- **Lists** take `page` (≥ 0, default 0), `size` (1-100, default 20), `sort` (an upper-case
  field name allowed for that list) and `direction` (`ASC` or `DESC`), and return a Spring
  `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`, ...). The id is always a
  tie-breaker. Archived records are excluded unless `archived=true` (then only archived ones).
  Text search `q` is trimmed; blank means no search.
- **Errors** always use `ApiError` (`status`, `message`, `fieldErrors`, ...). 400 invalid input or
  query parameter; 401 no valid token; 403 member without the permission; 404 unknown record,
  **or not a member of the workspace**; 409 version conflict, duplicate name, or a rule (convert a
  lead that isn't qualified, delete a stage in use, remove the last owner, ...).

Common shapes:

```ts
MemberRef  = { userId, email, displayName? }          // owner, assignee, author
MoneyTotal = { currency, amount }                     // amount as a string
Ref        = { id, name }                             // company, stage, source
```

## Account (boilerplate, extended)

| Method and path | Body → response | Notes |
|---|---|---|
| `GET /users/me` | → `UserResponse` + `displayName?` | |
| `PUT /users/me` | `{ displayName? (≤100) }` → `UserResponse` | |
| `POST /users/me/password` | `{ currentPassword, newPassword (6-72, as registration) }` → `AuthTokensResponse` | 400 with `fieldErrors.currentPassword` when it is wrong (not 401: that would end the session); wrong attempts count toward the per-email sign-in failure limit (429); revokes every other session and starts a new one |
| `POST /auth/register` | unchanged | 403 when `REGISTRATION_ENABLED=false` (invite-only) |

## Workspaces, members, invitations

| Method and path | Body → response | Permission |
|---|---|---|
| `GET /workspaces` | → `WorkspaceSummary[]` (`id, name, role`) of the caller, by name | signed in |
| `POST /workspaces` | `{ name (1-100), defaultCurrency, timezone }` → 201 `Workspace` | signed in; caller becomes owner |
| `GET /workspaces/{w}` | → `Workspace`: `id, name, defaultCurrency, timezone, role, permissions[], version, createdAt, updatedAt` | member |
| `PUT /workspaces/{w}` | `{ name, defaultCurrency, timezone, version }` → `Workspace` | `WORKSPACE_MANAGE` |
| `GET /workspaces/{w}/members` | → `Member[]` (`userId, email, displayName?, role, joinedAt`) | member |
| `PUT /workspaces/{w}/members/{userId}` | `{ role }` → `Member` | `MEMBERS_MANAGE` + owner rules |
| `DELETE /workspaces/{w}/members/{userId}` | → 204 | `MEMBERS_MANAGE`, or the caller themselves |
| `GET /workspaces/{w}/invitations` | → `Invitation[]` pending (`id, email, role, invitedBy: MemberRef?, createdAt, expiresAt`) | `MEMBERS_MANAGE` |
| `POST /workspaces/{w}/invitations` | `{ email, role }` → 201 `Invitation` + `token` (shown once) | `MEMBERS_MANAGE`; 409 already a member or invited |
| `DELETE /workspaces/{w}/invitations/{id}` | → 204 (revoked) | `MEMBERS_MANAGE` |
| `GET /invitations/{token}` | → `{ workspaceId, workspaceName, email, role, expiresAt, status }`, status `PENDING`, `ACCEPTED`, `REVOKED` or `EXPIRED` | **public**; 404 unknown token |
| `POST /invitations/{token}/accept` | → `{ workspaceId }` | signed in, email must match (403); 409 not pending |
| `POST /invitations/{token}/register` | `{ password, displayName? }` → 201 `AuthTokensResponse` | **public**, rate-limited like registration; creates the account and the membership; 409 email already registered |

## Reference data

| Method and path | Body → response | Permission |
|---|---|---|
| `GET /workspaces/{w}/pipeline-stages` | → `Stage[]` (`id, name, position, probability, kind, dealCount, version`), by position | member |
| `POST /workspaces/{w}/pipeline-stages` | `{ name (1-60), probability (0-100) }` → 201 `Stage` (an `OPEN` stage, placed after the open ones) | `SETTINGS_MANAGE` |
| `PUT /workspaces/{w}/pipeline-stages/{id}` | `{ name, probability, version }` → `Stage` | `SETTINGS_MANAGE` |
| `PUT /workspaces/{w}/pipeline-stages/order` | `{ stageIds: [...] }` all `OPEN` stage ids in the new order → `Stage[]` | `SETTINGS_MANAGE` |
| `DELETE /workspaces/{w}/pipeline-stages/{id}` | → 204; 409 used by a deal, `WON`/`LOST`, or the last open stage | `SETTINGS_MANAGE` |
| `GET /workspaces/{w}/lead-sources` | `?includeArchived` → `LeadSource[]` (`id, name, archived, version`) | member |
| `POST /workspaces/{w}/lead-sources` | `{ name (1-60) }` → 201 | `SETTINGS_MANAGE`; 409 duplicate name |
| `PUT /workspaces/{w}/lead-sources/{id}` | `{ name, archived, version }` | `SETTINGS_MANAGE` |
| `DELETE /workspaces/{w}/lead-sources/{id}` | → 204; 409 in use (archive it instead) | `SETTINGS_MANAGE` |

## Companies

`/workspaces/{w}/companies`

| Method and path | Body → response |
|---|---|
| `GET /` | `?q` (name, website, phone), `ownerId`, `industry`, `archived`, paging; `sort`: `NAME` (default, `ASC`), `CREATED_AT`, `UPDATED_AT` → `Page<CompanySummary>` |
| `GET /duplicates` | `?name&website` → up to 5 `CompanyRef` (`id, name, website?`): same name ignoring case, or same website host |
| `POST /` | `CompanyInput` → 201 `Company` |
| `GET /{id}` | → `Company` |
| `PUT /{id}` | `CompanyInput` + `version` → `Company` |
| `POST /{id}/archive`, `POST /{id}/restore` | → `Company` |
| `GET /export` | list filters → `text/csv` (`EXPORT`) |

```ts
CompanyInput   = { name (1-200), industry?, website?, phone?, address?, employeeCount? (0-10000000),
                   ownerId?, notes? }                 // ownerId defaults to the caller
CompanySummary = { id, name, industry?, website?, phone?, owner?: MemberRef, contactCount,
                   openDealCount, archived, updatedAt }
Company        = CompanySummary + { address?, employeeCount?, notes?, archivedAt?, createdAt, version }
```

## Contacts

`/workspaces/{w}/contacts`: the same endpoints as companies (`duplicates` takes `?email&phone`:
same email ignoring case, or same digits of the phone), plus `GET /tags` → the workspace's tags in
use, sorted. List filters: `q` (first and last name, email, phone, company name), `companyId`,
`ownerId`, `sourceId`, `tag`, `archived`; `sort`: `NAME` (default), `COMPANY`, `CREATED_AT`,
`UPDATED_AT`.

```ts
ContactInput   = { firstName (1-100), lastName?, email?, phone?, jobTitle?, companyId?, ownerId?,
                   sourceId?, tags? (≤20, each 1-40, stored lower case), notes? }
ContactSummary = { id, firstName, lastName?, fullName, email?, phone?, jobTitle?, company?: Ref,
                   owner?: MemberRef, tags, archived, updatedAt }
Contact        = ContactSummary + { source?: Ref, notes?, archivedAt?, createdAt, version }
```

## Leads

`/workspaces/{w}/leads`: list, create, get, update, archive, restore, export as above. List
filters: `q` (names, company name, email, phone), `status` (repeatable), `ownerId`, `sourceId`,
`priority`, `followUpBefore` (date), `archived`; `sort`: `CREATED_AT` (default, `DESC`),
`UPDATED_AT`, `FOLLOW_UP_DATE`, `PRIORITY`, `NAME`. Converted leads can't be updated (409).

| Method and path | Body → response |
|---|---|
| `POST /{id}/status` | `{ status: NEW, CONTACTED, QUALIFIED or UNQUALIFIED, reason? (required for UNQUALIFIED), version }` → `Lead` |
| `POST /{id}/convert` | `LeadConversionInput` → `LeadConversion`; 409 unless `QUALIFIED`; repeated after success: the first result with `alreadyConverted: true` |

```ts
LeadInput   = { firstName, lastName?, companyName?, email?, phone?, jobTitle?, sourceId?, ownerId?,
                priority (default MEDIUM), followUpDate?, estimatedValue?, currency?, notes? }
Lead        = { id, ...LeadInput fields, source?: Ref, owner?: MemberRef, status, unqualifiedReason?,
                convertedAt?, convertedCompany?: Ref, convertedContact?: Ref, convertedDeal?: Ref,
                archived, archivedAt?, createdAt, updatedAt, version }
LeadConversionInput = {
  companyId?,            // link to this existing company, or
  createCompany,         // create one from companyName (ignored when companyId is set)
  contactId?,            // link to this existing contact, else one is created from the lead
  deal?: { name, amount, currency?, stageId?, expectedCloseDate? }   // omit for no deal
}
LeadConversion = { leadId, company?: Ref, contact: Ref, deal?: Ref, alreadyConverted }
```

## Deals

`/workspaces/{w}/deals`: list, create, get, update (not the stage), archive, restore, export.
List filters: `q` (name, company name), `stageId`, `status` (`OPEN`, `WON`, `LOST`), `ownerId`,
`companyId`, `contactId`, `closeFrom` / `closeTo` (expected close date), `archived`; `sort`:
`UPDATED_AT` (default, `DESC`), `NAME`, `AMOUNT`, `EXPECTED_CLOSE_DATE`, `CREATED_AT`.

| Method and path | Body → response |
|---|---|
| `GET /board` | `?q&ownerId&companyId` → `{ stages: [{ stage: Stage, count, totals: MoneyTotal[], deals: DealSummary[] }] }`, all stages in order, up to 50 deals each, most recently updated first |
| `POST /{id}/move` | `{ stageId, version, lostReason? (required for LOST) }` → `Deal` |
| `GET /{id}/history` | → `StageChange[]` (`fromStage?: Ref, toStage: Ref, changedBy?: MemberRef, changedAt`), newest first |

```ts
DealInput   = { name (1-200), amount, currency?, stageId? (create only; an OPEN stage, default the
                first), probability? (0-100), expectedCloseDate?, ownerId?, companyId?, contactId?,
                sourceId?, notes? }
DealSummary = { id, name, amount, currency, stage: Stage-ref { id, name, kind }, status,
                probability, expectedCloseDate?, owner?: MemberRef, company?: Ref,
                contact?: { id, fullName }, closedAt?, archived, updatedAt, version }
Deal        = DealSummary + { probabilityOverride?, source?: Ref, lostReason?, lead?: Ref, notes?,
                archivedAt?, createdAt }
```

## Tasks and reminders

`/workspaces/{w}/tasks`: list, create, get, update. List filters: `q` (title), `status`
(repeatable), `assigneeId`, `mine` (assigned to the caller), `overdue`, `dueFrom` / `dueTo`
(instants), `companyId`, `contactId`, `leadId`, `dealId`; `sort`: `DUE_AT` (default, `ASC`, no due
date last), `PRIORITY`, `CREATED_AT`, `UPDATED_AT`.

| Method and path | Body → response |
|---|---|
| `POST /{id}/complete`, `/reopen`, `/cancel` | `{ version }` → `Task` |
| `GET /workspaces/{w}/reminders` | → `{ overdueTasks, dueSoonTasks (next 24 h), followUpLeads }` of the caller, at most 20 each |

```ts
TaskInput = { title (1-200), description?, dueAt?, priority (default MEDIUM), assigneeId?,
              companyId?, contactId?, leadId?, dealId? }       // assigneeId defaults to the caller
Task      = { id, ...TaskInput fields, assignee?: MemberRef, createdBy?: MemberRef, status,
              overdue, completedAt?, company?: Ref, contact?: { id, fullName }, lead?: { id, fullName },
              deal?: Ref, createdAt, updatedAt, version }
```

## Activities

`/workspaces/{w}/activities`: `GET` (filters `type`, `companyId`, `contactId`, `leadId`, `dealId`,
`authorId`, `from` / `to`; newest `occurredAt` first), `POST`, `GET /{id}`, `PUT /{id}`,
`DELETE /{id}`. A company's timeline includes activities logged on its contacts and deals: when an
activity names a contact or deal with a company, the service fills in `companyId`.

```ts
ActivityInput = { type: CALL | EMAIL | MEETING | NOTE, subject (1-200), body?, occurredAt? (default
                  now), companyId?, contactId?, leadId?, dealId? }     // at least one link
Activity      = { id, ...fields, author?: MemberRef, company?: Ref, contact?, lead?, deal?,
                  createdAt, updatedAt, version }
```

## Dashboard and reports

All take `from` / `to` dates (inclusive, in the workspace time zone; default the last 30 days)
where a period applies.

| Path | Response |
|---|---|
| `GET /workspaces/{w}/dashboard` | `{ period, counts: { companies, contacts, openLeads, openDeals, overdueTasks }, openPipeline: [{ currency, amount, weightedAmount }], won: { count, totals }, lost: { count }, byStage: [{ stage, count, totals }], recentActivities (8), myUpcomingTasks (8), myOverdueTasks (8), recentChanges (10 audit events on records) }` |
| `GET /workspaces/{w}/reports/pipeline` | open deals `{ byStage: [...], byOwner: [{ owner?, count, totals }] }` |
| `GET /workspaces/{w}/reports/won-lost` | `{ months: [{ month: "2026-09", won, lost, wonTotals }] }`, every month of the period, by `closedAt` |
| `GET /workspaces/{w}/reports/funnel` | deals created in the period: `{ created, stages: [{ stage, reached }] }`, where reached = deals whose history entered that stage or a later one |
| `GET /workspaces/{w}/reports/activities` | `{ byUser: [{ user?, calls, emails, meetings, notes, tasksCompleted }] }` in the period |
| `GET /workspaces/{w}/reports/follow-ups` | `{ overdueTasks, upcomingTasks (next 7 days), dueLeads }`: workspace-wide counts and the first 20 of each |

## Export

`GET .../{companies|contacts|leads|deals}/export` with the list's filters → `text/csv; charset=utf-8`
with a header row, `Content-Disposition: attachment`. Needs `EXPORT`. At most 10,000 rows (400
beyond that: narrow the filters). Values are quoted per RFC 4180, and a value starting with `=`,
`+`, `-`, `@`, tab or carriage return is prefixed with `'` so spreadsheets don't run it as a
formula.

## Audit

| Path | Response | Permission |
|---|---|---|
| `GET /workspaces/{w}/audit-events` | `?entityType&entityId&actorId`, paging, newest first → `Page<AuditEvent>` (`id, occurredAt, actor?: MemberRef-like {userId?, email?}, action, entityType, entityId, entityLabel?, changes`) | `AUDIT_READ`, or any member when both `entityType` and `entityId` are given |
