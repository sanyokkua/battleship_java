# Design

Condensed from `the original 003-frontend plan documents` and `contracts/ui-surface.md`. Assurance level: Standard (owner decision 2026-10-18); Lean for the pipeline extension. Evidence is the R54 unit list, the R55 journey matrix and owner-requested focused correction proofs. No pixel matching, coverage gate or cross-browser matrix.

## Context

A static React app in `frontend/` renders the server's `GameSnapshot` and sends only the actions the snapshot allows, plus a root integration layer in `scripts/` that runs it against the packaged backend in development, production-style and verification modes. No authoritative client game engine, no fake backend, no recorded game data: every game behaviour is proven by browser journeys against the real JAR (R53). Placement checks are advisory. Four contract and backend fixes ship with it (R57).

Script consolidation (owner decision 2026-11-07): `scripts/` holds only `local-build.sh` (gate; replaces `verify.sh`, `build.sh`, `install.sh`, `verify-timeout.mjs`), `local-deploy.sh` (replaces `dev.sh`, `local-prod.sh`, `run-docker-local-combined.sh`, the HTTPS combined image, `scripts/tests/*`) and `build-docker-{frontend,backend,render}.sh`. `stack.mjs` is replaced by `frontend/e2e/fixtures/stack.ts`. Task text keeps the command names of the day it was written.

## Goals / Non-Goals

- Goals: a view-only client over the contract; privacy of the invitation secret; honest recovery; inclusive responsive use; one gate; local images; a lean CI pipeline.
- Non-goals: see proposal Out of scope.

## Layout

```text
frontend/   standalone npm project; no root workspace
  package.json vite.config.ts tsconfig*.json eslint.config.js .prettierrc vitest.config.ts playwright.config.ts index.html public/
  src/main.tsx (fragment strip, then render, R08)  app/ (router, shell, header)
      contract/generated/ (openapi-typescript output, git-ignored)  gateway/ (GameGateway, EventStream, decode, problem->message)
      session/ (snapshot store, action slot, presence)  screens/ (home create join waiting placement gameplay results unavailable)
      components/ (Header Button Card Pill Toasts Stepper Meter Board FleetTray PlayerCard StatCard Dialog Qr ConnectionStatus + CSS)
      i18n/ (translate en uk)  preferences/  feedback/ (audio, haptics)  pwa/  styles/ (tokens.css base.css from the mockup)
  tests/ (vitest, R54 items only)   e2e/ (Playwright journeys + stack fixture)
scripts/  config/local.env.example  docs/local-operation.md
```

| Area | Owns | Must not contain |
|---|---|---|
| `gateway/` | Every HTTP and SSE call, CSRF, decode, problem mapping (Constitution III: one gateway) | UI, game decisions |
| `session/` | Latest accepted snapshot per open game, connection state, in-flight action | Rules, advisory placement checks, wire copies |
| `screens/`, `components/` | Rendering a snapshot; offering `allowedActions` | `fetch`, `EventSource`, storage |
| `scripts/` | Process orchestration and the gate | Application code, a root `package.json` |

## Stack

Runtimes by major only (Constitution v2.0.0): Node 24, Java 25. Exact npm pins, proven together in the owner's solitaire project (D1): `react`/`react-dom` 19.3.0, `vite` 8.3.0, `@vitejs/plugin-react` 6.1.1, `typescript` 6.0.3, `vite-plugin-pwa` 1.3.0, `workbox-window` 7.4.1, `vitest` 5.0.1, `jsdom` 30.1.1, `@testing-library/react` 16.3.3, `@testing-library/user-event` 14.6.7, `@testing-library/jest-dom` 7.0.1, `@playwright/test` 1.63.0, `@axe-core/playwright` 4.13.0, `eslint` 10.11.0, `@eslint/js` 10.0.1, `typescript-eslint` 8.70.1, `eslint-plugin-react-hooks` 7.1.1, `eslint-config-prettier` 10.1.8, `prettier` 3.9.8, `@types/react`/`@types/react-dom` 19.3.0, `@types/node` 26.6.2. Added: `openapi-typescript` 7.13.0, `openapi-fetch` 0.17.0, `qrcode` 1.5.4 + `@types/qrcode` 1.5.6.

Deliberately not added: TanStack Query (D2), Zod or any hand-written wire schema (D3, R56), a router library (D4), an i18n library (D5), `eslint-plugin-jsx-a11y` (ESLint 10 support unverified; axe proves S4), MSW or any fixture/fake backend (R53), Firefox/WebKit projects (Assumption 2).

