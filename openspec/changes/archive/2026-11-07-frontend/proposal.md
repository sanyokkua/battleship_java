# Proposal

Converted from the original workflow feature `the original 003-frontend artifacts` (Battleship Frontend and Local Integration). Branch `feature/003-frontend`, created 2026-10-18, status: implemented; T024 (coverage import into Sonar) and T036 (owner verifies GitHub triggers) still open.

## Why

The rewrite needs a playable client for the delivered backend (`002-backend`) and the API contract (`001-api-contract`). The feature also owns the root integration layer (there is no separate integration feature): one gate, local run commands, local Docker images and a GitHub Actions pipeline. The input was: plan the next feature from the legacy frontend seed documents (since removed; intent in `docs/intent.md`); do not mock the backend by reimplementing its logic; integration is part of this feature, so the frontend is tested against the real working API; issues found in the contract or backend may be fixed. Every optional seed capability (installable shell, drag placement, sound, haptics, emoji, QR) is in scope.

Authority: `contracts/openapi.yaml` is the source of truth for every wire behaviour; the delivered backend implements it. The Naval Command Center mockup (`docs/design/naval-command-center-mockup.html`) is the visual and interaction reference (R58-R61); its simulated rules, demo data, preview controls and the R60 elements are not reproduced.

## What Changes

- A static React app in `frontend/` that renders the server's caller-relative `GameSnapshot` and offers only the actions the snapshot allows: Home, Create game, Join invitation, Waiting room, Fleet placement, Gameplay, Results, Session unavailable, dialogs and toasts (US1-US6, R01-R41).
- Invitation privacy: secret stripped from the address before render, redeemed only on explicit Join, never rendered, stored or logged (US2, R08-R12).
- Live updates, recovery, leave/resign and expiry handling (US5, R24-R30).
- Installable offline-honest shell (US7, R42-R44).
- A root integration layer: `scripts/local-build.sh` (the single gate), `scripts/local-deploy.sh`, `build-docker-{frontend,backend,render}.sh`, run guide `docs/local-operation.md`, real-backend Playwright journeys (US8, R45-R56).
- Four contract and backend fixes found while building (R57): `browser-game-limit` 409, forked test JVM linger, Actuator off the public port, invitation fields excluded from `GameSnapshot.version`. Later amended: `placementBlockedCells` in the placement snapshot (R62).
- Extension: local SonarQube scans and repair, local Docker images, LAN HTTPS flow (US9, US10, R63-R69).
- Extension: GitHub Actions pipeline - branch build, PR gate, stable tag release to GHCR (R70-R78, S11; owner decision 2026-11-07, constitution 2.1.0).
- Script consolidation of 2026-11-07: `scripts/` now holds only `local-build.sh`, `local-deploy.sh` and `build-docker-{frontend,backend,render}.sh`; `stack.mjs` is replaced by `frontend/e2e/fixtures/stack.ts`. Requirements about the HTTPS combined image and the stack CLI are superseded by `local-deploy.sh`.

### Provenance

Requirements are `[user]` unless tagged `[agent]` (proposed by the agent and approved by the owner). Capability specs carry the original ids (R01-R78, S1-S11, US1-US10) in requirement text.

### Agent-proposed requirements

| Id | Why | Cost | Cheaper alternative | Recommendation |
|---|---|---|---|---|
| R07 | Server admits one live game per browser; a closed tab otherwise locks the player out until expiry | One stored public id, one Home action, one journey step; one contract problem code + backend mapping + its proof for the unknown-game case | Explain the busy answer only (player cannot tell it from overload) | Now |
| R35 | Contract has no ruleset or ship name field | Translation keys per known id | Show raw ids | Now |
| R44 | An unannounced service-worker update can swap code mid-game | One prompt; reused from prior art | Update silently on next load | Now |
| R56 | "Real backend only" cannot prove a hand-written schema; it would duplicate the contract | None (removes work) | Hand-written runtime schemas | Now |

Pipeline extension, agent-proposed block R74-R78. Aggregate cost: no extra files beyond the two workflows and `docs/ci.md`; roughly 25 added workflow lines and one doc page; no dependency.

