# Feature Specification: Synthesized Audio Feedback

**Feature Branch**: `feature/synthesized-audio-feedback`

**Created**: 2026-08-17

**Status**: Ready for implementation

**Input**: User description: "docs/superpowers/specs/2026-08-17-synthesized-audio-feedback-design.md"

**Spec Kit Artifacts**: `specs/002-synthesized-audio-feedback/`

## Scope and Context

### Problem

During an active Battleship game, players currently rely on board changes,
highlights, and toast notifications to understand the result of newly received
shots. A short optional sound would reinforce those results for either player,
including shots observed through a pushed or refreshed gameplay state, without
changing the game rules or requiring downloadable media.

### In Scope

- Three short synthesized gameplay effects: a water-drop-like miss, a
  shot-like hit, and a longer explosion-like destroyed-ship effect.
- Classification of newly observed outcomes on both the player's fleet board
  and the visible target board.
- Duplicate suppression, initial-state baselining, and suppression of
  auto-revealed moat cells.
- A localized, keyboard-accessible Sound toggle with a visible pressed state.
- A browser-persisted sound preference that defaults to enabled for a new
  browser profile.
- Silent first-interaction unlocking and best-effort operation when browser
  audio is unavailable, suspended, rejected, or interrupted.
- Focused unit, component, and gameplay regression tests for classification,
  deduplication, preference behavior, synthesis, and failure handling.

### Initial Audio Palette

- **MISS**: a short, gentle water-drop-like tonal sweep.
- **HIT**: a short shot-like generated-noise crack layered with a descending
  tonal report.
- **DESTROYED**: a longer explosion-like layered effect with rumble, impact,
  partial, and filtered-noise components.

FR-001 is the sole normative source for recipe durations, frequencies, gain,
component requirements, and deterministic-generation tolerances. These are
verification targets, not external assets; implementation may tune individual
synthesis constants only within the FR-001 contract.

### Out of Scope

- Checked-in audio files, audio downloads, or a sound-asset delivery pipeline.
- Music, spatial audio, a mixer, a volume slider, or a sound-effects editor.
- Audio feedback for preparation, waiting-room, or results events.
- Vibration or other haptic feedback.
- Backend routes, request or response fields, persistence changes, or changes
  to REST/SSE game contracts.
- A runtime audio library or a new external service.
- Direct audio triggering from the local shot action, which could omit the
  opponent's shots and duplicate feedback when the resulting state arrives.

## Clarifications

### Session 2026-08-17

- Q: Should browser-audio unlock and failure states remain completely silent, with only localized `Sound on`/`Sound off` control text exposed to users? → A: Keep unlock and failure behavior silent; require only localized `Sound on`/`Sound off` labels and the control's `aria-pressed` state.
- Q: When a target-board state shows multiple newly shot visible ship cells while the opponent’s alive-ship count decreases, which outcome should the classifier assign? → A: Classify the row-major first newly shot ship-bearing cell as `DESTROYED`; classify other newly shot visible ship cells as `HIT`.
- Q: What measurable audio tolerances should define the three synthesized recipes and the phrase “conservative level”? → A: The complete measurable recipe contract is recorded normatively in FR-001, including the three identities, duration/frequency/gain tolerances, required components, relative duration, and deterministic in-memory generation.
- Q: What should make two gameplay snapshots equivalent for audio feedback, and in what order should multiple events be emitted? → A: Compare both boards’ dimensions, coordinates, `hasShot` values, visible ship data, and both alive-ship counts; ignore unrelated metadata; emit `playerField` events row-major, then `opponentField` events row-major.

## User Scenarios & Testing *(mandatory)*

User stories MUST be independently testable. Prioritize them as P1, P2, P3,
with P1 representing the smallest valuable end-to-end slice.

### User Story 1 - Hear each newly observed shot result (Priority: P1)

As a Battleship player, I want a brief sound that matches each newly observed
miss, hit, or destroyed ship so that I can recognize the result while looking
at either gameplay board.

**Why this priority**: Matching feedback for the three game outcomes is the
core user value, and it must work for both players regardless of whether the
state arrived from the player's own shot or from the opponent's update.

**Independent Test**: A gameplay component test with a fake audio port can
apply an initial state followed by incoming and outgoing state changes and
verify the corresponding outcome events exactly once.

