# Quickstart: Synthesized Audio Feedback

**Feature**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

This guide validates the feature after implementation. It assumes commands are
run from the repository root and that the repository's pinned frontend runtime
has been installed by Maven or is available at `frontend/node`.

## Prerequisites

- Node/npm from `frontend/node` when running frontend commands directly.
- No backend or external audio service.
- A browser capable of running the existing mock Vite suite; actual speakers
  are not required for deterministic tests.

## Focused validation

Run the pure classifier and recipe tests with the pinned runtime:

```bash
PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- \
  src/logic/gameplayFeedback.test.ts \
  src/audio/audioRecipes.test.ts
```

Expected results:

- The first snapshot produces no events.
- New fleet-board MISS/HIT/DESTROYED cells classify correctly.
- Target-board MISS/HIT/DESTROYED classification uses visibility and alive-ship
  count rules.
- Duplicate snapshots, final-state visibility-only changes, and moat cells
  produce no false events.
- All three recipes and their fake-node adapter scheduling satisfy the complete
  normative FR-001 contract, including required components, tolerances,
  deterministic generation, peak gain, and cleanup.

Run service, provider, preference, screen, and locale tests:

```bash
PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test -- \
  src/audio/AudioFeedback.test.ts \
  src/audio/AudioFeedbackContext.test.tsx \
  src/services/AudioPreferences.test.ts \
  src/screens/GameplayScreen.test.tsx \
  src/i18n/keyParity.test.ts
```

Expected results:

- A missing preference loads enabled; toggling persists and survives
  `clearGameData`.
- First pointer/keyboard interaction unlocks silently; rejected or missing
  audio is a safe no-op with no unhandled rejection.
- Gameplay requests audio from state diffs, not `handleShot`, and retains
  existing toasts, highlights, board tabs, and results navigation.
- English and Ukrainian Sound labels have matching keys.

## Mock-browser validation

Run the mandatory `frontend/e2e/audio-feedback.spec.ts` journey with the
existing mock server:

```bash
PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test:e2e -- \
  audio-feedback.spec.ts
```

The journey should install a deterministic audio-context stub before app load,
reach gameplay through the existing mock flow, verify the localized toggle and
pressed state, exercise a user gesture, and confirm that disabling sound and
simulating audio failure do not block gameplay or results navigation. It must
not assert that a physical speaker emitted sound.

Run the existing mock suite as a regression:

```bash
PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run test:e2e
```

## Build and repository gate

```bash
PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run lint
PATH="$PWD/frontend/node:$PATH" npm --prefix frontend run build
scripts/verify.sh
```

Expected artifact evidence:

- Frontend build succeeds and contains no audio file or new runtime audio
  package.
- `docs/openapi.json` is unchanged because no backend/API contract changed.
- Existing live packaged-JAR gameplay-to-results regression remains green.
- Any Java 25, browser binding, or audio-capability limitation is reported
  separately from product behavior and does not get called a feature pass.
