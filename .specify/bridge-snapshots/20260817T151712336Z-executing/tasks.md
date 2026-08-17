---
description: "Dependency-ordered task ledger for synthesized Battleship gameplay audio feedback"
---

# Tasks: Synthesized Audio Feedback

**Input**: Design documents from `specs/002-synthesized-audio-feedback/`

**Prerequisites**: `spec.md` and `plan.md` are approved design inputs. The
implementation also uses `research.md`, `data-model.md`,
`contracts/audio-feedback.md`, `quickstart.md`, and
`checklists/audio-feedback.md`. T001 creates
`specs/002-synthesized-audio-feedback/verification.md` for baseline and
closeout evidence.

## Task Rules

- Use the format `[ID] [P?] [Story] Description`.
- `[P]` means the task can run in parallel without shared-file or dependency conflicts.
- `[Story]` maps the task to `US1`, `US2`, or `US3` from `spec.md`.
- Every task names exact repository paths, requirement/checklist coverage, and a proving test or command.
- Tests are required because the specification explicitly requires focused unit, component, mock-browser, and regression evidence.
- Keep one task per task branch and one task commit, following `AGENTS.md`.
- Keep all task checkboxes unchecked until the implementation and its proving evidence are complete.
- Treat T001's recorded worktree state as the baseline for later audits; do not use a `HEAD` diff alone to misclassify pre-existing user changes as feature work.
- Test-authoring tasks may precede implementation tasks, but their proving commands run only after the implementation dependency exists and the evidence is recorded in the feature verification file.
- Do not change backend, adapter, REST, SSE, DTO, OpenAPI, or game-session storage contracts for this frontend-only feature.

## Path Conventions

| Concern | Paths |
|---|---|
| Frontend application | `frontend/src/` |
| Frontend unit/component tests | Co-located `frontend/src/**/*.test.ts` and `frontend/src/**/*.test.tsx` |
| Mock browser tests | `frontend/e2e/` |
| Live browser tests | `frontend/e2e-live/` |
| API artifact | `docs/openapi.json` |
| Feature evidence | `specs/002-synthesized-audio-feedback/verification.md` |

## Resolved Planning Decisions

The following decisions close the review checklist gaps without changing the
approved product scope:

1. `AudioPreferences` owns one dedicated boolean local-storage key. Reads and
   writes catch storage/security/quota failures, keep the current in-memory
   value, and never block gameplay; successful storage remains persistent across
   reloads and `clearGameData()`.
2. Normal target-board transitions have at most one newly shot visible
   ship-bearing candidate when `opponentNumberOfAliveShips` decreases. For a
   synthetic malformed multi-candidate state, the classifier selects the
   row-major first ship-bearing candidate as `DESTROYED`, classifies the other
   newly shot visible ship cells as `HIT`, and still emits one event per real
   newly shot cell.
3. Audio failure has no user-facing error, retry, or loading copy. The only new
   localized control copy is the accessible visible `Sound on`/`Sound off`
   state in English and Ukrainian; all browser-audio failures remain silent
   no-ops as required by the design.
4. No backend/API/OpenAPI task is expected. The closeout audit must prove that
   this boundary remains unchanged and must explain any unexpected generated
   artifact diff before completion.
5. Feedback-equivalent snapshots compare only both board dimensions,
   coordinates, `hasShot` values, visible ship data, and both alive-ship counts;
   names, turn/readiness, winner, alive-cell counts, availability, and other
   unrelated metadata are ignored. Events are emitted in `playerField`
   row-major order followed by `opponentField` row-major order.
6. FR-001 is the sole normative recipe contract. Recipe and adapter tests
   must assert every FR-001 duration, frequency, gain, component,
   relative-duration, determinism, and in-memory-generation requirement.
7. T001 records active-feature resolution and the complete pre-existing dirty
   path set. Later boundary checks classify paths against that baseline and an
   explicit expected frontend allowlist rather than treating all `HEAD` diffs
   as feature changes.
8. The gameplay component test harness supplies an injectable fake
   `AudioFeedbackPort`; it never depends on a real `AudioContext` or speaker.
   The concrete service/provider keeps any failure seam test-only and outside
   the `GameAdapter` contract.
9. The closeout evidence is append-ordered because every verification task
   writes `verification.md`; independent implementation/test-authoring work is
   parallelized only before that shared evidence phase.

