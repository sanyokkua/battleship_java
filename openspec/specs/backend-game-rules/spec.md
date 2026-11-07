# backend-game-rules Specification

## Purpose
The rules of Battleship as pure server-side behaviour: the two published rulesets, ship placement legality, shooting, turn order, revealed water, the first-turn decision, how a game ends, the phase machine, stable ship identifiers and attempt-bounded random fleet arrangement. Source: spec 002-backend R03, R05-R12, R14, R21, R26 (Provenance `[user]` unless tagged `[agent]`). Covers User Stories US3 and US4, success criterion S1 and proof area 1 (ruleset correctness). The contract `contracts/openapi.yaml` wins over this text on any wire detail.

## Requirements

### Requirement: Rulesets are published immutable product data (R03)

The backend SHALL publish both rulesets with their board size, fleet composition and the three rule flags; rulesets SHALL be immutable and a corrected ruleset SHALL be published under a new identifier rather than edited.

#### Scenario: Both rulesets are listed

- **WHEN** a client lists rulesets
- **THEN** exactly `sea-battle-10-ship.v1` and `hasbro-classic-2002.v1` are returned with board, fleet and flags

#### Scenario: A correction does not edit an existing ruleset

- **WHEN** a ruleset must be corrected
- **THEN** it is published under a new identifier and the old identifier keeps its original definition

### Requirement: Exactly two rulesets with fixed boards, fleets and flags (R05)

The backend SHALL define exactly two rulesets, both 10x10. `sea-battle-10-ship.v1` has fleet `ship-4`x1 (length 4), `ship-3`x2 (3), `ship-2`x3 (2), `ship-1`x4 (1) and `shipsMayTouch=false`, `extraTurnOnHit=true`, `revealWaterAroundSunk=true`. `hasbro-classic-2002.v1` has fleet `carrier`x1 (5), `battleship`x1 (4), `destroyer`x1 (3), `submarine`x1 (3), `patrol-boat`x1 (2) and `shipsMayTouch=true`, `extraTurnOnHit=false`, `revealWaterAroundSunk=false`.

#### Scenario: Sea Battle fleet

- **WHEN** a game uses `sea-battle-10-ship.v1`
- **THEN** its fleet has 10 ships occupying 20 cells

#### Scenario: Hasbro fleet

- **WHEN** a game uses `hasbro-classic-2002.v1`
- **THEN** its fleet has 5 ships occupying 17 cells

#### Scenario: Rulesets demonstrably differ (S1)

- **WHEN** both rulesets are compared
- **THEN** they differ in adjacency, extra turn and revealed water

### Requirement: Adjacency rule (R06)

Where `shipsMayTouch` is false the backend SHALL refuse any placement that leaves two ships adjacent, diagonals included, with the touching reason; where it is true ships MAY touch.

#### Scenario: Diagonal touch refused under Sea Battle

- **WHEN** a ship is placed diagonally next to another under `sea-battle-10-ship.v1`
- **THEN** the action is refused `placement-touching`

#### Scenario: Touching allowed under Hasbro

- **WHEN** the same placement is made under `hasbro-classic-2002.v1`
- **THEN** it is accepted

### Requirement: Extra turn on hit (R07)

Where `extraTurnOnHit` is true a player who hits or sinks SHALL fire again; where false the turn SHALL pass after every shot, hit or miss.

#### Scenario: Hit keeps the turn under Sea Battle

- **WHEN** a player hits under `sea-battle-10-ship.v1`
- **THEN** the same player fires again, while a miss passes the turn

#### Scenario: Every shot passes the turn under Hasbro

- **WHEN** a player hits or misses under `hasbro-classic-2002.v1`
- **THEN** the turn passes

### Requirement: Water revealed around a sunk ship (R08)

Where `revealWaterAroundSunk` is true the backend SHALL, when a ship sinks, make every still-undisclosed neighbour cell of every cell of that ship (up to eight per cell) visible as water without a shot; where false nothing SHALL be revealed.

#### Scenario: Sinking reveals surrounding water

- **WHEN** a ship sinks under `sea-battle-10-ship.v1`
- **THEN** all its still-undisclosed neighbours become `REVEALED_WATER`, clipped at board edges (US4-2)

#### Scenario: Nothing revealed under Hasbro

- **WHEN** a ship sinks under `hasbro-classic-2002.v1`
- **THEN** its neighbours stay undisclosed

### Requirement: Ship geometry and distinct placement refusals (R09)

Ships SHALL be straight, horizontal or vertical, lie entirely on the board and never overlap. A coordinate outside the ruleset's board, or a ship identifier the fleet does not contain, SHALL be a field-level validation failure (`OUT_OF_RANGE`/`UNKNOWN_VALUE`, 422).

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** A ship whose anchor is on the board but which would extend past the edge (`placement-out-of-bounds`), an overlap (`placement-overlap`) and, where forbidden, a touch (`placement-touching`) SHALL each be refused with their own distinct reason, leaving the board and game exactly as they were (US3-2).

#### Scenario: Off-board coordinate

- **WHEN** an anchor coordinate lies outside the ruleset's board
- **THEN** the answer is a 422 validation failure pointing at the field

#### Scenario: Ship extends past the edge

- **WHEN** the anchor is on the board but the ship would extend past the edge
- **THEN** the answer is `placement-out-of-bounds`

#### Scenario: Overlap

