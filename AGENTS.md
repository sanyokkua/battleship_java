# Battleship Rewrite — how to work here

## Where this stands

This repository is a greenfield Battleship rewrite. Build from the OpenSpec specifications, not from the
implementation that currently lives on `master`. Branch `rewrite_prod_ready` is the main development branch.

**Stack.** Modules (contracts, backend, frontend), technologies and boundaries are in `openspec/config.yaml` and
`openspec/specs/`. Runtimes are pinned by major only (Java 25, Node 24, newest patch); npm and Maven dependencies stay exact.

**The spec is the authority.** `openspec/specs/` describes current behavior; `openspec/config.yaml` holds project
context and rules; `openspec/changes/` holds work in progress and `openspec/changes/archive/` the history (including the
three original features: API contract, backend, frontend). If the spec is silent on a behavior that matters, stop and
ask with a recommended default.

## Workflow (OpenSpec)

New work starts as an OpenSpec change. Use the generated commands (Claude Code `/opsx:*`, Codex `$openspec-*`):

`explore -> propose -> (update) -> apply -> verify -> sync/archive`

- `/opsx:explore` think and investigate; `/opsx:propose` create proposal, specs, design and tasks in one step.
- `/opsx:update` revise a change's artifacts and keep them coherent; never edits code.
- `/opsx:apply` implement tasks; `/opsx:sync` merge delta specs into `openspec/specs/`; `/opsx:archive` finish.
- `openspec validate --all --strict` must pass before a change is applied or archived.
- Generated skills and commands (`.claude/commands/opsx`, `.claude/skills/openspec-*`, `.agents/skills/openspec-*`) are
  tool output: refresh with `OPENSPEC_TELEMETRY=0 openspec update`, never hand-edit.
- Artifact rules (provenance tags, assurance level, task forecast and the 20-task limit, one task / one deliverable /
  one finite proof, mutation-fails-the-gate evidence) live in `openspec/config.yaml` and apply automatically.

Advance without asking when the next step is clear from the approved artifacts. Stop only for an ambiguous or silent
spec, a repeated verification failure with the same cause, a costly architecture decision, or an irreversible action
outside normal repo edits. Scope only grows by the owner's decision: agent-proposed requirements are listed with their
cost and default to deferred.

## Definition of Done

**Gate.** `./scripts/local-build.sh` is the single repository-wide product gate (`--docker` runs it inside Docker). Run
it before declaring a change complete. It checks runtimes and exact installs, the contract, backend, frontend, and
browser journeys against the packaged backend; any failed or timed-out stage fails the run.

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

**Generation path.** The backend generates the wire DTOs from `contracts/openapi.yaml` into
`backend/app/target/generated-sources/openapi` (package `ua.kostenko.battleship.app.web.dto`) during `generate-sources`:
Jackson 2 models plus only the whitelisted discriminator/date helpers the `Command` oneOf needs, no endpoint classes or
`ApiClient`; the app's HTTP converter must use the declared Jackson 2 mapper. Generated sources are build output: never
committed, hand-edited or Spotless-formatted; change the contract and regenerate.

Work is done only when the change's tasks are complete, `./scripts/local-build.sh` has run cleanly, and the delivered
behavior still matches the governing specs. Do not borrow commands, tests, or success criteria from `master`.

Capture evidence, not just conclusions. Report skipped, timed-out, failed, and environment-blocked checks distinctly;
none count as a passing gate.

## Git protocol

`master` is historical context for the old application, not the base for new work. At the start of each task inspect
`git status --short --branch` and the local branches, confirm the intended branch, and reuse an existing task
branch when appropriate. For multi-commit tasks create `feature/<change>--<task>` from the active integration
branch and record that parent. Keep unrelated staged or unstaged changes intact.

Commit each completed task with a concise imperative subject; at closeout verify it is committed and squash-merge a
task branch into its recorded parent. Keep `rewrite_prod_ready` for completed change-level integration. Do not rewrite
history, force-push, or use destructive cleanup unless asked; merges to the default branch need user approval.

## Delegation

The main agent is the orchestrator: it keeps task framing, decisions, synthesis and the final judgment on whether the
work satisfies the approved specs. Substantive inspection, implementation, debugging, research, verification and review
go to focused sub-agents (brief them with objective, expected output, paths, and what not to touch). If delegation
tooling is unavailable, say so and continue locally.

## Non-negotiables

- Greenfield: never use the current `master` implementation as the foundation. `openspec/specs/`, `openspec/config.yaml`
  and the active change are the authority; do not invent module boundaries, technologies, commands or standards they
  have not established.
- Update `AGENTS.md`, the OpenSpec specs/config and saved agent memory together when a change establishes or invalidates
  durable repository-wide knowledge (boundaries, architecture, commands, verification, security, deployment, workflow);
  correct stale memory rather than leaving it inconsistent.

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

`contracts/openapi.yaml` is the only wire authority: one file with operations, schemas and embedded examples (guide
`contracts/README.md`). The backend is authoritative: every game operation returns the caller-relative `GameSnapshot`
and the client enables exactly `allowedActions`; placement hints are advisory and the backend validates commands.
`contracts/` holds no application code, rules, persistence, generated client, CI or deployment. The gate is
`cd contracts && npm ci && npm run check` (Redocly lint validates every embedded example, plus `openapi-typescript`
generation into the ignored `.tmp/`); `npm run docs` builds an optional HTML reference. Use the pinned local tools
through these scripts, never a global CLI or `npx`.

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
  though they remain in git history.
- `openspec validate` checks artifact structure, not implementation health; a green validate is not a passing gate.
- Repository-wide standards are intentionally incomplete: when a rule is not approved, stay consistent with the current
  slice and record the durable decision once made.

## Where things live

- Product intent and visual mockup: `docs/intent.md`, `docs/design/`
- Specs, project context and rules: `openspec/specs/`, `openspec/config.yaml`; active work `openspec/changes/`; history `openspec/changes/archive/`
- Local commands, images and troubleshooting: `docs/local-operation.md`; Render: `docs/render-deployment.md`; CI: `docs/ci.md`
- Architecture and development guides: `docs/architecture.md`, `docs/development.md`
