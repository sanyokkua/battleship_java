# Battleship Rewrite — how to work here

## Where this stands

This repository is a greenfield Battleship rewrite. Build the new application from approved specifications, not from the
implementation that currently lives on `master`. Branch `rewrite_prod_ready` must be considered as main development
branch.

**Stack.** Target modules are API, backend, and frontend, but their concrete technologies, boundaries, and verification
commands are defined by the approved spec and technical plan for the active feature.

**State.** Current workflow state lives in the active Spec Kit feature artifacts and the prerequisite checker. Do not
assume an application module, build, test, lint, or local-run command exists until the approved plan introduces it.

**The spec is the authority.** `.specify/memory/constitution.md` is project governance. For implementation, use the
active feature's `spec.md`, `plan.md`, and `tasks.md`; if the spec is silent on a behavior that matters, stop and ask
with a recommended default.

## The loop

Use this common feature path; skip only explicitly optional steps. No feature code is written before
`$speckit-plan` completes.

**Common feature loop:** `orient -> specify -> clarify -> checklist? -> plan (Phase 0/1) -> tasks -> analyze
-> handoff -> guard -> bridge/implement -> converge -> implement again if needed -> verify -> close`

1. **Orient.** For a registered feature, run `bash .specify/scripts/bash/check-prerequisites.sh --json
   --require-tasks --include-tasks` and identify the active feature.
2. **Governance and memory.** Run `$speckit-constitution` only when governance is missing or changing. The
   standalone `$speckit-memory-loader-load` is optional; configured mandatory hooks run
   `speckit.memory-loader.load` before `specify`, `clarify`, `checklist`, `plan`, `tasks`, `analyze`, and
   `implement`.
3. **Specify and clarify.** Run `$speckit-specify`, then `$speckit-clarify`; clarification is completed before
   planning.
4. **Requirements quality (optional).** Run `$speckit-checklist` after specification/clarification and before
   planning or task generation as appropriate. It evaluates requirements writing, not implementation behavior.
5. **Plan and task.** Run `$speckit-plan` through Phase 0 research and Phase 1 design/contracts, then run
   `$speckit-tasks` to create the dependency-ordered implementation list.
6. **Analyze.** Run `$speckit-analyze` after tasks and before implementation. It is read-only; resolve material
   cross-artifact issues before building.
7. **Handoff and implement.** After tasks, the configured hook mandatorily creates the
   `speckit.speckit-superpowers-bridge.handoff`. Explicit bridge skills are
   `$speckit-speckit-superpowers-bridge-handoff` and
   `$speckit-speckit-superpowers-bridge-guard`; with bridge ownership, run the guard and one of
   `$speckit-speckit-superpowers-bridge-execute` or `$speckit-superpowers-bridge`; these are aliases, not
   sequential steps. Use `$speckit-implement` directly only when the bridge is not used. The configured
   `speckit.speckit-superpowers-bridge.guard` also runs before clarification, planning, tasks, and implementation.
8. **Converge and repeat.** Run `$speckit-converge` only after an implementation pass. If it appends remaining
   tasks, rerun the selected bridge or direct implementation path; do not use converge as initial planning.
9. **Verify, close.** After implementation, run the verification commands named in the approved plan, update
   durable docs/status, and hand off with evidence and the next step.

**Optional extensions.** Use `$speckit-taskstoissues` when tasks must become GitHub issues.

Advance without asking when the next phase is clear from the approved artifacts. Stop only to resolve an ambiguous or
silent spec, report a repeated verification failure with the same cause, raise a costly architecture decision, or
request approval for an irreversible action outside normal repo edits.

Spec Kit plans may include Mermaid diagrams when they clarify behavior, interactions, or boundaries. Diagrams are
optional and should never be decorative.

## Scope control

Every step above is additive. `specify`, `clarify`, `checklist`, `plan`, `tasks`, `analyze`, `implement`, and
`converge` can each add a requirement; none can remove one, so the loop has no fixed point and ends only when the
user tires. These seven rules are the missing counterweight. They bind the agent, not the user.

1. **Mark provenance.** Tag every requirement in `spec.md` `[user]` (traceable to the user's own words or the seed
   doc) or `[agent]`. Present the `[agent]` set as one block with its aggregate cost before it enters the spec;
   agent-added requirements default to deferred. `speckit-specify` is told to make informed guesses and to escalate
   only when scope is "significantly impacted", so unmarked agent expansion is the default failure.
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
6. **Bound convergence.** `$speckit-converge` is append-only by contract and never repairs the task that was wrongly
   checked. When a finding maps to an existing task, uncheck that task rather than letting the appended one stand
   alone. After two rounds with material findings, stop and fix the plan instead of appending a third phase.
7. **Present cost, not just benefit.** Anything offered for approval carries what it adds (files, dependencies,
   tasks), a cheaper alternative, and a recommendation of now / later / no. Offer decisions, not finished documents —
   a coherent document written faster than the user can read it cannot be rejected, only accepted.

## Definition of Done

**Gate.** There is no single repository-wide product gate at this `HEAD` yet. Before BUILD starts, the approved
technical plan must name the build, test, lint, and local-run commands for the affected module or feature.

**Generation path.** `002-backend` generates the wire DTOs from `contracts/openapi.yaml` into
`backend/app/target/generated-sources/openapi` (package `ua.kostenko.battleship.app.web.dto`) during
`generate-sources`; those sources are build output — never committed, never hand-edited, not formatted by
Spotless, and changed only by amending the contract and regenerating (constitution § *Scope and Technical
Baseline*, Principle VI).

For a registered feature, start by proving the feature artifacts exist:

`bash .specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks`

Work is done only when the approved tasks are complete, the required verification from the plan has run cleanly, and the
delivered behavior still matches the governing spec. Do not borrow commands, tests, or success criteria from `master`.

