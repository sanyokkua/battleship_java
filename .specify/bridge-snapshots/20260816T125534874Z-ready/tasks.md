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
- Use `frontend/node/npm` (the repository-pinned frontend runtime installed by
  Maven) for authoritative results; host Node/npm results are exploratory only.
- `checklists/requirements.md` and `checklists/quality.md` are reviewer-owned
  quality records. Tasks may cite them and record their state in
  `verification.md`, but must not rewrite their markers.

## Task Branch Map

Create each branch from the feature-parent state required by its dependencies,
make only that task's changes, and create exactly one commit on it:

| Task | Branch suffix |
|---|---|
| T001 | `baseline` |
| T002 | `qr-dependency` |
| T003 | `canonical-url` |
| T004 | `base-path-wiring` |
| T005 | `qr-widget` |
| T006 | `wait-screen-integration` |
| T007 | `url-join-conformance` |
| T008 | `mock-browser-join` |
| T009 | `sheet-accessibility-tests` |
| T010 | `qr-responsive-style` |
| T011 | `mock-browser-accessibility` |
| T012 | `focused-evidence` |
| T013 | `full-gate-closeout` |

The final integration of these task commits into the feature branch and then
`master` is outside this task-generation unit.

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

The task references below are derived from the explicit references on each task.
The checklist files remain reviewer-owned gates; implementation must not mark
or rewrite their review markers merely to make a task pass.

| Requirement | Owning tasks |
|---|---|
| FR-001 | T006, T008, T012 |
| FR-002 | T005, T006, T008, T012 |
| FR-003 | T002, T005, T009, T012 |
| FR-004 | T002, T005, T012 |
| FR-005 | T003, T004, T006, T007, T008, T012 |
| FR-006 | T003, T004, T006, T007, T008, T012 |
| FR-007 | T005, T009, T010, T011, T012 |
| FR-008 | T005, T009, T010, T011, T012 |
| FR-009 | T005, T009, T011, T012 |
| FR-010 | T005, T009, T011, T012 |
| FR-011 | T001, T006, T008, T012 |
| SC-001 | T005, T006, T008, T012 |
| SC-002 | T003, T004, T007, T008, T012 |
| SC-003 | T007, T008, T012 |
| SC-004 | T005, T009, T010, T011, T012 |
| SC-005 | T005, T009, T011, T012 |
| SC-006 | T006, T007, T008, T012 |
| SC-007 | T001, T007, T013 |
| `checklists/requirements.md` content quality, completeness, and readiness | T001, T012, T013; cite only, do not rewrite markers |
| `checklists/quality.md` CHK001-CHK040 | T001-T013 as referenced below; cite only, do not rewrite markers |

| Checklist range | Owning tasks |
|---|---|
| CHK001-CHK007 | T005, T006, T009, T011 |
| CHK008-CHK013 | T003, T005, T009, T010, T011 |
| CHK014-CHK018 | T001, T002, T004, T006, T012, T013 |
| CHK019-CHK023 | T005, T006, T007, T008, T012, T013 |
| CHK024-CHK028 | T005, T006, T008, T009, T011 |
| CHK029-CHK032 | T003, T005, T007, T009, T012 |
| CHK033-CHK036 | T005, T009, T010, T011 |
| CHK037-CHK040 | T001, T003, T004, T007, T012, T013 |

## Phase 1: Baseline and Setup

**Purpose**: Establish the implementation starting point and preserve the
approved frontend-only contract before application changes.

- [ ] T001 Create `specs/001-qr-session-sharing/baseline.md` on branch `feature/qr-session-sharing--baseline` by recording `git status --short --branch`, staged and unstaged diffs for the feature paths, the existing frontend-focused test/build result using `frontend/node/npm`, and the result or exact capability failure of `scripts/verify.sh`; map all 16 checks in `checklists/requirements.md` and CHK001-CHK040 in `checklists/quality.md` to tasks without changing checklist markers, and record that `src/main/java` and `docs/openapi.json` are expected to remain unchanged (FR-011, SC-007, CHK014, CHK017, CHK023, CHK040). Prove with `git status --short --branch`, `git diff HEAD --stat`, and the recorded focused/full verification commands.

**Checkpoint**: The baseline identifies the current dirty worktree, including
staged files, and separates pre-existing failures from QR work. The checklist
files remain reviewer-owned and unmodified.

## Phase 2: Foundational Frontend Configuration

**Purpose**: Establish the only new dependency and the single normalized
application base-path source before user-story implementation begins.

