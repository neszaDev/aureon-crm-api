# CI/CD

## Branches and environments

```
feature/* ──PR──▶ dev ──PR (release)──▶ main ──tag v1.2.3──▶ versioned image
                   │                     │
            CI/CD - dev            CI/CD - main
            publish :dev           approval, then publish :main :latest
```

| Workflow | Trigger | Does |
|---|---|---|
| `ci.yml` (**CI**) | every pull request | the jobs below, summed up by **CI Gate**, the one required check |
| `cd-dev.yml` (**CI/CD - dev**) | push to `dev` | CI, then publish `ghcr.io/<repo>:dev` |
| `cd-main.yml` (**CI/CD - main**) | push to `main`, tags `v*` | CI, **approval** on the `main` environment, then publish `:main` + `:latest`, or `:1.2.3` + `:1.2` for tags |
| `publish-image.yml` | called by the two above | the only place image build + push is defined; then rolls the image out to Azure Container Apps when the environment has `AZURE_*` variables ([deployment](deployment.md)) |
| `cleanup-pr-cache.yml` | PR closed | delete that PR's Actions caches |

Every published image also gets `:sha-<short>`, so a deployment can pin an exact commit.
Nothing is published without CI passing for the same commit: the CD workflows call `ci.yml`
and `publish` has `needs: ci`.

Images: `https://github.com/<owner>/<repo>/pkgs/container/<repo>`. Pull with
`docker pull ghcr.io/<owner>/<repo>:latest`.

## CI checks

Each job runs the same command you can run locally. They run in parallel after
**Install dependencies**, which fills the Maven cache once for all of them.

| Job | What it checks | Locally |
|---|---|---|
| Detect changes | Docs-only PRs (`*.md`, `docs/`) skip the build and test jobs; pushes run everything | |
| Install dependencies | `./mvnw dependency:go-offline`, saved to the cache keyed on `pom.xml` | |
| Security audit | Trivy on the resolved runtime jars: no fixable high/critical advisory (test-only jars: warning only). On PRs, `dependency-review-action` blocks new dependencies with high advisories | see below |
| Lint, format & types | Commit subjects (the `commit-msg` hook, on every PR commit), Spotless, main + test code compiled with `-Xlint:all -Werror` | `make lint` |
| Unit tests | `*Test` with Surefire, plus JaCoCo | `make test` |
| Integration tests (PostgreSQL) | `*IT` with Failsafe against `postgres:17-alpine` in Testcontainers, plus JaCoCo | `make verify` |
| Docker build test | Builds the runtime image (same target and layer cache as publishing), then Trivy: no fixable high/critical advisory in the image | `make smoke` builds it |
| App boot test | `scripts/smoke-test.sh` on that image: prod profile, real Postgres, health + readiness UP, JSON logs, Prometheus scrapable, `/actuator/metrics` not public, uploads writable | `make smoke` |
| **CI Gate** | Fails if any job above failed or was cancelled. The only check branch protection requires, so jobs can be added or split without touching settings | |

A new push to a PR cancels the previous run. The boot test runs even if only the image scan
failed, so you still learn whether the image starts.

Trivy runs from its image pinned by digest (`TRIVY` in `ci.yml`), not through `trivy-action`,
whose tags were hijacked in 2026. To audit locally:

```sh
./mvnw -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/audit/runtime
docker run --rm -v "$PWD/target/audit/runtime:/scan:ro" <TRIVY image from ci.yml> rootfs \
  --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed /scan
```

When an advisory is in a version Spring Boot manages and Boot hasn't released the fix yet,
override that version in `pom.xml` (`<tomcat.version>`, `<jackson-bom.version>`, ...) and drop
the override once the parent catches up.

| Job | Artifacts (kept 14 days) |
|---|---|
| Unit tests | `unit-test-reports`: Surefire + JaCoCo HTML |
| Integration tests | `integration-test-reports`: Failsafe + JaCoCo HTML |
| Docker build test | `runtime-image`: the built image for the boot test (kept 1 day) |
| App boot test | `smoke-app-log`: the container log, only on failure |

## When CI fails

1. Find the job and step in the PR's checks.
2. Reproduce locally with the matching `make` target. The commands are identical.
3. For test failures, download `unit-test-reports` or `integration-test-reports` and open the
   `*.txt` files in `surefire-reports/` or `failsafe-reports/`.
4. Fix the cause. Never skip tests or loosen checks to get green (see AGENTS.md).

## Releasing

1. Open a PR from `dev` to `main` and merge it once CI is green.
2. Approve the **main** deployment in the Actions run. `:main` and `:latest` are published.
3. For a versioned release, tag `main` (`git tag v1.2.0 && git push origin v1.2.0`), then
   approve again to publish `:1.2.0` and `:1.2`.

## Dependabot

Weekly maven, GitHub Actions and Docker base-image updates, opened against `dev`. Minor and
patch bumps are grouped per ecosystem. **Major** updates (for example Spring Boot 4) arrive as
separate PRs. Treat those as migrations, not routine bumps.

## Repository settings

These are GitHub settings, not files:

| Setting | Value |
|---|---|
| Branch protection: `main`, `dev` | require **CI Gate**; no force-push or deletion |
| Environment `dev` | deployable from `dev` only |
| Environment `main` | required reviewer; deployable from `main` and `v*` tags |
| Automatically delete head branches | on |
| Code scanning | CodeQL default setup |
| Dependabot alerts | on (dependency review needs the dependency graph) |
