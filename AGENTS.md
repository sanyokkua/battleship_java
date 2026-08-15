# battleship_java — how to work here

## Where this stands

This is an educational two-player Battleship web service: a Spring Boot REST/MVC backend and a React/Vite frontend bundled into one runnable JAR.

**Stack.** Java 25, Spring Boot 4.1.0, Maven, React 19, TypeScript, Vite, Vitest, Playwright.

**State.** The application is running and feature work is active. Use the current branch, source, tests, and generated artifacts as the live state; do not infer completion from older documentation.

**Authority.** Runtime behavior is defined by source, configuration, and tests. Human-facing architecture and API context are in `README.md`, `docs/index.md`, and `docs/architecture.md`. New behavior needs a written spec in `specs/<###-feature>/spec.md`.

## The loop

Six phases. No feature code before PLAN is complete.

| Phase | Exit condition | Command or artifact |
|---|---|---|
| ORIENT | Current state and unit of work are named | Read this file, `README.md`, relevant docs/source, and `git status --short --branch` |
| SPEC | Acceptance criteria and edge cases are written | `specs/<###-feature>/spec.md` |
| PLAN | Ordered tasks exist; one task = one branch = one commit | `specs/<###-feature>/plan.md` and `tasks.md` |
| BUILD | Each task is implemented and individually checked | Work on the task branch |
| VERIFY | The gate is green and behavior matches the spec | `scripts/verify.sh` |
| CLOSE | Evidence is recorded, docs are current, and the next unit is named | Update the feature artifacts and relevant docs; leave final merge to the user |

Advance through phases without asking. Stop only for an ambiguous spec, the same gate failure twice, an irreversible/out-of-repo action, a costly architectural decision, or information contradicting the approved spec.

## Definition of Done

**Gate:** `scripts/verify.sh`. It runs Maven verification, the pinned frontend runtime, frontend tests/lint, mock-browser E2E, and live packaged-JAR E2E. Its normal duration is about 70 seconds here; a hang is a failure.

A unit is done only when the gate is green and the implementation matches the spec.

- Capture the baseline before work; distinguish existing findings from new ones.
- `mvn verify` may regenerate tracked `docs/openapi.json`; inspect and record that diff when the API changes.
- Report evidence: acceptance criterion → proving test, gate result, generated-artifact changes, and remaining limitations.

## Git protocol

```
master                         protected; do not develop or commit directly
  └── feature/<slug>           one parent branch per unit of work
        └── feature/<slug>--<task>  one task branch per plan task
```

Use an existing suitable feature branch; do not stack another parent branch. Commit only on task branches, keep one task per commit, and verify before committing. Never merge the parent into `master`; final integration belongs to the user. Never use `--no-verify` or force-push; fix the check instead.

## Delegation

The main Codex session owns the plan, decisions, conversation, and judgment of completion. Delegate read-only mapping, broad searches, implementation task groups, test writing, or independent conformance review when useful.

Brief each delegated task with its objective, output format, files to inspect, and boundaries. Ask for a condensed summary, not a transcript. Never delegate the decision about the next step or whether the spec is satisfied.

## Non-negotiables

| Rule | Enforced by |
|---|---|
| Gate green before claiming done | `scripts/verify.sh`, pre-push hook |
| Agent entry points and skill mirrors stay synchronized | `scripts/sync-agent-files.py --check`, pre-push |
| `.agents/skills` is the only real project-skill tree | synchronizer check |
| API changes regenerate and review `docs/openapi.json` | Maven verify plus closing evidence |
| No feature code before SPEC and PLAN | — (advisory) |
| Preserve unrelated worktree changes | — (advisory) |
| Do not bypass hooks or force-push | — (advisory) |

## End every turn with Next step

Every turn that advances work ends with this block:

```markdown
## Next step

**State:** <where the work actually is, citing evidence>
**Command:** <exact command> — or "none — decision needed"
**Prompt:**
> <complete, copy-pasteable prompt naming the unit, artifacts to read, and the constraint most likely to be violated>
```

Be honest: written is not verified, and a red or unrun gate is unverified. Give one next step, not a plan.

## What will bite you

- **Wrong Node runtime.** The shell may expose Node 26, while Maven installs Node 24.18.0 in `frontend/node`. Running Vitest with the wrong runtime can make `localStorage` undefined and produce a cascade of unrelated-looking failures. The verification gate must prepend `frontend/node` to `PATH`.
- **Restricted localhost binding.** Playwright may fail before any test with `listen EPERM ... ::1:5173`. Treat that as an environment capability failure and retry with scoped host access; do not call it a product regression.
- **Generated OpenAPI drift.** Maven's integration-test phase starts the application and regenerates `docs/openapi.json`. A successful build can still leave a meaningful tracked diff.
- **Live E2E needs the packaged JAR.** The live suite starts `target/battleship-0.0.1-SNAPSHOT.jar` on port 8080 and uses in-memory state; build it before diagnosing browser failures.
- **Frontend commands are scoped.** Direct npm commands run from `frontend/`; the repository gate uses `npm --prefix frontend ...` after Maven installs dependencies.

## Where things live

- Behavior and API context: `README.md`, `docs/index.md`, `docs/architecture.md`, `docs/openapi.json`
- Spec Kit feature artifacts: `specs/<###-feature>/` (`spec.md`, `plan.md`, `tasks.md`, and optional research/design artifacts)
- Canonical agent skills: `.agents/skills/`
- Claude bridge/runtime: `CLAUDE.md`, `.claude/`; generated Codex mirror: `.codex/`
- Synchronizer and verification gate: `scripts/sync-agent-files.py`, `scripts/verify.sh`
