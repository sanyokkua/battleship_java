# Battleship — Implementation Plan

**Status:** v3 · **Date:** 2026-07-11
Companion to `SPECIFICATION.md` (what/why), `MOCKUP.html` (visuals), `TESTING_PLAN.md` (tests), `PHASE_BOOTSTRAP_PROMPT.md` (how to start a phase).
**v3 changes:** Phase 2 gains an additive `ExceptionDto.errorCode` ticket (2.6); Phase 5 gains i18n (5.3), the feedback/notifications system (5.5), the no-go moat + two-way ship removal, and game-mode cards; invariants now forbid hard-coded user-facing strings.

This plan defines **11 ordered phases**. Each phase entry gives its objective, the spec/mockup references to read, the concrete tasks (with sub-tickets where the phase is large), deliverables, per-phase testing, verification, and acceptance criteria.

---

## Execution model

- **Branch:** all work on `feature/redesign-v2` (create in Phase 1 setup if not created). Each phase = one or more commits; large phases may use short-lived sub-branches merged back into the feature branch.
- **Orchestration:** the **main session only orchestrates**. Each phase (and each sub-ticket) is executed by a **sub-agent** started from `PHASE_BOOTSTRAP_PROMPT.md`. The main session: (1) launches the phase in plan mode via the bootstrap prompt, (2) reviews the produced plan/sub-tickets, (3) dispatches sub-agents to implement, (4) verifies acceptance criteria, (5) records results, (6) proceeds to the next phase.
- **Sub-ticket rule:** if a phase's scope is large (flagged **SPLIT** below), the phase's first job is to produce sub-tickets `PHASE-<n>.<m>` — each independently implementable and testable — before any code is written.
- **Phase gate:** a phase is "done" only when its acceptance criteria and its `TESTING_PLAN.md` obligations are green. Do not start the next phase until the gate passes.
- **Invariants for every phase:** no *breaking* backend API changes — the only permitted API change is the additive `ExceptionDto.errorCode` for i18n (SPEC §6, §8.8.4); never change game logic except a Phase-2 bug fix (SPEC §2.2); keep the app building at each phase boundary; **no hard-coded user-facing strings** (everything localized, SPEC §8.8).

**Dependency order:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11. Phases 1–2 (backend) are independent of 3–7 (frontend) and *may* run in parallel tracks, but 8 (verification) requires both, and 9–11 require 8.

---

## Phase 0 — Baseline & safety net (prerequisite, do once)
**Objective:** capture current behavior as a regression oracle before touching anything.
**Read:** SPEC §3, §6.
**Tasks:**
- 0.1 Create branch `feature/redesign-v2`.
- 0.2 Build & run today: `mvn clean install && mvn spring-boot:run`; play one full game; note behavior.
- 0.3 Capture the API oracle: request/response samples for every endpoint in SPEC §6 (save under `docs/redesign/artifacts/api-baseline/`).
- 0.4 Confirm `mvn test` is green.
**Acceptance:** green build + tests; documented baseline committed.

---

## Phase 1 — Backend: Java 25 + Spring Boot 4.1 + dependencies
**Objective:** modernize the backend with zero behavior/API change.
**Read:** SPEC §4.1, §5.1, §6, §11. **Testing:** TESTING_PLAN §Backend-upgrade regression.
**Tasks:**
- 1.1 Bump `spring-boot-starter-parent` → 4.1.0; set `<java.version>25</java.version>`; ensure local + CI JDK 25 (Temurin).
- 1.2 Replace `springdoc-openapi-ui:1.8.0` → `springdoc-openapi-starter-webmvc-ui:2.8.x`; verify `/swagger-ui/index.html` and generated spec still describe SPEC §6 exactly.
- 1.3 Bump `commons-lang3` to latest 3.x; confirm Lombok resolves Java-25-capable (≥1.18.42); keep the Lombok exclude in `spring-boot-maven-plugin`.
- 1.4 Update `frontend-maven-plugin` to latest; bundled Node → 24 LTS.
- 1.5 Resolve Boot 3→4 / Spring 6→7 migration breakages (property/config renames, deprecated APIs) — **compile-fix only**; no controller/DTO/route changes.
- 1.6 `mvn test` green; smoke every endpoint against the Phase 0 oracle.
**Deliverables:** upgraded `pom.xml`; running app on Java 25 / Boot 4.1.
**Acceptance:** builds & runs on Java 25/Boot 4.1; all existing tests pass; API byte-compatible with Phase 0; Swagger unchanged.

