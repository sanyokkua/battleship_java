## Purpose

Defines play to the end: firing, ruleset-specific turns, live opponent updates, mobile board handoff, non-colour feedback, sound and haptics, and the results screen with server statistics (US4, R20, R21, R31, R32, R36, S1, S5). Source: the original workflow feature `003-frontend`.

## ADDED Requirements

### Requirement: Play to the end and see the results (US4)

Two players SHALL be able to play one full game per ruleset in two browsers; turn changes, shot results, sunk ships and revealed water SHALL follow that ruleset, and the results screen SHALL show the server's statistics. Two people in separate browsers SHALL be able to complete a full game under each advertised ruleset against the real backend with each ruleset's distinct turn and revealed-water behaviour visible (S1).

#### Scenario: Firing

- **WHEN** it is the player's turn and they fire at an unknown cell
- **THEN** one shot is sent, that cell is blocked while pending, and the result, turn and both boards come from the server

#### Scenario: Ruleset turn behaviour

- **WHEN** a Sea Battle hit occurs
- **THEN** the same player keeps the turn and a sunk ship's surrounding water is shown distinct from fired water
- **WHEN** a Classic hit occurs
- **THEN** the turn passes

#### Scenario: Opponent fires

- **WHEN** the opponent fires and the update arrives live
- **THEN** the player's own board, turn and a short announcement update without a reload; on the narrow single-board layout the last shot is shown and pulsed on the board it affected, and when that shot changes the turn its result stays visible for one second before the board for the next player is shown; a retained turn does not switch boards

#### Scenario: Finished game

- **WHEN** the game finishes
- **THEN** results show win or loss, both revealed boards, fleet condition (intact, damaged, sunk) and four statistic groups (match time: total, placement, gameplay, each player's placement; turns and accuracy: turns, shots, hits, accuracy; turn timing: total, average, fastest, slowest; and `[user]` shot-decision timing: each player's total, average, fastest and slowest accepted-shot decisions) all as supplied by the server

#### Scenario: Leave results

- **WHEN** the player chooses Leave results
- **THEN** their access to that result ends and they return Home

### Requirement: Conflict refusals refresh the view (R20)

On a conflict refusal other than invitation unavailable (which has no game to read) the client SHALL refresh the snapshot and show the server's view plus a worded reason for the refusal code.

#### Scenario: Action no longer allowed

- **WHEN** a refusal arrives for an action that is no longer allowed
- **THEN** the view refreshes from the server and the refusal is explained

### Requirement: Fire only on unknown cells, one shot pending (R21)

Fire SHALL be offered only on unknown cells of the opponent board while the server allows it, with one shot pending at a time. `[user]` A new shot's affected cell SHALL be visibly highlighted with the approved pulse, and a turn-changing shot in the narrow tabbed layout SHALL stay visible for one second before the active board changes. Both-board layouts SHALL remain usable.

#### Scenario: Rapid shots

- **WHEN** a newer shot arrives during a pending handoff
- **THEN** the pending timer is replaced

#### Scenario: Initial load or reconnect

- **WHEN** the screen loads or reconnects
- **THEN** the board for the current server turn is selected without replaying a stale shot

#### Scenario: Reduced motion

- **WHEN** reduced motion is requested
- **THEN** a static highlight is shown and no animation plays

#### Scenario: Board handoff

- **WHEN** a turn-changing shot arrives in the narrow tabbed layout
- **THEN** after one second the opponent's turn shows the player's fleet and the player's turn shows enemy waters, retained turns cause no switch and a manual tab selection cancels a pending switch

### Requirement: State changes understandable without colour or animation (R31)

Every board change SHALL be understandable without colour or animation: text, symbol or pattern, plus a concise live announcement. `[user]` Empty water and ship cells SHALL use cell styling or patterns rather than repeated visible glyphs; ordinary water and ships SHALL show no white dots or square glyphs.

#### Scenario: Announcement

- **WHEN** a new shot result arrives
- **THEN** a concise live announcement is made once per `lastShot`

### Requirement: Optional sound and haptics (R32)

Optional synthesized sound for miss, hit and sunk, and optional haptics, SHALL play once per new shot result from the server; failure, denial, background or reduced motion SHALL NOT block anything. Turning sound or haptics on, off or denied SHALL NOT change the outcome or timing of any journey (S5).

#### Scenario: Haptics unsupported

- **WHEN** sound or haptics are denied or unsupported
- **THEN** play and results are unaffected

### Requirement: Results render server statistics (R36)

Results SHALL render the server's outcome, both revealed boards, fleet condition and every statistic listed in US4 scenario 4, mapped to fields as in the design's UI surface contract; no statistic SHALL be computed locally (R01's display counts are not statistics). An absent optional value SHALL show an em dash.

#### Scenario: Zero-shot resignation

- **WHEN** a result has no shots
- **THEN** absent aggregates show an em dash and rows match each player's snapshot

#### Scenario: Retained-turn shot timing

- **WHEN** a shot retained the turn
- **THEN** the shot-decision timing is not substituted by turn timing

### Requirement: Abandoned result view (US5.2, R38)

An abandoned result SHALL show a "Game abandoned" message with no boards and no statistics, and a Leave results action.

#### Scenario: Abandoned

- **WHEN** the phase is `ABANDONED`
- **THEN** no boards or statistics are revealed
