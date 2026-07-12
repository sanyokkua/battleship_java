# Battleship — Redesign & Modernization Specification

**Status:** v3 · **Date:** 2026-07-11 · **Owner:** Oleksandr (sanyokkua)
**v3 changes:** aligned to current codebase (branch `feature/redesign-v2`, docs `docs/index.md` + `docs/architecture.md`); made game-mode (edition) selection explicit; specified the no-go moat + two-way ship removal in Preparation; added a notifications/dialogs/validation system (§8.7); added English+Ukrainian internationalization (§8.8) with one small additive backend field (`errorCode`).

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
- **Internationalization (added v3):** ship the UI in **English (default)** and **Ukrainian**, switchable at runtime (§8.8). All user-facing text — labels, buttons, notifications, validation messages, edition/ship names — must be localizable, with no hard-coded strings in components.
- **Richer UI feedback (added v3):** an explicit notifications/dialogs/validation system (§8.7).
- Documentation updates (`README.md`, this folder).

### 2.2 Out of scope (do NOT implement)
- **Backend REST API is change-controlled, not fully frozen (relaxed in v3).** No *breaking* changes to existing paths, verbs, request bodies, response DTOs, or status codes. **Additive, backwards-compatible changes are allowed only where required to deliver a better frontend** — specifically the i18n `errorCode` field (§8.8.4). Any such change must be documented here and covered by tests. Everything else about the API stays as-is.
- **No change to game rules/engine logic.** The engine may only be *modified* if Phase 2 testing reveals a genuine bug; such a change must be documented and covered by a regression test.
- **No new gameplay features**: no WebSockets/SSE, no password protection, no ship auto-place, no multi-instance/scaling, no database. Storage stays **in-memory, single instance**. (Localization, richer UI feedback, and the shareable join link are UI capabilities, not gameplay features, and are in scope.)
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
| i18n (added v3) | **react-i18next + i18next** (+ `i18next-browser-languagedetector`) | De-facto standard, lightweight, supports interpolation/plurals, JSON resource bundles, persisted language detection. |
| Error localization (added v3) | **Additive `errorCode` on `ExceptionDto`** | Lets the frontend show localized error text keyed by a stable code instead of the backend's English `errorMessage`. |

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
| Modify (additive, v3) | `ExceptionDto` → add `errorCode` field + set it in the two exception handlers | For i18n error text (§8.8.4). No new dependency; no route/status change. |

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
| Install (v3) | `i18next`, `react-i18next`, `i18next-browser-languagedetector` | localization (en/uk) — §8.8 |
| Install (dev) | `@types/react`, `@types/react-dom`, eslint + config | typings/lint |

---

## 6. Backend API (reference — no breaking changes; one additive field allowed)

Base path `/api/v2/game`. The frontend adapter maps 1:1 to these. The **only** permitted change is the additive `errorCode` on `ExceptionDto` (§8.8.4) — no path/verb/status/existing-field changes.

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
**Editions ("game modes"):** `GET /editions` returns raw enum names `["UKRAINIAN","MILTON_BRADLEY"]`. Both are 10×10 with **10 ships**; they differ only in ship-size distribution — Ukrainian: Patrol×4 (size 1), Submarine×3 (2), Destroyer×2 (3), Battleship×1 (4) = 20 cells; Milton Bradley: Submarine×4 (2), Destroyer×3 (3), Battleship×2 (4), Carrier×1 (5) = 30 cells. The frontend maps enum → localized label (§8.8).
**Key DTOs (unchanged except `ExceptionDto`):** `CellDto{row,col,ship,hasShot,isAvailable}` — `isAvailable=false` on occupied cells **and on the 8-neighbour "no-go" moat** around any placed ship (drives the blocked-cell rendering in §8.3/§8.5). `ShipDto{shipId,shipSize}`; `ResponseGameplayStateDto{playerName,isPlayerActive,isPlayerWinner,playerNumberOfAliveCells,playerNumberOfAliveShips,playerField,opponentName,isOpponentReady,opponentNumberOfAliveCells,opponentNumberOfAliveShips,opponentField,hasWinner,winnerPlayerName}`; `ResponsePreparationState{ships,field}`; `ResponseOpponentInformationDto{playerName,ready}`; **`ExceptionDto{status,errorMessage,errorCode?}`** (`errorCode` additive, v3).

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
| State | When | Appearance |
|---|---|---|
| Water (untouched) | `hasShot=false`, no ship (opponent view) or empty own cell | `--water` fill, `--water-line` border |
| Your ship | own cell with `ship` | navy/sea gradient (`--ship`→`--sea`) |
| Valid drop (prep) | hovered/selected placement is legal | light green fill, dashed `--ok` border (`ghost`) |
| **No-go / blocked (prep)** | `isAvailable=false` && no ship — the 8-neighbour moat around a placed ship | **hatched grey** (repeating diagonal stripes), not interactive |
| Hit | `hasShot=true` && ship, ship not fully sunk | red (`--hit`) with ✕ |
| Miss | `hasShot=true` && no ship | white with small grey dot (`--miss`) |
| Sunk | every cell of the ship hit | dark red (`--sunk`); the auto-revealed moat around it shows as misses |

