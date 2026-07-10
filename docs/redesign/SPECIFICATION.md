# Battleship — Redesign & Modernization Specification

**Status:** v2 · **Date:** 2026-07-10 · **Owner:** Oleksandr (sanyokkua)

This is the **mini-specification**: the authoritative description of *what* changes, *what does not*, how the new UI looks and behaves, which dependencies to update/install/remove, how to structure and style the frontend, how it connects to the backend, and the acceptance criteria. The companion documents are:

- `MOCKUP.html` — canonical visual reference (open in a browser; use the screen chips and 📱/🖥️ toggle).
- `IMPLEMENTATION_PLAN.md` — the phased execution plan (references this spec by section).
- `TESTING_PLAN.md` — the testing strategy for all phases.
- `PHASE_BOOTSTRAP_PROMPT.md` — the reusable plan-mode prompt used to start each phase.

> **Rule:** If reality disagrees with this document, update this document. For *visuals*, `MOCKUP.html` wins. For *data/behavior*, this spec and the existing backend API win.

---

## 1. Goals

1. Replace the frontend UI/UX with the **Modern Naval** design (`MOCKUP.html`).
2. Modernize the backend: **Java 25**, **Spring Boot 4.1**, current dependencies.
3. Close backend test gaps and fix any bugs discovered.
4. Re-tool and re-architect the frontend: **Vite + React 19 + TypeScript 5**, an **Adapter (port)** layer for all backend access, and **Widgets** that consume the adapter (never call the network directly).
5. Add a complete automated test suite (backend + frontend unit/UI + Playwright e2e).
6. Package the app with **Docker/Podman** following best practices.
7. Finalize documentation.

---

## 2. Scope

### 2.1 In scope
- Backend version/dependency upgrade and Java 25 migration (behavior-preserving).
- Backend test-gap analysis, new tests, and bug fixes uncovered by those tests.
- Frontend cleanup (remove dead code + deprecated deps), Vite setup, new dependencies.
- Frontend rebuild: Adapter layer + Widget layer + design system, matching `MOCKUP.html`.
- Frontend automated tests (unit, component/UI with a mock adapter, Playwright e2e mocked + live).
- Dockerfile + docker-compose, verified on Docker and Podman.
- Documentation updates (`README.md`, this folder).

### 2.2 Out of scope (do NOT implement)
- **No change to the backend REST API surface** — paths, verbs, request bodies, response DTOs, and status codes remain identical (§6).
- **No change to game rules/engine logic.** The engine may only be *modified* if Phase 2 testing reveals a genuine bug; such a change must be documented and covered by a regression test.
- **No new gameplay features**: no WebSockets/SSE, no password protection, no share-links, no ship auto-place, no multi-instance/scaling, no database. Storage stays **in-memory, single instance**.
- **No change to the polling model.** The frontend keeps polling the existing endpoints (redesigned UI, same mechanism, encapsulated behind the adapter).

### 2.3 Confirmed decisions
| Topic | Decision | Rationale |
|---|---|---|
| Frontend build tool | **Vite** (replaces CRA/`react-scripts`) | CRA is officially deprecated; cannot cleanly run React 19. |
| Styling | **Custom CSS design system** (CSS variables) | Mockup is bespoke; remove Bootstrap stack for an exact match and fewer deps. |
| Components | **Function components + hooks** | Aligns with React 19; UI is rebuilt anyway. |
| Backend access | **Adapter (port/interface) + implementations** | One "expectation" of the backend; widgets are transport-agnostic and testable with a mock adapter. |
| Test runner (FE) | **Vitest + React Testing Library**; **Playwright** for e2e | Native Vite integration; mock-adapter-based UI tests; Playwright for browser e2e. |
| Runtime image | **eclipse-temurin:25-jre** (multi-stage build) | Official, current, small runtime. |

---

## 3. Current-state inventory

