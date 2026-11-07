# api-reliability-errors Specification

## Purpose
Defines ordering, idempotency, streaming, lifetimes, errors and localization behavior so clients can recover from any interruption.

## Requirements

### Requirement: Version ordering (R17) [agent]
`version` SHALL grow on any change to either player's view and SHALL be used only to order snapshots; there SHALL be no version preconditions.

#### Scenario: Out-of-order snapshots
- **WHEN** a client receives two snapshots
- **THEN** it keeps the one with the higher `version`

### Requirement: Idempotent commands (R18) [user]
A command SHALL carry a client-generated `commandId`; resending an accepted one SHALL return the current snapshot and SHALL never be applied twice.

#### Scenario: Retried shot
- **WHEN** a `FIRE` command is resent with the same `commandId`
- **THEN** the current snapshot is returned and the shot is not applied again

### Requirement: Event stream semantics (R19) [agent]
The event stream SHALL always start with the current snapshot; a `closed` event SHALL tell the client not to reconnect (another tab, or the game is gone). There SHALL be no event replay.

#### Scenario: Second tab
- **WHEN** a second tab opens the stream
- **THEN** the first stream receives `closed` and its client does not reconnect

### Requirement: Safe repeatable leave (R20) [agent]
Leave SHALL have no body and be safe to repeat: before play it abandons the game, during play it is refused in favour of `RESIGN`, afterwards it drops the caller's access.

#### Scenario: Leave during play
- **WHEN** a player calls leave while `PLAYING`
- **THEN** it is refused and the client uses `RESIGN`

### Requirement: In-memory lifetimes (R21) [user]
Games SHALL live in memory only: 15 minutes idle, 2 hours at most, results readable for 5 minutes, invitations 15 minutes; visible-tab presence SHALL keep a game alive. Values SHALL be published by `GET /api/v1/meta` and SHALL NOT be compile-time constants of the contract.

#### Scenario: Reading limits
- **WHEN** a client calls `GET /api/v1/meta`
- **THEN** it receives the lifetime values

### Requirement: Problem documents (R22) [user]
Errors SHALL be RFC 9457 problem documents with one flat, stable `code` list and a status map distinguishing 400/401/403/404/409/410/422/429/5xx, and SHALL carry no exception text, secrets or board data.

#### Scenario: Error response
- **WHEN** an operation fails
- **THEN** the body is a problem document with a stable `code`

### Requirement: Localization by code (R23) [user]
The client SHALL localize by `code`; server text is for developers only.

#### Scenario: Showing an error
- **WHEN** the UI displays an error
- **THEN** it maps `code` to a localized message and ignores server text

### Requirement: Resume and recover (User Story 3, P2)
After a reload, a lost answer, a dropped stream or a second tab, the player SHALL get the current truth from the server and never have to guess whether an action happened.

#### Scenario: Reload mid-game
- **WHEN** a player reloads the page
- **THEN** a read returns the current `GameSnapshot`
