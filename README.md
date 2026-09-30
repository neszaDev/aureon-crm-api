# Spring Boot + PostgreSQL Boilerplate

A production-oriented Spring Boot 3 / Java 21 starter using PostgreSQL, Flyway, JWT authentication, Actuator, Prometheus metrics, structured production logs, and Docker.

## Run locally

1. Copy `.env.example` to `.env` and set a strong `JWT_SECRET` (at least 32 bytes).
2. Choose one Docker profile:

   - Local hot reload: `make watch`
   - Development image: `make up`
   - Production image: `docker compose --project-directory . -f docker/compose.yml --profile prod up --build`

   Docker files live in `docker/`; the `make` targets pass the compose file paths for you.

   The `local` profile runs Maven and enables Spring DevTools. Changes under `src/` sync into the container, then Maven restarts and recompiles the application automatically. `dev` and `prod` run the packaged, non-root JRE image. Production requires real `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` values in its deployment environment.

3. Visit `http://localhost:8080/swagger-ui/index.html`; health is at `http://localhost:8080/actuator/health`.

To run outside Docker, install Maven 3.9+ and Java 21, start Postgres, then run `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`.

## API quick start

Register:

```sh
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"a-secure-password"}'
```

The response contains a Bearer token. Pass it as `Authorization: Bearer <token>` to secured endpoints; `GET /api/v1/users/me` is included as a protected example.

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

## Configuration

Configuration is environment-driven. Common defaults are in `application.yml`; Compose selects `local`, `dev`, or `prod` automatically. Never commit real secrets: `.env.example` documents local variables only. Production requires at least `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS`.

## Database and tests

Schema changes go in `src/main/resources/db/migration` as immutable, versioned Flyway SQL migrations. Do not use `ddl-auto: update`. Run `mvn verify` for unit tests plus the Testcontainers/PostgreSQL integration test (Docker must be available).

## Shipping

The Docker image is a multi-stage Maven build with a non-root JRE runtime. GitHub Actions runs tests and validates an image build. Tag releases using semantic versioning (for example `v1.2.0`) and pass `APP_VERSION` at deployment time.
