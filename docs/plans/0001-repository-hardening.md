# Repository Hardening Plan — spring-postgres-boilerplate

> The first implementation step copies this file to `docs/plans/0001-repository-hardening.md`
> in the repo, so future developers and agents can see why each change was made.

## Context

`main` was force-reset on 2026-09-30 to `b4b1fb3` ("Initial Spring Boot boilerplate").
The 323 commits after it are preserved on `origin/backup/before-reset-20260930`. That
history went wrong in predictable ways: 12 overlapping CI workflows, committed logs, patches,
zips and jars, UTF-8 BOM fights with the formatter, and debug commits straight to `main`.

The goal is to make this "the same project, engineered like a serious production repo". We
add only what pays for itself in a **Java 21 / Maven / Spring Boot 3.5 / PostgreSQL / Flyway**
project. The reference repo is a Node/TypeScript project, so most of its tooling is replaced
by Java-native equivalents or skipped.

### Changes to the original request
- **Translate the tooling instead of copying it.** Use Spotless for Prettier/ESLint, JUnit 5 +
  Testcontainers + Failsafe for Jest, the compiler for `tsc`, the Maven Wrapper for pnpm, and
  shell hooks via `core.hooksPath` for Husky/commitlint. Do not add Node.js to a Java repo just
  to run Git hooks.
- **Add a guardrail against the previous failure.** Each phase is one branch and one PR, and CI
  must be green before merge. No debug commits, logs or patches in git.
- **Fix real bugs found in the audit first.** Quality gates are worthless while prod logging is
  broken and the integration test silently never runs.
- **Use one agent-instruction source.** Only Claude Code is used, so `AGENTS.md` is the single
  source of truth and `CLAUDE.md` imports it. No GEMINI.md, `.codex/` or `.agents/`.
- **Make deployment readiness a runnable check.** CI runs a container smoke test with the prod
  profile. A checklist item alone would not have caught the logback bug.

---

## Phase 1 — Audit (completed, read-only)

### CURRENT (what exists and works)
| Area | State |
|---|---|
| Architecture | Package-by-feature (`auth`, `user`, `testresult`) plus `common`, `security`. Controller → service → repository. DTOs are records, and entities are never returned. Documented in `ARCHITECTURE.md`. |
| Build | Maven, Spring Boot parent 3.5.5, Java 21, MapStruct processor. |
| Database | Flyway V1–V4 in `src/main/resources/db/migration`, and `ddl-auto: validate`. This is correct. |
| Config | Spring profiles `local` / `dev` / `test` / `prod`. Prod has no defaults for secrets. `.env.example` exists. |
| Docker | Multi-stage `Dockerfile` (local / build / runtime) with a non-root JRE runtime. Compose has `local` (watch + devtools), `dev` and `prod` profiles and a Postgres healthcheck. |
| CI | One workflow, `.github/workflows/ci.yml`: `mvn verify` plus `docker build`. |
| Ops | Actuator, Prometheus, `X-Request-Id` correlation filter and MDC, JSON logs in prod (intended). |
| Docs | `README.md` (run instructions, curl API walkthrough), `ARCHITECTURE.md`. |
| Local tooling | JDK 21.0.12, Maven 3.9.16, Docker 29.7 installed. The Docker daemon was not running during the audit. |

### BUGS (fix before building gates on top)
1. **Prod logging is broken.** `logback-spring.xml` uses
   `net.logstash.logback.appender.LogstashConsoleAppender`, and that class does not exist in
   logstash-logback-encoder 8.1 (checked the jar). The prod profile will fail logback init.
   Fix: `ch.qos.logback.core.ConsoleAppender` with `LogstashEncoder`.
2. **The integration test never runs.** `BoilerplateApplicationIT` ends in `IT`, Surefire only
   runs `*Test`, and Failsafe is not configured. The README claims `mvn verify` runs it.
3. **The compose override has the wrong logger name.** It uses
   `LOGGING_LEVEL_COM_EX_EXAMPLE_BOILERPLATE` instead of `LOGGING_LEVEL_COM_EXAMPLE_BOILERPLATE`,
   so it has no effect today.
4. **The Dockerfile copies twice.** The `build` stage re-copies `src` that it already inherited
   from `local`. Harmless, but it confuses readers.

### MISSING (genuinely useful)
- **Maven Wrapper** (`mvnw`). Builds are currently "whatever Maven you have".
- **Formatter** (Spotless + google-java-format). Code style is inconsistent: many files are
  one-line blobs (`GlobalExceptionHandler`, `TestResultService`, `JwtAuthenticationFilter`).