### 3.1 Backend (Spring Boot MVC + REST)
- `spring-boot-starter-parent` **3.3.5**, `java.version` **17**.
- Starters: `web`, `thymeleaf`, `test`; `springdoc-openapi-ui` **1.8.0** (legacy 1.x — mismatched with Boot 3+), `commons-lang3` **3.12.0**, Lombok, devtools.
- Build: `spring-boot-maven-plugin`; `maven-resources-plugin` copies `frontend/build` → `target/classes/static` and `index.html` → `target/classes/templates`; `frontend-maven-plugin` **1.12.1** bundling **Node v16.17.0** and running `npm run build`.
- Structure: `logic/engine/*` (pure game engine + models/records/enums/utils), `logic/api/*` (`GameControllerApi(+Impl)`, validation, id-gen, typed exceptions), `logic/persistence/*` (`Persistence`, `InMemoryPersistence`), `web/*` (3 REST controllers, `IndexController`, DTOs, exception handlers, config).
- Tests (13 classes, ~2.4k lines): engine utils, editions config, persistence, `GameControllerApiImpl`, `GameImpl`, `FieldManagementImpl`, `IdGenerator`, `ValidationUtils`, `ControllerUtils`, `IndexController`, plus `BattleshipApplicationTests`.
- **Test gaps (for Phase 2):** no dedicated tests for the three REST controllers (`GameSessionCommonRestController`, `GameplayRestController`, `PreparationRestController`), the exception handlers (`InternalExceptionHandler`, `ValidationExceptionHandler`), or web DTO mapping edge cases. Verify how much `BattleshipApplicationTests` already covers via MockMvc before writing new tests.

### 3.2 Frontend (React SPA)
- CRA `react-scripts` **5.0.1**; React **18.2**; TypeScript **4.9** (target `es5`).
- UI libs: `bootstrap` 5.2.2, `react-bootstrap` 2.6.0, `react-router-bootstrap` 0.26.2, `react-router-dom` 6.4.3.
- Data: `axios` 1.1.3, `axios-retry` 3.3.1, `copy-to-clipboard` 3.3.3, `prop-types`.
- **Class components** throughout. Polling via `setInterval` (Preparation 3s, Gameplay 5s). API in `services/BackendRequestService.ts`; `localStorage` in `services/GameBrowserStorage.ts`.
- Pages: `HomePage`, `NewGamePage`, `JoinGamePage`, `WaitForPlayersPage`, `PreparationPage`, `GameplayPage`, `FinishPage`. Elements: forms, preparation (`ShipsList`, `ButtonShip`, `Status`, `PrepareField`, `PrepCell` with a **direction modal**), gameplay (`GameplayField`, `Cell`), `ApplicationNavigationBar`.

---

## 4. Target architecture

### 4.1 Backend
- `spring-boot-starter-parent` **4.1.0** (Spring Framework 7), `java.version` **25**.
- Replace `springdoc-openapi-ui:1.8.0` → `springdoc-openapi-starter-webmvc-ui:2.8.x`.
- Bump `commons-lang3` to latest 3.x; Lombok managed by Boot (verify Java-25-capable, ≥1.18.42).
- `frontend-maven-plugin` latest, bundled **Node 24 LTS**.
- Keep Thymeleaf serving of the built `index.html`; keep `IndexController` SPA forwarding.
- Package as a runnable JAR *and* a Docker image (§9).

