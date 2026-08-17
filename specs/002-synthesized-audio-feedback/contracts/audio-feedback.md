# Audio Feedback Contracts

**Feature**: [spec.md](../spec.md)

This is a frontend-internal contract. It does not change REST, SSE, DTO,
OpenAPI, or `GameAdapter` interfaces.

## Outcome and event types

```ts
type BattleOutcome = 'MISS' | 'HIT' | 'DESTROYED';

type GameplayFeedbackEvent = {
  outcome: BattleOutcome;
  board: 'player' | 'opponent';
  cellKey: string;
};
```

The classifier accepts the previous and current full gameplay snapshots and
returns `GameplayFeedbackEvent[]`. A `null` previous snapshot returns an empty
array and establishes the baseline. The classifier never reads browser APIs,
storage, React state, or audio services. Snapshot equivalence compares only
both board dimensions, coordinates, `hasShot` values, visible ship data, and
both alive-ship counts; unrelated metadata is ignored. Events are returned in
`playerField` row-major order followed by `opponentField` row-major order. If a
target alive-ship decrease has multiple newly shot visible ship candidates,
the first row-major ship-bearing candidate is `DESTROYED` and the others are
`HIT`.

## AudioFeedbackPort

The gameplay screen depends on this narrow port rather than on Web Audio nodes
or a concrete service.

```ts
type AudioFeedbackPort = {
  unlockFromUserGesture: () => void;
  play: (outcome: BattleOutcome) => void;
  isEnabled: () => boolean;
  setEnabled: (enabled: boolean) => void;
  dispose: () => void;
};
```

Contract rules:

- `unlockFromUserGesture` may create/resume the single context, but catches all
  activation failures and produces no audible unlock sound.
- `play` is synchronous from the caller's perspective, never throws into the
  gameplay render/effect, and is a no-op when disabled or unavailable.
- `setEnabled` updates the provider-owned preference and prevents future
  playback requests while false.
- `dispose` stops/detaches scheduled work safely and is idempotent.
- `isEnabled` reflects the current user preference, not whether the browser
  has successfully unlocked audio.

## Recipe contract

`audioRecipes.ts` exports typed, deterministic tuning constants for the MISS,
HIT, and DESTROYED identities. The complete normative duration, frequency,
component, gain, relative-duration, and deterministic-generation contract is
FR-001 in [spec.md](../spec.md); this contract must not introduce a second set
of tolerances.

Recipes are generated in memory; no URL, file path, network request, or third-
party audio package is accepted by the contract. Tests MUST assert every
observable FR-001 component and tolerance through both the recipe descriptions
and fake-node adapter scheduling; exact node graph and envelope values beyond
those observable targets remain implementation details.

## Gameplay UI contract

- Gameplay renders one button alongside its utility actions.
- The button has a localized visible `Sound on` or `Sound off` label and
  `aria-pressed="true"` or `aria-pressed="false"` matching the preference.
- The button is keyboard reachable and does not replace board, toast, or
  highlight feedback.
- No audio status dialog, error toast, retry loop, volume slider, or control is
  required when audio is unsupported or blocked.

## Boundary contract

- The existing `useGameplay` state application remains the only gameplay state
  source.
- `handleShot` must not call the audio port directly.
- No new adapter method, backend endpoint, REST/SSE field, route, persistence
  entity, or OpenAPI output is introduced.
