# Implementation Plan: Battleship Frontend and Local Integration

**Branch**: `feature/003-frontend` · **Spec**: [spec.md](spec.md) · **Contract**: [`contracts/openapi.yaml`](../../contracts/openapi.yaml) · **Date**: 2026-10-18

## Summary

A static React app in `frontend/` that renders the server's `GameSnapshot` and sends only the actions the
snapshot allows, plus a root integration layer in `scripts/` that runs it against the packaged backend in
development, production-style and verification modes. No client rules, no fake engine, no recorded game data:
every game behaviour is proven by browser journeys against the real JAR (R53). Four fixes to the contract and
backend ship with it (R57). Decisions: [research.md](research.md). Client state:
[data-model.md](data-model.md). Routes, storage and commands: [contracts/ui-surface.md](contracts/ui-surface.md).
Runbook: [quickstart.md](quickstart.md).

## Assurance level: Standard

Owner decision 2026-10-18 (AGENTS.md rule 2). Evidence is the R54 unit list and the R55 journey matrix, and
nothing beyond them. No pixel matching, coverage gate or cross-browser matrix.

## Layout

```text
frontend/                      standalone npm project; no root workspace
├── package.json  package-lock.json  vite.config.ts  tsconfig*.json  eslint.config.js  .prettierrc
├── vitest.config.ts  playwright.config.ts  index.html  public/ (manifest, icons)
├── src/
│   ├── main.tsx               fragment strip, then render (R08)
│   ├── app/                   router, shell and header
│   ├── contract/generated/    openapi-typescript output — git-ignored build output
│   ├── gateway/               GameGateway, EventStream, decode, problem → message
│   ├── session/               snapshot store, action slot, presence
│   ├── screens/               home create join waiting placement gameplay results unavailable
│   ├── components/            Header, Button, Card, Pill, Toasts, Stepper, Meter, Board, FleetTray,
│   │                          PlayerCard, StatCard, Dialog, Qr, ConnectionStatus (each with its CSS)
│   ├── i18n/                  translate.ts, en.ts, uk.ts
│   ├── preferences/  feedback/ (audio, haptics)  pwa/  styles/ (tokens.css, base.css — from the mockup)
├── tests/                     vitest — R54 items only
└── e2e/                       Playwright journeys + stack fixture
scripts/   install.sh  stack.mjs  dev.sh  local-prod.sh  verify.sh
config/local.env.example
docs/local-operation.md
```

| Area | Owns | Must not contain |
|---|---|---|
| `gateway/` | Every HTTP and SSE call, CSRF, decode, problem mapping (Constitution III: one gateway) | UI, game decisions |
| `session/` | The latest accepted snapshot per open game, connection state, the in-flight action | Rules, derived legality, wire copies |
| `screens/`, `components/` | Rendering a snapshot; offering `allowedActions` | `fetch`, `EventSource`, storage |
| `scripts/` | Process orchestration and the gate | Application code, a root `package.json` |

## Stack

Runtimes by major only (Constitution v1.3.2): Node 24, Java 25. Exact npm pins, all already proven together on
this machine in `/Users/ok/Development/GitHub/solitaire` (research D1):

