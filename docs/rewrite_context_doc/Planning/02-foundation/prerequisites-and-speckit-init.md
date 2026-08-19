# Foundation prerequisites and SpecKit initialization

## Purpose and phase

This is the Foundation-phase input that follows the reviewed cleanup commit. It
is consumed once on `feature/rewrite-foundation--initialize`, created from the
`feature/rewrite-foundation` parent after the cleanup task has been integrated
there. The parent itself descends from protected `rewrite_prod_ready`. Its
command creates the core SpecKit baseline; it
does not recreate the prior application, import its history, or start feature
implementation.

Read this file together with `constitution-input.md` and
`agent-workflow-input.md` before initialization.

## Preconditions

- The cleanup commit has been reviewed. The repository retains only `LICENSE`
  and `docs/rewrite_context_doc/` from the prior project, plus Git metadata.
- The active branch is exactly `feature/rewrite-foundation--initialize` and
  descends from `feature/rewrite-foundation` and `rewrite_prod_ready`. Never
  commit directly to the protected base or the parent branch.
- The worktree has no unrelated staged or unstaged tracked changes. Inventory
  every untracked path. Foundation never deletes or overwrites one; a collision
  with any target below is a blocker that its owner must resolve separately.
- Git, Java 25 LTS, Apache Maven 3.9.16, Node 24.19.0 LTS with npm, Python 3,
  and `uv` are available. Global Maven is a one-time bootstrap prerequisite:
  the backend feature uses it to create the pinned Maven Wrapper, after which
  contributors and root scripts use `backend/mvnw` instead. A Maven Wrapper is
  not expected in the cleaned repository.
- Before running the command, confirm and record the reviewed SpecKit 0.16.4
  release. A later stable release requires a separate review of its release
  notes and generated layout rather than an automatic upgrade.

Useful official sources:

- [Specify CLI and SpecKit project](https://github.com/github/spec-kit)
- [SpecKit releases](https://github.com/github/spec-kit/releases)
- [Node.js releases](https://nodejs.org/en/about/previous-releases)
- [Apache Maven download and current release](https://maven.apache.org/download.cgi)
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)

Record these read-only prerequisite checks before initialization:

```sh
git --version
java --version
mvn --version
node --version
npm --version
python3 --version
uv --version
specify --version
```

If SpecKit is not installed, install the reviewed release from its official
repository and verify it:

```sh
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v0.16.4
specify --version
```

## Initialization command

First prove what the installed CLI will write. Create a temporary directory
outside the repository, run the exact initialization command there, and list
all generated paths:

```sh
preview_dir="$(mktemp -d -t battleship-speckit-preview.XXXXXX)"
cd "$preview_dir"
specify init --here --force --script sh --integration codex --integration-options="--skills"
find . -mindepth 1 -print | LC_ALL=C sort
cd -
```

For reviewed SpecKit 0.16.4 this preview must generate only the top-level
`.specify/` and `.agents/` trees. If another top-level target appears, stop and
review the installed release before touching the repository. Back at the
repository root, run this collision preflight:

```sh
git status --short --branch
git ls-files --others
test ! -e .specify && test ! -L .specify
test ! -e .agents && test ! -L .agents
```

Abort if `.specify` or `.agents` exists in any tracked, untracked, ignored,
symlinked, or broken-symlink form. Do not use `--force` to resolve a collision.
The old tracked trees should already be gone in the reviewed cleanup commit; an
untracked collision belongs to its owner and must be moved, committed, or
otherwise resolved outside this procedure. Later Foundation work applies the
same rule before creating `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.codex/`, or
`scripts/sync-agent-files.py`.

Run this exact core-only command from the cleaned repository root:

```sh
specify init --here --force --script sh --integration codex --integration-options="--skills"
```

The installed CLI documents `--here`, `--force`, the `sh` script option, and
the Codex integration with its skills option. The command is intentionally the
whole initialization surface for the baseline. Do not add a brownfield preset,
memory loader, Superpowers bridge, extensions, deployment integration, or a
second agent integration.

## Expected output and checks

The command must create the SpecKit project infrastructure in the repository
root, including `.specify/` and the Codex integration material selected by the
command. Inspect the generated files rather than assuming the exact template
layout from an older release. The minimum expected checks are:

- `.specify/` exists and contains the installed workflow scripts, templates,
  and shared project infrastructure for the installed CLI release.
- The Codex integration assets requested by `--integration codex` and
  `--integration-options="--skills"` exist in their generated locations.
- The generated initialization output identifies no brownfield migration,
  memory-loader setup, Superpowers bridge, extension, or prior implementation
  import.
- `specify --version` and the CLI release source are recorded in the Foundation
  evidence.
- `git status --short` contains only intentional Foundation artifacts and does
  not alter `LICENSE` or any existing path under `docs/rewrite_context_doc/`.

If the current stable CLI changes the generated file layout or command behavior,
stop and compare its official release notes and `specify init --help` with this
document before accepting the result. Preserve the core-only scope even when
the template offers additional options.

## Baseline technology decisions

Use these reviewed baselines for the first Foundation setup:

- Java 25 LTS and a Maven 3.9.16 Wrapper created by the backend feature with
  Maven Wrapper Plugin 3.3.4. Global Maven is not used after that checked-in
  wrapper exists.
- Spring Boot 4.1.0.
- Spotless Maven Plugin 3.9.0; Spring Boot parent-managed Maven Enforcer 3.6.3
  and Surefire/Failsafe 3.5.6. Do not add a coverage quota or a second Java
  formatter.
- Node 24.19.0 LTS and npm.
- React and React DOM 19.2.8, Vite 8.2.1, and TypeScript 6.0.3. The newer
  TypeScript 7.0.2 is not selected because the stable TypeScript ESLint parser
  does not yet support its peer range.
- Redocly CLI 2.45.0 and Ajv 8.20.0 for contract validation.
- OpenAPI TypeScript 7.13.0, OpenAPI Fetch 0.17.0, Zod 4.4.3, and TanStack
  Query 5.101.4 for the frontend contract boundary.
- Vitest 4.1.10, React Testing Library 16.3.2, DOM Testing Library 10.4.1,
  User Event 14.6.3, jsdom 30.0.1, Playwright 1.62.1, Vite PWA 1.3.0, and
  `qr` 0.6.0 for focused frontend and integration needs.
- Vite React Plugin 6.0.5, ESLint 9.39.5, TypeScript ESLint 8.67.0, React
  Hooks ESLint 7.1.1, JSX A11y ESLint 6.10.2, and Prettier 3.9.6. Prettier is
  the only frontend formatter; ESLint owns non-formatting correctness rules.

Write exact dependency versions without ranges to the owning product's lockfile
and run its compatibility checks before acceptance. Do not substitute Gradle, Bun, pnpm,
Next.js, Prisma, JPA, or Flyway. This rewrite has no database, accounts, chat,
spectators, offline commands, multi-instance support, CI, or deployment work.

## Feature order after foundation

After initialization and the agent/constitution setup, create and execute
features in this order:

1. `001-api-contract` establishes the public versioned contract and its
   contract artifacts before backend or frontend behavior.
2. `002-backend` implements the server-authoritative game and privacy boundary
   against the accepted contract.
3. `003-frontend` implements the independent browser client against the
   accepted contract.
4. `004-integration` proves the packaged system and the cross-boundary flows.

For each feature, use the generated SpecKit SPEC command to create
`specs/<feature>/spec.md`, then the PLAN and task-generation commands to create
`specs/<feature>/plan.md` and `specs/<feature>/tasks.md` before feature code.
The four artifacts are independent deliverables; do not collapse them into one
large rewrite task.

## Template customization before the first feature

After initialization, update the generated SpecKit templates once as part of
the Foundation task:

- The specification template separates user-visible behavior and non-goals
  from technical design, uses named EARS sentences, and does not require
  numeric requirement labels.
- The plan template asks for product ownership, folder responsibilities, data
  flow, security, logging, practical tests, and local operation only when they
  apply to the feature.
- The task template orders real file changes by dependency, prevents false
  parallel work on shared files, and asks for scenario-to-test evidence without
  a large traceability ledger.
- The templates name the three products and four feature directories in this
  launch kit and treat `rewrite_prod_ready` as the protected base.
- No template requires fixed coverage, pixel snapshots, an ADR per dependency,
  CI, deployment, or an excluded product feature.

Review the template diff and exercise its rendering/check path before accepting
Foundation. Do not create any of the four feature specs during this template
task.