### 8.3 Screen-by-screen (bind only to real DTO fields)
1. **Home** — hero + primary "New Game", secondary "Join Game".
2. **New Game — game-mode (edition) selection is a first-class step.** Render the editions from `GET /editions` as **selectable mode cards** (not a bare dropdown), each showing the localized edition name, a one-line description, and the ship-size makeup (Ukrainian: 10 ships, sizes 1–4, 20 cells; Milton Bradley: 10 ships, sizes 2–5, 30 cells). A `Select` is an acceptable fallback but the cards are the target. Below: name `Input`, submit → create session + player. Keep name validation (≥2 chars) with an **inline field error** (§8.7).
3. **Join Game** — name + Game ID inputs; keep 36-char UUID validation with inline validity feedback; submit → join. The Game ID field may arrive pre-filled via a `?id=` query param from a shared join link (see Wait screen below) — the valid-code checkmark shows immediately in that case, with no user interaction required.
4. **Wait** — greeting (`Hello, {name}!`), `StepTracker` (Create→Waiting→Prepare→Battle), Game ID box + Copy button (raw session UUID) with a **"copied" toast**, plus a **"Copy link" button** that copies a shareable `${window.location.origin}/join?id=<sessionId>` URL (same-origin — SPA and API share one Spring Boot instance — with its own **"link copied" toast**), animated "waiting" indicator; poll opponent every 3s → go to Preparation when both present.
5. **Loading** — top `LoadingBar` + centered anchor; reused wherever a fetch is in flight (replaces old spinner/progress).
6. **Preparation** — `ShipTray` (localized ship **names** + sizes), **inline `DirectionToggle` (Horizontal/Vertical)** replacing the old modal, `Board` showing water / placed ships / **valid-drop ghost** / **no-go moat** (from `isAvailable`), opponent status `Pill`. **Ship removal must be obvious and offered two ways:** (a) tap any placed ship on the board, and (b) a **✕ remove button** on each placed ship in the tray. Both call `removeShip(coordinate)` (the tray button uses a coordinate belonging to that ship from `preparationState.field`). A short helper line states both the placement and removal gestures. On a rejected placement → **error toast** ("ships can't touch"); on success → optional success toast. "Ready to go!" is enabled only when all ships are placed; note that removing a ship server-side resets `ready` (§ engine), so the UI must reflect that. Calls: `getPreparationState`, `addShip`, `removeShip`, `setReady`.
7. **Gameplay** — two `PlayerCard`s (cells health from `*NumberOfAliveCells` 0–100, ships from `*NumberOfAliveShips`), `TurnBanner` from `isPlayerActive`, **adaptive boards**: Target = opponent field (tap to `shoot`), Fleet = own field (read-only). Feedback: **"not your turn" info toast** if the player taps while inactive; **hit/miss/sunk** reflected on the board and optionally as a toast. Keep 5s poll; redirect to Results when `hasWinner`.
8. **Results** — win/lose hero from `isPlayerWinner`/`winnerPlayerName`, both boards read-only (hits/misses/ships), "Return to main menu". Show only API-backed stats (Ships sunk = edition total − `numberOfAliveShips`).

