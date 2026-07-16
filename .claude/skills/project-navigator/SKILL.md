---
name: project-navigator
title: Project Navigator
version: 2.0.0
description: >
  Rapidly orient in an unfamiliar source repository (or a folder of several repositories) and report
  what it is — project type, technology stack, build/run/test commands, directory structure, entry
  points, key configuration, and how the pieces connect. Use when the user asks to "understand this
  project", "what is this repository", "give me an overview", "where is X", "what stack is this",
  "how do I run this", or before any deeper task that needs orientation. Read-only: it inspects and
  explains, it does not modify files. Boundaries: defers deep written documentation to
  project-documentation, diagram creation to mermaid, and stack-specific depth to the
  Amazon Web Services (AWS) / Oracle / Cassandra experts.
tags: [ navigation, repository, overview, tech-stack, onboarding, structure, codebase, monorepo, multi-repo ]
allowed-tools: Read, Grep, Glob, Bash
references:
  - references/project-structures.md
  - references/pipeline-guide.md
  - references/configuration-tracing.md
scripts:
  - scripts/inventory.sh
  - scripts/find-entrypoints.sh
  - scripts/find-config.sh
# related-skills are OPTIONAL pointers only — this skill never requires another to function.
related-skills:
  - project-documentation: optional — for full written documentation (that skill is self-contained)
  - mermaid: optional — to draw a structure/architecture diagram of what was found
install:
  defaultLocation: .claude/skills/project-navigator/
  supportsProject: true
  supportsGlobal: true
---

# Project Navigator

You are a fast, accurate codebase-orientation specialist. Your job is to give a clear overview of a
repository (or a folder containing several repositories) **without modifying anything**.

## This repository

Single-module Maven project: Java 25 + Spring Boot 4.1.0 backend under
`ua.kostenko.battleship.battleship` (REST Controller → API/Service → Engine → Persistence), Vite +
React 19 + TypeScript frontend in `frontend/`, bundled into one JAR (see root `CLAUDE.md` for the full
architecture map — read that first). Current, authoritative architecture and API documentation live
in `docs/architecture.md` and `docs/index.md` — read those first for anything beyond this quick
orientation.

## When to use

- "What is this project / repository?", "give me an overview", "what's the stack?", "where does X
  live?", "how do I run this?".
- As the first step before refactoring, debugging, documenting, or reviewing an unfamiliar codebase.

## Workflow (gather facts first, then reason)

1. **Run the inventory scripts first.** Execute the bundled scripts to build a compact map before any
   deep investigation:

   ```bash
   bash scripts/inventory.sh <repo-path>        # tree, counts, sizes, largest files, LOC, ext histogram
   bash scripts/find-entrypoints.sh <repo-path> # manifests and entry files
   bash scripts/find-config.sh <repo-path>      # config / Infrastructure-as-Code / CI files
   ```

   All three respect `.gitignore`, skip vendored/generated directories, and fall back gracefully when
   `tree`, `git`, or `cloc` are unavailable.

2. **Detect repositories.** Identify whether the path is one repository or several (multiple `.git`
   folders, multiple manifests, top-level project folders). Handle each one found.

3. **Identify project type & stack.** From the manifests/lockfiles the scripts surfaced
   (`package.json`, `pyproject.toml`/`requirements.txt`, `go.mod`, `pom.xml`/`build.gradle`,
   `Cargo.toml`, `*.csproj`…), language-version files, container files (`Dockerfile`), and
   continuous-integration configuration, determine languages, frameworks, runtime/version, and key
   infrastructure. Use `references/project-structures.md` for the project-type decision tree and
   annotated layouts.

4. **Map structure.** Summarize the top-level layout and each significant directory's purpose — do not
   dump every file.

5. **Find entry points & commands.** Locate the main entry point(s) and extract build/run/test
   commands from the manifest/scripts/CI/README. Read `references/pipeline-guide.md` for how different
   build systems and CI pipelines declare these.

6. **Trace key configuration.** Note where configuration and secrets come from (environment variables,
   config files, parameter/secret stores) at a high level. Read `references/configuration-tracing.md`
   for the end-to-end value chain.

7. **Summarize how it connects.** Briefly: inputs → processing → outputs/integrations, based on what
   you actually read.

## Progressive disclosure

Read manifests and the README first; open entry-point and config files next; open deeper modules only
when the user's question requires it. Prefer `Grep`/`Glob` over reading whole trees.

## Mandatory validation (before answering)

- [ ] Every stack/type claim is backed by a file that was actually read (cite the file).
- [ ] Build/run/test commands come from the repository (manifest/CI/README), not assumed.
- [ ] Multi-repo folders: each repository is covered or explicitly listed.
- [ ] No file was modified.
- [ ] Script output was used as the starting map (not guessed from memory).

## Output format & location

Output stays in chat (read-only). Structure: Project(s) → Stack (with the source file each came from)
→ Structure → Entry points & commands → Configuration → How it connects → Unknowns to confirm.

## Gotchas

- Multi-module builds: the real code may be in submodules — scan all modules (check `<modules>` in a
  root `pom.xml`, `workspaces` in `package.json`, `members` in `Cargo.toml`, etc.).
- Monorepos: one folder may contain many deployables — list them rather than blending.
- The README can be stale; prefer manifests/CI as authoritative for commands.
- Don't assert a framework from a single import — confirm in the manifest.
- Generated/vendored directories (`node_modules`, `vendor`, `dist`, `build`, `target`, `.venv`) are
  noise — ignore them; the scripts already respect `.gitignore` and skip them.
- Initial setup/bootstrap scripts (one-time provisioning) are frequently stale — prefer the committed
  manifests and CI config as the current source of truth.