---

## Phase 2 — Backend: close test gaps + fix bugs  **[SPLIT]**
**Objective:** analyze coverage gaps, add tests, fix any bug found (the only permitted logic change; must be documented + regression-tested).
**Read:** SPEC §3.1 (gaps), §6. **Testing:** TESTING_PLAN §Backend-unit, §Backend-integration, §Coverage.
**Sub-tickets (produce these first):**
- 2.1 Coverage gap report: run coverage (JaCoCo), list untested classes/branches; prioritize `web/controllers/rest/*`, `web/exceptions/*`, DTO mapping, validation edge cases.
- 2.2 REST controller tests (MockMvc / `@WebMvcTest`) for the three controllers: happy path + validation errors + wrong-stage + not-found, asserting exact status codes and DTO shapes (SPEC §6).
- 2.3 Exception-handler tests (`ValidationExceptionHandler`, `InternalExceptionHandler`): each typed game exception → expected HTTP status + `ExceptionDto`.
- 2.4 Engine/edge-case top-ups where §2.1 shows gaps (persistence eviction, editions, coordinate/ship utils).
- 2.5 Bug triage: if a test reveals a defect, fix the minimal cause, document it in this plan's changelog + a regression test.
- 2.6 **Additive i18n support (SPEC §8.8.4):** add `errorCode` (stable enum-like string) to `ExceptionDto`; populate it in `ValidationExceptionHandler` (per typed exception) and `InternalExceptionHandler` (`INTERNAL`). Keep `errorMessage`. Add tests asserting each exception → expected `errorCode` + unchanged status/shape. This is the **only** permitted API change and must be backwards-compatible (existing fields untouched).
**Acceptance:** gap report committed; controllers + handlers covered; coverage target met (TESTING_PLAN); all tests green; any bug fix documented + covered. **No breaking API change; `errorCode` added additively and tested.**

---

## Phase 3 — Frontend: cleanup outdated code & dependencies
**Objective:** remove the deprecated stack and dead code to a clean, still-building baseline.
**Read:** SPEC §3.2, §5.2, §2.3. **Testing:** TESTING_PLAN §FE-build-smoke.
**Tasks:**
- 3.1 Remove `bootstrap`, `react-bootstrap`, `react-router-bootstrap`, `prop-types`; strip the Bootstrap CSS import.
- 3.2 Remove/quarantine class-component pages and Bootstrap-based elements that will be rebuilt in Phase 5 (keep `ApplicationTypes.ts`, `BackendRequestService.ts`, `GameBrowserStorage.ts`, `utils/*` as references for now).
- 3.3 Delete CRA-specific files/config not needed under Vite (identified with Phase 4).
- 3.4 Record a short "removed inventory" note.
**Acceptance:** no deprecated deps remain; repo still installs; no references to removed packages; documented removals.

> *Note:* Phases 3 and 4 are tightly coupled; a sub-agent may execute them together but must satisfy both gates.

---

## Phase 4 — Frontend: Vite + dependency install + configuration
**Objective:** stand up the Vite toolchain and install the new dependency set.
**Read:** SPEC §4.2, §5.2, §7, §9 (outDir note). **Testing:** TESTING_PLAN §FE-build-smoke.
**Tasks:**
- 4.1 Add `vite` + `@vitejs/plugin-react`; create `vite.config.ts`.
- 4.2 Move `index.html` to frontend root; entry `/src/index.tsx` (or `main.tsx`); keep `<div id="root">`.
- 4.3 Dev proxy `server.proxy['/api'] → http://localhost:8080`.
- 4.4 **Set `build.outDir = 'build'`** to match the Maven `copy-resources` source (SPEC §9); if choosing `dist`, update the Maven path instead — do exactly one and note it.
- 4.5 Scripts: `dev`, `build` (`tsc && vite build`), `preview`, `test`, `test:e2e`. Remove `react-scripts`.
- 4.6 `tsconfig.json`: target ES2020+, `moduleResolution: "bundler"`, `types: ["vite/client"]`, keep `strict`.
- 4.7 Upgrade React 19.2, react-router 8, TypeScript 5, axios/axios-retry; install Vitest + RTL + jsdom + `@playwright/test`.
- 4.8 Install Playwright browsers; scaffold `vitest.config` and a Playwright config.
**Acceptance:** `vite dev` serves; `npm run build` emits to the agreed `outDir`; type-check clean; e2e/test runners scaffolded.

---

