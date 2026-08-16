---
description: "Dependency-ordered implementation tasks for QR Session Sharing"
---

# Tasks: QR Session Sharing

**Input**: Design documents from `specs/001-qr-session-sharing/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/ui.md`, `quickstart.md`, `checklists/requirements.md`, and
`checklists/quality.md`.

## Task Rules

- Every task is one independently reviewable unit, one branch, and one commit.
- Branches are based on the current `feature/qr-session-sharing` parent and use
  `feature/qr-session-sharing--<task-slug>`; do not merge directly into `master`.
- Every task names its exact implementation/test paths and its proving command.
- A task checkbox is not evidence of completion. Record the command result and
  acceptance-criterion mapping in the feature evidence before checking it.
- Preserve the existing modified and untracked worktree files. Do not reset,
  clean, force-push, or use `--no-verify`.
- This feature is frontend-only. Do not add Java, REST, SSE, DTO, persistence,
  authentication, adapter, mock-adapter, or OpenAPI behavior.
- Use the repository-pinned frontend runtime installed by Maven for authoritative
  results; host Node/npm results are exploratory only.

## Task Branch Map

Create each branch from the feature parent state required by its dependencies,
make only that task's changes, and create exactly one commit on it:

| Tasks | Branch suffix |
|---|---|
| T001 | `baseline` |
| T002 | `qr-dependency` |
| T003 | `canonical-url` |
| T004 | `base-path-wiring` |
| T005 | `qr-widget-tests` |
| T006 | `wait-screen-tests` |
| T007 | `qr-widget` |
| T008 | `wait-screen-integration` |
| T009 | `url-join-conformance` |
| T010 | `mock-browser-join` |
| T011 | `sheet-accessibility-tests` |
| T012 | `qr-responsive-style` |
| T013 | `mock-browser-accessibility` |
| T014 | `focused-evidence` |
| T015 | `full-gate-closeout` |

For example, T007 uses `feature/qr-session-sharing--qr-widget`. The final
integration of these task commits into the feature branch and then `master` is
outside this task-generation unit.

## Path Conventions

| Concern | Paths |
|---|---|
| Frontend configuration/helper | `frontend/src/config/`, `frontend/vite.config.ts`, `frontend/src/index.tsx` |
| Waiting-room screen | `frontend/src/screens/WaitScreen.tsx`, `frontend/src/screens/WaitScreen.css` |
| QR widget | `frontend/src/widgets/sharing/QrCodeSheet.tsx`, `frontend/src/widgets/sharing/QrCodeSheet.css` |
| Shared overlay | `frontend/src/design/components/Sheet/Sheet.tsx`, `frontend/src/design/components/Sheet/Sheet.css` |
| Frontend unit/component tests | `frontend/src/**/*.test.ts`, `frontend/src/**/*.test.tsx` |
| Locale resources | `frontend/src/i18n/en/screens.json`, `frontend/src/i18n/uk/screens.json` |
| Mock-browser tests | `frontend/e2e/qr-session-sharing.spec.ts`, `frontend/e2e/support/mockBackdoor.ts` only when a helper is genuinely required |
| Feature evidence | `specs/001-qr-session-sharing/baseline.md`, `specs/001-qr-session-sharing/verification.md` |
| Verification gate | `scripts/verify.sh`, `docs/openapi.json` |

## Requirement and Checklist Coverage

The task references below are the required implementation/evidence owners. The
checklist files remain reviewer-owned gates; implementation must not mark or
rewrite their review markers merely to make a task pass.

| Requirement set | Owning tasks |
|---|---|
| FR-001, FR-002, FR-003, FR-004 | T005-T008, T010 |
| FR-005, FR-006 | T003-T004, T008-T010 |
| FR-007, FR-008, FR-009 | T005-T006, T011-T013 |
| FR-010 | T008, T011-T013 |
| FR-011 | T007-T010, T014 |
| SC-001 | T005-T008, T010 |
| SC-002 | T003-T004, T009-T010 |
| SC-003 | T009-T010 |
| SC-004 | T005-T006, T011-T013 |
| SC-005 | T008, T011-T013 |
| SC-006 | T007-T010, T014 |
| SC-007 | T001, T014-T015 |
| `checklists/requirements.md` CHK001-CHK007 | T005-T008 |
| CHK008-CHK013 | T003, T007, T011 |
| CHK014-CHK018 | T002-T004, T006, T008, T014 |
| CHK019-CHK023 | T005-T010, T014-T015 |
| CHK024-CHK028 | T005-T013 |
| CHK029-CHK032 | T003, T005-T006, T009-T011 |
| CHK033-CHK036 | T005-T006, T011-T013 |
| CHK037-CHK040 | T001-T004, T009, T014-T015 |
| `checklists/quality.md` requirements-quality review | T001, T014-T015; preserve reviewer markers and record any finding in `verification.md` |

