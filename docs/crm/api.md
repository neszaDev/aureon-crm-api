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
- **Updates** are `PUT` with every editable field (an omitted or null optional field is cleared,
  `ownerId` included) and the `version` the client last read. A different version means someone
  else changed the record: **409**, reload and retry. State changes (archive, convert, move,
  complete, ...) are `POST` actions; those that act on a version take it in the body too.
- **Owner** (tasks: `assigneeId`): on create, an omitted or null `ownerId` means the caller. On
  update it is the record's owner: send the current value to keep it, another member's id to
  reassign, or null to unassign. Reassigning and unassigning need `RECORDS_ASSIGN` (403), and so
  does creating a record for someone else; an id that isn't a member of the workspace is a 400 on
  that field.
- **Required fields** must be present: leaving out a required number or boolean (a stage's
  `probability`, a lead source's `archived`) is a 400 on that field, never `0` or `false`.
- **Lists** take `page` (≥ 0, default 0), `size` (1-100, default 20), `sort` (an upper-case
  field name allowed for that list) and `direction` (`ASC` or `DESC`), and return a Spring
  `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`, ...). Without `direction`
  a sort takes its own: `DESC` for `CREATED_AT`, `UPDATED_AT` and `PRIORITY`, `ASC` for `NAME`,
  `COMPANY`, `FOLLOW_UP_DATE` and `DUE_AT`. The id is always a tie-breaker. Archived records are excluded unless `archived=true` (then only archived ones).
  Text search `q` is trimmed; blank means no search.