## Phase 5 — Frontend: build the app (Adapter + Widgets + screens)  **[SPLIT]**
**Objective:** implement the redesigned UI with the adapter/widget architecture, matching `MOCKUP.html`, behavior identical to baseline.
**Read:** SPEC §4.2, §7, §8 (all — incl. §8.7 feedback, §8.8 i18n), `MOCKUP.html` (all screens + the Feedback catalog + EN/УКР toggle). **Testing:** TESTING_PLAN §FE-unit, §FE-component, §FE-i18n.
**Sub-tickets (produce these first):**
- 5.1 **Adapter layer:** `GameAdapter` interface (SPEC §7.1), `HttpGameAdapter` (wrap existing endpoints; parse `errorCode`), `MockGameAdapter` (deterministic in-memory), context + `useGameAdapter()`.
- 5.2 **Hooks:** `usePolling` (StrictMode-safe), screen hooks as needed; keep 3s/5s intervals.
- 5.3 **i18n foundation (SPEC §8.8):** set up `react-i18next` + language detector; create `en`/`uk` JSON bundles (namespaces `common/screens/notifications/errors`); `AppBar` language switch (persisted); helpers to localize edition + ship-type enum names. **Establish the "no hard-coded strings" rule for all later tickets.**
- 5.4 **Design system:** tokens + base CSS (SPEC §8.1–8.2); primitives `Button/Input/Field/Card/Pill/StepTracker/LoadingBar/ModeCard`.
- 5.5 **Feedback system (SPEC §8.7):** `Toast` (4 variants + ARIA-live host), inline field-error, focus-trapped confirmation `Dialog`; a `useNotify()` hook; error→message mapping keyed off `errorCode` with status+context fallback.
- 5.6 **Board system:** `Board` (full-width square grid + A–J/1–10 rails), `BoardCell` (all states incl. **no-go/hatched** SPEC §8.2), `Legend` (incl. no-go), `BoardTabs` (mobile adaptivity).
- 5.7 **AppBar + routing:** `NavLink`-based nav, hamburger on mobile (language switch reachable), route table + stage guards (SPEC §7.4).
- 5.8 **Screens** (one ticket each, localized, bind only to real DTO fields, exclude SPEC §8.5 items):
  Home · **New Game (mode cards, correct 10-ship editions)** · Join (inline validation) · Wait (StepTracker + copy toast) · Loading · **Preparation (inline `DirectionToggle`, no-go moat from `isAvailable`, two-way ship removal: tap board + tray ✕, error/success toasts; no auto-place)** · Gameplay (PlayerCards + TurnBanner + adaptive boards + not-your-turn toast) · Results (Ships-sunk only).
- 5.9 **Responsive pass:** breakpoints, board tabs, touch targets (SPEC §8.4).
**Acceptance:** all screens match the mockup at mobile & desktop in **both languages**; widgets consume only the adapter; behavior matches baseline; no-go moat + two-way removal + feedback system present; excluded elements absent; no hard-coded user-facing strings; type-check + unit/component/i18n tests green.

---

## Phase 6 — Frontend: automated testing (unit + component/UI, mock adapter)
**Objective:** prove widget/page behavior against `MockGameAdapter` without a live backend.
**Read:** TESTING_PLAN §FE-unit, §FE-component, §FE-e2e-mocked. **Depends on:** Phase 5.
**Tasks:**
- 6.1 Unit tests: adapters (Http against mocked axios; Mock adapter behavior), hooks (`usePolling` cleanup), utils.
- 6.2 Component/UI tests (RTL + `MockGameAdapter`): each screen renders correct states; interactions call the right adapter methods (place/remove ship, direction toggle, ready, shoot, copy ID); turn/loading/error states; responsive tab switch.
- 6.3 Playwright **mocked** e2e: drive full flows in-browser with the adapter mocked (route-level or injected), no server.
- 6.4 Meet coverage target (TESTING_PLAN).
**Acceptance:** unit + component + mocked-e2e suites green in CI; coverage target met; no reliance on a live backend.

---

## Phase 7 — Live e2e: automated single game against a running server
**Objective:** verify the real stack end-to-end.
**Read:** TESTING_PLAN §Live-e2e. **Depends on:** Phases 1–6.
**Tasks:**
- 7.1 Start the packaged app (JAR) on 8080 (CI-scriptable).
- 7.2 Playwright script: two contexts (two players) — create → share ID → join → prepare (place both orientations, ready) → alternate shots to completion → assert win on one side, lose on the other; assert board states (hit/miss/sunk).
- 7.3 Capture trace/video on failure.
**Acceptance:** a full game plays automatically against the live server with correct win/lose outcomes; script is CI-runnable.

