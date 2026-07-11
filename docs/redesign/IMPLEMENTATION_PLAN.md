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
- **Phase 0 (Baseline & safety net) — complete.** `feature/redesign-v2` confirmed as the working branch (already existed, no new branch needed). `mvn clean install` green (full build incl. frontend bundling). Added `ApiBaselineOracleCaptureTest` (`src/test/java/.../ApiBaselineOracleCaptureTest.java`), which drives one full 2-player game on the UKRAINIAN edition against every REST endpoint in SPEC §6 and dumps request/response/meta samples to `docs/redesign/artifacts/api-baseline/` (78 steps, 206 files, `manifest.json` + `README.md`); regen with `mvn test -Dtest=ApiBaselineOracleCaptureTest`. `mvn test` (full suite): 72 tests, 0 failures. No production code (`src/main`, `frontend/src`) touched — capture-only, per phase scope. Two pre-existing issues discovered and documented (not fixed, per frozen-logic/frozen-API invariants):
  - `springdoc-openapi-ui:1.8.0` is a Spring Boot 2.x-era artifact incompatible with this app's Spring Boot 3.3.5 — no Swagger/OpenAPI autoconfig registers, so `/swagger-ui/index.html` (and `/v3/api-docs`) 404 rather than serving Swagger UI. Needs `springdoc-openapi-starter-webmvc-ui` on any dependency upgrade.
  - An out-of-turn shot throws a bare `IllegalStateException` in `GameImpl#makeShot`, which `ValidationExceptionHandler` doesn't catch (it only catches typed exceptions under `logic.api.exceptions`), so it surfaces as an unstructured `500` instead of the usual `400 ExceptionDto`. Flagged for Phase 2 (test-gap phase) as a candidate documented, regression-tested bug fix — not addressed here.
  - Full detail in `docs/redesign/artifacts/baseline-notes.md`.