### 4.2 Frontend — layered design
```
┌──────────────────────────────────────────────┐
│  Pages (routed screens)                        │  ← compose widgets; own screen state
├──────────────────────────────────────────────┤
│  Widgets (Board, ShipTray, PlayerCard, …)      │  ← presentational; get data via hooks/props
├──────────────────────────────────────────────┤
│  Hooks (useGameAdapter, usePolling, …)         │  ← bridge widgets ↔ adapter
├──────────────────────────────────────────────┤
│  Adapter port  GameAdapter (interface)         │  ← the single "expectation" of the backend
│    ├─ HttpGameAdapter   (axios → REST §6)      │
│    └─ MockGameAdapter   (in-memory, for tests) │
├──────────────────────────────────────────────┤
│  Design system (tokens + CSS + primitives)     │
└──────────────────────────────────────────────┘
```
- **Widgets never import axios or call `fetch`.** They depend only on the `GameAdapter` interface (via a hook/context) and on props. This makes every widget testable against `MockGameAdapter` and keeps the backend swappable.
- **State/effects** live in pages/hooks using `useState`/`useReducer`/`useEffect`. Polling is a single reusable hook with correct cleanup (StrictMode-safe).
- **Routing**: `react-router-dom` v7/8 (pin latest v8). Same routes as today (§7.4). Replace `LinkContainer` with `NavLink`/`Link`.
- **API base** stays relative (`/api/v2/game`) so same-origin packaging needs no env config; Vite dev proxy handles local dev.

---

## 5. Dependencies — update / install / remove

### 5.1 Backend (pom.xml)
| Action | Artifact | From → To |
|---|---|---|
| Update | `spring-boot-starter-parent` | 3.3.5 → **4.1.0** |
| Update | `java.version` | 17 → **25** |
| Replace | `springdoc-openapi-ui` 1.8.0 → **`springdoc-openapi-starter-webmvc-ui` 2.8.x** |
| Update | `commons-lang3` | 3.12.0 → latest 3.x |
| Update | `frontend-maven-plugin` | 1.12.1 → latest; Node v16 → **Node 24 LTS** |
| Keep | `spring-boot-starter-web`, `-thymeleaf`, `-test`, `lombok`, `devtools` | (versions managed by Boot 4.1) |
| Add (later, Phase 2 if needed) | test helpers (e.g. `spring-boot-starter-test` already provides JUnit 5, Mockito, MockMvc, AssertJ) | — |

### 5.2 Frontend (package.json)
| Action | Package | Notes |
|---|---|---|
| Remove | `react-scripts` | replaced by Vite |
| Remove | `bootstrap`, `react-bootstrap`, `react-router-bootstrap` | replaced by custom CSS |
| Remove | `prop-types` | TS types cover this |
| Update | `react`, `react-dom` | 18 → **19.2.x** |
| Update | `typescript` | 4.9 → **5.x** (target ES2020+) |
| Update | `react-router-dom` | 6 → **8.x** |
| Update | `axios`, `axios-retry`, `copy-to-clipboard` | latest (copy may be replaced by native Clipboard API) |
| Install | `vite`, `@vitejs/plugin-react` | build tool |
| Install | `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom` | unit/component tests |
| Install | `@playwright/test` | e2e (mocked + live) |
| Install (dev) | `@types/react`, `@types/react-dom`, eslint + config | typings/lint |

---

## 6. Frozen backend API (reference — MUST NOT change)

Base path `/api/v2/game`. The frontend adapter maps 1:1 to these.

| Purpose | Method & path | Body |
|---|---|---|
| List editions | `GET /editions` | — |
| Create session | `POST /sessions` | `{gameEdition}` |
| Create player | `POST /sessions/{sessionId}/players` | `{playerName}` |
| Current stage | `GET /sessions/{sessionId}/state` | — |
| Change timestamp (poll) | `GET /sessions/{sessionId}/changesTime` | — |
| Preparation state | `GET /sessions/{sessionId}/players/{playerId}/preparationState` | — |
| Add ship | `PUT /sessions/{sessionId}/players/{playerId}/ships/{shipId}` | `{row,col,direction}` |
| Remove ship | `DELETE /sessions/{sessionId}/players/{playerId}/ships` | `{row,col}` |
| Player ready | `POST /sessions/{sessionId}/players/{playerId}/start` | — |
| Opponent info | `GET /sessions/{sessionId}/players/{playerId}/opponent` | — |
| Gameplay state | `GET /sessions/{sessionId}/players/{playerId}/state` | — |
| Make shot | `POST /sessions/{sessionId}/players/{playerId}/field/shot` | `{row,col}` |