## Runtime design

### Boot and routing (R06, R08, R38)

`main.tsx` reads `#invite=` on `/join/:gameId` into a module-scoped variable and calls `history.replaceState` with the fragmentless URL before `createRoot().render`. A History-API router serves `/`, `/create`, `/join/:gameId`, `/games/:gameId`; anything else renders Home. Reload of a game route rebuilds from `getGame`. The Shell persists across routes; its Game rules action loads `listRulesets` on demand; Settings stays in the Header.

### Gateway (R03, R13, R22, R23, R56)

- Types and enum value arrays only from `src/contract/generated/api.ts` (`openapi-typescript --enum-values`; a `.ts` file because the arrays are runtime values). A `generate` script runs before `predev`, `prebuild`, `pretypecheck`, `prelint`, `pretest`; output is never committed. Calls via `openapi-fetch`, same-origin credentials.
- CSRF: `getMeta` before the first POST; a `403 request-security-rejected` refreshes meta and resends that request once, create and join included (R03). A `createGame` ending in a network failure or unreadable answer is an unknown outcome and is never resent (R13); a problem answer (422, 429, 503, `browser-game-limit`) is a definite refusal with its own message (R22).
- Decode sniff: JSON content type, `JSON.parse`, presence of required keys from a list typed `as const satisfies readonly (keyof GameSnapshot)[]`; applies to HTTP bodies and SSE `data`. Failure is a transport failure and the value never reaches the store (R23). It checks readability, not the schema (R56).
- Problems: `Record<ProblemCode, MessageKey>` (a removed entry fails typecheck) plus a status-class fallback for unknown codes (the contract declares its enums open). `retryAfterSeconds` disables the retry control until it elapses (R22). Unknown `allowedActions` values are dropped.

### Live stream (R24-R26)

One `EventSource` per open game. Before opening, the previous instance is closed and its late events ignored. `closed`/`REPLACED` shows "open in another tab" and never reopens. `closed`/`GAME_UNAVAILABLE` does one `getGame`, no reopen. `error` with `readyState === CLOSED`, or the tab becoming visible, does `getGame` and reopens if it succeeds. While reconnecting, a failed `getGame` without a definite problem (network error, proxy 502, unreadable answer) retries with backoff (1, 2, 4, then every 8 s), and a 429/503 after its `retryAfterSeconds`, until it succeeds or returns 401/404/410. A stream that fails again after a successful `getGame` reopens on the same backoff, reset by its first received event, so a refused stream open never loops. Heartbeats send nothing.

### Session store (R01, R02, R05, R16, R19, R21)

`useSyncExternalStore` store per open game. One version gate for every snapshot source (command, presence, invitation, `getGame`, SSE): higher version replaces; equal version merges only fields the contract excludes from `version` (`expiresAt`, `serverTime`, and after fix 4 `invitationUrl`/`invitationExpiresAt`); lower is dropped. One action slot: a new `commandId` per user action, the same one on retry after a lost answer; controls disabled while pending. A command with no answer after 10 s is aborted and, like a network failure, puts the slot in checking. The slot settles only on that action's own HTTP answer; an SSE snapshot updates the board but leaves a lost action in checking until the player retries. Presence does not use the action slot: visible tab, `SEND_PRESENCE` allowed, every `limits.presenceIntervalSeconds` from `getMeta`; the timer restarts (does not fire) on visibility return or reconnect.

### Outcome to screen (R07, R12, R15, R20, R27, R28)

| Server answer | Client outcome |
|---|---|
| `410 game-expired` | Session unavailable - "game expired"; forget remembered id |
| `401 session-required`, `404 game-unavailable` | Session unavailable - "no longer available (ended, left, or server restarted)"; forget id |
| `404` after the caller's own leave | Leave succeeded, Home |
| join `409 invitation-unavailable` | Invitation unavailable (R15) |
| `409 browser-game-limit` (new, R57) | "You already have a game" + Return to your game if an id is remembered, else "play again once it expires" |
| other `409` on a command | `getGame`, then the worded reason (R20) |
| `429`, `503` | Calm wait message with `retryAfterSeconds`; no automatic retry |
| Host `WAITING` snapshot without `invitationUrl` | Invitation expired, sharing unavailable (R12) |

Replace invitation is offered whenever `NEW_INVITATION` is allowed, expired or not, and sent only after the host confirms. The client reads `getGame` once at `invitationExpiresAt` (offset-corrected) so the server's answer, not the clock, shows the expiry. Countdowns display server times corrected by the `serverTime` offset and never decide expiry.