- [ ] T002 [P] Add `qrcode` 1.5.x as a runtime dependency and matching `@types/qrcode` 1.5.x as a development dependency in `frontend/package.json`, update `frontend/package-lock.json`, and verify the resolved package metadata with `frontend/node/npm --prefix frontend install` and `frontend/node/npm --prefix frontend ls qrcode @types/qrcode`; keep export, download, server-side generation, and external-service behavior out of scope (FR-003, FR-004, CHK014, CHK039).

- [ ] T003 [P] Create `frontend/src/config/appBasePath.ts` and `frontend/src/config/appBasePath.test.ts` with the exact environment-agnostic interfaces `normalizeAppBasePath(raw: string | undefined): string` and `buildCanonicalJoinUrl(origin: string, normalizedBasePath: string, sessionId: string): string`; normalize empty or `/` to logical `/`, otherwise add exactly one leading slash, remove trailing slashes, preserve internal segments and path-only values such as `/battleship/v1`, reject query/hash values and current-route suffixes using the existing route set (`/join`, `/game/wait`, `/game/preparation`, `/game/gameplay`, `/game/results`) as explicit invalid fixtures, reject empty/whitespace session IDs without emitting a URL, and use the URL API with `searchParams.set('id', sessionId)` for encoded IDs; prove hostname, IP, protocol, non-default port, root, empty, duplicated-leading/trailing slash, internal segment, path-only, invalid query/hash/current-route, empty/whitespace-ID, and encoded-ID cases with `frontend/node/npm --prefix frontend run test -- src/config/appBasePath.test.ts` (FR-005, FR-006, SC-002, CHK004-CHK005, CHK009-CHK011, CHK020, CHK029, CHK031, CHK037-CHK038).

- [ ] T004 Wire the one logical value from `frontend/src/config/appBasePath.ts` into `frontend/vite.config.ts` and `frontend/src/index.tsx` without duplicating normalization rules: load `VITE_APP_BASE_PATH` with Vite `loadEnv`, use `/` for the root Vite asset base or `${normalizedBasePath}/` for a non-root asset base, and pass the slashless logical value to `BrowserRouter basename`; use the logical value unchanged in canonical join-URL construction. Add configuration assertions for root and `/battleship/`, run `frontend/node/npm --prefix frontend run build` and `VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run build`, inspect `frontend/build/index.html` and emitted asset references for `/` versus `/battleship/`, and verify an invalid query/hash/current-route setting fails deterministically before a share URL is accepted (FR-005, FR-006, SC-002, CHK010, CHK014-CHK016, CHK031, CHK038). Prove with the two build commands, the focused configuration test, the expected non-zero invalid-configuration command result, and assertions that the base path occurs exactly once.

**Checkpoint**: `qrcode` is available only to the frontend, the base-path
contract is deterministic and tested, valid root/subpath builds share one
normalized value across assets/router/join URLs, and invalid configuration is
rejected. No backend or API artifact changes are introduced.

## Phase 3: User Story 1 - Show a Session QR Code on Demand (Priority: P1)

**Goal**: A valid waiting-room player sees the new action, opens the existing
Sheet on demand, and receives one localized ready QR representation without QR
work during the initial render.

**Independent Test**: `QrCodeSheet.test.tsx` and `WaitScreen.test.tsx` prove no
QR import or UI before selection, valid-session-only action visibility, loading
before readiness, one accessible canvas after `toCanvas` fulfillment, localized
failure behavior, stale-result protection, and unchanged existing sharing.

### Implementation and Tests for User Story 1

- [ ] T005 [US1] Implement `frontend/src/widgets/sharing/QrCodeSheet.tsx` and `frontend/src/widgets/sharing/QrCodeSheet.css` with the existing `Sheet` as the sole overlay boundary, dynamic `qrcode` import only after `open`, `toCanvas` options `width: 300`, `margin: 2`, error correction `M`, idle/loading/ready/error state, hidden canvas until fulfillment, cleanup protection for late results, localized dialog/status/alert/Close content, ready canvas `role="img"` with `screens.qr.description` as its localized `aria-label`, and no copy/download/export/customization/navigation controls; add the complete English/Ukrainian keys in `frontend/src/i18n/en/screens.json` and `frontend/src/i18n/uk/screens.json` (`screens.wait.showQr`, `screens.qr.title`, `screens.qr.scan`, `screens.qr.loading`, `screens.qr.description`, `screens.qr.error`, `screens.qr.close`) and co-located contract tests in `frontend/src/widgets/sharing/QrCodeSheet.test.tsx` covering closed/lazy behavior, deferred module/render success, rejection, close/unmount/reopen, canvas accessibility, and both locale resources. The existing `Sheet` remains unchanged; its localized title labels the dialog and the canvas label supplies the QR description. Prove with `frontend/node/npm --prefix frontend run test -- src/widgets/sharing/QrCodeSheet.test.tsx src/i18n/keyParity.test.ts` (FR-002-FR-004, FR-007-FR-010, SC-001, SC-004, SC-005, CHK002-CHK007, CHK012-CHK013, CHK016, CHK026-CHK028, CHK032-CHK036).