**Open questions/gaps after planning**: None. The checklist ambiguities are
resolved above and each criterion is assigned to an owning task below.

## Phase 1: Baseline and Setup

**Purpose**: Capture the starting state and preserve the existing worktree
while locking the approved frontend-only scope.

- [x] T001 Create `specs/002-synthesized-audio-feedback/verification.md` by first resolving the active feature with `rtk .specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks`, then record `git status --short --branch`, `git diff HEAD --stat`, `git diff HEAD -- .specify/bridge-events.jsonl .specify/superpowers-handoff.json specs/002-synthesized-audio-feedback/`, `git diff HEAD -- docs/openapi.json`, `git ls-files --others --exclude-standard -- .specify/bridge-snapshots specs/002-synthesized-audio-feedback frontend/src/audio frontend/e2e/audio-feedback.spec.ts`, `git ls-files --others --exclude-standard -- src/main/java frontend/src/adapters frontend/src/logic/ApplicationTypes.ts docs/openapi.json frontend/package.json frontend/package-lock.json`, and `git diff --check`; preserve every pre-existing dirty path, including all currently present bridge-snapshot directories and untracked feature artifacts without hardcoding their names, and record the active feature resolution, baseline path classification, and no-backend/API boundary in `verification.md` before source changes. Covers FR-013–FR-015, SC-007–SC-008, CHK005, CHK012, CHK035, CHK037. Prove with the listed baseline commands and the recorded baseline table in `verification.md`; do not edit `tasks.md` to store execution evidence.

## Phase 2: Foundational Frontend Boundaries

**Purpose**: Establish reusable pure board logic and independent browser
preference behavior before story-specific audio and gameplay integration.

- [x] T002 [P] Extract `computeSunkShipIds` and `computeMoatCellKeys` from `frontend/src/widgets/board/Board.tsx` into `frontend/src/logic/boardState.ts`, re-export them from `Board.tsx`, and add `frontend/src/logic/boardState.test.ts`; preserve ship-ID completion, eight-neighbor moat bounds, row/column key format, every existing `Board.tsx` import/call site, and existing board rendering behavior. Covers FR-005, FR-007–FR-008, SC-001, SC-006, CHK007, CHK028, CHK030. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/logic/boardState.test.ts src/widgets/board/Board.test.tsx` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run build`.
- [x] T003 [P] Implement `frontend/src/services/AudioPreferences.ts` and `frontend/src/services/AudioPreferences.test.ts` with a dedicated boolean key, enabled-by-default loading, immediate save, non-throwing storage/security/quota fallback, and no interaction with `frontend/src/services/GameBrowserStorage.ts` or `clearGameData()`. Exercise the real `clearGameData()` import from `frontend/src/services/GameBrowserStorage.ts`, restore storage test fixtures after each case, and prove the preference survives cleanup. Covers FR-010, FR-012, Frontend Requirements, SC-003, CHK004, CHK036, CHK040. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/services/AudioPreferences.test.ts`.

**Checkpoint**: Pure board helpers and browser preference behavior are tested
without touching backend/API contracts or real audio devices.

## Phase 3: User Story 2 - Classify Only Genuinely New Outcomes (Priority: P1)

**Goal**: Compare full gameplay snapshots and produce deterministic,
deduplicated outcome events for both boards without false moat or visibility
feedback.

**Independent Test**: `frontend/src/logic/gameplayFeedback.test.ts` applies
representative previous/current `ResponseGameplayStateDto` snapshots and
expects row-major `GameplayFeedbackEvent[]` values for MISS, HIT, DESTROYED,
baseline, duplicates, final visibility-only changes, moat suppression, batched
shots, and target-board alive-ship-count rules, with `playerField` events
preceding `opponentField` events.

### Tests for User Story 2

- [x] T004 [P] [US2] Add `frontend/src/logic/gameplayFeedback.test.ts` with deterministic snapshot/cell fixtures covering null baseline, the feedback-relevant equivalent-snapshot projection, player-field MISS/HIT/newly sunk DESTROYED, target-field MISS/HIT/partial reveal/alive-ship-count DESTROYED, final-state visibility-only changes, moat suppression, real batched shots, player-then-opponent row-major event order, and the resolved malformed multi-candidate fallback. Keep the fixture projection explicit so changes to names, turn/readiness, winner, alive-cell counts, or availability cannot create events accidentally. Covers FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, SC-001, CHK001–CHK003, CHK006–CHK009, CHK012–CHK018, CHK022–CHK024, CHK027–CHK031, CHK038–CHK039. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/logic/gameplayFeedback.test.ts` after T005.

