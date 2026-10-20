# API guide

Interactive docs: run the app (`make up`) and open `http://localhost:8080/swagger-ui/index.html`.
The OpenAPI spec is at `/v3/api-docs`. Under the `prod` profile both are off unless
`SPRINGDOC_ENABLED=true`.

All endpoints are under `/api/v1`. This guide covers accounts, sign-in, files and the
`test-results` example. The CRM endpoints (workspaces, members, invitations, pipeline stages,
lead sources and the rest of the CRM) are in the [CRM API contract](crm/api.md); see
[Workspaces](#workspaces) below for a first request. Errors always use the same JSON shape:

```json
{"timestamp":"...","status":400,"error":"Bad Request","code":"VALIDATION_FAILED",
 "message":"Validation failed","path":"/api/v1/auth/register",
 "fieldErrors":{"email":"must be a well-formed email address"}}
```

`code` says what went wrong in a form that doesn't change, for clients that translate messages
(`message` stays English); errors without one (401, malformed JSON, 500, ...) leave it out. The
codes and their statuses are listed in the [CRM API contract](crm/api.md#conventions).

`path` is the route template when one matched (e.g.
`/api/v1/workspaces/{workspaceId}/pipeline-stages/{id}`), otherwise (an unknown URL, or a request
refused before routing) the request path with invitation tokens masked.

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, bad query/path parameter; a URL the security firewall rejects (`//`, `;x=1`, an encoded `..`), with message `Malformed request`. An encoded NUL or slash in the path (`%00`, `%2F`) is refused by Tomcat itself, before the application: a 400 with Tomcat's HTML page, not an `ApiError` |
| 401 | Missing, invalid or expired access token (with `WWW-Authenticate: Bearer`); wrong email/password; invalid refresh token; token of a deleted user, of a user whose email changed since, or of an earlier token version (an email, role or password change since) |
| 403 | Valid token without the required role (admin endpoints) or workspace permission; `POST /auth/register` when `REGISTRATION_ENABLED=false` |
| 404 | Unknown route, or a resource that doesn't exist *or belongs to another user*; anything under a workspace the caller is not a member of |
| 409 | Email already registered; an admin changing or deleting their own account; a concurrent update; a rule of the CRM (last owner, duplicate name, an invitation that is no longer pending, ...) |
| 413 | Upload larger than `FILES_MAX_SIZE` |
| 415 | Upload type not in `FILES_ALLOWED_TYPES`, or its content doesn't match the type; wrong request `Content-Type` |
| 429 | Too many sign-in, registration or refresh attempts; wait `Retry-After` seconds |

## Quick start

Register:

```sh
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"a-secure-password"}'
```

The response contains a Bearer token. Pass it as `Authorization: Bearer <token>` to secured endpoints; `GET /api/v1/users/me` is included as a protected example.

## Rate limits

| Endpoint | Limit (default) | Key |
|---|---|---|
| `POST /auth/login` | 20 per minute | client IP |
| `POST /auth/login` | 10 **failed** attempts per 15 minutes | email |
| `POST /auth/register` | 10 per hour | client IP |
| `POST /auth/refresh` | 30 per 15 minutes | user (the refresh token's) |

Over a limit the API answers 429 with `Retry-After` (seconds). Buckets refill gradually, not
all at once. Once an email is blocked, even the right password gets 429 until it refills: that
is what makes password guessing slow from many IPs, at the cost of letting someone delay a
victim's sign-in. Behind a proxy the client IP comes from `X-Forwarded-For` (see
[configuration](configuration.md#rate-limits)). Refreshes are counted per user, not per IP: the
frontend's server makes that call, so every client would share its IP.

## Workspaces

Every CRM endpoint lives under `/api/v1/workspaces/{workspaceId}` and needs a member of that
workspace. Anyone signed in can create a workspace and becomes its owner; others join through an
invitation link ([CRM API contract](crm/api.md#workspaces-members-invitations)).

```sh
# $ACCESS_TOKEN: the accessToken from register or login
# Create a workspace; it starts with default pipeline stages and lead sources
curl --request POST 'http://localhost:8080/api/v1/workspaces' \
  --header "Authorization: Bearer $ACCESS_TOKEN" --header 'Content-Type: application/json' \
  --data-raw '{"name":"Acme","defaultCurrency":"EUR","timezone":"Europe/Paris"}'

# Read its pipeline (use the id from the response)
curl 'http://localhost:8080/api/v1/workspaces/<workspaceId>/pipeline-stages' \
  --header "Authorization: Bearer $ACCESS_TOKEN"
```

A caller who is not a member gets 404, the same answer as for a workspace that doesn't exist.

## Refresh tokens and logout

Access tokens are short-lived (15 minutes by default). Refresh tokens expire after 30 days by default, are opaque random values, and only their SHA-256 hashes are stored in PostgreSQL. A refresh call rotates the refresh token; discard the old value immediately. Set `JWT_ACCESS_TOKEN_TTL` and `REFRESH_TOKEN_TTL` to ISO-8601 durations (for example `PT10M` and `P14D`).

A sign-in (register, login, registration through an invitation, a password change) starts a
**session**: a family of refresh tokens. Each refresh adds a token to the family and keeps the old
one as used. The responses don't change; the rules do:

- A used refresh token presented again within 30 seconds of its use (`REFRESH_REUSE_GRACE`) is a
  401 for that request only: two tabs that refresh together don't sign the user out, and the
  newer token keeps working.
- Presented later, it is treated as stolen: the whole family is revoked, so whoever holds the
  newer token is signed out too (401), and the user signs in again.
- A family can't rotate more than 90 days after its sign-in (`SESSION_MAX_AGE`): 401, sign in
  again. Rotating doesn't restart that clock.
- Logout ends the session: every token of the family. A password change still revokes every
  session of the user, and an admin's email or role change too.
- Every token carries the user's token version, which an email, role or password change moves
  on: a refresh token of an earlier version can't rotate (its family is deleted, 401), even one
  that a rotation running during the change created.
- Rotations are limited to 30 per 15 minutes per user (429 with `Retry-After`). Only successful
  rotations count toward it: a replay, an expired or revoked token and the losers of parallel
  refreshes don't, so a thief can't drain the budget to keep the victim's replay from revoking the
  family. The web frontend refreshes when its access cookie expires, about every 14.5 minutes of
  activity: a few per 15 minutes, far under the limit.
- Each rotation keeps its used row until it expires (30 days), so a session's rows grow with its
  rotations: with the limit, at most 30 per 15 minutes per user (about 86,000 over 30 days in
  the worst case; a user active 8 hours a day leaves about 1,000). Expired rows, used or not, are
  deleted on every sign-in and rotation (no scheduled job), and a family's rows all go when it is
  revoked or logged out. Follow-up: one row per family (the current token and the hashes it
  replaced) instead of one per rotation.

**Upgrading (release note):** access tokens from before this version have no `uid` or `ver`
claim and get 401. Refresh tokens survive the migrations (each becomes a one-token session at
version 0), so a client that refreshes on 401 recovers by itself. Web users active within about
15 minutes before the deploy are signed out once; other sessions continue through their refresh
token.

Copy these requests into a terminal or Thunder Client's cURL importer. Replace the values in angle brackets with values from the preceding response.

```sh
# 1. Register once (or use login below for an existing account)
curl --request POST 'http://localhost:8080/api/v1/auth/register' \
  --header 'Content-Type: application/json' \
  --data-raw '{"email":"me@example.com","password":"a-secure-password"}'

# 2. Login: save accessToken and refreshToken from the JSON response
curl --request POST 'http://localhost:8080/api/v1/auth/login' \
  --header 'Content-Type: application/json' \
  --data-raw '{"email":"me@example.com","password":"a-secure-password"}'

# 3. Call a protected endpoint
curl --request GET 'http://localhost:8080/api/v1/users/me' \
  --header 'Authorization: Bearer <accessToken>'

# 4. Refresh: replace both saved tokens with the response values; the old refresh token is invalid now
curl --request POST 'http://localhost:8080/api/v1/auth/refresh' \
  --header 'Content-Type: application/json' \
  --data-raw '{"refreshToken":"<refreshToken>"}'

# 5. Logout: the same refresh token can no longer be used
curl --request POST 'http://localhost:8080/api/v1/auth/logout' \
  --header 'Content-Type: application/json' \
  --data-raw '{"refreshToken":"<refreshToken>"}'
```

## Test-result CRUD example

`TestResult` is an owner-scoped sample feature for a frontend table and graph. All requests require an access token. The list response is paginated (`content` is the row array); the summary response is ready for a pie/bar chart using `byStatus`.

```sh
# Set this in your shell after login, or replace $ACCESS_TOKEN in Thunder Client.
export ACCESS_TOKEN='<accessToken>'

# Create a row
curl --request POST 'http://localhost:8080/api/v1/test-results' \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header 'Content-Type: application/json' \
  --data-raw '{"testName":"Blood pressure check","status":"PASSED","score":92.5,"testedAt":"2026-08-25T10:30:00Z","notes":"Routine check"}'

# Table data: pagination, newest test first
curl --request GET 'http://localhost:8080/api/v1/test-results?page=0&size=20' \
  --header "Authorization: Bearer $ACCESS_TOKEN"

# Chart data: total plus counts for PENDING, PASSED, and FAILED
curl --request GET 'http://localhost:8080/api/v1/test-results/summary' \
  --header "Authorization: Bearer $ACCESS_TOKEN"

# Read, edit, then delete one row
curl --request GET 'http://localhost:8080/api/v1/test-results/<id>' \
  --header "Authorization: Bearer $ACCESS_TOKEN"

curl --request PATCH 'http://localhost:8080/api/v1/test-results/<id>' \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header 'Content-Type: application/json' \
  --data-raw '{"testName":"Blood pressure check","status":"FAILED","score":45.0,"testedAt":"2026-08-25T10:30:00Z","notes":"Needs follow-up"}'

curl --request DELETE 'http://localhost:8080/api/v1/test-results/<id>' \
  --header "Authorization: Bearer $ACCESS_TOKEN"
```

## Your account

```sh
# Set or clear your display name (trimmed; blank or missing clears it; up to 100 characters)
curl --request PUT 'http://localhost:8080/api/v1/users/me' \
  --header "Authorization: Bearer $ACCESS_TOKEN" --header 'Content-Type: application/json' \
  --data-raw '{"displayName":"Ada Lovelace"}'

# Change your password; the response is a new token pair, like login
curl --request POST 'http://localhost:8080/api/v1/users/me/password' \
  --header "Authorization: Bearer $ACCESS_TOKEN" --header 'Content-Type: application/json' \
  --data-raw '{"currentPassword":"a-secure-password","newPassword":"another-password"}'
```

- `GET` and `PUT /users/me` return `id`, `email`, `displayName` (absent when unset), `role` and
  `createdAt`.
- A wrong `currentPassword` is a 400 with `fieldErrors.currentPassword`, never a 401: a 401 would
  tell the client its session is gone. Wrong attempts count toward the per-email sign-in failure
  limit (429 once it is used up).
- `newPassword` follows the registration rules: 6-72 characters and at most 72 bytes in UTF-8
  (24 Thai characters). Longer is a 400 on `newPassword` (`password` when registering).
- On success every refresh token of the user is revoked and a new pair is returned, so other
  devices must sign in again. Access tokens issued before the change get 401 at once: they carry
  the previous token version. The new pair carries the new one.

## User management (admins)

Every `/api/v1/users` endpoint except `/me` needs the `ADMIN` role. The first admin comes from
`ADMIN_EMAIL` / `ADMIN_PASSWORD` (see [configuration](configuration.md#first-admin)); admins can
then promote others.

```sh
export ADMIN_TOKEN='<accessToken of an admin>'

# List, newest first; q matches part of the email (case-insensitive)
curl 'http://localhost:8080/api/v1/users?page=0&size=20&q=example' \
  --header "Authorization: Bearer $ADMIN_TOKEN"

curl 'http://localhost:8080/api/v1/users/<id>' --header "Authorization: Bearer $ADMIN_TOKEN"

# Change role and/or email (omit a field to keep it)
curl --request PATCH 'http://localhost:8080/api/v1/users/<id>' \
  --header "Authorization: Bearer $ADMIN_TOKEN" --header 'Content-Type: application/json' \
  --data-raw '{"role":"ADMIN"}'

# Delete, with their test results, files and sessions
curl --request DELETE 'http://localhost:8080/api/v1/users/<id>' \
  --header "Authorization: Bearer $ADMIN_TOKEN"
```

- Changing a user's email or role signs them out: their refresh tokens are deleted and their
  access tokens get 401 at once. Requests take the role from the database, not from the token, and
  the admin endpoints re-check it, so a demoted admin loses admin access immediately.
- An admin can't change or delete their own account here (409), so there is always one admin.

## Files

Each user uploads, lists, downloads and deletes their own files. Another user's file is a 404.

```sh
# Upload (multipart field "file"): 201 with id, name, contentType, size, createdAt
curl --request POST 'http://localhost:8080/api/v1/files' \
  --header "Authorization: Bearer $ACCESS_TOKEN" --form 'file=@report.pdf'

curl 'http://localhost:8080/api/v1/files?page=0&size=20' --header "Authorization: Bearer $ACCESS_TOKEN"
curl 'http://localhost:8080/api/v1/files/<id>' --header "Authorization: Bearer $ACCESS_TOKEN"
curl -OJ 'http://localhost:8080/api/v1/files/<id>/content' --header "Authorization: Bearer $ACCESS_TOKEN"
curl --request DELETE 'http://localhost:8080/api/v1/files/<id>' --header "Authorization: Bearer $ACCESS_TOKEN"
```

- Only types in `FILES_ALLOWED_TYPES` are accepted (default: PNG, JPEG, GIF, WebP, PDF, plain
  text, CSV). PNG, JPEG, GIF, WebP and PDF must also start with that format's signature, and text
  must not contain NUL bytes; anything else is a 415.
- Only the base name of the client's file name is kept (no paths or control characters). Bytes
  are stored under a random key in `FILES_DIR`, never under that name.
- Downloads are always `Content-Disposition: attachment` with `X-Content-Type-Options: nosniff`
  and `Content-Security-Policy: sandbox`, so an uploaded file can't run as a page.
- Deleting a file, or its owner, removes the stored bytes after the database change commits.
