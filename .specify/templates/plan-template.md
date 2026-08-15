# Implementation Plan: [FEATURE]

**Branch**: `[feature/<slug>]` | **Date**: [DATE] | **Spec**: [link to `specs/[###-feature]/spec.md`]

**Input**: Feature specification from `specs/[###-feature]/spec.md`

This plan MUST be dependency-ordered. Follow `AGENTS.md`: no feature code
before SPEC and PLAN, use task branches under the feature branch, keep one
task per commit, preserve unrelated changes, and leave final merge to the user.

## Summary

[Restate the primary requirement and the smallest vertical slice that proves it.]

## Technical Context

**Backend Language/Version**: Java 25

**Frontend Language/Version**: TypeScript with React 19

**Build Tools**: Maven; `frontend-maven-plugin` installs Node v24.18.0 and runs npm; Vite builds to `frontend/build`

**Primary Dependencies**: Spring Boot Web MVC, Thymeleaf, springdoc OpenAPI, Lombok, Axios, React Router, i18next, Vitest, Testing Library, Playwright

**Storage**: In-memory `ConcurrentHashMap` through `logic/persistence`; no database in the current boundary

**Testing**: JUnit/Spring Boot tests, JaCoCo, Vitest/Testing Library, ESLint, mock-browser Playwright, live packaged-JAR Playwright

**Target Platform**: Browser client served by a Spring Boot JAR on port 8080; Docker/Compose supported

**Project Type**: Full-stack web service with bundled React SPA

**Performance Goals**: [Feature-specific measurable target, or `NEEDS CLARIFICATION`]

**Constraints**: Preserve the single-JAR packaging, layered backend, `GameAdapter`
port, in-memory session model, REST/SSE contracts, and repository verification
gate unless the feature specification explicitly changes them.

**Scale/Scope**: [Affected controllers, engine rules, screens, widgets, DTOs,
tests, and generated artifacts]

## Constitution Check

*GATE: Must pass before implementation. Re-check after design and before final verification.*

- [ ] Runtime authority is identified in source/configuration/tests; documentation is treated as context.
- [ ] Backend dependency direction remains `web -> logic API -> engine -> persistence`.
- [ ] Frontend network access remains behind `GameAdapter` and its context.
- [ ] REST/SSE, DTO, mock-adapter, and OpenAPI effects are identified.
- [ ] In-memory persistence, authentication scope, and external-service scope are unchanged or explicitly decided.
- [ ] Focused Java, Vitest, and browser tests are planned for changed behavior.
- [ ] Generated artifact and documentation effects are listed.
- [ ] Final evidence includes `scripts/verify.sh` and any capability limitation.

## Existing Project Structure

```text
src/main/java/ua/kostenko/battleship/battleship/
├── logic/api/                 # application interfaces, validation, events, implementations
├── logic/engine/              # game state, rules, models, utilities
├── logic/persistence/         # Persistence and InMemoryPersistence
└── web/
    ├── api/                   # controller contracts and DTOs
    ├── config/                # Spring beans/OpenAPI configuration
    ├── controllers/           # MVC index and REST controllers
    ├── exceptions/            # REST exception handlers
    └── sse/                   # session event broadcasting

src/test/java/                 # Java unit, MVC, concurrency, and application tests

frontend/src/
├── adapters/                  # GameAdapter, HttpGameAdapter, MockGameAdapter
├── services/                  # HTTP requests and browser storage
├── hooks/                     # session, preparation, gameplay, and event lifecycle
├── logic/                     # application types and pure helpers
├── routing/                   # routes and stage guards
├── screens/                   # user-facing route screens
├── widgets/                   # reusable UI components
├── design/                    # CSS tokens, base styles, component styles
└── i18n/                      # English/Ukrainian translations

frontend/e2e/                  # mock-adapter browser journeys
frontend/e2e-live/             # packaged-JAR browser journeys
```

**Structure Decision**: [Name the selected backend/frontend boundary and list
the exact files to change. Do not retain unused generic project options.]

## Contract and Data Flow Plan

### Backend/API

- Route/controller: [exact controller, verb, path, status codes].
- DTO/OpenAPI: [request/response/event shape and generated artifact impact].
- Application API: [interface and implementation methods].
- Engine: [state transition, validation, rules, and per-session locking].
- Persistence: [state snapshot impact or `none`].
- Events/SSE: [publisher, broadcaster, payload, reconnect/fallback behavior or `none`].

### Frontend

- Adapter/service: [GameAdapter and HTTP/mock methods].
- Hook/state: [lifecycle, storage, polling/fallback, or SSE behavior].
- UI: [screens/widgets/design/i18n/routing files].
- Browser behavior: [mock and live E2E surfaces].

## Implementation Phases

### Phase 0: Research and Baseline

- Inspect the current source, tests, generated OpenAPI, and worktree.
- Record existing gate status separately from new feature evidence.
- Resolve API, state, browser, and packaging decisions before implementation.

### Phase 1: Backend Contract and Engine

- Implement or change DTOs, web contracts, controllers, application API, and
  engine/persistence behavior in dependency order.
- Add focused Java tests, including MVC or concurrency tests where relevant.

### Phase 2: Frontend Integration and UI

- Update `ApplicationTypes`, `GameAdapter`, HTTP/mock adapters, services, hooks,
  screens, widgets, styles, routing, and translations as required.
- Add co-located Vitest/Testing Library coverage for changed behavior.

### Phase 3: Cross-Layer and Browser Verification

- Update mock and live Playwright journeys when the user-visible or real-server
  contract changes.
- Regenerate and review `docs/openapi.json` for API changes.
- Verify the frontend remains bundled into the packaged JAR.

### Phase 4: Documentation and Close

- Update affected README or `docs/` architecture/operations material.
- Record acceptance criterion to proving test/command mapping in the feature's plan, tasks, or quickstart artifacts.
- Run the full verification gate and report remaining limitations.

## Verification Plan

| Concern | Command/evidence |
|---|---|
| Backend focused tests | `mvn -Dtest=<focused-test-class> test` |
| Frontend focused tests | `npm --prefix frontend run test -- <pattern>` using the pinned runtime when required |
| Frontend lint | `npm --prefix frontend run lint` |
| Mock browser | `npm --prefix frontend run test:e2e` |
| Live packaged JAR | `npm --prefix frontend run test:e2e:live` after packaging |
| Full gate | `scripts/verify.sh` |
| Agent configuration | `python3 scripts/sync-agent-files.py --check` |
| API artifact | Review `git diff -- docs/openapi.json` after Maven verification |

## Complexity and Risk Factors

- API changes require DTO, controller, adapter, mock, OpenAPI, and live-E2E coordination.
- In-memory state changes may affect session isolation, concurrency, and process lifetime.
- SSE changes require subscription cleanup, reconnect/fallback, and browser evidence.
- Frontend changes may affect both Vite development/mock mode and the packaged JAR.
- Maven controls the pinned Node/npm runtime; host Node results are not authoritative for the repository gate.
- Localhost binding or browser capability failures MUST be reported separately from product failures.

## Complexity Tracking

> Fill this only when the Constitution Check has a genuine, explicitly approved violation.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| [None or explicit violation] | [reason] | [evidence] |
