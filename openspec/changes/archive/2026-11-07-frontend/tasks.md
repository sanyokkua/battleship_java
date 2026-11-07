# Tasks

Converted from `the original 003-frontend artifacts` (36 tasks T001-T036; checked state preserved: 34 done, T024 and T036 open). Each task keeps its Covers, Proof, Verify, Mutation and Depends-on lines; task ids and requirement ids are unchanged so coverage can be grepped.


## 1. Phase 1: Contract and backend fixes (R57)

- [x] T001 [P] Add the `browser-game-limit` 409 refusal in `contracts/openapi.yaml` and `backend/application/src/main/java/ua/kostenko/battleship/application/registry/SessionRegistry.java`
  - **Covers** R07, R57 · research D14 · decision B2
  - **Proof** `CapacityIT`: (1) `aBrowserAtItsLiveGameCapIsRefusedOnCreateAndOnJoin` expects 409 `browser-game-limit`, no `Retry-After`; (2) games/streams ceilings still 503 + `Retry-After`; header comment (L28-29) reworded. `WireConformanceIT`: (3) `conflictCodes()` (~L610) gains `browser-game-limit` and `conflict(code)` a driver (one browser creates twice); (4) `everyEmbeddedExampleConformsToItsSchema` covers the new example.
  - **Verify** `cd contracts && npm ci && npm run check` · `cd backend && ./mvnw -q verify && ./mvnw spotless:check`
  - **Mutation** Map `BrowserGameLimitException` back to `service-unavailable` in `CreateGameUseCase`; assertion (1) must fail.
  - **Depends on** —

- [x] T002 Stop the forked test JVM lingering 30 s after `verify` in `backend/`
  - **Covers** R57 · spec Assumption 5
  - **Proof** (1) a clean `verify` writes no dumpstream containing "kill self fork JVM"; (2) `GracefulShutdownIT` still passes unchanged.
  - **Verify** `cd backend && rm -rf app/target/failsafe-reports && ./mvnw -q verify && ! grep -l 'kill self fork JVM' app/target/failsafe-reports/*.dumpstream 2>/dev/null`
  - **Mutation** Revert the fix; the grep must find the line again.
  - **Depends on** T001 (same reactor; avoids concurrent `verify`)

- [x] T003 Remove Spring Boot Actuator from the public port in `backend/app/pom.xml`
  - **Covers** R57, R48 (health is the contract operation) · plan.md fix 3
  - **Proof** `PackagedArtifactIT`: (1) `GET /actuator/health` on the packaged JAR → 404; (2) `/api/v1/health` still `ready`.
  - **Verify** `cd backend && ./mvnw -q verify -Dit.test=PackagedArtifactIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false && ./mvnw spotless:check`
  - **Mutation** Re-add the dependency; assertion (1) must fail.
  - **Depends on** T002


## 2. Phase 2: Foundation (blocks every story)

