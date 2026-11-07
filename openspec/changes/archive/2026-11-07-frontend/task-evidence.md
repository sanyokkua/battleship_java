# Tasks: Battleship Frontend and Local Integration

> Script consolidation of 2026-11-07: see the note at the top of [plan.md](plan.md); command names below are those of the day they were written.


**Feature**: `003-frontend` · **Branch**: `feature/003-frontend` · **Date**: 2026-10-18
**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)
**Data model**: [data-model.md](data-model.md) · **UI surface**: [contracts/ui-surface.md](contracts/ui-surface.md) ·
**Run guide**: [quickstart.md](quickstart.md)
**Contract (authority)**: [`contracts/openapi.yaml`](../../contracts/openapi.yaml)
**Governance**: `openspec/config.yaml` v2.0.0 · [`AGENTS.md`](../../AGENTS.md)

**Assurance level**: Standard (plan.md § *Assurance level*). **Forecast**: 28 total tasks and approximately 71 distinct changed files for the current extension; the owner approved raising the earlier 40-file ceiling as needed (plan.md § *SonarQube and local-image extension*). T024 remains open because Sonar source-line access returned 403; the other listed tasks are complete with the reported Sonar exceptions. The approved mobile Pulse correction completed the reopened T012 and T018; the earlier UI correction completed T007, T009, T010, T011 and T018.

**Mobile Pulse evidence (2026-11-06)**: `./scripts/verify.sh` passed all five stages including 103 real-backend browser journeys. After that gate, an additional focused real-backend test passed: firing with focus on a board cell moves focus to the visible fleet tab during handoff. The journeys cover delayed handoffs, retained turns, manual selection, rapid shots, teardown, resize, short landscape, reduced motion and offline/reconnect without replay. A zero-delay mutation failed because the receiver's last-shot cell was hidden before inspection; removing both reduced-motion protections failed with `animation-name: shot-pulse` instead of `none`. Both were restored and focused tests passed. The responsive and keyboard regressions each passed five repeated focused runs after the first gate exposed their race. Backend SonarQube passed. Frontend SonarQube quality gate failed on 24.8% new-code Vitest coverage and 14 new findings; browser journeys are excluded from that coverage report. A very brief EventSource-only interruption and a manual selection racing a delayed layout callback were not separately reproduced.

**UI correction evidence (2026-11-06)**: `./scripts/verify.sh` passed all five stages, including 99 real-backend browser journeys. Classic fleet aggregation mutation failed with expected `2 × 3`, received `1 × 3`. Excluding the moving ship incorrectly removed the expected legal direction; permitting an invalid direction exposed a forbidden option. Removing both touch guards and setting `touch-action:none` failed with one drag preview instead of zero. Bypassing drop legality failed with direction buttons instead of `No ship fits here.` All mutations were restored. A real-backend race returned 409 for the move, retained the original ship and updated progress. Phone/desktop light/dark captures of Home, Create, Join, Waiting, tray and placement popups were compared with the mockup; the identified size, color, layout and edge-overlap differences were corrected. Touch gestures used Chromium emulation, not physical hardware. Backend SonarQube passed; frontend SonarQube failed its quality gate on 31.6% new-code Vitest coverage and eight new findings, with browser coverage excluded.

## How to run these tasks

One task per session, in ID order unless marked `[P]`. Each block is self-contained.

```text
- [ ] Tnnn [P?] [US?] <action> in <path>
  - **Covers**     R/S ids
  - **Read first** minimum authority
  - **Files**      created / modified
  - **How**        the design to follow
  - **Proof**      tests and their numbered assertions
  - **Verify**     exact command (repository root unless stated)
  - **Mutation**   named removal that must make the proof fail (AGENTS.md rule 5)
  - **Depends on** earlier ids only
```

- **Every task ships its own proof** — no test phase (memory *tasks carry their own proof*; departs from
  `the former task template`). Record the mutation's failing output in the task's commit message body.
- **No fake backend.** No MSW, recorded snapshots, fixtures of game data or reimplemented rules (R53). Units only
  for the R54 list plus `snapshotGate` (decision B1), including the approved advisory placement checks. Layout is CSS only (research D18), so it has no unit.
- **Visual source.** `docs/design/naval-command-center-mockup.html` is the
  styling reference (spec R58–R61); `contracts/ui-surface.md` § *Surfaces and widgets* lists each screen's widgets
  and data sources. A screen task ports that screen's markup/CSS into React components; R60 items are never built. Game flows are Playwright journeys against the packaged JAR.
- **Branches**: a task needing more than one commit uses `feature/003-frontend--tNNN` from `feature/003-frontend`,
  squash-merged back at closeout (Constitution VIII).
- **Labels**: `[US1]`–`[US8]` = spec.md user stories. Phases 1–2 carry none.

## Decisions this task list settles

| # | Decision | Applied |
|---|---|---|
| B1 | `[agent]` One ordering-only unit `frontend/tests/snapshotGate.test.ts` for the R05 version gate. Inputs are `{version, expiresAt, serverTime, invitationUrl?, invitationExpiresAt?}` only — no game data — so it falls under R54's rule ("behaviour that involves no game data"); no journey can deterministically fail on a broken gate. Cost: one test file, no task | T007 |
| B2 | `browser-game-limit` joins the shared `Conflict` response (`openapi.yaml` `components.responses.Conflict`, ~L638), its description scoped "(create, join)"; `createGame` gains `'409': $ref Conflict`. One response component, not a second 409 shape | T001 |
| B3 | Journey backends take their port from `SERVER_PORT` (the backend sets no `server.port`); `stack.mjs` maps `BACKEND_PORT` → `SERVER_PORT` | T005; plan.md § *Local topology* |
| B4 | Touch-scroll gestures in journeys use a Chromium CDP session (`Input.dispatchTouchEvent` touchStart → touchMove → touchEnd) in a context from Playwright's `devices['Pixel 7']` descriptor (`isMobile` + `hasTouch`, so `(hover: none)` matches); Playwright has no touch-drag API. Taps use `locator.tap()` | T011, T018 |
| B5 | The owner's mockup comparison (S8) is recorded as a `Visual review:` paragraph in T018's commit message body, listing each surface × size × theme and any accepted deviation | T018 |

## Conventions

