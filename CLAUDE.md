# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Battleship game (educational project) — Java 25 + Spring Boot 4.1.0 REST/MVC backend, Vite + React 19 + TypeScript frontend in `frontend/`, bundled together into one runnable JAR. A rewrite of a prior [Python version](https://github.com/sanyokkua/battleship_py).

**This repo is mid-redesign.** `docs/redesign/` (start with `docs/redesign/README.md`) is the plan this branch (`feature/redesign-v2`) has been executing — all 11 phases are implemented on this branch (not yet merged to `master`). Read it before making backend or frontend changes — see "Active redesign" below for the ground rules that still constrain work here.

## Build, Run, Test

- Full build (backend + frontend, bundled into JAR): `mvn clean install`
- Run the app: `mvn spring-boot:run` — serves at `http://localhost:8080`, Swagger UI at `/swagger-ui/index.html`
- Backend tests (all): `mvn test`
- Single test class: `mvn test -Dtest=ClassName`
- Single test method: `mvn test -Dtest=ClassName#methodName`
- Frontend, from `frontend/`: `npm run dev` (Vite dev server against a running backend), `npm run dev:mock` (Vite dev server against `MockGameAdapter`, no backend needed), `npm run build` (`tsc && vite build`), `npm run preview`, `npm run test` (Vitest), `npm run test:coverage`, `npm run test:e2e` (Playwright), `npm run test:e2e:live` (Playwright against a live server), `npm run lint`
- Container: `docker compose up`, or `docker build -t battleship . && docker run -p 8080:8080 battleship` (Podman needs `--format docker` on the build command)