- [x] T004 [P] Scaffold `frontend/`, type generation and `scripts/install.sh`
  - **Covers** R45, R47 (no dev address), R56 · research D1, D6 · plan § *Stack*, § *Gateway* (generation)
  - **Proof** (1) `./scripts/install.sh` exits 0 on this machine; (2) `npm run check` passes on the empty app (generation runs first; `api.ts` exists and is ignored by git); (3) `check-dist.mjs` fails on a `dist/` file containing `http://localhost:8080`.
  - **Verify** `./scripts/install.sh && (cd frontend && npm run check)`
  - **Mutation** Put a `PATH` shim first whose `node -p process.versions.node` prints `23.0.0`; `install.sh` must exit non-zero naming the runtime step. Remove the `localhost:` pattern from `check-dist.mjs`; (3) must fail.
  - **Depends on** — (parallel with T001; T005 needs T001's contract for generated `ProblemCode`)

- [x] T005 Build the stack orchestrator, dev/prod commands and the Playwright stack fixture in `scripts/stack.mjs`
  - **Covers** R46, R47, R48, R50, R51 · S7 (dev and prod commands start) · research D10, D11, D12 · decision B3 · US8
  - **Proof** `smoke.spec.ts`: (1) page at `http://localhost:4173/` renders; `page.request.get('/api/v1/health')` → `ready: true` through the proxy; (2) an `EventSource` opened via the proxy for a created game receives its first `snapshot` event (created with `page.request` after `getMeta`); (3) `GET /games/abc` and `/join/abc` reload return the app's `index.html`; (4) `startStack` on an occupied port rejects with a message containing `frontend`, the address and `lsof`; (5) unit `tests/redact.test.ts`: `redact()` removes `Set-Cookie`/`Cookie` values, `#invite=…` and `XSRF-TOKEN=…` from synthetic lines; (6) the CLI itself: `FRONTEND_PORT=5174 BACKEND_PORT=8084 node scripts/stack.mjs dev` and `FRONTEND_PORT=4177 BACKEND_PORT=8085 node scripts/stack.mjs prod` are each spawned as a child process; once it prints ready, a page at its origin gets `ready: true` from `/api/v1/health` and receives the first `snapshot` event of a created game through the proxy; SIGINT stops both children (ports free again).
  - **Verify** `cd backend && ./mvnw -q -DskipTests package && cd ../frontend && npm test -- tests/redact.test.ts && npm run build && npm run e2e -- e2e/smoke.spec.ts`
  - **Mutation** Remove the `preview.proxy` block; assertion (1) must fail. Remove the `server.proxy` block; (6) must fail for `dev`. Remove the `#invite=` rule from `redact()`; (5) must fail.
  - **Depends on** T003, T004

- [x] T006 Build the app shell, design tokens and shared components: router, header, i18n, preferences, theme, dialog and settings in `frontend/src/app/`
  - **Covers** R33, R34, R35, R38 (header and Settings), R41 (dialog primitive), R58, R61 (controls) · research D4, D5, D17 · S5 (partial)
  - **Proof** Units: (1) `en` and `uk` key sets equal; (2) unknown ruleset and ship identifiers fall back to their raw ids; (3) preferences round-trip and fall back on corrupt JSON and on `localStorage` throwing; (4) router maps the four routes and fallback correctly, and `navigate()` responds to `popstate`. `settings.spec.ts`: (5) switching to Ukrainian and light theme applies without reload and survives reload; (6) theme System with `emulateMedia({colorScheme:'light'})` then `'dark'` flips `html[data-theme]` without reload; (7) `getComputedStyle(document.documentElement).getPropertyValue('--accent')` equals the mockup's value per theme (`#3becb0` dark, `#1d7fa8` light); (8) a Home control has a minimum height of `2.75rem` and `touch-action: manipulation`; (9) Settings dialog is labelled, traps focus, closes on Escape, and returns focus to its opener.
  - **Verify** `cd frontend && npm test -- tests/router.test.ts tests/i18nParity.test.ts tests/preferences.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts`
  - **Mutation** Delete one key from `uk.ts`; (1) must fail. Return an empty string for unknown ruleset or ship ids; (2) must fail. Make `system` resolve to `dark` unconditionally; (6) must fail. Reduce the control height below `2.75rem` or remove `touch-action: manipulation`; (8) must fail.
  - **Depends on** T005


## 3. Phase 3: US1 — Host creates and shares (P1) 🎯 MVP

- [x] T007 [US1] Build the gateway, session store, the Home/Create screens and the Rules dialog in `frontend/src/gateway/`
  - **Covers** R01, R02, R03, R04, R05, R07, R13, R22, R23, R37, R38 (Home and Rules dialog), R56 · US1 scenarios 1–2 · research D2, D3, D6 · decision B1
  - **Proof** Units: (1) `decode` rejects HTML 502 body, truncated JSON, a JSON object missing `version`; accepts one with all required keys; (2) `snapshotGate`: higher replaces, equal keeps `version`/board but takes `expiresAt`, `serverTime`, `invitationUrl`, `invitationExpiresAt`, lower is dropped. `create.spec.ts`: (3) each advertised ruleset's aggregated fleet badges and the Details dialog values equal the server `listRulesets` response; Home's shared rules dialog and the global dialog from a real game route show labels and values matching the server response; (4) double-click on submit sends exactly one `POST /api/v1/games`; (5) game route loads from server response; reload rebuilds it; Home after closing/reopening the tab offers Return to your game; (6) deliberate second create receives one `browser-game-limit` refusal, displays its translated message, and sends no automatic retry; (7) a hidden create answer shows "outcome unknown" and sends no second `POST`; (8) header Refresh sends one `GET /api/v1/games/<id>`; (9) after removing `XSRF-TOKEN`, request log shows one rejected POST, one refreshed `GET /api/v1/meta`, and one successful resent POST; (10) real backend 429 displays its numeric retry wait and keeps submit disabled; (11) history retains `{toast: 'game-created'}` for T008, App does not consume/display it, and invitation secret URLs do not appear in DOM; (12) with meta/ruleset reads aborted, GameRoute still fetches the game; with a failed refresh it visibly marks connection unavailable while retaining the last snapshot; (13) a 404 for another game ID does not clear the remembered active game; (14) `decodeProblem` rejects HTML 502 so create handles an unreadable error as unknown, while live `route.fetch()` then `abort()` proves a lost answer is never resent.
  - **Verify** `cd frontend && npm test -- tests/decode.test.ts tests/snapshotGate.test.ts && npm run build && npm run e2e -- e2e/create.spec.ts`
  - **Mutation** Remove the submit `disabled` while pending; (4) must fail. Remove the wait-hint disablement; (10) must fail. Drop the resend after `request-security-rejected`; (9) must fail. Make `accept` replace on equal version; (2) must fail. Rename the `/api/v1/games/{gameId}` path in a scratch edit of `contracts/openapi.yaml` and run `npm run typecheck`; it must fail (revert the scratch edit).
  - **Depends on** T001, T006

- [x] T008 [US1] Build the waiting room with copy, QR and replace invitation, and amend `GameSnapshot.version` in `frontend/src/screens/WaitingRoom.tsx`
  - **Covers** R11, R12, R14, R16 (presence timer), R24 (stream), R38 (toasts), R59 (waiting room), US1 scenarios 3–5 · research D7, D16 · plan fix 4
  - **Proof** Units: (1) `Qr` renders a canvas for a given string with its `aria-label`; (2) Toasts use the polite live region, `status`/`alert` roles, close immediately on ×, and auto-dismiss after 6.5 s; (3) an `EventSource` error in `CONNECTING` sets the game connection to `reconnecting`; (4) an unknown `closed` reason leaves the connection offline without claiming "Open in another tab". `waiting.spec.ts` (default stack): (5) the "Game created" toast appears with `role="status"` on arrival from Create; dismiss it, reload, and confirm it does not reappear; (6) clipboard (granted permission) equals the snapshot's `invitationUrl`, and `document.documentElement.outerHTML` (including input values) does not contain the invitation secret; (7) QR dialog opens labelled and closes on Escape; (8) Replace → confirm → clipboard holds a different link; the old link's join is refused; (9) after a guest joins via `page.request`, the host reaches placement without reload; (10) for each server-advertised ruleset created in a fresh browser context, the waiting room's five `RulesetFacts` labels and values equal that ruleset's fields from `listRulesets`; (11) the exact waiting notice is visible with `role="status"`; (12) a real-backend `REPLACED` close event displays "Open in another tab", and a subsequent request log has no second events connection. Presence stack (`BATTLESHIP_PRESENCEINTERVALSECONDS=1`): (13) a visible waiting-room host with `SEND_PRESENCE` allowed sends presence after the interval; hiding the page prevents another request and restoring visibility restarts the timer without sending immediately. Short-lifetime stack (`BATTLESHIP_INVITATIONLIFETIMESECONDS=10`): (14) "invitation expired" appears without reload; Replace restores sharing; (15) the stepper marks Waiting with `aria-current="step"` and moves to Placement after the guest joins; the lifetime meter's value falls between two reads. `contracts npm run check` passes.
  - **Verify** `cd contracts && npm run check && cd ../frontend && npm test -- tests/qr.test.tsx tests/toasts.test.tsx tests/eventStream.test.ts && npm run build && npm run e2e -- e2e/waiting.spec.ts`
  - **Mutation** Remove the scheduled `getGame` at `invitationExpiresAt`; (14) must fail. Disable presence scheduling or let the timer fire while hidden; (13) must fail. Put the raw `invitationUrl` in the link field's `value`; (6) must fail. Omit the waiting room's shared `RulesetFacts` or skip selecting each advertised ruleset; (10) must fail. Remove the waiting status notice; (11) must fail. Omit the `CONNECTING` state update; (3) must fail. Treat `closed`/`REPLACED` as Offline or reopen its stream; (12) must fail. Treat an unknown closed reason as REPLACED; (4) must fail.
  - **Depends on** T007


## 4. Phase 4: US2 — Guest joins (P1)

- [x] T009 [US2] Strip the invitation fragment before render and join explicitly in `frontend/src/main.tsx`
  - **Covers** R07 (join path), R08, R09, R10, R15, R27 (join 410), R51, US2 · S2
  - **Proof** `invitation.spec.ts`: (1) an `addInitScript` MutationObserver on `#root` records `location.hash` at the first rendered node — it is empty; after navigating to `/` and `page.goBack()`, the URL still lacks the secret; (2) no `POST …/join` is sent before clicking Join; (3) two guests open the same link and both click Join: exactly one reaches placement, the other sees "invitation unavailable"; (4) host reaches placement live; (5) the secret string is absent from `localStorage`, `sessionStorage`, captured console messages and `e2e/.logs/default.log`; (6) a guest context that already hosts its own live game opens a fresh link and clicks Join: it shows the `browser-game-limit` message with Return to your game, and sends exactly one `POST …/join`; (7) short-lifetime stack 4174/8081 (`IDLETIMEOUTSECONDS=8`, `SWEEPINTERVALSECONDS=1`, `RESULTRETENTIONSECONDS=60`): a guest joins, both pages are hidden (visibility override, Conventions) until the game expires, then the guest reopens the same link and clicks Join — the answer is `410 game-expired` and the page shows Session unavailable "game expired", not "invitation unavailable".
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts`
  - **Mutation** Move `replaceState` into the Join component's effect; (1) must fail. Auto-join on load; (2) must fail. Map every join `409` to "invitation unavailable"; (6) must fail. Map join `410` to "invitation unavailable"; (7) must fail.
  - **Depends on** T008


## 5. Phase 5: US3 — Fleet placement (P1)

- [x] T010 [US3] Build the placement board and anchored ship menus in `frontend/src/screens/Placement.tsx`
  - **Covers** R01 (display counts), R02, R17, R19, R20, R31, R40 (cells), R59, R62, US3 scenarios 1–3
  - **Proof** Real-backend placement journeys cover both rulesets, edge anchors, overlapping and no-contact cases, exhausted sizes, exact moving ship identity when Classic ships touch, legal and illegal rotations, move cancel/failure, removal/clear and readiness. Check popup focus, dismissal and viewport edges; board and counts follow accepted snapshots. Supplement with focused advisory-check tests if useful.
  - **Verify** `cd contracts && npm run check`; `cd backend && ./mvnw -q verify && ./mvnw spotless:check`; `cd frontend && npm run build && npm run e2e -- e2e/placement.spec.ts`; final `./scripts/verify.sh`
  - **Mutation** Remove exclusion of the moving ship by identity; the assertion that the popup offers a legal direction into its vacated footprint must fail. Permit an invalid direction; the corresponding popup assertion must fail. Record failure outputs and restore each mutation.
  - **Depends on** T009

- [x] T011 [US3] Add mouse drag and touch ship selection in `frontend/src/screens/Placement.tsx`
  - **Covers** R18, R61, US3 scenario 4 · research D19 · decision B4
  - **Proof** Real-backend placement journey covers touch scrolling and tap selection, mouse dragging, keyboard operation, single-cell placement, legal direction filtering, no-fit recovery, and progress/count updates after placement, move, remove and clear.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/placement.spec.ts`
  - **Mutation** Start a drag on touch scroll; touch-scroll assertion must fail. Omit direction filtering on drop; no-invalid-direction assertion must fail. Record failure outputs and restore each mutation.
  - **Depends on** T010


## 6. Phase 6: US4 — Play and results (P1)

- [x] T012 [US4] Build gameplay with live updates, announcements and lost-answer retry in `frontend/src/screens/Gameplay.tsx`
  - **Covers** R01 (display counts), R19, R20, R21, R24, R31, R59, US4 scenarios 1–3, US5 scenario 6 · S3 (lost answer) · research D13
  - **Proof** `play.spec.ts`: (1) Sea Battle with the opponent's fleet placed at fixed anchors (Conventions): a hit keeps the turn; sinking a `ship-1` renders its surroundings as `REVEALED_WATER`, distinct from `MISS`; (2) Classic: a hit passes the turn; (3) the opponent's shot updates the player's board and announcement without reload; (4) action race (Classic, so any shot passes the turn): install `page.route('**/events', r => r.abort())` before the player's page opens the game, so its view comes from `getGame` only; when that view shows the player's turn, send one `FIRE` at an `UNKNOWN` cell through `page.request` (same session, own `commandId`, `X-XSRF-TOKEN` from the cookie) and, as soon as it answers, click a different `UNKNOWN` cell in the stale view — the page's `FIRE` gets `409 action-not-allowed`, the worded reason is shown, the board refreshes to `getGame`, and `getGame` shows exactly one new shot; (5) lost answer: `page.route('**/commands', r => r.fetch().then(() => r.abort()))` once, page shows checking; Retry's request body has the same `commandId`; the board shows the shot applied once (`getGame` via `page.request` shows one `MISS`/`HIT` at that cell); (6) after a hit, both player cards' ships-left and ship-cells-left values equal those derived from `getGame` via `page.request`.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/play.spec.ts`
  - **Mutation** Generate a new `commandId` on Retry; (5) must fail (second shot refused as `target-already-fired` or a different cell state).
  - **Depends on** T011

- [x] T013 [US4] Build the results screen and play one full game per ruleset in `frontend/src/screens/Results.tsx`
  - **Covers** R30, R36, R59, US4 scenarios 4–5 · S1
  - **Proof** `results.spec.ts` per ruleset: (1) both players reach results; winner/loser shown; (2) each stat-card row and fleet tile shown equals its `getGame` `statistics` field read via `page.request`; (3) Leave results returns Home and `getGame` then answers 404.
  - **Verify** `cd frontend && npm run check && npm run e2e -- e2e/results.spec.ts e2e/accessibility.spec.ts`
  - **Mutation** Compute accuracy locally as hits/shots rounded differently; (2) must fail.
  - **Depends on** T012


## 7. Phase 7: US5 — Leaving, expiry, restart, connection loss (P2)

- [x] T014 [US5] Implement leave, resign and the abandoned view in `frontend/src/screens/LeaveControl.tsx`
  - **Covers** R28, R29, R30, US5 scenarios 1–3
  - **Proof** `leaving.spec.ts`: (1) cancel keeps placement; confirm → Home; (2) remaining player sees abandoned without reload, no boards; (3) Settings → Resign match in play → opponent's results show a win by `RESIGNATION`; Resign match is absent from Settings outside play; (4) lost leave answer: `page.route('**/leave', r => r.fetch().then(() => r.abort()))` once; the page's retry gets 404 and lands Home, not Session unavailable.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/leaving.spec.ts`
  - **Mutation** Treat 404 after own leave as Session unavailable; (4) must fail.
  - **Depends on** T013

- [x] T015 [US5] Recover the live stream and honour rate limits in `frontend/src/gateway/eventStream.ts`
  - **Covers** R22, R24, R25, R26, R43 (actions disabled offline), US5 scenario 5 · S3 · fact F5 (stream also ends without `closed` at `stream-max-lifetime-seconds` and on shutdown)
  - **Proof** `recovery.spec.ts`: (1) in gameplay on the player's turn, `context.setOffline(true)` → status shows offline and every opponent cell's fire control and the header Refresh are disabled; then `false` → reconnecting then connected, fire controls enabled again; board equals `getGame`; no command was resent (request log); (2) opening the game in a second tab of the same context → first tab shows "open in another tab" and opens no new stream; (3) tab return: override `visibilityState` to hidden then visible — one `getGame` is sent and the stream reopens; (4) lowered-limit stack 4175/8082 (`RATELIMIT_READGAMEPERMINUTE=2`): the 429 shows a wait hint and no `getGame` is sent before `retryAfterSeconds` (test timeout ≥ 90 s; `Retry-After` can reach 60 s); (5) manual Refresh on the same stack honors the 429 deadline and stays disabled until it expires; (6) `GAME_UNAVAILABLE` on that stack does one terminal lookup only after the deadline and never reopens `/events`; (7) same stack with `RATELIMIT_STREAMOPENPERMINUTE=2` (reads unlimited): after the stream limit is hit, at most 4 `/events` requests are sent in the next 10 s (reopen backoff 1, 2, 4, 8 s). `eventStream.test.ts` additionally proves an offline terminal lookup stays pending through its deadline and runs once after online, without reopening.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/recovery.spec.ts`
  - **Mutation** Reopen the stream after `REPLACED`; (2) must fail. Retry 429 immediately; (4) must fail. Remove the reopen backoff; (5) must fail. Keep fire controls enabled while `offline`; (1) must fail.
  - **Depends on** T014

- [x] T016 [US5] Prove expiry, presence extension and restart loss on dedicated stacks in `frontend/e2e/lifetime.spec.ts`
  - **Covers** R16, R26 (`GAME_UNAVAILABLE`), R27, R50, US5 scenario 4 · S3
  - **Proof** (1) a waiting game whose page is hidden (visibility override, Conventions) → on return "game expired" (410); after retention, reload → "no longer available" (404); Home then has no Return action; (2) with the page visible, presence keeps a waiting game past the idle timeout and the shown expiry moves forward; (3) backend restart mid-placement → "no longer available … server restarted" with New game; (4) Home offers Return while the game remains live, and removes it after the matching `GAME_UNAVAILABLE` lookup returns 404/410 and clears the remembered id; (5) that stream closure causes exactly one `getGame` and no new `/events` request (request log).
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/lifetime.spec.ts`
  - **Mutation** Disable the presence timer; (2) must fail.
  - **Depends on** T015


## 8. Phase 8: US6 — Settings, feedback and inclusive use (P2)

- [x] T017 [US6] Add sound and haptics feedback in `frontend/src/feedback/`
  - **Covers** R32, R33, US6 scenarios 1–2 · S5 · research D8
  - **Proof** Unit: (1) `AudioContext` constructor throwing, `resume()` rejecting and `vibrate` absent/throwing all resolve without error. `settings.spec.ts`: (2) toggling sound and haptics on/off mid-game leaves the next shot's result and turn identical to `getGame`.
  - **Verify** `cd frontend && npm test -- tests/feedback.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts`
  - **Mutation** Remove the try/catch around `vibrate`; (1) must fail.
  - **Depends on** T016

- [x] T018 [US6] Correct visual parity and make the three layouts, keyboard, touch and screen-reader use pass in `frontend/`
  - **Covers** R31, R39, R40, R41, R58, R59, R60, R61, US6 scenarios 3–6 · S4, S8 · research D18 · decisions B4, B5
  - **Proof** Keep the existing `accessibility.spec.ts` keyboard/touch, overflow, axe, tab, forced-colour/reduced-motion, dialog-focus and screenshot coverage at 1280×800, 390×844 and 844×390 in both themes. Update its board-change assertion to inspect accessible state labels/data-state instead of visible cell text. Add assertions that (1) ordinary water/ship/unknown cells have no visible glyph, state labels remain accessible, and miss/hit/sunk have their specified pattern/mark; after sinking a real ship, revealed water remains visually distinct from ordinary water under forced colours; (2) emoji off produces no emoji, emoji on produces only transient fire/miss animation, Results and other static surfaces never show emoji, header icons use text presentation, and reduced motion suppresses animation; (3) Rules preference help text is accurate in English and Ukrainian; (4) rules dialog has padded head/body/actions, wrapping fleet content and viewport-wide backdrop blur, with keyboard scrolling at all three viewports; (5) create/join inputs are themed and Leave does not overlap its heading. All existing R38 surfaces remain horizontally overflow-free and essential controls reachable at all 3 viewport presets.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/accessibility.spec.ts`
  - **Mutation** Give the board a fixed `min-width: 900px`; (2) must fail at 390×844. Delete the `@container app` rule; (4) must fail at 390×844. Show the header Refresh control only on `:hover`; (7) must fail.
  - **Depends on** T017


## 9. Phase 9: US7 — Installable shell offline (P3)

- [x] T019 [US7] Make the app installable with an offline shell and update prompt in `frontend/src/pwa/`
  - **Covers** R42, R43, R44, US7 · S6 · research D9
  - **Proof** `offline.spec.ts` (preview stack): (1) after one load and SW `ready`, `context.setOffline(true)` + reload renders the shell with "network required" within 3 s; (2) no request to `/api` is attempted while offline (request log); (3) after creating a game and reading it (`createGame`, `getGame`, event stream) online, `caches` entries contain no `/api` URL; (4) after rebuilding into the served `dist/` with `VITE_BUILD_ID=next npm run build` (the id is rendered in Settings, so the precache manifest changes), the page shows the update prompt and does not reload until accepted.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/offline.spec.ts`
  - **Mutation** Remove `navigateFallbackDenylist` and add an `/api` `runtimeCaching` rule; (3) must fail.
  - **Depends on** T018 (journeys share fixed ports and `dist/`, so no parallel run)


## 10. Phase 10: US8 — One gate and local operation (P2)

- [x] T020 [US8] Create the single gate `scripts/verify.sh`, the local guide and the durable-doc updates
  - **Covers** R45, R49, R52, US8 · S7
  - **Proof** (1) `./scripts/verify.sh` exits 0 with all five stages listed; (2) for n in 1–4, `VERIFY_FAIL_STAGE=n ./scripts/verify.sh` exits non-zero and names stage n; `VERIFY_STAGE_TIMEOUT=1 ./scripts/verify.sh` exits non-zero naming a timeout; (3) `python3 scripts/sync-agent-files.py --check` passes.
  - **Verify** `./scripts/verify.sh && for n in 1 2 3 4; do ! VERIFY_FAIL_STAGE=$n ./scripts/verify.sh || exit 1; done`
  - **Mutation** Make `run_stage` ignore a non-zero exit when n=4; proof (2) for n=4 must fail.
  - **Depends on** T019


## 11. Extension: SonarQube and local Docker builds

- [x] T021 [US9] Capture the fresh Sonar baseline and issue manifest in `.sonar-reports/`
  - **Covers** R63, R64; S9 baseline
  - **Proof** (1) both exports identify project key and analysis time; (2) manifest counts match the exported overall OPEN issues; (3) gate and coverage state is recorded separately per project; (4) security hotspots have their own evidence status.
  - **Verify** `sonar-local status`; `cd backend && ./mvnw clean install org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar -Dsonar.qualitygate.wait=true`; `cd frontend && npm run test:coverage && sonar-local scan`; then both `sonar-local report` commands in `docs/local-operation.md`.
  - **Mutation** Remove one issue from the recorded inventory without changing the export; proof (2) must fail on a recount.
  - **Depends on** T020

- [x] T022 [US9] Repair backend actionable findings from the T021 manifest in `backend/`
  - **Covers** R63, R64; S9 backend
  - **Proof** (1) changed behavior passes focused tests; (2) the backend gate passes; (3) the refreshed overall issue list is reconciled key by key to the preserved rolling baseline; (4) any remaining open key has explicit evidence and a decision; (5) no cookie or origin protection was weakened.
  - **Verify** `cd backend && ./mvnw -q verify && ./mvnw spotless:check`; then the backend scan and export commands in `docs/local-operation.md`.
  - **Mutation** Remove the null-command guard protecting the validation response; the named focused proof must fail.
  - **Depends on** T021

- [x] T023 [US9] Repair frontend actionable findings from the rolling baseline in `frontend/`
  - **Covers** R63, R64; S9 frontend findings
  - **Proof** (1) focused tests and real-backend journeys pass; (2) the refreshed overall issue list is reconciled key by key; (3) remaining open keys and file-ceiling impact are explicit; (4) no test was weakened.
  - **Verify** `cd frontend && npm run check && npm run e2e`; then the frontend scan and export commands in `docs/local-operation.md`.
  - **Mutation** Remove a repaired notification's accessible status behavior; its focused test must fail.
  - **Depends on** T021; may run independently of T022

- Moved to change `complete-remaining-work` (not done): T024 [US9] Prove frontend coverage and refreshed Sonar gates with real backend journeys
  - **Covers** R53, R63, R64; S9
  - **Proof** (1) a named gameplay line appears covered in generated LCOV and the refreshed Sonar report; (2) a real-backend journey still passes; (3) both final gates and issue lists are recorded; (4) no quality gate, exclusion or test rule was relaxed.
  - **Verify** `cd frontend && npm run test:coverage && npm run e2e`; then both scan/export command pairs in `docs/local-operation.md` and `./scripts/verify.sh`.
  - **Mutation** Disable browser coverage collection for one named gameplay journey; proof (1) must fail without a falsely green coverage import.
  - **Depends on** T022, T023

- [x] T025 [US10] Add the complete local artifact build script in `scripts/build.sh`
  - **Covers** R45, R49, R65; S10 local build
  - **Proof** (1) JAR and `frontend/dist/` exist after a clean build; (2) a failed stage exits nonzero and names the stage; (3) no development address is embedded in `dist/`.
  - **Verify** `./scripts/build.sh`; `./scripts/verify.sh`.
  - **Mutation** Make a failed contract stage return zero; proof (2) must fail.
  - **Depends on** T020; may run independently of T022–T024

- [x] T026 [US10] Build the frontend-only Linux image from root context
  - **Covers** R65, R67; S10 frontend image
  - **Proof** (1) native-architecture image builds without host-built `node_modules`; (2) deep links, API and SSE proxying work against a real backend; (3) missing upstream has a useful failure.
  - **Verify** `./scripts/build-docker-frontend.sh`; inspect image architecture and run a real backend smoke/browser journey through the image.
  - **Mutation** Buffer the SSE path or remove deep-link fallback; proof (2) must fail.
  - **Depends on** T025

- [x] T027 [US10] Build the backend-only Linux image from root context
  - **Covers** R65, R67; S10 backend image
  - **Proof** (1) image builds without using a macOS-built JAR; (2) health endpoint becomes ready; (3) invalid public origin fails clearly and container stops on signal.
  - **Verify** `./scripts/build-docker-backend.sh`; inspect image architecture and run health/configuration smoke checks.
  - **Mutation** Remove public-origin validation; proof (3) must fail.
  - **Depends on** T026

- [x] T028 [US10] Build and run the combined HTTPS image for a LAN browser game
  - **Covers** R66–R69; S10 combined/LAN
  - **Proof** (1) combined image builds and starts; (2) API, invitations, CSRF POST and SSE work through the exact HTTPS origin; (3) a second LAN device completes a real game interaction where environment permits; (4) isolated real-OpenSSL shell integration cases cover valid IPv4, reuse, SAN/expiry/key mismatch, partial pairs, conflicting overrides, no-argument compatibility, automatic image build, configured port, missing prerequisites and occupied port; (5) stop kills proxy and backend; (6) `./scripts/verify.sh` passes.
  - **Verify** `bash scripts/tests/run-docker-local-combined.test.sh`; `./scripts/build-docker-local-combined.sh`; `./scripts/run-docker-local-combined.sh <IPv4>` with client trust installed; real browser/game flow; `./scripts/verify.sh`; `python3 scripts/sync-agent-files.py --check`.
  - **Mutation** Remove IP SAN checking and require the wrong-address certificate case to pass; the integration proof must fail. Existing proxy streaming and shutdown mutations remain covered by proofs (2) and (5).
  - **Depends on** T026, T027


## 12. Extension: GitHub Actions pipeline

- [x] T029 Amend governance so the pipeline is allowed and stale "no CI" statements are true
  - **Covers** R70–R73 (permission); constitution 2.1.0
  - **Proof** (1) `grep -rniE "hosting or CI|no CI|CI infrastructure" AGENTS.md docs AGENTS.md` finds nothing; (2) `python3 the former workflow script --check` passes; (3) the constitution footer and report entry both read 2.1.0.
  - **Verify** the grep and sync check above
  - **Mutation** Restore "no hosting or CI infrastructure" in `AGENTS.md`; proof (1) must report it.
  - **Depends on** T020

- [x] T030 Build-only run on branch pushes in `.github/workflows/ci.yml`
  - **Covers** R70, R72, R74, R75, R77
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) the two build commands succeed locally; (3) the file has no `master` push trigger and no test command.
  - **Verify** `actionlint .github/workflows/ci.yml && zizmor .github/workflows/ci.yml`; `cd backend && ./mvnw -q -DskipTests package`; `cd frontend && npm ci && npm run build`
  - **Mutation** Remove `branches-ignore: [master]`; the trigger assertion (3) must fail.
  - **Depends on** T029

