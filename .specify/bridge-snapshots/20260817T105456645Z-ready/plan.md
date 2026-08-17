# Implementation Plan: Synthesized Audio Feedback

**Branch**: `feature/synthesized-audio-feedback` | **Date**: 2026-08-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-synthesized-audio-feedback/spec.md`

This plan is dependency-ordered. It keeps implementation frontend-only, uses
the existing gameplay snapshot flow as the source of truth, preserves the
GameAdapter boundary, and leaves final integration to the user.

## Summary

Add optional synthesized MISS, HIT, and DESTROYED feedback to active gameplay.
The smallest valuable slice is a pure classifier that turns one new gameplay
snapshot into deduplicated outcome events, followed by a fake-testable browser
audio port and a localized Sound toggle. The existing visual board, toast,
highlight, turn, transport, and results behavior remain authoritative and
unchanged.

## Technical Context

**Backend Language/Version**: Java 25; no backend source or contract changes
are planned.

**Frontend Language/Version**: TypeScript with React 19.

**Build Tools**: Maven; `frontend-maven-plugin` installs Node v24.18.0 and
runs npm; Vite builds to `frontend/build`.

**Primary Dependencies**: Existing React, React Router, i18next, Vitest,
Testing Library, ESLint, and Playwright dependencies. No audio library or new
runtime dependency.

**Storage**: Existing browser `localStorage` for a dedicated sound-preference
key; game-session keys and in-memory backend state remain unchanged.

**Testing**: Vitest/Testing Library for pure logic, audio recipes, service,
provider, preference, and screen behavior; mock-browser Playwright for the
user-visible toggle and failure-safe gameplay journey; existing live packaged-
JAR regression through the repository gate.

**Target Platform**: Browser client served by the existing Spring Boot JAR on
port 8080 or through the mock Vite server. Web Audio capability is optional.

**Project Type**: Full-stack web service with a bundled React SPA; this feature
changes only the frontend bundle.

**Performance Goals**: State classification and audio-request scheduling must
not await network or audio work and must not block rendering. The audio service
uses one lazily-created context, schedules short-lived nodes directly, and
keeps the longest recipe near 780 ms without application timers for sound
lifetime.

**Constraints**: Preserve single-JAR packaging, the layered backend, the
`GameAdapter`/`GameAdapterContext` boundary, in-memory session behavior,
REST/SSE/DTO/OpenAPI contracts, English/Ukrainian translation parity, and
`scripts/verify.sh`. The browser must create/resume audio only from a user
gesture; missing or rejected audio must be a safe no-op.

**Scale/Scope**: Add `frontend/src/audio/` recipes, service, provider, and
focused tests; add pure `frontend/src/logic/gameplayFeedback.ts` and tests;
add `frontend/src/services/AudioPreferences.ts` and tests; modify
`frontend/src/App.tsx`, `frontend/src/screens/GameplayScreen.tsx`, its CSS,
the existing board-helper location/exports as needed, and both
`frontend/src/i18n/*/screens.json` resources. Add mock-browser coverage for the
toggle and failure-safe gameplay behavior. Do not change backend source,
`GameAdapter`, adapters, routes, DTOs, OpenAPI, or results behavior.

## Constitution Check

*GATE: Must pass before implementation. Re-check after design and before final verification.*

- [x] Runtime authority is identified in current `GameplayScreen`,
  `useGameplay`, `ResponseGameplayStateDto`, board helpers, tests, and storage;
  the supplied design and spec provide context and acceptance criteria.
- [x] Backend dependency direction remains untouched because no backend layer
  is affected.
- [x] Frontend network access remains behind `GameAdapter`; the feature reads
  already-delivered state and adds no adapter or network call.
- [x] REST/SSE, DTO, mock-adapter, and OpenAPI effects are explicitly `none`;
  the existing snapshot transport remains the event source.
- [x] In-memory persistence, authentication scope, and external-service scope
  are unchanged; only a dedicated browser preference is added.
- [x] Focused pure-logic, audio-service, preference, provider, component, and
  existing gameplay/browser regression tests are planned.
- [x] Generated artifact and documentation effects are listed: no OpenAPI
  regeneration is expected, while unexpected diffs must be inspected.
- [x] Final evidence includes `scripts/verify.sh` and separates any browser or
  Java capability limitation from product evidence.

## Existing Project Structure

```text
frontend/src/
├── adapters/                  # GameAdapter and concrete HTTP/mock adapters
├── audio/                     # new browser audio recipes, service, provider
├── hooks/                     # gameplay/event lifecycle
├── logic/                     # new pure gameplay-feedback classifier
├── screens/                   # GameplayScreen integration and CSS
├── services/                  # new dedicated audio preference storage
├── widgets/board/             # existing board helpers and rendering
└── i18n/                      # English/Ukrainian resources and parity tests

specs/002-synthesized-audio-feedback/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/audio-feedback.md
```

**Structure Decision**: Keep the browser audio implementation behind a new
`audio` boundary and keep gameplay interpretation in pure `logic`. Mount the
audio provider at the existing `App` composition boundary so the first gesture
can unlock audio from any screen without adding audio to waiting, preparation,
or results rendering. Integrate playback in the existing `[state]` diff effect
in `GameplayScreen`, alongside—not inside—the current toast/highlight logic.

The expected file set is:

- Add `frontend/src/audio/audioRecipes.ts` and
  `frontend/src/audio/audioRecipes.test.ts`.
- Add `frontend/src/audio/AudioFeedback.ts` and
  `frontend/src/audio/AudioFeedback.test.ts`.
- Add `frontend/src/audio/AudioFeedbackContext.tsx` and
  `frontend/src/audio/AudioFeedbackContext.test.tsx`.
- Add `frontend/src/logic/gameplayFeedback.ts` and
  `frontend/src/logic/gameplayFeedback.test.ts`.
- Add `frontend/src/services/AudioPreferences.ts` and
  `frontend/src/services/AudioPreferences.test.ts`.
- Modify `frontend/src/App.tsx` to mount the provider,
  `frontend/src/screens/GameplayScreen.tsx` and
  `frontend/src/screens/GameplayScreen.css` for state-diff playback and the
  accessible toggle, and both locale `screens.json` files for parity.
- Extract the existing pure sunk-ship/moat helpers from
  `frontend/src/widgets/board/Board.tsx` into the logic boundary and re-export
  them from `Board.tsx` if needed so existing board tests and imports remain
  stable. This avoids making pure logic depend on a UI module.
- Add `frontend/e2e/audio-feedback.spec.ts` only if the mock-browser journey
  needs coverage beyond the component tests; no live-only feature scenario is
  required because the server contract is unchanged.

## Contract and Data Flow Plan

### Backend/API

- Route/controller: none.
- DTO/OpenAPI: none; `ResponseGameplayStateDto` is consumed as currently
  defined and `docs/openapi.json` should remain unchanged.
- Application API: none.
- Engine: none; shot rules, sinking, moat reveal, and turn transitions remain
  server authorities.
- Persistence: none; game sessions remain in-memory and the sound preference is
  browser-local only.
- Events/SSE: none; existing push and fallback-refetch snapshots continue to
  flow through `useGameplay`.

### Frontend

- Adapter/service: no `GameAdapter` method changes. Add a browser-local
  `AudioFeedbackPort` and a dedicated `AudioPreferences` service.
- Hook/state: `AudioFeedbackProvider` owns enabled state, lazy service
  lifecycle, first-gesture unlock listeners, and best-effort visibility
  recovery. `GameplayScreen` obtains the port and invokes playback only for
  classifier results.
- Pure logic: `gameplayFeedback.ts` compares the prior and current full
  `ResponseGameplayStateDto`, classifies both boards, handles alive-ship-count
  destruction detection, and suppresses baseline, duplicate, visibility-only,
  and moat changes.
- UI: `GameplayScreen` renders a keyboard-reachable Sound button beside the
  refresh utility, with `aria-pressed` and localized `Sound on`/`Sound off`
  text. Existing board, toast, flash, tab, and result elements remain.
- Browser behavior: Vitest uses fake audio-context/port objects; mock-browser
  Playwright can install a deterministic audio-context stub before app load to
  prove gesture unlock, toggle state, no uncaught errors, and results
  navigation without requiring speakers. Existing live gameplay remains a
  regression check.

## Implementation Phases

### Phase 0: Research and Baseline

- Confirm current source/test boundaries and capture the existing worktree
  state; preserve the bridge-event log change already present.
- Record Web Audio decisions from the supplied design and MDN guidance:
  create or resume the context from a user gesture, treat autoplay rejection as
  optional feedback, use oscillator/buffer synthesis for short effects, expose
  user control, and use scheduled audio parameters for timing.
- Resolve all plan unknowns in `research.md`; no backend, dependency, or API
  alternative remains unresolved.

### Phase 1: Pure State Classifier and Recipe Contracts

- Extract/reuse sunk-ship and moat derivation in the pure logic boundary.
- Implement the `BattleOutcome` and gameplay-feedback event contract from
  `contracts/audio-feedback.md`.
- Implement and test baseline seeding, duplicate suppression, both-board cell
  diffs, fleet-board sink detection, target-board alive-ship-count detection,
  visibility-only final-state changes, moat suppression, and batched incoming
  shots.
- Implement deterministic tunable recipe constants for the three selected
  sound identities and test durations/components without browser output.

### Phase 2: Browser Audio Service and Preference Provider

- Implement the lazy single-context service with a conservative master gain,
  scheduled oscillator/noise nodes, deterministic in-memory noise buffers,
  safe no-op behavior, resume/cleanup handling, and no leaked unhandled
  promises.
- Implement dedicated preference load/save with enabled-by-default behavior
  and a key not touched by `clearGameData`.
- Mount the provider at the app root; install and remove first pointer/keyboard
  gesture listeners and best-effort visibility recovery.
- Add fake-context/provider/preference tests for enabled state, persistence,
  gesture-only unlock, rejected resume, unavailable audio, and unmount cleanup.

### Phase 3: Gameplay Integration, Localization, and Browser Evidence

- Replace the screen's player-field-only baseline with a full gameplay-state
  baseline while preserving existing turn-switch timing, toasts, and board
  highlights.
- Call the audio port from classifier events; do not trigger playback from
  `handleShot`, preventing duplicate local feedback and covering opponent
  updates uniformly.
- Add the localized accessible toggle and style it within the existing
  gameplay utility layout without changing board breakpoints.
- Add English/Ukrainian keys and retain the existing parity test.
- Extend `GameplayScreen.test.tsx` or a focused screen test with fake-port
  assertions for local and incoming outcomes, no initial/duplicate/moat
  playback, toggle persistence, and results navigation.
- Add a mock-browser journey if needed to verify real composition, gesture
  unlocking, disabled/enabled state, and no audio failure breaking gameplay.

### Phase 4: Verification and Close

- Run focused frontend tests, lint, and build with the repository's pinned
  `frontend/node` runtime.
- Run mock-browser E2E and existing live packaged-JAR regression.
- Run `scripts/verify.sh`; inspect `docs/openapi.json` and all generated/build
  differences even though no API change is expected.
- Update feature evidence and relevant product/architecture documentation only
  if maintained docs describe gameplay feedback or browser preferences.

## Verification Plan

| Concern | Command/evidence |
|---|---|
| Pure classifier and recipe tests | `npm --prefix frontend run test -- src/logic/gameplayFeedback.test.ts src/audio/audioRecipes.test.ts` |
| Audio service and provider tests | `npm --prefix frontend run test -- src/audio/AudioFeedback.test.ts src/audio/AudioFeedbackContext.test.tsx src/services/AudioPreferences.test.ts` |
| Gameplay component regression | `npm --prefix frontend run test -- src/screens/GameplayScreen.test.tsx` |
| Locale parity | `npm --prefix frontend run test -- src/i18n/keyParity.test.ts` |
| Frontend lint/build | `npm --prefix frontend run lint` and `npm --prefix frontend run build` |
| Mock browser | `npm --prefix frontend run test:e2e -- audio-feedback.spec.ts` if the planned journey is added; otherwise the existing suite |
| Live packaged JAR | `npm --prefix frontend run test:e2e:live` after packaging, using the existing live full-game regression |
| Full gate | `scripts/verify.sh` |
| Agent configuration | `python3 scripts/sync-agent-files.py --check` when configuration is touched |
| API artifact | `git diff -- docs/openapi.json` after Maven verification; expected empty |

## Complexity and Risk Factors

- Web Audio APIs differ across browsers and can be absent, suspended, or
  blocked by activation policy; the service must isolate all of those failures
  from React state and navigation.
- StrictMode and route changes can mount/unmount providers and screens more
  than once in development; event listeners, context ownership, subscriptions,
  and scheduled nodes require idempotent cleanup.
- `GameplayScreen` currently combines state diffing, toasts, highlights, and
  turn-driven tab switching in one effect. The classifier integration must
  preserve its existing timing and avoid reading a stale previous snapshot.
- The target board intentionally hides unshot ship data. Destruction detection
  must use the existing alive-ship count rather than applying full-fleet sunk
  derivation to that board.
- The repository's browser tests run under jsdom or Playwright without a real
  speaker; fake ports/contexts must prove scheduling and failure behavior while
  browser journeys prove only user-visible outcomes and resilience.
- Maven owns the pinned Node/npm runtime for the full gate; host Node results
  are not authoritative.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| None | The feature uses existing frontend boundaries and browser capabilities. | No new backend layer, dependency, API, persistence store, or workflow exception is required. |