- **Errors** always use `ApiError` (`status`, `code`, `message`, `fieldErrors`, ...). 400 invalid
  input or query parameter; 401 no valid token; 403 member without the permission; 404 unknown
  record, **or not a member of the workspace**; 409 version conflict, duplicate name, or a rule
  (convert a lead that isn't qualified, delete a stage in use, remove the last owner, ...).
  `message` is English, for logs and API users; clients translate by **`code`**, which is stable.
  The OpenAPI spec has the body as `components.schemas.ApiError`, `code` as an enum.
  A 400 on fields also has `fieldErrors` (`{ field: message }`). Errors outside this table have no
  `code` (it is left out): 401, a malformed body or a parameter of the wrong type (400), an unknown
  route (404), 405, 413, 415, the file uploads' 400/413/415, 500. Show those by status.

  | `code` | Status | Meaning |
  |---|---|---|
  | `VALIDATION_FAILED` | 400 | Invalid input or query parameter: `fieldErrors` names the fields when it can (bean validation, a link not in this workspace, a blank required text, `page`/`size` out of range, ...) |
  | `TEXT_INVALID` | 400 | The database refused a value: text with a NUL character, a value too long or a number out of range for its column |
  | `PIPELINE_CHANGED` | 400 | `PUT /pipeline-stages/order`: `stageIds` isn't exactly the current open stages (someone added or deleted one): reload the pipeline |
  | `FORBIDDEN` | 403 | The role lacks the permission, or the record isn't the caller's to edit; admin-only endpoints |
  | `REGISTRATION_DISABLED` | 403 | `POST /auth/register` with `REGISTRATION_ENABLED=false` (invite-only) |
  | `OWNER_ONLY` | 403 | Only owners manage owners: an admin changing an owner's role or making someone owner, removing an owner, inviting as `OWNER` or revoking an `OWNER` invitation |
  | `INVITATION_EMAIL_MISMATCH` | 403 | Accepting an invitation made for another email address |
  | `NOT_FOUND` | 404 | The record, invitation or user doesn't exist, or belongs to another workspace or user; a workspace the caller isn't a member of |
  | `VERSION_CONFLICT` | 409 | Stale `version`, or a concurrent update won: reload and retry |
  | `EMAIL_TAKEN` | 409 | An account with this email exists (registration, registering through an invitation, an admin changing an email) |
  | `OWN_ACCOUNT` | 409 | An admin changing or deleting their own account (`/users/{id}`) |
  | `ACCOUNT_OWNS_WORKSPACE` | 409 | Deleting an account that is the only owner of a workspace: add another owner first |
  | `LAST_OWNER` | 409 | Demoting, removing or leaving as the workspace's last owner |
  | `ALREADY_MEMBER` | 409 | Inviting someone who is already a member |
  | `ALREADY_INVITED` | 409 | Inviting an email that has a pending invitation |
  | `INVITATION_NOT_PENDING` | 409 | Accepting, registering through or revoking an invitation that was accepted or revoked (accept/register: or expired) |
  | `DUPLICATE_NAME` | 409 | A pipeline stage or lead source with this name (any case) exists, also when two requests race |
  | `STAGE_PROTECTED` | 409 | Deleting `WON` or `LOST` (`The Won and Lost stages can't be deleted`) or the last open stage (`The pipeline needs an open stage`): one code, the message says which |
  | `STAGE_IN_USE` | 409 | Deleting a stage that holds deals, archived ones too |
  | `SOURCE_IN_USE` | 409 | Deleting a lead source a lead, contact or deal uses: archive it instead |
  | `LEAD_CONVERTED` | 409 | Editing a converted lead or changing its status |
  | `LEAD_NOT_QUALIFIED` | 409 | Converting a lead that isn't `QUALIFIED` |
  | `TASK_STATUS_CONFLICT` | 409 | `complete` or `cancel` a task that isn't `OPEN`, `reopen` one that is |
  | `CONFLICT` | 409 | Any other conflict with stored data (a database constraint) |
  | `RATE_LIMITED` | 429 | Too many sign-in or registration attempts: wait `Retry-After` seconds |

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
| `POST /users/me/password` | `{ currentPassword, newPassword (6-72 characters and at most 72 UTF-8 bytes, as registration) }` → `AuthTokensResponse` | 400 with `fieldErrors.currentPassword` when it is wrong (not 401: that would end the session); wrong attempts count toward the per-email sign-in failure limit (429); revokes every other session and starts a new one |
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
| `GET /workspaces/{w}/invitations` | → `Invitation[]` pending (`id, email, role, invitedBy: MemberRef?, createdAt, expiresAt`), newest first; expired ones are not listed | `MEMBERS_MANAGE` |
| `POST /workspaces/{w}/invitations` | `{ email, role }` → 201 `Invitation` + `token` (shown once) | `MEMBERS_MANAGE`, owner for role `OWNER` (403); 409 already a member or already has a pending invitation (an expired one is replaced) |
| `DELETE /workspaces/{w}/invitations/{id}` | → 204 (revoked) | `MEMBERS_MANAGE`, owner for an `OWNER` invitation (403); 409 when it was already accepted or revoked (a pending or an expired one can be revoked) |
| `GET /invitations/{token}` | → `{ workspaceId, workspaceName, email, role, expiresAt, status }`, status `PENDING`, `ACCEPTED`, `REVOKED` or `EXPIRED` | **public**; 404 unknown token |
| `POST /invitations/{token}/accept` | → `{ workspaceId }` | signed in, email must match (403); 409 not pending |
| `POST /invitations/{token}/register` | `{ password (6-72 characters, at most 72 UTF-8 bytes), displayName? (≤100) }` → 201 `AuthTokensResponse` | **public**, rate-limited like registration; creates the account and the membership; 409 email already registered |

Invitation rules:

- A link works once, for 7 days, and only for the invited email (lower case). The token is shown
  only in the response that creates the invitation; the server keeps its SHA-256 hash.
- An admin can't invite someone as `OWNER` or revoke an `OWNER` invitation: only owners manage
  owners (403).
- The pending list hides expired invitations and is sorted newest first.
- Revoking an accepted or an already revoked invitation is 409; an expired one can be revoked.
  Inviting an email whose earlier invitation expired replaces it.
- Accepting or registering through a link that is no longer `PENDING` is 409. The preview still
  answers, with its status.

## Reference data

| Method and path | Body → response | Permission |
|---|---|---|
| `GET /workspaces/{w}/pipeline-stages` | → `Stage[]` (`id, name, position, probability, kind, dealCount, version`; `dealCount`: its active deals), by position | member |
| `POST /workspaces/{w}/pipeline-stages` | `{ name (1-60), probability (0-100) }` → 201 `Stage` (an `OPEN` stage, placed after the open ones) | `SETTINGS_MANAGE` |
| `PUT /workspaces/{w}/pipeline-stages/{id}` | `{ name, probability, version }` → `Stage` | `SETTINGS_MANAGE` |
| `PUT /workspaces/{w}/pipeline-stages/order` | `{ stageIds: [...] }` all `OPEN` stage ids in the new order → `Stage[]` | `SETTINGS_MANAGE` |
| `DELETE /workspaces/{w}/pipeline-stages/{id}` | → 204; 409 `WON`/`LOST`, the last open stage, or a deal in it (archived ones too: `Stage has deals; move them first`). Deals' stage history keeps the stage's name; its id there becomes null | `SETTINGS_MANAGE` |
| `GET /workspaces/{w}/lead-sources` | `?includeArchived` → `LeadSource[]` (`id, name, archived, version`) | member |
| `POST /workspaces/{w}/lead-sources` | `{ name (1-60) }` → 201 | `SETTINGS_MANAGE`; 409 duplicate name |
| `PUT /workspaces/{w}/lead-sources/{id}` | `{ name, archived, version }` | `SETTINGS_MANAGE` |
| `DELETE /workspaces/{w}/lead-sources/{id}` | → 204; 409 while a lead, contact or deal uses it, archived ones too (archive it instead) | `SETTINGS_MANAGE` |

## Companies

`/workspaces/{w}/companies`

| Method and path | Body → response |
|---|---|
| `GET /` | `?q` (name, website, phone), `ownerId`, `industry`, `archived`, paging; `sort`: `NAME` (default, `ASC`), `CREATED_AT`, `UPDATED_AT` → `Page<CompanySummary>` |
| `GET /duplicates` | `?name&website` → up to 5 active companies as `CompanyRef` (`id, name, website?`): same name ignoring case first, then same website host (case and a leading `www.` aside; hosts under 3 characters and a bare `www` match nothing) |
| `POST /` | `CompanyInput` → 201 `Company` |
| `GET /{id}` | → `Company` |
| `PUT /{id}` | `CompanyInput` + `version` → `Company` |
| `POST /{id}/archive`, `POST /{id}/restore` | → `Company` |
| `GET /export` | list filters → `text/csv` (`EXPORT`) |

```ts
CompanyInput   = { name (1-200), industry?, website?, phone?, address?, employeeCount? (0-10000000),
                   ownerId?, notes? }                 // ownerId: see Conventions, Owner
CompanySummary = { id, name, industry?, website?, phone?, owner?: MemberRef, contactCount,
                   openDealCount, archived, updatedAt }
Company        = CompanySummary + { address?, employeeCount?, notes?, archivedAt?, createdAt, version }
```

## Contacts

`/workspaces/{w}/contacts`: the same endpoints as companies (`duplicates` takes `?email&phone` →
up to 5 active contacts as `ContactRef` (`id, fullName, email?, phone?`): same email ignoring case
first, then the same digits of the phone, at least 6 of them), plus `GET /tags` → the tags of the
workspace's active contacts, each once, sorted. List filters: `q` (first and last name, both
together, email, phone, company name), `companyId`, `ownerId`, `sourceId`, `tag` (exactly, any
case), `archived`; `sort`: `NAME` (default; last name, then first name, ignoring case), `COMPANY`
(company name ignoring case), `CREATED_AT`, `UPDATED_AT`. Contacts without a last name (`NAME`) or
a company (`COMPANY`) come last ascending and first descending.

