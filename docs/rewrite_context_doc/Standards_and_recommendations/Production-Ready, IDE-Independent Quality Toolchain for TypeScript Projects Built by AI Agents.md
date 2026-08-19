# Production-Ready, IDE-Independent Quality Toolchain for TypeScript Projects Built by AI Agents

## 1. Executive Summary

The correct 2026 stack separates six concerns into distinct CLI tools, each with deterministic exit codes and machine-readable output, all runnable via `package.json` scripts so developers, AI agents, git hooks, and CI invoke the *same* commands. The recommended production default is: **tsc** (type checking) + **ESLint 9 flat config with typescript-eslint** (syntax and type-aware linting) + **Prettier 3.7** (formatting, single owner) + **Knip** (dead code / unused deps) + **npm audit / bun audit + OSV-Scanner** (supply chain) + **Lefthook** (git hooks). This combination reproduces every important IDE diagnostic on the command line.

The single most consequential finding as of late July 2026: **TypeScript 7.0 reached GA on July 8, 2026** — a native Go port that the official TypeScript team blog announced as "a 10x faster native port of TypeScript," characterizing the speedup as "typically between 8x and 12x on full builds" (Microsoft's own benchmarks show the VS Code codebase check dropping from 125.7s to 10.6s — 11.9x — and Sentry from 139.8s to 15.7s, with memory down 6–26%). But **TS 7.0 ships without a stable programmatic compiler API until 7.1**, and **typescript-eslint pins `typescript >=4.8.4 <6.1.0` and is structurally incompatible with TS 7** (issue #12518, filed and closed-as-not-planned on the day of GA because the fix is on the TS 7.1 side). Any project that wants both TS 7's fast `tsc` and type-aware ESLint must run a **split setup**: TS 7 for `tsc --noEmit`, and Microsoft's `@typescript/typescript6` compatibility package to feed the TS 6.0 API to ESLint. This is the dominant migration constraint of the moment and dictates version pinning across all three recommended stacks.

Three key architectural rulings: (1) **one formatter owner** — Prettier for most projects, Biome only when you deliberately adopt its unified toolchain; never both. (2) **Type-aware linting is worth its cost** but must be scoped with typescript-eslint's `projectService`; Oxlint is now a viable fast complement (its type-aware linting reached stable on July 22, 2026) but not yet a full ESLint replacement because of plugin-ecosystem gaps. (3) **Knip replaces the now-archived `depcheck` and `ts-prune`** as the single dead-code/dependency tool.

*Classification legend used below: [VF] Verified fact (official docs/primary source); [VC] Vendor/maintainer claim; [CR] Community-reported; [RI] Researcher inference.*

## 2. What IDE Diagnostics Must Be Reproduced

VS Code "Problems" and WebStorm inspections are surfaced from underlying engines that all have CLIs. The agent must reproduce:

- **TypeScript red squiggles** → `tsc --noEmit`. The editor uses the same `tsserver`; the CLI is authoritative. [VF] One important difference: editors type-check open files eagerly and may use a different effective `tsconfig` scope, so the CLI over a full project catches errors the editor misses. [RI]
- **ESLint underlines** → `eslint .`. The VS Code ESLint extension just runs ESLint; the CLI is the source of truth. [VF]
- **"X is deprecated" strikethrough** → NOT reported by `tsc` on its own; requires the type-aware `@typescript-eslint/no-deprecated` rule. typescript-eslint docs state: "TypeScript recognizes the `@deprecated` tag, allowing editors to visually indicate deprecated code — usually with a strikethrough. However, TypeScript doesn't report type errors for deprecated code on its own." [VF]
- **Unused import/variable graying** → `tsc` (`noUnusedLocals`/`noUnusedParameters`) and/or ESLint `no-unused-vars`.
- **Auto-import organization / import sorting** → formatter or a dedicated ESLint/Biome step, not an IDE-only action.
- **Unused files/exports/dependencies project-wide** → no editor shows these reliably; requires Knip.
- **React Hooks warnings** → `eslint-plugin-react-hooks`.

Diagnostics that **cannot** be fully reproduced statically and must be flagged as requiring runtime tests or a browser: React runtime warnings (hydration mismatches, key collisions surfaced at render, effect double-invocation in StrictMode), actual HTTP/stream/resource-leak behavior, and anything depending on runtime values (env-var presence at deploy time). These need unit/integration tests, not linters. [RI]

## 3. TypeScript Compiler Configuration

`tsc --noEmit` is the primary validation gate (or `tsc -b --noEmit` for project references). Enable these **immediately** in any new project:

- `"strict": true` — the non-negotiable baseline (enables `noImplicitAny`, `strictNullChecks`, `strictFunctionTypes`, `strictBindCallApply`, `strictPropertyInitialization`, `noImplicitThis`, `useUnknownInCatchVariables`, `alwaysStrict`). [VF]
- `"noUncheckedIndexedAccess": true` — adds `undefined` to indexed access; catches a large class of runtime errors. Enable immediately in new projects; **stage** in legacy code (it is noisy on existing array/object index access, and interacts awkwardly with `for...in`/`for...of` key access). [VF/CR]
- `"noImplicitOverride": true` — cheap, enable immediately.
- `"noFallthroughCasesInSwitch": true`, `"noImplicitReturns": true` — enable immediately.
- `"noUnusedLocals": true`, `"noUnusedParameters": true` — enable immediately in new projects; these are *also* covered by ESLint and can be delegated there to keep `tsc` output focused (see §4 overlap).
- `"verbatimModuleSyntax": true` — forces explicit `import type`/`export type` and, with `module: nodenext`, enforces correct ESM/CJS import syntax. Enable immediately in new ESM projects; stage in legacy CJS-interop code. [VF]
- `"exactOptionalPropertyTypes": true` — the strictest and most disruptive; **stage this one** — it frequently requires code changes and is the most likely to generate churn. [VF/RI]

Module/resolution settings [VF]:
- Node.js backend: `"module": "nodenext"`, `"moduleResolution": "nodenext"`.
- Bundler frontend (Vite/Next): `"module": "esnext"`, `"moduleResolution": "bundler"`.
- React: `"jsx": "react-jsx"` (no `import React` needed).
- Always: `"skipLibCheck": true`, `"isolatedModules": true`, `"forceConsistentCasingInFileNames": true`, `"moduleDetection": "force"`.
- TS 5.8+/6.0+/7.0: `"erasableSyntaxOnly": true` if you rely on Node's native type stripping (bans enums/namespaces/parameter properties that cannot be erased).

Project references / monorepos: use `"composite": true` + `"incremental": true` on library packages, and a root solution `tsconfig.json` with `"references"`. Build with `tsc -b`. Incremental builds write `.tsbuildinfo`; cache this in CI. [VF]

Library vs application: libraries emit declarations (`"declaration": true`, `"declarationMap": true`, `"outDir"`); applications commonly set `"noEmit": true` and let a bundler/transpiler emit. Test files **should** be type-checked — include them in a `tsconfig` (often a `tsconfig.test.json` referencing the base) so tests get the same rigor. [RI]

Bun compatibility: Bun runs TS directly and ships its own types; still run `tsc --noEmit` for checking. Use `"moduleResolution": "bundler"` and add Bun's types to `"types"`. [VF/RI]

**TS 7 caveat [VF]:** `tsc` from TS 7.0 is a drop-in for checking and dramatically faster, but if you use typescript-eslint's type-aware rules, keep TS 6.0 available via the `@typescript/typescript6` compatibility package (which provides a `tsc6` binary and re-exports the TS 6.0 API) for ESLint until typescript-eslint ships TS 7 support. Microsoft ran extensive pre-release testing of Project Corsa with named firms including Bloomberg, Canva, Figma, Google, Linear, Notion, Slack, Vercel, and VoidZero. Also note TS 7.0 has no public compiler API, which blocks Vue/Astro/Svelte/Angular-template tooling until 7.1. [VF]

Editor vs CLI difference to flag: editors may show *fewer* errors (open-files-only) or *different* errors (different effective tsconfig). CLI `tsc` over the whole project is authoritative; the agent must trust the CLI, not the editor. [RI]

## 4. Linter Comparison Matrix

| Tool | Type-aware? | Config | React/Node plugins | License | Role in 2026 |
|---|---|---|---|---|---|
| **ESLint 9 + typescript-eslint** | Yes (full, 61 rules) | Flat (default in v9) | Full ecosystem | MIT | **Primary linter.** Only option with complete plugin ecosystem + full type-aware coverage. |
| **Oxlint (+ tsgolint)** | Yes (59/61 rules, stable 2026-07-22) | `.oxlintrc.json` / `oxlint.config.ts` | Growing, partial | MIT | **Fast complement / pre-commit.** Not yet a full replacement. |
| **Biome** | Partial (~85% coverage, no tsc) | `biome.json` | Partial | MIT | All-in-one alternative; strong if you adopt its formatter too. |

Verified specifics:
- **typescript-eslint** enables type-aware rules via `parserOptions.projectService: true` (the modern, faster replacement for the older `project: [...]` glob approach). Use the `recommendedTypeChecked` + `strictTypeChecked` + `stylisticTypeChecked` shared configs. [VF]
- **Rules that duplicate `tsc`:** `noUnusedLocals`/`noUnusedParameters` vs `@typescript-eslint/no-unused-vars`. Pick one owner — delegating unused-vars to ESLint (which has autofix and `argsIgnorePattern`) is common; then leave those off in tsc, or vice versa. Do not report both. [RI]
- **Rules requiring type information** (the high-value ones): `no-floating-promises`, `no-misused-promises`, `await-thenable`, the `no-unsafe-*` family, `no-deprecated`, `no-unnecessary-condition`. These need `projectService` and are the main performance cost. [VF]
- **Performance:** typed linting can add tens of seconds to minutes on large repos — one engineering report measured a 7-minute cold run of typescript-eslint on a production monorepo. Scope with `projectService`, TypeScript project references, and per-directory `files` blocks. [CR]
- **Different configs per area — yes:** frontend (`browser` globals, React/JSX, hooks), backend (`node` globals, `eslint-plugin-n`), tests (Vitest/Jest globals, relaxed rules), and config/script files (disable type-checking via `tseslint.configs.disableTypeChecked`). [VF]
- **Oxlint** now offers type-aware linting through `tsgolint` (Go, built directly on TS 7). Per the Oxc blog (2026-07-22): "we're releasing tsgolint v7 … This release tracks TypeScript v7.0.2 and brings tsgolint to 59 of typescript-eslint's 61 type-aware rules," with benchmarks showing "tsgolint was 12 to 18 times faster than ESLint with typescript-eslint across four large TypeScript codebases" (measured on an Apple M4 Pro, 12 cores). [VC] Critically, Oxlint is *unaffected* by the TS 7 API gap because tsgolint links against TS 7 internals directly. But it cannot yet run the full React/Next/import/security plugin ecosystem, so treat it as a fast first pass with ESLint as the complete check. Enable via `oxlint --type-aware` (config: `typeAware: true` in `oxlint.config.ts`). [VF]

## 5. Formatter Comparison Matrix

| Tool | Determinism | Languages | Import sorting | Tailwind | Owner recommendation |
|---|---|---|---|---|---|
| **Prettier 3.7** | High (when pinned) | JS/TS/JSX, JSON/JSONC, CSS, MD, YAML, GraphQL, HTML (plugins) | via plugin | via plugin | **Default formatting owner.** |
| **Biome formatter** | High | JS/TS/JSX, JSON, CSS, GraphQL, HTML (stabilizing) | built-in (assist) | plugin | Owner only if adopting Biome wholesale. |
| **ESLint stylistic rules** | — | — | — | — | **Do NOT use for formatting.** |

Rulings:
- **Recommend exactly one owner: Prettier** for most projects. It is the de-facto standard, deterministic when version-pinned, and covers config files (JSON/YAML/Markdown) comprehensively. [RI]
- **Prevent formatter/linter conflicts:** turn OFF all stylistic/formatting ESLint rules. Use `eslint-config-prettier` (the flat-config `prettier` config, spread **last**) which disables every ESLint rule that could conflict with Prettier. Do **not** run `eslint-plugin-prettier` (Prettier-as-an-ESLint-rule) in agentic/CI workflows — it slows linting and mixes concerns; run `prettier --check` as its own step. [RI]
- **Import sorting ownership:** give it to the formatter or a dedicated plugin — not stylistic ESLint. Prettier itself does not reorder imports; use `@ianvs/prettier-plugin-sort-imports` or `prettier-plugin-organize-imports`. Biome does import organization natively (`assist`/`organizeImports`). Choose one. [VF]
- **Version pinning is mandatory.** A Prettier minor upgrade can reformat the whole repo — Prettier 3.7 (Nov 27, 2025) explicitly "focuses on polishing the TypeScript and Flow experience, specifically by aligning the formatting of classes and interfaces." Pin exactly (`"prettier": "3.7.0"`, no caret) and upgrade deliberately in a dedicated PR. [VF]
- Prettier 3.6 (June 23, 2025) introduced an experimental fast CLI (`--experimental-cli`, or `PRETTIER_EXPERIMENTAL_CLI=1`) and the official `@prettier/plugin-oxc`. [VF]
- npm and Bun both run Prettier via scripts identically (`bunx prettier` / `npx prettier`). Monorepo: run from root with a single config + `.prettierignore`. [VF]

## 6. Dead-Code and Dependency-Analysis Comparison

| Tool | Status | Detects | Verdict |
|---|---|---|---|
| **Knip** | Actively maintained, 150+ plugins | Unused files, exports, deps, missing deps, unlisted binaries, unused enum members, duplicate exports | **Recommended single tool.** |
| **depcheck** | **Archived 2025** | unused/missing deps | Do not adopt. |
| **ts-prune** | **Archived 2025** | unused exports | Do not adopt (author points to Knip). |
| **unimported** | Superseded | unused files/deps | Superseded by Knip. |
| **tsc `noUnusedLocals`** | Maintained | in-file unused only | Complements Knip (file-scope only). |

Knip is the clear winner on correctness + maintenance. It analyzes the whole project graph (entry points, import graph, exports, `package.json`) and has framework plugins (Next.js, Vite, Remix, Astro, Nx, Vitest, Storybook, etc.) that understand file-based entry points. [VF] `depcheck` and `ts-prune` were both archived in 2025 [CR].

Known limitations to configure around (all documented): dynamic imports, DI containers, CLI entry points, package `exports` maps, code generation, React lazy loading, and framework file conventions may cause false positives — fix by declaring `entry`/`project` patterns in `knip.json` rather than blanket-ignoring. Work findings top-down (files → exports → deps), since resolving files removes downstream false positives; "Hiding a result is not the same as resolving it." Reach for `ignore*` options only as a last resort. [VF]

## 7. React-Specific Recommendations

- **`eslint-plugin-react-hooks`** (maintained by the React team, synced to React releases): use the flat preset `reactHooks.configs.flat.recommended`. This ships `rules-of-hooks` and `exhaustive-deps`. The `recommended-latest` preset adds React Compiler-powered rules: `set-state-in-effect`, `refs`, `purity`, `error-boundaries`, `preserve-manual-memoization`, `component-hook-factories`, and `config`. [VF]
- **`eslint-plugin-react`** (`jsx-eslint`): JSX correctness, unstable keys, and general component rules via `reactPlugin.configs.flat.recommended` + `flat['jsx-runtime']`. [VF]
- **Server/Client component boundaries (Next.js):** use `@next/eslint-plugin-next`; the `'use client'`/`'use server'` boundary rules and RSC constraints are Next-specific. [RI]
- **Deprecated React APIs:** caught by `@typescript-eslint/no-deprecated` (reads `@deprecated` JSDoc from React's type definitions). Note a known limitation: this rule currently under-reports deprecated *JSX props* (typescript-eslint issue #10275). [VF]

Statically detectable: rules-of-hooks violations, missing/incorrect effect deps, invalid hook calls, some unstable-key patterns, deprecated APIs. **Not** statically detectable (needs tests/runtime): hydration mismatches, actual render-loop/state-update bugs, key collisions only visible at runtime, effect cleanup correctness. A general ESLint setup does **not** automatically cover modern React — you must add these plugins explicitly. [RI]

## 8. Node.js-Specific Recommendations

- **`eslint-plugin-n`** (eslint-community fork of the dead `eslint-plugin-node`): `no-unsupported-features/*` (checks against `engines.node`; defaults to `>=16.0.0` if unspecified), `no-deprecated-api` (deprecated Node APIs), `no-unpublished-import` (prevents shipping devDependency imports → avoids "Module Not Found" after publish), and `no-missing-import`. Provides `n/recommended-module`, `n/recommended-script`, and `n/mixed-esm-and-cjs` presets. [VF]
- **Unhandled promises / missing await:** typescript-eslint's `no-floating-promises` and `no-misused-promises` (type-aware) are the highest-value backend rules — enable them. [VF]
- **ESM/CJS errors:** `verbatimModuleSyntax` in tsc + `eslint-plugin-n` module presets. [VF]
- **Security-sensitive patterns / input validation:** ESLint alone is weak here. Add **Semgrep CE** (LGPL-2.1, free, runs locally, no login, 30+ languages, ~10s median scan, SARIF/JSON output) with its `p/typescript` and `p/javascript` rulesets for taint-style patterns (SQLi, XSS, hardcoded secrets, JWT `none` algorithm). Semgrep CE is single-file/single-function; cross-file dataflow (taint) is a paid Platform feature. [VF]
- **Env-var validation** is a runtime concern — enforce with a schema library (Zod/`envalid`) at startup, not a linter. [RI]

Coverage summary: TypeScript catches type-level API misuse; ESLint (+ `n` + typescript-eslint) catches async/promise/deprecation/module issues; Semgrep catches security patterns. Stream misuse and resource-cleanup correctness are largely runtime concerns requiring tests. [RI]

## 9. npm versus Bun Differences

- **Scripts:** identical `scripts` block works for both; run with `npm run <x>` or `bun run <x>`. `bunx` ≈ `npx`. [VF]
- **Audit:** `npm audit` is mature (`--audit-level`, `--omit=dev`, `npm audit fix`). **`bun audit`** landed in **Bun v1.2.15** — per Bun's blog (May 28, 2025; release tagged July 18, 2025): "bun audit performs security audits of your project's dependencies defined in bun.lock. It's like npm audit but for Bun… This uses the same API endpoint that npm audit uses." It supports `--audit-level=<low|moderate|high|critical>`, `--prod`, `--ignore <CVE>`, `--json`, and exits 1 on findings. **Key gap: Bun has no `bun audit fix`** (open issue #20238) — you must bump versions manually or migrate the lockfile to npm to auto-fix. [VF]
- **Lockfiles:** `package-lock.json` (npm) vs `bun.lock` (text, since Bun 1.2). Frozen installs: `npm ci` (always uses lockfile, fails on mismatch) vs `bun install --frozen-lockfile`. [VF]
- **Provenance:** npm supports SLSA provenance + trusted publishing. Per the GitHub Changelog (July 31, 2025), "npm trusted publishing with OIDC is generally available … When using trusted publishing, npm CLI publishes provenance attestations by default. The `--provenance` flag is no longer needed." It "requires npm CLI version 11.5.1 or later and Node version 22.14.0 or higher." Bun publishing does not generate npm provenance the same way — publish libraries with npm for provenance. [VF]
- **Corepack / packageManager:** the `"packageManager"` field pins the package-manager version; Corepack enforces it for npm/pnpm/yarn. Bun is not managed by Corepack — pin Bun via CI setup action / `.tool-versions` / Docker base image. [VF/RI]
- **Registry-scope caveat:** `bun audit` skips packages installed from non-default registries. [VF]

Recommendation: OSV-Scanner (below) is the runtime-agnostic supply-chain backstop that works identically regardless of npm vs Bun, since it reads the lockfile directly. [RI]

## 10. Dependency and Supply-Chain Security

Layer three complementary checks — they detect different things:
1. **Vulnerability scanning (known CVEs):** `npm audit` / `bun audit` (fast, advisory-DB based, exit 1 on findings) **plus** **OSV-Scanner v2** (Google, Apache-2.0, free, no account, no usage limits, 11+ ecosystems / 19+ lockfile formats, queries OSV.dev which normalizes NVD + GitHub Advisories + ecosystem sources). OSV-Scanner is the CI backstop: `osv-scanner -r .` or `osv-scanner --lockfile=package-lock.json`. V2 (March 2025) added container scanning, guided remediation (`osv-scanner fix --strategy=in-place|relock`), and interactive HTML output. It also does license checks (`osv-scanner --licenses="MIT,Apache-2.0" .`) and SBOM scanning, and can run offline against a downloaded local DB. [VF]
2. **Malicious-package detection (supply-chain attacks, NOT CVEs):** vulnerability scanners do not catch typosquatting/malware/install-script attacks. Bun's own docs note `bun audit` "only catches known, documented advisories. Does not detect zero-day exploits, malware, misconfigurations, or supply-chain attacks not yet reported." Use **Socket** (free tier for open-source / GitHub app) for behavioral/malware signals. This is a distinct capability from CVE scanning. [VF]
3. **Update automation:** **Renovate** (free, self-hostable, highly configurable, groups updates, respects `packageManager`) or **Dependabot** (built into GitHub, simpler). Prefer Renovate for monorepos. [RI]

Other essentials:
- **Lockfile validation / reproducibility:** `npm ci` / `bun install --frozen-lockfile` in CI. Never `npm install` in CI. [VF]
- **npm provenance / trusted publishing** for published libraries (OIDC, no long-lived tokens; provenance auto-generated). Provenance proves *where and how* a package was built but "does not guarantee the package has no malicious code." [VF]
- **SBOM:** `@cyclonedx/cyclonedx-npm` generates CycloneDX SBOMs; feed to OSV-Scanner.
- **CI exit codes:** all these tools exit non-zero on findings — that is the gate. Use `--audit-level=high` to avoid failing on low-severity noise, and document any `--ignore <CVE>` suppression with a reason and expiry.
- **Scheduled scans:** run full audit + OSV-Scanner nightly, because new advisories appear for unchanged code. [RI]

## 11. Minimal, Production, and Strict Stacks

### Configuration A — Minimal baseline (small TypeScript app)
- **Tools:** tsc + ESLint 9 (flat, typescript-eslint `recommended`, no type-aware) + Prettier (pinned).
- **Responsibilities:** tsc = types; ESLint = syntax lint; Prettier = formatting (sole owner via `eslint-config-prettier`).
- **Overlap avoided:** no stylistic ESLint.
- **npm:** `npm run typecheck` (`tsc --noEmit`), `npm run lint` (`eslint .`), `npm run format:check` (`prettier --check .`). **Bun:** same via `bun run`.
- **React additions:** `eslint-plugin-react-hooks` recommended. **Node additions:** none beyond core.
- **Pre-commit:** Prettier + ESLint on staged files. **CI:** typecheck + lint + format:check.
- **Perf:** fast (no type-aware lint). **Maintenance/upgrade risk:** minimal.

### Configuration B — Production default (most React/Node apps)
- **Tools:** tsc (strict) + ESLint 9 with typescript-eslint **type-aware** (`recommendedTypeChecked` + `strictTypeChecked` + `stylisticTypeChecked`) + React plugins (`react-hooks`, `react`) or `eslint-plugin-n` for backend + Prettier (pinned) + Knip + `npm/bun audit` + OSV-Scanner + Lefthook.
- **Responsibilities:** each concern isolated; unused-vars delegated to ESLint; deprecation via `no-deprecated`; dead code via Knip; CVEs via audit + OSV.
- **React additions:** `reactHooks.configs.flat.recommended`, `reactPlugin.configs.flat.recommended` + `flat['jsx-runtime']`, `@next/eslint-plugin-next` if Next.
- **Node additions:** `eslint-plugin-n` recommended preset; enable `no-floating-promises`/`no-misused-promises`.
- **npm/Bun:** `verify` = typecheck && lint && format:check && dead-code && security-check.
- **Pre-commit (Lefthook):** format + lint staged files (fast). **Pre-push:** typecheck + knip. **CI:** full `verify` on PRs; scheduled security scan.
- **Perf:** type-aware lint is the cost; scope with `projectService`. Optionally add Oxlint as a fast pre-commit pass.
- **Maintenance/upgrade risk:** the TS 7 / typescript-eslint incompatibility — pin TS 6 for ESLint via `@typescript/typescript6`, or stay on TS 6 until typescript-eslint supports TS 7.

### Configuration C — Strict monorepo / high-assurance
- **Everything in B**, plus: TypeScript **project references** + `tsc -b --noEmit`; **Turborepo** (or Nx) to orchestrate/cache `lint`/`typecheck`/`test`; ESLint **module-boundary** rules (`eslint-plugin-import-x` — the maintained fork with a Rust resolver, 16 deps vs 117, and `exports`-field support — or Nx `enforce-module-boundaries`); Knip in monorepo mode; **Semgrep CE** in CI; **OSV-Scanner** license + SBOM gates; **Renovate**; npm **trusted publishing + provenance**; **ESLint bulk suppressions** baseline for legacy code.
- **Monorepo additions:** `turbo run typecheck lint test` (Turborepo 2.x `tasks` key, `dependsOn: ["^build"]`); per-package `tsconfig` with `composite`; shared `eslint-config`/`tsconfig` packages; Lefthook commands scoped per directory (`root:`).
- **CI:** `turbo run` (affected only on PRs, full on main) + scheduled full security + license scan.
- **Perf:** Turborepo caching makes typed lint tractable; cache `.tsbuildinfo`. Consider Oxlint type-aware (`tsgolint`, TS 7-based) as the fast lane since it side-steps the TS 7 API gap.
- **Maintenance/upgrade risk:** highest config surface; mitigate with shared config packages and pinned versions.

## 12. Repository Command Contract

Stable top-level scripts the agent always uses, regardless of underlying tools:

```jsonc
{
  "scripts": {
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "typecheck": "tsc --noEmit",
    "lint": "eslint .",
    "lint:fix": "eslint . --fix",
    "dead-code": "knip",
    "dependency-check": "knip --dependencies",
    "security-check": "npm audit --audit-level=high && osv-scanner -r .",
    "verify:fast": "npm run format:check && npm run lint",
    "verify": "npm run format:check && npm run typecheck && npm run lint && npm run dead-code && npm run security-check"
  }
}
```

- `verify:fast` = seconds, for the edit loop (format + syntax lint, no type-aware, no network).
- `verify` = the single complete gate run before completing work; deterministic exit code.
- Monorepo: top-level scripts delegate to `turbo run` so the interface stays stable.
- Bun: identical, invoked with `bun run` (`bun run verify`); swap `npm audit` → `bun audit`.

## 13. Pre-commit and CI Workflow

**Recommendation: Lefthook** (single Go binary, one `lefthook.yml`, parallel execution, monorepo-aware, no Node startup tax, no separate `lint-staged` needed). Husky + lint-staged remains fine if already in use — it is battle-tested (~5M weekly downloads) — but Lefthook needs fewer moving parts. Avoid the Python `pre-commit` framework for pure JS/TS repos (extra runtime). [CR/RI]

Allocation of checks:
- **Staged files (pre-commit, fast):** format + ESLint on staged files. **Warning:** linting only staged files misses type errors and dependency issues that arise from interactions with *unchanged* files — do NOT rely on staged-only linting for correctness. [RI]
- **Pre-push:** `tsc --noEmit` (whole project — type checking is inherently cross-file) + Knip.
- **PR/CI:** full `verify` on all files; in monorepos, `turbo run` affected.
- **Scheduled:** security + license scans.

The rule: fast, staged-only feedback locally; complete, whole-project enforcement in CI. Type checking and dead-code detection are cross-file and must run over the whole project, never staged-only.

## 14. AI-Agent Operating Instructions

1. **Format first:** run `npm run format` after editing, before other checks, so formatting noise doesn't obscure real issues.
2. **Targeted checks while editing:** run `eslint <path>` and `tsc --noEmit` frequently; use `verify:fast` for the tight loop.
3. **Complete check before completion:** run `npm run verify` and ensure exit 0 before declaring work done.
4. **Distinguish new from legacy findings.** ESLint has a native baseline: `eslint --suppress-all` writes `eslint-suppressions.json` (feature added in **ESLint v9.24.0, April 2025**; docs: "This feature allows for enabling new lint rules as 'error' without fixing all violations upfront. While the rule will be enforced for new code, the existing violations will not be reported"). Commit that file; new violations then fail CI while legacy ones are held. Use `eslint --suppress-rule <rule>` for a single rule, `eslint --prune-suppressions` as issues are fixed, and `--max-warnings 0` to fail on any new warning. For tools *without* a baseline — **Knip, Oxlint, and Biome have no findings-baseline file** — gate on changed files instead: `git diff --name-only origin/main | xargs eslint`, `biome lint --changed` (requires VCS config + `defaultBranch`), or `eslint-plugin-diff` (`ESLINT_PLUGIN_DIFF_COMMIT="origin/main"`) to filter to changed *lines*. Do **not** treat a permanent baseline as a substitute for fixing problems — burn it down over time.
5. **Avoid unsafe autofix:** `eslint --fix` and Biome both distinguish "safe" from "unsafe" fixes; the agent must not assume any autofix is behavior-preserving. Review diffs; never blind-apply unsafe fixes.
6. **Report unresolved issues:** if a finding can't be fixed, surface it explicitly with the rule name and reason — do not silence it.
7. **Never disable rules without a documented technical reason:** inline `// eslint-disable-next-line <rule> -- <reason>` with a reason, never a blanket file/global disable, and never disable merely to make CI green.

## 15. Example `package.json`

```jsonc
{
  "name": "app",
  "type": "module",
  "packageManager": "npm@11.5.1",
  "engines": { "node": ">=22.14.0" },
  "scripts": {
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "typecheck": "tsc --noEmit",
    "lint": "eslint .",
    "lint:fix": "eslint . --fix",
    "dead-code": "knip",
    "security-check": "npm audit --audit-level=high && osv-scanner -r .",
    "verify:fast": "npm run format:check && npm run lint",
    "verify": "npm run format:check && npm run typecheck && npm run lint && npm run dead-code && npm run security-check",
    "prepare": "lefthook install"
  },
  "devDependencies": {
    "typescript": "6.0.3",
    "eslint": "9.39.0",
    "typescript-eslint": "8.63.0",
    "@eslint/js": "9.39.0",
    "eslint-plugin-react-hooks": "7.0.0",
    "eslint-plugin-react": "7.37.5",
    "eslint-config-prettier": "10.1.8",
    "prettier": "3.7.0",
    "knip": "5.64.0",
    "lefthook": "1.13.6"
  }
}
```
Pin exactly — no carets — for anything that can change diagnostics or formatting. **TypeScript is pinned to 6.0.x deliberately** for typescript-eslint compatibility (see §3/§4). If you want TS 7's fast `tsc`, add `typescript@7` plus `@typescript/typescript6` and point ESLint at the compat package while running `tsc` from TS 7. (Version numbers are representative of the pinning *pattern*; verify the latest patch at adoption time.)

## 16. Example ESLint Configuration (flat, `eslint.config.mjs`)

```js
// @ts-check
import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import react from 'eslint-plugin-react';
import prettier from 'eslint-config-prettier';

export default defineConfig(
  { ignores: ['dist/**', 'coverage/**', '**/*.d.ts'] },
  js.configs.recommended,
  tseslint.configs.strictTypeChecked,
  tseslint.configs.stylisticTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  // Frontend / React
  {
    files: ['src/**/*.{ts,tsx}'],
    ...react.configs.flat.recommended,
    ...react.configs.flat['jsx-runtime'],
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { 'react-hooks': reactHooks },
    extends: [reactHooks.configs.flat.recommended],
  },
  // Tests: relax selected rules
  {
    files: ['**/*.test.ts', '**/*.spec.ts'],
    rules: { '@typescript-eslint/no-non-null-assertion': 'off' },
  },
  // Config/JS files: no type-aware linting
  {
    files: ['**/*.{js,mjs,cjs}'],
    extends: [tseslint.configs.disableTypeChecked],
  },
  // MUST be last: turn off all formatting rules that conflict with Prettier
  prettier,
);
```

## 17. Example TypeScript Configuration (`tsconfig.json`)

```jsonc
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "noImplicitOverride": true,
    "noFallthroughCasesInSwitch": true,
    "noImplicitReturns": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "exactOptionalPropertyTypes": true,   // stage in legacy code
    "verbatimModuleSyntax": true,
    "module": "nodenext",
    "moduleResolution": "nodenext",
    "target": "es2023",
    "jsx": "react-jsx",
    "isolatedModules": true,
    "moduleDetection": "force",
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true,
    "noEmit": true
  },
  "include": ["src/**/*", "tests/**/*"],
  "exclude": ["node_modules", "dist"]
}
```
Frontend/bundler variant: `"module": "esnext"`, `"moduleResolution": "bundler"`. Library variant: drop `"noEmit"`, add `"declaration": true`, `"declarationMap": true`, `"outDir": "./dist"`, `"composite": true`.

## 18. Example Formatter Configuration (`.prettierrc.json` + `.prettierignore`)

```jsonc
// .prettierrc.json
{
  "singleQuote": true,
  "trailingComma": "all",
  "semi": true,
  "printWidth": 100,
  "plugins": ["@ianvs/prettier-plugin-sort-imports"]
}
```
```
# .prettierignore
dist
coverage
package-lock.json
bun.lock
pnpm-lock.yaml
```

## 19. Example Hook Configuration (`lefthook.yml`)

```yaml
pre-commit:
  parallel: true
  commands:
    format:
      glob: "*.{ts,tsx,js,jsx,json,css,md,yaml}"
      run: prettier --write {staged_files}
      stage_fixed: true
    lint:
      glob: "*.{ts,tsx}"
      run: eslint --max-warnings 0 {staged_files}

pre-push:
  parallel: true
  commands:
    typecheck:
      run: tsc --noEmit
    dead-code:
      run: knip
```

## 20. Monorepository Considerations

- **TypeScript:** project references + `tsc -b --noEmit`; shared base `tsconfig` package; cache `.tsbuildinfo`. [VF]
- **ESLint:** shared `eslint-config` package; `projectService: true` works with project references; enforce package boundaries with `eslint-plugin-import-x` or Nx `enforce-module-boundaries`. [VF]
- **Orchestration:** Turborepo 2.x `tasks` key (not the deprecated `pipeline`) with `typecheck`/`lint`/`test`, `dependsOn: ["^build"]` where needed; run affected on PRs, full on main. [VF]
- **Knip:** native workspace support; configure per-workspace entry points. A dep listed in a descendant workspace and used only there is flagged unused in the root — expected behavior. [VF]
- **Hooks:** Lefthook scopes commands per directory (`root:` / `glob:`), avoiding cross-package noise. [VF]
- **Package boundaries policy:** "no app-to-app imports; apps depend only on libs." [CR]

## 21. Adoption Plan

1. **Week 0 — formatting + syntax:** add Prettier (pinned) + `eslint-config-prettier`; ESLint flat config with `recommended` (no type-aware yet). Run `prettier --write .` once, commit the reformat as its own isolated commit.
2. **Week 1 — types:** enable `strict` + cheap flags (`noImplicitOverride`, `noFallthroughCasesInSwitch`, `noImplicitReturns`); wire `tsc --noEmit` into CI.
3. **Week 2 — type-aware lint:** add `projectService` + `recommendedTypeChecked`; capture a baseline with `eslint --suppress-all` so only new violations block; burn the baseline down over time (never permanent).
4. **Week 3 — dead code + supply chain:** add Knip (advisory first with `--no-exit-code`/`--max-issues`, then blocking), `npm/bun audit` + OSV-Scanner.
5. **Week 4 — stage strict flags:** turn on `noUncheckedIndexedAccess`, then `exactOptionalPropertyTypes`, fixing incrementally.
6. **Ongoing:** Renovate for updates; upgrade Prettier/tsc/ESLint deliberately in isolated PRs; re-evaluate TS 7 + typescript-eslint once 7.1 ships the new API.

## 22. Tools to Avoid or Replace

- **`depcheck`, `ts-prune`, `unimported`** — archived/superseded → **Knip**.
- **`eslint-plugin-node`** (original) — unmaintained → **`eslint-plugin-n`**.
- **`eslint-plugin-deprecation`** (gund) — archived; rule moved into **`@typescript-eslint/no-deprecated`**.
- **`.eslintrc` legacy config** — deprecated in ESLint 9 → flat config (`eslint.config.mjs`).
- **`eslint-plugin-prettier` for CI/agentic formatting** — mixes concerns and is slow → run `prettier --check` separately.
- **ESLint stylistic/formatting rules as the formatter** — let Prettier own formatting; use `@stylistic` only if you deliberately want non-Prettier style enforcement (not recommended alongside Prettier).
- **TSLint** — long dead → typescript-eslint.
- **`eslint-plugin-import`** — heavy (117 deps), no `exports`-field support, slower → **`eslint-plugin-import-x`**.
- **TypeScript 7 with typescript-eslint** — currently incompatible (peer dep `<6.1.0`; ESLint crashes in `typescript-estree`) → pin TS 6 for ESLint.
- **IDE-only inspections (WebStorm/VS Code extension-only rules)** — not reproducible in CI; excluded by design.

## 23. Evidence Gaps

- **typescript-eslint TS 7 support timeline** is unresolved — blocked on the TS 7.1 programmatic API; no committed date. Monitor typescript-eslint issue #12518 and the TS 7.1 release. [VF]
- **Oxlint as a full ESLint replacement:** type-aware linting is stable (59/61 rules), but plugin-ecosystem parity (React/Next/import/security) is not yet demonstrated; treat as a complement. [RI]
- **Biome type-aware coverage (~85%)** is a vendor/community claim; independent head-to-head vs typescript-eslint on real bug classes is thin. [VC]
- **Bun `bun audit fix`** does not exist yet (open issue); remediation ergonomics on Bun are worse than npm. [VF]
- **Speed multipliers** (8–12x tsc, 12–18x Oxlint) are largely vendor/maintainer benchmarks on their own hardware; independent numbers land lower (community testing on type-heavy codebases like Effect/drizzle-orm reported ~3.9–7.3x for tsc). [VC/CR]
- **Corepack's future for npm:** the `packageManager` field pinning story continues to evolve; verify current Corepack status at adoption time. [RI]

## 24. Source List

**Primary / official:** typescriptlang.org (TSConfig reference; 5.9/6.0 release notes); devblogs.microsoft.com (Announcing TypeScript 7.0, July 8 2026); typescript-eslint.io (typed linting, shared configs, `no-deprecated`, projectService; issues #12518, #12521, #10275, #10275); eslint.org (flat config / `defineConfig` extends; bulk suppressions v9.24.0 & `/docs/latest/use/suppressions`); react.dev + github.com/facebook/react (eslint-plugin-react-hooks); github.com/jsx-eslint/eslint-plugin-react; oxc.rs & voidzero.dev (Oxlint type-aware preview→alpha→stable, 2025-08 to 2026-07-22); biomejs.dev (Biome v2 / "Biotype"); prettier.io (3.6 & 3.7 release blogs); knip.dev + github.com/webpro-nl/knip (handling issues, CI guide, CLI reference); bun.com/docs & bun.com/blog/bun-v1.2.15 (`bun audit`); github.com/oven-sh/bun (issues #20238, #21813); docs.npmjs.com + github.blog (npm provenance & trusted publishing, 2025-07-31); security.googleblog.com + github.com/google/osv-scanner + osv.dev (OSV-Scanner V2); github.com/eslint-community/eslint-plugin-n; github.com/un-ts/eslint-plugin-import-x + e18e.dev; semgrep.dev; turborepo.dev (configuring tasks); github.com/eslint/eslint (issue #21070). **Supporting engineering reports:** solberg.is (fast type-aware linting), betterstack.com (Biome vs ESLint), appsecsanta.com (OSV-Scanner, Semgrep), digitalapplied.com & typescriptpro.com & morello.dev (TS 7 GA analysis), stevekinney.com & pkgpulse.com & andymadge.com (git hook frameworks), patricktree.me & totaltypescript.com (tsconfig defaults).