### Implementation for User Story 2

- [x] T005 [US2] Implement `frontend/src/logic/gameplayFeedback.ts` as a browser- and React-free classifier over `ResponseGameplayStateDto`; compare `hasShot === false` to `true`, return no events for `previous === null` or equivalent feedback projections, classify both `playerField` and `opponentField`, use extracted sunk/moat helpers only for the fleet board, use `opponentNumberOfAliveShips` for target-board destruction, ignore visibility-only cells, preserve one event per real shot in `playerField` row-major then `opponentField` row-major order, and apply the resolved row-major fallback for malformed target batches. Covers FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, SC-001, CHK003, CHK006–CHK009, CHK012–CHK013, CHK023–CHK024, CHK027–CHK029, CHK038–CHK039. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/logic/gameplayFeedback.test.ts src/logic/boardState.test.ts` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run build`.

**Checkpoint**: US2 is independently functional when the classifier tests pass
and no browser audio or gameplay UI is required to exercise it.

## Phase 4: User Story 1 - Hear Each Newly Observed Shot Result (Priority: P1)

**Goal**: Turn classifier outcomes into deterministic, browser-native,
fake-testable MISS, HIT, and DESTROYED feedback while preserving the existing
visual gameplay behavior.

**Independent Test**: `frontend/src/screens/GameplayScreen.test.tsx` supplies a
fake `AudioFeedbackPort`, applies an initial state followed by incoming and
outgoing full-state changes, and asserts exactly one matching playback request
per real shot with existing toast, highlight, tab, and results behavior intact.

### Tests for User Story 1

- [x] T006 [P] [US1] Add `frontend/src/audio/audioRecipes.test.ts` for the deterministic in-memory recipe contract defined by FR-001, covering all required MISS, HIT, and DESTROYED components, durations, frequencies, gain, relative duration, deterministic generation, and absence of external files, URLs, or runtime audio dependencies. Covers FR-001, FR-014, SC-002, CHK001, CHK010, CHK018, CHK037. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/audio/audioRecipes.test.ts` after T007.
- [x] T007 [US1] Implement `frontend/src/audio/audioRecipes.ts` with typed tunable constants and pure recipe descriptions/synthesis parameters satisfying the complete FR-001 contract. Keep all generation in memory and preserve the observable identities without importing React, storage, files, URLs, or runtime audio libraries. Covers FR-001, FR-014, SC-002, CHK001, CHK010, CHK018, CHK037. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/audio/audioRecipes.test.ts` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run build`.
- [x] T008 [P] [US1] Add `frontend/src/audio/AudioFeedback.test.ts` and `frontend/src/audio/AudioFeedbackContext.test.tsx` using an injectable fake `AudioContext`/node graph and a fake `AudioFeedbackPort`; never depend on a real speaker or a production `AudioContext` in Vitest. Assert that `play(MISS)`, `play(HIT)`, and `play(DESTROYED)` schedule every FR-001 component with the required observable timing/frequency/gain contract, then cover lazy creation, pointer/keyboard gesture-only unlock, silent rejected `resume()`, absent constructor, disabled/no-op behavior, synchronous non-throwing `play`, injected output/create/connect/start/stop failures, visibility recovery without treating visibility as the initial gesture, scheduled node cleanup, idempotent disposal, listener removal, root-level non-gameplay gestures without playback/UI changes, and no unhandled promise rejection. Covers FR-001, FR-011–FR-012, SC-002, SC-005, CHK004, CHK011, CHK014, CHK021, CHK025, CHK030–CHK036. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/audio/AudioFeedback.test.ts src/audio/AudioFeedbackContext.test.tsx` after T009.

### Implementation for User Story 1