`mvn clean install` runs `frontend-maven-plugin` (installs Node v24.18.0 — pinned in `pom.xml`'s plugin config, not in `frontend/package.json`, which has no `engines` field — and runs `npm run build` in `frontend/`) during the `compile` phase, then `maven-resources-plugin` copies `frontend/build` into `target/classes/static` and `index.html` into `target/classes/templates`. A full Maven build always builds the frontend too — there's no way to build backend-only and skip the frontend step short of editing the POM.

## Backend architecture

Base package `ua.kostenko.battleship.battleship`, layered as REST Controller → API/Service → Engine → Persistence:

- `logic.engine` — pure, framework-agnostic game engine: `Game`/`GameImpl` (state machine driven by the `GameStage` enum: INITIALIZED → WAITING_FOR_PLAYERS → PREPARATION → IN_GAME → FINISHED), `FieldManagement`/`FieldManagementImpl` (per-player board, ship placement, shot resolution), immutable records (`Ship`, `Cell`, `Coordinate`, `GameState`), and pluggable `GameEditionConfiguration` rule sets (Ukrainian, Milton Bradley) under `logic.engine.config`.
- `logic.api` — `GameControllerApi`/`GameControllerApiImpl`, `ValidationUtils`, `IdGenerator`, typed exceptions. This is the boundary between web and engine; no Spring MVC types leak below it.
- `logic.persistence` — `Persistence`/`InMemoryPersistence`. In-memory, single-instance only — no database, by design (see redesign scope below).
- `web.controllers.rest` — three REST controllers (`GameSessionCommonRestController`, `GameplayRestController`, `PreparationRestController`) under `/api/v2/game`; `web.api.dtos` grouped by feature (`session`, `preparation`, `gameplay`, `entities`).
- `web.exceptions`, `web.config` — exception handlers and Spring bean configuration.

Conventions used throughout: interface + `*Impl` pairs (`Game`/`GameImpl`, `FieldManagement`/`FieldManagementImpl`, `IdGenerator`/`IdGeneratorImpl`), constructor DI via Lombok `@RequiredArgsConstructor`, immutable records for value objects, DTOs built via static `from(...)` factory methods.

Backend tests (JUnit 5 + Mockito + AssertJ + MockMvc via `spring-boot-starter-test`) mirror the main package layout under `src/test/java/...`, and now include REST-controller-level integration tests (`GameSessionCommonRestControllerTest`, `GameplayRestControllerTest`, `PreparationRestControllerTest`).

## Frontend (current state, shipped v2)

`frontend/` is a Vite + React 19 + TypeScript app: function components + hooks, `setInterval`-based polling (Preparation every 3s, Gameplay every 5s — unchanged from pre-redesign), a custom CSS design system under `design/` (Bootstrap dropped), and i18next (`i18n/`, `en`/`uk` locales) for all UI copy. Key structure:

- `adapters/` — the `GameAdapter` port and its two implementations: `HttpGameAdapter` (real backend calls) and `MockGameAdapter` (used by `npm run dev:mock` and tests). Widgets and screens never call the network directly — everything goes through this port.
- `screens/` — one component per route: `HomeScreen`, `NewGameScreen`, `JoinGameScreen`, `WaitScreen`, `PreparationScreen`, `GameplayScreen`, `ResultsScreen`.
- `widgets/` — reusable feature UI grouped by area: `board/` (Board, BoardCell, BoardTabs, Legend), `preparation/` (ShipTray, ShipItem, DirectionToggle), `gameplay/` (PlayerCard, TurnBanner), `feedback/` (Toast, ConfirmDialog, error mapping), `layout/` (AppBar, LoadingView).
- `hooks/` — `usePreparation`, `useGameplay`, `useWaitRoom`, `usePolling`, `useSessionGuard` encapsulate polling/state logic per screen.
- `routing/` — `AppRoutes` + `StageGuard` (redirects based on the session's `GameStage`).
- `design/` — the CSS design system: tokens (`tokens.css`, `base.css`) and components (`Button`, `Card`, `Field`, `Input`, `LoadingBar`, `ModeCard`, `Pill`, `StepTracker`).
- `i18n-support/` — edition/ship-type name lookups feeding the i18next translations.

## Active redesign (branch `feature/redesign-v2`)

`docs/redesign/` is the plan this branch executed — all 11 phases are implemented here (this branch is not yet merged to `master`); read `docs/redesign/README.md` first — it indexes `SPECIFICATION.md`, `MOCKUP.html`, `IMPLEMENTATION_PLAN.md` (see its changelog for what shipped per phase), `TESTING_PLAN.md`, `PHASE_BOOTSTRAP_PROMPT.md`. The following ground rules were invariant throughout implementation and remain invariant for any further work on this branch:

- **Backend REST API is frozen** — no changes to paths, verbs, request/response DTOs, or status codes, with one documented additive exception: `ExceptionDto` gained a stable `errorCode` field.
- **Game engine logic is frozen** — the only allowed exception is a documented, regression-tested bug fix discovered during Phase 2 test-gap work.
- **Frontend widgets never call the network directly** — all backend access goes through the `GameAdapter` port (`HttpGameAdapter` for real use, `MockGameAdapter` for tests and `dev:mock`). Preserve this pattern for any future frontend work.
- Visuals follow `MOCKUP.html`; data/behavior follow `SPECIFICATION.md` and the existing backend API.
- The app must build at every phase boundary; each phase passed its `TESTING_PLAN.md` gate before the next began.

`docs/redesign/PHASE_BOOTSTRAP_PROMPT.md` is a reusable plan-mode prompt that was used to start each of the 11 phases — keep it as the template for any further phase-shaped work on this branch.

## Available Claude Code skills

Two project-scoped skills are installed under `.claude/skills/`:

- **project-navigator** — read-only fast orientation (stack, structure, entry points, config, how pieces connect). Use at the start of any session needing a refresher, or when asked "what is this project" / "where does X live" / "how do I run this."
- **project-documentation** — generates committed docs (`docs/index.md`, optional `docs/architecture.md`, `docs/diagrams/`) and updates `README.md`, or a scratch `.agent-docs/` folder. Use when asked to "document this project" or "write architecture docs." **Never** targets or overwrites `docs/redesign/` — that folder is the frozen v2 spec described above, not general project documentation.

Both are self-contained (bundled reference docs, scripts, templates) and read-only/non-destructive except for their own documented output paths.
