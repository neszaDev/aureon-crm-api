# Roles and permissions

Workspace roles are separate from the boilerplate's account role (`users.role`: `USER`/`ADMIN`).
A system `ADMIN` manages accounts but has **no** access to any workspace it is not a member of.

## Enforcement

1. Every CRM endpoint lives under `/api/v1/workspaces/{workspaceId}/...`.
2. `WorkspaceAccessInterceptor` runs before the controller. It looks up the caller's membership
   of `{workspaceId}` in the database (one indexed query). **No membership: 404**, exactly like an
   unknown workspace, so ids can't be probed. With a membership, it stores the workspace id and
   role for the request (`WorkspaceContext`).
3. Hibernate's `@TenantId` on every workspace-owned entity adds `workspace_id = <current>` to every
   query and sets it on insert, so a repository query can't return another workspace's rows even
   if a filter is forgotten. Requests without a workspace context use a tenant id that matches
   nothing. There is no root tenant: code outside a request (the demo seeder, tests) runs each
   workspace's inserts inside `WorkspaceContext.runAs(...)`, which refuses to run inside a
   transaction.
   This covers load by id too: in Hibernate 6.6.53, `findById` / `EntityManager.find` add the
   tenant restriction, so another workspace's id is not found (404) and services use the plain
   repository `findById` (verified by `TenantIsolationIT`). Hibernate does not re-check the tenant
   of an entity it already holds, so an entity loaded through native SQL could be changed across
   workspaces: never load workspace-owned entities with native SQL.
4. Composite foreign keys `(workspace_id, x_id)` make it impossible to store a link to a record,
   stage, source or member of another workspace.
5. Services check permissions with `WorkspaceAccess.require(Permission)`, ownership rules with
   `WorkspaceAccess.requireEditable(ownerId)` (a task: `requireEditableBy(assigneeId, createdBy)`) and owner changes with `ownerForCreate` /
   `ownerForUpdate`. Lacking a permission: **403**.
6. The role is read from the database on every request, so a role change or removal takes effect
   on the next request, without waiting for the access token to expire.

Native SQL (used only for reports) is not filtered by Hibernate and must bind the workspace id
explicitly; each such query has a cross-workspace test.

## Matrix

| Permission | Owner | Admin | Manager | Sales rep |
|---|:-:|:-:|:-:|:-:|
| Read the workspace, members, records, reports, record history | ✓ | ✓ | ✓ | ✓ |
| Create companies, contacts, leads, deals, tasks, activities | ✓ | ✓ | ✓ | ✓ |
| Edit, archive, restore, convert, move **any** record (`RECORDS_EDIT_ANY`) | ✓ | ✓ | ✓ | |
| Edit, archive, restore, convert, move records **they own** (or tasks assigned to them / created by them, activities they wrote) | ✓ | ✓ | ✓ | ✓ |
| Assign a record or task to someone else (`RECORDS_ASSIGN`) | ✓ | ✓ | ✓ | |
| Export CSV (`EXPORT`) | ✓ | ✓ | ✓ | |
| Workspace profile, currency, time zone (`WORKSPACE_MANAGE`) | ✓ | ✓ | | |
| Pipeline stages and lead sources (`SETTINGS_MANAGE`) | ✓ | ✓ | | |
| Invite, change roles, remove members (`MEMBERS_MANAGE`) | ✓ | ✓ (not owners) | | |
| Read the workspace audit trail (`AUDIT_READ`) | ✓ | ✓ | | |

Rules beyond the matrix:

- An admin can't invite, promote to, demote or remove an **owner**. Only owners manage owners.
- Invitations: an admin can't create or revoke an **owner** invitation (403). Revoking an accepted
  or an already revoked invitation is 409; an expired one can be revoked. The pending list hides
  expired invitations and shows the newest first.
- The last owner can't be demoted or removed, and can't leave. An account that is the last owner
  of a workspace can't be deleted by a system admin (409) until another owner exists.
- Anyone can leave a workspace (remove their own membership), except the last owner.
- Owner (tasks: assignee): on create, none given means the caller; giving anyone else needs
  `RECORDS_ASSIGN`. On update the request carries the owner: the current one keeps it (no
  permission needed), another member reassigns and null unassigns, both with `RECORDS_ASSIGN`
  only. A new owner must be a member of the workspace (400). So a sales rep owns what they create
  and can't hand it on. An unowned record (its owner left the workspace) can be edited only with
  `RECORDS_EDIT_ANY`.
- Tasks: editing needs `RECORDS_EDIT_ANY`, or being the task's assignee or its creator
  (`WorkspaceAccess.requireEditableBy`); completing, reopening and cancelling are edits. Another
  member only reads it. Tasks are never deleted.
- Removing a member keeps their records: owner/assignee/author/creator fields become empty
  ("Unassigned"), and managers can reassign them.

The permissions of the caller are returned with the workspace (`GET /workspaces/{id}` →
`permissions`), so the frontend can hide actions the caller can't take. The backend checks them
regardless.