- [ ] T006 [US1] Update `frontend/src/screens/WaitScreen.tsx`, `frontend/src/screens/WaitScreen.css`, and `frontend/src/screens/WaitScreen.test.tsx` to own `qrOpen`, derive one canonical URL from the shared logical base-path helper, pass that exact URL to both Copy link and `QrCodeSheet`, render `Show QR code` beside unchanged Copy ID/Copy link controls only after valid `WAITING_FOR_PLAYERS` context, a non-empty/non-whitespace session ID, and resolved loading, open the presentation before generation completes, and preserve refresh, polling, navigation, and existing copy behavior; add resolved-valid, empty/whitespace-session, missing-session/player, other-status, unresolved-loading, initial-no-QR, open-action, and existing-regression fixtures; prove with `frontend/node/npm --prefix frontend run test -- src/screens/WaitScreen.test.tsx` (FR-001, FR-002, FR-005-FR-006, FR-011, SC-001, SC-006, CHK001-CHK002, CHK008, CHK017, CHK019, CHK024, CHK030, CHK037).

**Checkpoint**: User Story 1 is independently green: the widget and waiting-room
integration each have their implementation and proving tests in the same task
sequence, QR generation is lazy, the valid-session boundary is preserved, and
existing sharing behavior is unchanged when QR is unused.

## Phase 4: User Story 2 - Scan the Same Public Destination as Copy Link (Priority: P1)

**Goal**: Copy link and QR use the identical absolute join URL across root,
LAN, hostname, port, protocol, and configured deployment-path shapes, and the
existing join screen receives the same session ID.

**Independent Test**: Focused URL/consumer tests compare the exact strings sent
to `copy-to-clipboard` and `qrcode.toCanvas`; the mock-browser journey proves
root and `/battleship/` route resolution and join-screen prefill.

- [ ] T007 [P] [US2] Add cross-consumer acceptance coverage in `frontend/src/config/appBasePath.test.ts`, `frontend/src/screens/WaitScreen.test.tsx`, and `frontend/src/screens/JoinGameScreen.test.tsx` for hostname/IP, HTTP/HTTPS protocol, non-default port, root and configured base path, encoded session ID, exactly-one base-path occurrence, byte-for-byte Copy-link/QR equality, and existing `?id=` prefill without changing the join route or adapter contract; prove with `frontend/node/npm --prefix frontend run test -- src/config/appBasePath.test.ts src/screens/WaitScreen.test.tsx src/screens/JoinGameScreen.test.tsx` and inspect `git diff HEAD -- docs/openapi.json` for unexpected API artifact changes (FR-005-FR-006, SC-002-SC-003, SC-006-SC-007, CHK004-CHK005, CHK009-CHK011, CHK015, CHK020-CHK021, CHK029, CHK031, CHK037-CHK040).

- [ ] T008 [P] [US2] Add `frontend/e2e/qr-session-sharing.spec.ts` for the mock-adapter journey: create a session through the real UI, reach `/game/wait`, assert no QR presentation before selection, select `Show QR code`, observe one ready canvas/instruction with its localized accessible label, and open the canonical join destination with the original encoded ID so `JoinGameScreen` pre-fills it; run the same journey at the configured `/battleship/` prefix by launching the mock server with `VITE_APP_BASE_PATH=/battleship/` and navigating below that prefix, assert asset URLs use the derived trailing-slash asset base while router paths and join URLs use the logical slashless base exactly once, and use `frontend/e2e/support/mockBackdoor.ts` only for setup/navigation support; prove with `frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts` and `VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts` (FR-001-FR-006, FR-011, SC-001-SC-003, SC-006, CHK001-CHK005, CHK019-CHK021, CHK024-CHK025, CHK037-CHK038).