**Stages:** `INITIALIZED`, `WAITING_FOR_PLAYERS`, `PREPARATION`, `IN_GAME`, `FINISHED`.
**Key DTOs (unchanged):** `CellDto{row,col,ship,hasShot,isAvailable}`, `ShipDto{shipId,shipSize}`, `ResponseGameplayStateDto{playerName,isPlayerActive,isPlayerWinner,playerNumberOfAliveCells,playerNumberOfAliveShips,playerField,opponentName,isOpponentReady,opponentNumberOfAliveCells,opponentNumberOfAliveShips,opponentField,hasWinner,winnerPlayerName}`, `ResponsePreparationState{ships,field}`, `ResponseOpponentInformationDto{playerName,ready}`.

---

## 7. Frontend architecture — detail

### 7.1 Adapter port
Define `GameAdapter` (TypeScript interface) with one method per API operation (§6), returning typed DTOs (reuse `logic/ApplicationTypes.ts`). Example shape:
```ts
export interface GameAdapter {
  getEditions(): Promise<string[]>;
  createSession(edition: string): Promise<string>;                 // sessionId
  createPlayer(sessionId: string, name: string): Promise<PlayerDto>;
  getStage(sessionId: string): Promise<GameStage>;
  getChangeTime(sessionId: string): Promise<string>;
  getPreparationState(sessionId: string, playerId: string): Promise<PreparationState>;
  addShip(sessionId: string, playerId: string, shipId: string, at: Coordinate, dir: ShipDirection): Promise<void>;
  removeShip(sessionId: string, playerId: string, at: Coordinate): Promise<void>;
  getOpponent(sessionId: string, playerId: string): Promise<OpponentInfo>;
  setReady(sessionId: string, playerId: string): Promise<boolean>;
  getGameState(sessionId: string, playerId: string): Promise<GameplayState>;
  shoot(sessionId: string, playerId: string, at: Coordinate): Promise<ShotResult>;
}
```
- `HttpGameAdapter` wraps the current `BackendRequestService` calls (same endpoints, same relative base, `axios-retry`).
- `MockGameAdapter` implements the same interface with deterministic in-memory state for tests, UI development, and Playwright mocked runs.
- Provide the active adapter through a React **context** + `useGameAdapter()` hook. Production wires `HttpGameAdapter`; tests/Storybook wire `MockGameAdapter`.

### 7.2 Hooks
- `useGameAdapter()` — returns the injected adapter.
- `usePolling(fn, ms, enabled)` — interval with cleanup; used by Preparation (3s) and Gameplay (5s). Must be StrictMode double-invoke safe.
- Screen-specific hooks may wrap adapter calls + local state (e.g. `usePreparation`, `useGameplay`).

### 7.3 Widgets (presentational, adapter-agnostic)
`AppBar`, `Button`, `Input`, `Select`, `Field`, `Card`, `Pill/Badge`, `StepTracker`, `Board`, `BoardCell`, `Legend`, `ShipTray`/`ShipItem`, `DirectionToggle`, `PlayerCard`, `TurnBanner`, `BoardTabs`, `LoadingBar`, `Toast`. Widgets receive data + callbacks via props; they do not fetch.

### 7.4 Routes (unchanged)
`/` Home · `/new` New Game · `/join` Join · `/game/wait` Wait · `/game/preparation` Preparation · `/game/gameplay` Gameplay · `/game/results` Results. Preserve `App`'s stage-driven guarding on `sessionId`/`playerDto` and stage transitions.

---

## 8. UI/UX specification

> Visual source of truth: `MOCKUP.html`. This section defines the rules an implementer must follow; screen layouts are shown in the mockup.