| Id | Why | Cost | Cheaper alternative | Recommendation |
|---|---|---|---|---|
| R74 | A compromised or buggy step cannot write packages or contents outside the release job | A few `permissions` lines | Repository default token permissions | Now |
| R75 | Mutable action tags are a supply-chain risk in a job that holds `packages: write` | SHA plus comment per action | Version tags | Now |
| R76 | A tag on a feature commit would publish an unreviewed image | One `git merge-base --is-ancestor` step | Convention only | Now |
| R77 | Prevents hung runners, wasted minutes and a half-pushed release | A few lines | Defaults | Now |
| R78 | The release steps and repository settings cannot be inferred from the workflow files | One page | Comments in the workflow | Now |

### Out of scope

Everything the constitution's launch baseline excludes (database, accounts, chat, spectators, rankings, payments, analytics, ads, offline play, command queues, scaling, deployment, hosting). Local-only Docker build/run scripts (R65-R68) and the GitHub Actions pipeline (R70-R78) are inside this feature; neither establishes deployment or hosting, and deploying to Render stays manual. Also out: automated pixel comparison, a cross-browser/device matrix, load testing, and the capabilities the contract defers - claiming a win against a vanished opponent, rematch, a list of my games, recovering a join whose answer was lost, conditional reads and salvo rules.

Deferred, not built (pipeline): provenance and SBOM attestations, pre-release tags (`-rc`, `-beta`), a Playwright browser cache, automatic retries, re-running the gate on release (branch protection with a required check is the recommended mitigation), Dependabot.

### Assumptions

1. The pinned toolchain and exact dependency versions are chosen in the design from the seed and from working prior art in the owner's projects; they are not part of the specification.
2. Browser journeys run in one desktop engine (Chromium). The backend's cookies are always Secure and `__Host-` prefixed; over plain `http://localhost` this relies on the browser treating localhost as a secure context.
3. The backend's default public base address (`http://localhost:5173`) matches the development frontend origin; the production-style run sets it to its own origin through configuration.
4. The two documented rulesets are what the server currently advertises; the UI renders whatever the server lists.
5. A backend issue noticed while closing 002 (the forked test JVM lingers 30 s after the backend tests finish) is fixed under R57 when the root gate makes it visible.

## Capabilities

### New Capabilities

- `frontend-server-state`: server authority, anti-forgery, version gate, decode, problem mapping, contract and backend fixes (R01-R06, R19, R22, R23, R56, R57).
- `frontend-invitation`: create, share, join, invitation privacy and presence (US1, US2, R07-R16).
- `frontend-placement`: fleet placement, drag and touch, blocked-cell hint (US3, R17, R18, R62).
- `frontend-battle`: fire, turns, live feedback, mobile handoff, results (US4, R20, R21, R31, R32, R36).
- `frontend-recovery`: live stream, recovery, leave, resign, expiry (US5, R24-R30).
- `frontend-shell`: surfaces, settings, i18n, visual design (US6 settings, R33-R35, R37, R38, R58-R60).
- `frontend-accessibility`: layouts, inclusive interaction, dialogs (US6, R39-R41, R61, S4).
- `frontend-pwa`: installable shell and offline honesty (US7, R42-R44, S6).
- `local-build-gate`: root commands, orchestration, proof rules (US8, R45-R55, S7).
- `frontend-quality-images`: SonarQube repair, local Docker images, LAN HTTPS (US9, US10, R63-R69, S9, S10).
- `ci-release`: GitHub Actions pipeline (R70-R78, S11).

### Modified Capabilities

None; this is a greenfield rewrite.

## Impact

- New: `frontend/`, `scripts/`, `config/local.env.example`, `docs/local-operation.md`, `docs/ci.md`, root `Dockerfile`, `.github/workflows/{ci,release}.yml`.
- Amended: `contracts/openapi.yaml` (+ `contracts/README.md`), backend (`SessionRegistry`, use cases, `ProblemAdvice`, `app/pom.xml`, snapshot projection), `the original 002-backend artifacts` wording, constitution 2.1.0, `AGENTS.md`.
- Forecast: 36 tasks (T001-T036); Standard assurance for the app and Sonar/Docker extension, Lean for the pipeline extension. Approximately 120 files for the original 20 tasks, about 71 changed paths for the Sonar/Docker extension, about 7 product files plus 10 governance/doc paths for the pipeline.
- Risks: see design.md.