`react`/`react-dom` 19.3.0 · `vite` 8.3.0 · `@vitejs/plugin-react` 6.1.1 · `typescript` 6.0.3 ·
`vite-plugin-pwa` 1.3.0 · `workbox-window` 7.4.1 · `vitest` 5.0.1 · `jsdom` 30.1.1 ·
`@testing-library/react` 16.3.3 · `@testing-library/user-event` 14.6.7 · `@testing-library/jest-dom` 7.0.1 ·
`@playwright/test` 1.63.0 · `@axe-core/playwright` 4.13.0 · `eslint` 10.11.0 · `@eslint/js` 10.0.1 ·
`typescript-eslint` 8.70.1 · `eslint-plugin-react-hooks` 7.1.1 · `eslint-config-prettier` 10.1.8 ·
`prettier` 3.9.8 · `@types/react`/`@types/react-dom` 19.3.0 · `@types/node` 26.6.2.
Added for this feature: `openapi-typescript` 7.13.0 (same as `contracts/`), `openapi-fetch` 0.17.0, `qrcode`
1.5.4 + `@types/qrcode` 1.5.6 (master's QR library, D7).

**Deliberately not added:** TanStack Query (D2), Zod or any hand-written wire schema (D3, R56), a router library
(D4), an i18n library (D5), `eslint-plugin-jsx-a11y` (ESLint 10 support unverified; axe proves S4), MSW or any
fixture/fake backend (R53), Firefox/WebKit projects (spec Assumption 2).

## Runtime design

### Boot and routing (R06, R08, R38)

`main.tsx` reads `#invite=` on `/join/:gameId` into a module-scoped variable and calls `history.replaceState`
with the fragmentless URL **before** `createRoot().render`. A History-API router serves `/`, `/create`,
`/join/:gameId`, `/games/:gameId`; anything else renders Home. Reload of a game route rebuilds from `getGame`.
The Shell persists across routes; its global Game rules action loads `listRulesets` through the gateway on demand (T007), and Settings remains available in the Header.

### Gateway (R03, R13, R22, R23, R56)

- Types and enum value arrays only from `src/contract/generated/api.ts` (`openapi-typescript --enum-values`; a
  `.ts` file because the arrays are runtime values). A `generate` script runs before every script that reads
  `src/` (`predev`, `prebuild`, `pretypecheck`, `prelint`, `pretest`); output never committed. Calls through
  `openapi-fetch`, same-origin credentials.
- CSRF: `getMeta` before the first POST; a `403 request-security-rejected` refreshes meta and resends that request
  once — create and join included (R03). A `createGame` that ends in a network failure or an unreadable answer is
  an unknown outcome and is never resent (R13); a problem answer (422, 429, 503, `browser-game-limit`) is a definite
  refusal with its own message (R22).
- Decode sniff: JSON content type, `JSON.parse`, and presence of required keys from a list typed
  `as const satisfies readonly (keyof GameSnapshot)[]`. Applies to HTTP bodies and SSE `data`. Failure → transport
  failure; the value never reaches the store (R23). This checks readability, not the schema (R56).
- Problems: `Record<ProblemCode, MessageKey>` (a removed entry fails typecheck) plus a status-class fallback for
  codes the client does not know — the contract declares its enums open. `retryAfterSeconds` disables the retry
  control until it elapses (R22). Unknown `allowedActions` values are dropped.

### Live stream (R24–R26)

One `EventSource` per open game. Before opening, the previous instance is closed and its late events ignored.
`closed`/`REPLACED` → "open in another tab", never reopened. `closed`/`GAME_UNAVAILABLE` → one `getGame`, no
reopen. `error` with `readyState === CLOSED`, or the tab becoming visible → `getGame`; reopen if it succeeds.
While reconnecting, a failed `getGame` without a definite problem (network error, proxy 502, unreadable answer)
is retried with backoff (1, 2, 4, then every 8 s), and a `429`/`503` after its `retryAfterSeconds`, until it
succeeds or returns 401/404/410 — the contract's
"poll every few seconds" fallback. A stream that fails again after a successful `getGame` reopens on the same
backoff, reset by its first received event, so a refused stream open never loops. Heartbeats send nothing.

### Session store (R01, R02, R05, R16, R19, R21)

`useSyncExternalStore` store per open game: [data-model.md](data-model.md). One version gate for every
snapshot source (command, presence, invitation, `getGame`, SSE): higher version replaces; equal version merges
only the fields the contract excludes from `version` (`expiresAt`, `serverTime`, and — after fix 4 below —
`invitationUrl`/`invitationExpiresAt`); lower is dropped. One action slot: a new `commandId` per user action, the
same one on retry after a lost answer; controls disabled while it is pending. A command with no answer after
10 s is aborted and, like a network failure, puts the slot in *checking*. The slot settles only on that
action's own HTTP answer — an SSE snapshot updates the board but leaves a lost action in *checking* until the
player retries (R19, R24). Presence does not use the action slot:
visible tab, `SEND_PRESENCE` allowed, every `limits.presenceIntervalSeconds` from `getMeta`; the timer restarts
(does not fire) on visibility return or reconnect.

### Outcome → screen (R07, R12, R15, R20, R27, R28)

| Server answer | Client outcome |
|---|---|
| `410 game-expired` | Session unavailable — "game expired"; forget remembered id |
| `401 session-required`, `404 game-unavailable` | Session unavailable — "no longer available (ended, left, or server restarted)"; forget id |
| `404` after the caller's own leave | Leave succeeded → Home |
| join `409 invitation-unavailable` | Invitation unavailable (R15) |
| `409 browser-game-limit` (new, R57) | "You already have a game" + Return to your game if an id is remembered, else "play again once it expires" |
| other `409` on a command | `getGame`, then the worded reason (R20) |
| `429`, `503` | Calm wait message with `retryAfterSeconds`; no automatic retry |
| Host `WAITING` snapshot without `invitationUrl` | Invitation expired, sharing unavailable (R12) |

Replace invitation is offered whenever `NEW_INVITATION` is allowed, expired or not, and sent only after the host
confirms the current link stops working (R12). The client reads `getGame` once at `invitationExpiresAt`
(offset-corrected) so the server's answer, not the clock, shows the expiry. Countdowns display server times
corrected by the `serverTime` offset; they never decide expiry.

### Leaving, layouts, dialogs (R28–R30, R39, R41)

- Leave in `WAITING`/`PLACEMENT`: confirmation dialog → `leaveGame`; Home after `204` or `404` (already left).
  In `PLAYING` the leave path is Settings → Resign match (shown while `RESIGN` is allowed): the dialog says it
  means resigning → `RESIGN` command. Header Refresh calls `getGame` through the version gate. Leave results: `leaveGame` from a finished
  or abandoned result → Home; forget the remembered id.
- Layouts are CSS only (D18): below the mockup's `app` container width of 47.5rem a `role="tablist"` Enemy
  waters / Your fleet switch shows one board, with turn pill, player cards and action status outside the tab
  panels; `(max-height: 31.25rem) and (orientation: landscape)` shows two compact boards and no tabs; otherwise
  both boards side by side. No horizontal overflow (R39).
- One `Dialog` component on native `<dialog>` + `showModal()` (focus containment, Escape → `cancel`), labelled by
  its heading, returns focus to the opener; Escape is disabled where dismissal is unsafe (pending action) (R41).

### Placement, play, results (R17, R18, R21, R31, R32, R36)

- Board: `role="grid"` with one roving tab stop (Arrow keys, Home/End; Enter/Space activates), each cell labelled
  with coordinate and state ("A1, hit"); fired and read-only cells `aria-disabled`. Tracks and column/row labels
  come from `ruleset.board` (letters A–Z, then AA…; numbers from 1).
- Placement: cell → ship-action dialog (unplaced ship chips + Horizontal/Vertical; or move/rotate/remove for a
  placed ship). Move and rotate are one `PLACE_SHIP`. Drag per D19 from a fleet-tray tile or a placed ship sends
  the same `PLACE_SHIP`; the dialog path stays. Ready opens a confirmation (the fleet locks) before `READY`.
- Play: fire only on `UNKNOWN` opponent cells while `FIRE` is allowed. Announcements and sound/haptics trigger once
  per new `lastShot` (`by` + `target`).
- Results render `outcome`, both boards, fleet-condition tiles and the three stat cards from `statistics` as
  supplied; display counts are the data-model's derived values only.

### Visual design (R58–R61, D17)

- `styles/tokens.css`: the mockup's custom properties, dark on `:root`, light on `[data-theme="light"]`;
  `themeController` sets `data-theme` and resolves `system` via `matchMedia('(prefers-color-scheme: dark)')`,
  following its changes. `base.css`: system font stack, focus-visible ring, the mockup's `forced-colors` rule, and
  `prefers-reduced-motion: reduce` disabling transitions and animations.
- Components and their CSS are ported from the mockup's markup and rules, re-expressed as React components; every
  surface's widget list and data source is `contracts/ui-surface.md` § *Surfaces and widgets*. Icons are the
  mockup's Unicode glyphs; emoji only when the emoji preference is on and `aria-hidden`.
- Interaction: hover styles only inside `@media (hover: hover)`; controls keep the mockup's `--control-h`
  (2.75rem) and `touch-action: manipulation`; board cells fill the grid width with `aspect-ratio: 1`.

### Preferences, i18n, feedback, PWA (R32–R35, R42–R44)

- Translator, storage wrapper and theme controller adapted from solitaire `src/i18n/translate.ts`,
  `src/features/persistence/storageGateway.ts`, `src/app/themeController.ts` (Redux removed). Ruleset and ship
  names by id with raw-id fallback (R35). Theme handling: § *Visual design*.
- Audio: master `frontend/src/audio/audioRecipes.ts` + `AudioFeedback.ts` (WebAudio, swallow-all failures). Haptics:
  `navigator.vibrate` behind a capability check. Both no-op on denial.
- PWA: solitaire `src/pwa/` (`registerType: 'prompt'`, update applied only after consent, R44); workbox precaches
  static assets only, `navigateFallbackDenylist: [/^\/api\//]`, no runtime caching (R42). Offline: `navigator.onLine`
  + `online`/`offline` events → "network required", game actions disabled (R43).

### Contract and backend fixes (R57)

1. **`browser-game-limit` (409)** — new `ProblemCode` on `createGame` and `joinGame`, with an example, in
   `contracts/openapi.yaml`. Backend: `SessionRegistry.admitGame` (`SessionRegistry.java:58-59`) throws a distinct
   exception instead of `CapacityExceededException`; `CreateGameUseCase` (`:99`) and `JoinGameUseCase` (`:118`) map it
   to the new code; `ProblemAdvice` maps it to 409 with no `Retry-After`. `createGame` gains a `409` response
   (it has none today). Dependents updated in the same change: `CapacityIT` (its header, `:28`, says every ceiling
   answers 503) and `specs/002-backend/spec.md` R62/S11 wording. Proof: `CapacityIT` expects 409
   `browser-game-limit` for the cap and still 503 for a full server; `WireConformanceIT` covers the example.
2. **Forked test JVM lingers 30 s** — Failsafe dumpstream: "kill self fork JVM … 30 seconds after System.exit(0)";
   shutdown hook waits in Tomcat `GracefulShutdown`. Diagnose the cause first (hypothesis: ITs inheriting
   `server.shutdown: graceful` with a 5 s phase timeout), fix at the root without weakening `GracefulShutdownIT`.
   Proof: a clean `./mvnw -q verify` leaves no "kill self fork JVM" line in `app/target/failsafe-reports/*.dumpstream`.
3. **Actuator on the public port** — `spring-boot-starter-actuator` (`app/pom.xml:169`) has no use and no
   `management.*` config, contradicting 002 plan ("Actuator stays off the public surface"). Confirm
   `/actuator/health` answers, then remove the dependency (or disable web exposure if something needs it).
   Proof: `PackagedArtifactIT` asserts `404` on `/actuator/health`. Agent-proposed; owner approved 2026-10-18.
4. **Invitation lapse is invisible to `version`** — `GameSlot.contextFor` (`GameSlot.java:145-163`) drops
   `invitationUrl` once its deadline passes, but no version bump or push happens, while `GameSnapshot.version`
   (`openapi.yaml:1029-1035`) claims it grows on every view change. Fix in the contract only (D16): `version`
   also excludes `invitationUrl` and `invitationExpiresAt`, which — like `expiresAt` — are always current. The
   backend already behaves so; no backend change; `contracts/README.md` gets a matching note. Delivered with the waiting-room task. Proof: `npm run check`;
   the waiting journey on the short-lifetime stack shows "invitation expired" without a reload.

### Local topology (R45–R52)

| Mode | Frontend | Backend | `BATTLESHIP_PUBLICBASEURL` |
|---|---|---|---|
| Development (`scripts/dev.sh`) | `vite` on `localhost:5173`, `strictPort` | JAR on `:8080` | default `http://localhost:5173` |
| Production-style (`scripts/local-prod.sh`) | `vite preview` of `dist/` on `localhost:4173`, `strictPort` | JAR on `:8080` | `http://localhost:4173` |
| Command checks (`smoke`) | `vite` on 5174 / `vite preview` on 4177 | JAR on 8084 / 8085 | matching frontend origin |
| Journeys | `vite preview` per stack: default `4173`, short-lifetime `4174`, lowered-limit `4175`, restart `4176` | JAR per stack: `8080`, `8081`, `8082`, `8083` | matching frontend origin |

`vite.config.ts` reads `FRONTEND_PORT` (default 5173 for `vite`, 4173 for `vite preview`) and `BACKEND_PORT` (default 8080); `stack.mjs` passes `BACKEND_PORT` to the JAR as `SERVER_PORT` (the backend sets no `server.port`).

- Both Vite servers proxy `/api` to `http://127.0.0.1:<backend port>` (SSE streamed, no compression); `vite
  preview` serves `index.html` for deep links (D10). The backend accepts unsafe requests only from that exact
  origin, so `localhost` is used everywhere, never `127.0.0.1`, for the page.
- `scripts/stack.mjs` (Node standard library only): starts the JAR with env overrides and the frontend server,
  waits for `GET /api/v1/health` `ready`, prefixes and redacts output (drops `Set-Cookie`, `#invite=`, cookie
  values), names component + address + next step on failure, stops only the processes it started (R48, R51). It is
  the CLI behind `dev.sh`/`local-prod.sh` and is imported by the Playwright fixture (D11).
- The default journey stack raises the `create-game`/`join` rate limits (keyed by client address; all proxied
  traffic shares one) and the per-session `commands`/`read-game`/`stream-open` limits (a full game sends ~100
  commands per player against a 60/min default). Dedicated stacks set short `idle-timeout-seconds`, `presence-interval-seconds`,
  `sweep-interval-seconds`, `result-retention-seconds`, `invitation-lifetime-seconds`, or a lowered rate limit,
  through existing env keys only (R50, D12). Restart = stop and start the same JAR.
- `scripts/install.sh`: checks `java` major 25 and `node` major 24, `npm ci` in `contracts/` and `frontend/`,
  `npx`-free Playwright Chromium install via the frontend script, `backend/mvnw -q validate` (R45).

## Validation

Every task delivers one behaviour and its own proof in the same change (AGENTS.md rules 4–5, memory
*tasks carry their own proof*). The tables are the evidence map, not a separate slice of work.

Journey files (`frontend/e2e/`, Chromium, `workers: 1`, two isolated browser contexts per game), the complete
list: `smoke`, `settings`, `create`, `waiting`, `invitation`, `placement`, `play`, `results`, `leaving`,
`lifetime`, `recovery`, `accessibility`, `offline` (`*.spec.ts`).

| Proof | Owner |
|---|---|
| R54: key parity, preference persistence, sound/haptic no-op on denial, QR rendering of a string, decode rejection | `frontend/tests/*.test.ts(x)` (vitest, jsdom) |
| Proxy, SSE through the proxy, deep-link reload, occupied-port failure message; `stack.mjs dev` (5174/8084) and `stack.mjs prod` (4177/8085) each start, answer health and stream one snapshot, and stop their children (R46–R48, S7) | `smoke` |
| Settings apply immediately, survive reload, mid-game toggles of sound/haptics (R33, S5) | `settings` |
| Each advertised ruleset's facts before submit; single create request; a wrong `XSRF-TOKEN` → one 403, one `getMeta`, one resend (R03); Home then offers Return to your game; with storage cleared a second create shows the `browser-game-limit` message and is not retried; a create answer hidden by `page.route` → `route.abort()` shows "outcome unknown" and sends no second create (R04, R07, R13) | `create` |
| For each advertised ruleset, create in a fresh browser context and compare all five waiting-room facts with `listRulesets`; verify invitation lifetime, copy/QR, replace after confirmation, exact waiting notice, live REPLACED terminal status/no reopen, and invitation expiry without reload on the short-lifetime stack (R11, R12, R24, R26, R59) | `waiting` |
| Fragment gone before render, explicit join, two-guest race; join refused with `browser-game-limit` shows the R07 message, a join `410` follows R27; scan of URL, history, `localStorage`, `sessionStorage`, console and captured `stack.mjs` output for the secret, `Set-Cookie` and cookie values (R08–R10, R15, R51, S2) | `invitation` |
| Place/move/rotate/remove, refusal wording, random/clear, ready after confirmation once, one tray drag with a mouse and one ship drag in a touch context (R17, R18, R61) | `placement` |
| Fire, turn per ruleset, live update, action race (Classic; a `page.request` shot against a stale view); lost answer — `page.route` → `route.fetch()` reaches the real server, `route.abort()` hides the answer, the page shows *checking*, the retry request carries the same `commandId`, the board shows the action applied once (R19–R21, D13) | `play` |
| One full game per ruleset; statistics as supplied (R36, S1) | `results` |
| Leave before play, resign, abandoned view, leave results (R28–R30) | `leaving` |
| Expiry → "expired" then "no longer available" after retention; presence extends expiry; restart → "no longer available" (R16, R27, R50) | `lifetime` |
| Stream drop and tab return refresh without repeating an action; a second tab in the same context opens the game → the first shows "open in another tab" and opens no new stream; offline disables fire controls and Refresh (R43); `429` with wait hint under a lowered read limit; reopen backoff under a lowered stream-open limit (R22, R25, R26) | `recovery` |
| Three layouts at 390×844, 844×390, 1280×800 with no horizontal overflow, tab control, keyboard-only journey, touch-only journey in a `devices['Pixel 7']` context (`hover: none`), axe serious/critical = 0, forced colours and reduced motion via `emulateMedia`, dialogs focus/Escape (R39–R41, R61, S4) | `accessibility` |
| Screenshots of the 19 surfaces named in tasks T018 (every screen, dialog and status overlay of R38) at the three sizes in dark and light into `frontend/test-results/visual/`; the owner's comparison with the mockup recorded in the task (R58–R60, S8) | `accessibility` |
| Offline shell ≤ 3 s with "network required" and no request sent; `caches` hold no `/api` URL; update prompt after rebuilding into the served `dist/` while `vite preview` keeps serving the same origin (R42–R44, S6) | `offline` |
| No development address in `dist/` (R47) | `frontend` `npm run check` build step |
| Contract and backend fixes (R57) | `contracts` `npm run check`; `CapacityIT`; `PackagedArtifactIT`; Failsafe dumpstream |
| Runtime check and exact installs (R45); gate fails on a broken stage (R49, S7) | `scripts/verify.sh` stage 0 runs `install.sh`; one run with a stage forced to fail |

| Success criterion | Owner |
|---|---|
| S1 full game per ruleset | `results` |
| S2 secret never leaks | `invitation` |
| S3 honest recovery | `lifetime`, `recovery`, `play` |
| S4 keyboard, no horizontal scroll, no serious/critical axe violation at 3 sizes | `accessibility` |
| S5 sound/haptics never change outcome | `tests/feedback.test.ts` + `settings` |
| S6 offline shell ≤ 3 s, nothing sent | `offline` |
| S7 clean-clone commands, gate fails on broken stage | `scripts/verify.sh` evidence |
| S8 screens compared with the mockup | `accessibility` screenshots + owner review |

## Gate

Created by this feature. From the repository root:

| Command | Proves |
|---|---|
| `./scripts/install.sh` | runtime majors, exact lockfile installs, Playwright Chromium, wrapper valid (R45) |
| `./scripts/dev.sh` | backend + Vite dev, one origin, live updates (R46) |
| `./scripts/local-prod.sh` | packaged JAR + built `dist/` behind one origin (R47) |
| `./scripts/verify.sh` | stage 0 `install.sh`, then in order: `contracts` `npm run check` → `backend` `./mvnw -q verify && ./mvnw spotless:check` → `frontend` `npm run check` (format, lint, typecheck, vitest, build, no-dev-address) → `frontend` `npm run e2e` against the packaged JAR. Any failed, skipped or timed-out stage exits non-zero (R49) |

## Forecast

20 tasks, ≈120 files (≈85 frontend source and CSS, ≈20 unit/e2e, ≈10 scripts/config/docs, ≈5 contract/backend).
Visual work adds no task: tokens and initial shared components belong to task 6, task 8 introduces Pill, Toasts, Stepper and Meter at their first callers, and each screen task delivers its own
widgets from `contracts/ui-surface.md`, and screenshots belong to task 18.
At the 20-task line of AGENTS.md rule 3, not above it; any further task needs owner approval.

| # | Task | Proof it carries |
|---|---|---|
| 1 | `browser-game-limit` in contract + backend (fix 1) | `npm run check`, `CapacityIT` |
| 2 | Backend: forked test JVM linger (fix 2) | clean dumpstream |
| 3 | Backend: actuator off the public port (fix 3) | `PackagedArtifactIT` |
| 4 | `install.sh` + `frontend/` scaffold, pins, lint/format, type generation | clean install; renaming a contract operation fails typecheck |
| 5 | `stack.mjs`, `dev.sh`, `local-prod.sh`, `config/local.env.example`, Playwright config + stack fixture | `smoke` |
| 6 | Shell: router, header, i18n, preferences, tokens + theme (incl. system), Home, buttons/cards, dialog primitive and Settings | key parity, persistence units; `settings` |
| 7 | Gateway + session store + Home/Create + server-backed Rules dialog and shared ruleset facts | decode unit; `create` |
| 8 | Waiting room: each advertised ruleset's facts, waiting notice, copy, QR, replace, invitation lifetime/expiry, live replacement and presence + shared Pill/Toasts/Stepper/Meter components + contract fix 4 | QR/Toast/EventSource units; `waiting` |
| 9 | Join: fragment strip, explicit join, race | `invitation` |
| 10 | Placement: board grid + keyboard model, fleet tray, ship-action dialog, random/clear, ready confirmation, refusals | `placement` |
| 11 | Drag placement (Pointer Events: tray and placed ship; mouse and touch) | drag steps in `placement` |
| 12 | Play: player cards, turn pill, fire, turns, live updates, announcements, action race, lost answer | `play` |
| 13 | Results: hero, fleet tiles, stat cards, boards + one full game per ruleset | `results` (S1) |
| 14 | Sound + haptics | no-op unit; `settings` mid-game toggles |
| 15 | Leave, resign, abandoned view, leave results | `leaving` |
| 16 | Session unavailable, expiry, presence extension, restart | `lifetime` (dedicated stacks) |
| 17 | Stream recovery with backoff, rate-limit refusal | `recovery` |
| 18 | Three layouts, tab control, keyboard, touch, a11y, screenshots for mockup review | `accessibility` (S4, S8) |
| 19 | PWA: installable shell, offline state, update prompt | `offline` (S6) |
| 20 | `verify.sh` + `docs/local-operation.md` + AGENTS.md gate/Definition-of-Done update | full run green; broken-stage run red (S7) |

## Risks

- `__Host-`/`Secure` cookies over `http://localhost` are relied on in Chromium only (spec Assumption 2).
- `vite preview` as the production-style server: SSE streaming and deep-link fallback are proven by the
  `smoke` and `waiting` journeys (tasks 5 and 8); if either fails, `stack.mjs` gains a Node standard-library static server, recorded as
  a decision.
- The page must be opened on `localhost` with the configured port; any other host gets `403` on every POST.
- Playwright 1.63.0 browser revision must be installed by `install.sh`; the cache on this machine is not assumed.

## Complexity Tracking

Nothing to justify.
