# Tasks: Battleship Frontend and Local Integration

**Feature**: `003-frontend` · **Branch**: `feature/003-frontend` · **Date**: 2026-10-18
**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)
**Data model**: [data-model.md](data-model.md) · **UI surface**: [contracts/ui-surface.md](contracts/ui-surface.md) ·
**Run guide**: [quickstart.md](quickstart.md)
**Contract (authority)**: [`contracts/openapi.yaml`](../../contracts/openapi.yaml)
**Governance**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) v1.3.2 · [`AGENTS.md`](../../AGENTS.md)

**Assurance level**: Standard (plan.md § *Assurance level*). **Forecast**: 20 tasks — met exactly.
**Status**: not started.

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
  `.specify/templates/tasks-template.md`). Record the mutation's failing output in the task's commit message body.
- **No fake backend.** No MSW, recorded snapshots, fixtures of game data or reimplemented rules (R53). Units only
  for the R54 list plus `snapshotGate` (decision B1). Layout is CSS only (research D18), so it has no unit.
- **Visual source.** `docs/rewrite_context_doc/Design/Naval Command Center — Interactive HTML Mockup.html` is the
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
| B4 | Touch drags in journeys use a Chromium CDP session (`Input.dispatchTouchEvent` touchStart → touchMove → touchEnd) in a context from Playwright's `devices['Pixel 7']` descriptor (`isMobile` + `hasTouch`, so `(hover: none)` matches); Playwright has no touch-drag API. Taps use `locator.tap()` | T011, T018 |
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
  - **Files** `contracts/openapi.yaml`, `contracts/README.md` (Client rules: one line), `SessionRegistry.java`, new `application/registry/BrowserGameLimitException.java`, `CreateGameUseCase.java`, `JoinGameUseCase.java`, `ProblemAdvice.java`, `app/src/test/.../CapacityIT.java`, `app/src/test/.../WireConformanceIT.java`, `application/src/test/.../usecase/CreateGameUseCaseTest.java` (L62, L77, L205 — only assertions about the per-browser cap), `application/src/test/.../usecase/InvitationLifecycleTest.java` (L118, L122 — same rule; the games-ceiling assertions in these and `ExpireGamesUseCaseTest`/`LifetimeBoundaryTest` stay `service-unavailable`), `specs/002-backend/spec.md` (R62 L381, S11 L565)
  - **How** Contract: add `browser-game-limit` to `ProblemCode`; add `browserGameLimit` example (`status: 409`, `title: Browser already in a game`) to `Conflict`; description: "(create, join) this browser already holds its live game; do not retry; play again once it ends or expires"; add `'409': $ref Conflict` to `createGame`. Backend: `admitGame` throws `BrowserGameLimitException` at line 58-59 (the sessions ceiling at 67/99 stays `CapacityExceededException`); both use cases map it to `ApplicationFailure("browser-game-limit", …, retryAfter null)`; `ProblemAdvice` maps `BROWSER_GAME_LIMIT` → `Entry(409, "Browser already in a game")`, no `Retry-After`. Regenerated `ProblemCode` comes from the build.
  - **Proof** `CapacityIT`: (1) `aBrowserAtItsLiveGameCapIsRefusedOnCreateAndOnJoin` expects 409 `browser-game-limit`, no `Retry-After`; (2) games/streams ceilings still 503 + `Retry-After`; header comment (L28-29) reworded. `WireConformanceIT`: (3) `conflictCodes()` (~L610) gains `browser-game-limit` and `conflict(code)` a driver (one browser creates twice); (4) `everyEmbeddedExampleConformsToItsSchema` covers the new example.
  - **Verify** `cd contracts && npm ci && npm run check` · `cd backend && ./mvnw -q verify && ./mvnw spotless:check`
  - **Mutation** Map `BrowserGameLimitException` back to `service-unavailable` in `CreateGameUseCase`; assertion (1) must fail.
  - **Depends on** —

- [x] T002 Stop the forked test JVM lingering 30 s after `verify` in `backend/`
  - **Covers** R57 · spec Assumption 5
  - **Read first** plan.md fix 2 · `backend/app/src/main/resources/application.yaml:7,14` (`server.shutdown: graceful`, `timeout-per-shutdown-phase: ${battleship.shutdown-drain-seconds}s`) · root `backend/pom.xml:92-106` (Surefire/Failsafe, default fork) · `app/pom.xml:17-27` · `GracefulShutdownIT`
  - **Files** determined by the diagnosis (expected: `app/src/test/resources/application*.yaml` or Failsafe config); no production behaviour change
  - **How** Use `superpowers:systematic-debugging`. Reproduce: `./mvnw -q verify`, then `grep -l 'kill self fork JVM' app/target/failsafe-reports/*.dumpstream`. Take a thread dump of the lingering fork (`jstack`) to name the blocking thread before changing anything. Fix the root cause (hypothesis: in-process ITs inherit graceful shutdown waiting on open SSE streams); do not lower `GracefulShutdownIT`'s expectations or kill the JVM.
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

