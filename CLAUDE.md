@AGENTS.md

## Claude Code specifics

- `.claude/settings.json` pre-approves the build and test commands above. Personal overrides
  go in `.claude/settings.local.json` (git-ignored).
- For migrations, security rules or anything under "Files that need extra care", plan first
  and state the verification you will run before editing.
