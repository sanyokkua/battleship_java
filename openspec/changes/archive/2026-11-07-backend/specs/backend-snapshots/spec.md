## Purpose

The caller-relative game snapshot and player privacy: one document for HTTP and events, the version rule, what the opponent may and may not see, the complete own board, and the extra facts a client needs. Source: spec 002-backend R17, R18, R19, R20, R22. Covers User Stories US1 and US5 (reload recovery), success criteria S2 and S3, proof area 3 (player privacy) and the Key Entities Board, Ship, Player and Snapshot.

## ADDED Requirements

### Requirement: One snapshot representation (R17)

Every successful game operation SHALL answer with the same caller-relative snapshot, and the identical document SHALL be what the event stream delivers. There SHALL be no second representation of a game.

#### Scenario: HTTP and events share the document

- **WHEN** the same captured view is served by `getGame` and the event stream with a fixed time source
- **THEN** the bytes are identical

### Requirement: Version and view definition (R18)

A player's view SHALL be that player's snapshot excluding `serverTime` and `expiresAt`. The version SHALL rise by one when either player's view changes and never otherwise: not on a read, not on a refused action, not on an accepted action that changes no view (R23). `serverTime` and `expiresAt` SHALL always be current in whatever snapshot is produced.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The version exists only to order snapshots arriving by different routes, is never a precondition for an action, and its numbers MAY skip because a player is only sent the versions their own view reached (R28). The contract text for `GameSnapshot.version` was aligned to this rule within `001-api-contract` R24 (description-level, wire JSON byte-identical).

#### Scenario: Reads do not bump the version

- **WHEN** a game is read twice with no state change
- **THEN** version is equal and only `serverTime` differs

#### Scenario: Refused action

- **WHEN** an action is refused
- **THEN** the version is unchanged

### Requirement: Player privacy of placement (R19)

A player SHALL never be sent the opponent's placement, with exactly three exceptions: cells that were hit or sunk, water the ruleset revealed around a sunk ship, and the opponent's complete board once the game is finished. An abandoned or expired game SHALL disclose no part of the opponent's placement; each player still sees their own board.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The opponent grid carries only `UNKNOWN`/`MISS`/`HIT`/`SUNK`/`REVEALED_WATER` and `ships` lists only sunk ships until `FINISHED`. Success criterion S2: in every phase no player receives the opponent's placement outside the allowed cases (US3-1).

#### Scenario: Placement is private

- **WHEN** a player places, moves, rotates, removes, randomises or clears ships
- **THEN** only that player's own board changes and the opponent's view of it is unchanged

#### Scenario: Finished game reveals both boards

- **WHEN** the game is `FINISHED`
- **THEN** both full boards are visible to both players

#### Scenario: Abandoned game

- **WHEN** a game is abandoned or expired
- **THEN** no part of the opponent's placement is disclosed

### Requirement: Own board complete and drawable (R20)

A player's own board SHALL be complete and drawable as it stands, with no `UNKNOWN` cells. From placement onwards it SHALL list the entire fleet, including ships not yet on the board. Each own-board cell carries exactly one of `SHIP`, `HIT`, `SUNK`, `MISS`, `REVEALED_WATER`, `WATER`, which are disjoint and cover the board.

#### Scenario: Own board in placement

- **WHEN** a player reads the game in `PLACEMENT`
- **THEN** the own board lists every ship including unplaced ones and has no undisclosed cells

### Requirement: Snapshot carries derived facts (R22)

The snapshot SHALL carry the most recent accepted shot, whether each player currently has an open event stream, and how many ships each player has left, so a client needs no diffing, counting or rule knowledge. The connection flag changes the opponent's view like anything else and is delivered at once; a stream replaced by a newer one from the same player is not a disconnection and produces no flag change, so a reload never flickers.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** `shipsRemaining` is derived from fleet size minus sunk ships.

#### Scenario: Replacement does not flicker

- **WHEN** a player's stream is replaced by a newer one
- **THEN** the connected flag and version do not change

### Requirement: Reload recovery from one read (S3)

After a reload at any point in a game, one read SHALL return everything needed to continue, including whose turn it is and what the player may do (US5-1; success criterion S3).

#### Scenario: Reload mid-game

- **WHEN** the page is reloaded
- **THEN** a single `getGame` returns the full current snapshot with `turn` and `allowedActions`

### Requirement: Proof area 3 - player privacy

Proof area 3 (owner `ProjectionPrivacyTest`, `application`) SHALL build two states differing only in the opponent's undiscovered ships and assert the opponent-facing snapshots are byte-identical until the game finishes. The `SnapshotProjector` is the sole place that decides disclosure and `allowedActions`, returning the framework-free `SnapshotView`.

#### Scenario: Identical opponent view

- **WHEN** two games differ only in undiscovered opponent ships
- **THEN** the opponent-facing snapshots are identical until FINISHED