Capture evidence, not just conclusions. If no product verification command exists yet, say that plainly and leave the
work in a not-fully-verified state.

## Git protocol

`master` is historical context for the old application, not the base for new implementation work.

Work from a feature branch with `feature/` names unless the user asks for a different branch strategy. Keep unrelated
staged or unstaged changes intact.

Make small, focused commits with concise imperative subjects. The exact naming and formatting standard can be formalized
later; until then, stay internally consistent within the active task.

Do not rewrite history, force-push, or use destructive git cleanup unless the user explicitly asks. Final merges to the
default branch remain a user-approved step.

## Delegation

The main agent is the orchestrator. Keep the main context for task framing, decisions, coordination, synthesis, and
integration.

Substantive repository work such as inspection, implementation, debugging, research, verification, and review must go to
focused sub-agents when that tooling is available. Brief them with the objective, expected output, files or paths to
inspect, and what must not be touched.

If delegation tooling is unavailable, say so and continue locally instead of pretending the repository rule does not
exist.

Never delegate the final judgment about whether the implementation satisfies the approved spec or what the next step
should be.

## Non-negotiables

- Treat the new Battleship application as greenfield work; never use the current `master` implementation as the
  foundation. Enforced by `AGENTS.md` and approved specs — (advisory)
- `.specify/memory/constitution.md` plus the active feature's `spec.md`, `plan.md`, and `tasks.md` are the
  implementation authority. Enforced by review discipline — (advisory)
- Do not invent module boundaries, technologies, commands, or standards that the approved artifacts have not established
  yet. Enforced by plan-first workflow — (advisory)
- Preserve unrelated staged and unstaged changes. Enforced by task scope and git discipline — (advisory)
- Use orchestrator-plus-focused-sub-agent execution for substantive repo work when the tooling exists; if it does not,
  state that limitation. Enforced by `AGENTS.md` — (advisory)
- Add Mermaid diagrams to Spec Kit technical plans only when they materially clarify the design. Enforced by plan
  review — (advisory)
- Update `AGENTS.md`, the constitution, and this project's saved agent memory together when a change establishes or
  invalidates durable repository-wide knowledge such as module boundaries, architecture, commands, verification,
  security, deployment, or workflow decisions; remove or correct memory that has gone stale rather than leaving it
  inconsistent. Enforced by repository maintenance discipline — (advisory)
- Never exceed a feature's recorded assurance level or task forecast without a new explicit approval, and never let a
  requirement the user did not ask for enter a spec unmarked. Enforced by `AGENTS.md` Scope control — (advisory)

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

Feature `specs/001-api-contract` publishes the API contract under `contracts/`. The whole contract is the single
file `contracts/openapi.yaml` (operations, schemas, embedded examples); `contracts/README.md` is its guide. The
backend is the model and a client is only a view: every game operation returns the caller-relative `GameSnapshot`
and the client enables exactly `allowedActions`. `contracts/` contains no application code, game rules,
persistence, generated client package, CI or deployment.

The gate is `cd contracts && npm ci && npm run check` (Redocly lint, which validates every embedded example against
its schema, plus `openapi-typescript` generation into the ignored `.tmp/`). `npm run docs` builds an optional HTML
reference into the ignored `dist/`. Use the pinned local tools through these scripts; no global CLI or `npx`.

Keep it lean. Do not add oracle files, tests that assert wording, symbolic fixtures, vendor `x-` extensions that
restate behaviour, or a second copy of any rule. A contract change is an edit to `openapi.yaml` with a matching
embedded example. Where `docs/rewrite_context_doc/Planning/` disagrees with `openapi.yaml`, the OpenAPI file wins
(see `contracts/README.md` § Supersedes planning inputs).

The contract stays v1 for this rewrite; it is not frozen. A gap or design flaw found while implementing the backend
or frontend MAY be fixed by editing `openapi.yaml` directly, provided every current dependent is updated in the same
change. Do not build migration, deprecation, or versioning machinery for this pre-release contract.

## End every turn with Next step

Every work turn should end with a compact `Next step` handoff block that states the real state, the exact next command
when one exists, and a self-contained prompt for the next session.

Be precise about verification status. Checked boxes are not proof; evidence is. If the next step is a decision, say that
directly instead of inventing a command.

The block should be short enough to scan quickly and complete enough to survive a fresh session.

## What will bite you

- The old Battleship app on `master` is not the rewrite baseline. Reusing its commands, tests, or architecture
  assumptions will send work down the wrong path even when those artifacts still exist in git history.
- This `HEAD` may have no product modules or gate yet. Treat that as real project state, not as permission to import
  conventions from `master` or invent missing commands.
- The Spec Kit prerequisite checker is about feature artifacts, not implementation health. If it fails because tasks or
  a registered feature are missing, fix the workflow state instead of papering over the error.
- Skills live in two real trees rendered per agent by Spec Kit: `.agents/skills` (Codex) and `.claude/skills` (Claude
  Code), with the same skills and content apart from that rendering. `python3 scripts/sync-agent-files.py --check`
  must pass; `--apply` copies a skill that exists in only one tree. Never symlink or hand-merge the trees.
- Repository-wide standards are intentionally incomplete right now. When a naming rule, test framework, or architecture
  boundary has not been approved, keep the work consistent with the current slice and record the durable decision once
  it is made.

## Where things live

- Rewrite context and historical planning inputs: `docs/rewrite_context_doc/`
- Project governance: `.specify/memory/constitution.md`
- Spec Kit workflows, templates, and prerequisite scripts: `.specify/`
- Active feature authority: `specs/<feature-id>/spec.md`, `plan.md`, and `tasks.md`
- Agent skill sync check: `scripts/sync-agent-files.py`