- [ ] T005 Build the stack orchestrator, dev/prod commands and the Playwright stack fixture in `scripts/stack.mjs`
  - **Covers** R46, R47, R48, R50, R51 · S7 (dev and prod commands start) · research D10, D11, D12 · decision B3 · US8
  - **Read first** plan § *Local topology* · contracts/ui-surface.md § *Root commands* · Planning/06-integration/specify-input.md (context only)
  - **Files** `scripts/stack.mjs`, `scripts/dev.sh`, `scripts/local-prod.sh`, `config/local.env.example`, `frontend/vite.config.ts`, `frontend/playwright.config.ts`, `frontend/e2e/fixtures/stack.ts`, `frontend/e2e/smoke.spec.ts`, `frontend/tests/redact.test.ts`, `frontend/eslint.config.js` (`route.fulfill` ban in `e2e/**`)
  - **How** `vite.config.ts`: `server`/`preview` `host: 'localhost'`, `port` from `FRONTEND_PORT` (dev 5173, preview 4173), `strictPort: true`, `proxy: {'/api': {target: 'http://127.0.0.1:' + BACKEND_PORT}}` on both (no `changeOrigin` needed; Origin must stay `http://localhost:<port>`). `stack.mjs` (Node stdlib only) exports `startStack({name, frontendPort, backendPort, mode:'dev'|'preview', env})` → `{origin, stop(), restartBackend()}`: refuses if a port is busy (message names component, address, `lsof -i :<port>`), spawns `java -jar <JAR>` with `SERVER_PORT`, `BATTLESHIP_PUBLICBASEURL=http://localhost:<frontendPort>` + `env`, polls `/api/v1/health` until 200 with `ready: true` (timeout 60 s → names component + next step), then spawns `vite`/`vite preview`, prefixes lines `[backend]`/`[frontend]`, redacts `Set-Cookie`, `Cookie`, `#invite=…`, `XSRF-TOKEN=…`, writes to `frontend/e2e/.logs/<name>.log`, kills only its children on stop/SIGINT. CLI: `node scripts/stack.mjs dev|prod`. `dev.sh` builds the JAR if absent; `local-prod.sh` packages the JAR and `npm run build` first. `config/local.env.example` lists every `BATTLESHIP_*` key with its default. Playwright: Chromium only, `workers: 1`, `testDir: e2e`, no `webServer`; fixture `stack` (worker-scoped default stack: `BATTLESHIP_RATELIMIT_CREATEGAMEPERMINUTE`, `…_JOINPERMINUTE`, `…_COMMANDSPERMINUTE`, `…_READGAMEPERMINUTE`, `…_STREAMOPENPERMINUTE` all `1000` — a full game sends ~100 commands per player against a 60/min default) plus `useStack(name, ports, env)` for per-file dedicated stacks. `stack.mjs` exports `redact(line)` and helper `twoPlayers()` giving two isolated contexts.
  - **Proof** `smoke.spec.ts`: (1) page at `http://localhost:4173/` renders; `page.request.get('/api/v1/health')` → `ready: true` through the proxy; (2) an `EventSource` opened via the proxy for a created game receives its first `snapshot` event (created with `page.request` after `getMeta`); (3) `GET /games/abc` and `/join/abc` reload return the app's `index.html`; (4) `startStack` on an occupied port rejects with a message containing `frontend`, the address and `lsof`; (5) unit `tests/redact.test.ts`: `redact()` removes `Set-Cookie`/`Cookie` values, `#invite=…` and `XSRF-TOKEN=…` from synthetic lines; (6) the CLI itself: `FRONTEND_PORT=5174 BACKEND_PORT=8084 node scripts/stack.mjs dev` and `FRONTEND_PORT=4177 BACKEND_PORT=8085 node scripts/stack.mjs prod` are each spawned as a child process; once it prints ready, a page at its origin gets `ready: true` from `/api/v1/health` and receives the first `snapshot` event of a created game through the proxy; SIGINT stops both children (ports free again).
  - **Verify** `cd backend && ./mvnw -q -DskipTests package && cd ../frontend && npm test -- tests/redact.test.ts && npm run build && npm run e2e -- e2e/smoke.spec.ts`
  - **Mutation** Remove the `preview.proxy` block; assertion (1) must fail. Remove the `server.proxy` block; (6) must fail for `dev`. Remove the `#invite=` rule from `redact()`; (5) must fail.
  - **Depends on** T003, T004

- [ ] T006 Build the app shell, design tokens and shared components: router, header, i18n, preferences, theme, dialog, toasts, rules and settings in `frontend/src/app/`
  - **Covers** R33, R34, R35, R37, R38 (header, toasts), R41 (primitive), R58, R61 (controls) · research D4, D5, D17 · S5 (partial)
  - **Read first** plan § *Boot and routing*, § *Visual design*, § *Preferences, i18n…* · data-model § *Persisted* · contracts/ui-surface.md § *Surfaces and widgets* (header, Home, dialogs row) · solitaire `src/i18n/translate.ts`, `src/features/persistence/storageGateway.ts` (port as-is), `src/app/themeController.ts` (Redux-coupled: replace the store with a `get/subscribe` pair, keep `data-theme`) · mockup tokens `:root` (L15-75) and `html[data-theme="light"]` (L77-118), base rules and `:focus-visible` (L160-185), app bar (L343-405), buttons/cards/pills/toasts (grep `.btn`, `.card`, `.pill`, `.toast`, L1664), stepper/meter, `forced-colors` (L1956), settings dialog (L2431), rules dialog (L2470)
  - **Files** `src/main.tsx`, `src/app/{router.ts,App.tsx,Shell.tsx,Header.tsx,ErrorBoundary.tsx}`, `src/i18n/{translate.ts,en.ts,uk.ts,useT.ts}`, `src/preferences/{storageGateway.ts,preferences.ts,themeController.ts}`, `src/components/{Dialog,Button,Card,Pill,Toasts,Stepper,Meter}.tsx` with sibling `.css`, `src/screens/{Home.tsx,RulesDialog.tsx,SettingsDialog.tsx}`, `src/styles/{tokens.css,base.css}`, `tests/{i18nParity.test.ts,preferences.test.ts}`, `e2e/settings.spec.ts`
  - **How** Router: History API, patterns `/`, `/create`, `/join/:gameId`, `/games/:gameId`, else Home; `navigate()` + `popstate`. `main.tsx` renders `<App/>` (T009 inserts the fragment strip before render). Preferences in `localStorage` key `battleship.preferences` = `{locale:'en'|'uk', theme:'dark'|'light'|'system', emoji:boolean, sound:boolean, haptics:boolean}`; invalid stored value → defaults. `tokens.css`: copy the mockup's custom properties verbatim (dark on `:root`, light on `[data-theme="light"]`), font stacks reduced to the system stack. `base.css`: the mockup's base and focus-visible rules, its `forced-colors` rule, `@media (prefers-reduced-motion: reduce) { *, ::before, ::after { animation: none !important; transition: none !important; } }`, hover rules only inside `@media (hover: hover)`. `themeController` sets `data-theme` to `dark`/`light`, resolving `system` from `matchMedia('(prefers-color-scheme: dark)')` and following its `change` event. App frame is a size container (`container-name: app; container-type: inline-size`). Header (mockup app bar): brand, a title slot, Refresh slot (filled on game routes in T007), Settings; below the 47.5rem container width the labels collapse to the mockup's glyphs with accessible names. Components port the mockup's classes; `Toasts` is an `aria-live="polite"` region, toasts `role="status"` (`alert` for errors), auto-dismiss 6.5 s, × close. `Dialog`: native `<dialog>` + `showModal()`, `aria-labelledby` its heading, `cancel` prevented when `dismissible=false`, focus returns to opener. Ruleset/ship names: keys `ruleset.<id>` / `ship.<shipTypeId>` with raw-id fallback. Rules dialog: advertised rulesets side by side with their facts (from `listRulesets`). Settings: theme Dark/Light/System, emoji, language, sound, haptics (Resign match added in T014). Home (mockup Home): title, New game, Game rules, Settings (Return to your game added in T007; no Join game — R60).
  - **Proof** Units: (1) `en` and `uk` key sets equal; (2) preferences round-trip and fall back on corrupt JSON and on `localStorage` throwing. `settings.spec.ts`: (3) switching to Ukrainian and light theme applies without reload and survives reload; (4) theme System with `emulateMedia({colorScheme:'light'})` then `'dark'` flips `html[data-theme]` without reload; (5) `getComputedStyle(document.documentElement).getPropertyValue('--accent')` equals the mockup's value per theme (`#3becb0` dark, `#1d7fa8` light); (6) Settings dialog closes on Escape and focus returns to its opener.
  - **Verify** `cd frontend && npm test -- tests/i18nParity.test.ts tests/preferences.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts`
  - **Mutation** Delete one key from `uk.ts`; (1) must fail. Make `system` resolve to `dark` unconditionally; (4) must fail.
  - **Depends on** T005