```ts
ContactInput   = { firstName (1-100), lastName? (≤100), email? (≤254, stored lower case), phone? (≤50),
                   jobTitle? (≤100), companyId?, ownerId?, sourceId?, tags? (≤20 once stripped,
                   lower-cased and deduplicated, each 1-40; stored sorted), notes? (≤5000) }
ContactSummary = { id, firstName, lastName?, fullName (first + " " + last), email?, phone?,
                   jobTitle?, company?: Ref, owner?: MemberRef, tags, archived, updatedAt }
Contact        = ContactSummary + { source?: Ref, notes?, archivedAt?, createdAt, version }
```

## Leads

`/workspaces/{w}/leads`: list, create, get, update, archive, restore, export as above. List
filters: `q` (names, company name, email, phone), `status` (repeatable), `ownerId`, `sourceId`,
`priority`, `followUpBefore` (date), `archived`; `sort`: `CREATED_AT` (default, `DESC`),
`UPDATED_AT`, `FOLLOW_UP_DATE`, `PRIORITY`, `NAME`. Converted leads can't be updated (409).
The list returns full `Lead`s. `NAME` is last name, then first name, ignoring case; `PRIORITY`
descending lists HIGH first. Leads without a follow-up date come last in both directions;
`followUpBefore` is inclusive and never matches them. Archiving and restoring are allowed on
converted leads (they are not edits).

