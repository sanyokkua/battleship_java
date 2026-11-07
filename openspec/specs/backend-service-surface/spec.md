# backend-service-surface Specification

## Purpose
The public surface of the backend: the eleven operations of the contract under `/api/v1`, service information, health, the problem-document failure model and wire conformance. Source: spec 002-backend R01, R02, R04, R49, R50, Authority section and proof area 10. `contracts/openapi.yaml` is the source of truth for every externally visible behaviour and wins over any other description. Covers User Stories US1-US6 at the wire level.

## Requirements

### Requirement: Implement every contract operation and nothing else (R01)

The deliverable SHALL be one runnable backend service that implements every operation of `contracts/openapi.yaml` under `/api/v1` (`getMeta`, `listRulesets`, `getHealth`, `createGame`, `getGame`, `joinGame`, `sendCommand`, `streamGameEvents`, `replaceInvitation`, `sendPresence`, `leaveGame`) and SHALL expose no other public surface, no user-interface assets and no second game engine.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Wire DTOs are generated from the contract (models only, package `ua.kostenko.battleship.app.web.dto`) and never hand-edited.

#### Scenario: All operations served

- **WHEN** each of the eleven operations is called
- **THEN** it behaves per the contract

#### Scenario: Nothing else is served

- **WHEN** `GET /` is requested
- **THEN** the answer is 404 because no UI assets are bundled

### Requirement: Service information publishes enforced limits (R02)

Service information SHALL report the contract version the server implements (`apiVersion` constant `1.0.0`, equal to the contract's `info.version`), the server's current time, and the six published limits: idle lifetime, absolute game lifetime, result retention, invitation lifetime, presence interval and event-stream heartbeat interval. The published values SHALL be the values the running service actually enforces (R55).

#### Scenario: Meta

- **WHEN** `getMeta` is called
- **THEN** it returns the API version, `serverTime` and the six limits and sets the anti-forgery cookie

### Requirement: Ruleset listing (R03 surface)

`listRulesets` SHALL publish both rulesets from the domain constants (see backend-game-rules).

#### Scenario: Listing

- **WHEN** `listRulesets` is called
- **THEN** both rulesets are returned with board, fleet and flags

### Requirement: Health reports liveness and readiness (R04)

Health SHALL report liveness and readiness: ready once the service accepts work, not ready while starting and while shutting down, each with its reason (`STARTING`, `DRAINING`). A service that is full is still ready. Health is the only operation whose unavailable answer is not a problem document.

#### Scenario: Starting

- **WHEN** health is read during start-up
- **THEN** `live: true, ready: false, reason: STARTING`

#### Scenario: Full but ready

- **WHEN** capacity ceilings are reached
- **THEN** health still reports ready

### Requirement: Problem documents with stable codes (R49)

Every failure SHALL be a problem document with a stable machine-readable code from the contract's list, mapped: `malformed-request` 400; `session-required` 401; `request-security-rejected` 403; `game-unavailable` 404; `invitation-unavailable`, `action-not-allowed`, `placement-out-of-bounds`, `placement-overlap`, `placement-touching`, `random-arrangement-failed` and `target-already-fired` 409; `game-expired` 410; `payload-too-large` 413;

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** `unsupported-media-type` 415; `validation-failed` 422; `rate-limit-exceeded` 429; `internal-error` 500; `service-unavailable` 503. The `browser-game-limit` refusal is a 409 (R62). The problem type is the generated `Problem`; one advice owns the single code-to-status mapping; CSRF and site-isolation failures use the same writer.

#### Scenario: Code mapping

- **WHEN** each failure class is provoked
- **THEN** the status and code match the table

### Requirement: Problem documents carry correlation, never secrets (R50)

A problem document SHALL carry a correlation identifier (16 hex) that ties it to the logs, and SHALL never carry a secret, a cookie, board data, the request body, an exception message or a stack trace. Its human-readable text SHALL be English developer text; a client localizes by the code.

#### Scenario: Internal error

- **WHEN** an unexpected exception occurs
- **THEN** the body has `internal-error` and a correlation id but no exception text

### Requirement: Proof area 10 - wire conformance

Proof area 10 (owner `WireConformanceIT`, `app`) SHALL assert every operation's success and failure bodies field by field against the contract's examples, including that `SnapshotDtoAssembler` copies every field of a `SnapshotView` into the generated DTO (generation fixes shape, not that the assembler fills it). It reuses the fixtures of `GameJourneyIT` rather than duplicating journeys (Constitution IV).

#### Scenario: Field-by-field

- **WHEN** `WireConformanceIT` runs
- **THEN** every response body matches the contract example field by field

### Requirement: Whole game over the wire (S1, S3)

`GameJourneyIT` SHALL prove a complete game over HTTP under each ruleset, a resignation game and reload recovery, as the server-level owner of S1 and S3 (browser journeys belong to `003-frontend`).

#### Scenario: Journey

- **WHEN** the journey runs
- **THEN** a game completes from creation to a stated winner and a reload restores everything with one read