### Leaving, layouts, dialogs (R28-R30, R39, R41)

- Leave in `WAITING`/`PLACEMENT`: confirmation dialog then `leaveGame`; Home after 204 or 404. In `PLAYING` the leave path is Settings, Resign match (shown while `RESIGN` is allowed): the dialog says it means resigning, then `RESIGN`. Header Refresh calls `getGame` through the version gate. Leave results: `leaveGame` from a finished or abandoned result, Home, forget the remembered id.
- Layouts are CSS only (D18): below the mockup's `app` container width of 47.5rem a `role="tablist"` Enemy waters / Your fleet switch shows one board, with turn pill, player cards and action status outside the panels; `(max-height: 31.25rem) and (orientation: landscape)` shows two compact boards and no tabs; otherwise both boards side by side.
- One `Dialog` on native `<dialog>` + `showModal()` (focus containment, Escape to `cancel`), labelled by its heading, returns focus to the opener; Escape is disabled where dismissal is unsafe (pending action).

### Placement, play, results (R17, R18, R21, R31, R32, R36)

- Board: `role="grid"` with one roving tab stop (Arrow keys, Home/End; Enter/Space activates), each cell labelled with coordinate and state ("A1, hit"); fired and read-only cells `aria-disabled`. Tracks and labels come from `ruleset.board` (letters A-Z, then AA...; numbers from 1).
- Placement uses a compact anchored popup for an empty cell and a stacked adjacent menu for a placed ship; shows only available ship lengths and directions that fit; one-cell ships need no direction choice. A multi-cell drop opens the compact direction chooser, or reports no fit and lets the player choose another cell. Server `Ship.cells` and `shipId` exclude only the ship being moved; dimensions, occupancy, orientation and `shipsMayTouch` are checked against other ships. The server hint is used for ordinary display; during a move the display restriction is computed from remaining ships including overlapping halos. Advisory only. Move or rotate sends one `PLACE_SHIP`, never a preliminary removal. The selected ship is lifted visually during a move; cancel, rejection or a superseding snapshot restores the authoritative view. Fleet counts, progress and readiness come from snapshots. Touch selects a ship then a cell without hijacking scrolling; mouse dragging remains. Ready opens confirmation before `READY`.
- Play: fire only on `UNKNOWN` opponent cells while `FIRE` is allowed. Announcements and sound/haptics trigger once per new `lastShot` (`by` + `target`).
- Mobile turn handoff (approved Pulse): use server `turn` and `lastShot`, board tabs and announcement; do not predict shots or turns. For a new live shot show the affected board and its pulsing cell, then after 1,000 ms select the board for the new turn if the turn changed. Sea Battle hits retain the board. A newer shot replaces a pending timer; a manual tab selection cancels it. On initial load or reconnect select the board for the current turn without replaying a stale shot. Stop pending work on unmount or game end. Avoid moving keyboard focus into a hidden board. Reduced motion uses a visible static highlight. Automatic switching only in the single-board layout.
- Results render `outcome`, both boards, fleet-condition tiles and four stat cards from `statistics` as supplied; a separate shot-decision card shows each player's total, average, fastest and slowest; absent aggregates show an em dash. Journey compares rows to each player's snapshot including zero-shot results and proves retained-turn shot timing cannot be substituted with turn timing.

### Visual design (R58-R62, D17)

- `tokens.css`: the mockup's custom properties, dark on `:root`, light on `[data-theme="light"]`; `themeController` sets `data-theme` and resolves `system` via `matchMedia('(prefers-color-scheme: dark)')`. `base.css`: system font stack, focus-visible ring, the mockup's `forced-colors` rule, `prefers-reduced-motion: reduce` disabling transitions and animations.
- Components ported from mockup markup/CSS as React components; icons are the mockup's Unicode glyphs. Empty water and ship cells have no repeated visible glyphs; hit/sunk use concise non-colour marks; water/ship/miss stay distinguishable by patterns and accessible state labels, also under forced colours. Emoji off by default, limited to a transient fire or miss/empty-cell animation; no static emoji.
- Home, Create, Join and Waiting use flat screen wrappers in both themes; ruleset cards show aggregated count x length badges; View details retains the complete rules. Modal dialogs use the mockup's padded head/body/actions structure, scroll within short viewports, blur the backdrop. During ordinary placement `placementBlockedCells` use the mockup's diagonal hatch and an accessible unavailable-for-placement label; during a move, move-relative restrictions apply instead.
- Interaction: hover styles only in `@media (hover: hover)`; controls keep `--control-h` (2.75rem) and `touch-action: manipulation`; board cells fill the grid width with `aspect-ratio: 1`.

