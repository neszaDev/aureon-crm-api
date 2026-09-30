## What and why

<!-- One or two sentences. Link the issue/ticket if there is one. -->

## Checklist

- [ ] `make verify` passes locally (and `make smoke` if Docker/config/logging changed)
- [ ] New behaviour is covered by a `*Test` or `*IT`
- [ ] No existing Flyway migration was edited; schema changes are a new `V{n}__*.sql`
- [ ] Docs updated if commands, config or env vars changed (`README.md`, `docs/`, `env/.env.example`)
- [ ] No secrets, `env/.env`, logs or build output committed
