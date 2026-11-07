# Battleship Rewrite — how to work here

## Where this stands

This repository is a greenfield Battleship rewrite. Build the new application from approved specifications, not from the
implementation that currently lives on `master`. Branch `rewrite_prod_ready` must be considered as main development
branch.

**Stack.** Modules (API, backend, frontend) and their technologies, boundaries and verification commands come from the
approved spec and plan of the active feature. Runtimes are pinned by major only (Java 25, Node 24, newest patch); npm
and Maven dependencies stay exact.

**State.** Workflow state lives in the active Spec Kit feature artifacts and the prerequisite checker; the approved
`003-frontend` feature supplies the modules, local commands and the gate, and `./scripts/local-build.sh` is the
authoritative product check.

**The spec is the authority.** `.specify/memory/constitution.md` is governance; implement from the active feature's
`spec.md`, `plan.md` and `tasks.md`. If the spec is silent on a behavior that matters, stop and ask with a recommended
default.

## The loop

Use this common feature path; skip only explicitly optional steps. No feature code is written before
`$speckit-plan` completes.

**Common feature loop:** `orient -> specify -> clarify -> checklist? -> plan (Phase 0/1) -> tasks -> analyze
-> handoff -> guard -> bridge/implement -> converge -> implement again if needed -> verify -> close`

1. **Orient.** For a registered feature, run `bash .specify/scripts/bash/check-prerequisites.sh --json
   --require-tasks --include-tasks` and identify the active feature.
2. **Governance and memory.** Run `$speckit-constitution` only when governance is missing or changing. Mandatory hooks
   run `speckit.memory-loader.load` before `specify`, `clarify`, `checklist`, `plan`, `tasks`, `analyze`, `implement`.
3. **Specify and clarify.** Run `$speckit-specify`, then `$speckit-clarify`, before planning.
4. **Requirements quality (optional).** `$speckit-checklist` evaluates requirements writing, not implementation.
5. **Plan and task.** Run `$speckit-plan` through Phase 0 research and Phase 1 design/contracts, then run
   `$speckit-tasks` to create the dependency-ordered implementation list.
6. **Analyze.** Run `$speckit-analyze` after tasks and before implementation. It is read-only; resolve material
   cross-artifact issues before building.
7. **Handoff and implement.** After tasks, the configured hook creates the
   `speckit.speckit-superpowers-bridge.handoff`; the guard (also hooked before clarify, plan, tasks, implement) and
   `$speckit-speckit-superpowers-bridge-execute` / `$speckit-superpowers-bridge` are aliases, not sequential steps. Use
   `$speckit-implement` directly only when the bridge is not used.
8. **Converge and repeat.** Run `$speckit-converge` only after an implementation pass; if it appends tasks, rerun the
   implementation path. It is not initial planning.
9. **Verify, close.** After implementation, run the verification commands named in the approved plan, update
   durable docs/status, and hand off with evidence and the next step.

**Optional extensions.** `$speckit-taskstoissues` turns tasks into GitHub issues. Mermaid diagrams in plans only when
they clarify behavior or boundaries.

Advance without asking when the next phase is clear from the approved artifacts. Stop only for an ambiguous or silent
spec, a repeated verification failure with the same cause, a costly architecture decision, or an irreversible action
outside normal repo edits.

## Scope control

Every step above is additive. `specify`, `clarify`, `checklist`, `plan`, `tasks`, `analyze`, `implement`, and
`converge` can each add a requirement; none can remove one, so the loop has no fixed point and ends only when the
user tires. These seven rules are the missing counterweight. They bind the agent, not the user.