- **`.editorconfig`** and **`.gitattributes`** (LF, UTF-8, no BOM). These prevent the BOM mess
  from the old history.
- **Build guardrails** via `maven-enforcer-plugin` (Java 21, Maven ≥ 3.9) and compiler
  `-Xlint:all`.
- **Tests.** Only 1 unit test and 1 context-load IT exist. Nothing covers the auth flow,
  JWT/security rules, refresh rotation, owner scoping of test results, validation errors or
  migrations.
- **Typed config validation.** `@Value` strings mean a short JWT secret fails deep in `Keys`.
  Use `@ConfigurationProperties` records with `@Validated` (the validation starter is already a
  dependency).
- **Git hooks and commit convention**: pre-commit runs `spotless:check`, commit-msg checks
  Conventional Commits. Both are shell scripts in `.githooks/`.
- **CI hardening**: concurrency cancel, a Failsafe IT step, test-report upload, Dependabot and a
  container smoke test.
- **Docs**: CONTRIBUTING, a migrations guide, a CI guide, an env var reference and agent
  instructions.

### OUTDATED / to improve
- `README.md` mixes onboarding with a long curl tutorial. Move the API walkthrough to
  `docs/api.md`.
- The IT uses `@DynamicPropertySource` boilerplate. Replace it with Spring Boot's
  `@ServiceConnection` and a shared Testcontainers config.
- The `.dockerignore` rule `*.md` is fine. Add `.github`, `.githooks`, `docs` and `.claude`.
- `.gitignore` ignores `.vscode/` entirely. Keep it that way (see UNNECESSARY).

### UNNECESSARY (do not copy from the reference)
| Reference item | Why not here |
|---|---|
| pnpm, TypeScript, ESLint, Prettier, Jest, commitlint, Husky | Node ecosystem; replaced by Maven-native tools above |
| `lib/` | Maven manages dependencies |
| root `migrations/` | Flyway's convention is `src/main/resources/db/migration`; moving it adds config for no gain |
| `env/` | Spring profiles plus `.env.example` already cover it |
| `test-reports/`, `uploads/` | Generated output belongs in `target/`, and there is no upload feature |
| `.aws/` | No AWS deployment target is defined. Add it when there is one. |
| `GEMINI.md`, `.codex/`, `.agents/` | Only Claude Code is used, so these would duplicate `AGENTS.md` |
| `.vscode/` | `.editorconfig` + Spotless cover editor consistency for every IDE |
| Extra linters (Checkstyle, SpotBugs, Error Prone) | Deferred. Formatter + `-Xlint` + CodeQL default setup first. Reconsider after Phase 5. |
| Spring Boot 4 upgrade | This is a framework migration, a separate decision and PR |

---

## Delivery rules (all phases)
- Work on one branch per phase (`chore/p2-foundation`, …) with a PR onto `main` via the
  `neszaDev` account. CI must be green before merge.
- Keep commits focused and use Conventional Commits (`fix:`, `build:`, `test:`, `ci:`,
  `docs:`, `chore:`).
- **The formatter reformat is its own commit** containing nothing else, and its SHA goes in
  `.git-blame-ignore-revs`.
- Before every commit, run `./mvnw verify` and `git status` / `git diff --stat`. Confirm there
  are no logs, `target/`, reports, jars or zips in the diff.
- Never skip or disable a test to make CI green. Never weaken validation.
- Report at the end of each phase: what was inspected, what changed and why, files added and
  modified, validation performed, and remaining work.

---

## Phase 2 — Foundation (branch `chore/p2-foundation`)
0. `docs: add repository hardening plan`: copy this file to `docs/plans/0001-repository-hardening.md`.
1. `build: add Maven wrapper`: run `mvn wrapper:wrapper` (pin Maven 3.9.x). CI and docs use
   `./mvnw` from here on.
