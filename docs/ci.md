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
| `ci.yml` (**CI**) | every pull request | `verify` + `image-smoke`; required to merge |
| `cd-dev.yml` (**CI/CD - dev**) | push to `dev` | CI, then publish `ghcr.io/<repo>:dev` |
| `cd-main.yml` (**CI/CD - main**) | push to `main`, tags `v*` | CI, **approval** on the `main` environment, then publish `:main` + `:latest`, or `:1.2.3` + `:1.2` for tags |
| `publish-image.yml` | called by the two above | the only place image build + push is defined |
| `cleanup-pr-cache.yml` | PR closed | delete that PR's Actions caches |

Every published image also gets `:sha-<short>`, so a deployment can pin an exact commit.
Nothing is published without CI passing for the same commit: the CD workflows call `ci.yml`
and `publish` has `needs: ci`.

Images: `https://github.com/<owner>/<repo>/pkgs/container/<repo>`. Pull with
`docker pull ghcr.io/<owner>/<repo>:latest`.

## CI checks

Each job runs the same command you can run locally.

```
pre-commit hook   ./mvnw spotless:check           (formatting)
commit-msg hook   Conventional Commits subject
verify            ./mvnw verify                   make verify
                    enforcer → compile -Xlint -Werror → unit tests → integration tests
                    (Testcontainers) → JaCoCo → Spotless
image-smoke       docker build + smoke-test.sh    make smoke
                    runtime image, prod profile, real Postgres: health + readiness UP,
                    JSON logs, Prometheus scrapable, /actuator/metrics not public
```

`verify` and `image-smoke` run in parallel. A new push to a PR cancels the previous run.

| Job | Artifacts |
|---|---|
| `verify` | `test-reports`: Surefire, Failsafe, JaCoCo HTML (kept 14 days) |
| `image-smoke` | `smoke-app-log`: the container log, uploaded only on failure |

## When CI fails

1. Find the job and step in the PR's checks.
2. Reproduce locally with the matching `make` target. The commands are identical.
3. For test failures, download `test-reports` and open `failsafe-reports/*.txt`.
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
| Branch protection: `main`, `dev` | require `verify` + `image-smoke`; no force-push or deletion |
| Environment `dev` | deployable from `dev` only |
| Environment `main` | required reviewer; deployable from `main` and `v*` tags |
| Automatically delete head branches | on |
| Code scanning | CodeQL default setup |
