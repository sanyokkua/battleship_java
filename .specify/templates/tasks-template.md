---
description: "Dependency-ordered task template for the battleship_java full-stack service"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `specs/[###-feature-name]/`

**Prerequisites**: `spec.md` and `plan.md` are required. Use `research.md`,
`data-model.md`, `contracts/`, and `quickstart.md` when the plan creates them.

## Task Rules

- Use the format `[ID] [P?] [Story] Description`.
- `[P]` means the task can run in parallel without shared-file or dependency conflicts.
- `[Story]` maps the task to `US1`, `US2`, or another independently testable story.
- Every task MUST name exact repository paths and its proving test or command.
- Tests are required for changed behavior; use the existing Java, Vitest, and Playwright suites.
- Keep one task per task branch and one task commit, following `AGENTS.md`.
- Do not mark a task complete from a checkbox alone; verify the live behavior and record evidence.

## Path Conventions

| Concern | Paths |
|---|---|
| Backend application | `src/main/java/ua/kostenko/battleship/battleship/` |
| Backend tests | `src/test/java/ua/kostenko/battleship/battleship/` |
| Frontend application | `frontend/src/` |
| Frontend unit/component tests | Co-located `frontend/src/**/*.test.ts` and `frontend/src/**/*.test.tsx` |
| Mock browser tests | `frontend/e2e/` |
| Live browser tests | `frontend/e2e-live/` |
| API artifact | `docs/openapi.json` |
| Human-facing docs | `README.md`, `docs/index.md`, `docs/architecture.md`, `docs/operations.md` when present |

## Phase 1: Baseline and Setup

**Purpose**: Confirm the starting state and prepare only the files required by
the approved plan.

- [ ] T001 Record `git status --short --branch`, affected source paths, and the existing verification baseline
- [ ] T002 [P] Update the feature specification or plan artifacts if an approved clarification changes scope
- [ ] T003 [P] Add or update the affected API contract/data model artifact under `specs/[###-feature-name]/`

## Phase 2: Backend Contract and Foundation

**Purpose**: Implement the backend boundary and engine prerequisites before
frontend work that consumes them.

- [ ] T004 [P] Add or update Java DTOs and web API contracts in `src/main/java/.../web/api`
- [ ] T005 Add or update application API interfaces/implementations in `src/main/java/.../logic/api`
- [ ] T006 Add or update engine rules, state transitions, validation, or models in `src/main/java/.../logic/engine`
- [ ] T007 Add or update persistence behavior in `src/main/java/.../logic/persistence` only when required by the approved spec
- [ ] T008 Add focused Java unit/MVC/concurrency tests under `src/test/java` for the changed backend behavior
- [ ] T009 [P] Update REST/SSE exception mapping or event broadcasting when required by the contract

**Checkpoint**: Backend focused tests pass and the API/engine contract is
stable enough for frontend integration.

## Phase 3: Frontend Adapter and State Integration

**Purpose**: Connect the frontend to the approved backend contract through the
existing adapter boundary.

- [ ] T010 Update shared application types in `frontend/src/logic/ApplicationTypes.ts`
- [ ] T011 Update `frontend/src/adapters/GameAdapter.ts` and `GameAdapterContext.tsx` for the contract change
- [ ] T012 Update `frontend/src/adapters/HttpGameAdapter.ts` and `frontend/src/services/BackendRequestService.ts`
- [ ] T013 Keep `frontend/src/adapters/MockGameAdapter.ts` behaviorally aligned for mock development and tests
- [ ] T014 Update affected hooks, browser storage, routing, or lifecycle logic under `frontend/src/hooks`, `services`, `routing`, or `logic`
- [ ] T015 Add/update co-located Vitest/Testing Library tests in `frontend/src/**/*.test.ts{,x}`

## Phase 4: Screens, Widgets, and Browser Journeys

**Purpose**: Deliver the user-visible behavior and prove it in a browser where
the feature crosses the UI boundary.