2. `fix: correct prod JSON log appender`: fixes bug #1 in `src/main/resources/logback-spring.xml`.
3. `build: run integration tests with Failsafe`: add `maven-failsafe-plugin` to `pom.xml` so
   `*IT` runs in `verify` (bug #2). Confirm that it now actually runs.
4. `fix: correct logger env var in compose override` (bug #3) and remove the duplicate `COPY`
   in the Dockerfile (bug #4).
5. `build: add enforcer and compiler lint`: `maven-enforcer-plugin` (Java 21, Maven 3.9+) and
   `-Xlint:all` in `pom.xml`, plus `-Werror` if the codebase is already clean.
6. `build: add Spotless with google-java-format` in `pom.xml`, bound to `check` in `verify`.
   It formats Java (google-java-format; the repo already uses 2-space indents), plus `pom.xml`
   (sortPom off, just trim/EOL) and Markdown/YAML whitespace trimming.
7. `style: apply formatter` is the mechanical commit only. Add its SHA to `.git-blame-ignore-revs`.
8. `chore: add editorconfig and gitattributes` (LF, UTF-8, `*.sh`/`mvnw` executable, `mvnw.cmd`
   CRLF) and extend `.gitignore` and `.dockerignore`.
9. `feat: validate app config at startup`: replace the `@Value` injection in
   `auth/JwtService.java` and `auth/RefreshTokenService.java`, and the CORS config in
   `security/CorsConfig.java`, with `@ConfigurationProperties` records (`app.security.*`,
   `app.cors.*`) annotated `@Validated`. The secret must be ≥ 32 bytes, TTLs must be positive,
   and origins must be non-empty. Behaviour is unchanged for valid configs, and startup fails
   clearly for invalid ones.
10. `chore: add git hooks`: `.githooks/pre-commit` (`./mvnw -q spotless:check`) and
    `.githooks/commit-msg` (Conventional Commits regex). Install them with
    `git config core.hooksPath .githooks`, which `make setup` documents and runs.
11. `chore: add Makefile command catalog`: thin aliases only (`setup`, `fmt`, `check`, `test`,
    `it`, `verify`, `up`, `watch`, `down`, `db-reset`). Each target delegates to `./mvnw` or
    `docker compose` and contains no logic of its own.

## Phase 3 — Project structure & tests (branch `chore/p3-tests`)
The package-by-feature layout already fits the domain, so **no source moves**. The work is in
tests:
- **Shared Testcontainers config**: `src/test/java/.../support/PostgresTestcontainersConfig.java`
  using `@ServiceConnection` (`postgres:17-alpine`, the same as compose), reused by all `*IT`
  classes. Refactor `BoilerplateApplicationIT` to use it.
- **Naming rule**: `*Test` means a unit test with no Spring context or Docker (Surefire).
  `*IT` means real Postgres (Failsafe).
- **New unit tests**: `JwtServiceTest` (issue/parse, expiry, bad signature),
  `RefreshTokenServiceTest` (rotation, expiry, revoke) and `AuthServiceTest` (login success and
  failure, email normalisation).
- **New ITs** (MockMvc + real DB):
  - `AuthFlowIT`: register → login → `/users/me` → refresh (old token rejected) → logout.
  - `SecurityIT`: public vs protected endpoints, 401 without or with an invalid token, actuator
    exposure.
  - `TestResultIT`: CRUD, pagination, summary, **owner isolation** (user B gets 404 on user A's
    row) and validation errors → `ApiError` shape.
  - `MigrationIT`: Flyway applies V1..Vn cleanly and `ddl-auto: validate` passes (this is
    already implied by the context test, but asserted explicitly).
- **JaCoCo** report on `verify` (HTML under `target/`, never committed). No coverage threshold
  yet; add a ratchet after the baseline is known.

## Phase 4 — AI/agent engineering (branch `docs/p4-agents`)
- **`AGENTS.md`** is the single source of truth, kept to about 150 lines. It covers:
  - the architecture summary, linking to `ARCHITECTURE.md` rather than repeating it
  - the command table (`make …` / `./mvnw …`)
  - conventions: package-by-feature, records for DTOs, services own transactions, no entities
    in responses, errors via `GlobalExceptionHandler`/`ApiError`
  - the definition of done: `./mvnw verify` green, with new behaviour covered by a Test or IT
  - **files requiring special care**:
    - existing Flyway migrations are immutable, so always add a new `V{n+1}__*.sql`
    - `security/*` and `auth/*` changes need an IT
    - `application-prod.yml` must have no defaults for secrets
    - Dockerfile and CI changes
  - **forbidden shortcuts**:
    - `ddl-auto` other than `validate`
    - `-DskipTests`, or disabling or `@Disabled` tests
    - committing `.env`, logs or `target/`
    - editing applied migrations
    - weakening security matchers
    - adding dependencies without a stated reason
    - force-pushing `main`
- **`CLAUDE.md`** contains `@AGENTS.md` plus only Claude-specific notes (a couple of lines).
- **`.claude/settings.json`** (committed, shared) holds a permission allowlist for `./mvnw *`,
  `make *`, `docker compose *` and read-only `git`/`gh`, and denies `Read(.env)`. Personal
  overrides stay in the git-ignored `.claude/settings.local.json`.
- Nothing else. There are no skills or hooks for now, because the commands above are simple
  enough.

## Phase 5 — Quality gates (branch `ci/p5-gates`)
The pipeline has the same commands locally and in CI:
```
format   ./mvnw spotless:check        (pre-commit hook + CI)
compile  ./mvnw compile               (-Xlint, enforcer)
unit     ./mvnw test                  (Surefire, *Test)
integ    ./mvnw verify                (Failsafe *IT + Testcontainers, JaCoCo)
image    docker build --target runtime
smoke    compose prod profile + postgres → wait for /actuator/health UP → down
```
- Rewrite `.github/workflows/ci.yml` as **one workflow** with these jobs:
  - `verify`: setup-java with Maven cache, `./mvnw -B verify`, upload Surefire/Failsafe/JaCoCo
    reports as artifacts (not committed).
  - `image-smoke`, which needs `verify`: build the runtime image, run it with the `prod` profile
    against a Postgres service container, and curl `/actuator/health` plus check that the log
    output is JSON. This catches bug-#1-class regressions.
  - Also: `concurrency` cancel-in-progress, `permissions: contents: read`, and pinned action
    major versions.
- **`.github/dependabot.yml`** covers maven, github-actions and docker, weekly and **grouped**
  (minor/patch together) to avoid the branch sprawl seen before. Majors stay as separate PRs.
- **`.github/pull_request_template.md`** is a short checklist: tests, migration immutability,
  docs updated.
- **Branch protection on `main`**: require the `verify` and `image-smoke` checks, and disallow
  force-push. This is a GitHub setting done by the repo owner and documented in `docs/ci.md`.
- CodeQL uses GitHub's **default setup** (a repo setting, so no workflow file is needed).

## Phase 6 — Documentation (branch `docs/p6-docs`)
| File | Content |
|---|---|
| `README.md` (slimmed) | What it is, prerequisites, 5-minute quick start (`make setup && make watch`), command table, and links to everything below |
| `docs/api.md` | The curl walkthrough moved from the README, and a Swagger UI link |
| `docs/configuration.md` | Every env var: name, default, required-in-prod, and which profile uses it (the source of truth for `.env.example`) |
| `docs/database.md` | Flyway rules, how to add a migration, naming, never edit applied ones, local reset (`make db-reset`) |
| `docs/testing.md` | Test vs IT, Testcontainers requirement (Docker running), reports location |
| `docs/ci.md` | Pipeline stages, how to reproduce each locally, branch protection, Dependabot policy |
| `CONTRIBUTING.md` | Branching, Conventional Commits, hooks setup, PR checklist, definition of done |
| `ARCHITECTURE.md` | Kept at the root, updated for config properties and the testing strategy, plus a request-flow diagram (Mermaid) |
| `CHANGELOG.md` | Only if releases start being tagged (skip for now) |

## Phase 7 — Validation (runs after every commit group, not only at the end)
1. Run `./mvnw -B verify` (needs Docker running). Confirm in the output that the ITs actually
   ran: the Failsafe summary shows > 0 tests.
2. Run `./mvnw spotless:check` and confirm it is clean.
3. Run `docker build --target runtime .` and the smoke test locally (`make smoke`, added in
   Phase 5).
4. Run `docker compose --profile local watch`, change a file and confirm the reload still works.
5. Run `git status` and `git diff --stat` and check that only intended files changed. Then run
   `git ls-files | grep -E '\.(log|zip|jar|patch)$|^target/'` and confirm it returns nothing.
6. Push the branch, confirm GitHub Actions is green, and open the PR.
7. Report at the end of each phase in the format above.

## Decisions made during implementation
- **`docker/` adopted** (user request, after Phase 5): the Dockerfile and compose files moved
  to `docker/` for a tidier root. Compose runs with `--project-directory .`, so `.env`, the
  build context and volume names stay rooted at the repo; `make` hides the flags.
- **403 for missing/invalid tokens kept** and pinned by `SecurityIT`. Switching to 401 is an
  API change for clients and needs a decision.

## Remaining / explicitly out of scope
- Spring Boot 4.x / jjwt 0.13 upgrades: separate PRs via Dependabot after gates exist.
- Deployment pipeline (registry push, environments). Needs a decision on the hosting target.
- Delete the stale remote branches from the old history (keep `backup/before-reset-20260930`)
  once the user confirms.
- Possible later additions once gates are stable: a coverage ratchet, Error Prone, and OpenAPI
  contract snapshot tests.
