# T007 implementation report

## Result

Implemented the US1 host-create slice: typed gateway operations, CSRF refresh/retry, response/problem decoding,
monotonic snapshot store, current-game persistence, Home/Create/Game routes, shared server-backed rules dialog, and
the real-backend create journey. No contract-generated file or dependency pin was edited. R19's action slot remains
owned by T012.

## Test-driven evidence

- `decode.test.ts`: RED failed to resolve the missing `src/gateway/decode` module; after the decoder was added, 4/4
  assertions passed (HTML error, truncated JSON, missing required key, valid body with unknown action dropped).
- `snapshotGate.test.ts`: RED failed to resolve the missing session gate; after implementation, 4/4 passed (higher,
  equal merge including invitation fields, clearing omitted invitation fields, lower snapshot drop).
- `gameStore.test.ts`: RED failed to resolve the missing store; initial GREEN attempt exposed a one-millisecond
  expectation race between two `Date.now()` calls. The test was corrected to assert the captured time interval;
  1/1 then passed. The production clock offset remains `serverTime - Date.now()`.
- The create E2E was added before the screens. Its initial run exposed that the test did not request the `stack`
  fixture, leaving port 4173 unserved. The test now uses the fixture's `stack.origin`; after the app was implemented,
  the create and rules journeys passed against packaged backends.
- Final review found a race where Create could be submitted before `listRulesets` selected a ruleset. The new
  `create stays disabled until the server advertises a selectable ruleset` E2E failed first with
  `Expected: disabled; Received: enabled`; adding `!selected` to the button and submit guard made it pass.

## Verification

Final frontend checks were run on the final code:

- `npm run lint` — no issues.
- `npm run typecheck` — passed.
- `npm run build` — passed (Vite production build, 55 modules transformed).
- `npm test` — 9 files, 23 tests passed.
- `npm run format:check` — all files matched Prettier.
- `npm run e2e -- e2e/create.spec.ts` — 11/11 passed using the real packaged backend, including server-backed rules,
  one-submit behavior, reload/remembered-game behavior, 409 refusal, lost answer handling, refresh, CSRF refresh and
  resend, and real 429 wait disabling.
- Manual browser check through the in-app browser on the packaged local stack — Home → Create showed both server
  rulesets and facts; creation navigated to the server's WAITING snapshot and showed the invitation link; the shared
  Game rules dialog showed facts from both advertised rulesets.
- `git diff --check` — clean.

## Named mutation proof

Each mutant was restored after the failing check; the app was rebuilt before preview-based E2E mutations so the test
ran against the mutant bundle.

1. Removed pending submit disabling. Targeted create E2E failed with `Expected: disabled; Received: enabled` at
   `expect(submit).toBeDisabled()`.
2. Removed wait-hint disabling. Real 429 E2E failed with `Expected: disabled; Received: enabled` at the disabled
   create-button assertion.
3. Removed the post-403 retry. CSRF E2E failed because it stayed at `/create` instead of navigating to `/games/...`.
4. Changed equal-version gate to replace the snapshot. Snapshot unit failed because the next board (`SHIP`) replaced
   the existing board (`WATER`).
5. Renamed `/api/v1/games/{gameId}` in a scratch OpenAPI edit. `npm run typecheck` failed at `gameGateway.ts` because
   `"/api/v1/games/{gameId}"` no longer matched the generated paths. The OpenAPI file was restored and generated
   client types refreshed.

## Files

- Added gateway client/CSRF/decode/problem/game operations in `frontend/src/gateway/`.
- Added per-game store, hook, snapshot gate and current-game storage in `frontend/src/session/`.
- Added Home, Create, GameRoute, RulesDialog and SessionUnavailable screens; ruleset card/facts components and CSS.
- Extended router/history state, shared Header/Shell rules action, translations and base styling.
- Added decoder/store/snapshot tests and `frontend/e2e/create.spec.ts`.
- Marked T007 complete in `specs/003-frontend/tasks.md`; preserved the task decomposition edits already present on
  this task branch.

## Self-review and concerns

- Gateway calls use `openapi-fetch` with generated path/schema types and same-origin credentials. No fake API/server,
  handwritten wire schema, action slot, or new package was added.
- Unknown create outcomes are not retried. The only retry is one resend after an explicit
  `request-security-rejected`, after refreshing metadata/CSRF.
- The game route deliberately renders phase placeholders; later tasks own full Waiting/Placement/Gameplay screens and
  streaming. The route does not implement R19 controls.
- The task description asks for Home/Create and Rules dialog; Home is implemented as its own screen component, and the
  shared native `<dialog>` receives rules from the gateway each time it opens.

## Commit

Recorded after final verification on `feature/003-frontend--t007`.

## Review follow-up

Addressed the fresh T007 review findings in a separate follow-up commit:

- `GameRoute` starts meta, ruleset, and game reads together so a supplemental read failure cannot suppress the game
  retrieval. It marks a failed game read as offline/reconnecting while keeping the last accepted snapshot visible;
  supplemental-only failures mark reconnecting.
