---
name: project-documentation
title: Project Documentation
version: 2.0.0
description: >
  Generate comprehensive, accurate documentation for a service/repository from its actual contents —
  architecture, inputs/outputs, external services and integrations, data flow, business logic, data
  contracts, configuration, and project structure. Selectable output target: a maintained, committed
  set under `docs/` plus an updated `README.md`, OR a git-ignored working folder (`.agent-docs/`) for
  throwaway/scratch analysis. Use when the user asks to "document this project/service", "create
  repository docs", "write architecture docs", "describe the system end to end", or "initialize
  documentation". Produces written files; it does not just navigate (defer pure overview to
  project-navigator). Boundaries: never invents endpoints/fields/behavior (marks gaps "TODO: confirm");
  never pastes secret values into docs; never commits or pushes without the user.
tags: [documentation, architecture, service-docs, data-flow, business-logic, integrations, data-contracts, configuration, repository]
allowed-tools: Read, Grep, Glob, Write, Edit, Bash
references:
  - references/documentation-requirements.md
  - references/dual-output-workflow.md
  - references/project-structure-guide.md
  - references/mermaid-rules.md          # self-contained diagram rules — this skill does NOT depend on a separate Mermaid skill
scripts:
  - scripts/inventory.sh
  - scripts/detect-stack.sh
  - scripts/git-metadata.sh
assets:
  - assets/project-doc-template.md
  - assets/readme-template.md
# related-skills are OPTIONAL pointers only — this skill is fully self-contained and never requires another skill to be installed.
related-skills:
  - project-navigator: optional — deeper repository orientation (this skill already orients via its own scripts)
  - mermaid: optional — deeper diagram support (diagram rules are embedded here in references/mermaid-rules.md)
  - drawio: optional — for existing `.drawio` diagrams
install:
  defaultLocation: .claude/skills/project-documentation/
  supportsProject: true
  supportsGlobal: true
---

# Project Documentation

You are a senior technical writer who documents a service/repository by analyzing its code,
configuration, and infrastructure — accurately and from source, never invented.

## This repository

Single-module Maven project: Java 17 + Spring Boot 3.3.5 backend under
`ua.kostenko.battleship.battleship` (REST Controller → API/Service → Engine → Persistence), CRA/
TypeScript frontend in `frontend/`, bundled into one JAR (see root `CLAUDE.md` for the full
architecture map — read that first).

## When to use

"Document this service/repository", "write architecture/overview docs", "describe the system end to
end", "initialize project documentation".

## Output target (one parameter, two destinations — identical rules and structure)

- `output_target: committed` → maintained, human-facing docs in `docs/` plus an updated `README.md`,
  intended for review and commit. **This is the default deliverable.**
- `output_target: temporary` → a hidden, **git-ignored** `.agent-docs/` folder for scratch notes,
  extraction tables, and feeding other agent steps; never committed.

Only the destination (and the "is this committed?" framing) differs — the traversal, analysis, section
layout, and validation are shared. Read `references/dual-output-workflow.md` for the full rules of each
target (folder structure, `.gitignore` handling, overwrite confirmation).

## Workflow (load references progressively; gather facts with scripts first)

1. **Orient.** Run the bundled fact-gatherers (this skill is self-contained — it does not require the
   project-navigator skill) to determine project type and entry points:

   ```bash
   bash scripts/inventory.sh <repo-path>      # tree, counts, sizes, LOC, ext histogram
   bash scripts/detect-stack.sh <repo-path>   # inferred language/framework/build/test tools
   bash scripts/git-metadata.sh <repo-path>   # name, default branch, recent activity, contributors
   ```

2. **Extract** (into `.agent-docs/` working notes regardless of final target):
   - **Identity** (name, purpose, owners),
   - **Entry points** (REST/RPC routes, message listeners, scheduled jobs, CLIs, webhooks — with
     request/message shapes),
   - **Exit points** (outbound calls, publishers, database writes, external systems),
   - **Business logic** (each flow: entry → processing → exit, with branches/validations/errors),
   - **External services & integrations**, **Data contracts**, **Configuration** (environment/config/
     secret chain per environment — keys, never values).

   Read `references/documentation-requirements.md` for the per-section requirements and quality bar;
   read `references/project-structure-guide.md` for where things live per project type.

3. **Generate** to the selected target using `assets/project-doc-template.md` (and
   `assets/readme-template.md` when `output_target: committed`): Purpose → Architecture overview (with
   a render-ready **Mermaid** diagram authored per `references/mermaid-rules.md` — embedded here, no
   separate skill needed) → Inputs → Outputs → Data flow → Business logic → External services → Data
   contracts → Configuration → How to run/operate. Reference specific files (`path#symbol`).

4. **Finalize per target.**
   - **`temporary`:** ensure `/.agent-docs/` is in `.gitignore` (add it if missing).
   - **`committed`:** confirm before overwriting existing `docs/`/`README.md`; do **not** commit or push.

## Mandatory validation (before finishing)

- [ ] Every entry point and exit point found in code is documented.
- [ ] Cross-service references use consistent canonical names.
- [ ] Each business-logic flow is traceable entry → exit with file references.
- [ ] The configuration/secret chain is documented (keys, not values).
- [ ] No invented endpoints/fields/behavior; gaps marked "TODO: confirm".
- [ ] No section is silently omitted (write "None"/"N/A" with a note instead).
- [ ] Target honored: `temporary` → `.agent-docs/` is git-ignored; `committed` → `docs/`+`README.md`
      written, nothing committed/pushed.

## Output format & location

A populated doc set at the selected target (with at least one architecture/data-flow diagram), plus a
short completion report: files written, diagrams generated, sections with TODOs, and the target used.
End with `PROJECT_DOCS_COMPLETE`.

## Gotchas

- Multi-module / monorepo builds: scan all modules; the entry point may be in a submodule.
- Framework auto-configuration: some entry points (health/metrics endpoints, stream bindings) are
  implicit — check config, not just annotated handlers.
- Tests often reveal business rules not obvious in production code — read them during logic extraction.
- A single infrastructure-as-code construct can create many resources — read the construct/output.
- Stale existing docs: prefer code/config as the source of truth and flag contradictions.
- Never paste secret values into docs — reference the config key/path instead.
