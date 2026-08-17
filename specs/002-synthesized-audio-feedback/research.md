# Research: Synthesized Audio Feedback

**Feature**: [spec.md](./spec.md)

**Date**: 2026-08-17

## Decision 1: Use one lazy browser-native audio context

**Decision**: Create the audio context only from an explicit pointer or
keyboard gesture, reuse one context for the provider lifetime, and treat
missing, suspended, or rejected audio as a no-op condition.

**Rationale**: The design explicitly forbids audio files and runtime audio
libraries. MDN documents that Web Audio source `start()` calls outside user
input are subject to autoplay blocking and recommends creating or resuming an
`AudioContext` from a user gesture. The provider can therefore unlock silently
without making gameplay wait for an audio promise.

**Alternatives considered**:

- Creating the context during app startup was rejected because browsers may
  leave it suspended and because it violates the feature's no-audible-output-
  before-interaction requirement.
- HTML audio files were rejected because the specification forbids checked-in
  assets and an audio-download pipeline.
- Tone.js, Howler, and similar libraries were rejected because the scope
  explicitly excludes a runtime audio dependency for three small effects.

**Sources**: [MDN autoplay guidance](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay)
and [MDN Web Audio best practices](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API/Best_practices).

## Decision 2: Generate short effects with oscillators and in-memory buffers

**Decision**: Keep recipe constants in a pure module and synthesize the MISS
and HIT tonal/noise components plus DESTROYED layered noise/oscillator
components in memory. The observable recipe contract is the normative FR-001
section in `spec.md`; use the context's sample rate for generated buffers while
testing the design target at 44.1 kHz.

**Rationale**: MDN identifies `OscillatorNode` and generated buffers as the
native ways to create sound without loading files. The selected recipes are
short, deterministic, and tunable; the FR-001 gain and timing contract plus
scheduled `AudioParam` envelopes provide the specified level and predictable
duration.

**Alternatives considered**:

- Runtime decoding of downloaded or bundled samples was rejected by the
  no-audio-file boundary.
- Application timers for sound lifetime were rejected; scheduled node
  `start()`/`stop()` operations keep audio lifetime inside the audio clock and
  reduce React timer cleanup risk.

## Decision 3: Classify full state before playback

**Decision**: Add a pure classifier that compares the previous and current
`ResponseGameplayStateDto` and returns one event per genuinely newly shot cell.
The first state is only a baseline. Fleet-board destruction uses visible ship
completion and existing moat derivation; target-board destruction uses a
decrease in `opponentNumberOfAliveShips` because unshot opponent ship cells are
hidden.

**Rationale**: `useGameplay` applies both pushed session snapshots and the
local shot's refetched snapshot through the same state setter. The existing
`GameplayScreen` effect already owns the authoritative player-field diff,
toast, highlight, and tab-switch behavior. Calling audio from that state-diff
boundary covers both players and prevents `handleShot` from double-triggering
the local result.

**Alternatives considered**:

- Triggering audio directly from `handleShot` was rejected because it misses
  opponent shots and duplicates the subsequent state snapshot.
- Classifying only `playerField` was rejected because it omits the local
  player's outgoing result on `opponentField`.
- Inferring target-board destruction from `computeSunkShipIds` was rejected
  because hidden unshot ship cells make a partially revealed ship appear
  falsely complete.

## Decision 4: Keep preference and audio lifecycle separate from game storage

**Decision**: Store one boolean preference under a dedicated audio key, default
to enabled when absent, and do not add it to `GameBrowserStorage.clearGameData`.
The provider owns the preference state and the service owns context/node
cleanup.

**Rationale**: The preference is user-level browser state, while session,
player, and stage keys are disposable game state. Separating the service also
lets tests inject a fake port without accessing speakers or browser audio
globals.

**Alternatives considered**:

- Reusing `GameBrowserStorage` was rejected because its public cleanup function
  intentionally removes all game-session keys and the feature requires audio
  preference survival after leaving a game.
- A volume slider or persisted audio profile was rejected as out of scope.

## Decision 5: Use layered test evidence instead of real-speaker assertions

**Decision**: Prove recipe scheduling and browser failure behavior with fake
`AudioContext`/port objects in Vitest. Prove user-visible toggle semantics,
gesture unlock wiring, no uncaught failures, and results navigation in the
mandatory mock-browser journey with an init-time audio stub. Retain the existing
live full-game regression; no backend-specific audio journey is required.

**Rationale**: Real output devices are unavailable and nondeterministic in CI,
while the feature contract is about state classification, scheduling, control,
and graceful degradation. The existing repository already separates
co-located Vitest tests, mock-browser journeys, and packaged-JAR live tests.

**Alternatives considered**:

- Relying only on browser E2E was rejected because it cannot reliably prove
  oscillator parameters or exact deduplication.
- Adding a backend/live API test was rejected because no backend or DTO change
  exists.

## Resolved Planning Unknowns

- **Performance**: no blocking or awaited audio work in the state effect; one
  lazy context; the complete FR-001 recipe contract; and no app timers for
  sound lifetime.
- **Browser support**: use the standard `AudioContext` with a best-effort
  compatibility lookup for the browser's supported constructor; absent support
  is a silent no-op, not a new dependency.
- **Persistence**: dedicated boolean local-storage key, enabled by default,
  untouched by game-data cleanup.
- **Integration**: no `GameAdapter`, backend, REST, SSE, DTO, route, OpenAPI,
  or generated-artifact contract change.
- **Documentation**: keep the design/spec/plan artifacts current; update
  maintained product docs only if they already document gameplay controls.