- Removed the invitation URL input from this T007 placeholder route. The invitation value is therefore absent from the
  DOM until T008 provides its redacted sharing flow.
- `getGame` clears remembered-game storage for 401/404/410 only when the failed id matches the stored id.
- Removed App-level consumption and display of `{toast: 'game-created'}`; T008 owns the one-time toast behavior.
- 429 now shows the server-provided numeric retry duration using the active locale.
- Expanded real-backend E2E assertions: ruleset cards and both dialogs compare all five facts of every advertised
  ruleset against the live API response; history/toast ownership and secret URL DOM exposure are checked; route
  supplemental failures, prior-snapshot transport failure, wrong-game 404 persistence, and numeric 429 wait are
  covered.
- Updated `tsconfig.node.json` to include only the generated contract types and locale source files used by E2E.

### Test-driven follow-up evidence

The new E2E assertions were run against the pre-fix branch before implementation. RED evidence: App consumed the
created history signal; 429 rendered no numeric wait; GameRoute did not issue `getGame` after metadata/ruleset failure;
transport failure had no connection status; and a 404 for a different game cleared the active remembered id. The
all-ruleset five-fact card/dialog assertions passed before UI changes, confirming the existing rules-data rendering was
already correct. With the fixes, full `e2e/create.spec.ts` passed 14/14 against the real packaged backend.

### Follow-up verification

- `npm run format:check` — passed.
- `npm run typecheck` — passed (including regenerated contract types and the E2E project).
- `npm run lint` — no issues.
- `npm test` — 9 files, 23 tests passed.
- `npm run build` — passed.
- `npm run e2e -- e2e/create.spec.ts` — 14/14 passed against the real packaged backend. The first sandboxed browser
  attempt failed at Chromium launch due to macOS Mach rendezvous permissions before tests began; rerunning with browser
  launch permission completed successfully.
- `git diff --check` — passed.

The original five named mutation proofs above were completed and their failing outputs captured before this follow-up;
the follow-up retains their target behaviors and adds direct E2E proof for the review corrections. No dependency pins,
generated sources, fake backend, hand-written wire types, or T008-owned toast behavior were introduced.

## Fresh review follow-up

Applied two additional corrections in another separate commit:

- The game route now reports `reconnecting` for network/unreadable failures and HTTP 429/502/503 while
  `navigator.onLine` is true. It reports `offline` when the browser's offline signal is false.
- E2E now checks both labels and values for the five advertised ruleset facts in the selectable cards and dialogs,
  including the global rules dialog opened from an actual game route.
- Corrected the T007 mutation references to the current proof numbering.

TDD evidence: before the route fix, the online transport failure test failed waiting for the reconnecting message;
rules-card and real-game-route dialog server comparisons passed. After the fix, the full T007 E2E passed 14/14.

Verification for this follow-up: `npm run format:check`, `npm run typecheck`, `npm run lint`,
`npm test -- tests/decode.test.ts tests/snapshotGate.test.ts` (8 passed), `npm run build`, and the exact T007
`npm run e2e -- e2e/create.spec.ts` (14 passed against the packaged backend). `git diff --check` passed.

## Unreadable problem-response follow-up

Added a strict problem decoder at the gateway boundary. A non-2xx response must use `application/problem+json` and
contain the contract-required title, matching status, known code, and a valid optional retry duration. Invalid or HTML
error responses now throw `TransportFailure`; Create handles that as an unknown outcome and does not retry. The
decoder regression was red first (`decodeProblem(502, '<html>Bad gateway</html>', 'text/html')` returned a definite
fallback problem), then passed after the strict validation was added. T007's pending-submit mutation reference now
points to proof (4).

I did not add an E2E that fabricates an HTML 502 response: repository rules forbid `route.fulfill` and fake backends in
E2E. The existing real-backend `route.fetch()` then `route.abort()` journey continues to verify unknown outcome and no
resend for an actually lost create answer; the HTML problem body is covered at the pure decoder boundary.

Verification for this follow-up: `npm run format:check`, `npm run typecheck`, `npm run lint`,
`npm test -- tests/decode.test.ts tests/snapshotGate.test.ts` (9 passed), `npm run build`, and
`npm run e2e -- e2e/create.spec.ts` (14 passed against the packaged backend). `git diff --check` passed.

## Forward-compatible problem code review follow-up

Kept strict validation of problem media type and required/matching fields, but stopped rejecting codes unknown to the
current generated message map. Unknown nonempty codes now flow to the existing HTTP status fallback, preserving
forward compatibility. TDD RED: a well-formed 503 problem with code `future-service-failure` threw `TransportFailure`.
GREEN: the decoder returns the localized 503 fallback, and the HTML 502 rejection still passes.

Verification: formatting, typecheck, lint, targeted decode/snapshot tests (10 passed), build, and packaged-backend
T007 E2E (14 passed).
