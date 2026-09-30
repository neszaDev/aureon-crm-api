# Contributing

## Setup

```sh
make setup   # git hooks + env/.env
make verify  # should pass before you change anything (Docker must be running)
```

## Workflow

1. Branch from `dev`: `feat/…`, `fix/…`, `chore/…`, `docs/…`, `ci/…`, and open the PR against
   `dev`. Never push directly to `dev` or `main`; releases are a `dev` → `main` PR
   ([CI/CD](../docs/ci.md#releasing)).
2. Make small, focused commits. Keep mechanical changes (reformatting, renames) in their own
   commits.
3. Before pushing, run `make verify`, and also `make smoke` if you touched Docker, config or
   logging.
4. Open a PR and fill in the template checklist. CI (`verify` + `image-smoke`) must pass.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/), checked by the `commit-msg`
hook:

```
<type>(<optional scope>): <summary>

<optional body: what and why>
```

Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`,
`revert`. Add `!` for breaking changes (`feat(api)!: …`).

## Definition of done

- `make verify` passes.
- New behaviour has tests: `*Test` for logic, `*IT` for HTTP, security or persistence
  ([Testing](../docs/testing.md)).
- Schema changes are a new Flyway migration; existing ones are never edited
  ([Database](../docs/database.md)).
- New config is documented in [Configuration](../docs/configuration.md) and `env/.env.example`.
- No secrets, `env/.env`, logs or build output in the diff.

The full set of conventions and the "don't do this" list are in [AGENTS.md](../AGENTS.md). It
applies to humans too.

## Reviewing

- `git config blame.ignoreRevsFile .git-blame-ignore-revs` hides the bulk-reformat commit
  from `git blame`.
- Stacked PRs are fine. Merge the base first; GitHub retargets the next one.