**Acceptance Scenarios**:

1. **Given** sound is enabled and audio has been unlocked, **when** a newly
   received gameplay state reveals a miss on either board, **then** one
   water-drop-like feedback event is requested and the existing board and toast
   behavior remains visible.
2. **Given** sound is enabled and audio has been unlocked, **when** a newly
   received gameplay state reveals a hit that does not destroy a ship, **then**
   one shot-like feedback event is requested.
3. **Given** sound is enabled and audio has been unlocked, **when** a newly
   received gameplay state reveals that a ship was destroyed, **then** one
   longer explosion-like feedback event is requested.
4. **Given** two players are viewing the same active game, **when** each player
   receives their own gameplay-state update showing a shot result, **then** the
   same outcome classification and sound behavior is available to each player.

### User Story 2 - Classify only genuinely new outcomes (Priority: P1)

As a player receiving full gameplay snapshots, I want sound to represent only
new shot outcomes so that refreshes, duplicate updates, hidden ship details,
and automatic moat reveals do not create misleading or repeated sounds.

**Why this priority**: Full snapshots are the authoritative source for both
transport paths. Correct state-diff behavior prevents feedback from becoming
noisy or factually wrong.

**Independent Test**: A pure classifier test can compare representative prior
and current snapshots for fleet-board shots, target-board shots, sunk ships,
moat cells, batched changes, duplicate snapshots, and completion reveals.

**Acceptance Scenarios**:

1. **Given** the first gameplay snapshot already contains previously completed
   shots, **when** that snapshot becomes the baseline, **then** no sound event
   is requested for any existing shot.
2. **Given** a gameplay snapshot has already been processed, **when** an
   equivalent pushed or refreshed snapshot is processed again, **then** no
   duplicate sound event is requested.
3. **Given** a player's fleet board shows a newly shot cell, **when** the cell
   contains no ship, contains a live ship, or completes a ship, **then** the
   classifier returns MISS, HIT, or DESTROYED respectively, while moat cells
   revealed by that destruction are ignored as separate misses.
4. **Given** the visible target board hides unshot ship cells, **when** a newly
   shot cell becomes visible, **then** a cell without a ship is classified as
   MISS and a cell with a ship is classified as HIT unless the opponent's
   alive-ship count decreased in the same state transition. In that case, the
   row-major first newly shot ship-bearing cell is classified as DESTROYED and
   any other newly shot visible ship cells remain HIT.
5. **Given** a target-board ship is only partially revealed, **when** no alive-
   ship count decrease is observed, **then** the visible hit is not classified
   as DESTROYED.
6. **Given** several genuinely new shot cells arrive in one state update,
   **when** the update is classified, **then** each real shot produces its own
   outcome event and auto-revealed moat cells remain suppressed.

### User Story 3 - Control optional sound accessibly (Priority: P2)

As a player, I want to turn sound feedback on or off and have that choice
remembered so that audio remains under my control across reloads and game
navigation.

**Why this priority**: Audio is supplementary feedback and must never be
forced on a player or become a barrier to gameplay.

**Independent Test**: A component and preference test can exercise the toggle
in both locales, verify its pressed state and labels, reload the provider, and
confirm that the selected preference remains available.

**Acceptance Scenarios**:

1. **Given** a new browser profile with no saved sound preference, **when** the
   gameplay screen renders, **then** sound is enabled by default and the
   toggle exposes the localized `Sound on` state through its visible label and
   pressed state.
2. **Given** sound is enabled, **when** the player activates the Sound toggle,
   **then** it becomes disabled, exposes the localized `Sound off` state, and
   subsequent outcome events request no playback.
3. **Given** the player has changed the sound preference, **when** the page is
   reloaded or the player leaves the game, **then** the preference remains
   stored independently of game-session data.
4. **Given** the player switches between English and Ukrainian, **when** the
   gameplay utility actions and Sound toggle are rendered, **then** every new
   sound label and status has a matching translation key in both languages.

### Edge Cases and Failure Behavior

- The initial gameplay snapshot seeds the comparison baseline and never plays
  sounds for shots that happened before the screen observed that snapshot.
- Equivalent state snapshots received through push or refresh are idempotent;
  they do not repeat an outcome.