| Method and path | Body → response |
|---|---|
| `POST /{id}/status` | `{ status: NEW, CONTACTED, QUALIFIED or UNQUALIFIED, reason? (≤500, required for UNQUALIFIED, dropped for the others), version }` → `Lead`; `CONVERTED` as the target is a 400 (field `status`), a converted lead 409 (both before the version is compared); the lead's current status again is a 200 that changes nothing, except UNQUALIFIED with a different reason, which saves the reason (new version, `STATUS_CHANGE` audit) |
| `POST /{id}/convert` | `LeadConversionInput` → `LeadConversion`; needs the right to edit the lead (403, checked first, on a retry too); 409 unless `QUALIFIED`; repeated after success: the first result with `alreadyConverted: true`, nothing written (an archived record is still listed). An archived `QUALIFIED` lead can still be converted (archiving hides a lead, it doesn't lock it). One transaction: an error (e.g. 400 on `deal.stageId`) leaves no company, contact or deal behind. The new records belong to the lead's owner, or to the caller when the lead has none. Concurrent conversions of one lead run one after the other: the later one gets `alreadyConverted: true`. The lead's activities and tasks join the contact, and the company (onto an existing contact without `companyId`: the contact's) and deal where they have none (a link they have stays), in the same transaction: the contact's timeline and tasks show the lead's history; their `version` and `updatedAt` change; a retry links nothing again. Audit: `CREATE` for each new record, `CONVERT` on the lead (status and `converted*` changes, plus `linkedActivities` / `linkedTasks` `{ from: null, to: <count> }` when there were any; no event per activity or task) |

```ts
LeadInput   = { firstName, lastName?, companyName?, email?, phone?, jobTitle?, sourceId?, ownerId?,
                priority (default MEDIUM), followUpDate?, estimatedValue?, currency?, notes? }
Lead        = { id, ...LeadInput fields, source?: Ref, owner?: MemberRef, status, unqualifiedReason?,
                convertedAt?, convertedCompany?: Ref, convertedContact?: Ref, convertedDeal?: Ref,
                archived, archivedAt?, createdAt, updatedAt, version }
// estimatedValue and currency: both or neither. A value alone takes the workspace's currency (on a
// PUT too: an omitted currency is not kept); a currency alone is a 400 on estimatedValue.
LeadConversionInput = {
  companyId?,            // link to this active company (400 when unknown or archived), or
  createCompany,         // required; create one from companyName (none without a companyName;
                         // ignored when companyId is set)
  contactId?,            // link to this active contact (400 when unknown or archived), else one is
                         // created from the lead's names, email, phone, job title and source, with
                         // the company
  deal?: { name, amount, currency?, stageId?, expectedCloseDate? }   // omit for no deal; the
                         // DealInput rules (an open stage, default the first; the workspace's
                         // currency), errors on deal.<field>; it gets the company, contact,
                         // source and lead
}
LeadConversion = { leadId, company?: Ref, contact: { id, fullName }, deal?: Ref, alreadyConverted }
```

## Deals