- [ ] T016 Update affected screens under `frontend/src/screens`
- [ ] T017 [P] Update reusable widgets and design styles under `frontend/src/widgets` and `frontend/src/design`
- [ ] T018 [P] Update English/Ukrainian resources under `frontend/src/i18n` and i18n support when copy changes
- [ ] T019 Add/update mock-adapter Playwright coverage under `frontend/e2e`
- [ ] T020 Add/update live packaged-JAR Playwright coverage under `frontend/e2e-live` when real REST/SSE or packaging behavior is material

## Phase 5: Integration, Artifacts, and Verification

**Purpose**: Verify the complete packaged application and record evidence.

- [ ] T021 Regenerate and review `docs/openapi.json` when backend API behavior changed
- [ ] T022 [P] Update affected README or `docs/` architecture/operations documentation
- [ ] T023 Run focused backend tests with `mvn -Dtest=<focused-test-class> test`
- [ ] T024 Run frontend tests and lint using the repository-approved runtime: `npm --prefix frontend run test` and `npm --prefix frontend run lint`
- [ ] T025 Run mock and live browser suites: `npm --prefix frontend run test:e2e` and `npm --prefix frontend run test:e2e:live`
- [ ] T026 Run `scripts/verify.sh` and record acceptance criterion -> proving evidence -> result
- [ ] T027 Run `python3 scripts/sync-agent-files.py --check` when agent/configuration files are involved
- [ ] T028 Update the feature artifacts in `specs/[###-feature-name]/` and relevant README/docs with generated-artifact changes and remaining limitations

## User Story Phases

For each user story in the specification, add a phase using this shape. Keep
the story independently testable and do not hide backend/frontend work in a
single vague task.

## Phase N: User Story [N] - [Title] (Priority: P[N])

**Goal**: [What this story delivers.]

**Independent Test**: [Exact focused test or browser journey and expected result.]

### Tests for User Story [N]

- [ ] TXXX [P] [USN] Add/update Java test in `src/test/java/...`
- [ ] TXXX [P] [USN] Add/update Vitest test in `frontend/src/...test.tsx`
- [ ] TXXX [USN] Add/update Playwright test in `frontend/e2e` or `frontend/e2e-live` when applicable

### Implementation for User Story [N]

- [ ] TXXX [USN] Update the backend contract/engine file at `src/main/java/...`
- [ ] TXXX [USN] Update the frontend adapter/hook/screen/widget file at `frontend/src/...`
- [ ] TXXX [USN] Update generated artifacts/docs and name the verification command

**Checkpoint**: Story [N] is independently functional and its focused tests
pass before the next story begins.

## Dependencies and Execution Order

### Phase Dependencies

- Baseline/spec/plan artifacts precede implementation.
- Backend contract and engine work precedes frontend integration when the API changes.
- Adapter/state work precedes screen/widget browser work.
- Integration, generated-artifact review, and the full gate follow implementation.
- Story phases may run in parallel only when their files and contracts are independent.

### Within a Story

1. Define or confirm the contract.
2. Add/update the focused tests.
3. Implement backend boundary and engine behavior.
4. Implement adapter/state behavior.
5. Implement screens/widgets and browser coverage.
6. Verify the story independently.

## Command Reference

Use the pinned runtime installed by Maven for authoritative frontend results.

```bash
mvn -Dtest=<focused-test-class> test
npm --prefix frontend run test
npm --prefix frontend run lint
npm --prefix frontend run test:e2e
npm --prefix frontend run test:e2e:live
scripts/verify.sh
python3 scripts/sync-agent-files.py --check
```

If a browser command fails before tests execute because of restricted localhost
binding, report it as an environment capability limitation and preserve the
unchanged product command/result separately.

## Implementation Strategy

### MVP First

1. Complete the baseline and foundation tasks required by the plan.
2. Complete the smallest P1 story across backend, frontend, and tests.
3. Run its focused evidence and then the full gate when the slice is complete.
4. Continue with later stories only after the P1 checkpoint is honest and green.

### Closeout

The feature is ready for user integration only when the specification is
implemented, evidence is recorded, generated artifacts are reviewed, the full
gate is green or limitations are explicit, and the next unit is named.
