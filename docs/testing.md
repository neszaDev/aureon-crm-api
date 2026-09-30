# Testing

| Kind | Naming | Runner | Needs | Run |
|---|---|---|---|---|
| Unit | `*Test` | Surefire (`test` phase) | nothing | `make test` |
| Integration | `*IT` | Failsafe (`integration-test` / `verify`) | Docker | `make verify` |

The naming rule matters: Surefire is configured to exclude `*IT`, and Failsafe only runs
`*IT`. A class named `FooIntegrationTest` would run as a unit test and fail without Docker.

## Unit tests

Plain JUnit 5 + Mockito + AssertJ. No Spring context. Construct the class under test directly,
as in `auth/JwtServiceTest` and `auth/RefreshTokenServiceTest`. To test configuration binding,
use `ApplicationContextRunner` (see `auth/AuthPropertiesTest`).

## Integration tests

Extend `support/AbstractIntegrationTest`. It provides:

- the full application context with the `test` profile and the real security filter chain
- `MockMvc` (`mvc`) and `ObjectMapper` (`json`)
- a real `postgres:17-alpine` from `support/PostgresTestcontainersConfig`, connected via
  `@ServiceConnection`, with Flyway migrations applied
- helpers: `uniqueEmail()`, `register(email)`, `registerAndGetAccessToken()`, `bearer(token)`

All IT classes share **one** Spring context and one database, and nothing is cleaned between
tests. Isolate tests with fresh users (`uniqueEmail()`), and never assert on global counts.

What the existing ITs cover:

| Class | Covers |
|---|---|
| `BoilerplateApplicationIT` | Migrations applied, schema validates |
| `auth/AuthFlowIT` | Register → login → refresh rotation → logout, error paths |
| `security/SecurityIT` | Public vs protected endpoints, `X-Request-Id` |
| `testresult/TestResultIT` | CRUD, pagination, summary, owner isolation |
| `common/ErrorHandlingIT` | Client errors map to 4xx with the `ApiError` shape |

## Reports

After `make verify`:

- `target/surefire-reports/`, `target/failsafe-reports/`: per-class results
- `target/site/jacoco/index.html`: coverage for unit + integration tests combined

CI uploads the same files as the `test-reports` artifact. They are never committed.

## Troubleshooting

- **`Could not find a valid Docker environment`**: start Docker. Testcontainers needs 1.21.4
  or newer for Docker Engine 29 (pinned in `pom.xml`).
- **`No qualifying bean of type 'UserMapper'` locally but not in CI**: the VS Code Java
  extension rebuilds `target/classes` while Maven runs, and races the MapStruct-generated
  class. Re-run without `clean`, or run from a fresh clone. It is not a code problem.
- **Port 5432 already allocated** (`make watch` / `make up`): set `POSTGRES_PORT` in `.env`.
  Tests are unaffected, because Testcontainers picks a random port.