*(The mockup's "Feedback" chip is a component catalog, not a routed screen — it documents toasts, inline validation, and the confirmation dialog per §8.7.)*

### 8.4 Adaptivity / responsive rules
- **Breakpoint:** mobile ≤ **640px**, desktop ≥ **641px** (demoed in the mockup toggle).
- **Mobile:** single column; `AppBar` collapses to a hamburger (the **language switch stays reachable**); gameplay/results boards use a **tab switch** (Target ↔ Fleet) instead of stacking; preparation stacks tray above board; forms full-width; touch targets ≥ **44px**.
- **Desktop:** multi-column; inline `AppBar` nav + language switch; gameplay/results boards **side-by-side**; preparation shows tray + board in two columns.
- **Board sizing:** the `Board` is a square CSS grid that **fills its container width** (`width:100%`, `aspect-ratio:1/1`) with coordinate rails (A–J columns, 1–10 rows).
- Content max-widths keep forms readable on large screens (~440–460px form column).

### 8.5 Excluded mockup elements (NOT API-backed — do not build)
- **"Auto-place remaining"** button — no endpoint. Omit. (Removed from the mockup in v3.)
- **Results "Hits" and "Time"** stats — not tracked by the API. Omit. Only **"Ships sunk"** is shown (derived: edition total − `numberOfAliveShips`).
- Any password / realtime affordance — deferred, out of scope. (Share-links were previously deferred here too, but are now in scope — see the Wait screen's "Copy link" button in §8.3 item 4.)

### 8.6 Accessibility
- Keyboard operable; visible focus rings on all interactive elements; the language switch and mode cards are real buttons/radios.
- `aria-label`s on icon-only controls (hamburger, copy, tray remove ✕) and on board cells (include coordinate + state, e.g. "C7, hit"). Toasts use an ARIA live region; the confirmation dialog is a focus-trapped `role="dialog"`.
- Color-contrast AA for text and cell states (don't rely on color alone — hit uses ✕, miss uses a dot, no-go uses a hatch).

### 8.7 Notifications & user feedback (new in v3)
Every state change and user action must produce visible feedback. Build a small feedback system (see the mockup's **Feedback** catalog):

- **Toasts** — transient, auto-dismiss, ARIA-live, four variants:
  - *success* (ship placed, Game ID copied, you ready),
  - *info* (not your turn, waiting for opponent, opponent is ready),
  - *warning/hit* (direct hit / ship sunk),
  - *error* (invalid placement, ship can't touch, join failed, session not found, action in wrong stage).
- **Inline validation** — field-level errors under inputs (name too short, malformed Game ID), shown on blur/submit; submit disabled until valid.
- **Confirmation dialog** — focus-trapped modal for destructive/irreversible actions (e.g. "Leave this game?" — the game is in-memory and can't be resumed). Cancel + confirm actions.
- **Status surfaces** — `TurnBanner`, opponent-status `Pill`, `StepTracker`, and the loading bar convey ambient state without a toast.
- **Error mapping** — every toast/error message is keyed by a stable identifier and localized (§8.8); the frontend derives the key from `ExceptionDto.errorCode` (preferred) or, as a fallback, from the HTTP status + the action context. Never render the backend's raw English `errorMessage` to the user (keep it for logs).

**Notification catalog (message keys → trigger):** `ship.placed` (200 add), `ship.removed` (200 delete), `place.tooClose`/`place.outOfBounds`/`place.occupied` (coordinate 400), `ready.needAllShips` (start in wrong state), `shot.hit`/`shot.miss`/`shot.sunk` (shot result), `turn.notYours` (tap while inactive), `id.copied`, `join.invalidId`, `session.notFound`, `error.generic` (500). Each key has en + uk text.

### 8.8 Internationalization (new in v3)
- **Languages:** English (`en`, default/fallback) and Ukrainian (`uk`). Architecture must make adding a third language a matter of adding one resource file.
- **Library:** `react-i18next` + `i18next` + `i18next-browser-languagedetector`. Resource bundles as JSON namespaces (e.g. `common`, `screens`, `notifications`, `errors`) under `src/i18n/{en,uk}/`.
- **Coverage:** *all* user-facing text is localized — screen copy, buttons, labels, placeholders, `StepTracker`/tab labels, notifications & validation (§8.7), the confirmation dialog, and **derived display names** for editions (`UKRAINIAN`→"Ukrainian"/"Українська") and ship types (`CARRIER`→"Carrier"/"Авіаносець", etc.). No literal user-facing string may live in a component.
- **Language selector:** a control in the `AppBar` (EN / УКР), reachable on mobile too; selection persists (localStorage via the language detector) and applies instantly without reload; default English on first visit.
- **Formatting:** use i18next interpolation for dynamic values (`{{name}}`, counts). Use ICU/plural rules for count-bearing strings ("1 ship" / "5 ships" / Ukrainian plural forms).
- **8.8.4 Backend support (small additive change):** add an `errorCode` string (stable enum-like values, e.g. `COORDINATE_INVALID`, `SHIP_ID_INVALID`, `STAGE_INVALID`, `EDITION_INVALID`, `PLAYER_NAME_INVALID`, `SESSION_NOT_FOUND`, `INTERNAL`) to `ExceptionDto`, populated by `ValidationExceptionHandler` / `InternalExceptionHandler` from the typed exception. `errorMessage` stays (English, for logs/fallback). This is additive and backwards-compatible; existing consumers ignore the new field. The frontend maps `errorCode` → localized text; if absent, it falls back to status+context mapping so it also works against an un-upgraded backend.

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
5. **FE development** — all screens match `MOCKUP.html`; widgets use the adapter only; behavior identical to baseline; responsive per §8.4; excluded items absent. Includes: game-mode selection cards, the no-go moat + two-way ship removal (§8.3), the notifications/dialog/validation system (§8.7), and full en/uk localization with a working language switch (§8.8). No hard-coded user-facing strings.
6. **FE testing** — unit + component/UI tests pass against `MockGameAdapter`; i18n (both locales, no missing keys) and feedback/notification behavior covered; coverage target met.
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