### Preferences, i18n, feedback, PWA (R32-R35, R42-R44)

- Translator, storage wrapper and theme controller adapted from solitaire (Redux removed). Ruleset and ship names by id with raw-id fallback (R35).
- Audio: master `audioRecipes.ts` + `AudioFeedback.ts` (WebAudio, swallow all failures). Haptics: `navigator.vibrate` behind a capability check. Both no-op on denial.
- PWA: solitaire `src/pwa/` (`registerType: 'prompt'`, update only after consent, R44); workbox precaches static assets only, `navigateFallbackDenylist: [/^\/api\//]`, no runtime caching (R42). Offline: `navigator.onLine` + `online`/`offline` events show "network required" and disable game actions (R43).

### Contract and backend fixes (R57)

1. `browser-game-limit` (409): new `ProblemCode` on `createGame` and `joinGame` with an example in `openapi.yaml`; `SessionRegistry.admitGame` throws a distinct exception instead of `CapacityExceededException`; `CreateGameUseCase` and `JoinGameUseCase` map it; `ProblemAdvice` maps it to 409 without `Retry-After`; `createGame` gains a 409 response. Dependents: `CapacityIT`, `WireConformanceIT`, `the original 002-backend artifacts` R62/S11 wording. Proof: `CapacityIT` expects 409 for the cap and 503 for a full server.
2. Forked test JVM lingers 30 s: Failsafe dumpstream "kill self fork JVM"; diagnose first (hypothesis: ITs inheriting `server.shutdown: graceful`), fix at the root without weakening `GracefulShutdownIT`. Proof: clean `verify` leaves no such dumpstream line.
3. Actuator on the public port: remove `spring-boot-starter-actuator`; `PackagedArtifactIT` asserts 404 on `/actuator/health` (agent-proposed, owner approved 2026-10-18).
4. Invitation lapse invisible to `version`: contract-only fix (D16): `version` also excludes `invitationUrl` and `invitationExpiresAt`; `contracts/README.md` note; delivered with the waiting-room task.
5. `placementBlockedCells` in the `PLACEMENT` snapshot (R62), delivered in T010 across contract, domain projection, assembler and tests.

### Local topology (R45-R52)

| Mode | Frontend | Backend | `BATTLESHIP_PUBLICBASEURL` |
|---|---|---|---|
| Development | `vite` on `localhost:5173`, `strictPort` | JAR on `:8080` | default `http://localhost:5173` |
| Production-style | `vite preview` of `dist/` on `localhost:4173`, `strictPort` | JAR on `:8080` | `http://localhost:4173` |
| Journeys | `vite preview` per stack: default 4173, short-lifetime 4174, lowered-limit 4175, restart 4176 (later also 4193-4201) | JAR 8080, 8081, 8082, 8083 (also 8193-8201) | matching frontend origin |

`vite.config.ts` reads `FRONTEND_PORT` and `BACKEND_PORT` (passed to the JAR as `SERVER_PORT`; B3). Both Vite servers proxy `/api` to `http://127.0.0.1:<backend port>` (SSE streamed, no compression); `vite preview` serves `index.html` for deep links (D10). The backend accepts unsafe requests only from the exact origin, so `localhost` is used for the page, never `127.0.0.1`. The stack fixture (formerly `stack.mjs`; Node stdlib) starts the JAR with env overrides and the frontend server, waits for `GET /api/v1/health` ready, prefixes and redacts output (drops `Set-Cookie`, `#invite=`, cookie values), names component + address + next step on failure, stops only its own processes (R48, R51). The default journey stack raises create/join and per-session rate limits to 1000 (a full game sends ~100 commands per player against a 60/min default); dedicated stacks set short `idle-timeout-seconds`, `presence-interval-seconds`, `sweep-interval-seconds`, `result-retention-seconds`, `invitation-lifetime-seconds` or a lowered limit through existing env keys only (R50, D12). Restart = stop and start the same JAR. Backend env keys: `BATTLESHIP_PUBLICBASEURL`, `..._IDLETIMEOUTSECONDS`, `..._PRESENCEINTERVALSECONDS`, `..._SWEEPINTERVALSECONDS`, `..._RESULTRETENTIONSECONDS`, `..._INVITATIONLIFETIMESECONDS`, `BATTLESHIP_RATELIMIT_{CREATEGAME,JOIN,COMMANDS,READGAME,STREAMOPEN}...PERMINUTE`, `SERVER_PORT`; all `@Min(1)`. Create/join limits are keyed by remote address; the others by session cookie.