- A newly visible target-board cell that has not been shot is ignored, including
  cells revealed when the game reaches its final state.
- Destroying a fleet-board ship may mark surrounding moat cells as shot; those
  cells are not additional MISS outcomes. Real newly shot cells in the same
  update still produce individual outcomes.
- A partially revealed target-board ship is a HIT unless the opponent's alive-
  ship count decreased in the same transition.
- If a malformed target-board transition contains multiple newly shot visible
  ship cells while the opponent's alive-ship count decreases, the row-major
  first ship-bearing candidate is DESTROYED and the other newly shot visible
  ship cells are HIT.
- Sound is not emitted before the first pointer or keyboard interaction. That
  interaction may unlock audio silently and must not create a visible error or
  audible unlock sound.
- Disabled sound, an unavailable audio capability or output device, a
  suspended context, a rejected resume, a failure while creating, connecting,
  or scheduling audio nodes, or a document visibility change results in
  best-effort no-op behavior. None may block rendering, toasts, tab switching,
  navigation, or results display.
- A scheduled sound that outlives the gameplay screen is cleaned up or safely
  ignored; it must not update an unmounted screen or create an unhandled
  promise rejection.
- Rapid successive state updates and batched new shots remain safe and do not
  collapse distinct real outcomes into duplicate or missing feedback.
- Leaving a game or clearing game-session data does not clear the independent
  sound preference.
- No new backend or generated API artifact should appear. Any unexpected
  artifact difference requires inspection before the feature proceeds.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide three distinct synthesized feedback
  outcomes: a short water-drop-like MISS, a short shot-like HIT, and a longer
  explosion-like DESTROYED effect. MISS MUST last `145 ms ±15 ms`, HIT MUST
  last `130 ms ±15 ms`, and DESTROYED MUST last `780 ms ±80 ms` and at least
  three times longer than the longer short effect. Peak master gain MUST be at
  or below `0.25` linear (`-12 dB`). MISS MUST include one tonal sweep with
  nominal `330 Hz` and `470 Hz` endpoints, each within `±5 Hz`, constrained to
  `325–475 Hz`. HIT MUST include one generated-noise crack and one descending
  tonal report with `127 Hz ±5 Hz` and `92 Hz ±5 Hz` endpoints. DESTROYED MUST
  include a `54 Hz ±5 Hz` to `40 Hz ±5 Hz` decaying rumble sweep, a
  `112 Hz ±5 Hz` impact partial, a `180 Hz ±5 Hz` to `140 Hz ±5 Hz` descending
  partial, and deterministic low-pass-filtered noise. All effects MUST be
  non-blocking and MUST NOT replace, suppress, or delay the existing visual
  board, toast, highlight, tab, or navigation feedback.
- **FR-002**: The system MUST classify newly observed shot outcomes from the
  full gameplay state received through either pushed updates or refetches,
  considering both the player's fleet board and the visible target board.
- **FR-003**: The system MUST seed its comparison baseline from the first
  observed gameplay state and MUST NOT request feedback for pre-existing shots.
- **FR-004**: The system MUST request at most one outcome event per genuinely
  newly shot cell and MUST suppress events for equivalent repeated snapshots.
  Feedback equivalence MUST compare both boards' dimensions, coordinates,
  `hasShot` values, visible ship data, and both alive-ship counts while ignoring
  names, turn/readiness, winner, alive-cell counts, availability, and other
  unrelated metadata.
- **FR-005**: For the fleet board, a newly shot cell without a ship MUST be
  classified as MISS, a newly shot cell containing a ship that remains alive
  MUST be classified as HIT, and a newly shot cell that newly completes a ship
  MUST be classified as DESTROYED.
- **FR-006**: For the visible target board, a newly shot cell without a visible
  ship MUST be classified as MISS and a newly shot cell with a visible ship
  MUST be classified as HIT unless the full-state opponent alive-ship count
  decreased in that transition. When it decreased, the row-major first newly
  shot ship-bearing cell MUST be classified as DESTROYED and any other newly
  shot visible ship cells in that transition MUST remain HIT. The system MUST
  NOT infer destruction solely from a partially revealed target-board ship.
- **FR-007**: The system MUST ignore target-board cells that merely become
  visible without becoming shot and MUST ignore fleet-board moat cells
  automatically revealed by a newly destroyed ship.