## Phase 1: Baseline and Setup

**Purpose**: Establish the implementation starting point and preserve the
approved frontend-only contract before any application code changes.

- [ ] T001 Create `specs/001-qr-session-sharing/baseline.md` on branch `feature/qr-session-sharing--baseline` by recording `git status --short --branch`, the current feature artifacts, the existing frontend-focused test/build result, and the result or exact capability failure of `scripts/verify.sh`; map `checklists/requirements.md` CHK001-CHK040 and `checklists/quality.md` to the owning tasks in this file without changing checklist markers, and record that `src/main/java` and `docs/openapi.json` are expected to remain unchanged (FR-011, SC-007, CHK014, CHK017, CHK023, CHK040).

**Checkpoint**: The baseline identifies the existing dirty worktree and any
pre-existing failures separately from QR work. The checklist files are still
reviewer-owned and unmodified.

## Phase 2: Foundational Frontend Configuration

**Purpose**: Establish the only new dependency and the single normalized
application base-path source before user-story implementation begins.

- [ ] T002 [P] Add `qrcode` 1.5.x as a runtime dependency and matching `@types/qrcode` 1.5.x as a development dependency in `frontend/package.json`, update `frontend/package-lock.json`, and verify the resolved package metadata with `npm --prefix frontend install` and `npm --prefix frontend ls qrcode @types/qrcode`; use `/Users/ok/Development/GitHub/dev.tools/src/pages/qr/QrPage.tsx` as the local reference for the browser-side `import('qrcode')`/`toCanvas` boundary, while keeping export, download, server-side generation, and external-service behavior out of scope (FR-003, FR-004, CHK014, CHK039).

- [ ] T003 [P] Create `frontend/src/config/appBasePath.ts` and `frontend/src/config/appBasePath.test.ts` with one pure normalization function and one canonical absolute join-URL builder; normalize empty or `/` to `/`, add exactly one leading slash, remove trailing slashes, preserve internal segments, reject query/hash/current-route suffixes, use the current or injected protocol/host/IP/port, append `/join`, and set `id` through the URL API so encoded session IDs are never unsafe string concatenations; prove the matrix with hostname, IP, non-default port, root, empty, duplicated-leading/trailing slash, internal segment, path-only, query/hash, and encoded-ID cases using `npm --prefix frontend run test -- src/config/appBasePath.test.ts` (FR-005, FR-006, SC-002, CHK004-CHK005, CHK009-CHK011, CHK020, CHK029, CHK031, CHK037-CHK038).

- [ ] T004 Wire the normalized value from `frontend/src/config/appBasePath.ts` into `frontend/vite.config.ts` as the Vite asset `base` and into `frontend/src/index.tsx` as `BrowserRouter basename`, using `loadEnv` or an equivalent module-safe input path without duplicating normalization rules; add/extend configuration assertions in `frontend/src/config/appBasePath.test.ts` or the relevant existing routing test, then run `npm --prefix frontend run build` and verify root deployment remains `/` while a configured subpath appears exactly once in assets, router resolution, and the join URL (FR-005, FR-006, SC-002, CHK010, CHK014-CHK016, CHK031, CHK038).

**Checkpoint**: `qrcode` is available only to the frontend, the base-path
contract is deterministic and tested, and Vite/router/join-URL consumers share
one normalized setting. No backend or API artifact changes are introduced.

## Phase 3: User Story 1 - Show a Session QR Code on Demand (Priority: P1)

**Goal**: A valid waiting-room player sees the new action, opens the existing
Sheet on demand, and receives one localized ready QR representation without QR
work during the initial render.

**Independent Test**: With a valid waiting-room fixture whose guard admits a
non-empty session/player and whose initial loading has resolved, the focused
tests show no QR UI before selection, open the Sheet after `Show QR code`, and
show a localized instruction plus one completed canvas after `toCanvas` resolves.
Missing context, non-waiting state, and unresolved loading withhold the action.

### Tests for User Story 1