---

## Phase 8 — Final verification & validation
**Objective:** confirm the whole system before packaging.
**Read:** SPEC §10; all test docs. **Depends on:** Phases 1–7.
**Tasks:**
- 8.1 Clean build: `mvn clean install && mvn spring-boot:run`; load `http://localhost:8080`, test all routes incl. direct deep links.
- 8.2 Re-run the Phase 0 API oracle → responses byte-compatible.
- 8.3 Run all suites: backend, FE unit/component, mocked e2e, live e2e.
- 8.4 Confirm SPEC §8.5 exclusions did not ship; a11y spot-check (SPEC §8.6); cross-browser smoke.
- 8.5 Type-check clean; no console errors in production build.
**Acceptance:** every gate from Phases 1–7 green simultaneously on a clean checkout; API unchanged; UI matches mockup.

---

## Phase 9 — Docker/Podman packaging
**Objective:** containerize following best practices.
**Read:** SPEC §9. **Testing:** TESTING_PLAN §Container.
**Tasks:**
- 9.1 Multi-stage `Dockerfile`: build stage (Maven + Temurin 25 JDK, `mvn clean package` incl. frontend) → runtime stage (`eclipse-temurin:25-jre`, copy fat JAR).
- 9.2 Best practices: non-root user, `.dockerignore` (`node_modules`, `target`, `frontend/build`, `.git`), pinned tags, minimal layers, `EXPOSE 8080`, `HEALTHCHECK`, container-aware JVM.
- 9.3 `docker-compose.yml`: single service, port 8080, in-memory.
- 9.4 Avoid Docker-only features so Podman works identically.
**Acceptance:** image builds via multi-stage; compose config valid; best-practices checklist satisfied.

---

## Phase 10 — Container verification (Docker AND Podman)
**Objective:** prove the image runs and is playable on both engines.
**Read:** SPEC §9. **Depends on:** Phase 9.
**Tasks:**
- 10.1 `docker build` + `docker run` (or `docker compose up`) → app reachable on 8080; smoke a game (reuse Phase 7 script against the container).
- 10.2 `podman build` + `podman run` (and `podman compose`/`play` if used) → same result.
- 10.3 Record image size and startup time; confirm non-root and healthcheck behavior.
**Acceptance:** identical, playable behavior under Docker and Podman; documented run commands.

---

## Phase 11 — Documentation finalization & branch wrap-up
**Objective:** leave the repo self-explanatory and ready to merge.
**Read:** everything. **Depends on:** Phases 1–10.
**Tasks:**
- 11.1 Update root `README.md`: prerequisites (JDK 25, Node 24), new build/run (`npm run dev`/`build`, Maven, Docker/Podman commands), screenshots note.
- 11.2 Update this folder: mark completed phases, record decisions/bug fixes/changelog, refresh version table if anything shifted.
- 11.3 Ensure `docs/img/*` (or new screenshots of the redesign) reflect the new UI; add a note if regenerated.
- 11.4 Final review; squash/organize commits; open PR from `feature/redesign-v2`.
**Acceptance:** docs match the shipped app; branch builds green; PR ready with a clear summary and test evidence.

---

## Phase → primary references map
| Phase | SPEC sections | Mockup | Testing plan |
|---|---|---|---|
| 1 | §4.1, §5.1, §6, §11 | — | Backend-upgrade regression |
| 2 | §3.1, §6, §8.8.4 | — | Backend-unit, Backend-integration, Coverage |
| 3 | §3.2, §5.2, §2.3 | — | FE-build-smoke |
| 4 | §4.2, §5.2, §7, §9 | — | FE-build-smoke |
| 5 | §4.2, §7, §8 (incl. §8.7, §8.8) | all screens + Feedback + EN/УКР | FE-unit, FE-component, FE-i18n |
| 6 | §7, §8.7, §8.8 | all screens | FE-unit, FE-component, FE-i18n, FE-e2e-mocked |
| 7 | §6, §8 | all screens | Live-e2e |
| 8 | §10 | all screens | all |
| 9 | §9 | — | Container |
| 10 | §9 | — | Container |
| 11 | all | all | — |

## Changelog / decisions (append during execution)
- _(empty — record bug fixes, deviations, and version pins here as phases complete.)_
