# Database and migrations

PostgreSQL 17. **Flyway is the only thing that changes the schema.** Hibernate runs with
`ddl-auto: validate`, so it checks entities against the schema at startup and never alters it.

## Adding a migration

1. Create `src/main/resources/db/migration/V{next}__{description}.sql`, where `{next}` is the
   highest existing version + 1. Example: `V12__add_company_nickname.sql`. Note the **two**
   underscores.
2. Write plain PostgreSQL. Put related changes in one file, and add indexes for new
   foreign keys and query paths.
3. Update the JPA entity so `validate` passes.
4. Run `make verify`. `BoilerplateApplicationIT` fails if a migration doesn't apply or the
   entities don't match the schema.

Migrations run automatically at application startup, in every profile.

## Rules

- **Never edit, rename or delete a migration that has been merged.** Flyway checksums applied
  migrations, and any environment that already ran it will refuse to start. Fix mistakes with
  a new migration.
- Don't switch `ddl-auto` to `update` or `create`.
- Make destructive changes (dropping columns or tables) in two steps across releases: stop
  using the column first, then drop it in a later migration.
- Keep data migrations idempotent where practical, and keep them fast: they run during
  deployment startup.

## Current schema

| Migration | Change |
|---|---|
| `V1__init.sql` | `users` |
| `V2__add_refresh_tokens.sql` | `refresh_tokens` (SHA-256 hash only, FK to users, expiry index) |
| `V3__make_refresh_token_hash_varchar.sql` | `token_hash` CHAR(64) → VARCHAR(64) |
| `V4__add_test_results.sql` | `test_results` (owner FK, score check 0–100, owner/date index) |
| `V5__add_stored_files.sql` | `stored_files` (file metadata; owner FK cascades; the bytes live in `FILES_DIR`) |
| `V6__user_display_name.sql` | `users.display_name` (optional, up to 100 characters) |
| `V7__workspaces.sql` | `workspaces` and `workspace_members` (role check; `UNIQUE (workspace_id, user_id)`, the target of the CRM tables' composite foreign keys; deleting an account cascades to its memberships) |
| `V8__audit_events.sql` | `audit_events` (plain FK to the workspace, no FK to users; a trigger refuses `UPDATE` and `DELETE`; indexes by workspace/time and by entity) |
| `V9__crm_reference_data.sql` | `pipeline_stages` (kind and probability checks, unique name per workspace, one Won and one Lost stage per workspace) and `lead_sources` (unique name per workspace, `archived_at`) |
| `V10__workspace_invitations.sql` | `workspace_invitations` (SHA-256 of the link token, unique; partial unique index: one open invitation per email and workspace) |
| `V11__invitation_invited_by_index.sql` | index on `workspace_invitations(invited_by)`, so deleting an account (`ON DELETE SET NULL`) doesn't scan the table |

Every workspace-owned table has `workspace_id` and extends `WorkspaceOwnedEntity` in JPA. The
tenant boundary, the composite foreign keys and the rest of the CRM tables are described in
[docs/crm/data-model.md](crm/data-model.md). Workspaces are never hard-deleted, so
`audit_events.workspace_id` is a plain foreign key and the trail stays append-only.

## Local database

- Connect with `psql -h localhost -p ${POSTGRES_PORT:-5432} -U boilerplate boilerplate`
  (password from `env/.env`).
- `make db-reset` deletes the local volume. The next `make watch` or `make up` recreates the
  database and reapplies all migrations.
