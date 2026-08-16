# battleship_java Constitution

This constitution governs feature work in the `battleship_java` repository. It
complements `AGENTS.md`; when the two documents describe the same workflow,
the repository instructions remain the operational source of truth.

## Project Identity

`battleship_java` is an educational two-player Battleship web service. It is a
single deployable Spring Boot application with a React/Vite single-page
frontend bundled into the runnable JAR.

- Backend language and runtime: Java 25, Spring Boot 4.1.0, Maven.
- Frontend language and runtime: TypeScript, React 19, Vite, npm.
- Communication: REST endpoints under `/api/v2/game` and Server-Sent Events.
- State: in-memory game sessions backed by `ConcurrentHashMap`; no database or
  external service is part of the current product boundary.
- Deployment unit: one packaged JAR, with Docker/Compose support.

## Core Principles

### I. Runtime Behavior Is Authoritative

Source code, configuration, tests, and generated artifacts define implemented
behavior. `README.md`, `docs/index.md`, and `docs/architecture.md` provide
context but do not silently override runtime evidence. New behavior MUST have
written acceptance criteria before implementation.

### II. Preserve the Layered Boundary

Backend changes MUST preserve the direction:

`web controllers and DTOs -> logic API -> engine -> persistence`.

Spring MVC types belong in the web layer. `logic/api/impl`, `logic/engine`, and
`logic/persistence` MUST remain usable without controller-layer response or
request types. Frontend screens, widgets, and hooks MUST use the
`GameAdapter` boundary through `GameAdapterContext`; they MUST NOT call Axios,
`EventSource`, or concrete adapters directly.

### III. Treat API and UI as One Contract

An end-to-end feature MUST identify its backend endpoint/DTO effects and its
frontend adapter, hook, screen, widget, routing, or localization effects.
REST and SSE behavior MUST remain consistent between `HttpGameAdapter`,
`MockGameAdapter`, backend controllers, DTOs, and the generated OpenAPI
document. API changes MUST regenerate and review `docs/openapi.json`.

### IV. Prove Behavior at the Right Level

Behavior changes MUST add or update focused tests in the existing test style:

- Java unit, MVC, concurrency, and application tests under `src/test/java`.
- Vitest/Testing Library tests co-located with frontend sources under
  `frontend/src`.
- Mock-browser Playwright tests under `frontend/e2e` for user journeys that do
  not require the real server.
- Live Playwright tests under `frontend/e2e-live` when the packaged JAR or
  real REST/SSE integration is material.

Tests complement, but do not replace, inspection of generated artifacts and
live behavior when the change affects packaging, routing, browser interaction,
SSE, or deployment.

### V. Keep Delivery Reproducible

The repository gate is `scripts/verify.sh`. A feature is not complete until
the gate is green or a specific capability limitation is recorded. The gate
uses Maven to install the pinned frontend runtime (`Node v24.18.0`) before
running frontend tests, lint, mock E2E, and live packaged-JAR E2E. No feature
work may bypass hooks with `--no-verify` or force-pushes.

### VI. Keep State and Dependencies Within Scope

The current service is intentionally single-instance and in-memory. A feature
MUST NOT introduce a database, authentication layer, external service, queue,
or cache as an incidental implementation detail. Any such architectural change
requires an explicit specification and decision record.

## Code Boundaries and Naming

| Area | Location | Boundary and naming rule |
|---|---|---|
| Spring entry/configuration | `src/main/java/.../BattleshipApplication.java`, `.../web/config` | Spring bootstrap and bean wiring only |
| Application API | `.../logic/api` | Interfaces, validation, application events, and API implementations; implementation classes use `Impl` |
| Game engine | `.../logic/engine` | Game state, rules, models, records, enums, and engine utilities; no web DTOs |
| Persistence | `.../logic/persistence` | Persistence interface and in-memory implementation; no controller concerns |
| Web API | `.../web/api` | Controller contracts and DTOs |
| Web delivery | `.../web/controllers`, `.../web/exceptions`, `.../web/sse` | REST/MVC routing, error translation, and SSE delivery |
| Frontend entry/config | `frontend/src/index.tsx`, `frontend/src/App.tsx` | React bootstrap, providers, and top-level composition |
| Frontend integration | `frontend/src/adapters`, `frontend/src/services` | Adapter port, HTTP/mock implementations, Axios calls, and browser storage |
| Frontend behavior | `frontend/src/hooks`, `frontend/src/logic`, `frontend/src/routing` | Lifecycle, derived rules, routing guards, and application types |
| Frontend UI | `frontend/src/screens`, `frontend/src/widgets`, `frontend/src/design` | Screens, reusable widgets, tokens, and component CSS |

Java packages use lowercase names; Java types use PascalCase, records/enums
follow the existing type names, and DTOs use the existing `Param*` and
`Response*` conventions. Java tests use `*Test.java`.

Frontend component files use PascalCase, hooks use `useX`, and services and
utilities use camelCase. Component styles remain co-located as CSS files.
Vitest tests use `*.test.ts` or `*.test.tsx`; Playwright tests use `*.spec.ts`.
No additional naming convention should be invented where the repository has
no consistent precedent.

The documented git protocol is `master` as protected, `feature/<slug>` as the
parent branch, and `feature/<slug>--<task>` as the task branch. Keep one task
per branch and one task commit, as required by `AGENTS.md`.

## Testing and Quality Gates

The following existing thresholds and checks are part of the delivery
contract:

- JaCoCo enforces an 80% instruction coverage bundle threshold and the
  configured 100% line threshold for the selected REST/error packages.
- Vitest coverage thresholds apply to frontend adapters, hooks, and utilities;
  screens/widgets are protected by the repository's presence and component
  tests.
- `mvn clean verify` builds the frontend, runs backend verification, starts the
  application for integration/OpenAPI generation, and stops it.
- `scripts/verify.sh` checks OpenAPI drift, prepends `frontend/node` to `PATH`,
  runs frontend test/lint, mock-browser E2E, and live packaged-JAR E2E.
- `python3 scripts/sync-agent-files.py --check` and the pre-push hook MUST
  remain green when agent configuration is touched.

When Maven changes `docs/openapi.json`, the diff MUST be inspected and either
committed as the intentional API artifact or resolved before completion.

## Specification and Workflow Governance

Feature work follows the six phases in `AGENTS.md`: ORIENT, SPEC, PLAN, BUILD,
VERIFY, and CLOSE. No feature code is written before the specification and
plan are complete.

Spec Kit feature artifacts and delivery records live together under
`specs/<###-feature>/` because the installed scripts resolve that directory.
The standard records are `spec.md`, `plan.md`, `tasks.md`, and any applicable
`research.md`, `data-model.md`, `quickstart.md`, or `contracts/` artifacts.
Keep acceptance criteria, implementation planning, tasks, and verification
evidence aligned within that feature directory.

Constitution amendments MUST be explicit, reviewed, and reflected in the
templates or workflow that depend on them. Existing user changes MUST be
preserved. A green focused test does not waive the full gate or artifact review
when the feature affects those surfaces.

**Version**: 1.0.0 | **Ratified**: 2026-08-15 | **Last Amended**: 2026-08-15