**Checkpoint**: User Story 2 proves one canonical public URL in root and
configured-subpath journeys. The QR payload does not invent a route or security
model, and existing join query handling remains authoritative.

## Phase 5: User Story 3 - Use and Dismiss the Presentation Accessibly (Priority: P2)

**Goal**: The QR presentation inherits the existing Sheet dialog, focus,
dismissal, desktop/mobile, and localization contract while exposing reliable
loading and failure behavior.

**Independent Test**: Focused widget/Sheet tests and the browser journey verify
Close, Escape, backdrop dismissal, focus restoration, status/error semantics,
stale-result protection, both locales' visible strings, and a 320px viewport
with no horizontal overflow.

- [ ] T009 [P] [US3] Extend `frontend/src/widgets/sharing/QrCodeSheet.test.tsx`, `frontend/src/design/components/Sheet/Sheet.test.tsx`, and `frontend/src/i18n/keyParity.test.ts` to cover dialog/`aria-modal`/localized title wiring through the unchanged `Sheet`, role `status` loading, role `alert` error, ready canvas `role="img"` with the localized `screens.qr.description` `aria-label`, visible localized scan instruction, usable localized Close in every open state, focus entering/restoring through the existing overlay, Escape and backdrop dismissal, no stale completion after close/unmount, fresh state on reopen, no export/copy controls, and non-empty English/Ukrainian action/title/instruction/loading/error/Close values; do not add an `aria-describedby` requirement or reimplement/weaken `Sheet`; prove with `frontend/node/npm --prefix frontend run test -- src/widgets/sharing/QrCodeSheet.test.tsx src/design/components/Sheet/Sheet.test.tsx src/i18n/keyParity.test.ts` (FR-003, FR-007-FR-010, SC-004-SC-005, CHK003, CHK006-CHK007, CHK013, CHK016, CHK018, CHK022, CHK026-CHK028, CHK032-CHK036).

- [ ] T010 [P] [US3] Complete responsive styling in `frontend/src/widgets/sharing/QrCodeSheet.css` and any narrowly scoped `frontend/src/screens/WaitScreen.css` changes so the nominal canvas is 300 CSS pixels with proportional `max-width: 100%` behavior inside the existing overlay, fits at a 320px viewport without horizontal overflow, and retains centered desktop and mobile bottom-sheet behavior; keep computed-size/overflow proof in T011 and prove the style/build contract with `frontend/node/npm --prefix frontend run lint` and `frontend/node/npm --prefix frontend run build` (FR-007-FR-008, SC-004, CHK012-CHK013, CHK028, CHK033-CHK035).

- [ ] T011 [US3] Extend `frontend/e2e/qr-session-sharing.spec.ts` to exercise every visible dismissal and recovery path through real controls: Close button, Escape, backdrop, focus restoration to `Show QR code`, close while generation is pending where the browser can observe it, reopen with fresh current state, narrow 320px bottom-sheet layout without horizontal overflow, and English/Ukrainian action/title/instruction/loading/error/Close text; deterministic module/render failure remains owned by T005/T009 focused tests, do not add brittle browser fault injection, and record browser capability limitations separately from product failures; prove with `frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts` (FR-007-FR-010, SC-004-SC-005, CHK006-CHK007, CHK013, CHK018, CHK022, CHK026-CHK028, CHK032-CHK036).

**Checkpoint**: User Story 3 is independently accessible and responsive. No
new overlay implementation, QR export control, or QR-specific privacy rule is
introduced.

## Phase 6: Integration, Artifacts, and Verification

**Purpose**: Prove the complete feature, keep generated artifacts and
requirements-quality records honest, and leave the next integration step clear.

- [ ] T012 Run the focused feature evidence from `specs/001-qr-session-sharing/quickstart.md`: `frontend/node/npm --prefix frontend run test -- src/config/appBasePath.test.ts src/widgets/sharing/QrCodeSheet.test.tsx src/screens/WaitScreen.test.tsx src/screens/JoinGameScreen.test.tsx src/i18n/keyParity.test.ts`, `frontend/node/npm --prefix frontend run lint`, and the dedicated `frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts`; write acceptance-criterion-to-command/result mappings and remaining limitations to `specs/001-qr-session-sharing/verification.md`, including explicit results for FR-001-FR-011, SC-001-SC-006, all 16 checks in `checklists/requirements.md`, and CHK001-CHK040 without modifying reviewer-owned checklist markers (FR-001-FR-011, SC-001-SC-006, CHK001-CHK040).

