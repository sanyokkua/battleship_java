# Battleship — Testing Plan

**Status:** v2 · **Date:** 2026-07-10
Companion to `SPECIFICATION.md` and `IMPLEMENTATION_PLAN.md`. Defines every test type, its scope, tooling, coverage target, how to run it, and which phase owns it.

---

## 1. Principles
- **Behavior is frozen.** The backend API (SPEC §6) and game rules (SPEC §2.2) must not change; tests exist largely to *prove* they didn't. The only permitted logic change is a Phase-2 bug fix, which must ship with a regression test.
- **Test at the right level.** Prefer fast unit/component tests; use e2e sparingly for real integration confidence.
- **Frontend is transport-agnostic.** UI tests run against `MockGameAdapter` (SPEC §7.1), not the network. Live integration is proven once, in the live-e2e phase.
- **Everything is CI-runnable and deterministic** (seeded mock data; no reliance on wall-clock races).

---

## 2. Test taxonomy

### 2.1 Backend — unit  *(Phase 2)*
- **Scope:** engine (`GameImpl`, `FieldManagementImpl`, utils, editions), `logic/api` (validation, id-gen, `GameControllerApiImpl`), persistence.
- **Tools:** JUnit 5, AssertJ, Mockito (from `spring-boot-starter-test`).
- **Add:** branch/edge cases flagged by the coverage gap report — coordinate/ship utils bounds, edition ship counts, persistence eviction/lookup, validation rejects.
- **Run:** `mvn test`.

### 2.2 Backend — integration / web layer  *(Phase 2)*
- **Scope:** the three REST controllers + exception handlers + DTO mapping.
- **Tools:** `@WebMvcTest` / `MockMvc` (slice) and/or `@SpringBootTest` for full-context flows.
- **Assert:** exact HTTP status codes, response DTO JSON shapes (SPEC §6), validation failures, wrong-stage and not-found handling, `ExceptionDto` for each typed game exception.
- **Run:** `mvn test`.

### 2.3 Backend — API contract / regression oracle  *(Phases 1, 8)*
- **Scope:** all endpoints in SPEC §6 respond byte-compatibly before vs. after the upgrade.
- **Tools:** the Phase 0 saved request/response samples (`docs/redesign/artifacts/api-baseline/`), replayed via MockMvc assertions or an HTTP client script; Swagger spec diff for §6.
- **Assert:** no path/verb/field/status drift.

### 2.4 Frontend — unit  *(Phase 6)*
- **Scope:** `HttpGameAdapter` (axios mocked — correct URLs/bodies/parsing per SPEC §6), `MockGameAdapter` (deterministic state transitions), hooks (`usePolling` starts/stops/cleans up, StrictMode-safe), pure utils.
- **Tools:** Vitest + jsdom.
- **Run:** `npm run test`.

### 2.5 Frontend — component / UI  *(Phase 6)*
- **Scope:** every widget and screen rendered with `MockGameAdapter` injected via context.
- **Tools:** Vitest + React Testing Library + `@testing-library/user-event` + `jest-dom`.
- **Assert per screen (SPEC §8.3):**
  - Home: navigation actions.
  - New/Join: validation gating, submit triggers correct adapter calls.
  - Wait: StepTracker state, copy button writes the session id, poll → transition.
  - Preparation: ship select → `DirectionToggle` → cell tap calls `addShip` with right args; tap placed ship → `removeShip`; valid-drop highlighting; "Ready" calls `setReady`; error toast on failure; **no auto-place control present**.
  - Gameplay: PlayerCard health from DTO fields; TurnBanner reflects `isPlayerActive`; tapping a target cell calls `shoot`; own board read-only; adaptive board tabs on mobile; redirect on `hasWinner`.
  - Results: win/lose from `isPlayerWinner`; read-only boards; **no Hits/Time stats present**.
- **Cell states:** water/ship/hit/miss/sunk render per SPEC §8.2 (assert by role/aria, not color alone).
- **Run:** `npm run test`.

### 2.6 Frontend — e2e (mocked backend)  *(Phase 6)*
- **Scope:** full user journeys in a real browser with the backend mocked (inject `MockGameAdapter` or stub network routes).
- **Tools:** `@playwright/test`.
- **Assert:** create → wait → prepare → play → results happy path; responsive layout at a mobile viewport (board tabs) and desktop viewport (side-by-side).
- **Run:** `npm run test:e2e` (mocked project/config).

### 2.7 Live e2e (real server, single game)  *(Phase 7)*
- **Scope:** the packaged app on `:8080`, two Playwright browser contexts = two players, one complete game to a decisive result.
- **Tools:** Playwright; app started from the built JAR.
- **Assert:** create/share/join, ship placement (both orientations) + ready, alternating shots, hit/miss/sunk visuals, one **win** + one **lose** screen. Trace/video on failure.
- **Run:** start JAR → `npm run test:e2e:live`.

### 2.8 Container smoke  *(Phase 10)*
- **Scope:** the Docker image running under **Docker** and **Podman**.
- **Assert:** container starts, app reachable on 8080, healthcheck healthy, non-root user, and the Phase 7 live-e2e script passes against the container on both engines.
- **Run:** `docker compose up` / `podman compose up` (or `run`) → live-e2e.

### 2.9 Accessibility  *(Phases 5–6, 8)*
- **Scope:** keyboard nav, focus visibility, aria-labels (hamburger, copy, board cells with coordinate + state), color contrast AA.
- **Tools:** RTL role/label queries; optional axe integration in Playwright; manual keyboard pass.

### 2.10 Cross-browser & responsive  *(Phase 8)*
- **Scope:** Chromium, Firefox, WebKit via Playwright projects; mobile (≤640px) and desktop (≥641px) viewports; direct-URL deep links under Spring.

---

## 3. Coverage targets
| Area | Target |
|---|---|
| Backend line/branch (JaCoCo) | ≥ 80% overall; **100% of web controllers + exception handlers** |
| Frontend adapters + hooks | ≥ 90% lines |
| Frontend widgets/screens | every screen + every cell state has at least one component test |
| e2e | 1 mocked happy-path per screen flow + 1 live full game |

Targets are gates, not vanity metrics — a gap must be justified in the phase changelog if not met.

---

## 4. Test → phase ownership
| Test type | Owning phase | Blocks phase |
|---|---|---|
| Backend unit | 2 | 8 |
| Backend web/integration | 2 | 8 |
| API contract/regression | 1 (capture 0), re-run 8 | 8 |
| FE unit | 6 | 8 |
| FE component/UI | 6 | 8 |
| FE e2e (mocked) | 6 | 8 |
| Live e2e (single game) | 7 | 8, 10 |
| Container smoke (Docker+Podman) | 10 | 11 |
| A11y / cross-browser | 5–6 / 8 | 11 |

---

## 5. CI wiring (recommended)
- **Job A (backend):** JDK 25 → `mvn clean verify` (unit + web + JaCoCo + contract) — fails on coverage gate.
- **Job B (frontend):** Node 24 → `npm ci` → `tsc` → `npm run test` (Vitest) → `npm run test:e2e` (mocked).
- **Job C (live e2e):** build JAR → start on 8080 → Playwright live single game.
- **Job D (container):** build image → run under Docker; where the runner supports it, repeat under Podman → live-e2e against the container.
- Artifacts: JaCoCo report, Vitest coverage, Playwright HTML report + traces.

---

## 6. Definition of "tested" per phase
A phase passes its testing gate when: its owned suites are green in CI, the coverage target for its area is met (or justified), and it introduces **no** regression in the API contract oracle or in previously-green suites.
