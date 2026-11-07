# backend-actions Specification

## Purpose
What a player may do and how actions are applied: the allowed-action table, refusal stability, leaving, the seven state-changing actions, repeat safety through caller-generated action identifiers, and single-order serialization per game. Source: spec 002-backend R13, R15, R16, R23, R24, R25. Covers User Stories US3, US5 (recovery after a lost answer, simultaneous actions) and US6 (leaving), success criteria S4 and S7, and proof areas 2 (action legality and state stability), 4 (repeat safety) and 5 (concurrency).

## Requirements

### Requirement: Allowed actions table is the single authority (R13)

Every snapshot SHALL state exactly what its recipient may do, computed by the server per player per moment from the table below; a client SHALL never derive permission and nothing outside the table SHALL be offered.

#### Scenario: The offered-action table

- **WHEN** a snapshot is produced for a player
- **THEN** `allowedActions` equals the row for that situation

| Situation | `allowedActions` |
|---|---|
| `WAITING`, the host | `NEW_INVITATION`, `SEND_PRESENCE`, `LEAVE` |
| `PLACEMENT`, not ready, fleet incomplete | `PLACE_SHIP`, `REMOVE_SHIP`, `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `SEND_PRESENCE`, `LEAVE` |
| `PLACEMENT`, not ready, fleet complete | the above + `READY` |
| `PLACEMENT`, already ready | `SEND_PRESENCE`, `LEAVE` |
| `PLAYING`, the player whose turn it is | `FIRE`, `RESIGN`, `SEND_PRESENCE` |
| `PLAYING`, the other player | `RESIGN`, `SEND_PRESENCE` |
| `FINISHED` or `ABANDONED`, either player | `LEAVE` |

#### Scenario: Fleet complete offers READY

- **WHEN** a not-ready player in `PLACEMENT` has placed the whole fleet
- **THEN** `READY` is offered in addition to the editing actions

#### Scenario: Turn holder versus other player

- **WHEN** the game is `PLAYING`
- **THEN** only the turn holder is offered `FIRE`, both are offered `RESIGN`, and `LEAVE` is offered to neither

#### Scenario: Action outside the table

- **WHEN** a command whose action is not in the caller's offered set is sent
- **THEN** it is refused `action-not-allowed`

### Requirement: A refused action changes nothing (R15)

A refused action SHALL leave the game's state and its version exactly as they were before the attempt. The domain returns the input state by identity.

#### Scenario: Refusal stability matrix

- **WHEN** each of the seven command types is attempted in every phase where it is not offered
- **THEN** each is refused `action-not-allowed` and state and version are unchanged

### Requirement: Leaving is allowed only when offered (R16)

Leaving SHALL be allowed only when it is offered, and `LEAVE` SHALL never be offered during play, which keeps the refusal consistent with R13 rather than circular. Before play, leaving SHALL end the game for both as abandoned with nothing revealed (a `WAITING` game nobody joined is simply removed).

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** During play leaving SHALL be refused in favour of resigning, and RESIGN SHALL stay offered to both players so the game always ends with a stated winner. After the game is over it SHALL remove only the caller's access; afterwards that browser is told the game does not exist, including for a repeated leave (US6-2, US6-3, US6-4).

#### Scenario: Leave before play

- **WHEN** a player leaves in `PLACEMENT`
- **THEN** the game ends for both as `ABANDONED` and neither placement is revealed

#### Scenario: Leave a waiting game

- **WHEN** the host leaves a `WAITING` game
- **THEN** the game is removed outright

#### Scenario: Leave during play

- **WHEN** a player tries to leave in `PLAYING`
- **THEN** it is refused `action-not-allowed` and resigning remains offered

#### Scenario: Leave after the game is over

- **WHEN** one player leaves a finished game
- **THEN** only that player's access ends, the other player's result is untouched, and a repeated leave answers as an unknown game

### Requirement: Seven actions change a game (R23)

Seven actions SHALL change a game: place a ship (which also moves and rotates an already placed ship atomically), remove a ship, arrange the whole fleet at random, clear the fleet, declare ready, fire, and resign. Placing a ship exactly where it already is, and removing a ship that is not placed, SHALL both succeed and change nothing: they are accepted, so they move the idle deadline and their identifier is recorded, but no view changes so the version does not rise.

#### Scenario: No-op placement

- **WHEN** a ship is placed exactly where it already is
- **THEN** the action is accepted, the version does not rise and nothing is pushed

#### Scenario: Atomic relocation

- **WHEN** a placed ship is placed at a new anchor
- **THEN** it moves atomically with no intermediate state

### Requirement: Repeat safety through action identifiers (R24)

Every action SHALL carry a caller-generated identifier. Re-sending an identifier already accepted SHALL return the current state without applying anything again; only the identifier is compared. An accepted identifier SHALL be remembered for the whole life of its game, so a retry is never applied twice however late it arrives.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The memory SHALL be bounded by construction (game lives at most the absolute lifetime and actions are rate limited). A retried action arriving after the game finished returns the current state; a fresh action for a finished game is refused (edge case). Success criterion S4: an action sent twice is applied once and the second answer shows the current state (US5-2).

#### Scenario: Lost answer retry

- **WHEN** the identical action identifier is sent again
- **THEN** the current state is returned and the action is not applied a second time

#### Scenario: Late retry

- **WHEN** a retry arrives many actions later
- **THEN** it is still not applied again

#### Scenario: Different identifier for same intent

- **WHEN** a new identifier is sent for the same intent
- **THEN** it applies again where the rules allow and is refused where they do not (proof area 4)

### Requirement: Actions for one game are serialized (R25)

Actions for one game SHALL be applied in a single order: authorization, repeat detection, the transition, the version increase and the notification of both players happen as one indivisible step. Actions for different games SHALL never wait on each other. Success criterion S7: simultaneous actions produce one ordered outcome with no lost update and no partially applied state (US5-5).

#### Scenario: Simultaneous actions on one game

- **WHEN** two actions arrive at the same instant
- **THEN** one ordered outcome results and both callers receive consistent state

#### Scenario: Different games

- **WHEN** actions run on different games
- **THEN** they do not block one another

#### Scenario: Both ready at once

- **WHEN** both players declare ready simultaneously
- **THEN** play starts once and the first turn is settled once

### Requirement: Proof areas 2, 4 and 5

Proof area 2 (owners `AllowedActionsTest`, `RefusedCommandStabilityTest`, `domain`) SHALL prove the offered-action table cell by cell, every action attempted in every phase it is not allowed in, and each refusal leaving the game and version unchanged. Proof area 4 (`CommandIdempotencyTest`, `application`) SHALL prove repeat safety.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Proof area 5 (`GameSerializationTest`, `application`, staged with a `CyclicBarrier` asserting the set of allowed outcomes) SHALL prove that simultaneous actions on one game linearize, different games do not block, and simultaneous ready starts play once.

#### Scenario: Ten proof areas stay owned

- **WHEN** the backend gate runs
- **THEN** these named test classes exist and fail when the guarded behaviour is removed