- [ ] T013 Run `scripts/verify.sh` from the packaged-JAR-capable implementation state, inspect staged and unstaged changes with `git diff HEAD -- src/main/java docs/openapi.json frontend/package.json frontend/package-lock.json README.md docs` plus `git status --short --branch`, inspect `frontend/build` when present, compare against `baseline.md`, confirm no Java/API/OpenAPI change is present or explain and resolve any unexpected generated diff, run `python3 scripts/sync-agent-files.py --check` only if agent/configuration files were touched, and append the exact gate result, environment limitations such as localhost binding, reviewer-owned checklist state, and next task for user integration to `specs/001-qr-session-sharing/verification.md`; do not modify checklist files or markers (SC-007, CHK014, CHK017, CHK023, CHK039-CHK040).

**Closeout condition**: The feature is ready for user integration only after
the specification, plan, task branches/commits, focused tests, mock-browser
journey, full gate, generated-artifact review, checklist evidence, and
remaining limitations are all recorded. Final integration into `master` is the
user's responsibility.

## Dependencies and Execution Order

### Phase Dependencies

- T001 precedes all implementation and establishes the baseline/checklist map.
- T002 and T003 can run in parallel after T001; T004 depends on T003.
- T005 depends on T002 and T004 and owns both QR widget implementation and its
  focused tests; T006 depends on T005 and T004 and owns waiting-room integration
  plus its focused tests.
- T007 and T008 depend on the integrated screen/helper behavior in T006. T009
  depends on T006 and can run in parallel with T007/T008. T010 depends on T005
  and can run in parallel with T007-T009 because it owns CSS files only.
- T011 depends on T007-T010; T012 depends on all story checkpoints; T013
  depends on T012 and is the final gate/closeout task.

### Story Completion Order

```text
T001
 ├── T002 ────────────────┐
 └── T003 ── T004 ── T005 ── T006 ──┬── T007 ──┐
                                    ├── T008 ──┼── T011 ── T012 ── T013
                                    ├── T009 ──┤
                                    └── T010 ──┘
```

US1 is the first independently green product slice after the shared
configuration foundation. US2 is also P1 and must be completed before release.
US3 may proceed in parallel after US1 and closes accessibility, failure,
responsive, and bilingual coverage before final evidence.

### Parallel Execution Examples

```text
# After T001
T002 (dependency metadata) || T003 (base-path helper and tests)

# After T006
T007 (URL/join conformance tests) || T008 (root/subpath mock journey) || T009 (overlay/accessibility tests)

# After T005
T010 (responsive CSS and build proof) || T007/T008/T009

# After all story checkpoints
T012 (focused evidence) -> T013 (full gate and closeout)
```

Every parallel pair uses distinct files and must still obey the branch/commit
rule. If a task discovers a needed shared-file change, stop parallel work,
rebase that task onto the latest feature-parent state, and keep ownership
explicit rather than creating conflicting commits.

## Command Reference

Use `frontend/node`/the Maven-installed runtime for authoritative frontend
results when the full gate provides it.

```bash
frontend/node/npm --prefix frontend install
frontend/node/npm --prefix frontend run test -- <focused paths>
frontend/node/npm --prefix frontend run lint
frontend/node/npm --prefix frontend run build
frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts
VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts
scripts/verify.sh
python3 scripts/sync-agent-files.py --check
git diff HEAD -- <paths>
```

If Playwright fails before a test executes with restricted localhost binding,
record it as an environment capability limitation and retain the unchanged
product command/result separately. Do not convert an unrun browser suite into
a passing claim.

## Implementation Strategy

### MVP First

The smallest user-visible MVP is User Story 1 after the shared T001-T004
foundation: T005-T006 deliver a valid waiting-room `Show QR code` action,
lazy browser canvas generation, localized ready/loading/error states, and
regression coverage for existing sharing actions. Because User Story 2 is also
P1, a releasable feature must immediately include T007-T008; User Story 3 and
the final gate remain required for the complete specification.

### Incremental Delivery

1. Capture the baseline, including staged changes, and preserve checklist ownership.
2. Land dependency and shared base-path configuration in separate task branches/commits.
3. Land the QR widget and waiting-room integration with their focused tests in the same implementation sequence.
4. Prove exact URL equality, root/subpath routing, and existing join-screen prefill before treating QR as usable across deployments.
5. Close accessibility, failure, mobile, bilingual, mock-browser, generated-artifact, and full-gate evidence before user integration.
