# Aureon CRM: product specification

Aureon CRM is a multi-user CRM starter for small and medium-sized businesses. This repository is
its API (Aureon CRM API, built from `spring-postgres-boilerplate`); the sibling frontend is
[aureon-crm-web](https://github.com/neszaDev/aureon-crm-web). "Aureon CRM" is a placeholder name:
derived products rename it (frontend `AppConfig.name` and locale copy).

| Document | Contents |
|---|---|
| this file | scope, users, lifecycles, rules every module follows |
| [data-model.md](data-model.md) | tables, relationships, tenant boundary, archival, money |
| [permissions.md](permissions.md) | roles, the permission matrix, how it is enforced |
| [api.md](api.md) | the REST contract the frontend codes against |
| [../plans/0004-crm.md](../plans/0004-crm.md) | phases, slices, acceptance checklist, status |

## Who uses it

- **Owner**: created the workspace (the account of a business). Manages everything, including
  other owners.
- **Admin**: runs the workspace day to day: members, roles, pipeline and reference data.
- **Manager**: leads a sales team. Works with every record, reassigns work, exports data.
- **Sales representative**: works their own leads, deals and tasks; reads the rest of the
  workspace so the team can collaborate.

## Goals

1. A coherent CRM: companies, contacts, leads, deals, tasks and activities that link to each other,
   with a dashboard and reports computed from the stored data.
2. Strict tenant isolation: a workspace's data is reachable only by its members, enforced in the
   API and backed by database constraints.
3. A template another developer can clone, run with documented commands, understand and extend.

## Non-goals (first release)

- Email sending, calendar sync, telephony, file attachments on CRM records, custom fields defined
  by users, multiple pipelines per workspace, currency conversion, billing or plans.
- Deleting a workspace: workspaces are never hard-deleted, which keeps the audit trail append-only
  (see [data-model.md](data-model.md)).
- Public marketing site beyond the existing landing and about pages.

## Anti-goals

- Anything that looks like it works but does not persist (optimistic UI that hides a failed write).
- Authorization only in the UI.
- Statistics that imply history the data does not have.

## Workspaces

- Any signed-in user can create a workspace and becomes its **owner**. A user can belong to many
  workspaces with a different role in each; the frontend puts the workspace id in the URL.
- People join only through an **invitation link** created by an owner or admin (see
  [permissions.md](permissions.md)). There is no email service: the admin copies the link and
  shares it. A link works once, expires after 7 days, and only for the invited email address.
- A workspace always has at least one owner.
- Workspaces have a name, a default currency (ISO 4217) and a time zone (IANA). The time zone
  decides what "today", "overdue follow-up" and monthly report buckets mean.

## Lifecycles

### Lead

| Status | Meaning |
|---|---|
| `NEW` | captured, nobody has talked to them yet |
| `CONTACTED` | first contact made |
| `QUALIFIED` | worth pursuing; may be converted |
| `UNQUALIFIED` | not a fit (a reason is recorded) |
| `CONVERTED` | turned into a contact (and possibly a company and a deal); terminal and read-only |

Any of the first four may move to any other of the four. `CONVERTED` is reached only through
conversion, which requires `QUALIFIED`. Conversion runs in one transaction: it links to or creates
the company, links to or creates the contact, optionally creates a deal, and marks the lead
converted. Retrying a conversion that already happened returns the existing result and creates
nothing. The new records belong to the lead's owner; an unassigned lead's go to whoever converts
it (only a member who may edit any record can).

### Deal

A deal sits in one **pipeline stage**. Stages have a kind: `OPEN`, `WON` or `LOST`. A deal's status
is its stage's kind. Every workspace has exactly one `WON` and one `LOST` stage (renamable, not
deletable) and at least one `OPEN` stage. Defaults for a new workspace:

| Position | Stage | Kind | Probability |
|---|---|---|---|
| 1 | Qualification | OPEN | 10% |
| 2 | Needs analysis | OPEN | 25% |
| 3 | Proposal | OPEN | 50% |
| 4 | Negotiation | OPEN | 75% |
| 5 | Won | WON | 100% |
| 6 | Lost | LOST | 0% |

Moving a deal into `WON` or `LOST` records `closedAt`; moving to `LOST` needs a reason. Moving a
closed deal back to an open stage reopens it (clears `closedAt` and the reason). Every stage
change is kept in the deal's stage history, which feeds the funnel report. A deal's probability
is its own override, or else its stage's probability.

### Task

`OPEN` → `COMPLETED` or `CANCELLED`; both can be reopened. Overdue means `OPEN` with `dueAt` in the
past. In-app reminders show the caller's overdue tasks, tasks due within 24 hours, and their leads
whose follow-up date is today or earlier. Nothing is emailed.

### Records: archive, never delete

Companies, contacts, leads and deals are **archived**, not deleted: they disappear from lists and
pickers but keep their history and links, and can be restored. Tasks are cancelled, not deleted.
Activities (call, email, meeting and note logs) can be edited or deleted by their author or a
manager; deletions are audited. Pipeline stages and lead sources can be deleted only while nothing
uses them.

## Money

Amounts are decimal (`NUMERIC(15,2)` in PostgreSQL, `BigDecimal` in Java, a string such as
`"1250.00"` in JSON) with an ISO 4217 currency. Nothing converts between currencies: totals are
reported per currency. No floating point is used for money anywhere.

## Search, filters, sorting, pagination

Every list is paginated on the server (`page` from 0, `size` 1-100), sorted by an allow-listed
field with the record id as a tie-breaker so pages are stable, and filtered by query parameters
validated on the server. Text search is a case-insensitive substring match on the fields listed
per endpoint in [api.md](api.md).

## Audit

Important changes (records created, edited, archived, restored, converted, stage moves, task
completion, membership, roles, invitations and settings) are written to an append-only audit trail
in the same transaction as the change. Owners and admins can read the whole trail; every member can
read the history of a record they can see.