- **Phase 1.1 (Backend toolchain: Java 25 + Spring Boot 4.1.0) — complete.** Zero API/logic behavior change, per phase scope; one commit per dependency-bump step, plus extra commits for migration-driven fixes discovered along the way (all bisectable). Final versions: `spring-boot-starter-parent` **4.1.0**, `<java.version>` **25** (built/run on Amazon Corretto 25.0.3), Lombok **1.18.46** (already the version the Boot 4.1.0 parent manages — no explicit pin needed, well above the 1.18.42 Java-25-capable floor), `springdoc-openapi-starter-webmvc-ui` **3.0.3** (live-verified on Maven Central; resolves the "springdoc 2.8.x vs 3.x" ambiguity flagged going into this phase — the 3.x line is real, published, and current, contrary to an earlier stale read that Central only had releases through 2.8.6), `commons-lang3` **3.20.0**, `frontend-maven-plugin` **2.0.1**, Node **v24.18.0** (current 24 LTS "Krypton" patch). `mvn clean install` and `mvn test`: **72/72, 0 failures**. Final API oracle re-run (`ApiBaselineOracleCaptureTest`) diffed clean against the Phase 0 baseline, modulo only the already-known non-deterministic fields (`sessionId`, `playerId`, `shipId`, `changesTime`/`lastId`, and request `path`s that embed those IDs). Swagger/OpenAPI now works end-to-end as a side effect of the springdoc swap (`/swagger-ui/index.html` → 200, `/v3/api-docs` → 200, path list includes all game endpoints) — the Phase 0-documented 404 is resolved, not separately "fixed." The pre-existing out-of-turn-shot bare-500 quirk was manually re-confirmed unchanged (still a bare `{"status":500,"errorMessage":"..."}`, not a structured 400) — left untouched per the frozen-logic invariant, as before.
  - **Deviation — Lombok annotation processing silently no-ops under Maven + JDK 25 without an explicit processor path.** With only the parent/Java bump applied, `mvn compile` failed across every Lombok-annotated class (`cannot find symbol: log`/`val`, "variable not initialized in the default constructor" for `@Builder`/`@Data`/`@AllArgsConstructor` classes) even though Lombok 1.18.46 was correctly resolved on the compile classpath. Root cause: under `maven-compiler-plugin` 3.15.0 + JDK 25, Maven's implicit annotation-processor discovery (relying on Lombok merely being present on the compile classpath, with no `<annotationProcessorPaths>` configured) does not reliably invoke Lombok's code generation. Fixed by adding an explicit, version-less `<annotationProcessorPaths>` entry for Lombok to `maven-compiler-plugin` in `pom.xml` (resolves via the Boot-managed Lombok version) — a standard, documented Lombok+Maven best practice, not a Lombok version problem. Bundled into the same commit as the parent/Java bump since the tree doesn't compile under JDK 25 without it.
  - **Deviation — `TestRestTemplate` relocated in Spring Boot 4.0.** `org.springframework.boot.test.web.client.TestRestTemplate` moved to `org.springframework.boot.resttestclient.TestRestTemplate`, split into two new artifacts (test-scope `spring-boot-resttestclient` for the class itself, plus `spring-boot-restclient` for its `RestTemplateBuilder` runtime dependency — both added as test-scope deps). `@SpringBootTest` also no longer auto-provides a `TestRestTemplate` bean; `@AutoConfigureTestRestTemplate` must be added explicitly. Both existing test classes (`BattleshipApplicationTests`, `ApiBaselineOracleCaptureTest`) updated accordingly — import/dependency/annotation only, no test logic changed. Own commit.
  - **Deviation — pre-existing test bug exposed by Jackson 3's stricter deserialization.** `BattleshipApplicationTests#post_player_field_shot_should_make_a_shot_by_cell` posted a `Coordinate` engine record (serializes to `{"row":0,"column":0}`) as the shot request body instead of the endpoint's actual `ParamCoordinateDto` (`{"row":0,"col":0}`). Under Jackson 2/Boot 3.3.5 this passed by accident: unmapped JSON properties were silently ignored by default, so `column` was dropped and `col` fell back to its Java `int` default of `0`, which happened to match the test's intended shot at `(0,0)`. Jackson 3's default deserialization is stricter and 400s on the malformed body, surfacing the latent bug. Confirmed via manual curl against the running app (pre-fix) that `{"row":0,"column":0}` 400s while `{"row":0,"col":0}` is accepted — a test defect, not a production/API behavior change. Fixed by sending the correct DTO. Own commit.
  - **Deviation — genuine Jackson 2→3 wire-format change requiring a DTO fix (the anticipated risk, confirmed real).** Boot 4's Jackson 3 default no longer collapses an auto-detected `is`-prefixed boolean accessor property onto an explicit `@JsonProperty("isXxx")`-renamed property the way Jackson 2 did — it treats them as two distinct logical properties, so **both** get serialized. This silently changed the API response shape: `CellDto` (`isAvailable` field, `@JsonProperty("isAvailable")`) started emitting both `"isAvailable"` and a new bare `"available"` key; `ResponseGameplayStateDto` similarly double-emitted `isPlayerActive`+`playerActive`, `isPlayerWinner`+`playerWinner`, `isOpponentReady`+`opponentReady`. Caught via the Phase 0 oracle diff showing unexpected added JSON keys beyond the known-noisy ID/timestamp fields. Per this phase's frozen-API invariant, this required a human decision rather than an improvised fix: resolved by renaming the affected private fields to drop the redundant `is` prefix (`available`, `playerActive`, `playerWinner`, `opponentReady`) while leaving every `@JsonProperty("isXxx")` annotation untouched, restoring the exact pre-Boot-4 single-field wire shape. Lombok's `@Data` still generates an `isXxx()` getter for `boolean` fields regardless of the field's own name, so no call sites outside the two DTOs' own builder calls needed changes — confirmed via full test suite (`ControllerUtilsTest` etc. compiled and passed unchanged) and the clean final oracle diff. Own commit.
  - Full commit list (bisectable, oldest → newest): parent+Java bump (incl. the Lombok `annotationProcessorPaths` fix, bundled since the tree doesn't compile under JDK 25 without it) → springdoc swap → `TestRestTemplate` relocation fix → pre-existing test-payload bug fix → Jackson double-field DTO fix → commons-lang3 bump → frontend-maven-plugin/Node bump → oracle baseline regeneration → this changelog entry.
- **Phase 2.5 (Bug triage) — out-of-turn shot 500→400, narrow fix — complete.** Fixes the bug flagged (not fixed) in the Phase 0 changelog: a shot made by a player who is not the currently active player threw a bare `IllegalStateException` from `GameImpl#makeShot`, which `ValidationExceptionHandler` doesn't recognize (it only maps typed exceptions under `logic.api.exceptions`), so the API surfaced an unstructured `500` instead of the standard `400 ExceptionDto`. This is the one permitted engine-logic change for this phase, scoped to exactly this call site per an explicit prior user decision. Fix: added `PlayerNotActiveException` (extends `IllegalStateException`) under a new `logic/engine/exceptions/` package, thrown in place of the bare `IllegalStateException` at the `!player.isActive()` check in `GameImpl#makeShot`; added the API-level sibling `GamePlayerNotActiveException` under `logic/api/exceptions/`, following the existing `GameStageIsNotCorrectException`-style shape; `GameControllerApiImpl#makeShotByField` now catches `PlayerNotActiveException` in a new clause placed *before* its existing generic `catch (IllegalArgumentException | IllegalStateException ex)`, rethrowing as `GamePlayerNotActiveException`; registered the new exception in `ValidationExceptionHandler`'s `@ExceptionHandler` list and its `errorCode` switch (`"PLAYER_NOT_ACTIVE"`), so the endpoint now returns `400` with a structured `ExceptionDto`. `GameImplTest#testGetWinner`'s regression assertion (line ~376, previously `assertThrows(IllegalStateException.class, ...)` on a same-player double-shot) retyped to `assertThrows(PlayerNotActiveException.class, ...)` so it proves the fix rather than just the superclass; scenario itself unchanged. A structurally identical generic-rewrap pattern exists at ~11 other call sites in `GameControllerApiImpl` (including the wrong-game-stage precondition, which is the closest analog and also still 500s) — deliberately left untouched, per the same prior user decision restricting this phase's one engine-logic exception to only the out-of-turn-shot case. Small in-scope tightening made while already editing `ValidationExceptionHandler#resolveErrorCode`: its `default -> null` branch changed to `default -> throw new IllegalStateException(...)` naming the unmapped exception's class, so that if a future exception type is ever wired into the `@ExceptionHandler` list without a matching switch case, the mistake fails fast instead of silently shipping a `400` with a `null` `errorCode`. `mvn test`: all tests green. Own commit.
- **Phase 3 (Frontend: cleanup outdated code & dependencies) — complete.** Removed the entire Bootstrap/class-component frontend layer down to a clean, still-building baseline ahead of Phase 4 (Vite) and Phase 5 (rebuild); no backend/API/game-logic files touched. Deleted outright (git history is the recovery path, not a quarantine folder, per an explicit prior user decision): all 7 class-component pages under `ui/pages/` (Home/NewGame/JoinGame/Preparation/WaitForPlayers/Gameplay/Finish) plus `ui/pages/common/PagesCommonTypes.ts`; `ui/ApplicationNavigationBar.tsx`; the form elements (`ui/elements/forms/{JoinGameForm,NewGameForm}.tsx`, `forms/common/FormTypes.ts`); the gameplay elements (`ui/elements/gameplay/{Cell,GameplayField}.tsx`, `gameplay/common/GameplayTypes.ts`); the preparation elements (`ui/elements/preparation/{ButtonShip,PrepCell,PrepareField,ShipsList,Status}.tsx`, `preparation/common/PreparationTypes.ts`) — 20 files plus their now-empty directories. Kept untouched, per the plan's explicit keep-list, as Phase 5 references: `logic/ApplicationTypes.ts`, `services/BackendRequestService.ts`, `services/GameBrowserStorage.ts`, `utils/{GameUtils,StringUtils}.ts` — confirmed beforehand to have zero forward dependency into anything deleted. `App.tsx` replaced with a minimal placeholder function component (keeps `react-router-dom`'s `Routes`/`Route`, drops `react-bootstrap`'s `Alert` and all 7 page imports); `index.tsx` edited to drop `import "bootstrap/dist/css/bootstrap.min.css";` only. `package.json`: removed dependencies `bootstrap`, `react-bootstrap`, `react-router-bootstrap`, `prop-types`, `@types/react-router-bootstrap`, and devDependencies `@babel/core`, `@babel/preset-env`, `@babel/preset-react`, `webpack`, `webpack-cli`. The five babel/webpack devDependencies were a judgment call beyond task 3.1's four explicitly-named packages: confirmed unused first (no `.babelrc`/`babel.config.*`/`webpack.config.*` anywhere in `frontend/`, no npm script invoking them; `react-scripts` bundles its own webpack/babel), so removed as inert cruft in the same pass. `package-lock.json` regenerated via `rm -rf node_modules && npm install`. Task 3.3 (delete CRA-specific files/config) had almost nothing to do now — this repo's CRA scaffolding was already minimal (no `.env*`/`reportWebVitals.ts`/`setupTests.ts`/`react-app-env.d.ts`, no Bootstrap reference in `public/index.html`) — moving `index.html` and reconfiguring `tsconfig.json` remain Phase 4's job (tasks 4.2, 4.6) per the plan. Full inventory with per-file rationale: `docs/redesign/artifacts/phase-3-removed-inventory.md`. Verified: `npm ls bootstrap react-bootstrap react-router-bootstrap prop-types` shows the three Bootstrap packages absent (`prop-types` remains only as a transitive dep of `react-scripts`'s own eslint config, not a direct dependency); `grep` for all four removed package names across `frontend/src` and `package.json` returns zero matches; `npm run build` (still `react-scripts build` — Vite arrives in Phase 4) compiles cleanly, gzipped bundle shrank ~56 KB JS / ~28 KB CSS from dropping Bootstrap; `mvn clean install` from repo root green end-to-end (118 tests, 0 failures), confirming the `frontend-maven-plugin` → `maven-resources-plugin` boundary still holds with the trimmed frontend. Own commit.
- **Phase 4 (Frontend: Vite + dependency install + configuration) — complete.** Swapped `react-scripts` (CRA) for Vite across the toolchain — no UI/behavior change; `App.tsx`, `index.tsx`, `logic/`, `services/`, `utils/` content untouched, `pom.xml`/`src/main/java` untouched. **`build.outDir` decision:** set to `'build'` (not `dist`) in `vite.config.ts` specifically so `pom.xml`'s existing `maven-resources-plugin` copy paths (`frontend/build` → `target/classes/static`, `frontend/build/index.html` → `target/classes/templates`) needed zero changes — the alternative (`dist` + editing `pom.xml`) was rejected as out of scope for a frontend-only phase. Added `frontend/vite.config.ts` (`@vitejs/plugin-react`, dev-server `proxy['/api'] → http://localhost:8080`, `outDir: 'build'`); moved `index.html` from `frontend/public/` to the frontend root with a `<script type="module" src="/src/index.tsx">` entry; added `frontend/src/vite-env.d.ts`; reconfigured `tsconfig.json` (ES2020 target, `moduleResolution: "bundler"`, `types: ["vite/client"]`, `strict` kept); new npm scripts `dev`, `build` (`tsc && vite build`), `preview`, `test` (`vitest run`), `test:e2e` (`playwright test --pass-with-no-tests`); removed `react-scripts`, `@types/jest`, `web-vitals`, and the CRA `"proxy"` field. Final installed versions (live-verified against npm at implementation time): `vite` **8.1.4**, `@vitejs/plugin-react` (matching major), `react`/`react-dom` **19.2.x**, `typescript` **5.9.3**, `axios`/`axios-retry`/`copy-to-clipboard` latest majors, `vitest` **4.1.10**, `@testing-library/react` **16.3.2**, `jest-dom` **6.9.1**, `user-event` **14.6.1**, `jsdom` **29.1.1**, `@playwright/test` **1.61.1**, plus `i18next`/`react-i18next`/`i18next-browser-languagedetector` installed (per SPEC §5.2) but not wired — Phase 5's job. Scaffolded `vitest.config.ts` (jsdom env, RTL setup, `passWithNoTests: true`) and `playwright.config.ts` (chromium project, dev-server `webServer` block); both `npm run test` and `npm run test:e2e` exit 0 with zero tests.
  - **Deviation — `react-router-dom` pinned to 7.18.1, not the spec-target 8.x.** Live-checked against the npm registry: v8 is not published (latest is 7.18.1). Not an implementer error — the ticket brief pre-authorized picking the newest compatible version and flagging it. Phase 5 (routing) should re-check for a v8 release before building the router layer.
  - **Deviation — `eslint` installed but initially left unwired, then closed as a same-phase follow-up.** SPECIFICATION.md §5.2 (line 142) lists "eslint + config" as a Phase-4 dependency-install item, but neither `IMPLEMENTATION_PLAN.md`'s Phase 4 task list (4.1-4.8) nor this ticket's original file-mapping table named an eslint config file — the implementer correctly declined to expand scope unilaterally and flagged it as a ⚠️ item in task review instead of silently dropping or silently adding it. Given the human decision to close it in-phase: added `frontend/eslint.config.js` (flat config, required by `eslint@10.7.0`) composing `@eslint/js` recommended + `typescript-eslint` recommended + `eslint-plugin-react-hooks` (`configs.flat["recommended-latest"]` — the legacy-shaped `configs["recommended-latest"]` is incompatible with ESLint 10's flat-config plugin-object requirement) + `eslint-plugin-react-refresh`'s `only-export-components` rule; added `"lint": "eslint ."` script. Fixing the 3 lint errors this surfaced required touching `frontend/src/utils/GameUtils.ts` — normally out of scope for this phase — but the fix is exactly `let` → `const` on three variables that are each assigned once and read once (independently verified via `grep`: no other references anywhere in `frontend/src`), i.e. mechanically behavior-neutral, not a logic change. Separate follow-up commit, not amended onto the toolchain-swap commit.
  - Two rounds of independent task review (spec compliance + code quality) both returned Approved with zero Critical/Important findings; Minor notes carried forward: `test:e2e`'s `--pass-with-no-tests` flag should be revisited once Phase 5/6 add real e2e specs (a fully-filtered/skipped suite would still silently report success), and `eslint.config.js`'s `ignores: ["node", ...]` entry is intentional (matches this repo's `frontend-maven-plugin` Node-binary-cache dir, not a `node_modules` typo) but could use an inline comment if the file is touched again.
  - Verification (orchestrator, independent of the implementer's own runs): `npm install`, `npm run build` (emits `frontend/build/index.html` + hashed assets), `npx tsc --noEmit` (clean), `npx vitest run` (0 tests, green), `npm run test:e2e` (0 tests, green), `npm run dev` (dev server serves the shell at `:5173`, `/api` proxy config confirmed pointed at `:8080`), `npm ls react-scripts` (absent), `git diff --stat` against the Phase 3 baseline (frontend-only — no `pom.xml`, no `src/main/java`). From repo root: `mvn clean install` green end-to-end (118/118 tests), `target/classes/static` and `target/classes/templates/index.html` populated from the Vite build, and the packaged jar (`java -jar target/battleship-0.0.1-SNAPSHOT.jar`) serves the unchanged Phase 3 placeholder page with correctly-linked hashed JS/CSS assets at `http://localhost:8080`. Noted and reverted as expected pre-existing noise (not caused by this phase): `mvn install` re-runs `ApiBaselineOracleCaptureTest`, which regenerates `docs/redesign/artifacts/api-baseline/*` fixtures with fresh non-deterministic UUIDs/timestamps on every run. Commits: `262921b` (Vite toolchain + dependency bump), `5a93fde` (eslint config follow-up).
