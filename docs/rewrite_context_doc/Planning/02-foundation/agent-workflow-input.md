# Agent workflow input for the rewrite

## Purpose and consuming work

Use this input during the Foundation phase, after the core `specify init`
command and before the first feature specification. It directs the creation of
the rewrite's agent workflow files. The expected outputs are a concise
canonical `AGENTS.md`, a minimal real `CLAUDE.md` import, one real
`.agents/skills/` tree, supported generated or symlinked Codex mirrors, and a
deterministic sync/drift checker wired into local pre-push checks.

This is Foundation configuration work, not an invitation to retain or copy any
old application-specific agent rules. It must fit the generated SpecKit
workflow and use the current installed tool capabilities rather than assuming a
legacy mirror layout.

Before creating any target, inventory the worktree and run these exact
collision checks from the repository root:

```sh
git status --short --branch
git ls-files --others
test ! -e AGENTS.md && test ! -L AGENTS.md
test ! -e CLAUDE.md && test ! -L CLAUDE.md
test ! -e .claude && test ! -L .claude
test ! -e .codex && test ! -L .codex
test ! -e scripts && test ! -L scripts
test ! -e scripts/sync-agent-files.py && test ! -L scripts/sync-agent-files.py
```

Abort if any target exists in tracked, untracked, ignored, symlinked, or
broken-symlink form. Do not treat cleanup or SpecKit `--force` as authority over
someone else's work; its owner must resolve a collision separately. The
generated `.agents/` tree is expected input from SpecKit and may be extended
deliberately only after its contents are reviewed.

## Canonical file layout

- `AGENTS.md` is the only canonical repository-wide instruction file. Keep it
  at or below 200 lines and use the nine-section structure from the Agent
  Memory Standard: current state, workflow loop, definition of done, Git
  protocol, delegation, non-negotiables, next step, known traps, and pointers.
- `CLAUDE.md` is a regular three-line file whose first nonblank line is
  `@AGENTS.md`. It adds only Claude-specific mechanics and does not duplicate
  repository policy.
- `.agents/skills/` is the only real, tool-neutral skills tree. Put reusable
  multi-step procedures there rather than expanding `AGENTS.md`.
- `.claude/` holds only Claude-specific settings, hooks, path-scoped rules,
  agents, and symlinks that the installed tools support. Do not mirror its
  settings or hooks into Codex.
- `.codex/` is generated output or a supported symlinked view, never a
  hand-maintained second source of rules. Generate it from canonical inputs and
  make the checker detect drift.
- `scripts/sync-agent-files.py` is a deterministic, standard-library-friendly
  synchronizer and check command. It must validate the real Claude import,
  canonical skill links, and generated mirror content without changing files in
  check mode.

The model for this layout is the
[Agent Memory Standard](https://agents.md/). Follow its portability principle
without copying its examples or the deleted application's particulars.

## Workflow to encode in AGENTS.md

Use the six phases: ORIENT, SPEC, PLAN, BUILD, VERIFY, and CLOSE. Bind them to
the actual generated SpecKit commands and repository artifacts after
initialization. A feature must have `specs/<feature>/spec.md`,
`specs/<feature>/plan.md`, and `specs/<feature>/tasks.md` before its feature
code. The API contract feature comes first, followed by backend, frontend, and
integration.

The definition of done reserves `scripts/verify.sh` as the single final root
verification command. Feature 004 creates that command after all three products
exist. Before then, each feature uses the focused validation command in its
accepted plan. The workflow must require a baseline before new work, current
docs and generated contract artifacts when applicable, and evidence tied to
acceptance behavior. Do not claim completion from task checkboxes alone or
claim the root gate exists before feature 004.

Use `rewrite_prod_ready` as the protected base. Never commit directly to it.
Each planned task uses a branch named `feature/<feature>-<task>` based on its
feature parent and a focused commit after its local checks. The user retains
final integration authority.

The short non-negotiables list has at most ten true rules. Each rule must name
its actual enforcer. Where an enforcement mechanism does not yet exist, say it
is advisory instead of pretending that markdown blocks the action. Foundation
may wire `scripts/sync-agent-files.py --check` into a local pre-push hook.
Feature 004 may add the completed root gate to that hook; Foundation must not
wire a missing or placeholder gate. No CI is added now.

Add a concise next-step block format that reports the real state, an exact next
command or decision, and a self-contained prompt for the next task. Keep
high-value operational traps specific to the new repository as evidence
emerges; do not prefill them from the deleted application.

## Checks before accepting the workflow setup

- `AGENTS.md` is a regular file, has the nine sections in order, is no more
  than 200 lines, and has no more than ten non-negotiables.
- `CLAUDE.md` is a regular file and its first nonblank line is `@AGENTS.md`.
- Each real skill exists only below `.agents/skills/`; supported Claude and
  Codex views are links or generated artifacts rather than copied files.
- `python3 scripts/sync-agent-files.py --check` reports no drift and leaves the
  worktree unchanged.
- The Foundation local pre-push configuration invokes the drift check. Once
  feature 004 exists, integration work also invokes the real root verify
  command. It does not configure CI, deployment, or a remote-side action.
- The workflow names no database, accounts, chat, spectators, offline commands,
  multi-instance support, Brownfield migration, memory loader, or Superpowers
  bridge as baseline scope.

Record the exact generated paths, the checker command, and the local hook
mechanism in the new repository documentation after verifying them. Do not
invent paths or claim a hook works before it has been exercised.