### 8.1 Design tokens
Port these CSS variables as the base (values from the mockup):
- **Brand:** `--navy #0b2545`, `--navy2 #13315c`, `--sea #1b4b7a`, `--steel #3a6ea5`, `--teal #12a8b8`, `--teal-dark #0b8494`, `--gold #f4a534`.
- **Surfaces/ink:** `--bg #eef3f9`, `--surface #ffffff`, `--surface-2 #f5f8fc`, `--ink #0e2136`, `--ink-soft #5a6b80`, `--line #dbe4f0`.
- **Board:** `--water #e7f0fb`, `--water-line #c8dcf3`, `--ship #12507e`, `--ship-soft #d9e8f6`, `--hit #e63946`, `--miss #9aa8b8`, `--sunk #b3242f`, `--ok #2a9d8f`.
- **Shape:** `--radius 16px`, `--radius-sm 10px`, `--shadow 0 8px 30px rgba(11,37,69,.10)`.
- **Type:** system stack; title 24–30px, body 14.5–16px, labels 13.5px.

### 8.2 Cell state colors (semantic, used everywhere)
| State | Appearance |
|---|---|
| Water (untouched) | `--water` fill, `--water-line` border |
| Your ship | navy/sea gradient (`--ship`→`--sea`) |
| Valid drop (prep) | light green fill, dashed `--ok` border |
| Blocked (prep) | muted grey, not interactive |
| Hit | red (`--hit`) with ✕ |
| Miss | white with small grey dot (`--miss`) |
| Sunk | dark red (`--sunk`) |

### 8.3 Screen-by-screen (bind only to real DTO fields)
1. **Home** — hero + primary "New Game", secondary "Join Game".
2. **New Game** — edition `Select` (from `GET /editions`), name `Input`, submit → create session + player; keep name validation.
3. **Join Game** — name + Game ID inputs; keep 36-char UUID validation; submit → join.
4. **Wait** — greeting (`Hello, {name}!`), `StepTracker` (Create→Waiting→Prepare→Battle), Game ID box + Copy button (raw session UUID), animated "waiting" indicator; poll opponent every 3s → go to Preparation when both present.
5. **Loading** — top `LoadingBar` + centered anchor; reused wherever a fetch is in flight (replaces old spinner/progress).
6. **Preparation** — `ShipTray` (named ships + sizes), **inline `DirectionToggle` (Horizontal/Vertical)** replacing the modal, `Board` with valid-drop highlight + tap-to-place / tap-to-remove, opponent status `Pill`, "Ready to go!" button, error `Toast`. Calls: `getPreparationState`, `addShip`, `removeShip`, `setReady`.
7. **Gameplay** — two `PlayerCard`s (cells health from `*NumberOfAliveCells` 0–100, ships from `*NumberOfAliveShips`), `TurnBanner` from `isPlayerActive`, **adaptive boards**: Target = opponent field (tap to `shoot`), Fleet = own field (read-only). Keep 5s poll; redirect to Results when `hasWinner`.
8. **Results** — win/lose hero from `isPlayerWinner`/`winnerPlayerName`, both boards read-only (hits/misses/ships), "Return to main menu". Show only API-backed stats.

### 8.4 Adaptivity / responsive rules
- **Breakpoint:** mobile ≤ **640px**, desktop ≥ **641px** (demoed in the mockup toggle).
- **Mobile:** single column; `AppBar` collapses to a hamburger; gameplay/results boards use a **tab switch** (Target ↔ Fleet) instead of stacking; forms full-width; touch targets ≥ **44px**.
- **Desktop:** multi-column; inline `AppBar` nav; gameplay/results boards **side-by-side**; preparation shows tray + board in two columns.
- **Board sizing:** the `Board` is a square CSS grid that **fills its container width** (`width:100%`, `aspect-ratio:1/1`) with coordinate rails (A–J columns, 1–10 rows).
- Content max-widths keep forms readable on large screens (~440–460px form column).