---

## Phase 3: US1 — Host creates and shares (P1) 🎯 MVP

**Independent test**: create a game per ruleset; waiting room shows facts, lifetime, copy and QR.

- [ ] T007 [US1] Build the gateway, session store and the Home/Create screens in `frontend/src/gateway/`
  - **Covers** R01, R02, R03, R04, R05, R07, R13, R19 (slot), R22, R23, R56 · US1 scenarios 1–2 · research D2, D3, D6 · decision B1
  - **Read first** plan § *Gateway*, § *Session store*, § *Outcome → screen* · data-model (whole) · `openapi.yaml` operations `getMeta` (L85, cookie `XSRF-TOKEN`), `listRulesets` (L112), `createGame` (L161), `getGame` (L215), `sendCommand` (L326, `CommandRequest` L1311), `ProblemCode` (L1342), `Problem.retryAfterSeconds`, `Action` (L787), shared responses (L600-726)
  - **Files** `src/gateway/{client.ts,csrf.ts,decode.ts,problems.ts,gameGateway.ts}`, `src/session/{snapshotGate.ts,gameStore.ts,useGame.ts,currentGame.ts}`, `src/screens/{Home.tsx,CreateGame.tsx,GameRoute.tsx,SessionUnavailable.tsx}`, `src/components/RulesetCard.tsx` (+ `.css`), i18n keys, `tests/{decode.test.ts,snapshotGate.test.ts}`, `e2e/create.spec.ts`
  - **How** Implement only the operations this task calls (`getMeta`, `listRulesets`, `createGame`, `getGame`); later tasks add theirs (Constitution IX). `openapi-fetch` `createClient<paths>({baseUrl:'', credentials:'same-origin'})` with a middleware adding `X-XSRF-TOKEN` from `document.cookie` on POST; `getMeta` before the first POST; on `403 request-security-rejected` refresh meta and resend that request once (create/join included). `decode.ts`: require `application/json` (or `problem+json`), `JSON.parse`, and the keys in `REQUIRED_SNAPSHOT_KEYS = ['gameId','version','serverTime','rulesetId','phase','you','allowedActions','expiresAt','yourBoard','opponentBoard'] as const satisfies readonly (keyof GameSnapshot)[]`; failure → `TransportFailure`. Unknown `allowedActions` dropped. `problems.ts`: `Record<ProblemCode, MessageKey>` + status-class fallback. `snapshotGate.accept(current, next)` per data-model § *Version rule*. Store via `useSyncExternalStore`; single action slot with `crypto.randomUUID()`; a command with no answer after 10 s is aborted (`AbortController`) and, like a network failure, sets `checking` (data-model § *Action slot*). `currentGame.ts` (via `storageGateway`): `battleship.currentGameId` set on create/join, cleared on 410/401/404/leave. Create (mockup Create screen L2048): ruleset radio cards (`RulesetCard`, `role="radio"`, Enter/Space selects) with rows field, fleet, ship contact, turn and revealed-water rows from `board`, `fleet`, `shipsMayTouch`, `extraTurnOnHit`, `revealWaterAroundSunk`; captain name `maxLength` 32, help "1–32 characters" (contract `DisplayName`); Back, View details (rules dialog), Create game; submit disabled while pending; network failure/unreadable → "outcome unknown" + Home, never resent; `browser-game-limit` → message + Return to your game when an id is remembered; 422/429/503 worded. `GameRoute` loads `getMeta`, `listRulesets`, `getGame` (contracts/ui-surface.md § *Routes*), fills the header title with the translated ruleset name and its Refresh with `getGame` through the gate, and renders by `snapshot.phase` (placeholder per phase until later tasks) or SessionUnavailable (mockup L2328: status badge, 410 "expired" / 401·404 "no longer available — ended, left or server restarted", Create new game, Return home). Home gains Return to your game when an id is remembered.
  - **Proof** Units: (1) `decode` rejects HTML 502 body, truncated JSON, a JSON object missing `version`; accepts one with all required keys; (2) `snapshotGate`: higher replaces, equal keeps `version`/board but takes `expiresAt`, `serverTime`, `invitationUrl`, `invitationExpiresAt`, lower is dropped. `create.spec.ts`: (3) each advertised ruleset shows its facts before submit; (4) double-click on submit sends exactly one `POST /api/v1/games` (counted with `page.on('request')`); (5) the waiting route `/games/<id>` loads from the server answer; reload rebuilds it; (6) Home after closing and reopening the tab offers Return to your game; (7) with `localStorage` cleared, a second create from the same context shows the `browser-game-limit` message, not "busy", and sends no second request; (8) a create whose answer is hidden (`page.route('**/api/v1/games', r => r.fetch().then(() => r.abort()))` once) shows "outcome unknown" and sends no second `POST`; (9) header Refresh sends one `GET /api/v1/games/<id>`; (10) after the page's `getMeta`, delete the `XSRF-TOKEN` cookie (`context.clearCookies({name:'XSRF-TOKEN'})`), then create: the request log shows one `POST` answered `403 request-security-rejected`, one `GET /api/v1/meta`, one resent `POST` that succeeds — no further requests. (The backend compares header with cookie, so only a missing token is refused; the client calls `getMeta` once per page load, not whenever the cookie is absent.)
  - **Verify** `cd frontend && npm test -- tests/decode.test.ts tests/snapshotGate.test.ts && npm run build && npm run e2e -- e2e/create.spec.ts`
  - **Mutation** Remove the submit `disabled` while pending; (4) must fail. Drop the resend after `request-security-rejected`; (10) must fail. Make `accept` replace on equal version; (2) must fail. Rename the `/api/v1/games/{gameId}` path in a scratch edit of `contracts/openapi.yaml` and run `npm run typecheck`; it must fail (revert the scratch edit).
  - **Depends on** T001, T006