`/workspaces/{w}/deals`: list, create, get, update (not the stage), archive, restore, export.
List filters: `q` (name, company name), `stageId`, `status` (`OPEN`, `WON`, `LOST`), `ownerId`,
`companyId`, `contactId`, `closeFrom` / `closeTo` (expected close date), `archived`; `sort`:
`UPDATED_AT` (default, `DESC`), `NAME`, `AMOUNT`, `EXPECTED_CLOSE_DATE`, `CREATED_AT`.
The list returns `DealSummary`s. Without `direction`, `UPDATED_AT`, `CREATED_AT` and `AMOUNT`
descend, `NAME` (ignoring case) and `EXPECTED_CLOSE_DATE` ascend; deals without an expected close
date come last in both directions, and `closeFrom` / `closeTo` are inclusive and never match them.
`q` matches the name or the company's name. A `PUT` has no `stageId`: only a move changes the stage.
Every create and every move to another stage adds a history entry (the first one without
`fromStage`).

| Method and path | Body → response |
|---|---|
| `GET /board` | `?q&ownerId&companyId` → `{ stages: [{ stage: Stage, count, totals: MoneyTotal[], deals: DealSummary[] }] }`, all stages in order (those without deals too), up to 50 active deals each, most recently updated first; `count` and `totals` (per currency, by code) cover every matching active deal of the stage |
| `POST /{id}/move` | `{ stageId, version, lostReason? (≤500, required for LOST, dropped for the others) }` → `Deal`; a stage of another workspace is a 400 (field `stageId`), LOST without a reason a 400 (field `lostReason`), both before the version is compared. Entering `WON` or `LOST` from another kind sets `closedAt`; an open stage clears it and the reason. The current stage again is a 200 that changes nothing, except LOST with a different reason, which saves the reason (new version, `STAGE_CHANGE` audit, no history entry) |
| `GET /{id}/history` | → `StageChange[]` (`fromStage?: HistoryStageRef, toStage: HistoryStageRef, changedBy?: MemberRef, changedAt`), newest first; `HistoryStageRef = { id?, name }` is the stage as it was at the change: its name then (a later rename doesn't change it), its id until the stage is deleted. `fromStage` is null for the first entry, `changedBy` once that person is no member |

```ts
DealInput   = { name (1-200), amount (≥ 0), currency? (default the workspace's, on a PUT too),
                stageId? (create only; an OPEN stage (400 otherwise), default the open stage with
                the lowest position), probability? (0-100), expectedCloseDate?, ownerId?,
                companyId?, contactId?, sourceId?, notes? (≤5000) }
DealSummary = { id, name, amount, currency, stage: Stage-ref { id, name, kind }, status,
                probability, expectedCloseDate?, owner?: MemberRef, company?: Ref,
                contact?: { id, fullName }, closedAt?, archived, updatedAt, version }
Deal        = DealSummary + { probabilityOverride?, source?: Ref, lostReason?, lead?: Ref, notes?,
                archivedAt?, createdAt }
```

## Tasks and reminders

`/workspaces/{w}/tasks`: list, create, get, update, and the status actions below. Tasks are
cancelled, never deleted (there is no `DELETE`). List filters: `q` (title), `status` (repeatable),
`assigneeId`, `mine` (assigned to the caller; with `assigneeId` both must hold), `overdue`
(`true`: open tasks due in the past; `false`: all the others, those without a due time included;
omitted: both), `dueFrom` / `dueTo` (instants, inclusive, never matching tasks without a due
time), `companyId`, `contactId`, `leadId`, `dealId`; `sort`: `DUE_AT` (default, `ASC`; tasks
without a due time come last in both directions), `PRIORITY` (HIGH first descending), `CREATED_AT`,
`UPDATED_AT`. The list returns full `Task`s. Due times are kept in microseconds, as stored. Whoever
may edit a task (its assignee, its creator, or `RECORDS_EDIT_ANY`; 403 otherwise, checked first)
may also complete, reopen and cancel it; an unassigned task whose creator left is a manager's.

| Method and path | Body → response |
|---|---|
| `POST /{id}/complete`, `/reopen`, `/cancel` | `{ version }` → `Task`; a stale version is a 409, compared first. `complete` and `cancel` need an `OPEN` task (`complete` sets `completedAt`), `reopen` a `COMPLETED` or `CANCELLED` one (back to `OPEN`, `completedAt` cleared); any other move, a repeat included, is a 409 that changes nothing. Audit: `COMPLETE`, `REOPEN`, `CANCEL` with the changed fields |
| `GET /workspaces/{w}/reminders` | → `{ overdueTasks, dueSoonTasks (next 24 h), followUpLeads }` of the caller, at most 20 each |

The reminders are the caller's: their `OPEN` tasks due before now (`overdueTasks`, oldest due time
first) and due from now to 24 hours ahead (`dueSoonTasks`, soonest first), and their non-archived
`NEW`, `CONTACTED` or `QUALIFIED` leads whose `followUpDate` is today or earlier (`followUpLeads`,
oldest date first, full `Lead`s). Task times are instants; only "today" for the follow-up date
uses the workspace's time zone.