- **FR-008**: When multiple real shot cells are newly observed in one state
  update, the system MUST preserve one outcome event for each real shot while
  continuing to suppress automatic moat reveals. Events MUST be emitted in
  deterministic order: `playerField` row-major first, followed by
  `opponentField` row-major.
- **FR-009**: The Sound control MUST be available during gameplay as a
  keyboard-reachable button with `aria-pressed`, a visible enabled/disabled
  state, and localized `Sound on` and `Sound off` labels in English and
  Ukrainian.
- **FR-010**: Sound feedback MUST default to enabled for a browser profile
  without a saved preference, MUST be disabled immediately after the player
  turns it off, and MUST persist the selected preference across reloads and
  leaving a game. The preference MUST remain separate from game-session data.
- **FR-011**: The system MUST unlock audible feedback only in response to an
  explicit user gesture, MUST attempt unlocking silently, and MUST skip sound
  rather than block gameplay when no user gesture has occurred.
- **FR-012**: The system MUST treat browser audio as optional. Missing audio
  capability, suspended or rejected audio activation, unavailable output, a
  failure while creating, connecting, or scheduling audio nodes, and cleanup
  during unmount MUST not throw into gameplay rendering, produce an unhandled
  rejection, or prevent existing visual feedback and navigation.
  Unlock, loading, retry, and failure states MUST remain silent and MUST NOT
  emit separate user-facing status or error copy.
- **FR-013**: Outcome playback MUST be driven by the newly classified gameplay
  state, not directly by the local shot action, so that incoming opponent shots
  and the local player's refetched result follow the same deduplicated path.
- **FR-014**: The three synthesized effects MUST be generated in browser
  memory from deterministic tunable recipes and MUST NOT require checked-in
  audio files, a download, a backend field, a new endpoint, or a runtime audio
  library.
- **FR-015**: Existing gameplay rules, board rendering, toast notifications,
  board flashes, tab switching, transport behavior, and results navigation MUST
  remain unchanged when sound is disabled, unavailable, or unused.

### Frontend Requirements

- The existing gameplay-state flow remains the source of truth; no new server
  event, REST field, or adapter operation is required.
- State interpretation MUST remain isolated from browser audio playback so a
  fake audio port can be supplied in focused tests without using a real output
  device.
- Gameplay UI MUST expose the Sound control alongside existing gameplay utility
  actions without removing or weakening current visual and toast feedback.
- Browser preference handling MUST use a dedicated storage entry and MUST not
  be removed by the existing game-data cleanup behavior.
- English and Ukrainian resources MUST contain matching keys for the Sound
  control's on/off states and its accessible control label. No unlock, loading,
  retry, or failure message is required or emitted.
- Frontend tests MUST cover the classifier, synthesized recipes, preference
  persistence, toggle semantics, first-gesture unlocking, safe audio failure,
  both-board gameplay updates, deduplication, moat suppression, and existing
  gameplay regressions.

### Contract and Boundary Requirements

- Backend layer affected: `none`.
- Frontend layers affected: browser audio service, pure gameplay-feedback
  logic, gameplay screen/provider state, browser preference storage, and
  localization.
- Allowed dependency direction: gameplay state is interpreted by pure frontend
  logic; the gameplay screen consumes the resulting outcome events through a
  browser-local audio boundary; browser audio failure cannot flow back into
  game state or navigation.
- The provider may be mounted at the app root and may silently create or resume
  its browser context after a pointer or keyboard gesture on any route, but
  only `GameplayScreen` may request playback or render the Sound control. No
  audio scheduling, audio status copy, or gameplay behavior may be added to
  waiting, preparation, or results routes.
- New dependency: `none`; audio is synthesized with browser capabilities
  already available to the application.
- Persistence/auth/external-service changes: `none`; only the local sound
  preference is persisted in the browser.
- REST, SSE, DTO, OpenAPI, backend, and game-storage contracts MUST remain
  unchanged.

## Key Entities and State

- **Battle outcome**: One of MISS, HIT, or DESTROYED, representing the
  observable result of one genuinely newly shot cell.
- **Gameplay snapshot baseline**: The previous full gameplay state used to
  identify newly shot cells and compare alive-ship counts. The first snapshot
  establishes the baseline without producing feedback. Feedback comparisons
  use only board dimensions, coordinates, `hasShot`, visible ship data, and
  alive-ship counts; unrelated snapshot metadata is ignored.