- [ ] T008 [US1] Build the waiting room with copy, QR and replace invitation, and amend `GameSnapshot.version` in `frontend/src/screens/WaitingRoom.tsx`
  - **Covers** R11, R12, R14, R16 (presence timer), R24 (stream), R59 (waiting room), US1 scenarios 3–5 · research D7, D16 · plan fix 4
  - **Read first** plan § *Live stream*, § *Outcome → screen* (invitation rows), fix 4 · `openapi.yaml` `version` (L1029-1035), `invitationUrl` (L1072-1077), `replaceInvitation` (L464), `sendPresence` (L503), `streamGameEvents` (L412, events `snapshot`/`closed`, `: keep-alive`) · `GameSlot.java:145-163` · master `frontend/src/widgets/sharing/QrCodeSheet.tsx` (`git show master:…`; strip `react-i18next`, design `Sheet`/`Button`, CSS import)
  - **Files** `contracts/openapi.yaml` (version description), `contracts/README.md` (one note), `src/gateway/eventStream.ts`, `src/session/presence.ts`, `src/screens/WaitingRoom.tsx` (+ `.css`), `src/components/{Qr.tsx,ConnectionStatus.tsx,ConfirmDialog.tsx}`, `package.json` (`qrcode` 1.5.4, `@types/qrcode` 1.5.6), `tests/qr.test.tsx`, `e2e/waiting.spec.ts`
  - **How** Contract: `version` "…excluding `serverTime`, `expiresAt`, `invitationUrl` and `invitationExpiresAt`, which are always current". `eventStream.ts`: one `EventSource('/api/v1/games/<id>/events')` per open game; close previous before opening; `snapshot` → decode → gate; `closed` handling per plan § *Live stream* (full recovery in T015). Presence: every `meta.limits.presenceIntervalSeconds` while visible and `SEND_PRESENCE` allowed; timer restarts on visibility return. Waiting room (mockup L2126; widgets per contracts/ui-surface.md): stepper Created/Waiting/Placement/Battle from `phase`; connection pill; invitation link field showing `invitationUrl` with everything after `#invite=` replaced by `••••••••` (the full link never enters the DOM); invitation lifetime countdown and meter (remaining ÷ `meta.limits.invitationLifetimeSeconds`, offset-corrected, display only); status notice; "Game created" toast on arrival from Create; Copy link (`navigator.clipboard.writeText(snapshot.invitationUrl)`, success/failure toast), Show QR (`qrcode` lazy `toCanvas`, `aria-label`, the mockup's privacy warning), no token text or Copy token (R60); no `invitationUrl` → "Invitation expired, sharing unavailable"; Replace invitation whenever `NEW_INVITATION` allowed, after a confirmation that the old link stops working; one `getGame` at `invitationExpiresAt`. Guest join snapshot (newer version, phase `PLACEMENT`) moves the host on.
  - **Proof** Unit: (1) `Qr` renders a canvas for a given string with its `aria-label`. `waiting.spec.ts` (default stack): (2) clipboard (granted permission) equals the snapshot's `invitationUrl`; `document.documentElement.outerHTML` (including input values) does not contain the invitation secret; (3) QR dialog opens labelled and closes on Escape; (4) Replace → confirm → clipboard holds a different link; the old link's join is refused; (5) after a guest joins via `page.request`, the host reaches placement without reload. Short-lifetime stack (`BATTLESHIP_INVITATIONLIFETIMESECONDS=10`): (6) "invitation expired" appears without reload; Replace restores sharing; (7) the stepper marks Waiting with `aria-current="step"` and moves to Placement after the guest joins; the lifetime meter's value falls between two reads. `contracts npm run check` passes.
  - **Verify** `cd contracts && npm run check && cd ../frontend && npm test -- tests/qr.test.tsx && npm run build && npm run e2e -- e2e/waiting.spec.ts`
  - **Mutation** Remove the scheduled `getGame` at `invitationExpiresAt`; (6) must fail. Put the raw `invitationUrl` in the link field's `value`; (2) must fail.
  - **Depends on** T007

---

## Phase 4: US2 — Guest joins (P1)

- [ ] T009 [US2] Strip the invitation fragment before render and join explicitly in `frontend/src/main.tsx`
  - **Covers** R07 (join path), R08, R09, R10, R15, R27 (join 410), R51, US2 · S2
  - **Read first** plan § *Boot and routing* · data-model § *Invitation secret* · `openapi.yaml` `joinGame` (L264, body `{invitationSecret (43 chars), displayName}`, 409 `invitation-unavailable`, 410)
  - **Files** `src/main.tsx`, `src/session/inviteSecret.ts`, `src/screens/JoinInvitation.tsx`, `e2e/invitation.spec.ts`
  - **How** Before `createRoot().render`: on `/join/:gameId` read `#invite=` into a module variable and `history.replaceState(null,'','/join/<id>')`. Join screen (mockup Join screen L2098 without its token field — R60): `getMeta`, captain name field, Join game button only; on activation send `joinGame` once (CSRF retry rule), clear the secret on settle. Success → `/games/<id>` + remember id. Outcomes (plan § *Outcome → screen*): `409 invitation-unavailable` and a lost answer → one "invitation unavailable" screen with Home (R15); `409 browser-game-limit` → the R07 message with Return to your game when an id is remembered, never retried; `410 game-expired` → Session unavailable "game expired" (R27).
  - **Proof** `invitation.spec.ts`: (1) an `addInitScript` MutationObserver on `#root` records `location.hash` at the first rendered node — it is empty; after navigating to `/` and `page.goBack()`, the URL still lacks the secret; (2) no `POST …/join` is sent before clicking Join; (3) two guests open the same link and both click Join: exactly one reaches placement, the other sees "invitation unavailable"; (4) host reaches placement live; (5) the secret string is absent from `localStorage`, `sessionStorage`, captured console messages and `e2e/.logs/default.log`; (6) a guest context that already hosts its own live game opens a fresh link and clicks Join: it shows the `browser-game-limit` message with Return to your game, and sends exactly one `POST …/join`; (7) short-lifetime stack 4174/8081 (`IDLETIMEOUTSECONDS=8`, `SWEEPINTERVALSECONDS=1`, `RESULTRETENTIONSECONDS=60`): a guest joins, both pages are hidden (visibility override, Conventions) until the game expires, then the guest reopens the same link and clicks Join — the answer is `410 game-expired` and the page shows Session unavailable "game expired", not "invitation unavailable".
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts`
  - **Mutation** Move `replaceState` into the Join component's effect; (1) must fail. Auto-join on load; (2) must fail. Map every join `409` to "invitation unavailable"; (6) must fail. Map join `410` to "invitation unavailable"; (7) must fail.
  - **Depends on** T008

---

## Phase 5: US3 — Fleet placement (P1)

- [ ] T010 [US3] Build the placement board, fleet tray and ship-action dialog in `frontend/src/screens/Placement.tsx`
  - **Covers** R01 (display counts), R02, R17, R19, R20, R31, R40 (cells), R59, US3 scenarios 1–3
  - **Read first** plan § *Placement, play, results* · README phase table (`PLACEMENT` actions) · `openapi.yaml` `PlaceShipCommand {type, shipId, anchor, orientation}`, `RemoveShipCommand`, `SimpleCommand` (`PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY`), `Board {grid: CellState[][], ships}`, `CellState` (L804-814), `Player.ready`
  - **Files** `src/components/{Board,FleetTray}.tsx` (+ `.css`), `src/screens/{Placement.tsx,ShipActionDialog.tsx}`, i18n keys, `e2e/placement.spec.ts`
  - **How** Mockup placement screen (L2172), board CSS (L999-1137 without the hatched zone — R60), ship-action dialog (L2520). `Board` (shared by T012/T013): `role="grid"`, grid tracks and labels from `ruleset.board` (columns A–Z then AA…, rows from 1); one roving tab stop, Arrow keys/Home/End move, Enter/Space activates; cells named `"<col><row>, <state>"`; state shown by symbol + text, not colour only; cells fill the width (`aspect-ratio: 1`). `FleetTray`: unplaced ships (empty `cells`) grouped by `length` with counts, plus a fleet-progress tile (placed ÷ total) — data-model § *Derived for display only*. Opponent pill from `opponent.ready`; the ruleset's touching rule as text under the board. Empty cell or tray tile → dialog: ship chips (by `shipTypeId`, names via i18n) × Horizontal/Vertical, Cancel/Apply; placed ship → move (pick new anchor), Rotate (same anchor, other orientation — one `PLACE_SHIP`), Remove. Random, Clear, Ready shown only when allowed; Ready opens a confirmation that the fleet locks; hint "fleet incomplete" or "waiting for opponent" from `you.ready`. Refusal 409 → `getGame` then worded reason.
  - **Proof** `placement.spec.ts` on Sea Battle: (1) place a ship by keyboard only (Arrow keys to the cell, Enter, dialog by Tab/Enter); board equals server answer; (2) move and rotate each send exactly one `PLACE_SHIP`; (3) a touching placement shows the `placement-touching` message and the board is unchanged; (4) random then clear; the tray counts equal the unplaced ships in `getGame`; (5) Ready asks for confirmation, then readiness is shown and no un-ready control exists; Ready absent while incomplete with its reason.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/placement.spec.ts`
  - **Mutation** Implement rotate as remove + place; (2) must fail. Make Arrow keys a no-op in `Board`; (1) must fail.
  - **Depends on** T009

- [ ] T011 [US3] Add mouse and touch drag placement in `frontend/src/screens/Placement.tsx`
  - **Covers** R18, R61, US3 scenario 4 · research D19 · decision B4
  - **Read first** plan § *Placement, play, results* (drag) · research D19 · mockup drag target style `.is-drag-target` (L2869-2887)
  - **Files** `src/screens/usePlacementDrag.ts`, `src/screens/Placement.tsx`, `src/components/FleetTray.tsx`, `e2e/placement.spec.ts` (two steps)
  - **How** One Pointer Events path with `setPointerCapture` for mouse, touch and pen; sources: a tray tile (first unplaced ship of that length, last chosen orientation, default horizontal) and a placed ship (keeps its orientation); `touch-action: none` on drag sources only; drop target highlighted with the mockup's style; on drop compute the anchor cell, clamp to `0..rows-length`/`0..columns-length` for the orientation, send one `PLACE_SHIP`; dialog path unchanged.
  - **Proof** (1) dragging a tray tile with `page.mouse` sends one `PLACE_SHIP` whose anchor is inside the board even when dropped past the edge; board equals the answer; (2) in a `hasTouch` context, a CDP touch drag (B4) of a placed ship sends one `PLACE_SHIP` and the board equals the answer.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/placement.spec.ts`
  - **Mutation** Remove the clamp; (1) must fail (anchor outside the board or a `placement-out-of-bounds` refusal). Replace the pointer handlers with `mousedown`/`mousemove`; (2) must fail.
  - **Depends on** T010

---

## Phase 6: US4 — Play and results (P1)

- [ ] T012 [US4] Build gameplay with live updates, announcements and lost-answer retry in `frontend/src/screens/Gameplay.tsx`
  - **Covers** R01 (display counts), R19, R20, R21, R24, R31, R59, US4 scenarios 1–3, US5 scenario 6 · S3 (lost answer) · research D13
  - **Read first** plan § *Session store*, § *Placement, play, results* · data-model § *Action slot* · `openapi.yaml` `FireCommand {type, target}`, `turn` (Side), `lastShot {by, target, result MISS|HIT|SUNK, sunkShipId?}`, `CommandRequest.commandId` ("server remembers at least the last 16")
  - **Files** `src/screens/Gameplay.tsx` (+ `.css`), `src/components/{Announcer.tsx,TurnPill.tsx,PlayerCard.tsx}` (+ `.css`), `src/session/gameStore.ts` (checking state + retry), `e2e/play.spec.ts`
  - **How** Mockup gameplay screen (L2213): turn pill from `turn`; two player cards (name, ships left = `shipsRemaining` ÷ Σ `ruleset.fleet[].count`, ship cells left = Σ `length × count` − `HIT`/`SUNK` cells on that board, each with the mockup's bar); both boards (Enemy waters / Your fleet tab control rendered for T018's narrow layout) with hit ×, miss •, sunk and revealed-water styles; footer with connection state and "Session expires in" (`expiresAt`, offset-corrected); no snapshot-version text (R60). Fire only on `UNKNOWN` opponent cells while `FIRE` allowed; target cell pending. Announcer (`aria-live="polite"`) once per new `lastShot` (`by`+`target`). Action without an answer → `checking` + Retry that resends the same body and `commandId`; an SSE snapshot does not settle the slot.
  - **Proof** `play.spec.ts`: (1) Sea Battle with the opponent's fleet placed at fixed anchors (Conventions): a hit keeps the turn; sinking a `ship-1` renders its surroundings as `REVEALED_WATER`, distinct from `MISS`; (2) Classic: a hit passes the turn; (3) the opponent's shot updates the player's board and announcement without reload; (4) action race (Classic, so any shot passes the turn): install `page.route('**/events', r => r.abort())` before the player's page opens the game, so its view comes from `getGame` only; when that view shows the player's turn, send one `FIRE` at an `UNKNOWN` cell through `page.request` (same session, own `commandId`, `X-XSRF-TOKEN` from the cookie) and, as soon as it answers, click a different `UNKNOWN` cell in the stale view — the page's `FIRE` gets `409 action-not-allowed`, the worded reason is shown, the board refreshes to `getGame`, and `getGame` shows exactly one new shot; (5) lost answer: `page.route('**/commands', r => r.fetch().then(() => r.abort()))` once, page shows checking; Retry's request body has the same `commandId`; the board shows the shot applied once (`getGame` via `page.request` shows one `MISS`/`HIT` at that cell); (6) after a hit, both player cards' ships-left and ship-cells-left values equal those derived from `getGame` via `page.request`.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/play.spec.ts`
  - **Mutation** Generate a new `commandId` on Retry; (5) must fail (second shot refused as `target-already-fired` or a different cell state).
  - **Depends on** T011

- [ ] T013 [US4] Build the results screen and play one full game per ruleset in `frontend/src/screens/Results.tsx`
  - **Covers** R30, R36, R59, US4 scenarios 4–5 · S1
  - **Read first** contracts/ui-surface.md § *Surfaces and widgets* (Results rows and the stat-card field table) · mockup results screen (L2261) · `openapi.yaml` `outcome {winner, reason}`, `statistics` (`match`, `you`, `opponent`; `PlayerStatistics` with optional `accuracy`; `DurationAggregate`; `FleetSummary`) L909-1009
  - **Files** `src/screens/Results.tsx` (+ `.css`), `src/components/StatCard.tsx` (+ `.css`), `e2e/helpers/playGame.ts`, `e2e/results.spec.ts`
  - **How** Victory/Defeat hero with reason; fleet-condition tiles from `statistics.you.fleet` (intact/damaged/sunk); stat cards Match time, Turns & accuracy, Turn timing with exactly the rows and fields of the ui-surface table (durations formatted, an absent optional value shows "—"); both revealed boards (`Board`, read-only); result-availability note from `expiresAt`; Create another game, Leave results. Leave results → `leaveGame` → Home, forget id. `playGame.ts`: drive both players through the UI (random fleet, ready, fire by reading each player's own UI turn) until FINISHED.
  - **Proof** `results.spec.ts` per ruleset: (1) both players reach results; winner/loser shown; (2) each stat-card row and fleet tile shown equals its `getGame` `statistics` field read via `page.request`; (3) Leave results returns Home and `getGame` then answers 404.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/results.spec.ts`
  - **Mutation** Compute accuracy locally as hits/shots rounded differently; (2) must fail.
  - **Depends on** T012

---

## Phase 7: US5 — Leaving, expiry, restart, connection loss (P2)

- [ ] T014 [US5] Implement leave, resign and the abandoned view in `frontend/src/screens/LeaveControl.tsx`
  - **Covers** R28, R29, R30, US5 scenarios 1–3
  - **Read first** plan § *Leaving, layouts, dialogs* · `openapi.yaml` `leaveGame` (L542, 204; 404 after own leave = success) · README phase table
  - **Files** `src/screens/LeaveControl.tsx`, `src/screens/Abandoned.tsx`, `src/screens/SettingsDialog.tsx` (Resign match), `e2e/leaving.spec.ts`
  - **How** WAITING/PLACEMENT: Leave game → confirm → `leaveGame` → Home after 204 or 404; cancel keeps the view. PLAYING: Settings shows Resign match while `RESIGN` is allowed (the mockup's in-play leave path); its dialog says leaving means resigning → `RESIGN` command. ABANDONED: "Game abandoned", no boards, no statistics, Leave results.
  - **Proof** `leaving.spec.ts`: (1) cancel keeps placement; confirm → Home; (2) remaining player sees abandoned without reload, no boards; (3) Settings → Resign match in play → opponent's results show a win by `RESIGNATION`; Resign match is absent from Settings outside play; (4) lost leave answer: `page.route('**/leave', r => r.fetch().then(() => r.abort()))` once; the page's retry gets 404 and lands Home, not Session unavailable.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/leaving.spec.ts`
  - **Mutation** Treat 404 after own leave as Session unavailable; (4) must fail.
  - **Depends on** T013

- [ ] T015 [US5] Recover the live stream and honour rate limits in `frontend/src/gateway/eventStream.ts`
  - **Covers** R22, R24, R25, R26, R43 (actions disabled offline), US5 scenario 5 · S3 · fact F5 (stream also ends without `closed` at `stream-max-lifetime-seconds` and on shutdown)
  - **Read first** plan § *Live stream* · data-model § *Connection* · `openapi.yaml` `StreamClosed` (L1324, `REPLACED`, `GAME_UNAVAILABLE`), getGame polling fallback (L219-220), 429 `retryAfterSeconds`
  - **Files** `src/gateway/eventStream.ts`, `src/session/gameStore.ts`, `src/components/ConnectionStatus.tsx`, `e2e/recovery.spec.ts`
  - **How** `error` with `readyState===CLOSED` or tab visible → `getGame`, reopen on success; failures without a problem retry after 1, 2, 4, then every 8 s; a stream that fails again after a successful `getGame` reopens on the same backoff, reset by its first received event; 429/503 wait `retryAfterSeconds`; 401/404/410 → outcome table. `REPLACED` → "open in another tab", no reopen. `GAME_UNAVAILABLE` → one `getGame`, no reopen. `offline` only from the `offline` event / `navigator.onLine === false` (close stream, disable game actions); `online` → as the CLOSED path; a network error while online stays `reconnecting` (data-model § *Connection*).
  - **Proof** `recovery.spec.ts`: (1) in gameplay on the player's turn, `context.setOffline(true)` → status shows offline and every opponent cell's fire control and the header Refresh are disabled; then `false` → reconnecting then connected, fire controls enabled again; board equals `getGame`; no command was resent (request log); (2) opening the game in a second tab of the same context → first tab shows "open in another tab" and opens no new stream; (3) tab return: override `visibilityState` to hidden then visible — one `getGame` is sent and the stream reopens; (4) lowered-limit stack 4175/8082 (`RATELIMIT_READGAMEPERMINUTE=2`): the 429 shows a wait hint and no `getGame` is sent before `retryAfterSeconds` (test timeout ≥ 90 s; `Retry-After` can reach 60 s); (5) same stack with `RATELIMIT_STREAMOPENPERMINUTE=2` (reads unlimited): after the stream limit is hit, at most 4 `/events` requests are sent in the next 10 s (reopen backoff 1, 2, 4, 8 s).
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/recovery.spec.ts`
  - **Mutation** Reopen the stream after `REPLACED`; (2) must fail. Retry 429 immediately; (4) must fail. Remove the reopen backoff; (5) must fail. Keep fire controls enabled while `offline`; (1) must fail.
  - **Depends on** T014

- [ ] T016 [US5] Prove expiry, presence extension and restart loss on dedicated stacks in `frontend/e2e/lifetime.spec.ts`
  - **Covers** R16, R26 (`GAME_UNAVAILABLE`), R27, R50, US5 scenario 4 · S3
  - **Read first** plan § *Outcome → screen* · research D12 · `openapi.yaml` 410 `game-expired` → 404 after forget (L665-676)
  - **Files** `src/screens/SessionUnavailable.tsx` (final wording, New game), `src/session/presence.ts` (expiry display), `e2e/lifetime.spec.ts`
  - **How** Short-lifetime stack 4174/8081: `IDLETIMEOUTSECONDS=8`, `SWEEPINTERVALSECONDS=1`, `RESULTRETENTIONSECONDS=4`, `PRESENCEINTERVALSECONDS=2`. Restart stack 4176/8083 via `restartBackend()`. Session unavailable clears game presentation state and the remembered id.
  - **Proof** (1) a waiting game whose page is hidden (visibility override, Conventions) → on return "game expired" (410); after retention, reload → "no longer available" (404); (2) with the page visible, presence keeps a waiting game past the idle timeout and the shown expiry moves forward; (3) backend restart mid-placement → "no longer available … server restarted" with New game; (4) Home no longer offers Return to your game; (5) when the server forgets the game, its stream's `closed` `GAME_UNAVAILABLE` causes exactly one `getGame` and no new `/events` request (request log).
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/lifetime.spec.ts`
  - **Mutation** Disable the presence timer; (2) must fail.
  - **Depends on** T015

---

## Phase 8: US6 — Settings, feedback and inclusive use (P2)

- [ ] T017 [US6] Add sound and haptics feedback in `frontend/src/feedback/`
  - **Covers** R32, R33, US6 scenarios 1–2 · S5 · research D8
  - **Read first** master `frontend/src/audio/audioRecipes.ts`, `AudioFeedback.ts` (`git show master:…`; no imports to strip)
  - **Files** `src/feedback/{audioRecipes.ts,audioFeedback.ts,haptics.ts,useShotFeedback.ts}`, `src/screens/Gameplay.tsx` (hook), `tests/feedback.test.ts`, `e2e/settings.spec.ts` (mid-game step)
  - **How** Port both audio files; map `lastShot.result` → recipe; haptics `navigator.vibrate?.(pattern)` in try/catch; both only when the preference is on, once per new `lastShot`, never awaited.
  - **Proof** Unit: (1) `AudioContext` constructor throwing, `resume()` rejecting and `vibrate` absent/throwing all resolve without error. `settings.spec.ts`: (2) toggling sound and haptics on/off mid-game leaves the next shot's result and turn identical to `getGame`.
  - **Verify** `cd frontend && npm test -- tests/feedback.test.ts && npm run build && npm run e2e -- e2e/settings.spec.ts`
  - **Mutation** Remove the try/catch around `vibrate`; (1) must fail.
  - **Depends on** T016

- [ ] T018 [US6] Make the three layouts, keyboard, touch and screen-reader use pass and capture screens for mockup review in `frontend/src/styles/layout.css`
  - **Covers** R31, R39, R40, R41, R58, R59, R60, R61, US6 scenarios 3–6 · S4, S8 · research D18 · decisions B4, B5
  - **Read first** plan § *Leaving, layouts, dialogs*, § *Visual design* · research D18 · spec R39–R41, R58–R61 · mockup container query (L1728-1886), phone-landscape rules (L1888-1948, translated from `data-device` to the media query), `@media (max-height:31.25rem)` (L1950)
  - **Files** `src/styles/layout.css`, `src/screens/Gameplay.tsx` (tablist wiring), `src/components/Board.tsx` (compact size), `e2e/accessibility.spec.ts`
  - **How** CSS only: `@container app (max-width: 47.5rem)` shows the `role="tablist"` Enemy waters / Your fleet switch (Arrow keys, `aria-selected`) and one board, turn pill, player cards and action status outside the panels, single-column grids, fleet tray scrolls within itself; `@media (max-height: 31.25rem) and (orientation: landscape)` shows two compact boards side by side, no tabs, non-sticky header, and overrides the narrow rule; wide: both boards side by side. No fixed widths; no JavaScript layout detection.
  - **Proof** `accessibility.spec.ts` at 1280×800, 390×844, 844×390: (1) a full journey (create → join → random → ready → several shots → resign) by keyboard only; (2) `document.documentElement.scrollWidth <= innerWidth` on Home, Create, Join, Waiting, Placement, Gameplay, Results, Session unavailable; (3) `@axe-core/playwright` serious/critical = 0 on Home, Create, Join, Waiting, Placement, Gameplay, Results, Abandoned, Session unavailable; (4) at 390×844 the tablist is visible, operable by arrows, and exactly one board is visible; at 844×390 and 1280×800 the tablist is hidden and both boards are visible; (5) `emulateMedia({forcedColors:'active', reducedMotion:'reduce'})` keeps cell states readable (text present) and `document.getAnimations().length === 0` after a shot; (6) dialogs rules, settings, QR, ready confirmation, replace-invitation confirmation, leave confirmation, resign confirmation, ship action, checking: each labelled, focus inside, Escape closes where dismissible (not checking while pending), focus returns to its opener; (7) the same journey in a `devices['Pixel 7']` context (B4; `matchMedia('(hover: none)').matches` is asserted) using only `tap()` and one CDP touch drag reaches results, and one tap on the header Refresh during play sends exactly one `GET /api/v1/games/<id>`; (8) screenshots at the three sizes in dark and light saved to `test-results/visual/` for these 19 surfaces — Home, Create, Join, Waiting (with the "Game created" toast), QR dialog, Placement, ship-action dialog, ready confirmation, Gameplay, checking dialog (lost answer via `route.fetch()` + `route.abort()`), refusal notice (a `placement-touching` refusal), Results, Abandoned, Session unavailable, rules dialog, settings dialog, replace-invitation confirmation, leave confirmation, resign confirmation — 114 files present. The owner compares them with the mockup; the outcome is recorded per B5.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/accessibility.spec.ts`
  - **Mutation** Give the board a fixed `min-width: 900px`; (2) must fail at 390×844. Delete the `@container app` rule; (4) must fail at 390×844. Show the header Refresh control only on `:hover`; (7) must fail.
  - **Depends on** T017

---

## Phase 9: US7 — Installable shell offline (P3)

- [ ] T019 [US7] Make the app installable with an offline shell and update prompt in `frontend/src/pwa/`
  - **Covers** R42, R43, R44, US7 · S6 · research D9
  - **Read first** solitaire `src/pwa/{registerPwa.ts,pwaGateway.ts,deferredGateway.ts,installGateway.ts}` and its `VitePWA` block (`registerType:'prompt'`, `injectRegister:false`, `manifest:false`, `navigateFallback:'index.html'`, `cleanupOutdatedCaches:true`)
  - **Files** `src/pwa/*`, `frontend/vite.config.ts` (VitePWA + `navigateFallbackDenylist: [/^\/api\//]`, no `runtimeCaching`), `public/{manifest.webmanifest,favicon.svg,icons/*}` (Battleship-branded, `start_url: '/'`), `src/components/{OfflineBanner.tsx,UpdatePrompt.tsx}`, `e2e/offline.spec.ts`
  - **How** Copy the four gateway files; register on load; offline banner from `online`/`offline`; game controls disabled while offline, nothing queued; update prompt calls `updateSW()` only on consent.
  - **Proof** `offline.spec.ts` (preview stack): (1) after one load and SW `ready`, `context.setOffline(true)` + reload renders the shell with "network required" within 3 s; (2) no request to `/api` is attempted while offline (request log); (3) after creating a game and reading it (`createGame`, `getGame`, event stream) online, `caches` entries contain no `/api` URL; (4) after rebuilding into the served `dist/` with `VITE_BUILD_ID=next npm run build` (the id is rendered in Settings, so the precache manifest changes), the page shows the update prompt and does not reload until accepted.
  - **Verify** `cd frontend && npm run build && npm run e2e -- e2e/offline.spec.ts`
  - **Mutation** Remove `navigateFallbackDenylist` and add an `/api` `runtimeCaching` rule; (3) must fail.
  - **Depends on** T018 (journeys share fixed ports and `dist/`, so no parallel run)

---

## Phase 10: US8 — One gate and local operation (P2)

- [ ] T020 [US8] Create the single gate `scripts/verify.sh`, the local guide and the durable-doc updates
  - **Covers** R45, R49, R52, US8 · S7
  - **Read first** plan § *Gate* · AGENTS.md § *Definition of Done*, § *Where this stands* · constitution VII
  - **Files** `scripts/verify.sh`, `frontend/vitest.config.ts` (remove `passWithNoTests`, set in T004 for the empty app), `docs/local-operation.md`, `AGENTS.md` (Gate + Definition of Done: `./scripts/verify.sh`), memory `frontend-absorbs-integration.md` (gate now exists)
  - **How** `set -euo pipefail`; stages printed `[n/5] <name>`: 0 `install.sh`; 1 `contracts npm run check`; 2 `backend ./mvnw -q verify && ./mvnw spotless:check`; 3 `frontend npm run check`; 4 `frontend npm run e2e` (all journeys). Each stage runs under a timeout (`VERIFY_STAGE_TIMEOUT`, default 1800 s, via a Node or `perl -e alarm` wrapper — macOS has no `timeout`); a non-zero exit, a timeout, or a stage that reports zero tests run (Playwright `--pass-with-no-tests` not set, Vitest list empty) → exit non-zero naming it; `VERIFY_FAIL_STAGE=<n>` makes stage n run its real command and then a failing check inside the same stage subshell, so the failure travels through the same `run_stage` exit-code path as a real one (for the S7 proof). Guide: commands, ports (5173, 5174, 4173–4177, 8080–8085), `BATTLESHIP_*` overrides, lifetime limits (15 min idle, 2 h, 5 min retention, restart loss), common failures (port in use, wrong runtime, backend down, expired invite, restart, `127.0.0.1` vs `localhost`).
  - **Proof** (1) `./scripts/verify.sh` exits 0 with all five stages listed; (2) for n in 1–4, `VERIFY_FAIL_STAGE=n ./scripts/verify.sh` exits non-zero and names stage n; `VERIFY_STAGE_TIMEOUT=1 ./scripts/verify.sh` exits non-zero naming a timeout; (3) `python3 scripts/sync-agent-files.py --check` passes.
  - **Verify** `./scripts/verify.sh && for n in 1 2 3 4; do ! VERIFY_FAIL_STAGE=$n ./scripts/verify.sh || exit 1; done`
  - **Mutation** Make `run_stage` ignore a non-zero exit when n=4; proof (2) for n=4 must fail.
  - **Depends on** T019

---

## Dependencies and execution order

```text
T001 ─ T002 ─ T003 ─┐
T004 ───────────────┴─ T005 ─ T006 ─ T007 ─ T008 ─ T009 ─ T010 ─ T011 ─ T012 ─ T013 ─ T014 ─ T015 ─ T016 ─ T017 ─ T018 ─ T019 ─ T020
```

- Parallel: only T001 ∥ T004 (contracts/backend vs `frontend/`). Journeys share fixed ports and `dist/`, so
  frontend tasks never run concurrently.
- Story tasks are serial: all screens share `session/gameStore.ts`, the router and the journeys' helpers.
- Example: session A runs T001→T002→T003 while session B runs T004; T005 starts when both finish.

## Implementation strategy

1. **MVP** = Phases 1–3 (T001–T008): a host can create and share against the real backend.
2. Then T009–T013 complete the P1 game loop (S1). Stop and run all journeys so far.
3. T014–T016 (P2 leaving, recovery, lifetime), T017–T018 (P2 inclusive use), T019 (P3), T020 (gate).

## Coverage map

| Requirement | Task(s) | | Requirement | Task(s) |
|---|---|---|---|---|
| R01 R02 R04 | T007, then each screen task T008–T014 | | R30 | T013, T014 |
| R03 R05 R13 R22 R23 R56 | T007 (R22 429: T015) | | R31 | T010, T012, T018 |
| R06 | T007 | | R32 | T017 |
| R07 | T001, T007, T009, T016 | | R33 R34 R35 | T006 (R33 sound/haptics: T017) |
| R08 R09 R10 R15 | T009 | | R36 | T013 |
| R11 R12 R14 | T008 | | R37 R38 | T006, T007, T014 (Resign match) |
| R16 | T008, T016 | | R39 R40 R41 | T006 (primitives, reduced motion, forced colours), T010 (board keyboard), T018 |
| R17 | T010 | | R42 R43 R44 | T019 (R43 actions disabled: T015) |
| R18 | T011 | | R45 | T004, T020 |
| R19 | T007, T012 | | R46 R47 R48 | T005 incl. dev/prod CLI (R47 no dev address: T004) |
| R20 R21 | T010, T012 | | R49 | T020 |
| R24 | T008, T012, T015 | | R50 | T005, T015, T016 |
| R25 R26 | T015 (R26 `GAME_UNAVAILABLE`: T016) | | R51 | T005, T009 |
| R27 | T007, T009 (join 410), T016 | | R52 | T020 |
| R28 R29 | T014 | | R53 R54 R55 | journeys in T005–T019 · units in T005–T008, T017 · B1 · `route.fulfill` ban (T005) |
| R58 | T006, T018 | | R57 | T001, T002, T003, T008 |
| R59 | built in T006–T014; asserted in T008(7), T010(4), T012(6), T013(2); every other widget by T018(8) screenshots | | R60 | T006 (Home), T008, T009, T010, T012 (exclusions) |
| R61 | T006 (controls), T011, T018 | | | |

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

## Gate

Until T020: per-task **Verify** commands, plus `cd backend && ./mvnw -q verify && ./mvnw spotless:check` and
`cd contracts && npm run check` whenever those trees change. After T020: `./scripts/verify.sh`.
