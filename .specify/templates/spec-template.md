# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[feature/<slug>]`

**Created**: [DATE]

**Status**: Draft

**Input**: User description: "$ARGUMENTS"

**Spec Kit Artifacts**: `specs/[###-feature-name]/`

## Scope and Context

### Problem

[Describe the user or system problem in observable terms.]

### In Scope

- [Specific user-visible or API-visible behavior included in this feature]
- [Affected backend/frontend surfaces, if known]

### Out of Scope

- [Explicitly excluded behavior]
- [Database, authentication, external-service, or deployment changes unless explicitly required]

## User Scenarios & Testing *(mandatory)*

User stories MUST be independently testable. Prioritize them as P1, P2, P3,
with P1 representing the smallest valuable end-to-end slice.

### User Story 1 - [Brief Title] (Priority: P1)

[Describe the user journey in plain language.]

**Why this priority**: [Explain value and sequencing.]

**Independent Test**: [Name the focused unit, component, mock-browser, or live-JAR test that proves this story.]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [observable result].
2. **Given** [initial state], **When** [action], **Then** [observable result].

### User Story 2 - [Brief Title] (Priority: P2)

[Describe the next independently valuable journey, or remove this section.]

**Independent Test**: [Proof method and expected result.]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [observable result].

### Edge Cases and Failure Behavior

- Invalid session, player, ship, edition, coordinate, or lifecycle stage:
  [expected status/error behavior].
- Concurrent requests against the same session: [expected serialization or
  conflict behavior].
- Browser refresh, navigation, reconnect, or missing browser state:
  [expected behavior].
- SSE unavailable, delayed, duplicated, or disconnected: [fallback behavior].
- Packaging or generated-artifact impact: [expected evidence].

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST [specific capability].
- **FR-002**: The system MUST [validation or lifecycle behavior].
- **FR-003**: The system MUST expose [observable UI/API result].
- **FR-004**: The system MUST preserve [existing invariant].

Mark missing decisions explicitly as `[NEEDS CLARIFICATION: reason]` rather
than assuming a new architectural dependency.

### Backend/API Requirements

Complete this section when the feature affects `src/main/java`:

- Controller and route: [file, HTTP verb, path, request/response DTO].
- Application API: [interface and implementation impact].
- Engine/state: [game rule, state transition, validation, or concurrency impact].
- Persistence: [in-memory state impact; explain if none].
- Exceptions/status mapping: [typed exception and HTTP behavior].
- SSE/event behavior: [event name, payload, subscription, fallback, or none].
- OpenAPI: [whether `docs/openapi.json` must change].

### Frontend Requirements

Complete this section when the feature affects `frontend/src`:

- Adapter contract: [method or DTO impact in `GameAdapter`].
- HTTP/mock behavior: [changes to `HttpGameAdapter`, `MockGameAdapter`, or
  `BackendRequestService`].
- State/lifecycle: [hook, browser storage, routing guard, or event behavior].
- Screens/widgets/design: [affected paths and user-visible states].
- Localization/accessibility/responsiveness: [required labels, semantics, or
  viewport behavior].
- Browser tests: [mock Playwright and/or live-JAR coverage].

### Contract and Boundary Requirements

- Backend layer affected: `[web | api | engine | persistence | none]`.
- Frontend layer affected: `[adapter | service | hook | screen | widget | design | none]`.
- Allowed dependency direction: [describe the existing boundary being used].
- New dependency: `[none | package and reason]`.
- Persistence/auth/external-service changes: `[none | explicit decision]`.

## Key Entities and State

- **[Entity/state]**: [Meaning, important fields, lifecycle, and ownership.]
- **[DTO/event]**: [Wire shape and producer/consumer, if applicable.]

## Success Criteria *(mandatory)*

Success criteria MUST be measurable and tied to evidence.

- **SC-001**: [User journey completes with the expected visible result.]
- **SC-002**: [Focused Java/Vitest test proves the changed rule or contract.]
- **SC-003**: [Mock or live Playwright test proves the browser journey, if applicable.]
- **SC-004**: `scripts/verify.sh` passes, or the exact capability limitation is recorded.
- **SC-005**: `docs/openapi.json` is unchanged or its intentional diff is reviewed when the API changes.

## Assumptions and Clarifications

- [Assumption grounded in current source/configuration.]
- [Assumption grounded in current product documentation.]
- [Open question, if any, marked `[NEEDS CLARIFICATION]` with its impact.]

## Evidence and Documentation Impact

- Proving tests: [exact files or commands].
- Generated artifacts: [OpenAPI, frontend build, JAR, or none].
- Documentation: [README, `docs/index.md`, `docs/architecture.md`, or none].
- Feature artifacts: [updates required in `specs/[###-feature-name]/`].