- [x] T009 [US1] Implement `frontend/src/audio/AudioFeedback.ts` and `frontend/src/audio/AudioFeedbackContext.tsx`, exposing the `AudioFeedbackPort` contract and a narrow test-only context/failure injection seam; create one lazy standard/compatible `AudioContext` only from a pointer or keyboard gesture, resume silently, schedule the FR-001 oscillator/buffer/filter components with no application timers, make `play` synchronous and safe when disabled/unavailable, isolate injected context/node/output failures, keep unlock/loading/retry/failure states silent, retry an existing context on visibility recovery, and dispose all work/listeners idempotently. Mount the provider in `frontend/src/App.tsx` and load the enabled value through `frontend/src/services/AudioPreferences.ts`; root-level gestures outside gameplay may unlock silently but must never schedule playback or render audio UI outside `GameplayScreen`, and the seam must not alter `GameAdapter` or any backend contract. Covers FR-001, FR-011–FR-012, FR-014, SC-002, SC-005, CHK004, CHK011, CHK014, CHK025, CHK030–CHK036. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/audio/AudioFeedback.test.ts src/audio/AudioFeedbackContext.test.tsx src/services/AudioPreferences.test.ts` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run build`.
- [x] T010 [US1] Extend `frontend/src/screens/GameplayScreen.test.tsx` and its `renderGameplayScreen` fixture with a fake-port/provider injection for first-state baselining, incoming `playerField` MISS/HIT/DESTROYED, outgoing `opponentField` MISS/HIT/DESTROYED, duplicate/refetch suppression, batched outcomes, moat suppression, no direct `handleShot` playback, and unchanged toast/highlight/tab/results behavior. The test must exercise the same provider boundary used by the screen without constructing a real browser audio context. Covers FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-013, FR-015, SC-001, SC-005–SC-006, CHK002–CHK005, CHK013, CHK015–CHK017, CHK022–CHK032. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/screens/GameplayScreen.test.tsx` after T011.
- [x] T011 [US1] Replace the player-field-only baseline in `frontend/src/screens/GameplayScreen.tsx` with a full `ResponseGameplayStateDto` baseline and call the pure classifier from the existing `[state]` diff effect; use the feedback-relevant snapshot projection, send each returned outcome to the provider port in `playerField` row-major then `opponentField` row-major order, keep `handleShot` limited to validation/shoot/toasts, and preserve the existing fleet-board toast/highlight logic, highlight timers, moat behavior, turn-driven tab switching, visual boards, refresh, and results navigation. Equivalent snapshots must still advance the baseline without playback. Covers FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-013, FR-015, SC-001, SC-005–SC-006, CHK002–CHK005, CHK013, CHK015–CHK017, CHK022–CHK032. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/screens/GameplayScreen.test.tsx src/logic/gameplayFeedback.test.ts` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run lint`.

**Checkpoint**: US1 is independently functional when fake-port gameplay tests
prove both-board result playback and all existing visual/toast behavior remains
unchanged.

## Phase 5: User Story 3 - Control Optional Sound Accessibly (Priority: P2)

**Goal**: Give the player a localized keyboard-reachable Sound control whose
preference persists independently of game data and whose failure paths never
block gameplay.

**Independent Test**: The gameplay component/provider tests and
`frontend/e2e/audio-feedback.spec.ts` exercise English and Ukrainian labels,
`aria-pressed`, default enabled state, toggle persistence, first gesture,
audio failure, and results navigation without asserting physical sound output.

### Tests for User Story 3

- [x] T012 [US3] Extend `frontend/src/screens/GameplayScreen.test.tsx`, `frontend/src/audio/AudioFeedbackContext.test.tsx`, and `frontend/src/i18n/keyParity.test.ts` with enabled/disabled pressed-state assertions, localized English/Ukrainian `Sound on`/`Sound off` labels, keyboard activation, preference reload/leave-game persistence, `clearGameData()` independence, disabled playback suppression, and parity for every newly used key. Reuse the injectable fake-port/provider harness from T010 and import the real `clearGameData()` path rather than clearing all storage indiscriminately. Covers FR-009–FR-012, Frontend Requirements, SC-003–SC-004, CHK004, CHK014, CHK019–CHK020, CHK025–CHK026, CHK032–CHK036, CHK040. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/screens/GameplayScreen.test.tsx src/audio/AudioFeedbackContext.test.tsx src/services/AudioPreferences.test.ts src/i18n/keyParity.test.ts` after T013.

### Implementation for User Story 3