## Decisions (task-list decisions B1-B5)

- B1 `[agent]` One ordering-only unit `frontend/tests/snapshotGate.test.ts` for the R05 version gate (no game data, falls under R54; no journey can deterministically fail on a broken gate).
- B2 `browser-game-limit` joins the shared `Conflict` response (description scoped "(create, join)"); `createGame` gains a 409.
- B3 Journey backends take their port from `SERVER_PORT`; `BACKEND_PORT` maps to it.
- B4 Touch-scroll gestures in journeys use a Chromium CDP session (`Input.dispatchTouchEvent`) in a `devices['Pixel 7']` context; taps use `locator.tap()`.
- B5 The owner's mockup comparison (S8) is recorded as a `Visual review:` paragraph in T018's commit body.
- Conventions: deterministic fleets placed through the opponent's UI at fixed anchors; `route.fulfill` forbidden in `e2e/` (only `route.fetch()` then `route.abort()`, or plain `route.abort()`; ESLint `no-restricted-syntax` rule); hidden tab simulated by overriding `document.visibilityState`; viewports 390x844, 844x390, 1280x800; visual output `frontend/test-results/visual/<surface>-<size>-<theme>.png`; rulesets `sea-battle-10-ship.v1` (ships `ship-4/3/2/1`; no touch, extra turn, reveal water) and `hasbro-classic-2002.v1` (`carrier`, `battleship`, `destroyer`, `submarine`, `patrol-boat`; touch, no extra turn, no reveal).

## Research decisions (D1-D19)