| Item | Value |
|---|---|
| Frontend root | `frontend/` (standalone npm project; no root `package.json`) |
| Generated types | `frontend/src/contract/generated/api.ts` (git-ignored; `npm run generate`) |
| JAR | `backend/app/target/battleship-app-1.0.0-SNAPSHOT.jar` (`cd backend && ./mvnw -q -DskipTests package`) |
| Health | `GET /api/v1/health` → 200 `{live: true, ready: true}` (503 `{ready:false, reason}` while starting) |
| Journey stacks (UI / backend) | default 4173/8080 (one per test run) · short-lifetime 4174/8081 · lowered-limit 4175/8082 · restart 4176/8083. Dedicated stacks are started per spec file with that file's `env` and stopped in `afterAll`, so two files may reuse a port with different settings |
| Visibility | Playwright pages always report `visible`; journeys that need a hidden tab use `page.evaluate` to override `document.visibilityState` → `'hidden'` and dispatch `visibilitychange` |
| Deterministic fleets | Journeys needing a known hit/sink place the opponent's fleet through that player's UI at fixed anchors, then read `turn` from the snapshot (first mover depends on ready order, `GameRules.java:174-180`) |
| No backend fakes | `route.fulfill` is forbidden in `e2e/`; the only interceptions are `route.fetch()` then `route.abort()` (lost answer) and a plain `route.abort()` (network drop, e.g. of `**/events`). T005 adds an ESLint `no-restricted-syntax` rule for `fulfill` over `e2e/**` |
| Backend env keys | `BATTLESHIP_PUBLICBASEURL`, `BATTLESHIP_IDLETIMEOUTSECONDS`, `BATTLESHIP_PRESENCEINTERVALSECONDS`, `BATTLESHIP_SWEEPINTERVALSECONDS`, `BATTLESHIP_RESULTRETENTIONSECONDS`, `BATTLESHIP_INVITATIONLIFETIMESECONDS`, `BATTLESHIP_RATELIMIT_CREATEGAMEPERMINUTE`, `BATTLESHIP_RATELIMIT_JOINPERMINUTE`, `BATTLESHIP_RATELIMIT_COMMANDSPERMINUTE`, `BATTLESHIP_RATELIMIT_READGAMEPERMINUTE`, `BATTLESHIP_RATELIMIT_STREAMOPENPERMINUTE`, `SERVER_PORT` — all `@Min(1)` (`backend/app/src/main/java/ua/kostenko/battleship/app/config/BattleshipProperties.java`). Create/join limits are keyed by remote address (all proxied traffic = 127.0.0.1); the others by session cookie |
| Rulesets | `sea-battle-10-ship.v1` (ships `ship-4`,`ship-3`,`ship-2`,`ship-1`; no touch, extra turn, reveal water) · `hasbro-classic-2002.v1` (`carrier`,`battleship`,`destroyer`,`submarine`,`patrol-boat`; touch, no extra turn, no reveal) |
| Command-check stacks | `stack.mjs dev` on UI 5174 / backend 8084 and `stack.mjs prod` on 4177 / 8085 (T005), so they never collide with the journey stacks |
| Viewports | phone portrait 390×844 · phone landscape 844×390 · laptop 1280×800 (the mockup's presets) |
| Visual output | `frontend/test-results/visual/<surface>-<size>-<theme>.png` (git-ignored build output) |
| Unit command | `cd frontend && npm test -- <file>` |
| Journey command | `cd frontend && npm run e2e -- e2e/<name>.spec.ts` (needs the JAR and `npm run build`) |

---

## Phase 1: Contract and backend fixes (R57)

- [x] T001 [P] Add the `browser-game-limit` 409 refusal in `contracts/openapi.yaml` and `backend/application/src/main/java/ua/kostenko/battleship/application/registry/SessionRegistry.java`
  - **Covers** R07, R57 · research D14 · decision B2
  - **Read first** plan.md § *Contract and backend fixes* item 1 · `openapi.yaml` `ProblemCode` (L1342-1362), `createGame` responses (L180-210), `components.responses.Conflict` (~L638-664) · `SessionRegistry.java:51-60` · `CreateGameUseCase.java:99-100` · `JoinGameUseCase.java:118-119` · `app/web/ProblemAdvice.java:71,115,212`
  - **Files** `contracts/openapi.yaml`, `contracts/README.md` (Client rules: one line), `SessionRegistry.java`, new `application/registry/BrowserGameLimitException.java`, `CreateGameUseCase.java`, `JoinGameUseCase.java`, `ProblemAdvice.java`, `app/src/test/.../CapacityIT.java`, `app/src/test/.../WireConformanceIT.java`, `application/src/test/.../usecase/CreateGameUseCaseTest.java` (L62, L77, L205 — only assertions about the per-browser cap), `application/src/test/.../usecase/InvitationLifecycleTest.java` (L118, L122 — same rule; the games-ceiling assertions in these and `ExpireGamesUseCaseTest`/`LifetimeBoundaryTest` stay `service-unavailable`), `the original 002-backend artifacts` (R62 L381, S11 L565)
  - **How** Contract: add `browser-game-limit` to `ProblemCode`; add `browserGameLimit` example (`status: 409`, `title: Browser already in a game`) to `Conflict`; description: "(create, join) this browser already holds its live game; do not retry; play again once it ends or expires"; add `'409': $ref Conflict` to `createGame`. Backend: `admitGame` throws `BrowserGameLimitException` at line 58-59 (the sessions ceiling at 67/99 stays `CapacityExceededException`); both use cases map it to `ApplicationFailure("browser-game-limit", …, retryAfter null)`; `ProblemAdvice` maps `BROWSER_GAME_LIMIT` → `Entry(409, "Browser already in a game")`, no `Retry-After`. Regenerated `ProblemCode` comes from the build.
  - **Proof** `CapacityIT`: (1) `aBrowserAtItsLiveGameCapIsRefusedOnCreateAndOnJoin` expects 409 `browser-game-limit`, no `Retry-After`; (2) games/streams ceilings still 503 + `Retry-After`; header comment (L28-29) reworded. `WireConformanceIT`: (3) `conflictCodes()` (~L610) gains `browser-game-limit` and `conflict(code)` a driver (one browser creates twice); (4) `everyEmbeddedExampleConformsToItsSchema` covers the new example.
  - **Verify** `cd contracts && npm ci && npm run check` · `cd backend && ./mvnw -q verify && ./mvnw spotless:check`
  - **Mutation** Map `BrowserGameLimitException` back to `service-unavailable` in `CreateGameUseCase`; assertion (1) must fail.
  - **Depends on** —

- [x] T002 Stop the forked test JVM lingering 30 s after `verify` in `backend/`
  - **Covers** R57 · spec Assumption 5
  - **Read first** plan.md fix 2 · `backend/app/src/main/resources/application.yaml:7,14` (`server.shutdown: graceful`, `timeout-per-shutdown-phase: ${battleship.shutdown-drain-seconds}s`) · root `backend/pom.xml:92-106` (Surefire/Failsafe, default fork) · `app/pom.xml:17-27` · `GracefulShutdownIT`
  - **Files** determined by the diagnosis (expected: `app/src/test/resources/application*.yaml` or Failsafe config); no production behaviour change
  - **How** Use `systematic debugging`. Reproduce: `./mvnw -q verify`, then `grep -l 'kill self fork JVM' app/target/failsafe-reports/*.dumpstream`. Take a thread dump of the lingering fork (`jstack`) to name the blocking thread before changing anything. Fix the root cause (hypothesis: in-process ITs inherit graceful shutdown waiting on open SSE streams); do not lower `GracefulShutdownIT`'s expectations or kill the JVM.
  - **Proof** (1) a clean `verify` writes no dumpstream containing "kill self fork JVM"; (2) `GracefulShutdownIT` still passes unchanged.
  - **Verify** `cd backend && rm -rf app/target/failsafe-reports && ./mvnw -q verify && ! grep -l 'kill self fork JVM' app/target/failsafe-reports/*.dumpstream 2>/dev/null`
  - **Mutation** Revert the fix; the grep must find the line again.
  - **Depends on** T001 (same reactor; avoids concurrent `verify`)

- [x] T003 Remove Spring Boot Actuator from the public port in `backend/app/pom.xml`
  - **Covers** R57, R48 (health is the contract operation) · plan.md fix 3
  - **Read first** `app/pom.xml:169` · `PackagedArtifactIT` (starts the JAR with `java -jar`)
  - **Files** `backend/app/pom.xml`, `backend/app/src/test/java/ua/kostenko/battleship/app/PackagedArtifactIT.java`
  - **How** Confirm `/actuator/health` answers today (first run of the new assertion fails). No `management.*` config or actuate import exists, so delete the dependency.
  - **Proof** `PackagedArtifactIT`: (1) `GET /actuator/health` on the packaged JAR → 404; (2) `/api/v1/health` still `ready`.
  - **Verify** `cd backend && ./mvnw -q verify -Dit.test=PackagedArtifactIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false && ./mvnw spotless:check`
  - **Mutation** Re-add the dependency; assertion (1) must fail.
  - **Depends on** T002

---

## Phase 2: Foundation (blocks every story)

- [x] T004 [P] Scaffold `frontend/`, type generation and `scripts/install.sh`
  - **Covers** R45, R47 (no dev address), R56 · research D1, D6 · plan § *Stack*, § *Gateway* (generation)
  - **Read first** plan § *Stack* (exact pins) · solitaire `package.json`, `tsconfig.app.json`, `tsconfig.node.json`, `eslint.config.js`, `.prettierrc.json`, `vitest.config.ts` (paths under `/Users/ok/Development/GitHub/solitaire/`)
  - **Files** `frontend/{package.json,package-lock.json,tsconfig.json,tsconfig.app.json,tsconfig.node.json,eslint.config.js,.prettierrc.json,vite.config.ts,vitest.config.ts,index.html,src/main.tsx,src/app/App.tsx,tests/setup.ts,scripts/check-dist.mjs}`, `scripts/install.sh`, `.gitignore`
  - **How** Exact pins from plan § Stack (drop Redux, husky, lint-staged, coverage, react-refresh; `engines.node: ">=24 <25"`). Scripts: `generate` = `openapi-typescript ../contracts/openapi.yaml --enum-values -o src/contract/generated/api.ts`; `predev/prebuild/pretypecheck/prelint/pretest` run it; `build` = `tsc -b && vite build`; `check` = `format:check && lint && typecheck && test && build && node scripts/check-dist.mjs`; `e2e` = `playwright test`. ESLint: solitaire config minus its `no-restricted-imports` layer rules; add a rule forbidding `fetch`/`EventSource`/`localStorage` outside `src/gateway/` and `src/preferences/` (Constitution III); `src/session/` persists through `preferences/storageGateway.ts`. `vite.config.ts`: `react()` plugin only (T005 adds servers/proxy). Vitest: jsdom, `url: http://localhost/`, `passWithNoTests: true`, no coverage threshold. `check-dist.mjs` fails if `dist/` contains `localhost:`, `127.0.0.1` or `:8080`. `.gitignore`: `/frontend/dist`, `/frontend/dev-dist`, `/frontend/src/contract/generated`, `/frontend/*.tsbuildinfo`, `/frontend/e2e/.logs`, `/frontend/test-results`. `install.sh` (`set -euo pipefail`): `java -version` major == 25, `node -p process.versions.node` major == 24, `npm ci` in `contracts/` and `frontend/`, `npm --prefix frontend exec -- playwright install chromium`, `backend/mvnw -q -f backend/pom.xml validate`; each failure prints the step name and exits non-zero.
  - **Proof** (1) `./scripts/install.sh` exits 0 on this machine; (2) `npm run check` passes on the empty app (generation runs first; `api.ts` exists and is ignored by git); (3) `check-dist.mjs` fails on a `dist/` file containing `http://localhost:8080`.
  - **Verify** `./scripts/install.sh && (cd frontend && npm run check)`
  - **Mutation** Put a `PATH` shim first whose `node -p process.versions.node` prints `23.0.0`; `install.sh` must exit non-zero naming the runtime step. Remove the `localhost:` pattern from `check-dist.mjs`; (3) must fail.
  - **Depends on** — (parallel with T001; T005 needs T001's contract for generated `ProblemCode`)

- [x] T005 Build the stack orchestrator, dev/prod commands and the Playwright stack fixture in `scripts/stack.mjs`
  - **Covers** R46, R47, R48, R50, R51 · S7 (dev and prod commands start) · research D10, D11, D12 · decision B3 · US8
  - **Read first** plan § *Local topology* · contracts/ui-surface.md § *Root commands*
  - **Files** `scripts/stack.mjs`, `scripts/dev.sh`, `scripts/local-prod.sh`, `config/local.env.example`, `frontend/vite.config.ts`, `frontend/playwright.config.ts`, `frontend/e2e/fixtures/stack.ts`, `frontend/e2e/smoke.spec.ts`, `frontend/tests/redact.test.ts`, `frontend/eslint.config.js` (`route.fulfill` ban in `e2e/**`)
  - **How** `vite.config.ts`: `server`/`preview` `host: 'localhost'`, `port` from `FRONTEND_PORT` (dev 5173, preview 4173), `strictPort: true`, `proxy: {'/api': {target: 'http://127.0.0.1:' + BACKEND_PORT}}` on both (no `changeOrigin` needed; Origin must stay `http://localhost:<port>`). `stack.mjs` (Node stdlib only) exports `startStack({name, frontendPort, backendPort, mode:'dev'|'preview', env})` → `{origin, stop(), restartBackend()}`: refuses if a port is busy (message names component, address, `lsof -i :<port>`), spawns `java -jar <JAR>` with `SERVER_PORT`, `BATTLESHIP_PUBLICBASEURL=http://localhost:<frontendPort>` + `env`, polls `/api/v1/health` until 200 with `ready: true` (timeout 60 s → names component + next step), then spawns `vite`/`vite preview`, prefixes lines `[backend]`/`[frontend]`, redacts `Set-Cookie`, `Cookie`, `#invite=…`, `XSRF-TOKEN=…`, writes to `frontend/e2e/.logs/<name>.log`, kills only its children on stop/SIGINT. CLI: `node scripts/stack.mjs dev|prod`. `dev.sh` builds the JAR if absent; `local-prod.sh` packages the JAR and `npm run build` first. `config/local.env.example` lists every `BATTLESHIP_*` key with its default. Playwright: Chromium only, `workers: 1`, `testDir: e2e`, no `webServer`; fixture `stack` (worker-scoped default stack: `BATTLESHIP_RATELIMIT_CREATEGAMEPERMINUTE`, `…_JOINPERMINUTE`, `…_COMMANDSPERMINUTE`, `…_READGAMEPERMINUTE`, `…_STREAMOPENPERMINUTE` all `1000` — a full game sends ~100 commands per player against a 60/min default) plus `useStack(name, ports, env)` for per-file dedicated stacks. `stack.mjs` exports `redact(line)` and helper `twoPlayers()` giving two isolated contexts.
  - **Proof** `smoke.spec.ts`: (1) page at `http://localhost:4173/` renders; `page.request.get('/api/v1/health')` → `ready: true` through the proxy; (2) an `EventSource` opened via the proxy for a created game receives its first `snapshot` event (created with `page.request` after `getMeta`); (3) `GET /games/abc` and `/join/abc` reload return the app's `index.html`; (4) `startStack` on an occupied port rejects with a message containing `frontend`, the address and `lsof`; (5) unit `tests/redact.test.ts`: `redact()` removes `Set-Cookie`/`Cookie` values, `#invite=…` and `XSRF-TOKEN=…` from synthetic lines; (6) the CLI itself: `FRONTEND_PORT=5174 BACKEND_PORT=8084 node scripts/stack.mjs dev` and `FRONTEND_PORT=4177 BACKEND_PORT=8085 node scripts/stack.mjs prod` are each spawned as a child process; once it prints ready, a page at its origin gets `ready: true` from `/api/v1/health` and receives the first `snapshot` event of a created game through the proxy; SIGINT stops both children (ports free again).
  - **Verify** `cd backend && ./mvnw -q -DskipTests package && cd ../frontend && npm test -- tests/redact.test.ts && npm run build && npm run e2e -- e2e/smoke.spec.ts`
  - **Mutation** Remove the `preview.proxy` block; assertion (1) must fail. Remove the `server.proxy` block; (6) must fail for `dev`. Remove the `#invite=` rule from `redact()`; (5) must fail.
  - **Depends on** T003, T004

- [x] T006 Build the app shell, design tokens and shared components: router, header, i18n, preferences, theme, dialog and settings in `frontend/src/app/`
  - **Covers** R33, R34, R35, R38 (header and Settings), R41 (dialog primitive), R58, R61 (controls) · research D4, D5, D17 · S5 (partial)
  - **Read first** plan § *Boot and routing*, § *Visual design*, § *Preferences, i18n…* · data-model § *Persisted* · contracts/ui-surface.md § *Surfaces and widgets* (header, Home, settings dialog) · solitaire `src/i18n/translate.ts`, `src/features/persistence/storageGateway.ts` (port as-is), `src/app/themeController.ts` (Redux-coupled: replace the store with a `get/subscribe` pair, keep `data-theme`) · mockup tokens `:root` (L15-75) and `html[data-theme="light"]` (L77-118), base rules and `:focus-visible` (L160-185), app bar (L343-405), buttons/cards (grep `.btn`, `.card`, L1664), `forced-colors` (L1956), settings dialog (L2431)
  - **Files** `src/main.tsx`, `src/app/{router.ts,App.tsx,Shell.tsx,Header.tsx}`, `src/i18n/{translate.ts,en.ts,uk.ts,useT.ts}`, `src/preferences/{storageGateway.ts,preferences.ts,themeController.ts}`, `src/components/{Dialog,Button,Card}.tsx` with sibling `.css`, `src/screens/{Home.tsx,SettingsDialog.tsx}`, `src/styles/{tokens.css,base.css}`, `tests/{router.test.ts,i18nParity.test.ts,preferences.test.ts}`, `e2e/settings.spec.ts`
  - **How** Router: History API, patterns `/`, `/create`, `/join/:gameId`, `/games/:gameId`, else Home; `navigate()` + `popstate`. `main.tsx` renders `<App/>` (T009 inserts the fragment strip before render). Preferences in `localStorage` key `battleship.preferences` = `{locale:'en'|'uk', theme:'dark'|'light'|'system', emoji:boolean, sound:boolean, haptics:boolean}`; invalid stored value → defaults. `tokens.css`: copy the mockup's custom properties verbatim (dark on `:root`, light on `[data-theme="light"]`), font stacks reduced to the system stack. `base.css`: the mockup's base and focus-visible rules, its `forced-colors` rule, `@media (prefers-reduced-motion: reduce) { *, ::before, ::after { animation: none !important; transition: none !important; } }`, hover rules only inside `@media (hover: hover)`. `themeController` sets `data-theme` to `dark`/`light`, resolving `system` from `matchMedia('(prefers-color-scheme: dark)')` and following its `change` event. App frame is a size container (`container-name: app; container-type: inline-size`). Header (mockup app bar): brand, a title slot, Refresh slot (filled on game routes in T007), Settings; below the 47.5rem container width the labels collapse to the mockup's glyphs with accessible names. `Dialog`: native `<dialog>` + `showModal()`, `aria-labelledby` its heading, `cancel` prevented when `dismissible=false`, focus returns to the opener. Ruleset/ship names: keys `ruleset.<id>` / `ship.<shipTypeId>` with raw-id fallback. Settings: theme Dark/Light/System, emoji, language, sound, haptics (Resign match added in T014). Home: title, New game and Settings; T007 adds the global and Home Game rules actions and Return to your game (no Join game — R60).
  - **Proof** Units: (1) `en` and `uk` key sets equal; (2) unknown ruleset and ship identifiers fall back to their raw ids; (3) preferences round-trip and fall back on corrupt JSON and on `localStorage` throwing; (4) router maps the four routes and fallback correctly, and `navigate()` responds to `popstate`. `settings.spec.ts`: (5) switching to Ukrainian and light theme applies without reload and survives reload; (6) theme System with `emulateMedia({colorScheme:'light'})` then `'dark'` flips `html[data-theme]` without reload; (7) `getComputedStyle(document.documentElement).getPropertyValue('--accent')` equals the mockup's value per theme (`#3becb0` dark, `#1d7fa8` light); (8) a Home control has a minimum height of `2.75rem` and `touch-action: manipulation`; (9) Settings dialog is labelled, traps focus, closes on Escape, and returns focus to its opener.
  - **Verify** `cd frontend && npm test -- tests/router.test.ts tests/i18nParity.test.ts tests/preferences.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts`
  - **Mutation** Delete one key from `uk.ts`; (1) must fail. Return an empty string for unknown ruleset or ship ids; (2) must fail. Make `system` resolve to `dark` unconditionally; (6) must fail. Reduce the control height below `2.75rem` or remove `touch-action: manipulation`; (8) must fail.
  - **Depends on** T005

---

## Phase 3: US1 — Host creates and shares (P1) 🎯 MVP

**Independent test**: create a game per ruleset; waiting room shows facts, lifetime, copy and QR.

- [x] T007 [US1] Build the gateway, session store, the Home/Create screens and the Rules dialog in `frontend/src/gateway/`
  - **Covers** R01, R02, R03, R04, R05, R07, R13, R22, R23, R37, R38 (Home and Rules dialog), R56 · US1 scenarios 1–2 · research D2, D3, D6 · decision B1
  - **Read first** plan § *Gateway*, § *Session store*, § *Outcome → screen* · data-model § *Version rule* and § *Persisted* · `openapi.yaml` operations `getMeta` (L85, cookie `XSRF-TOKEN`), `listRulesets` (L112), `createGame` (L161), `getGame` (L215), `ProblemCode` (L1342), `Problem.retryAfterSeconds`, shared responses (L600-726)
  - **Files** `src/gateway/{client.ts,csrf.ts,decode.ts,problems.ts,gameGateway.ts}`, `src/session/{snapshotGate.ts,gameStore.ts,useGame.ts,currentGame.ts}`, `src/app/{Header.tsx,Shell.tsx,router.ts}`, `src/screens/{Home.tsx,CreateGame.tsx,GameRoute.tsx,SessionUnavailable.tsx,RulesDialog.tsx}`, `src/components/{RulesetCard.tsx,RulesetFacts.tsx}` (+ `.css` for both), i18n keys, `tests/{decode.test.ts,snapshotGate.test.ts}`, `e2e/create.spec.ts`
  - **How** Implement only the operations this task calls (`getMeta`, `listRulesets`, `createGame`, `getGame`); later tasks add theirs (Constitution IX). `openapi-fetch` `createClient<paths>({baseUrl:'', credentials:'same-origin'})` with a middleware adding `X-XSRF-TOKEN` from `document.cookie` on POST; `getMeta` before the first POST; on `403 request-security-rejected` refresh meta and resend that request once (create/join included). `decode.ts`: require `application/json` (or `problem+json`), `JSON.parse`, and the keys in `REQUIRED_SNAPSHOT_KEYS = ['gameId','version','serverTime','rulesetId','phase','you','allowedActions','expiresAt','yourBoard','opponentBoard'] as const satisfies readonly (keyof GameSnapshot)[]`; failure → `TransportFailure`. Unknown `allowedActions` dropped. `problems.ts`: require a readable `application/problem+json` document matching the response status and contract fields; malformed/HTML errors throw `TransportFailure` and create displays outcome unknown. Map known `ProblemCode` values through `Record<ProblemCode, MessageKey>`, use only translated client messages, retain `retryAfterSeconds`, and disable resubmission until the server's wait hint elapses. `snapshotGate.accept(current, next)` per data-model § *Version rule*. Define `GameState` with `snapshot: GameSnapshot | undefined`, `meta: Meta | undefined`, `clockOffsetMs: number`, and `connection: 'connected' | 'reconnecting' | 'offline'`. Export `GameStore` with `subscribe(listener: () => void): () => void`, `getSnapshot(): GameState`, `setMeta(meta: Meta): void`, `acceptSnapshot(snapshot: GameSnapshot): void`, and `setConnection(connection: GameState['connection']): void`; export `getGameStore(gameId: GameId): GameStore` and `useGame(gameId: GameId): GameState`, where the hook subscribes with `useSyncExternalStore`. `setMeta` and accepted snapshots update the server clock offset; `acceptSnapshot` applies the version gate. `currentGame.ts` (via `storageGateway`): `battleship.currentGameId` is set after create and cleared when `getGame` reports 410/401/404; T009 adds join persistence and T014 adds leave clearing. On successful creation navigate to `/games/<gameId>` with history state `{toast: 'game-created'}`; extend `navigate(path, state?)` to accept that typed one-time signal, without storing game or invitation data there. Create (mockup Create screen L2048): ruleset radio cards (`RulesetCard`, `role="radio"`, Enter/Space selects) with fleet counts grouped by length from `fleet`; full facts from `board`, `fleet`, `shipsMayTouch`, `extraTurnOnHit`, `revealWaterAroundSunk` appear in View details; captain name `maxLength` 32, help "1–32 characters" (contract `DisplayName`); Back, View details (rules dialog). The global Header Game rules action and Home Game rules action fetch `listRulesets()` through this gateway when opened and show the server-advertised facts in the shared native dialog; Rules dialog uses `RulesetFacts`; Create uses selectable compact `RulesetCard` controls. Create game; submit disabled while pending; network failure/unreadable → "outcome unknown" + Home, never resent; `browser-game-limit` → message + Return to your game when an id is remembered; 422/429/503 worded. `GameRoute` loads `getMeta`, `listRulesets`, `getGame` (contracts/ui-surface.md § *Routes*), fills the header title with the translated ruleset name and its Refresh with `getGame` through the gate, and renders by `snapshot.phase` (placeholder per phase until later tasks) or SessionUnavailable (mockup L2328: status badge, 410 "expired" / 401·404 "no longer available — ended, left or server restarted", Create new game, Return home). Home gains Return to your game when an id is remembered.
  - **Proof** Units: (1) `decode` rejects HTML 502 body, truncated JSON, a JSON object missing `version`; accepts one with all required keys; (2) `snapshotGate`: higher replaces, equal keeps `version`/board but takes `expiresAt`, `serverTime`, `invitationUrl`, `invitationExpiresAt`, lower is dropped. `create.spec.ts`: (3) each advertised ruleset's aggregated fleet badges and the Details dialog values equal the server `listRulesets` response; Home's shared rules dialog and the global dialog from a real game route show labels and values matching the server response; (4) double-click on submit sends exactly one `POST /api/v1/games`; (5) game route loads from server response; reload rebuilds it; Home after closing/reopening the tab offers Return to your game; (6) deliberate second create receives one `browser-game-limit` refusal, displays its translated message, and sends no automatic retry; (7) a hidden create answer shows "outcome unknown" and sends no second `POST`; (8) header Refresh sends one `GET /api/v1/games/<id>`; (9) after removing `XSRF-TOKEN`, request log shows one rejected POST, one refreshed `GET /api/v1/meta`, and one successful resent POST; (10) real backend 429 displays its numeric retry wait and keeps submit disabled; (11) history retains `{toast: 'game-created'}` for T008, App does not consume/display it, and invitation secret URLs do not appear in DOM; (12) with meta/ruleset reads aborted, GameRoute still fetches the game; with a failed refresh it visibly marks connection unavailable while retaining the last snapshot; (13) a 404 for another game ID does not clear the remembered active game; (14) `decodeProblem` rejects HTML 502 so create handles an unreadable error as unknown, while live `route.fetch()` then `abort()` proves a lost answer is never resent.
  - **Verify** `cd frontend && npm test -- tests/decode.test.ts tests/snapshotGate.test.ts && npm run build && npm run e2e -- e2e/create.spec.ts`
  - **Mutation** Remove the submit `disabled` while pending; (4) must fail. Remove the wait-hint disablement; (10) must fail. Drop the resend after `request-security-rejected`; (9) must fail. Make `accept` replace on equal version; (2) must fail. Rename the `/api/v1/games/{gameId}` path in a scratch edit of `contracts/openapi.yaml` and run `npm run typecheck`; it must fail (revert the scratch edit).
  - **Corrective Evidence (2026-10-28)** The re-opened Home return regression failed before the fix. Restored the remembered-game Home action and its English/Ukrainian labels. `npm run build && npm run e2e -- e2e/create.spec.ts` passed (14 real-backend journeys).
  - **Approved UI correction** Flatten Home/Create and render fleet badges aggregated by length. `create.spec.ts` checks both modes, Classic’s combined three-cell count, full View details and absent expanded facts; mutate aggregation to per-type badges and record the failing assertion.
  - **Depends on** T001, T006

- [x] T008 [US1] Build the waiting room with copy, QR and replace invitation, and amend `GameSnapshot.version` in `frontend/src/screens/WaitingRoom.tsx`
  - **Covers** R11, R12, R14, R16 (presence timer), R24 (stream), R38 (toasts), R59 (waiting room), US1 scenarios 3–5 · research D7, D16 · plan fix 4
  - **Read first** plan § *Live stream*, § *Outcome → screen* (invitation rows), fix 4 · `openapi.yaml` `version` (L1029-1035), `invitationUrl` (L1072-1077), `replaceInvitation` (L464), `sendPresence` (L503), `streamGameEvents` (L412, events `snapshot`/`closed`, `: keep-alive`) · `GameSlot.java:145-163` · master `frontend/src/widgets/sharing/QrCodeSheet.tsx` (`git show master:…`; strip `react-i18next`, design `Sheet`/`Button`, CSS import)
  - **Files** `contracts/openapi.yaml` (version description), `contracts/README.md` (one note), `src/gateway/{gameGateway.ts,eventStream.ts}`, `src/session/presence.ts`, `src/screens/{GameRoute.tsx,WaitingRoom.tsx}` (+ WaitingRoom `.css`), `src/components/{Qr.tsx,ConnectionStatus.tsx,ConfirmDialog.tsx,Pill.tsx,Toasts.tsx,Stepper.tsx,Meter.tsx}` with sibling `.css` (Pill, Toasts, Stepper and Meter are introduced here at their first screen caller), `tests/{qr.test.tsx,toasts.test.tsx,eventStream.test.ts}`, `e2e/waiting.spec.ts` (`qrcode` and its types are already pinned by T004)
  - **How** Contract: `version` "…excluding `serverTime`, `expiresAt`, `invitationUrl` and `invitationExpiresAt`, which are always current". Extend `gameGateway.ts` with `replaceInvitation(gameId: GameId): Promise<GameSnapshot>` and `sendPresence(gameId: GameId): Promise<GameSnapshot>` using T007's shared typed CSRF client. `eventStream.ts`: one `EventSource('/api/v1/games/<id>/events')` per open game; close previous before opening; `snapshot` → decode → `getGameStore(gameId).acceptSnapshot(snapshot)`; `error`/`CONNECTING` sets connection to reconnecting; `closed`/`REPLACED` is terminal, shows "Open in another tab", and never reopens; `closed`/`GAME_UNAVAILABLE` follows plan § *Live stream* (full recovery in T015); an unknown closed reason leaves a generic offline state without the replaced label. Presence: every `meta.limits.presenceIntervalSeconds` while visible and `SEND_PRESENCE` allowed; timer restarts on visibility return. Waiting room (mockup L2126; widgets per contracts/ui-surface.md): render the selected server `Ruleset`'s five facts (board size, fleet, ship contact, extra turn on hit, revealed water) with T007's shared `RulesetFacts`; show the exact status notice "Waiting for an opponent. This page updates automatically." with `role="status"`; stepper Created/Waiting/Placement/Battle from `phase`; connection pill; invitation link field showing `invitationUrl` with everything after `#invite=` replaced by `••••••••` (the full link never enters the DOM); invitation lifetime countdown and meter (remaining ÷ `meta.limits.invitationLifetimeSeconds`, offset-corrected, display only); `GameRoute` consumes then clears the one-time `{toast: 'game-created'}` history state from T007, and WaitingRoom shows "Game created" on arrival from Create (Toasts uses an `aria-live="polite"` region, normal `role="status"`, error `role="alert"`, 6.5 s auto-dismiss and a close control); Copy link (`navigator.clipboard.writeText(snapshot.invitationUrl)`, success/failure toast), Show QR (`qrcode` lazy `toCanvas`, `aria-label`, the mockup's privacy warning), no token text or Copy token (R60); no `invitationUrl` → "Invitation expired, sharing unavailable"; Replace invitation whenever `NEW_INVITATION` allowed, after a confirmation that the old link stops working; one `getGame` at `invitationExpiresAt`. Guest join snapshot (newer version, phase `PLACEMENT`) moves the host on.
  - **Proof** Units: (1) `Qr` renders a canvas for a given string with its `aria-label`; (2) Toasts use the polite live region, `status`/`alert` roles, close immediately on ×, and auto-dismiss after 6.5 s; (3) an `EventSource` error in `CONNECTING` sets the game connection to `reconnecting`; (4) an unknown `closed` reason leaves the connection offline without claiming "Open in another tab". `waiting.spec.ts` (default stack): (5) the "Game created" toast appears with `role="status"` on arrival from Create; dismiss it, reload, and confirm it does not reappear; (6) clipboard (granted permission) equals the snapshot's `invitationUrl`, and `document.documentElement.outerHTML` (including input values) does not contain the invitation secret; (7) QR dialog opens labelled and closes on Escape; (8) Replace → confirm → clipboard holds a different link; the old link's join is refused; (9) after a guest joins via `page.request`, the host reaches placement without reload; (10) for each server-advertised ruleset created in a fresh browser context, the waiting room's five `RulesetFacts` labels and values equal that ruleset's fields from `listRulesets`; (11) the exact waiting notice is visible with `role="status"`; (12) a real-backend `REPLACED` close event displays "Open in another tab", and a subsequent request log has no second events connection. Presence stack (`BATTLESHIP_PRESENCEINTERVALSECONDS=1`): (13) a visible waiting-room host with `SEND_PRESENCE` allowed sends presence after the interval; hiding the page prevents another request and restoring visibility restarts the timer without sending immediately. Short-lifetime stack (`BATTLESHIP_INVITATIONLIFETIMESECONDS=10`): (14) "invitation expired" appears without reload; Replace restores sharing; (15) the stepper marks Waiting with `aria-current="step"` and moves to Placement after the guest joins; the lifetime meter's value falls between two reads. `contracts npm run check` passes.
  - **Verify** `cd contracts && npm run check && cd ../frontend && npm test -- tests/qr.test.tsx tests/toasts.test.tsx tests/eventStream.test.ts && npm run build && npm run e2e -- e2e/waiting.spec.ts`
  - **Mutation** Remove the scheduled `getGame` at `invitationExpiresAt`; (14) must fail. Disable presence scheduling or let the timer fire while hidden; (13) must fail. Put the raw `invitationUrl` in the link field's `value`; (6) must fail. Omit the waiting room's shared `RulesetFacts` or skip selecting each advertised ruleset; (10) must fail. Remove the waiting status notice; (11) must fail. Omit the `CONNECTING` state update; (3) must fail. Treat `closed`/`REPLACED` as Offline or reopen its stream; (12) must fail. Treat an unknown closed reason as REPLACED; (4) must fail.
  - **Depends on** T007

---

## Phase 4: US2 — Guest joins (P1)

- [x] T009 [US2] Strip the invitation fragment before render and join explicitly in `frontend/src/main.tsx`
  - **Covers** R07 (join path), R08, R09, R10, R15, R27 (join 410), R51, US2 · S2
  - **Read first** plan § *Boot and routing* · data-model § *Invitation secret* · `openapi.yaml` `joinGame` (L264, body `{invitationSecret (43 chars), displayName}`, 409 `invitation-unavailable`, 410)
  - **Files** `src/main.tsx`, `src/session/inviteSecret.ts`, `src/screens/JoinInvitation.tsx`, `e2e/invitation.spec.ts`
  - **How** Before `createRoot().render`: on `/join/:gameId` read `#invite=` into a module variable and `history.replaceState(null,'','/join/<id>')`. Join screen (mockup Join screen L2098 without its token field — R60): `getMeta`, captain name field, Join game button only; on activation send `joinGame` once (CSRF retry rule), clear the secret on settle. Success → `/games/<id>` + remember id. Outcomes (plan § *Outcome → screen*): `409 invitation-unavailable` and a lost answer → one "invitation unavailable" screen with Home (R15); `409 browser-game-limit` → the R07 message with Return to your game when an id is remembered, never retried; `410 game-expired` → Session unavailable "game expired" (R27).
  - **Proof** `invitation.spec.ts`: (1) an `addInitScript` MutationObserver on `#root` records `location.hash` at the first rendered node — it is empty; after navigating to `/` and `page.goBack()`, the URL still lacks the secret; (2) no `POST …/join` is sent before clicking Join; (3) two guests open the same link and both click Join: exactly one reaches placement, the other sees "invitation unavailable"; (4) host reaches placement live; (5) the secret string is absent from `localStorage`, `sessionStorage`, captured console messages and `e2e/.logs/default.log`; (6) a guest context that already hosts its own live game opens a fresh link and clicks Join: it shows the `browser-game-limit` message with Return to your game, and sends exactly one `POST …/join`; (7) short-lifetime stack 4174/8081 (`IDLETIMEOUTSECONDS=8`, `SWEEPINTERVALSECONDS=1`, `RESULTRETENTIONSECONDS=60`): a guest joins, both pages are hidden (visibility override, Conventions) until the game expires, then the guest reopens the same link and clicks Join — the answer is `410 game-expired` and the page shows Session unavailable "game expired", not "invitation unavailable".
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts`
  - **Mutation** Move `replaceState` into the Join component's effect; (1) must fail. Auto-join on load; (2) must fail. Map every join `409` to "invitation unavailable"; (6) must fail. Map join `410` to "invitation unavailable"; (7) must fail.
  - **Approved UI correction** Flatten Join invitation while retaining its established navigation and responsive behavior; run invitation and accessibility journeys in both themes and phone/desktop layouts.
  - **Depends on** T008

---

## Phase 5: US3 — Fleet placement (P1)

- [x] T010 [US3] Build the placement board and anchored ship menus in `frontend/src/screens/Placement.tsx`
  - **Covers** R01 (display counts), R02, R17, R19, R20, R31, R40 (cells), R59, R62, US3 scenarios 1–3
  - **Read first** plan § *Placement, play, results* · README phase table (`PLACEMENT` actions) · `openapi.yaml` `GameSnapshot.placementBlockedCells`, `PlaceShipCommand {type, shipId, anchor, orientation}`, `RemoveShipCommand`, `SimpleCommand` (`PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY`), `Board {grid: CellState[][], ships}`, `CellState` (L804-814), `Player.ready`
  - **Files** `contracts/openapi.yaml`; `backend/domain/.../GameRules.java`; `backend/application/.../{SnapshotView,SnapshotProjector}.java`; `backend/app/.../SnapshotDtoAssembler.java`; backend `PlacementRulesTest`, `GameJourneyIT`, `WireConformanceIT`; `frontend/src/components/{Board,FleetTray}.tsx` (+ `.css`), `frontend/src/screens/{Placement.tsx,ShipActionDialog.tsx,usePlacementDrag.ts}`, `frontend/src/i18n/{en,uk}.ts`, `frontend/e2e/{placement,accessibility}.spec.ts`
  - **How** Implement compact cell-adjacent menus and advisory validity as specified in plan § Placement. Check bounds, full candidate footprint, occupancy and contact against all other ships identified by `shipId`. When moving, visually lift the ship and calculate restrictions from other ships, retaining any overlapping halo; cancel or failure restores the server view. Send only atomic `PLACE_SHIP` for move/rotation. Preserve notices, random/clear/ready and snapshot-derived progress. Popup positioning, focus, Escape and outside dismissal work at viewport edges.
  - **Proof** Real-backend placement journeys cover both rulesets, edge anchors, overlapping and no-contact cases, exhausted sizes, exact moving ship identity when Classic ships touch, legal and illegal rotations, move cancel/failure, removal/clear and readiness. Check popup focus, dismissal and viewport edges; board and counts follow accepted snapshots. Supplement with focused advisory-check tests if useful.
  - **Verify** `cd contracts && npm run check`; `cd backend && ./mvnw -q verify && ./mvnw spotless:check`; `cd frontend && npm run build && npm run e2e -- e2e/placement.spec.ts`; final `./scripts/verify.sh`
  - **Mutation** Remove exclusion of the moving ship by identity; the assertion that the popup offers a legal direction into its vacated footprint must fail. Permit an invalid direction; the corresponding popup assertion must fail. Record failure outputs and restore each mutation.
  - **Historical evidence (superseded UI correction)** The empty-projection mutation failed `PlacementRulesTest.noTouchProjectionMarksOnlyEmptyInBoundsNeighbours` (11 tests, 1 failure). Disabling the drag-state update failed the active touch-drag assertion (`expected 0`, received 6); both mutations were restored. Contract check, backend verify + Spotless, frontend check, all 6 accessibility matrices, and all 9 placement journeys passed. `./scripts/verify.sh` passed all 5 stages, including 89 packaged-backend browser tests.
  - **Depends on** T009

- [x] T011 [US3] Add mouse drag and touch ship selection in `frontend/src/screens/Placement.tsx`
  - **Covers** R18, R61, US3 scenario 4 · research D19 · decision B4
  - **Read first** plan § *Placement, play, results* (drag) · research D19 · mockup drag target style `.is-drag-target` (L2869-2887)
  - **Files** `src/screens/usePlacementDrag.ts`, `src/screens/Placement.tsx`, `src/components/FleetTray.tsx`, `e2e/placement.spec.ts` (two steps)
  - **How** Render a compact two-row ship grid with counts and independent fleet progress. Touch selects a ship then destination without converting touch scrolling into drag. Preserve mouse drag; a multi-cell drop opens the compact chooser with only legal directions, one-cell places directly, and no-fit keeps the ship available and offers another cell. Keyboard reaches the same actions.
  - **Proof** Real-backend placement journey covers touch scrolling and tap selection, mouse dragging, keyboard operation, single-cell placement, legal direction filtering, no-fit recovery, and progress/count updates after placement, move, remove and clear.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/placement.spec.ts`
  - **Mutation** Start a drag on touch scroll; touch-scroll assertion must fail. Omit direction filtering on drop; no-invalid-direction assertion must fail. Record failure outputs and restore each mutation.
  - **Depends on** T010

---

## Phase 6: US4 — Play and results (P1)

- [x] T012 [US4] Build gameplay with live updates, announcements and lost-answer retry in `frontend/src/screens/Gameplay.tsx`
  - **Covers** R01 (display counts), R19, R20, R21, R24, R31, R59, US4 scenarios 1–3, US5 scenario 6 · S3 (lost answer) · research D13
  - **Read first** plan § *Session store*, § *Placement, play, results* · data-model § *Action slot* · `openapi.yaml` `FireCommand {type, target}`, `turn` (Side), `lastShot {by, target, result MISS|HIT|SUNK, sunkShipId?}`, `CommandRequest.commandId` ("server remembers at least the last 16")
  - **Files** `src/screens/Gameplay.tsx` (+ `.css`), `src/components/{Announcer.tsx,TurnPill.tsx,PlayerCard.tsx}` (+ `.css`), `src/session/gameStore.ts` (checking state + retry), `e2e/play.spec.ts`
  - **How** Mockup gameplay screen (L2213): turn pill from `turn`; two player cards (name, ships left = `shipsRemaining` ÷ Σ `ruleset.fleet[].count`, ship cells left = Σ `length × count` − `HIT`/`SUNK` cells on that board, each with the mockup's bar); both boards (Enemy waters / Your fleet tab control rendered for T018's narrow layout) with hit ×, miss •, sunk and revealed-water styles; footer with connection state and "Session expires in" (`expiresAt`, offset-corrected); no snapshot-version text (R60). Fire only on `UNKNOWN` opponent cells while `FIRE` allowed; target cell pending. Announcer (`aria-live="polite"`) once per new `lastShot` (`by`+`target`). Action without an answer → `checking` + Retry that resends the same body and `commandId`; an SSE snapshot does not settle the slot.
  - **Proof** `play.spec.ts`: (1) Sea Battle with the opponent's fleet placed at fixed anchors (Conventions): a hit keeps the turn; sinking a `ship-1` renders its surroundings as `REVEALED_WATER`, distinct from `MISS`; (2) Classic: a hit passes the turn; (3) the opponent's shot updates the player's board and announcement without reload; (4) action race (Classic, so any shot passes the turn): install `page.route('**/events', r => r.abort())` before the player's page opens the game, so its view comes from `getGame` only; when that view shows the player's turn, send one `FIRE` at an `UNKNOWN` cell through `page.request` (same session, own `commandId`, `X-XSRF-TOKEN` from the cookie) and, as soon as it answers, click a different `UNKNOWN` cell in the stale view — the page's `FIRE` gets `409 action-not-allowed`, the worded reason is shown, the board refreshes to `getGame`, and `getGame` shows exactly one new shot; (5) lost answer: `page.route('**/commands', r => r.fetch().then(() => r.abort()))` once, page shows checking; Retry's request body has the same `commandId`; the board shows the shot applied once (`getGame` via `page.request` shows one `MISS`/`HIT` at that cell); (6) after a hit, both player cards' ships-left and ship-cells-left values equal those derived from `getGame` via `page.request`.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/play.spec.ts`
  - **Mutation** Generate a new `commandId` on Retry; (5) must fail (second shot refused as `target-already-fired` or a different cell state).
  - **Mobile Pulse correction** Highlight the newly resolved shot on its affected board; in the single-board layout, keep the result visible for 1,000 ms before selecting the server's next-turn board when the turn changes. Preserve retained turns, manual tab selection, newer-shot supersession, server authority and existing announcements. Initial/reconnected snapshots do not replay old shots; gameplay teardown cancels pending work. Extend real-backend `play.spec.ts` to prove both directions, timing, same-turn hits, consecutive shots, manual selection, reconnect and cancellation when gameplay ends; prove both boards remain visible in desktop and short landscape. A named mutation removing the delay must fail the pre-delay board assertion, then be restored and rerun.
  - **Evidence** `npm run check` passed (29 unit tests); `npm run build && npm run e2e -- e2e/play.spec.ts e2e/placement.spec.ts` passed (13 journeys). Retry-UUID mutation failed at the same-body assertion (see `/private/tmp/t012-mutation-final.log`).
  - **Depends on** T011

- [x] T013 [US4] Build the results screen and play one full game per ruleset in `frontend/src/screens/Results.tsx`
  - **Covers** R30, R36, R59, US4 scenarios 4–5 · S1
  - **Read first** contracts/ui-surface.md § *Surfaces and widgets* (Results rows and the stat-card field table) · mockup results screen (L2261) · `openapi.yaml` `outcome {winner, reason}`, `statistics` (`match`, `you`, `opponent`; `PlayerStatistics` with optional `accuracy`; `DurationAggregate`; `FleetSummary`) L909-1009
  - **Files** `src/screens/Results.tsx` (+ `.css`), `src/components/StatCard.tsx` (+ `.css`), `src/i18n/{en,uk}.ts`, `e2e/helpers/playGame.ts`, `e2e/results.spec.ts`
  - **How** Victory/Defeat hero with reason; fleet-condition tiles from `statistics.you.fleet` (intact/damaged/sunk); stat cards Match time, Turns & accuracy, Turn timing and Shot decision timing with the rows and fields of the ui-surface table (durations formatted, an absent optional value shows "—"); both revealed boards (`Board`, read-only); result-availability note from `expiresAt`; Create another game, Leave results. Leave results → `leaveGame` → Home, forget id. `playGame.ts`: drive both players through the UI (random fleet, ready, fire by reading each player's own UI turn) until FINISHED. Keep translations in parity and arrange four cards responsively.
  - **Proof** `results.spec.ts` per ruleset: (1) both players reach results; winner/loser shown; (2) each stat-card row and fleet tile shown equals its `getGame` `statistics` field read via `page.request`; (3) Leave results returns Home and `getGame` then answers 404.
  - **Verify** `cd frontend && npm run check && npm run e2e -- e2e/results.spec.ts e2e/accessibility.spec.ts`
  - **Mutation** Compute accuracy locally as hits/shots rounded differently; (2) must fail.
  - **Shot-decision correction** Compare each new row with the real server's corresponding `shotDecisions` value for both players and a zero-shot result. In a real retained-hit sequence, use a controlled delay and assert a formatted shot-decision duration differs from the corresponding formatted turn duration before checking the UI. Replacing `shotDecisions` with `turns` must fail that focused proof, then be restored. Verify the four cards at the existing three responsive viewports.
  - **Correction evidence (2026-11-06)** The real-backend results and accessibility run passed 21 journeys, including both rulesets, zero-shot resignation, the delayed retained-turn comparison and three viewports. The focused mutation substituting turn aggregates failed with expected `Your average0:01`, received `Your average0:02`; after restoring the source, the focused journey passed. `npm run check` passed before the subsequent Sonar semantic cleanup; rerun the final-tree checks before closing.
  - **Evidence** `npm run check` passed (29 unit tests); `npm run build && npm run e2e -- e2e/results.spec.ts e2e/play.spec.ts` passed (9 journeys). Accuracy recomputation mutation failed: expected server-formatted `18.18%`, received `18.2%` (see `/private/tmp/t013-mutation.log`).
  - **Depends on** T012

---

## Phase 7: US5 — Leaving, expiry, restart, connection loss (P2)

- [x] T014 [US5] Implement leave, resign and the abandoned view in `frontend/src/screens/LeaveControl.tsx`
  - **Covers** R28, R29, R30, US5 scenarios 1–3
  - **Read first** plan § *Leaving, layouts, dialogs* · `openapi.yaml` `leaveGame` (L542, 204; 404 after own leave = success) · README phase table
  - **Files** `src/screens/LeaveControl.tsx`, `src/screens/Abandoned.tsx`, `src/screens/SettingsDialog.tsx` (Resign match), `e2e/leaving.spec.ts`
  - **How** WAITING/PLACEMENT: Leave game → confirm → `leaveGame` → Home after 204 or 404; cancel keeps the view. PLAYING: Settings shows Resign match while `RESIGN` is allowed (the mockup's in-play leave path); its dialog says leaving means resigning → `RESIGN` command. ABANDONED: "Game abandoned", no boards, no statistics, Leave results.
  - **Proof** `leaving.spec.ts`: (1) cancel keeps placement; confirm → Home; (2) remaining player sees abandoned without reload, no boards; (3) Settings → Resign match in play → opponent's results show a win by `RESIGNATION`; Resign match is absent from Settings outside play; (4) lost leave answer: `page.route('**/leave', r => r.fetch().then(() => r.abort()))` once; the page's retry gets 404 and lands Home, not Session unavailable.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/leaving.spec.ts`
  - **Mutation** Treat 404 after own leave as Session unavailable; (4) must fail.
  - **Evidence** `npm run format:check && npm run lint && npm test && npm run build && npm run e2e -- e2e/leaving.spec.ts` passed (12 unit files / 29 tests, 4 real-backend journeys). The resignation lost-answer proof failed before the shared action-slot integration, then passed with the same command ID on retry. Treating leave retry 404 as unavailable failed the Home assertion (see `/private/tmp/t014-mutation.log`).
  - **Depends on** T013

- [x] T015 [US5] Recover the live stream and honour rate limits in `frontend/src/gateway/eventStream.ts`
  - **Covers** R22, R24, R25, R26, R43 (actions disabled offline), US5 scenario 5 · S3 · fact F5 (stream also ends without `closed` at `stream-max-lifetime-seconds` and on shutdown)
  - **Read first** plan § *Live stream* · data-model § *Connection* · `openapi.yaml` `StreamClosed` (L1324, `REPLACED`, `GAME_UNAVAILABLE`), getGame polling fallback (L219-220), 429 `retryAfterSeconds`
  - **Files** `src/app/App.tsx`, `src/gateway/eventStream.ts`, `src/screens/GameRoute.tsx`, `src/session/gameStore.ts`, `src/session/useRetryWindow.ts`, `tests/eventStream.test.ts`, `tests/gameStore.test.ts`, `e2e/recovery.spec.ts`
  - **How** `error` with `readyState===CLOSED` or tab visible → `getGame`, reopen on success; failures without a problem retry after 1, 2, 4, then every 8 s; a stream that fails again after a successful `getGame` reopens on the same backoff, reset by its first received event; 429/503 wait `retryAfterSeconds`; 401/404/410 → outcome table. `REPLACED` → "open in another tab", no reopen. `GAME_UNAVAILABLE` → one `getGame`, no reopen. `offline` only from the `offline` event / `navigator.onLine === false` (close stream, disable game actions); `online` → as the CLOSED path; a network error while online stays `reconnecting` (data-model § *Connection*).
  - **Proof** `recovery.spec.ts`: (1) in gameplay on the player's turn, `context.setOffline(true)` → status shows offline and every opponent cell's fire control and the header Refresh are disabled; then `false` → reconnecting then connected, fire controls enabled again; board equals `getGame`; no command was resent (request log); (2) opening the game in a second tab of the same context → first tab shows "open in another tab" and opens no new stream; (3) tab return: override `visibilityState` to hidden then visible — one `getGame` is sent and the stream reopens; (4) lowered-limit stack 4175/8082 (`RATELIMIT_READGAMEPERMINUTE=2`): the 429 shows a wait hint and no `getGame` is sent before `retryAfterSeconds` (test timeout ≥ 90 s; `Retry-After` can reach 60 s); (5) manual Refresh on the same stack honors the 429 deadline and stays disabled until it expires; (6) `GAME_UNAVAILABLE` on that stack does one terminal lookup only after the deadline and never reopens `/events`; (7) same stack with `RATELIMIT_STREAMOPENPERMINUTE=2` (reads unlimited): after the stream limit is hit, at most 4 `/events` requests are sent in the next 10 s (reopen backoff 1, 2, 4, 8 s). `eventStream.test.ts` additionally proves an offline terminal lookup stays pending through its deadline and runs once after online, without reopening.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/recovery.spec.ts`
  - **Mutation** Reopen the stream after `REPLACED`; (2) must fail. Retry 429 immediately; (4) must fail. Remove the reopen backoff; (5) must fail. Keep fire controls enabled while `offline`; (1) must fail.
  - **Evidence** `npm run format:check && npm run lint && npm test` passed (12 files / 31 tests); `npm run build && npm run e2e -- e2e/recovery.spec.ts` passed (7 real-backend journeys). The lowered-read-limit `GAME_UNAVAILABLE` journey verifies that an SSE snapshot does not clear a 429 wait notice, no read occurs before `Retry-After`, then one lookup occurs without reopening the stream. A unit proof verifies a terminal lookup stays pending if its deadline expires offline and runs once after reconnect, without reopening. Mutations failed as required: offline controls (`/private/tmp/t015-mutation-offline.log`), `REPLACED` reopen (`/private/tmp/t015-mutation-replaced.log`), removed stream backoff (`/private/tmp/t015-mutation-backoff.log`), immediate 429 retry (`/private/tmp/t015-mutation-retryafter.log`), and removed absolute retry deadline guard (`/private/tmp/t015-mutation-retryafter-guard.log`). All mutations were restored.
  - **Depends on** T014

- [x] T016 [US5] Prove expiry, presence extension and restart loss on dedicated stacks in `frontend/e2e/lifetime.spec.ts`
  - **Covers** R16, R26 (`GAME_UNAVAILABLE`), R27, R50, US5 scenario 4 · S3
  - **Read first** plan § *Outcome → screen* · research D12 · `openapi.yaml` 410 `game-expired` → 404 after forget (L665-676)
  - **Files** `src/screens/Home.tsx`, `src/screens/WaitingRoom.tsx`, `src/i18n/{en.ts,uk.ts}`, `e2e/lifetime.spec.ts`
  - **How** Short-lifetime stack 4174/8081: `IDLETIMEOUTSECONDS=8`, `SWEEPINTERVALSECONDS=1`, `RESULTRETENTIONSECONDS=4`, `PRESENCEINTERVALSECONDS=2`. Restart stack 4176/8083 via `restartBackend()`. Session unavailable clears game presentation state and the matching remembered id; while a remembered game is live, Home retains the R07 Return action.
  - **Proof** (1) a waiting game whose page is hidden (visibility override, Conventions) → on return "game expired" (410); after retention, reload → "no longer available" (404); Home then has no Return action; (2) with the page visible, presence keeps a waiting game past the idle timeout and the shown expiry moves forward; (3) backend restart mid-placement → "no longer available … server restarted" with New game; (4) Home offers Return while the game remains live, and removes it after the matching `GAME_UNAVAILABLE` lookup returns 404/410 and clears the remembered id; (5) that stream closure causes exactly one `getGame` and no new `/events` request (request log).
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/lifetime.spec.ts`
  - **Mutation** Disable the presence timer; (2) must fail.
  - **Evidence** `npm run format:check`, `npm run lint`, `npm run typecheck`, and `npm test` passed (12 files / 31 tests); `npm run build && npm run e2e -- e2e/lifetime.spec.ts` passed (4 real-backend journeys). The visible-presence proof waits past the 8-second idle timeout, confirms a real GET still returns a WAITING game, and observes another expiry advance. With presence delayed by 60 seconds, that proof failed at the expiry-advance assertion (`/private/tmp/t016-mutation-presence-strengthened.log`); the mutation was restored.
  - **Corrective Evidence (2026-10-28)** Red run reproduced the old live-Home assertion: Return was present while its remembered game was still live. The journey now confirms Return during the live state, then confirms the single terminal lookup clears the remembered id and Home no longer offers Return. `npm run build && npm run e2e -- e2e/lifetime.spec.ts` passed (4 real-backend journeys).
  - **Depends on** T015

---

## Phase 8: US6 — Settings, feedback and inclusive use (P2)

- [x] T017 [US6] Add sound and haptics feedback in `frontend/src/feedback/`
  - **Covers** R32, R33, US6 scenarios 1–2 · S5 · research D8
  - **Read first** master `frontend/src/audio/audioRecipes.ts`, `AudioFeedback.ts` (`git show master:…`; no imports to strip)
  - **Files** `src/feedback/{audioRecipes.ts,audioFeedback.ts,haptics.ts,useShotFeedback.ts}`, `src/screens/Gameplay.tsx` (hook), `tests/feedback.test.ts`, `e2e/settings.spec.ts` (mid-game step)
  - **How** Port both audio files; map `lastShot.result` → recipe; haptics `navigator.vibrate?.(pattern)` in try/catch; both only when the preference is on, once per new `lastShot`, never awaited.
  - **Proof** Unit: (1) `AudioContext` constructor throwing, `resume()` rejecting and `vibrate` absent/throwing all resolve without error. `settings.spec.ts`: (2) toggling sound and haptics on/off mid-game leaves the next shot's result and turn identical to `getGame`.
  - **Verify** `cd frontend && npm test -- tests/feedback.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts`
  - **Mutation** Remove the try/catch around `vibrate`; (1) must fail.
  - **Evidence** The full-gate run exposed the settings journey reusing `J10` after a hit retained the shooter’s turn. The journey now fires distinct, distant `J10` and `A1` cells, so either a hit (same shooter) or miss (other shooter) leaves the next target actionable. Before each shot, it asserts sound and haptics in the opposite state for the active player, toggles both, and asserts the requested state. `npm test -- tests/feedback.test.ts` passed (1 file / 4 tests), `npm run build` passed, `npm run e2e -- e2e/settings.spec.ts` passed (2 real-backend journeys), `npm run lint` and `npm run format:check` passed; server shot targets, results, and turns remain asserted.
  - **Depends on** T016

- [x] T018 [US6] Correct visual parity and make the three layouts, keyboard, touch and screen-reader use pass in `frontend/`
  - **Covers** R31, R39, R40, R41, R58, R59, R60, R61, US6 scenarios 3–6 · S4, S8 · research D18 · decisions B4, B5
  - **Read first** plan § *Leaving, layouts, dialogs*, § *Visual design* · research D18 · spec R39–R41, R58–R61 · mockup container query (L1728-1886), phone-landscape rules (L1888-1948, translated from `data-device` to the media query), `@media (max-height:31.25rem)` (L1950)
  - **Files** `contracts/ui-surface.md`, `src/app/Header.tsx`, `src/components/Board.tsx/.css`, `src/screens/{Gameplay,Results,RulesDialog,WaitingRoom,Placement,GameRoute,LeaveControl}.tsx`, `src/components/{Dialog.css,RulesetFacts.tsx,RulesetFacts.css}`, `src/styles/{base.css,layout.css}`, `src/i18n/{en.ts,uk.ts}`, `tests/board.test.tsx`, `e2e/{accessibility,create,leaving,play,waiting}.spec.ts`. No new dependency.
  - **How** Preserve the R39 container-query layouts and existing keyboard/touch interactions. Remove repeated `?`, `≈`, ship-square, water-dot and other static board glyphs; use cell patterns and hit/sunk marks for non-colour distinction while preserving coordinate/state accessible names and live announcements, including when forced colours suppress background images. Wire the emoji preference only to transient fire and miss/empty-cell feedback, with no static emojis in Results or elsewhere; render branding and utility icons as text-presenting glyphs. Suppress animation under reduced motion. Restore the mockup's padded rules-dialog head/body/actions and distinct padded ruleset cards with wrapping fleet entries; blur the full native modal backdrop; theme text inputs; align Leave controls with their owning screen without overlap. Correct responsive defects found in the named surface set.
  - **Proof** Keep the existing `accessibility.spec.ts` keyboard/touch, overflow, axe, tab, forced-colour/reduced-motion, dialog-focus and screenshot coverage at 1280×800, 390×844 and 844×390 in both themes. Update its board-change assertion to inspect accessible state labels/data-state instead of visible cell text. Add assertions that (1) ordinary water/ship/unknown cells have no visible glyph, state labels remain accessible, and miss/hit/sunk have their specified pattern/mark; after sinking a real ship, revealed water remains visually distinct from ordinary water under forced colours; (2) emoji off produces no emoji, emoji on produces only transient fire/miss animation, Results and other static surfaces never show emoji, header icons use text presentation, and reduced motion suppresses animation; (3) Rules preference help text is accurate in English and Ukrainian; (4) rules dialog has padded head/body/actions, wrapping fleet content and viewport-wide backdrop blur, with keyboard scrolling at all three viewports; (5) create/join inputs are themed and Leave does not overlap its heading. All existing R38 surfaces remain horizontally overflow-free and essential controls reachable at all 3 viewport presets.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/accessibility.spec.ts`
  - **Mutation** Give the board a fixed `min-width: 900px`; (2) must fail at 390×844. Delete the `@container app` rule; (4) must fail at 390×844. Show the header Refresh control only on `:hover`; (7) must fail.
  - **Corrective mutation** Restore a static ship glyph; the board assertion in (1) must fail. Remove the emoji preference gate; (2) must fail with emoji off. Remove dialog body padding; (3) must fail.
  - **Evidence** Original verification passed (see commit `1a96e8d`). For the owner's visual corrections, `./scripts/verify.sh` passed all five stages: runtime/install, contract, backend `verify` + Spotless, frontend format/lint/typecheck/Vitest/build, and 87 real-packaged-backend Playwright journeys. Frontend Vitest: 15 files / 43 tests. The accessibility journey passed 15/15 and generated 114 screenshots (19 named surfaces × portrait 390×844, landscape 844×390, laptop 1280×800 × dark/light). `git diff --check` passed. Red/green proof: restoring a static ship glyph, removing the emoji preference gate, and removing dialog padding each failed the targeted assertions; restored behavior passes. The reviewer found no Blocking or Important findings after the persistent Leave control/portal correction. Owner mockup comparison across the screenshot matrix: the three viewport compositions and dark/light theme hierarchy remain consistent; rules dialog now has the mockup's padded header, scrollable padded body, separated ruleset cards and action footer, and the native modal backdrop blurs the full page; gameplay uses clean patterned cells with no repeated dot/square glyphs and preserves X/✕ hit/sunk marks; utility icon/text presentation and surrounding spacing are consistent; no static emoji remain, with optional transient fire/miss animation only. Portrait rules content scrolls within the viewport, while landscape and laptop retain both cards where space allows. Accepted deviation: revealed-water pattern and forced-colors inset border are necessary accessible state cues absent from empty/unknown water; actual server ruleset text and fleet names remain server-derived. End-to-end verification also passed the R28 lost-leave-response → terminal 404 → retry journey, so the Leave control stays available across resync.
  - **Approved UI correction** Flatten comparable screen wrappers including Waiting while keeping controls, boards, notices and dialogs distinct. Compare Home/Create/Join/Waiting and placement popups to the approved mockup at phone and desktop sizes in light and dark themes; run accessibility, waiting and smoke journeys. Record each visual comparison and deviations.
  - **Mobile Pulse correction** In `accessibility.spec.ts`, check a keyboard-operable tab remains usable during the transition, focus is not stranded in a hidden board, and reduced-motion emulation gives a static last-shot indication without animation. Compare the real mobile view with the approved Pulse preview in both themes. Mutation: removing the reduced-motion override must fail the animation assertion, then restore and rerun.
  - **Depends on** T017

---

## Phase 9: US7 — Installable shell offline (P3)

- [x] T019 [US7] Make the app installable with an offline shell and update prompt in `frontend/src/pwa/`
  - **Covers** R42, R43, R44, US7 · S6 · research D9
  - **Read first** solitaire `src/pwa/{registerPwa.ts,pwaGateway.ts,deferredGateway.ts,installGateway.ts}` and its `VitePWA` block (`registerType:'prompt'`, `injectRegister:false`, `manifest:false`, `navigateFallback:'index.html'`, `cleanupOutdatedCaches:true`)
  - **Files** `src/pwa/*`, `src/main.tsx`, `src/app/App.tsx`, `src/components/{OfflineBanner.tsx,UpdatePrompt.tsx}`, `src/screens/{CreateGame.tsx,GameRoute.tsx,JoinInvitation.tsx,SettingsDialog.tsx}`, `src/i18n/{en.ts,uk.ts}`, `src/styles/layout.css`, `src/vite-env.d.ts`, `vite.config.ts` (VitePWA + `navigateFallbackDenylist: [/^\/api\//]`, no `runtimeCaching`), `index.html`, `public/{manifest.webmanifest,favicon.svg,icons/*}` (Battleship-branded, `start_url: '/'`), `tests/pwa.test.ts`, `e2e/offline.spec.ts`
  - **How** Copy the four gateway files; register on load; offline banner from `online`/`offline`; game controls disabled while offline, nothing queued; update prompt calls `updateSW()` only on consent.
  - **Proof** `offline.spec.ts` (preview stack): (1) after one load and SW `ready`, `context.setOffline(true)` + reload renders the shell with "network required" within 3 s; (2) no request to `/api` is attempted while offline (request log); (3) after creating a game and reading it (`createGame`, `getGame`, event stream) online, `caches` entries contain no `/api` URL; (4) after rebuilding into the served `dist/` with `VITE_BUILD_ID=next npm run build` (the id is rendered in Settings, so the precache manifest changes), the page shows the update prompt and does not reload until accepted.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/offline.spec.ts`
  - **Mutation** Remove `navigateFallbackDenylist` and add an `/api` `runtimeCaching` rule; (3) must fail.
  - **Evidence** Original verification passed: `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm test` (14 files / 38 tests), `npm run build`, and `npm run e2e -- e2e/offline.spec.ts` (3 real-backend journeys). T019 corrections: red proofs reproduced cold offline deep-link, resignation/leave/replacement confirmation availability, absent cached-game network banner, and expiry-triggered offline GET; after fixes `npm run build && npm run e2e -- e2e/offline.spec.ts` passed (8/8 real-backend journeys, including a real 10-second invitation lifetime and reconnect), and format/lint/typecheck/Vitest (14 files / 38 tests) and `git diff --check` passed. Independent final review approved the corrected T019/T020 diff. Final `./scripts/verify.sh` passed all five stages, including 84 real-backend browser journeys in 8.4m. With the `/api/` `NetworkFirst` runtime rule and denylist removed, the cache proof failed as required: expected no API URLs but found `/api/v1/meta`, `/api/v1/rulesets`, and `/api/v1/games/<id>`; the mutation was restored.
  - **Depends on** T018 (journeys share fixed ports and `dist/`, so no parallel run)

---

## Phase 10: US8 — One gate and local operation (P2)

- [x] T020 [US8] Create the single gate `scripts/verify.sh`, the local guide and the durable-doc updates
  - **Covers** R45, R49, R52, US8 · S7
  - **Read first** plan § *Gate* · AGENTS.md § *Definition of Done*, § *Where this stands* · constitution VII
  - **Files** `scripts/verify.sh`, `frontend/vitest.config.ts` (remove `passWithNoTests`, set in T004 for the empty app), `docs/local-operation.md`, `AGENTS.md` (Gate + Definition of Done: `./scripts/verify.sh`), memory `frontend-absorbs-integration.md` (gate now exists)
  - **How** `set -euo pipefail`; stages printed `[n/5] <name>`: 0 `install.sh`; 1 `contracts npm run check`; 2 `backend ./mvnw -q verify && ./mvnw spotless:check`; 3 `frontend npm run check`; 4 `frontend npm run e2e` (all journeys). Each stage runs under a timeout (`VERIFY_STAGE_TIMEOUT`, default 1800 s, via a Node or `perl -e alarm` wrapper — macOS has no `timeout`); a non-zero exit, a timeout, or a stage that reports zero tests run (Playwright `--pass-with-no-tests` not set, Vitest list empty) → exit non-zero naming it; `VERIFY_FAIL_STAGE=<n>` makes stage n run its real command and then a failing check inside the same stage subshell, so the failure travels through the same `run_stage` exit-code path as a real one (for the S7 proof). Guide: commands, ports (5173, 5174, 4173–4177, 8080–8085), `BATTLESHIP_*` overrides, lifetime limits (15 min idle, 2 h, 5 min retention, restart loss), common failures (port in use, wrong runtime, backend down, expired invite, restart, `127.0.0.1` vs `localhost`).
  - **Proof** (1) `./scripts/verify.sh` exits 0 with all five stages listed; (2) for n in 1–4, `VERIFY_FAIL_STAGE=n ./scripts/verify.sh` exits non-zero and names stage n; `VERIFY_STAGE_TIMEOUT=1 ./scripts/verify.sh` exits non-zero naming a timeout; (3) `python3 scripts/sync-agent-files.py --check` passes.
  - **Verify** `./scripts/verify.sh && for n in 1 2 3 4; do ! VERIFY_FAIL_STAGE=$n ./scripts/verify.sh || exit 1; done`
  - **Mutation** Make `run_stage` ignore a non-zero exit when n=4; proof (2) for n=4 must fail.
  - **Evidence** `./scripts/verify.sh` passed all five stages, including 79 browser journeys (7.9m); `VERIFY_FAIL_STAGE=1..4` each ran its real command and reported the injected stage failure (exit 97 through `run_stage`); `VERIFY_STAGE_TIMEOUT=1` reported stage 0 timed out after 1s; `python3 scripts/sync-agent-files.py --check`, `git diff --check`, `bash -n scripts/verify.sh`, and `node --check scripts/verify-timeout.mjs` passed. With stage 4's non-zero exit temporarily ignored, the full gate logged the injected stage-4 failure but returned 0; the mutation was restored.
  - **Depends on** T019

---

## Extension: SonarQube and local Docker builds

The owner approved at most 31 total tasks and subsequently approved raising the earlier 40-file
ceiling as needed to fix actionable findings and close the tasks. This list uses eight new tasks
(28 total); the revised forecast is approximately 71 changed files. Count paths against the
extension starting commit and report the actual total. Do not suppress or silently defer a finding.
A passing new-code gate alone does not close T022 or T023.

**Extension evidence, 2026-11-06.** The local build and `./scripts/verify.sh` passed; the final-tree
gate ran 105 real-backend browser journeys after the accessibility-test synchronization.
Three Linux arm64 images built and were started: backend
health and frontend deep-link/API proxy checks passed; the combined image passed LAN-address HTTPS
health, host/guest create and join, CSRF/cookie checks, live update, private backend binding and
graceful shutdown. `curl --cacert` verified the temporary certificate; Chromium bypassed its trust
check and no second physical device was available. Removing the SPA fallback made a deep link return
404 instead of 200; removing the backend-origin requirement allowed startup without that setting;
omitting backend SIGTERM prevented the graceful-shutdown log before forced termination.

The refreshed backend scan passed its gate with 10 open issues, including the contract-required
readable CSRF cookie. The final frontend coverage pipeline passed 51 Vitest tests and 105 browser
journeys; its merge covered 101 scripts from 84 journeys. The refreshed frontend scan reported
80.2% new-code coverage (minimum 80%) but failed its gate on one finding: the rules region's
keyboard focus target (`typescript:S6845`). The existing keyboard journey verifies that removing
`tabIndex` prevents scrolling; the section is labeled and keeps the focus target instead of assigning
it a misleading interactive role. The separate backend CSRF finding (`java:S3330`) conflicts with
the contract's readable double-submit token; the session cookie remains HttpOnly and both cookies are
Secure and SameSite=Strict. Neither finding was suppressed or closed.
Historical findings span more files than the approved 40-file ceiling permits. Unchecked tasks below
retain their outstanding proofs and decisions.

The last exported overall lists contain 10 backend findings (one S3330, one each S2589, S2925,
S1172, S5961 and S6353, and four S107) and 56 frontend findings (46 S7764, eight S6819, one
S7718 and one S6845). Reports are local ignored artifacts in `.sonar-reports/`; these counts do not
imply that the historical findings have been resolved or individually accepted.

**Rolling baseline (owner direction, 2026-11-06).** The original T021 16/81 issue-key exports were
overwritten; the retained count and server history cannot establish their exact membership. The owner
directed fresh scans and repair of findings as the baseline changes. Preserve each fresh export and
reconcile it with the immediately preceding preserved export; do not claim a key-by-key comparison
against the lost lists. The reviewed starting snapshots are copied to
`.sonar-reports/baseline/{backend,frontend}/issues.json` (10 and 56 keys; SHA-256 respectively
`ce085811edc0b10aac3a66153038cd8e3ff3e1bbd138ce1bc12399cbb399b604` and
`5cc834df4fd02034d7ffa7285294ca6ef8daa5eb160dc27afabf7236a4219a6d`). They are local ignored evidence.
The refreshed scans reduced backend open keys from 10 to 7 (gate OK) and frontend open keys from
56 to 5, with no new keys. The backend fixes use ASCII-equivalent regex shorthand, a Java 25
no-argument test launcher, and process-aware readiness polling; their focused tests passed, and an
isolated Unicode-regex mutation failed the new ASCII boundary test. The remaining backend findings
cover the contract-required readable CSRF token, the tested null guard, one integrated assertion
journey and four constructor parameter counts. The five frontend findings concern the focusable rules region,
three named non-form groups and the labelled QR canvas. After adding real cross-tab preference and
invitation-fragment privacy tests and merging browser coverage, frontend new-code coverage was 80.4%
(threshold 80%); its gate still failed on the rules-region finding. The named source-line import
check was denied by Sonar's 403 permission response. Preserve these unresolved findings and limits.
The current issue-by-issue reviews and rolling-baseline comparisons are saved locally in ignored
`.sonar-reports/backend/current-review.md` and `.sonar-reports/frontend/review.md`; open issues were
not suppressed or marked resolved.
The current extension changes 71 distinct paths relative to its recorded starting commit `2a26eff`,
including the new privacy tests and backend quality fixes; the owner approved raising the earlier ceiling as needed.
The final `./scripts/verify.sh` run passed all five stages, including 54 frontend unit tests and
106 real-backend browser journeys. `./scripts/build.sh` also passed after the backend fixes. All
three final-source Docker images built for linux/arm64; standalone and combined HTTPS smoke checks
passed. A separately trusted browser on a second physical LAN device was unavailable.

- [x] T021 [US9] Capture the fresh Sonar baseline and issue manifest in `.sonar-reports/`
  - **Covers** R63, R64; S9 baseline
  - **Read first** `docs/local-operation.md` § *Local SonarQube analysis*; existing Sonar configuration, reports and project settings
  - **Files** ignored `.sonar-reports/backend/`, `.sonar-reports/frontend/`; no product source
  - **How** Check the existing local server, quality gates and profiles. Run both documented scans and export reports even when a gate fails. Preserve credentials in the environment. Inventory every overall OPEN issue by project, rule, source file and issue key, plus new-code findings and coverage gaps. Distinguish scanner failure from processed analysis with failing gate.
  - **Proof** (1) both exports identify project key and analysis time; (2) manifest counts match the exported overall OPEN issues; (3) gate and coverage state is recorded separately per project; (4) security hotspots have their own evidence status.
  - **Verify** `sonar-local status`; `cd backend && ./mvnw clean install org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar -Dsonar.qualitygate.wait=true`; `cd frontend && npm run test:coverage && sonar-local scan`; then both `sonar-local report` commands in `docs/local-operation.md`.
  - **Mutation** Remove one issue from the recorded inventory without changing the export; proof (2) must fail on a recount.
  - **Evidence** Baseline exports contain 16 backend issues and an OK gate, and 81 frontend issues and an ERROR gate. Local recount matched each summary total; omitting one issue in memory yielded `FAIL expected 16, got 15` and `FAIL expected 81, got 80`. The frontend failure is the 24.8% new-code Vitest coverage measure and new-code findings; browser journeys are not imported. Reports remain ignored under `.sonar-reports/`.
  - **Depends on** T020

- [x] T022 [US9] Repair backend actionable findings from the T021 manifest in `backend/`
  - **Covers** R63, R64; S9 backend
  - **Read first** T021 backend report and security hotspot; constitution security and server-authority principles; existing backend tests
  - **Files** only the backend source/tests named by the T021 issue manifest; no contract change unless a dependent is updated in the same task
  - **How** For each manifest key, inspect the actual behavior, make the smallest behavior-preserving repair with an observable test where useful, or record why the proposed change would break an approved boundary. In particular verify the JavaScript-readable CSRF cookie's double-submit role and that the separate session cookie is HttpOnly. Do not hide issues through exclusions, suppression, false-positive dispositions, gate edits or constructor wrappers that only conceal dependencies. Re-export the backend overall list after scanning.
  - **Proof** (1) changed behavior passes focused tests; (2) the backend gate passes; (3) the refreshed overall issue list is reconciled key by key to the preserved rolling baseline; (4) any remaining open key has explicit evidence and a decision; (5) no cookie or origin protection was weakened.
  - **Verify** `cd backend && ./mvnw -q verify && ./mvnw spotless:check`; then the backend scan and export commands in `docs/local-operation.md`.
  - **Mutation** Remove the null-command guard protecting the validation response; the named focused proof must fail.
  - **Fresh review evidence (2026-11-06)** The current 10 findings were inspected against their real collaborators and contract. The JavaScript-readable CSRF token is required by the double-submit contract and the separate session cookie remains HttpOnly. Removing the null-command guard made `ProblemMappingIT.anEmptyBodyReportsEveryRequiredField` return 500 instead of 422; restoring it made `./mvnw -q -pl app -am -Dtest='ProblemMappingIT#anEmptyBodyReportsEveryRequiredField' -Dsurefire.failIfNoSpecifiedTests=false test-compile surefire:test` pass. A fresh scan and key-by-key reconciliation remain required.
  - **Depends on** T021

- [x] T023 [US9] Repair frontend actionable findings from the rolling baseline in `frontend/`
  - **Covers** R63, R64; S9 frontend findings
  - **Read first** the preserved frontend overall and new-code lists; R01, R53–R55; the affected component and journey tests
  - **Files** frontend source/tests named by the current preserved findings, within the owner-approved expanded scope
  - **How** Start with the new-code findings in `Gameplay.tsx`, `Placement.tsx` and `ShipActionDialog.tsx`, then process the historical overall list key by key. Preserve server-authoritative state and keyboard/touch/dialog semantics. Add tests for behavior that can be observed without fake game data; run real-backend journeys for game screens. Do not count Playwright execution as Sonar coverage until T024 verifies an importable report.
  - **Proof** (1) focused tests and real-backend journeys pass; (2) the refreshed overall issue list is reconciled key by key; (3) remaining open keys and file-ceiling impact are explicit; (4) no test was weakened.
  - **Verify** `cd frontend && npm run check && npm run e2e`; then the frontend scan and export commands in `docs/local-operation.md`.
  - **Mutation** Remove a repaired notification's accessible status behavior; its focused test must fail.
  - **Fresh mutation evidence (2026-11-06)** Replacing the success-toast native status output with a `div` made `npm test -- --run tests/toasts.test.tsx` fail because no accessible `status` existed. Restoring the file byte-for-byte made that focused test pass. The current baseline and refreshed issues still need the final scan and reconciliation.
  - **Depends on** T021; may run independently of T022

- [ ] T024 [US9] Prove frontend coverage and refreshed Sonar gates with real backend journeys
  - **Covers** R53, R63, R64; S9
  - **Read first** T021 frontend coverage data; `frontend/playwright.config.ts`, journey fixtures and Vitest coverage configuration
  - **Files** frontend coverage harness/config and `docs/local-operation.md`; exact dependency and file additions count against the ceiling
  - **How** The existing Vitest LCOV omits gameplay screens. Instrument existing Playwright journeys against the packaged backend, map executed browser code back to source, merge with Vitest LCOV and confirm imported source lines in Sonar before claiming coverage. Use exact dependencies only if needed. If reliable mapping is infeasible within the budget, leave the gate failure explicit. Rerun/export both projects; compare their overall OPEN lists and gates with T021.
  - **Proof** (1) a named gameplay line appears covered in generated LCOV and the refreshed Sonar report; (2) a real-backend journey still passes; (3) both final gates and issue lists are recorded; (4) no quality gate, exclusion or test rule was relaxed.
  - **Verify** `cd frontend && npm run test:coverage && npm run e2e`; then both scan/export command pairs in `docs/local-operation.md` and `./scripts/verify.sh`.
  - **Mutation** Disable browser coverage collection for one named gameplay journey; proof (1) must fail without a falsely green coverage import.
  - **Coverage evidence (2026-11-06)** The instrumented suite passed 106 journeys and merged 101 scripts from 85 journeys. In an isolated copy, removing the coverage file for the three-layout keyboard journey changed `Gameplay.tsx` line 180 from `DA:180,6` to `DA:180,0`; an explicit named-line assertion then failed, although the aggregate merge correctly still succeeded. The missing live coverage file was recovered by rerunning that journey; 85 files were merged again. The Sonar source-line API returned 403 for the available token, so the import of that specific line is not independently verified.
  - **Depends on** T022, T023

- [x] T025 [US10] Add the complete local artifact build script in `scripts/build.sh`
  - **Covers** R45, R49, R65; S10 local build
  - **Read first** `scripts/install.sh`, `scripts/verify.sh`, package scripts and Maven modules
  - **Files** `scripts/build.sh` and a focused script check if needed
  - **How** Reuse existing exact-install and contract/backend/frontend build commands; print stage names, fail on a failed stage, and leave the packaged JAR and built static app in their normal ignored outputs. Do not introduce a root package manager or global tool.
  - **Proof** (1) JAR and `frontend/dist/` exist after a clean build; (2) a failed stage exits nonzero and names the stage; (3) no development address is embedded in `dist/`.
  - **Verify** `./scripts/build.sh`; `./scripts/verify.sh`.
  - **Mutation** Make a failed contract stage return zero; proof (2) must fail.
  - **Isolated mutation evidence (2026-11-06)** In a disposable copy of the script with a simulated contract-stage exit 42, the original exited 1 and named `OpenAPI contract check`. Changing the copied failure branch to ignore that failure returned 0 and the harness reported `FAIL: build reported success after failed contract stage`. A fresh real build remains required.
  - **Depends on** T020; may run independently of T022–T024

- [x] T026 [US10] Build the frontend-only Linux image from root context
  - **Covers** R65, R67; S10 frontend image
  - **Read first** T025 build outputs, `contracts/openapi.yaml`, frontend build scripts and current same-origin proxy
  - **Files** root `Dockerfile`, `.dockerignore`, `scripts/build-docker-frontend.sh`, image runtime configuration
  - **How** Use a pinned-major Node 24 Linux build stage and a `frontend` target with static SPA serving, configurable API upstream and unbuffered SSE forwarding; run Docker with repository root as build context. Tag `battleship-frontend:local`. Document that its upstream must provide the same public origin expected by the backend.
  - **Proof** (1) native-architecture image builds without host-built `node_modules`; (2) deep links, API and SSE proxying work against a real backend; (3) missing upstream has a useful failure.
  - **Verify** `./scripts/build-docker-frontend.sh`; inspect image architecture and run a real backend smoke/browser journey through the image.
  - **Mutation** Buffer the SSE path or remove deep-link fallback; proof (2) must fail.
  - **Fresh evidence (2026-11-06)** The final-source native arm64 image built successfully and failed clearly without its upstream; with the real backend, a deep link returned 200, game creation through the proxy returned 201, and the authenticated event stream delivered its initial snapshot within four seconds. In an isolated container, replacing the mounted SPA fallback with `=404` made the deep link return 404 instead of 200.
  - **Depends on** T025

- [x] T027 [US10] Build the backend-only Linux image from root context
  - **Covers** R65, R67; S10 backend image
  - **Read first** T026 shared Dockerfile; backend Maven wrapper, `BattleshipProperties` and packaged artifact tests
  - **Files** root `Dockerfile`, `scripts/build-docker-backend.sh`
  - **How** Add Java 25 Linux build/runtime stages and a `backend` target using the repository root context for the contract. Tag `battleship-backend:local`. Require an exact public base origin and expose only its API port when this standalone image is run by an operator.
  - **Proof** (1) image builds without using a macOS-built JAR; (2) health endpoint becomes ready; (3) invalid public origin fails clearly and container stops on signal.
  - **Verify** `./scripts/build-docker-backend.sh`; inspect image architecture and run health/configuration smoke checks.
  - **Mutation** Remove public-origin validation; proof (3) must fail.
  - **Fresh evidence (2026-11-06)** The native arm64 image built successfully and rejected malformed and missing public origins; `docker stop --time 15` produced Spring's graceful-shutdown-complete log. Bypassing the image's missing-origin entrypoint guard in an isolated container allowed the JAR to start with its default origin. In a disposable backend copy, removing the HTTP/HTTPS scheme predicate made `ConfigurationValidationIT.ftpPublicBaseUrlStopsStartupAndNamesSetting` fail: `Expected java.lang.RuntimeException to be thrown, but nothing was thrown`; the unmutated test passed.
  - **Depends on** T026

- [x] T028 [US10] Build and run the combined HTTPS image for a LAN browser game
  - **Superseded (2026-11-07, owner decision)** The combined HTTPS image, its runner and shell tests were removed in the script consolidation. LAN play is now `scripts/local-deploy.sh` (HTTPS via a generated self-signed certificate); the combined image is the Render image (`scripts/build-docker-render.sh`). Evidence below is historical.
  - **Covers** R66–R69; S10 combined/LAN
  - **Read first** T026–T027 image targets; backend Origin and cookie tests; `docs/local-operation.md`
  - **Files** root `Dockerfile`, combined entrypoint/proxy config, `scripts/build-docker-local-combined.sh`, `scripts/run-docker-local-combined.sh`, new `scripts/tests/run-docker-local-combined.test.sh`, `docs/local-operation.md`, `AGENTS.md`, `AGENTS.md`
  - **How** Reuse the image build stages. Serve SPA and `/api` through one HTTPS port (container 8443, configurable host port), keep the backend private, proxy SSE without buffering, and set exact `BATTLESHIP_PUBLICBASEURL`. Preserve no-argument explicit TLS configuration. The positional IPv4 mode validates the address, uses it as the public-origin authority, builds a missing image, and creates or reuses a restrictive per-IP TLS pair only after SAN, expiry and key-match checks. Partial or invalid files fail without replacement. Child termination and unexpected exit must stop the container. Document that each client must trust the certificate. Keep LAN clients on one exact origin.
  - **Proof** (1) combined image builds and starts; (2) API, invitations, CSRF POST and SSE work through the exact HTTPS origin; (3) a second LAN device completes a real game interaction where environment permits; (4) isolated real-OpenSSL shell integration cases cover valid IPv4, reuse, SAN/expiry/key mismatch, partial pairs, conflicting overrides, no-argument compatibility, automatic image build, configured port, missing prerequisites and occupied port; (5) stop kills proxy and backend; (6) `./scripts/verify.sh` passes.
  - **Verify** `bash scripts/tests/run-docker-local-combined.test.sh`; `./scripts/build-docker-local-combined.sh`; `./scripts/run-docker-local-combined.sh <IPv4>` with client trust installed; real browser/game flow; `./scripts/verify.sh`; `python3 scripts/sync-agent-files.py --check`.
  - **Mutation** Remove IP SAN checking and require the wrong-address certificate case to pass; the integration proof must fail. Existing proxy streaming and shutdown mutations remain covered by proofs (2) and (5).
  - **LAN runner evidence (2026-11-06)** `bash scripts/tests/run-docker-local-combined.test.sh` passed with macOS Bash 3.2 and LibreSSL; accepting an incorrect SAN made its negative case fail. The positional runner started the combined image on the host LAN address, and `curl --cacert` returned `{"live":true,"ready":true}` before the temporary container stopped. Client trust and a second physical device were not tested in this check.
  - **Final-image smoke (2026-11-06)** The rebuilt arm64 combined image passed HTTPS health with a temporary certificate explicitly trusted by curl, host creation (201), separate-cookie guest join (200), authenticated SSE snapshot and graceful shutdown. Browser/system trust and a second physical device were unavailable and remain unverified. The final repository gate remains required.
  - **Depends on** T026, T027

## Extension: GitHub Actions pipeline

Eight tasks (T029–T036; 36 in total), assurance Lean, forecast in plan § *Extension: GitHub Actions pipeline*.
Dependency order: T029 first (governance permits the workflows); T030, T031 and T032 write
disjoint parts of two files in sequence; T034 documents what exists; T035 lints everything; T036 is the owner's
check on GitHub. T030–T035 are checked only when the workflow files and `docs/ci.md` exist and pass the lint proof.

- [x] T029 Amend governance so the pipeline is allowed and stale "no CI" statements are true
  - **Covers** R70–R73 (permission); constitution 2.1.0
  - **Read first** constitution VII and Scope; `AGENTS.md` § *Definition of Done*, § *Contract product boundary*
  - **Files** `openspec/config.yaml`, `AGENTS.md`, `docs/local-operation.md`, `docs/render-deployment.md`, `docs/index.md`, `AGENTS.md`, saved memory `ci-pipeline-in-003-frontend`
  - **How** Bump 2.0.1 to 2.1.0 with a Sync Impact Report entry; replace the "no hosting or CI infrastructure" statements; keep `contracts/` itself CI-free and the `master` historical wording untouched.
  - **Proof** (1) `grep -rniE "hosting or CI|no CI|CI infrastructure" AGENTS.md docs AGENTS.md` finds nothing; (2) `python3 the former workflow script --check` passes; (3) the constitution footer and report entry both read 2.1.0.
  - **Verify** the grep and sync check above
  - **Mutation** Restore "no hosting or CI infrastructure" in `AGENTS.md`; proof (1) must report it.
  - **Depends on** T020

- [x] T030 Build-only run on branch pushes in `.github/workflows/ci.yml`
  - **Covers** R70, R72, R74, R75, R77
  - **Read first** root `Dockerfile` `jar` and `dist` stages; `scripts/local-build.sh`
  - **Files** `.github/workflows/ci.yml` (build job only)
  - **How** `on.push.branches-ignore: [master]` and `tags-ignore: ['**']`; one job running the backend package without tests and the frontend `npm ci && npm run build`; `permissions: contents: read`, timeout, concurrency per ref with cancel, pinned actions.
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) the two build commands succeed locally; (3) the file has no `master` push trigger and no test command.
  - **Verify** `actionlint .github/workflows/ci.yml && zizmor .github/workflows/ci.yml`; `cd backend && ./mvnw -q -DskipTests package`; `cd frontend && npm ci && npm run build`
  - **Mutation** Remove `branches-ignore: [master]`; the trigger assertion (3) must fail.
  - **Depends on** T029

- [x] T031 Pull-request gate in `.github/workflows/ci.yml`
  - **Covers** R71, R74, R75, R77
  - **Read first** T030 output; `docs/local-operation.md` § gate
  - **Files** `.github/workflows/ci.yml` (gate job only)
  - **How** `on.pull_request.branches: [master, rewrite_prod_ready, 'feature/**']`; Temurin 25, Node 24, Maven and npm caches; run `./scripts/local-build.sh`; upload `frontend/playwright-report`, `frontend/test-results` and backend surefire/failsafe reports with `if: failure()`; no `pull_request_target`, no secrets; timeout 45 minutes; concurrency per pull request.
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) the job's only test command is `./scripts/local-build.sh`; (3) the artifact paths exist after a failing local run.
  - **Verify** `actionlint .github/workflows/ci.yml && zizmor .github/workflows/ci.yml`; `grep -c local-build.sh .github/workflows/ci.yml`
  - **Mutation** Change the trigger to `pull_request_target`; `zizmor` must fail.
  - **Depends on** T030

- [x] T032 Release build, smoke and GHCR push in `.github/workflows/release.yml`
  - **Covers** R73 (image), R74–R77
  - **Read first** `scripts/build-docker-render.sh`, root `Dockerfile` `render` target, `docs/render-deployment.md`
  - **Files** `.github/workflows/release.yml` (verify, build, push part)
  - **How** `on.push.tags: ['v[0-9]+.[0-9]+.[0-9]+']`; checkout with full history and master-ancestry check; build `render` for `linux/amd64`; run the existing `--smoke` checks through `scripts/build-docker-render.sh`; push to `ghcr.io/<owner>/battleship` with `X.Y.Z`, `X.Y` and `vX.Y.Z` tags and the source label; job permissions `contents: write` and `packages: write` only; concurrency per tag without cancel.
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) `./scripts/build-docker-render.sh --smoke` passes locally; (3) a tag pattern test accepts `v1.2.3` and rejects `v1.2`, `v1.2.3-rc1` and `1.2.3`.
  - **Verify** `actionlint .github/workflows/release.yml && zizmor .github/workflows/release.yml`; `./scripts/build-docker-render.sh --smoke`
  - **Mutation** Replace the tag pattern with `v*`; proof (3) must fail. Remove the ancestry step; the step-presence check must fail.
  - **Depends on** T031

- [x] T033 GitHub Release creation with digest and re-run safety in `.github/workflows/release.yml`
  - **Covers** R73 (release, re-run), R74
  - **Read first** T032 output
  - **Files** `.github/workflows/release.yml` (release step)
  - **How** After the push, read the image digest; `gh release create` with generated notes and a body naming the image reference, `@sha256:` digest, `BATTLESHIP_PUBLICBASEURL`, one instance and `docs/render-deployment.md`; if the release exists, compare the digest and fail on mismatch instead of overwriting.
  - **Proof** (1) `actionlint` and `zizmor` pass; (2) the release body rendered locally from the workflow's template contains reference, digest and instructions; (3) the existing-release branch is present.
  - **Verify** `actionlint .github/workflows/release.yml && zizmor .github/workflows/release.yml`
  - **Mutation** Remove the digest from the body template; proof (2) must fail.
  - **Depends on** T032

- [x] T034 Write `docs/ci.md`
  - **Covers** R78
  - **Read first** the workflow files; `docs/render-deployment.md`, `docs/configuration.md`
  - **Files** `docs/ci.md`
  - **How** Trigger-to-job diagram, release procedure (`git tag vX.Y.Z` on `master`, `git push origin vX.Y.Z`), repository settings, GHCR in Render, the no-gate-on-release risk; link, do not repeat.
  - **Proof** (1) every workflow file and trigger named in the guide exists in the workflows; (2) links resolve; (3) `docs/index.md` links it.
  - **Verify** `grep -n "ci.md" docs/index.md AGENTS.md`; manual link check
  - **Mutation** Rename a workflow file; proof (1) must fail.
  - **Depends on** T033

- [x] T035 Lint and hardening proof over all workflows
  - **Covers** S11, R74, R75, R77
  - **Read first** both workflow files
  - **Files** none (evidence recorded in this task)
  - **How** Run `actionlint` and `zizmor` on `.github/workflows/`; check that every `uses:` is a full SHA, every job has `permissions` and `timeout-minutes`; run `python3 the former workflow script --check`.
  - **Proof** (1) both linters exit 0; (2) the pin and permission checks report no violation; (3) the sync check passes.
  - **Verify** `actionlint && zizmor .github/workflows/`; `grep -n "uses:" .github/workflows/*.yml`
  - **Mutation** Unpin one action, drop one `permissions` block and change one tag glob in a scratch copy; each must make a named check fail. Record the output.
  - **Depends on** T034

- [ ] T036 Owner verifies triggers on GitHub
  - **Covers** R70–R73 (behaviour GitHub alone can show)
  - **Files** none
  - **How** Owner checklist after approval: push a branch (build only); open a pull request (gate, artifacts on a forced failure); merge (nothing runs on `master`); push a test tag after approval (GHCR image, release body, re-run safe); set Actions permissions, GHCR visibility and branch protection from `docs/ci.md`.
  - **Proof** The owner records the run links; this task is never checked on local evidence.
  - **Verify** n/a, GitHub
  - **Depends on** T035

## Dependencies and execution order

```text
T001 ─ T002 ─ T003 ─┐
T004 ───────────────┴─ T005 ─ T006 ─ T007 ─ T008 ─ T009 ─ T010 ─ T011 ─ T012 ─ T013 ─ T014 ─ T015 ─ T016 ─ T017 ─ T018 ─ T019 ─ T020
T020 ─ T021 ─┬─ T022 ─┐
             └─ T023 ─┴─ T024
T020 ─ T025 ─ T026 ─ T027 ─ T028
T020 ─ T029 ─ T030 ─ T031 ─ T032 ─ T033 ─ T034 ─ T035 ─ T036
```

- Parallel: only T001 ∥ T004 (contracts/backend vs `frontend/`). Journeys share fixed ports and `dist/`, so
  frontend tasks never run concurrently.
- Story tasks are serial: all screens share `session/gameStore.ts`, the router and the journeys' helpers.
- Example: session A runs T001→T002→T003 while session B runs T004; T005 starts when both finish.
- Extension: T021 and T025 can run independently. T022 and T023 can run independently after T021.
  T024 follows both Sonar repair tasks; T026→T027 share the Dockerfile; T028 follows T027.

## Implementation strategy

1. **MVP** = Phases 1–3 (T001–T008): a host can create and share against the real backend.
2. Then T009–T013 complete the P1 game loop (S1). Stop and run all journeys so far.
3. T014–T016 (P2 leaving, recovery, lifetime), T017–T018 (P2 inclusive use), T019 (P3), T020 (gate).
4. T021–T024 reconcile both Sonar projects and coverage. T025–T028 deliver the local build and
   images, ending with a real HTTPS LAN flow and the unchanged repository gate.

## Coverage map

| Requirement | Task(s) | | Requirement | Task(s) |
|---|---|---|---|---|
| R01 R02 R04 | T007, then each screen task T008–T014 | | R30 | T013, T014 |
| R03 R05 R13 R22 R23 R56 | T007 (R22 429: T015) | | R31 | T010, T012, T018 |
| R06 | T007 | | R32 | T017 |
| R07 | T001, T007, T009, T016 | | R33 R34 R35 | T006 (R33 sound/haptics: T017) |
| R08 R09 R10 R15 | T009 | | R36 | T013 |
| R11 R12 R14 | T008 | | R37 R38 | T006 (Settings and shared header), T007 (Rules dialog and Home), T008 (toasts), T014 (Resign match) |
| R16 | T008, T016 | | R39 R40 R41 | T006 (primitives, reduced motion, forced colours), T010 (board keyboard), T018 |
| R17 | T010 | | R42 R43 R44 | T019 (R43 actions disabled: T015) |
| R18 | T011 | | R45 | T004, T020 |
| R19 | T012 | | R46 R47 R48 | T005 incl. dev/prod CLI (R47 no dev address: T004) |
| R20 R21 | T010, T012 | | R49 | T020 |
| R24 | T008, T012, T015 | | R50 | T005, T015, T016 |
| R25 R26 | T015 (R26 `GAME_UNAVAILABLE`: T016) | | R51 | T005, T009 |
| R27 | T007, T009 (join 410), T016 | | R52 | T020 |
| R28 R29 | T014 | | R53 R54 R55 | journeys in T005–T019 · units in T005–T008, T017 · B1 · `route.fulfill` ban (T005) |
| R58 | T006, T018 | | R57 | T001, T002, T003, T008 |
| R59 | built in T006–T014; asserted in T008(7), T010(4), T012(6), T013(2); every other widget by T018(8) screenshots | | R60 | T006 (Home), T008, T009, T010, T012 (exclusions) |
| R61 | T006 (controls), T011, T018 | | | |
| R62 | T010 | | | |

| Success criterion | Task(s) |
|---|---|
| S1 | T013 |
| S2 | T009 |
| S3 | T012, T015, T016 |
| S4 | T018 |
| S5 | T017 |
| S6 | T019 |
| S7 | T005, T020 |
| S8 | T018 (screenshots + owner review, B5) |
| S9 | T021–T024 |
| S10 | T025–T028 |

| Extension requirement | Task(s) |
|---|---|
| R63 R64 | T021–T024 |
| R65 | T025–T028 |
| R66 | T028 |
| R67 | T026–T028 |
| R68 | T028 |
| R70 R72 | T030 |
| R71 | T031 |
| R73 | T029, T032, T033 |
| R74 R75 R77 | T030–T033, T035 |
| R76 | T032 |
| R78 | T034 |
| S11 | T035 (T036 for GitHub behaviour) |

## Gate

Until T020: per-task **Verify** commands, plus `cd backend && ./mvnw -q verify && ./mvnw spotless:check` and
`cd contracts && npm run check` whenever those trees change. After T020: `./scripts/verify.sh`.
