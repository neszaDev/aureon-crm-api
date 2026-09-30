# Continuous integration

One workflow, `.github/workflows/ci.yml`, runs on every pull request and every push to `main`.
Each job runs the same command you can run locally.

```
developer change
  → pre-commit hook        ./mvnw spotless:check           (formatting)
  → commit-msg hook        Conventional Commits subject
  → CI: verify             ./mvnw verify                   make verify
        enforcer (Java 21+, Maven 3.9+) → compile -Xlint -Werror → unit tests
        → integration tests (Testcontainers) → JaCoCo report → Spotless check
  → CI: image-smoke        docker build + smoke-test.sh    make smoke
        runtime image, prod profile, real Postgres: health UP, JSON logs,
        no public /actuator/metrics
  → review + merge → deployment-ready image
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

## Dependabot

`.github/dependabot.yml` checks maven, GitHub Actions and the Docker base images weekly.
Minor and patch updates are grouped into one PR per ecosystem. **Major** updates (for
example Spring Boot 4) arrive as separate PRs. Treat those as migrations, not routine bumps.

## Repository settings (owner, one-time)

These can't be expressed as files:

1. **Branch protection on `main`**: require the `verify` and `image-smoke` checks and one
   review, and block force-pushes and deletion.
2. **Code scanning**: Settings → Code security → CodeQL → *Default setup*. This replaces a
   workflow file.