- D1 Stack pins: solitaire's npm matrix plus openapi-typescript/openapi-fetch/qrcode; rejected seed pins and npm latest (unverified peers).
- D2 No TanStack Query: a small `useSyncExternalStore` store; R05 needs a custom gate and Query's retries/refetch would be switched off anyway.
- D3 No hand-written schemas; decode sniff of content type, JSON parse and required keys; rejected Zod and Ajv.
- D4 No router library: four static patterns; fragment stripping must run before any router.
- D5 Hand-written i18n (flat keys, `{name}` placeholders, `Intl.PluralRules`, English fallback); rejected i18next.
- D6 Type generation via `openapi-typescript --enum-values` into a git-ignored `.ts`; SSE payloads typed by `GameSnapshot`/`StreamClosed` schemas; rejected committing generated output and plain `fetch`.
- D7 QR: `qrcode` 1.5.4, lazy-imported, `toCanvas` with `aria-label` (port of master's QrCodeSheet); rejected `qr` 0.6.0.
- D8 Sound and haptics: port master audio recipes; result keys map from `lastShot.result`; haptics via `navigator.vibrate`.
- D9 PWA: copy solitaire `src/pwa/` and its VitePWA block (`registerType: 'prompt'`, static manifest, precache, `navigateFallback: 'index.html'` with `/api/` denylist, no runtime caching).
- D10 Production-style server is `vite preview` with `preview.proxy` and `strictPort`; rejected serving UI from the JAR, nginx/Caddy, a hand-written Node server (fallback only if journeys disprove streaming or deep links).
- D11 One orchestrator (stack module) for people and the Playwright fixture; rejected Playwright `webServer` alone.
- D12 Short lifetimes and limits by env configuration on dedicated stacks; rejected test-only endpoints or clock control.
- D13 Lost answer without a mock: `page.route` with `route.fetch()` then `route.abort()`; the retry must carry the same `commandId`.
- D14 `browser-game-limit`: HTTP 409, no `Retry-After`; rejected 429 and 503 with different `detail`.
- D15 Runtime majors only (Java 25, Node 24); constitution amended to v1.3.2.
- D16 Invitation fields leave `version`; rejected a backend sweeper that bumps the version.
- D17 Visual system from the mockup: tokens in `tokens.css`, system font stack, Unicode glyphs; rejected CSS frameworks.
- D18 Responsive model CSS only; board tracks come from `ruleset.board`; rejected JS layout detection.
- D19 Placement input (owner correction 2026-11-06): practical mouse dragging, touch selects ship then cell, compact direction chooser, one atomic command; rejected unconditional touch drag and clamping drops.

## Client data model

No game model is stored by the client; wire shapes are generated types.

Per open game, in memory: `gameId` (route), `snapshot` (last accepted), `clockOffsetMs` (`serverTime - Date.now()`, display only), `connection` (`connected|reconnecting|offline`), `checking` (lost answer), `action` (`{commandId: uuid, kind, body}` single-flight slot), `notice` (`{messageKey, retryAfterSeconds?}`), `lastAnnounced` (`{by, target}` dedupes announcement/sound per `lastShot`).

Version rule (R05): absent `c` or `s.version > c.version` replaces; equal keeps `c` and takes `expiresAt`, `serverTime`, `invitationUrl`, `invitationExpiresAt`; lower is dropped. Applies to command, presence, invitation, join, create, `getGame`, SSE.

Action slot (R19, R21): `idle -> pending(commandId)` on an allowed action (`crypto.randomUUID()`); `pending -> idle` on that action's own HTTP answer; SSE does not settle it; `pending -> checking` on network failure or 10 s timeout, the retry resends the same body and `commandId`. Fire marks its target cell pending. Presence, invitation replacement and leave carry no `commandId` but disable their own control while pending.

Connection (R24-R26): open game screen -> `reconnecting`; first `snapshot` -> `connected`; `error` in CONNECTING -> `reconnecting` (browser retries); `error` CLOSED or tab visible -> `getGame`, reopen if OK, backoff 1,2,4 then 8 s; `offline` event -> `offline` (close stream, actions disabled); `online` -> `reconnecting`; `closed REPLACED` -> terminal (other tab); `closed GAME_UNAVAILABLE` -> terminal, one `getGame`. A network error while `navigator.onLine` is true never means `offline`.

Presence (R16): timer per open game at `meta.limits.presenceIntervalSeconds`, only while visible and `SEND_PRESENCE` allowed; restarts on visibility return and reconnect; no action slot.

Invitation secret (R08-R10): module variable only, set in `main.tsx`, read once by the Join submit, cleared when the join settles; never in rendered state, storage, logs or URLs.

Persisted (localStorage, non-secret): `battleship.preferences` = `{locale: en|uk, theme: dark|light|system, emoji, sound, haptics}` (never cleared); `battleship.currentGameId` = public game id, cleared when the server reports it gone (410/401/404), after leave or leave-results. Nothing else persisted; service worker caches static assets only.

Derived for display only: ruleset facts (`listRulesets`); ship/ruleset names (i18n by id); countdowns (`expiresAt` / `invitationExpiresAt - (Date.now() + clockOffsetMs)`); invitation meter (remaining / `meta.limits.invitationLifetimeSeconds`); fleet tray (unplaced ships grouped by `length`); fleet progress (placed / total); ships left (`shipsRemaining` / fleet total); ship cells left (sum of `length x count` minus HIT/SUNK cells); waiting-room stepper (`WAITING` Waiting, `PLACEMENT` Placement, `PLAYING`+ Battle); result availability note (`expiresAt`). Advisory placement checks derive valid directions and move-relative restrictions from the ruleset and exact `Ship.cells`; they never decide command success.

## UI surface contract

Routes: `/` Home (reads `battleship.currentGameId`, offers Return to your game); `/create` (`getMeta`, `listRulesets`); `/join/:gameId` (`#invite=` stripped before render; `getMeta`; nothing redeemed until Join game); `/games/:gameId` (Waiting room, Fleet placement, Gameplay, Results, Session unavailable by `snapshot.phase` or the outcome table; `getMeta`, `listRulesets`, `getGame`, then the stream); anything else Home. The invitation link format is `{public-base-url}/join/{gameId}#invite={secret}`, so public-base-url must equal the frontend origin (R47). Dialogs and placement popups have no route (R37).

Surfaces and widgets (every surface has the header: brand, Game rules, translated ruleset name on game surfaces, Refresh on game surfaces, Settings):

| Surface | Widgets and source |
|---|---|
| Home | title; New game, Return to your game, Game rules, Settings |
| Create game | ruleset radio cards (name, aggregated count x length badges); captain name (1-32, `DisplayName`); Back, View details, Create game |
| Join invitation | captain name; Join game; invitation-unavailable state |
| Waiting room | five ruleset facts; stepper Created/Waiting/Placement/Battle; connection pill incl. "Open in another tab"; invitation field showing `invitationUrl` with everything after `#invite=` replaced by bullets (full link never in the DOM); Copy link, Show QR; invitation countdown and meter; notice "Waiting for an opponent. This page updates automatically."; Replace invitation; Leave game; "Game created" toast |
| Fleet placement | fleet tray by length with counts; progress tile; opponent status pill; your board; touching rule text; blocked-cell hint and move-relative restrictions marked visibly and accessibly; Random, Clear, Ready (confirmation); cell-adjacent popups and menus |
| Gameplay | turn pill; two player cards (name, ships left, ship cells left); Enemy waters / Your fleet tabs (narrow); both boards with coordinate labels, patterned cells, hit/sunk marks, accessible state names; footer with connection state and "Session expires in" |
| Results `FINISHED` | Victory/Defeat hero with reason; fleet tiles intact/damaged/sunk; stat cards Match time, Turns & accuracy, Turn timing, Shot decision timing; both boards; availability note; Create another game, Leave results |
| Results `ABANDONED` | "Game abandoned"; no boards or statistics; Leave results |
| Session unavailable | status code badge; expired or no-longer-available message; Create new game, Return home |
| Dialogs and overlays | rules (rulesets side by side), settings (theme, emoji, language, sound, haptics, Resign match when allowed), QR with privacy warning, confirmations (ready, replace invitation, leave, resign), checking, error; toasts (success, info, warning, error) |

Results stat card fields: fleet tiles `statistics.you.fleet.intact/damaged/sunk`; Match time `match.totalDurationMs`, `match.placementDurationMs`, `you`/`opponent.placementDurationMs`, `match.gameplayDurationMs`; Turns & accuracy `you`/`opponent.turns.count`, `you.hits`, `you.shots`, `you.accuracy`; Turn timing `you`/`opponent.turns.totalMs`, `...averageMs`, `you.turns.fastestMs/slowestMs`; Shot decision timing each player's `shotDecisions.totalMs/averageMs/fastestMs/slowestMs`. Absent optional value shows an em dash.

## Gate

| Command | Proves |
|---|---|
| `./scripts/local-build.sh [--docker]` | runtime majors, exact lockfile installs, Playwright Chromium (R45); then in order: `contracts` `npm run check`, `backend` `./mvnw -q verify && ./mvnw spotless:check`, `frontend` `npm run check` (format, lint, typecheck, vitest, build, no-dev-address), `frontend` `npm run e2e` against the packaged JAR. Any failed, skipped or timed-out stage exits non-zero (R49) |
| `./scripts/local-deploy.sh [IPv4 \| localhost]` | packaged JAR + built `dist/` behind one origin (HTTPS with a self-signed per-IP certificate on a LAN IP; plain HTTP on `localhost`); runs until Ctrl-C; stops only its children |

Journey files (`frontend/e2e/`, Chromium, `workers: 1`, two isolated browser contexts per game): `smoke`, `settings`, `create`, `waiting`, `invitation`, `placement`, `play`, `results`, `leaving`, `lifetime`, `recovery`, `accessibility`, `offline`. Proof ownership: R54 units in `frontend/tests/*`; proxy/SSE/deep link in `smoke`; settings in `settings`; create/CSRF/`browser-game-limit`/lost create answer in `create`; five waiting facts, copy/QR/replace/expiry in `waiting`; fragment strip, race, secret scan in `invitation`; placement and drag/touch in `placement`; fire/turn/lost answer in `play`; full game per ruleset in `results`; leave/resign in `leaving`; expiry/presence/restart in `lifetime`; stream recovery, 429, backoff in `recovery`; layouts, axe, keyboard, touch, forced colours, 19-surface screenshots in `accessibility`; offline shell in `offline`; no dev address in `npm run check`; contract/backend fixes in `contracts npm run check`, `CapacityIT`, `PackagedArtifactIT`. Success criteria owners: S1 `results`; S2 `invitation`; S3 `lifetime`/`recovery`/`play`; S4 `accessibility`; S5 `tests/feedback.test.ts` + `settings`; S6 `offline`; S7 gate evidence; S8 screenshots + owner review.

Manual smoke (quickstart): create with each ruleset; second profile opens the link (no `#invite`, nothing happens until Join); both place and ready; play to the end (Sea Battle hit keeps turn and shows revealed water, Classic passes turn); results with statistics; stop/restart backend mid-game shows "no longer available - server restarted"; offline reopen shows shell with connection needed; compare screens with the mockup at three sizes and both themes; touch emulation place and fire. Ports free: 8080, 4173 (journeys also 4174-4176, 4193-4201, 8081-8083, 8193-8201).

## Extension: SonarQube and local images (2026-11-06)

Assurance Standard. Owner approved up to 31 total tasks for this extension and raising the earlier 40-file ceiling; T021-T028 add eight tasks; working forecast about 71 files. Existing boundaries: `docs/local-operation.md` defines the Sonar scan and report commands and says imported frontend LCOV covers Vitest, not Playwright; the backend has a Secure JavaScript-readable `XSRF-TOKEN` cookie for the double-submit header and a separate HttpOnly session cookie; the origin filter compares state-changing requests with the exact `BATTLESHIP_PUBLICBASEURL` origin; same-origin proxying and trusted HTTPS are required for LAN use. Backend and frontend images need the root Docker build context because builds read `contracts/openapi.yaml`. In the combined image the proxy terminates TLS, serves SPA deep links and forwards `/api` including SSE unbuffered; the backend listens only on the container's private path. Create/join limits are keyed by remote address, so proxy aggregation affects them and is documented. Game state is in memory.

Design: a finite local artifact build, one root `Dockerfile` with Linux Node 24 and Java 25 build stages and `frontend`, `backend`, `combined` (later `render`) targets; tags `battleship-frontend:local`, `battleship-backend:local`, `battleship-local:local`; the combined container exposes HTTPS 8443 by default, caller supplies certificate, key and exact `https://` public base URL; entrypoint starts proxy and backend, propagates termination, exits if a child fails; standalone frontend config names its upstream, backend config names its exact public origin; nothing is pushed. LAN convenience (T028): positional IPv4 derives the HTTPS origin and `.local-tls/<IPv4>/`; reuse only a complete, parseable, unexpired pair with matching IP SAN and matching keys; never overwrite a partial or invalid identity; directory mode 700, key mode 600; conflicting overrides fail before start; the operator trusts the certificate on each device.

Sonar repair: export both baseline reports after fresh scans; inspect overall issue lists, quality gates, profiles; fix by source behaviour and security context; the readable CSRF token is part of the double-submit protocol (verify same-origin, `Secure`, `SameSite=Strict`, separate HttpOnly session cookie before judging a hotspot); record any finding whose fix would break an approved boundary and leave it open with evidence. The frontend 80% new-code coverage gate cannot be claimed from Vitest LCOV alone; a real-backend Playwright coverage merge is added only if it maps to source lines reliably; gate criteria unchanged. Recorded outcome: backend gate passed with 7 open issues (from 10); frontend open keys 56 to 5, new-code coverage 80.4% but gate failed on the rules-region focus finding (`typescript:S6845`); named source-line import check denied (403), which keeps T024 open.

## Extension: GitHub Actions pipeline (2026-11-07)

Assurance Lean; at most 8 tasks (T029-T036). Files: `.github/workflows/ci.yml`, `release.yml`, `docs/ci.md`, plus governance/doc edits; no dependency, no script. `ci.yml`: `push` on all branches except `master` (tags ignored) runs `./mvnw -q -DskipTests package` and `npm ci && npm run build`; `pull_request` to `master`, `rewrite_prod_ready` and `feature/**` runs `./scripts/local-build.sh` on `ubuntu-latest` with Temurin 25 and Node 24, uploading Playwright and backend test reports on failure. `release.yml`: tags `v[0-9]+.[0-9]+.[0-9]+`; checks master ancestry, builds the `render` target for `linux/amd64`, reuses `scripts/build-docker-render.sh --smoke`, pushes to GHCR (`GITHUB_TOKEN`, semver tags), creates the GitHub Release with `gh release create` (no third-party release action) including the digest. No `master` push trigger. Every job sets `permissions`, `timeout-minutes`, `concurrency`, SHA-pinned actions and `persist-credentials: false`. Lint: `actionlint` and `zizmor`; `act` cannot cover tags, GHCR or permissions, so Docker commands run locally and trigger behaviour is verified by the owner on GitHub (T036).

Assumption and risk: the release does not re-run the gate; a direct push of an unchecked commit to `master` followed by a tag would publish it. Mitigation: branch protection with the gate as a required check (documented in `docs/ci.md`). Cost: two workflow files, one guide, about 25 permissions/pin lines, about 10 runner minutes per PR gate. Cheaper alternative: one workflow with the gate only and a manual release. Recommendation: now.

## Risks

- `__Host-`/`Secure` cookies over `http://localhost` are relied on in Chromium only (Assumption 2).
- `vite preview` as the production-style server: SSE streaming and deep-link fallback are proven by the `smoke` and `waiting` journeys; if either fails a Node stdlib static server becomes the recorded fallback.
- The page must be opened on `localhost` with the configured port; any other host gets 403 on every POST.
- Playwright 1.63.0 browser revision must be installed by the gate's install stage; the cache is not assumed.
- Sonar frontend gate not passing; GitHub trigger behaviour unverified until T036.
- Complexity Tracking: nothing to justify.

## Migration Plan

Greenfield; no migration. the original workflow feature artifacts under `the original 003-frontend artifacts/` remain the source this change was converted from.
