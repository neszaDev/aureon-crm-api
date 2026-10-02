# API guide

Interactive docs: run the app (`make up`) and open `http://localhost:8080/swagger-ui/index.html`.
The OpenAPI spec is at `/v3/api-docs`.

All endpoints are under `/api/v1`. Errors always use the same JSON shape:

```json
{"timestamp":"...","status":400,"error":"Bad Request","message":"Validation failed",
 "path":"/api/v1/auth/register","fieldErrors":{"email":"must be a well-formed email address"}}
```

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, bad query/path parameter |
| 401 | Missing, invalid or expired access token (with `WWW-Authenticate: Bearer`); wrong email/password; invalid refresh token; token of a deleted user |
| 403 | Valid token without the required role (admin endpoints) |
| 404 | Unknown route, or a resource that doesn't exist *or belongs to another user* |
| 409 | Email already registered; an admin changing or deleting their own account; a concurrent update |
| 413 | Upload larger than `FILES_MAX_SIZE` |
| 415 | Upload type not in `FILES_ALLOWED_TYPES`, or its content doesn't match the type; wrong request `Content-Type` |
| 429 | Too many sign-in or registration attempts; wait `Retry-After` seconds |

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

Over a limit the API answers 429 with `Retry-After` (seconds). Buckets refill gradually, not
all at once. Once an email is blocked, even the right password gets 429 until it refills: that
is what makes password guessing slow from many IPs, at the cost of letting someone delay a
victim's sign-in. Behind a proxy the client IP comes from `X-Forwarded-For` (see
[configuration](configuration.md#rate-limits)).

## Refresh tokens and logout

Access tokens are short-lived (15 minutes by default). Refresh tokens expire after 30 days by default, are opaque random values, and only their SHA-256 hashes are stored in PostgreSQL. A refresh call rotates the refresh token; discard the old value immediately. Logout deletes the supplied refresh-token record. Set `JWT_ACCESS_TOKEN_TTL` and `REFRESH_TOKEN_TTL` to ISO-8601 durations (for example `PT10M` and `P14D`).

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

- Changing a user's email or role signs them out: their refresh tokens are deleted. Their current
  access token keeps working until it expires (15 minutes by default), but the admin endpoints
  check the role in the database, so a demoted admin loses admin access immediately.
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
