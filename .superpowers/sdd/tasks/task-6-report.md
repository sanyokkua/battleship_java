# T006 implementation report

## Status

Implemented T006 on `feature/003-frontend--t006`, based on `feature/003-frontend` at `2c28a15bc3719f9fdfe9b21a80f0654265f34d7b`. The Spec Kit task remains unchecked and the existing handoff remains `executing`.

## Delivered

- Added the History API route matcher and navigation helper for Home, Create, Join, Game, and fallback paths.
- Added English and Ukrainian catalogs, locale-aware translator with raw ruleset/ship ID fallback, and the document language update.
- Added safe local-storage gateway and validated preferences with the approved defaults and key.
- Added a `get`/`subscribe` preference store and a theme controller that applies dark/light/system themes and follows OS color-scheme changes.
- Added the persistent app shell, header title/refresh slots, Home screen, Settings dialog, reusable native Dialog, Button and Card components, design tokens, and base/focus/reduced-motion/forced-colors styling.
- Added router, translation parity/fallback, preference, and system-theme unit proofs plus a real-backend Playwright Settings journey covering language/theme persistence, live system theme changes, accent token values, modal focus containment/Escape/focus return, and Home control touch sizing.
- Corrected the three feature artifacts for the task boundary: `contracts/ui-surface.md`, `plan.md`, and `tasks.md`.

## TDD and verification evidence

- Initial RED: the required focused Vitest run failed all three suites because the T006 router, catalogs, and preference modules did not exist yet.
- GREEN focused proofs: `npm test -- tests/router.test.ts tests/i18nParity.test.ts tests/preferences.test.ts` — 3 files, 7 tests passed.
- Full frontend unit suite: `npm test` — 6 files, 14 tests passed.
- Build: `npm run build` — TypeScript and Vite production build passed.
- Formatting/lint: `npm run format:check` and `npm run lint` — both passed.
- Required combined Verify command: `npm test -- tests/router.test.ts tests/i18nParity.test.ts tests/preferences.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts` — passed; Playwright Settings journey passed against the packaged backend via the T005 stack fixture.
- `git diff --check` — passed.

## Mutation checks

- Removed `settings.system` from `uk.ts`: `i18nParity.test.ts` failed because English and Ukrainian key lists differed.
- Changed unknown ruleset/ship fallback to return `''`: `i18nParity.test.ts` failed with expected `unlisted-ruleset`, received empty string.
- Changed system theme resolution to always dark: `preferences.test.ts` failed with expected light, received dark.
- Reduced `--control-h` to `2.5rem`: rebuilt preview and ran the real-backend Settings E2E; the Home control guard failed with expected `>= 44`, received `40`.
- All mutations were restored before final verification.

## Concerns

- The first local Playwright launch was blocked by the sandbox's Chromium macOS bootstrap-port restriction. Running the required browser checks with approved process access succeeded.
- The task does not add T007's Game rules dialog/wiring, T008's Toasts/Pill/Stepper/Meter, or ErrorBoundary behavior.

## Independent review follow-up

- Fresh-context review found malformed percent-encoded route IDs could throw during rendering. Added regression cases for `/join/%` and `/games/%E0%A4%A`; `getRoute` now returns Home when an ID cannot be decoded.
- RED evidence: `npm test -- tests/router.test.ts` failed with `URIError: URI malformed` before the fix.
- GREEN/recheck: `npm test -- tests/router.test.ts`, `npm run build`, `npm run lint`, and `npm run format:check` all passed after the fix.
