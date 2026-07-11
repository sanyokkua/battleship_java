# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Battleship game (educational project) — Java 17 + Spring Boot 3.3.5 REST/MVC backend, React + TypeScript (CRA) frontend in `frontend/`, bundled together into one runnable JAR. A rewrite of a prior [Python version](https://github.com/sanyokkua/battleship_py).

**This repo is mid-redesign.** `docs/redesign/` (start with `docs/redesign/README.md`) is the single source of truth for an in-progress v2 modernization tracked on this branch. Read it before making backend or frontend changes — see "Active redesign" below.

## Build, Run, Test

- Full build (backend + frontend, bundled into JAR): `mvn clean install`
- Run the app: `mvn spring-boot:run` — serves at `http://localhost:8080`, Swagger UI at `/swagger-ui/index.html`
- Backend tests (all): `mvn test`
- Single test class: `mvn test -Dtest=ClassName`
- Single test method: `mvn test -Dtest=ClassName#methodName`
- Frontend only, from `frontend/`: `npm start` (dev server), `npm run build`, `npm test`

`mvn clean install` runs `frontend-maven-plugin` (installs Node v16.17.0, runs `npm run build` in `frontend/`) during the `compile` phase, then `maven-resources-plugin` copies `frontend/build` into `target/classes/static` and `index.html` into `target/classes/templates`. A full Maven build always builds the frontend too — there's no way to build backend-only and skip the frontend step short of editing the POM.

## Backend architecture

Base package `ua.kostenko.battleship.battleship`, layered as REST Controller → API/Service → Engine → Persistence:

- `logic.engine` — pure, framework-agnostic game engine: `Game`/`GameImpl` (state machine driven by the `GameStage` enum: INITIALIZED → WAITING_FOR_PLAYERS → PREPARATION → IN_GAME → FINISHED), `FieldManagement`/`FieldManagementImpl` (per-player board, ship placement, shot resolution), immutable records (`Ship`, `Cell`, `Coordinate`, `GameState`), and pluggable `GameEditionConfiguration` rule sets (Ukrainian, Milton Bradley) under `logic.engine.config`.
- `logic.api` — `GameControllerApi`/`GameControllerApiImpl`, `ValidationUtils`, `IdGenerator`, typed exceptions. This is the boundary between web and engine; no Spring MVC types leak below it.
- `logic.persistence` — `Persistence`/`InMemoryPersistence`. In-memory, single-instance only — no database, by design (see redesign scope below).
- `web.controllers.rest` — three REST controllers (`GameSessionCommonRestController`, `GameplayRestController`, `PreparationRestController`) under `/api/v2/game`; `web.api.dtos` grouped by feature (`session`, `preparation`, `gameplay`, `entities`).
- `web.exceptions`, `web.config` — exception handlers and Spring bean configuration.

Conventions used throughout: interface + `*Impl` pairs (`Game`/`GameImpl`, `FieldManagement`/`FieldManagementImpl`, `IdGenerator`/`IdGeneratorImpl`), constructor DI via Lombok `@RequiredArgsConstructor`, immutable records for value objects, DTOs built via static `from(...)` factory methods.

Backend tests (JUnit 5 + Mockito + AssertJ + MockMvc via `spring-boot-starter-test`) mirror the main package layout under `src/test/java/...`. There are currently no REST-controller-level integration tests — see redesign Phase 2 below.

## Frontend (current state, pre-redesign)

`frontend/` is a Create React App (`react-scripts` 5.0.1) TypeScript app: class components, `setInterval`-based polling (Preparation every 3s, Gameplay every 5s), direct axios calls from `services/BackendRequestService.ts`, `localStorage` via `services/GameBrowserStorage.ts`, Bootstrap/react-bootstrap for styling. Pages: `HomePage`, `NewGamePage`, `JoinGamePage`, `WaitForPlayersPage`, `PreparationPage`, `GameplayPage`, `FinishPage`. This entire layer is being replaced — see below.

## Active redesign (branch `feature/redesign-v2`)

`docs/redesign/` is the authoritative plan for an in-progress modernization; read `docs/redesign/README.md` first — it indexes `SPECIFICATION.md`, `MOCKUP.html`, `IMPLEMENTATION_PLAN.md`, `TESTING_PLAN.md`, `PHASE_BOOTSTRAP_PROMPT.md` and lists the 11 execution-order phases. Ground rules that apply to every phase:

- **Backend REST API is frozen** — no changes to paths, verbs, request/response DTOs, or status codes.
- **Game engine logic is frozen** — the only allowed exception is a documented, regression-tested bug fix discovered during Phase 2 test-gap work.
- **Frontend widgets never call the network directly** — all backend access goes through a `GameAdapter` port/interface (`HttpGameAdapter` for real use, `MockGameAdapter` for tests).
- Visuals follow `MOCKUP.html`; data/behavior follow `SPECIFICATION.md` and the existing backend API.
- The app must build at every phase boundary; each phase must pass its `TESTING_PLAN.md` gate before the next begins.

Target stack per the spec: Java 25 + Spring Boot 4.1.0, Vite + React 19 + TypeScript 5 (replacing CRA), a custom CSS design system (dropping Bootstrap), function components + hooks, Vitest + React Testing Library + Playwright for frontend tests, Docker/Podman packaging on `eclipse-temurin:25-jre`. Explicitly out of scope: no WebSockets/SSE, no new gameplay features, no database, no change to the existing polling model.

`docs/redesign/PHASE_BOOTSTRAP_PROMPT.md` is a reusable plan-mode prompt for starting any one of the 11 phases — use it as the template when picking up redesign work.