```ts
TaskInput = { title (1-200), description? (≤5000), dueAt?, priority (default MEDIUM), assigneeId?,
              companyId?, contactId?, leadId?, dealId? }       // assigneeId: see Conventions, Owner
Task      = { id, ...TaskInput fields, assignee?: MemberRef, createdBy?: MemberRef, status,
              overdue, completedAt?, company?: Ref, contact?: { id, fullName }, lead?: { id, fullName },
              deal?: Ref, createdAt, updatedAt, version }
// overdue: computed, OPEN with dueAt in the past. assignee and createdBy are missing once that
// person left the workspace. An edit (PUT) can't change the status: only the actions do.
```

## Activities

`/workspaces/{w}/activities`: `GET` (filters `type`, `companyId`, `contactId`, `leadId`, `dealId`,
`authorId`, `from`, `to`), `POST` (201), `GET /{id}`, `PUT /{id}`, `DELETE /{id}` (204).

- **List**: `page` and `size` as everywhere, always newest `occurredAt` first with the id breaking
  ties the same way (there is no `sort` or `direction`). Every filter given must hold. `from` and
  `to` are instants on `occurredAt`, `from` inclusive and `to` exclusive, so consecutive periods
  neither overlap nor leave a gap. `type` takes one value; an unknown one, or a `from` / `to` that
  isn't an instant, is a 400.
- **Links**: at least one of `companyId`, `contactId`, `leadId`, `dealId`, else 400 with
  `fieldErrors.links`: `Link the activity to a company, contact, lead or deal`. Each must be a
  record of this workspace (400 on that field). Archived records can still be linked: archiving
  hides a record, it doesn't end its history. An edit can't drop the last link either.
- **Company timeline**: a company's timeline includes the activities logged on its contacts and
  deals, because the service fills `companyId` in when the request sends none: the contact's
  company, else the deal's. A `PUT` works the same way: `companyId` null derives it again from the
  links it carries (and clears it when they have no company), a `companyId` keeps it whatever the
  contact's is. It is filled when the activity is written: a contact that later moves to another
  company leaves its past activities on the old one.
- **`occurredAt`** is an instant, stored in microseconds. A new activity without one happens now;
  an edit that leaves it out keeps the current one.
- **Author** is the caller, whatever the body says, and never changes. It is missing once that
  person left the workspace. Only the author or a member with `RECORDS_EDIT_ANY` edits or deletes
  (403, checked before the version and the links; a body that fails bean validation is a 400
  first. The activity of a member who left is for `RECORDS_EDIT_ANY` only); anyone in the
  workspace reads.
- **Audit**: `CREATE` with no changes, `UPDATE` with the changed fields (none, no event), and
  `DELETE`, which keeps what the activity was: every field it had as the `from` of a change (`to`
  null). The label is the subject. `DELETE` takes no version; a deleted activity is a 404.

```ts
ActivityInput = { type: CALL | EMAIL | MEETING | NOTE, subject (1-200), body? (≤5000), occurredAt?
                  (default now), companyId?, contactId?, leadId?, dealId? }  // at least one link
Activity      = { id, ...ActivityInput fields, author?: MemberRef, company?: Ref,
                  contact?: { id, fullName }, lead?: { id, fullName }, deal?: Ref,
                  createdAt, updatedAt, version }
// PUT takes ActivityInput + version (every field; an omitted body or link is cleared)
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
