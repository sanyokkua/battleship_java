# api-privacy-security Specification

## Purpose
Defines what the API hides, how callers are identified and which request protections form part of the contract.

## Requirements

### Requirement: Opponent placement privacy (R12) [user]
Opponent placement SHALL never be sent, except hit and sunk cells, ruleset-revealed water, and the full board once `FINISHED`. `ABANDONED` and expired games SHALL reveal nothing.

#### Scenario: Mid-game snapshot
- **WHEN** a client reads the opponent board during `PLAYING`
- **THEN** it sees only `UNKNOWN`, `MISS`, `HIT`, `SUNK` and `REVEALED_WATER`, and only sunk ships

#### Scenario: Abandoned game
- **WHEN** a game is `ABANDONED` or expired
- **THEN** no opponent placement is revealed

### Requirement: Anonymous cookie identity (R13) [user]
Identity SHALL be an anonymous `HttpOnly` session cookie; `gameId` SHALL only locate a game; no credential SHALL appear in a URL, body or script-readable storage. Non-players SHALL get `404`.

#### Scenario: Non-player access
- **WHEN** a caller who is not a player requests a game
- **THEN** the response is `404`

### Requirement: Invitation secret handling (R14) [user]
The invitation secret SHALL travel only in the link fragment and the join body. Every refused join SHALL give the same `409 invitation-unavailable`.

#### Scenario: Refused joins are indistinguishable
- **WHEN** a join is refused because the secret is wrong, replaced, expired or the seat is taken
- **THEN** the response is the same `409 invitation-unavailable`

### Requirement: CSRF and same origin (R15) [agent]
CSRF SHALL use the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header. UI and API SHALL share one origin so CORS is not part of the contract; Origin and Fetch-Metadata checks SHALL be server policy, not request parameters.

#### Scenario: Mutating request
- **WHEN** a client sends a mutating request
- **THEN** it echoes the `XSRF-TOKEN` cookie in `X-XSRF-TOKEN`

### Requirement: Strict request bodies (R16) [agent]
Requests SHALL reject unknown fields, so a client cannot supply a seat, turn, winner or result.

#### Scenario: Extra field
- **WHEN** a command contains `winner`
- **THEN** the request is invalid (additional properties rejected)

### Requirement: No opponent placement in examples (S3)
No embedded example SHALL show opponent placement outside the cases allowed by R12.

#### Scenario: Example review
- **WHEN** examples of every non-`FINISHED` phase are inspected
- **THEN** none exposes unsunk opponent ships