### 8.5 Excluded mockup elements (NOT API-backed — do not build)
- **"Auto-place remaining"** button (Preparation) — no endpoint. Omit.
- **Results "Hits" and "Time"** stats — not tracked by the API. Omit. "Ships sunk" only if derivable (edition total − `numberOfAliveShips`).
- Any share-link / password / realtime affordance — deferred, out of scope.

### 8.6 Accessibility
- Keyboard operable; visible focus rings on all interactive elements.
- `aria-label`s on icon-only controls (hamburger, copy) and on board cells (include coordinate + state).
- Color-contrast AA for text and for cell states (don't rely on color alone — hit uses ✕, miss uses a dot).

---

## 9. Packaging (Docker/Podman) — target

- **Multi-stage build**: (1) build stage on a Maven + Temurin-25 JDK image running `mvn clean package` (which builds the frontend via `frontend-maven-plugin` and copies assets); (2) runtime stage on **`eclipse-temurin:25-jre`** copying only the fat JAR.
- Best practices: non-root user, minimal layers, `.dockerignore` (exclude `node_modules`, `target`, `frontend/build`, `.git`), pinned base tags, `EXPOSE 8080`, healthcheck hitting a lightweight endpoint, JVM container-awareness flags as needed.
- **`docker-compose.yml`** for one-command run (single service, port 8080, in-memory).
- Must run identically under **Docker and Podman** (`podman build` / `podman run`, `podman compose` or `podman play`). Avoid Docker-only features.
- Image stays single-instance/in-memory — no external services.

---

## 10. Acceptance criteria (per phase — summary)

Detailed criteria live in `IMPLEMENTATION_PLAN.md`; the essentials:

1. **Backend upgrade** — builds on Java 25 / Boot 4.1; all existing tests pass; API byte-compatible with the pre-upgrade baseline; Swagger UI serves the same spec.
2. **Backend tests** — documented gap analysis; controllers + exception handlers covered; any bug fixed with a regression test; coverage target met (§TESTING_PLAN).
3. **FE cleanup** — no Bootstrap/CRA/`prop-types` references; dead code removed; app still builds (unstyled acceptable).
4. **FE tooling** — Vite dev + prod build work; Vite `outDir` aligned with Maven copy; deps installed; type-check clean.
5. **FE development** — all 8 screens match `MOCKUP.html`; widgets use the adapter only; behavior identical to baseline; responsive per §8.4; excluded items absent.
6. **FE testing** — unit + component/UI tests pass against `MockGameAdapter`; coverage target met.
7. **Live e2e** — Playwright plays a full single game against a running server and asserts win/lose.
8. **Verification** — full clean build + manual/automated regression pass; API oracle matches.
9. **Docker** — image builds via multi-stage; compose runs; best-practices checklist satisfied.
10. **Container verification** — image runs and is playable under **both** Docker and Podman.
11. **Docs** — `README.md` + this folder updated; branch finalized.

---

## 11. Version reference (verified 2026-07-10)
| Component | Target | Source |
|---|---|---|
| Java | 25 (LTS) | Adoptium / Temurin 25 |
| Spring Boot | 4.1.0 | spring.io |
| springdoc | 2.8.x (`-starter-webmvc-ui`) | springdoc.org |
| React | 19.2.x | react.dev |
| Vite | latest | vite.dev |
| Vitest | 4.1.x | npm |
| react-router | v8 | github/remix-run |
| Node (build/test) | 24 LTS (≥22.12) | nodejs.org |
| Runtime image | eclipse-temurin:25-jre | Docker Hub |

Sources: [Spring Boot 4.0 announcement](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/) · [Sunsetting CRA](https://react.dev/blog/2025/02/14/sunsetting-create-react-app) · [springdoc modules](https://springdoc.org/modules.html) · [Temurin 25 available](https://adoptium.net/news/2025/09/eclipse-temurin-25-available) · [eclipse-temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin) · [Vitest on npm](https://www.npmjs.com/package/vitest) · [react-router releases](https://github.com/remix-run/react-router/releases)