- **WHEN** a ship overlaps another
- **THEN** the answer is `placement-overlap`, distinct from `placement-touching`

### Requirement: Shooting (R10)

A shot SHALL name one cell of the opponent's board. A coordinate outside the ruleset's board SHALL be a field-level validation failure. A cell already disclosed SHALL be refused `target-already-fired`, cost nothing and not pass the turn. A shot resolves as miss, hit or sinking (US4-1).

#### Scenario: Fire at an undisclosed cell

- **WHEN** the player whose turn it is fires at an undisclosed cell
- **THEN** the answer states miss, hit or sinking and the turn moves or stays per the ruleset

#### Scenario: Fire at a disclosed cell

- **WHEN** a player fires at a cell that was a miss, hit, sunk or revealed water
- **THEN** it is refused `target-already-fired` and the turn is not consumed

### Requirement: A game ends by destroyed fleet or resignation, and ready order picks the first turn (R11)

A game SHALL end when a fleet is completely sunk or when a player resigns; in both cases the other player wins and the reason (`FLEET_DESTROYED` or `RESIGNATION`) is stated. The ending SHALL be atomic with the move to `FINISHED`. The player who declared ready first SHALL fire first: the server records each player's ready moment and compares them, and breaks an exact tie randomly from the injectable random source (R26).

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The choice is first visible when play starts. Both players ready at the same instant SHALL start play once and decide the first turn once (edge case).

#### Scenario: Fleet destroyed

- **WHEN** the last ship of a fleet sinks
- **THEN** the game is `FINISHED`, the shooter wins with reason `FLEET_DESTROYED`, both full boards become visible and statistics are present (US4-3)

#### Scenario: Resignation

- **WHEN** a player resigns during play
- **THEN** the game finishes, the other player wins with reason `RESIGNATION`

#### Scenario: Earlier ready fires first

- **WHEN** the second player readies and one ready moment is earlier
- **THEN** the earlier player holds the turn when play starts

#### Scenario: Exact tie

- **WHEN** both ready moments are identical
- **THEN** the seeded random source decides, once, and both players see the same starting state

### Requirement: Phase machine without a way back (R12)

A game SHALL move `WAITING` -> `PLACEMENT` -> `PLAYING` -> `FINISHED`, or to `ABANDONED` when a player leaves before play starts. There SHALL be no other transition and no way back; `phase` never moves backwards.

#### Scenario: Normal progression

- **WHEN** a guest joins, both ready, and a fleet is destroyed
- **THEN** the phases advance in order and never regress

#### Scenario: Abandoned before play

- **WHEN** a player leaves during `PLACEMENT`
- **THEN** the game becomes `ABANDONED`

### Requirement: Fleet editing until ready; ready is irrevocable (R14)

During placement the four fleet-editing actions SHALL stay available until the player declares ready. Declaring ready SHALL be offered only when the whole fleet is placed, SHALL NOT be undoable, and SHALL start play once both players have done it. A player who tries to edit after readying is refused (US3-4); readying with an incomplete fleet is refused (US3-3); the second ready starts play with a server-chosen first turn (US3-5).

#### Scenario: Ready with incomplete fleet

- **WHEN** a player sends READY with an unplaced ship
- **THEN** the action is refused and not offered

#### Scenario: Edit after ready

- **WHEN** a ready player attempts to edit the fleet
- **THEN** it is refused with `action-not-allowed`

### Requirement: Stable server-generated ship identifiers (R21)

`[agent]` Each ship SHALL be given a server-generated identifier when its fleet is created so it can be addressed before placement. The identifier SHALL be unique within its own player's board (boards may reuse values), stable for the whole game, survive clearing and random rearrangement unchanged, and carry no hidden information. Any value of the contract's identifier shape is acceptable (`s01`...`s10` chosen).

#### Scenario: Identifiers survive clear and re-randomise

- **WHEN** a fleet is cleared or randomly rearranged
- **THEN** every ship keeps its identifier

### Requirement: Attempt-bounded random arrangement (R26)

`[agent]` Arranging the fleet at random SHALL produce an arrangement legal under the active ruleset that replaces whatever was there. Randomness SHALL come from an injectable source so a specific arrangement is reproducible in a proof and the same source breaks a tied first turn (R11).

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The search SHALL be attempt-bounded: after the configured number of failed attempts it SHALL give up, refuse with the dedicated `random-arrangement-failed` code (HTTP 409) and leave fleet, state and version exactly as they were. `[user]` Clarification 2026-09-29.

#### Scenario: Seeded arrangement is reproducible

- **WHEN** the same seed arranges the fleet twice
- **THEN** the arrangement is identical cell by cell and legal under both rulesets

#### Scenario: Attempts exhausted

- **WHEN** the attempt limit is exhausted
- **THEN** the action is refused `random-arrangement-failed`, 409, and the original state is unchanged

### Requirement: Success criterion S1 and proof area 1 - ruleset correctness

A complete game SHALL be playable from creation to a stated winner under each ruleset (S1). Proof area 1 (ruleset correctness, owner `RulesetRulesTest` in `domain`) SHALL prove legal and illegal placements under both rulesets, the adjacency difference, the extra-turn difference, the revealed-water difference and both ways a game ends.

#### Scenario: Whole game to destroyed fleet under each ruleset

- **WHEN** a full game is played under each ruleset
- **THEN** 20 hits sink 10 ships (Sea Battle) and 17 hits sink 5 ships (Hasbro) and the game finishes with a winner
