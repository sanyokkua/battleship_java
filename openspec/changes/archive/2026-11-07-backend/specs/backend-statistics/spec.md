## Purpose

Server-calculated statistics of a finished game. Source: spec 002-backend R51 and R60 (injected time), data-model Statistics boundaries. Covers User Story US6 (result and statistics) and the Key Entity Statistics; co-owns proof area 6 (lifetimes, statistics durations).

## ADDED Requirements

### Requirement: Finished games carry server-calculated statistics (R51)

A finished game SHALL carry the statistics the contract defines, calculated by the server from the transitions it accepted and the injected time source (R60). The contract fixes every field, unit, clock boundary and rounding. Every identity the contract states SHALL hold in the delivered values; an abandoned game SHALL carry no statistics at all; a client SHALL format these values and never recompute them.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Statistics are recorded in a `Timeline` from accepted transitions only (US6-1).

#### Scenario: Finished game statistics

- **WHEN** either player reads a finished game
- **THEN** the outcome, both boards and statistics are present and stay readable for the retention period

#### Scenario: Abandoned game

- **WHEN** a game is abandoned
- **THEN** no statistics are present

### Requirement: Sampling boundaries

The statistics SHALL use these boundaries: instants `guestJoinedAt` (join accepted), `readyAt[seat]` (that seat's READY accepted), `playStartedAt` (second READY accepted), `finishedAt` (sinking or resignation). `match.totalDurationMs` = guestJoinedAt to finishedAt; `match.placementDurationMs` = guestJoinedAt to playStartedAt;

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** `match.gameplayDurationMs` = playStartedAt to finishedAt; per-player `placementDurationMs` = guestJoinedAt to readyAt[seat]; `shots` = accepted FIRE commands by that seat; `hits` = those resulting HIT or SUNK; `accuracy` = hits/shots to 4 decimals, absent when shots is 0; `turns` = one sample per turn from gaining the right to fire until it passes or the game ends (turnStartedAt starts at playStartedAt, then the instant of each pass, and the holder's open turn is closed at finishedAt); `shotDecisions` = one sample per accepted FIRE, reset at every turnStartedAt and after each accepted FIRE that retains the turn, and not reset by a refused FIRE; `fleet` = that seat's own fleet at finishedAt (`intact` no hits, `damaged` some hits not sunk, `sunk` all cells hit). `averageMs` = round(totalMs / count); `fastestMs`, `slowestMs`, `averageMs` are absent when count is 0.

#### Scenario: Boundaries from the injected clock

- **WHEN** the time source is advanced between transitions
- **THEN** every duration equals the advanced interval for its boundary

#### Scenario: Empty aggregates

- **WHEN** a seat has zero turns or zero shot decisions
- **THEN** fastest, slowest and average are absent

### Requirement: Statistical identities hold

The delivered values SHALL satisfy: total = placement + gameplay; you.turns.totalMs + opponent.turns.totalMs = match.gameplayDurationMs; shotDecisions.count = shots; intact + damaged + sunk = total fleet size.

#### Scenario: Identities

- **WHEN** a finished game's statistics are checked
- **THEN** all four identities hold (proof owner `StatisticsTest`, `application`, proof area 6)