- **Feedback event**: A classified outcome emitted once for a newly observed
  real shot, after fleet-board moat suppression and target-board visibility
  rules have been applied.
- **Sound preference**: The player's enabled/disabled choice, defaulting to
  enabled and stored independently of the current game session.
- **Audio availability state**: The best-effort browser capability and unlock
  status that determines whether a requested event can be heard without
  affecting gameplay when it cannot.

## Success Criteria *(mandatory)*

Success criteria MUST be measurable and tied to evidence.

- **SC-001**: Focused classification and gameplay tests report exactly one
  matching MISS, HIT, or DESTROYED event for every representative newly
  observed real shot on both boards, with zero events for the initial state,
  equivalent duplicate snapshots, target-board visibility-only changes, and
  auto-revealed moat cells.
- **SC-002**: Recipe and audio-adapter tests cover all three recipes, confirm
  that `play(MISS)`, `play(HIT)`, and `play(DESTROYED)` schedule every
  component required by FR-001, and verify every FR-001 duration, frequency,
  gain, relative-duration, determinism, and in-memory-generation constraint.
  The adapter tests MUST observe the scheduled node/component types and safe
  cleanup without reading an external audio file.
- **SC-003**: Preference and component tests confirm that a missing preference
  starts enabled, toggling produces the correct pressed state and localized
  label, disabling prevents playback requests, and the selected value survives
  reload and game-session cleanup.
- **SC-004**: English and Ukrainian translation parity tests report identical
  key sets and both locales provide the Sound on/off labels and accessible
  control label required by the gameplay control; no unlock, loading, retry,
  or failure copy is emitted.
- **SC-005**: Failure-path tests cover missing browser audio capability,
  suspended or rejected activation, unavailable output, injected context/node
  scheduling failures, rapid updates, and unmount cleanup with zero unhandled
  promise rejections and no interruption to visual rendering, toast display,
  tab switching, or results navigation.
- **SC-006**: Existing gameplay tests for shot handling, incoming-shot
  batching, sink detection, visual highlights, and final-state navigation remain
  green with the audio feature enabled and disabled.
- **SC-007**: No backend source, REST/SSE contract, DTO, runtime audio-library
  dependency, or checked-in audio file is added; `docs/openapi.json` remains
  unchanged unless an unexpected diff is explicitly inspected and explained.
- **SC-008**: `scripts/verify.sh` passes before the feature is declared complete,
  or the exact environment capability limitation and affected checks are
  recorded.

## Assumptions and Clarifications

- The existing full gameplay snapshot is authoritative for both an opponent's
  incoming shot and the local player's result after a shot/refetch; no separate
  last-shot event is introduced.
- A new browser profile has audio enabled by default, but browsers may require a
  pointer or keyboard gesture before any audible output is permitted.
- The browser's current audio capability is sufficient; this feature does not
  add device selection, volume configuration, or an alternate audio backend.
- The sound preference is a user preference rather than game state and remains
  available after leaving or completing a game.
- Exact oscillator, noise, envelope, filter, and scheduling parameters remain
  tunable implementation constants during PLAN, provided the FR-001 recipe
  contract and deterministic in-memory generation remain intact.
- No clarification is required: the supplied design explicitly fixes the
  outcome set, both-board scope, default preference, browser-only generation,
  failure behavior, and backend/API boundary.

## Evidence and Documentation Impact

- Proving tests: focused frontend unit tests for recipes, preference storage,
  and gameplay classification; component tests for the gameplay screen and
  Sound toggle; browser regression coverage for gameplay and results
  navigation; repository verification through `scripts/verify.sh`.
- Generated artifacts: no OpenAPI or backend artifact is expected to change;
  inspect the packaged frontend/JAR output and report any unexpected diff.
- Documentation: implementation should update maintained product or
  architecture documentation only if the repository documents gameplay audio
  controls or browser-storage behavior; this specification is the required
  planning record for the feature.
- Feature artifacts: PLAN must define the concrete audio boundary, recipe
  tests, preference key, classifier tests, component integration, and ordered
  verification tasks under `specs/002-synthesized-audio-feedback/`.