- [x] T031 Pull-request gate in `.github/workflows/ci.yml`
  - **Covers** R71, R74, R75, R77
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) the job's only test command is `./scripts/local-build.sh`; (3) the artifact paths exist after a failing local run.
  - **Verify** `actionlint .github/workflows/ci.yml && zizmor .github/workflows/ci.yml`; `grep -c local-build.sh .github/workflows/ci.yml`
  - **Mutation** Change the trigger to `pull_request_target`; `zizmor` must fail.
  - **Depends on** T030

- [x] T032 Release build, smoke and GHCR push in `.github/workflows/release.yml`
  - **Covers** R73 (image), R74–R77
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) `./scripts/build-docker-render.sh --smoke` passes locally; (3) a tag pattern test accepts `v1.2.3` and rejects `v1.2`, `v1.2.3-rc1` and `1.2.3`.
  - **Verify** `actionlint .github/workflows/release.yml && zizmor .github/workflows/release.yml`; `./scripts/build-docker-render.sh --smoke`
  - **Mutation** Replace the tag pattern with `v*`; proof (3) must fail. Remove the ancestry step; the step-presence check must fail.
  - **Depends on** T031

- [x] T033 GitHub Release creation with digest and re-run safety in `.github/workflows/release.yml`
  - **Covers** R73 (release, re-run), R74
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) the release body rendered locally from the workflow's template contains reference, digest and instructions; (3) the existing-release branch is present.
  - **Verify** `actionlint .github/workflows/release.yml && zizmor .github/workflows/release.yml`
  - **Mutation** Remove the digest from the body template; proof (2) must fail.
  - **Depends on** T032

