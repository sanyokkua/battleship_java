# Synthesized Audio Feedback Design

**Status:** Draft for review

**Date:** 2026-08-17

**Feature:** synthesized audio feedback for Battleship gameplay

## Goal

Give both Battleship players short synthesized audio feedback when a newly received gameplay state reveals a shot result:

- a short water-drop-like sound for a miss;
- a shot-like sound for a hit;
- a longer explosion-like sound when a ship is destroyed.

The sounds must be generated in the browser with the Web Audio API. The feature must not add audio files, backend endpoints, API fields, or a runtime audio library.

## Selected sound palette

The initial recipes are deliberately simple and remain tunable constants rather than external assets. They are based on the listening previews selected during design:

- **MISS:** a mono 44.1 kHz sine sweep from approximately 330 Hz to 470 Hz over 145 ms, with an approximately 6 ms attack and 55 ms release. This is the original `battleship-hit.wav` preview, which sounded closest to a water drop.
- **HIT:** a 130 ms generated noise crack layered with a low-frequency sine report. The report starts around 127 Hz and settles near 92 Hz; both components decay quickly. This is the `battleship-shot-hit.wav` preview.
- **DESTROYED:** a 780 ms layered explosion with a strong initial impact followed by a decaying rumble: a low sine sweep around 54 Hz to 40 Hz, a 112 Hz impact partial, a short 180 Hz to 140 Hz partial, and deterministic low-pass-filtered noise. This is the extended `battleship-explosion-sunk-long.wav` preview.

The generated noise is an in-memory `AudioBuffer`, not a downloaded or checked-in sound file. Final gain remains subtle enough that the audio supplements the visual result and toast rather than dominating the game.

## Scope

The feature includes:

- a small browser audio adapter backed by one lazily-created `AudioContext`;
- the three synthesized recipes above;
- a pure gameplay-state diff classifier that identifies newly observed MISS, HIT, and DESTROYED outcomes;
- feedback for both the player's own target board and the player's fleet board when either board changes through a received state;
- first-user-interaction audio unlocking without an audible unlock sound;
- a player-controlled sound toggle with an accessible pressed state;
- persistence of the sound preference in browser storage;
- best-effort behavior when the browser blocks, suspends, or does not expose Web Audio;
- unit, component, and gameplay regression tests for classification, deduplication, and adapter behavior.

The feature does not include:

- audio files or a sound-download pipeline;
- music, a sound mixer, spatial audio, volume sliders, or a sound-effects editor;
- backend or REST changes;
- vibration implementation;
- audio playback from preparation, waiting-room, or results events;
- a new third-party audio library such as Tone.js.

## Existing context and injection points

`useGameplay` applies gameplay state from both sources that matter:

- SSE/fallback snapshots are applied through the session-event callback;
- the active player's own shot calls `shoot`, then immediately refetches and applies a fresh gameplay snapshot.

`GameplayScreen` already owns the state-diff effect that compares successive `playerField` snapshots, classifies incoming miss/hit/sunk results, suppresses auto-revealed moat cells, and manages the corresponding board flash. That effect is the correct integration boundary for audio because it already represents the UI's authoritative interpretation of a newly received game update.

`handleShot` continues to own validation and localized shot toasts. It must not become an audio trigger: doing so would cover only the local player's action and could play a duplicate sound when the resulting state snapshot is applied.

## User experience

The gameplay screen shows a compact Sound toggle next to the existing gameplay utility actions. It uses an accessible button with `aria-pressed`:

- `Sound on` when feedback is enabled;
- `Sound off` when feedback is disabled.

The labels are localized in English and Ukrainian. Audio is enabled by default for a new browser profile, but no sound is emitted until the browser has received a user gesture and the audio context has been unlocked. The first pointer or keyboard interaction may unlock audio silently. A player can turn feedback off at any time; the preference survives reloads and does not get cleared when a game session is left.

If the first game update arrives before interaction, the visual board and toast still update and the sound is skipped. There is no blocking dialog, error toast, or retry loop for an unavailable audio device or autoplay rejection.

## Architecture and data flow

The preferred architecture separates state interpretation from browser audio:

```text
SSE / fallback refetch / own-shot refetch
                    |
                    v
       useGameplay applies a full state snapshot
                    |
                    v
     GameplayScreen compares previous and current state
                    |
                    v
       pure classifier returns feedback events
                    |
                    v
          AudioFeedbackPort.play(outcome)
                    |
                    v
        AudioContext oscillator/noise synthesis
```

The public adapter boundary is conceptually:

```ts
type BattleOutcome = 'MISS' | 'HIT' | 'DESTROYED';

type AudioFeedbackPort = {
    unlockFromUserGesture: () => void;
    play: (outcome: BattleOutcome) => void;
    isEnabled: () => boolean;
    setEnabled: (enabled: boolean) => void;
};
```

The React provider/hook owns the enabled preference and supplies the port to `GameplayScreen`. The concrete audio service owns the `AudioContext`, master gain, generated noise buffers, oscillator scheduling, and safe cleanup. Tests can supply a fake port or fake `AudioContext` without using a real speaker.

## State-diff classification

The classifier compares both board fields, not only the current player's own field:

- a new `hasShot` cell in `playerField` represents a shot received by this player;
- a new `hasShot` cell in `opponentField` represents this player's shot result as observed in the refetched or pushed state.

The initial state snapshot seeds the baseline and produces no sound. Repeated equivalent snapshots also produce no sound.

For `playerField`, all ship cells are visible, so the existing `computeSunkShipIds` and `computeMoatCellKeys` logic can identify a newly destroyed ship and ignore auto-revealed moat cells.

