# api-game-model Specification

## Purpose
Defines the game concepts exposed by the API: rulesets, phases, ship addressing, commands, derived fields and finish data.

## Requirements

### Requirement: Two immutable rulesets (R06) [user]
The API SHALL offer `sea-battle-10-ship.v1` (10 ships, no touching, extra turn on hit, water revealed around a sunk ship) and `hasbro-classic-2002.v1` (5 ships, touching allowed, turn always passes). Rulesets SHALL be immutable and the API SHALL publish board, fleet and these three rule flags.

#### Scenario: Reading rulesets
- **WHEN** a client calls the rulesets operation
- **THEN** it receives both rulesets with board, fleet and the three rule flags

### Requirement: Game phases (R07) [user]
Phases SHALL be `WAITING` then `PLACEMENT` then `PLAYING` then `FINISHED`, or `ABANDONED` when a player leaves before play.

#### Scenario: Leave before play
- **WHEN** a player leaves while the phase is `WAITING` or `PLACEMENT`
- **THEN** the phase becomes `ABANDONED`

### Requirement: Addressable fleet (R08) [agent]
From `PLACEMENT` on, the player's own board SHALL list the whole fleet with server-minted `shipId`s, so every ship can be addressed before it is placed.

#### Scenario: Unplaced ship
- **WHEN** a player is in `PLACEMENT` and has placed no ships
- **THEN** their board lists every fleet ship with a `shipId`

### Requirement: Command set (R09) [agent]
Commands SHALL be `PLACE_SHIP` (also an atomic move/rotate), `REMOVE_SHIP`, `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY` (irreversible, offered only for a complete fleet), `FIRE` and `RESIGN`.

#### Scenario: Ready with incomplete fleet
- **WHEN** the fleet is incomplete
- **THEN** `READY` is not in `allowedActions`

### Requirement: Derived display fields (R10) [agent]
`lastShot`, `connected` and `shipsRemaining` SHALL be provided so the client needs no board diffing or counting, and boards SHALL be dense grids drawable as they are.

#### Scenario: After a shot
- **WHEN** a shot is fired
- **THEN** the snapshot carries `lastShot` and updated `shipsRemaining` without client computation

### Requirement: Finished game result (R11) [user]
`FINISHED` SHALL carry `outcome` and server-calculated `statistics` (match durations; per player placement time, shots, hits, accuracy, turn and shot-decision timing, fleet condition).

#### Scenario: Reading a result
- **WHEN** the phase is `FINISHED`
- **THEN** the snapshot includes `outcome` and `statistics` for both players

### Requirement: Create and join (User Story 1, P1)
A host SHALL be able to pick a ruleset and a name, create a private game and share one invitation link; a guest SHALL open the link, confirm a name and join. Opening the link alone SHALL claim nothing, and only one guest SHALL ever be able to join.

#### Scenario: Second guest
- **WHEN** a guest has already joined and another opens the link and tries to join
- **THEN** the join is refused

### Requirement: Place and play (User Story 2, P1)
Each player SHALL arrange a fleet privately (by hand or randomly), declare ready, then fire in turns until a fleet is destroyed or someone resigns; the server SHALL decide legality, turns, results and the winner.

#### Scenario: Illegal shot
- **WHEN** a player fires out of turn
- **THEN** the server refuses it and the client never decides legality

### Requirement: Finish and leave (User Story 4, P2)
Players SHALL see the result, both boards and server-calculated statistics, and SHALL be able to leave; leaving before play ends the game for both without revealing anything.

#### Scenario: Leave in placement
- **WHEN** a player leaves during `PLACEMENT`
- **THEN** the game is abandoned for both and no opponent placement is revealed

### Requirement: Both rulesets playable end to end (S2)
Both rulesets SHALL be playable from create to result using only `openapi.yaml`.

#### Scenario: Walkthrough
- **WHEN** a reviewer walks each ruleset from create to result using only the document
- **THEN** every step has an operation and an example