1. **Mark provenance.** Tag every requirement in `spec.md` `[user]` (traceable to the user's words or the seed doc) or
   `[agent]`. Present the `[agent]` set as one block with its aggregate cost before it enters the spec; agent-added
   requirements default to deferred.
2. **Name the assurance level in `plan.md`.** `Lean` — canonical artifact, standard lint, one guide. `Standard` —
   targeted examples and tests for named risks. `Release-grade` — baselines, generated consumers, exhaustive
   fixtures, privacy matrices. Default to `Lean`. Never escalate silently; escalation is a user decision.
3. **Forecast before `tasks`.** State projected file and task counts in `plan.md`. More than 20 tasks stops the loop
   for a simplify / split / approve decision. The plan template's `Complexity Tracking` fires only on a constitution
   violation, so it will never catch a large but compliant design — this rule is what catches it.
4. **One task, one deliverable, one finite proof.** A task may not claim a requirement range (`FR-028-FR-065`) or use
   "all", "every", "complete", or "final" without an enumerated manifest of what it covers.
5. **Done means a named mutation fails the gate.** Record the command output showing the assertion failing when the
   thing it guards is removed. A green test that cannot fail is not evidence, and a checked box is not evidence.
6. **Bound convergence.** `$speckit-converge` is append-only and never repairs a wrongly checked task: when a finding
   maps to an existing task, uncheck it. After two rounds with material findings, fix the plan instead of appending.
7. **Present cost, not just benefit.** Anything offered for approval carries what it adds (files, dependencies,
   tasks), a cheaper alternative, and a recommendation of now / later / no. Offer decisions, not finished documents.

## Definition of Done

**Gate.** `003-frontend` owns the root integration layer and the single repository-wide product gate,
`./scripts/local-build.sh` (there is no `004-integration`; `--docker` runs the same gate inside Docker). Run it before
declaring the feature complete. It checks runtimes and exact installs, the contract, backend, frontend, and browser
journeys against the packaged backend; any failed or timed-out stage fails the run.

**Backend gate.** `cd backend && ./mvnw -q verify && ./mvnw spotless:check`. Contract gate: see below. For substantial
implementation, refactoring, or a requested quality audit, also run the local SonarQube scans in
`docs/local-operation.md`; review findings in context, report scan or server failures, and never suppress findings to
improve metrics. They do not replace the product gate.

**Local topology.** UI and API share one origin (no CORS): `vite preview` serves the built UI and proxies `/api`
(`./scripts/local-deploy.sh`, HTTPS on the LAN because the session cookie is `Secure`). Frontend journeys run against the
real packaged backend; no recorded game data and no fake engine.

**Scripts.** `scripts/` holds only product scripts: `local-build.sh` (gate), `local-deploy.sh` (run on the LAN),
and `build-docker-{frontend,backend,render}.sh` (images built in Linux stages from the root `Dockerfile`; no push).
Commands, ports, images and troubleshooting: `docs/local-operation.md`. The Render image (frontend + backend,
`linux/amd64`, `--smoke` checks) is an intentional, owner-approved artifact; deploying to Render stays manual
(`docs/render-deployment.md`). There is no hosting or deployment infrastructure in this repository.

**CI** (`.github/workflows/ci.yml`, `release.yml`; guide `docs/ci.md`) has three paths in two workflows: build-only on branch
pushes except `master`, the PR gate `./scripts/local-build.sh`, and a stable `vX.Y.Z` tag on `master` that pushes the
Render image to GHCR and creates a GitHub Release. No new script; SonarQube stays local; only `GITHUB_TOKEN`.

**Generation path.** `002-backend` generates the wire DTOs from `contracts/openapi.yaml` into
`backend/app/target/generated-sources/openapi` (package `ua.kostenko.battleship.app.web.dto`) during `generate-sources`:
Jackson 2 models plus only the whitelisted discriminator/date helpers the `Command` oneOf needs, no endpoint classes or
`ApiClient`; the app's HTTP converter must use the declared Jackson 2 mapper. Generated sources are build output: never
committed, hand-edited or Spotless-formatted; change the contract and regenerate (constitution Principle VI).

For a registered feature, first prove the artifacts exist:
`bash .specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks`

Work is done only when the approved tasks are complete, `./scripts/local-build.sh` has run cleanly, and the delivered
behavior still matches the governing spec. Do not borrow commands, tests, or success criteria from `master`.

Capture evidence, not just conclusions. Report skipped, timed-out, failed, and environment-blocked checks distinctly;
none count as a passing gate.

## Git protocol

`master` is historical context for the old application, not the base for new work. At the start of each task inspect
`git status --short --branch` and the local branches, confirm the intended feature branch, and reuse an existing task
branch when appropriate. For multi-commit tasks create `feature/<feature>--<task>` from the active feature integration
branch and record that parent. Keep unrelated staged or unstaged changes intact.

Commit each completed task with a concise imperative subject; at closeout verify it is committed and squash-merge a
task branch into its recorded parent. Keep `rewrite_prod_ready` for completed feature-level integration. Do not rewrite
history, force-push, or use destructive cleanup unless asked; merges to the default branch need user approval.

## Delegation

The main agent is the orchestrator: it keeps task framing, decisions, synthesis and the final judgment on whether the
work satisfies the approved spec. Substantive inspection, implementation, debugging, research, verification and review
go to focused sub-agents (brief them with objective, expected output, paths, and what not to touch). If delegation
tooling is unavailable, say so and continue locally.

## Non-negotiables

- Greenfield: never use the current `master` implementation as the foundation. The constitution plus the active
  feature's `spec.md`, `plan.md` and `tasks.md` are the authority; do not invent module boundaries, technologies,
  commands or standards they have not established.
- Update `AGENTS.md`, the constitution and saved agent memory together when a change establishes or invalidates durable
  repository-wide knowledge (boundaries, architecture, commands, verification, security, deployment, workflow); correct
  stale memory rather than leaving it inconsistent.

## Engineering discipline

Build code only when this change has a concrete caller or test for it; do not add functions, DTOs, models, or
interfaces "for later" (YAGNI). Reuse an existing function, component, or type with the needed or closely similar
behavior before writing a new one; never duplicate close-by functionality (DRY). Prefer the simplest working design
(KISS) and single-purpose, substitutable components (SOLID) over speculative structure. Introduce an interface or
abstract class only once a second concrete implementation is known, not in anticipation of one.

Tests should exercise real collaborators and observable behavior (black-box) rather than mocks, except where a real
collaborator is external, non-deterministic, or not yet built in this change. Delivered functionality MUST be
integrated into its consumer and covered by a test in the same change — code with no caller and no test is not done.

## Contract product boundary

`contracts/openapi.yaml` (feature `001-api-contract`; guide `contracts/README.md`) is the only wire authority: one
file with operations, schemas and embedded examples. The backend is authoritative: every game operation returns the
caller-relative `GameSnapshot` and the client enables exactly `allowedActions`; placement hints are advisory and the
backend validates commands. `contracts/` holds no application code, rules, persistence, generated client, CI or
deployment. The gate is `cd contracts && npm ci && npm run check` (Redocly lint validates every embedded example,
plus `openapi-typescript` generation into the ignored `.tmp/`); `npm run docs` builds an optional HTML reference. Use
the pinned local tools through these scripts, never a global CLI or `npx`.

Keep it lean: no oracle files, wording-asserting tests, symbolic fixtures, `x-` extensions that restate behaviour, or a
second copy of any rule. A contract change is an edit to `openapi.yaml` with a matching embedded example. The contract
stays v1 and is not frozen: a gap found while implementing MAY be fixed in `openapi.yaml` if every dependent is updated
in the same change. Build no migration, deprecation or versioning machinery.

## End every turn with Next step

Every work turn should end with a compact `Next step` block: the real verification status (checked boxes are not
proof; evidence is), the exact next command when one exists (or the decision, stated directly), and a self-contained
prompt for the next session. Short enough to scan, complete enough to survive a fresh session.

## What will bite you

- The old app on `master` is not the rewrite baseline; its commands, tests and architecture assumptions mislead even
  though they remain in git history. The active feature plan defines the modules and commands.
- The Spec Kit prerequisite checker is about feature artifacts, not implementation health. If it fails because tasks or
  a registered feature are missing, fix the workflow state instead of papering over the error.
- Skills live in two real trees rendered per agent by Spec Kit: `.agents/skills` (Codex) and `.claude/skills` (Claude
  Code), with the same skills and content apart from that rendering. `python3 .specify/scripts/sync-agent-files.py --check`
  must pass; `--apply` copies a skill that exists in only one tree. Never symlink or hand-merge the trees.
- Repository-wide standards are intentionally incomplete: when a rule is not approved, stay consistent with the current
  slice and record the durable decision once made.

## Where things live

- Product intent and visual mockup: `docs/intent.md`, `docs/design/`
- Project governance: `.specify/memory/constitution.md`
- Durable frontend and integration memory: `.specify/memory/frontend-absorbs-integration.md`
- Local commands, images and troubleshooting: `docs/local-operation.md`; Render: `docs/render-deployment.md`
- Spec Kit workflows, templates, and prerequisite scripts: `.specify/`
- Active feature authority: `specs/<feature-id>/spec.md`, `plan.md`, and `tasks.md`
- Agent skill sync check: `.specify/scripts/sync-agent-files.py`