For `opponentField`, unshot ships are intentionally hidden. The classifier must not call `computeSunkShipIds` on that field to infer destruction, because a partially revealed ship can appear to contain only the one visible hit cell. Instead:

- `cell.ship == null` on a newly shot visible cell means MISS;
- `cell.ship != null` means HIT unless the full-state `opponentNumberOfAliveShips` count decreased relative to the previous snapshot;
- a decrease in that count classifies the corresponding newly revealed ship cell as DESTROYED;
- cells that merely become visible at game completion while `hasShot` remains false are ignored.

The classifier returns one event per genuinely newly shot cell. The existing moat suppression remains applied to incoming fleet-board updates. The normal live transport emits one state change per shot; if a test or fallback batches several changes into one render, the classifier preserves the individual outcomes without treating moat cells as shots.

## Audio synthesis behavior

`AudioFeedback` creates the `AudioContext` only in response to an explicit user gesture. If the context is suspended, unlocking calls `resume()` and catches rejection. Playback never throws into React or interrupts rendering.

The service uses:

- `OscillatorNode` plus `GainNode` for the MISS sweep;
- a generated short noise `AudioBufferSourceNode` plus a low oscillator for HIT;
- generated low-pass-filtered noise plus several short-lived oscillators for DESTROYED;
- one master gain for a conservative overall level;
- scheduled `start()`/`stop()` times so no application timers are needed for sound lifetime.

`play` is a no-op when disabled, when Web Audio is unavailable, or when the context cannot be resumed after the browser's activation policy. The service should attempt to resume an already-created context when the document becomes visible again, but visibility recovery is best-effort and is not treated as a user gesture.

Modern browsers commonly require a user interaction before audible Web Audio can start, and a newly-created context may initially be suspended. The implementation follows the browser guidance to resume it from a user action and treats autoplay rejection as an optional-feedback condition. See [MDN autoplay guidance](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay) and [MDN Web Audio best practices](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API/Best_practices).

## Component and file responsibilities

The implementation should follow these boundaries; exact filenames can be adjusted during PLAN if repository conventions require it:

- `frontend/src/audio/audioRecipes.ts`: typed recipe constants and synthesis parameters; no React or storage access.
- `frontend/src/audio/AudioFeedback.ts`: concrete Web Audio adapter, context lifecycle, synthesis, master gain, safe no-op behavior, and disposal.
- `frontend/src/audio/AudioFeedbackContext.tsx`: React provider/hook, first-gesture unlock listeners, enabled preference, and visibility recovery.
- `frontend/src/logic/gameplayFeedback.ts`: pure previous/current state classifier for both fields, including moat suppression and opponent alive-ship-count destruction detection.
- `frontend/src/screens/GameplayScreen.tsx`: consume the feedback port from the provider, run the classifier from the existing `[state]` diff effect, and render the localized toggle. Toasts and board rendering remain in this screen.
- `frontend/src/services/AudioPreferences.ts`: load/save the enabled preference under a dedicated key that is not removed by `clearGameData`.
- `frontend/src/i18n/en/screens.json` and `frontend/src/i18n/uk/screens.json`: localized sound-toggle labels and accessible status text.

No backend, adapter contract, REST controller, OpenAPI, board DTO, route, or game-storage changes are expected.

## Accessibility and failure behavior

The sound toggle is keyboard reachable, has a visible state, and exposes `aria-pressed`. It must not be the only way the player learns about a shot: the board state, toast notifications, and existing visual highlight remain authoritative.

The app must tolerate:

- browsers without `window.AudioContext` or `window.webkitAudioContext`;
- suspended contexts;
- rejected `resume()` calls;
- output-device or silent-mode suppression;
- rapid successive state updates;
- the screen unmounting while a sound is scheduled.

None of these conditions may produce an unhandled promise rejection or prevent navigation to results.

## Verification

Tests will prove:

- each recipe schedules the expected oscillator/noise components, envelopes, duration, and cleanup without external files;
- the default preference is enabled and the toggle persists changes;
- first state arrival produces no sound;
- a new incoming MISS, HIT, and DESTROYED state produces exactly one corresponding event;
- auto-revealed moat cells do not produce MISS sounds;
- a new outgoing MISS, HIT, and DESTROYED state is classified from `opponentField` and the alive-ship count;
- a partially revealed opponent ship is not falsely classified as DESTROYED;
- equivalent SSE/refetch snapshots do not duplicate sound;
- both English and Ukrainian sound labels remain in translation parity;
- an unavailable or suspended audio implementation does not break visual rendering, toasts, or results navigation;
- the existing gameplay tests continue to pass, including incoming-shot batching, sink detection, and final-state navigation.

The implementation will run the repository frontend tests, lint/build checks, and `scripts/verify.sh`. Since the feature is frontend-only, no OpenAPI regeneration is expected; any generated diff must still be inspected and reported.

## Acceptance criteria

- During gameplay, a newly observed miss plays the selected water-drop-like synthesized effect when audio is enabled and unlocked.
- A newly observed hit plays the selected shot-like synthesized effect.
- A newly observed destroyed ship plays the longer selected explosion-like synthesized effect.
- Both players receive the same behavior from their own received gameplay-state updates.
- The initial snapshot, stale duplicate snapshot, and auto-revealed moat cells do not create false feedback.
- No external audio file is added to the repository.
- Audio failure never prevents state rendering, toast display, tab switching, or results navigation.
- The player can disable and re-enable sound with an accessible localized toggle, and the preference survives reloads.
- The implementation adds no backend/API changes or runtime audio-library dependency.
- The verification gate is green before the feature is declared complete.
