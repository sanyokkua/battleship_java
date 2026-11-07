## Context

Original context, evidence and mutation records are in `openspec/changes/archive/*-frontend/task-evidence.md` (entries for the coverage task and the GitHub trigger task). The Vitest LCOV omits gameplay screens; the instrumented Playwright suite already passed 106 journeys and merged 101 scripts, but a named-line assertion showed the live per-journey coverage mapping is not yet reliable.

## Decisions

- Reuse the existing Playwright journeys and Vitest coverage configuration; add exact-version dependencies only if mapping needs them.
- GitHub trigger verification is manual (owner); it is never checked on local evidence.