- [ ] T005 [P] [US1] Add contract-first Vitest/Testing Library coverage in `frontend/src/widgets/sharing/QrCodeSheet.test.tsx` for `QrCodeSheet({open, url, onClose})`: prove no dynamic QR import and no QR presentation while closed, loading before completion, `qrcode.toCanvas` receives the exact URL plus width `300`, margin `2`, and error-correction `M`, one accessible canvas and scan instruction only after success, localized error with no ready/blank canvas after import or render rejection, and ignored deferred completion after close/unmount/reopen; use deferred/rejected mocks for both `import('qrcode')` and `toCanvas`, and run `npm --prefix frontend run test -- src/widgets/sharing/QrCodeSheet.test.tsx` (FR-002-FR-004, FR-008-FR-009, SC-001, SC-004, CHK002-CHK007, CHK026-CHK028, CHK032-CHK035).

- [ ] T006 [P] [US1] Extend `frontend/src/screens/WaitScreen.test.tsx` with valid and withheld-action fixtures: resolved `INITIALIZED`/`WAITING_FOR_PLAYERS` sessions with session ID and player identity, missing session/player, non-waiting stage, and initial loading; assert the initial screen has no dialog/canvas/loading/error, `Show QR code` appears only for valid fixtures, the action opens the presentation, and existing Copy ID, Copy link, refresh, polling, and navigation assertions remain green (FR-001, FR-002, FR-011, SC-001, SC-006, CHK001-CHK002, CHK008, CHK017, CHK019, CHK024, CHK030).

### Implementation for User Story 1

- [ ] T007 [US1] Implement `frontend/src/widgets/sharing/QrCodeSheet.tsx` and `frontend/src/widgets/sharing/QrCodeSheet.css` using the existing `frontend/src/design/components/Sheet/Sheet.tsx` as the sole overlay boundary; dynamically import `qrcode` only after `open`, call `toCanvas` with the approved `300`/`2`/`M` options, model idle/loading/ready/error state, keep the canvas hidden until a successful current render, ignore late promise results after cleanup, expose localized title/scan/loading/error/Close content with dialog/status/alert/canvas semantics, and include no copy/download/export/customization/navigation controls (FR-002-FR-004, FR-007-FR-009, SC-001, SC-004, CHK002-CHK007, CHK012-CHK013, CHK016, CHK026-CHK028, CHK032-CHK035).

- [ ] T008 [US1] Update `frontend/src/screens/WaitScreen.tsx`, `frontend/src/screens/WaitScreen.css`, `frontend/src/i18n/en/screens.json`, `frontend/src/i18n/uk/screens.json`, and the tests in `frontend/src/screens/WaitScreen.test.tsx` to own `qrOpen`, derive one canonical URL from the shared helper, render `Show QR code` beside unchanged Copy ID/Copy link controls only for the existing valid waiting context, open the QR Sheet before generation completes, and add exact English/Ukrainian keys `screens.wait.showQr`, `screens.qr.title`, `screens.qr.scan`, `screens.qr.loading`, `screens.qr.description`, `screens.qr.error`, and `screens.qr.close`; prove `npm --prefix frontend run test -- src/screens/WaitScreen.test.tsx src/i18n/keyParity.test.ts` and preserve refresh, polling, and navigation behavior (FR-001-FR-006, FR-010-FR-011, SC-001, SC-005-SC-006, CHK001-CHK008, CHK017-CHK018, CHK024, CHK028, CHK030, CHK037).

**Checkpoint**: User Story 1 works independently in focused tests: QR code
generation is lazy, the valid-session boundary is preserved, and existing
sharing behavior is unchanged when QR is unused.

## Phase 4: User Story 2 - Scan the Same Public Destination as Copy Link (Priority: P1)

**Goal**: Copy link and QR use the identical absolute join URL across root,
LAN, hostname, port, and configured deployment-path shapes, and the existing
join screen receives the same session ID.

**Independent Test**: The helper and waiting-room tests compare the exact URL
passed to `copy-to-clipboard` and `qrcode.toCanvas`; the mock-browser journey
opens the canonical `/join?id=...` destination and observes the existing
JoinGameScreen pre-filled with that ID.

- [ ] T009 [P] [US2] Add cross-consumer acceptance coverage in `frontend/src/config/appBasePath.test.ts`, `frontend/src/screens/WaitScreen.test.tsx`, and `frontend/src/screens/JoinGameScreen.test.tsx` for hostname/IP, protocol, non-default port, root and configured base path, encoded session ID, exactly-one base-path occurrence, byte-for-byte Copy-link/QR equality, and existing `?id=` prefill without changing the join route or adapter contract; run `npm --prefix frontend run test -- src/config/appBasePath.test.ts src/screens/WaitScreen.test.tsx src/screens/JoinGameScreen.test.tsx` and treat any changed `docs/openapi.json` as an unexpected artifact requiring review (FR-005-FR-006, SC-002-SC-003, SC-006-SC-007, CHK004-CHK005, CHK009-CHK011, CHK015, CHK020-CHK021, CHK029, CHK031, CHK037-CHK040).