- [x] T013 [US3] Add the gameplay Sound button in `frontend/src/screens/GameplayScreen.tsx` beside the existing refresh utility, bind it to provider preference state, expose matching visible `Sound on`/`Sound off` text and `aria-pressed`, use the exact accessible label key `gameplay.soundLabel`, keep it keyboard reachable without replacing board/toast feedback, style it in `frontend/src/screens/GameplayScreen.css` without changing board breakpoints, and add matching `gameplay.soundOn`, `gameplay.soundOff`, and `gameplay.soundLabel` keys to `frontend/src/i18n/en/screens.json` and `frontend/src/i18n/uk/screens.json`; do not add failure/loading/error copy because audio failures are silent no-ops. Covers FR-009–FR-012, FR-015, Frontend Requirements, SC-003–SC-004, CHK004, CHK014, CHK019–CHK020, CHK025–CHK026, CHK032–CHK033, CHK040. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/screens/GameplayScreen.test.tsx src/audio/AudioFeedbackContext.test.tsx src/i18n/keyParity.test.ts` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run lint`.
- [x] T014 [US3] Add `frontend/e2e/audio-feedback.spec.ts` using the existing `frontend/e2e/support/mockBackdoor.ts` helpers `readPersistedSession`, `createOpponent`, `placeFullFleetAndReady`, `persistStage`, and `hardNavigate`, plus `page.addInitScript` to install a deterministic audio-context stub before app load. Reach gameplay, exercise English and Ukrainian Sound labels/pressed state, trigger pointer/keyboard unlock, disable sound, switch the stub through missing-context, rejected-resume, and output/node-scheduling-failure modes, confirm no uncaught error interrupts board interaction or results navigation, and verify a root-level gesture on a non-gameplay route does not add playback, status copy, or gameplay behavior. Assert UI resilience and stub observations only, never physical speaker output; if the existing StageGuard workaround is needed, keep it confined to the named helpers. Covers FR-009–FR-012, FR-015, SC-003–SC-006, CHK021, CHK025–CHK026, CHK030–CHK036, CHK040. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test:e2e -- audio-feedback.spec.ts`.

**Checkpoint**: US3 is independently functional when the toggle/provider tests,
translation parity, and mock-browser journey pass in both supported locales.

## Phase 6: Polish, Cross-Cutting Verification, and Closeout

**Purpose**: Prove the complete frontend-only boundary, preserve existing
regressions, record evidence, and close every checklist reference without
marking the reviewer-owned checklist items themselves.

- [x] T015 Audit the complete worktree against T001's baseline and the explicit expected frontend allowlist (`frontend/src/audio/**`, `frontend/src/logic/boardState*`, `frontend/src/logic/gameplayFeedback*`, `frontend/src/services/AudioPreferences*`, `frontend/src/App.tsx`, `frontend/src/screens/GameplayScreen*`, the two gameplay `screens.json` resources, `frontend/e2e/audio-feedback.spec.ts`, and feature evidence); reject any new or modified `src/main/java/`, `frontend/src/adapters/`, `frontend/src/logic/ApplicationTypes.ts`, `docs/openapi.json`, `frontend/package.json`, or `frontend/package-lock.json` path unless T001 proves it pre-existed and the result is explicitly classified. Record the audit in `specs/002-synthesized-audio-feedback/verification.md`. Covers FR-014–FR-015, SC-007, CHK005, CHK012, CHK016, CHK037. Prove with `git status --short --branch`, `git diff --name-only HEAD -- src/main/java frontend/src/adapters frontend/src/logic/ApplicationTypes.ts docs/openapi.json frontend/package.json frontend/package-lock.json`, `git ls-files --others --exclude-standard -- src/main/java frontend/src/adapters frontend/src/logic/ApplicationTypes.ts docs/openapi.json frontend/package.json frontend/package-lock.json`, `git diff --name-only HEAD -- docs/openapi.json frontend/package.json frontend/package-lock.json`, and the T001 baseline comparison recorded in `verification.md`; do not use broad `*audio*` searches as the sole boundary proof because expected feature paths also contain `audio`.
- [x] T016 Run focused frontend unit/component evidence with the pinned runtime for `src/logic/gameplayFeedback.test.ts`, `src/logic/boardState.test.ts`, `src/audio/audioRecipes.test.ts`, `src/audio/AudioFeedback.test.ts`, `src/audio/AudioFeedbackContext.test.tsx`, `src/services/AudioPreferences.test.ts`, `src/screens/GameplayScreen.test.tsx`, and `src/i18n/keyParity.test.ts`; record command, result, and requirement evidence in `specs/002-synthesized-audio-feedback/verification.md`. Covers SC-001–SC-006 and CHK001–CHK040 through the task-specific evidence above. Prove with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- src/logic/gameplayFeedback.test.ts src/logic/boardState.test.ts src/audio/audioRecipes.test.ts src/audio/AudioFeedback.test.ts src/audio/AudioFeedbackContext.test.tsx src/services/AudioPreferences.test.ts src/screens/GameplayScreen.test.tsx src/i18n/keyParity.test.ts`.
- [x] T017 Run `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run lint` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run build`, inspect the generated frontend output for absence of audio files, URLs, and new runtime packages, and record results in `specs/002-synthesized-audio-feedback/verification.md`. Compare both `frontend/package.json` and `frontend/package-lock.json` to the T001 baseline and inspect the production dependency tree with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend ls --depth=0 --omit=dev`; do not treat the expected `frontend/src/audio/**` source directory as generated output. Covers FR-014, FR-015, SC-002, SC-007–SC-008, CHK010, CHK018, CHK031, CHK034, CHK037. Prove with the lint/build/package commands, `find frontend/build -type f \( -iname '*audio*' -o -iname '*.mp3' -o -iname '*.wav' -o -iname '*.ogg' \) -print`, and the generated-output/package inspection recorded in `verification.md`.
- [x] T018 Run the mandatory mock-browser journey and the existing mock suite with `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test:e2e -- audio-feedback.spec.ts` and `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test:e2e`; record any localhost-binding capability limitation separately from product results in `specs/002-synthesized-audio-feedback/verification.md`. Covers SC-005–SC-006, CHK021, CHK025–CHK026, CHK030–CHK036. Prove with both Playwright commands and the limitation record in `verification.md`.
- [x] T019 Run `mvn clean package -DskipTests` from the repository root, confirm `target/battleship-0.0.1-SNAPSHOT.jar` exists, then run `PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test:e2e:live` against that packaged JAR and preserve the existing gameplay-to-results regression evidence; no live audio-specific server journey is required because the backend contract is unchanged. Do not stop an unrelated process already using port 8080; record any reuse, binding, Java, or packaging limitation separately from product results. Record the packaging and live-test results in `specs/002-synthesized-audio-feedback/verification.md`. Covers FR-015, SC-006, CHK005, CHK016–CHK017, CHK021. Prove with both commands and their recorded results in `verification.md`.
- [x] T020 Run `scripts/verify.sh`, inspect `docs/openapi.json`, and record acceptance criterion -> proving test/command -> result -> limitation in `specs/002-synthesized-audio-feedback/verification.md`; because the gate rejects a pre-existing OpenAPI diff before Maven starts, compare with T001 and report that limitation instead of overwriting or silently normalizing the artifact. Do not claim completion if the gate is red or environment-limited without naming the exact affected check. Covers SC-007–SC-008, CHK017–CHK021, CHK031, CHK034–CHK037. Prove with `scripts/verify.sh`, `git diff HEAD -- docs/openapi.json`, and the evidence record.
- [x] T021 Review the complete task/checklist traceability matrix in `specs/002-synthesized-audio-feedback/tasks.md`, ensure every `CHK001`–`CHK040`, `FR-001`–`FR-015`, and `SC-001`–`SC-008` has an owning task and proving evidence, ensure all tasks remain sequential/dependency-valid with serial ownership of `verification.md`, and update `specs/002-synthesized-audio-feedback/verification.md` with any final limitation; leave `checklists/audio-feedback.md` and `checklists/requirements.md` reviewer-owned artifacts unchanged. Covers all checklist items and closes the no-open-question/no-unowned-requirement condition. Prove with `rg -n 'CHK0(0[1-9]|[1-3][0-9]|40)|FR-0[1-9]|FR-01[0-5]|SC-00[1-8]' specs/002-synthesized-audio-feedback/tasks.md specs/002-synthesized-audio-feedback/verification.md`, `awk '/^- \[[ x]\] T[0-9]{3}/{count++; id=$3; sub(/^T/, "", id); if ((id + 0) != count) exit 1} END {exit count == 21 ? 0 : 1}' specs/002-synthesized-audio-feedback/tasks.md`, `git diff --check`, and `git diff HEAD -- specs/002-synthesized-audio-feedback/checklists`.

## Checklist Coverage Matrix

Every reviewer-owned checklist item is assigned to one or more executable
tasks. The checklist files intentionally remain unchanged and unchecked; task
completion plus proving evidence closes implementation coverage, while a
reviewer remains responsible for marking requirements-quality criteria.

| Checklist items | Owning tasks | Closure evidence |
|---|---|---|
| CHK001 | T006, T007, T016 | Recipe identity, duration, and focused evidence |
| CHK002 | T004, T010, T011 | Both-board full-state classification and screen evidence |
| CHK003 | T004, T005, T010 | Baseline, duplicate, moat, visibility, and batching cases |
| CHK004 | T003, T008, T012 | Preference, unlock, disabled, unavailable, and cleanup cases |
| CHK005 | T001, T010, T011, T015, T019 | Preserved visual/gameplay and backend boundary evidence |
| CHK006 | T004, T005 | `hasShot` transition contract |
| CHK007 | T002, T004, T005 | Fleet newly-sunk classification |
| CHK008 | T004, T005 | Target alive-ship-count classification |
| CHK009 | T004, T005 | Partial target reveal remains HIT |
| CHK010 | T006, T007, T017 | Observable recipe targets and build evidence |
| CHK011 | T008, T009 | Gesture, silent unlock, and no-op contract |
| CHK012 | T001, T004, T005, T015 | Pure full-state boundary and no browser dependency |
| CHK013 | T010, T011 | Baseline ownership and duplicate consistency |
| CHK014 | T008, T009, T012 | Default enabled and pre-gesture silence |
| CHK015 | T010, T011 | No direct `handleShot` playback |
| CHK016 | T001, T010, T011, T015, T019 | Out-of-scope lifecycle and regression boundaries |
| CHK017 | T004, T010, T016, T020 | Measurable event counts and gate evidence |
| CHK018 | T006, T007, T016, T017 | Recipe evidence without node-graph overconstraint |
| CHK019 | T012, T013, T016 | Preference and control acceptance evidence |
| CHK020 | T012, T013, T016 | Locale parity acceptance evidence |
| CHK021 | T008, T010, T012, T014, T018 | Failure and lifecycle resilience evidence |
| CHK022 | T004, T010, T011 | MISS/HIT/DESTROYED on both boards |
| CHK023 | T004, T005, T010 | Baseline, duplicate, visibility, and moat scenarios |
| CHK024 | T004, T005, T010, T014 | Batched real shots and target destruction |
| CHK025 | T008, T009, T012, T014 | Disabled, locked, unsupported, and rejected audio |
| CHK026 | T012, T013, T014 | English/Ukrainian accessible control |
| CHK027 | T004, T005, T010 | Refresh/remount baseline versus duplicate |
| CHK028 | T002, T004, T005 | Real shots versus moat cells |
| CHK029 | T004, T005, T021 | Multi-candidate target fallback decision |
| CHK030 | T008, T009, T010, T014 | Rapid updates, unmount, cleanup, and rejection |
| CHK031 | T008, T009, T011, T016 | Synchronous non-blocking playback |
| CHK032 | T012, T013, T014 | Keyboard and ARIA control semantics |
| CHK033 | T012, T013 | Locale parity and explicit silent failure-copy decision |
| CHK034 | T008, T009, T014, T017 | Browser capability and no-unhandled-rejection evidence |
| CHK035 | T001, T003, T008, T009, T020 | Full-state, browser, and gesture assumptions |
| CHK036 | T003, T008, T009 | Storage failure fallback |
| CHK037 | T001, T015, T017, T020 | No new dependency/API boundary |
| CHK038 | T004, T005, T021 | Newly-observed event definition alignment |
| CHK039 | T004, T005, T021 | Target destruction candidate resolution |
| CHK040 | T003, T012, T013, T021 | Preference lifetime and browser-profile persistence |

## Requirement Traceability

| Requirements / criteria | Tasks |
|---|---|
| FR-001 | T006–T009, T016–T017 |
| FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008 | T002, T004–T005, T010–T011, T016 |
| FR-009–FR-012 | T003, T008–T009, T012–T014, T016, T018 |
| FR-013 | T010–T011, T019 |
| FR-014 | T006–T009, T015–T017 |
| FR-015 | T010–T011, T013–T014, T018–T020 |
| Frontend and contract boundary requirements | T001–T003, T009–T011, T013, T015, T020–T021 |
| SC-001 | T004–T005, T010–T011, T016 |
| SC-002 | T006–T009, T016–T017 |
| SC-003 | T003, T009, T012–T014, T016 |
| SC-004 | T012–T013, T016 |
| SC-005 | T008–T014, T016, T018 |
| SC-006 | T002, T010–T011, T016, T018–T019 |
| SC-007 | T001, T015, T017, T020 |
| SC-008 | T001, T017–T021 |

## Dependencies and Execution Order

### Phase Dependencies

```text
T001 Baseline
  ├── T002 Board pure-logic boundary ──> T004 [P] -> T005 (US2 classifier)
  ├── T003 [P] Preference boundary
  ├── T006 [P] -> T007 (US1 recipe contract)
  └── T008 [P] (audio lifecycle/adapter test authoring; proof waits for T009)
T003 + T007 + T008 ──> T009 (US1 audio service/provider)
T005 + T007 + T009 ──> T010 -> T011 (US1 gameplay integration)
T011 + T003 + T009 ── T012 -> T013 (US3 toggle/localization)
T013 ── T014 (US3 mock-browser journey)
T011 + T013 ── T015 ── T016 ── T017 ── T018 ── T019 ── T020 ── T021 closeout audit
```

### Story Completion Order

1. US2 classification is first because US1 playback consumes its pure event contract.
2. US1 audio recipes, service/provider, and state-diff playback form the smallest valuable end-to-end slice.
3. US3 adds the player control, persistence proof, localization, and mock-browser resilience journey.
4. Cross-cutting verification follows all story checkpoints.

### Parallel Execution Examples

- After T001, T002 (board helper extraction) and T003 (preference service) can run in parallel because they use disjoint files.
- After T001, T006 (recipe-test authoring) and T008 (audio/provider-test authoring) can run in parallel with T003; after T002, T004 can be authored in parallel with those disjoint tasks. T005 and T007 can then be implemented independently after their respective test tasks, while T009 waits for T003, T007, and T008.
- After T014, T015 through T020 run serially because each task appends results to the shared `verification.md`; this also keeps the final evidence order deterministic.
- T021 remains the final closeout task because it consumes all prior evidence and the final artifact state.

## Implementation Strategy

### MVP First

1. Complete T001–T005 to establish the pure state-diff contract.
2. Complete T006–T011 to deliver the P1 slice: deterministic recipes, safe browser audio, and both-board gameplay playback.
3. Run the US1 focused checkpoint before starting the P2 control work.
4. Complete T012–T014 for preference control, localization, and browser resilience.

### Closeout

Complete T015–T021 only after all story checkpoints are green. The feature is
ready for user integration only when the specification is implemented, every
checklist item has an owning task and evidence, generated artifacts are
reviewed, `scripts/verify.sh` is green or its exact capability limitation is
recorded, and the reviewer-owned checklist files remain semantically intact.

## Phase 7: Convergence

- [ ] T022 Apply the clarified frontend-only same-update moat rule per FR-007 and FR-008 (partial). Update `frontend/src/logic/gameplayFeedback.ts` and its focused tests so a transition containing a newly destroyed ship suppresses newly shot adjacent water cells as ambiguous automatic moat, while distinguishable real shots elsewhere still emit MISS/HIT/DESTROYED events in deterministic player-then-opponent row-major order; keep visibility-only target changes, duplicate snapshots, and known automatic moat cells silent. Do not add backend/API fields or adapter operations.
- [ ] T023 Assert the complete fake-audio scheduling contract per SC-002 and T008 (partial). Extend `frontend/src/audio/audioRecipes.test.ts` and `frontend/src/audio/AudioFeedback.test.ts` to verify FR-001 component start offsets and durations, oscillator frequency ramps, per-component gains and master-gain ceiling, generated-noise/filter scheduling, deterministic in-memory buffers, safe node cleanup, and idempotent disposal without external audio files or a real output device.