- [x] T034 Write `docs/ci.md`
  - **Covers** R78
  - **Proof** (1) every workflow file and trigger named in the guide exists in the workflows; (2) links resolve; (3) `docs/index.md` links it.
  - **Verify** `grep -n "ci.md" docs/index.md AGENTS.md`; manual link check
  - **Mutation** Rename a workflow file; proof (1) must fail.
  - **Depends on** T033

- [x] T035 Lint and hardening proof over all workflows
  - **Covers** S11, R74, R75, R77
  - **Proof** (1) both linters exit 0; (2) the pin and permission checks report no violation; (3) the sync check passes.
  - **Verify** `actionlint && zizmor .github/workflows/`; `grep -n "uses:" .github/workflows/*.yml`
  - **Mutation** Unpin one action, drop one `permissions` block and change one tag glob in a scratch copy; each must make a named check fail. Record the output.
  - **Depends on** T034

- Moved to change `complete-remaining-work` (not done): T036 Owner verifies triggers on GitHub
  - **Covers** R70–R73 (behaviour GitHub alone can show)
  - **Proof** The owner records the run links; this task is never checked on local evidence.
  - **Verify** n/a, GitHub
  - **Depends on** T035

## Dependencies and execution order

T001, T002, T003 sequential; T004 parallel with T001; T005 needs T003 and T004; T006..T020 serial in id order.
T020 precedes T021 (then T022 and T023 in parallel, then T024), T025 (then T026, T027, T028 serial) and T029 (then T030..T036 serial).
Parallel only T001 and T004; journeys share fixed ports and `dist/`, so frontend tasks never run concurrently.

## Gate

Per-task Verify commands; after T020 the gate is `./scripts/local-build.sh` (formerly `verify.sh`).