- [ ] T010 [US2] Add `frontend/e2e/qr-session-sharing.spec.ts` for the mock-adapter journey: create a session through the real UI, reach `/game/wait`, assert no QR presentation before selection, select `Show QR code`, observe localized loading then one ready canvas/instruction, and open the canonical join destination with the original encoded ID so the existing `JoinGameScreen` pre-fills it; use `frontend/e2e/support/mockBackdoor.ts` only for session setup/navigation support and never for the primary share controls, then run `npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts` (FR-001-FR-006, FR-011, SC-001-SC-003, SC-006, CHK001-CHK005, CHK019-CHK021, CHK024-CHK025, CHK037).

**Checkpoint**: User Story 2 proves one canonical public URL. The QR payload
does not invent a route or security model, and the existing join query handling
remains authoritative.

## Phase 5: User Story 3 - Use and Dismiss the Presentation Accessibly (Priority: P2)

**Goal**: The QR presentation inherits the existing Sheet dialog, focus,
dismissal, desktop/mobile, and localization contract while exposing reliable
loading and failure behavior.

**Independent Test**: Focused widget tests and the browser journey verify
Close, Escape, backdrop dismissal, focus restoration, status/error semantics,
stale-result protection, English/Ukrainian text, and a 320px viewport with no
horizontal overflow.

- [ ] T011 [P] [US3] Extend `frontend/src/widgets/sharing/QrCodeSheet.test.tsx` and, only for regression assertions of the shared contract, `frontend/src/design/components/Sheet/Sheet.test.tsx` to cover dialog/`aria-modal`/title wiring, role `status` loading, role `alert` error, accessible canvas description, usable localized Close in every open state, focus entering/restoring through the existing Sheet, Escape and backdrop dismissal, no stale completion after close/unmount, fresh state on reopen, and no export/copy controls; run `npm --prefix frontend run test -- src/widgets/sharing/QrCodeSheet.test.tsx src/design/components/Sheet/Sheet.test.tsx` without reimplementing or weakening `Sheet` behavior (FR-003, FR-007-FR-009, FR-010, SC-004-SC-005, CHK003, CHK006-CHK007, CHK013, CHK016, CHK022, CHK026-CHK028, CHK032-CHK036).

- [ ] T012 [US3] Complete the responsive presentation styling in `frontend/src/widgets/sharing/QrCodeSheet.css` and any narrowly scoped `frontend/src/screens/WaitScreen.css` changes so the nominal canvas is 300 CSS pixels with proportional `max-width: 100%` behavior inside the existing Sheet, fits at a 320px viewport without horizontal overflow, and retains the existing centered desktop and mobile bottom-sheet behavior; verify with computed-size/overflow assertions in the widget test and `npm --prefix frontend run lint` (FR-007-FR-008, SC-004, CHK012-CHK013, CHK028, CHK033-CHK035).

- [ ] T013 [US3] Extend `frontend/e2e/qr-session-sharing.spec.ts` to exercise every dismissal and recovery path through real controls: Close button, Escape, backdrop, focus restoration to `Show QR code`, close while generation is pending, failed dynamic import/render with localized error and usable Close, reopen with fresh current state, narrow 320px bottom-sheet layout without horizontal overflow, and English/Ukrainian action/title/status/Close text; run the dedicated mock-browser spec across the configured projects and record any browser capability limitation separately from a product failure (FR-007-FR-010, SC-004-SC-005, CHK006-CHK007, CHK013, CHK022, CHK026-CHK028, CHK032-CHK036).

**Checkpoint**: User Story 3 is independently accessible and responsive. No
new overlay implementation, QR export control, or QR-specific privacy rule is
introduced.

## Phase 6: Integration, Artifacts, Checklists, and Verification

**Purpose**: Prove the complete feature, keep generated artifacts and
requirements-quality records honest, and leave the next integration step clear.

- [ ] T014 [P] Run the focused feature evidence from `specs/001-qr-session-sharing/quickstart.md`: `npm --prefix frontend run test -- src/config/appBasePath.test.ts src/widgets/sharing/QrCodeSheet.test.tsx src/screens/WaitScreen.test.tsx src/i18n/keyParity.test.ts`, `npm --prefix frontend run lint`, and the dedicated `npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts`; write acceptance-criterion-to-command/result mappings and remaining limitations to `specs/001-qr-session-sharing/verification.md`, including explicit results for FR-001-FR-011, SC-001-SC-006, and the relevant CHK001-CHK040 entries (FR-001-FR-011, SC-001-SC-006, CHK001-CHK040).

