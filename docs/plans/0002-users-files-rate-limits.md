# User management, file uploads and login rate limiting

Status: in progress (2026-10-02). Branch `feat/users-files-rate-limits`, one PR into `dev`, one
commit per feature. The frontend follows in its own PR once the `:dev` image is published.

## Why

The API had sign-in and one owner-scoped CRUD example, but no way to manage accounts, no file
handling, and nothing to stop password guessing. These are the three features almost every app
built on this boilerplate needs next. The `ADMIN` role existed but nothing checked it.

## Decisions

| Topic | Decision | Rejected |
|---|---|---|
| Admin access | `/api/v1/users/**` (except `/me`) requires `ROLE_ADMIN` in the token, **and** the service re-checks the caller's role in the database. | Token claim only: a demoted or deleted admin would keep access until the access token expires (15 min). |
| First admin | `ADMIN_EMAIL` + `ADMIN_PASSWORD` create the account at startup if it doesn't exist. An existing non-admin account is **never** promoted. | Promoting a configured email: there is no email verification, so anyone who registers that address first would become admin. |
| Lock-out | An admin can't change or delete their own account through the admin endpoints, so at least one admin always remains. | A "last admin" count check: more code for the same guarantee. |
| Revocation | Changing a user's role or email, or deleting them, deletes their refresh tokens. | |
| File storage | Local disk behind a `FileStorage` interface (`LocalFileStorage`). Keys are random UUIDs, so client file names never reach the filesystem. One instance only; an S3 implementation can replace it without touching callers. | S3/MinIO now: another service in compose and CI for a single-instance boilerplate. Postgres `bytea`: bloats the database and backups. |
| File types | Allowlist (`FILES_ALLOWED_TYPES`) plus a signature check for binary types (PNG, JPEG, GIF, WebP, PDF) and a no-NUL-bytes check for text. Downloads are always `attachment`, `nosniff`, `Content-Security-Policy: sandbox`. | Apache Tika: a large dependency for a short allowlist. |
| Blob cleanup | Rows are deleted in the transaction; blobs after commit. Deleting a user publishes `UserDeletionEvent` so `file/` removes that user's blobs (rows cascade in the database). | Deleting blobs inside the transaction: a rollback would leave rows pointing at missing files. |
| Rate limiting | Bucket4j token buckets in a bounded Caffeine cache: login per IP, failed logins per email, register per IP. 429 + `Retry-After`. In-memory, so limits are per instance. | Postgres-backed counters: a write on every login. Hand-written buckets: more code to get right. Redis: a new service. |
| Client IP | `server.forward-headers-strategy: native`: Tomcat takes the client IP from `X-Forwarded-For` only when the connection comes from a trusted proxy (`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`, default: private and loopback ranges). The Next.js server is such a proxy. | Reading `X-Forwarded-For` ourselves: easy to get wrong and spoofable. Using the connection IP: behind the frontend every user shares one IP. |

New dependencies: `com.bucket4j:bucket4j_jdk17-core` (rate limiting) and
`com.github.ben-manes.caffeine:caffeine` (bounded bucket cache, version managed by Spring Boot).

## API

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/api/v1/users?page&size&q` | admin | `q` matches part of the email; newest first |
| GET | `/api/v1/users/{id}` | admin | |
| PATCH | `/api/v1/users/{id}` | admin | `{email?, role?}`; 409 on a taken email or on your own account |
| DELETE | `/api/v1/users/{id}` | admin | 204; cascades test results, refresh tokens and files |
| POST | `/api/v1/files` | user | multipart `file`; 201; 413 too large, 415 type not allowed |
| GET | `/api/v1/files?page&size` | user | own files, newest first |
| GET | `/api/v1/files/{id}` | user | metadata |
| GET | `/api/v1/files/{id}/content` | user | download |
| DELETE | `/api/v1/files/{id}` | user | 204 |

Another user's file is a 404, as for test results.

## Out of scope (follow-ups)

- Self-service password change and account deletion (`/users/me`).
- Disabling accounts without deleting them.
- Per-user storage quota; S3 storage; virus scanning.
- Shared rate-limit state for more than one instance (Bucket4j has Redis/Postgres backends).