- [ ] T015 Run `scripts/verify.sh` from the packaged-JAR-capable implementation state, inspect `git diff -- src/main/java docs/openapi.json frontend/package.json frontend/package-lock.json`, confirm no Java/API/OpenAPI change is present or explain and resolve any unexpected generated diff, run `python3 scripts/sync-agent-files.py --check` only if agent/configuration files were touched, and update `specs/001-qr-session-sharing/verification.md` plus the reviewer-owned `checklists/requirements.md` and `checklists/quality.md` only with evidence/findings permitted by their lifecycle (never mark implementation complete by checkbox alone); record the exact gate result, environment limitations such as localhost binding separately, and the next task for user integration (SC-007, CHK014, CHK017, CHK023, CHK039-CHK040).

**Closeout condition**: The feature is ready for user integration only after
the specification, plan, task branches/commits, focused tests, mock-browser
journey, full gate, generated-artifact review, checklist evidence, and
remaining limitations are all recorded. Final integration into `master` is the
user's responsibility.

## Dependencies and Execution Order

### Phase Dependencies

- T001 precedes all implementation and establishes the baseline/checklist map.
- T002 and T003 can run in parallel after T001; T004 depends on T003.
- T005 and T006 depend on the required dependency/configuration inputs and can
  run in parallel because they change separate test files; T007 depends on T005
  and existing `Sheet`; T008 depends on T004, T006, and T007.
- T009 and T010 depend on the integrated screen/helper behavior in T008.
- T011 can run after T006/T008 and in parallel with T009; T012 depends on the
  widget contract and T013 depends on T010-T012.
- T014 depends on all story checkpoints; T015 depends on T014 and is the final
  gate/closeout task.

### Story Completion Order

```text
T001
 ├── T002 ── T005 ── T006 ──┐
 └── T003 ── T004 ───────────┼── T007 ── T008 ──┬── T009 ──┐
                             │                  └── T010 ──┼── T014 ── T015
                             └── T011 ── T012 ── T013 ─────┘
```

US1 is the first independently testable product slice after the shared
configuration foundation. US2 is a same-priority P1 contract and must be
completed before release even though its URL helper is foundational. US3 then
closes accessibility, failure, responsive, and localization coverage.

### Parallel Execution Examples

```text
# After T001
T002 (dependency metadata) || T003 (base-path helper and tests)

# After T002/T003
T005 (QR widget tests) || T006 (WaitScreen tests)

# After T008
T009 (URL/join conformance tests) || T011 (Sheet/accessibility tests)

# After all story checkpoints
T014 (focused evidence) -> T015 (full gate and closeout)
```

Every parallel pair uses distinct files and must still obey the branch/commit
rule. If a task discovers a needed shared-file change, stop parallel work,
rebase that task onto the latest feature-parent state, and keep ownership
explicit rather than creating conflicting commits.

## Command Reference

Use `frontend/node`/the Maven-installed runtime for authoritative frontend
results when the full gate provides it.

```bash
npm --prefix frontend install
npm --prefix frontend run test -- <focused paths>
npm --prefix frontend run lint
npm --prefix frontend run build
npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts
scripts/verify.sh
python3 scripts/sync-agent-files.py --check
```

If Playwright fails before a test executes with restricted localhost binding,
record it as an environment capability limitation and retain the unchanged
product command/result separately. Do not convert an unrun browser suite into
a passing claim.

## Implementation Strategy

### MVP First

The smallest user-visible MVP is User Story 1 after the shared T001-T004
foundation: T005-T008 deliver a valid waiting-room `Show QR code` action,
lazy browser canvas generation, localized ready/loading/error states, and
regression coverage for existing sharing actions. Because User Story 2 is also
P1, a releasable feature must immediately include T009-T010; User Story 3 and
the final gate remain required for the complete specification.

### Incremental Delivery

1. Capture the baseline and preserve the checklist/repository boundaries.
2. Land dependency and shared base-path configuration in separate task
   branches/commits.
3. Land the QR widget contract and implementation, then the waiting-room
   integration, with focused evidence at each checkpoint.
4. Prove exact URL equality and existing join-screen prefill before treating
   the QR as usable across deployments.
5. Close accessibility, failure, mobile, bilingual, mock-browser, generated
   artifact, and full-gate evidence before user integration.
