# backend-realtime Specification

## Purpose
Live updates over a server-sent event stream: first message is the current snapshot, per-player delivery, deliberate closes, heartbeats and bounds, one stream per player and game. Source: spec 002-backend R27-R31. Covers User Story US5 (dropped stream, second tab), success criterion S8 and proof area 8 (live updates).

## Requirements

### Requirement: First message is the current snapshot (R27)

An event stream's first message SHALL always be the caller's current snapshot, so connecting and reconnecting need no other request and there is nothing to replay. `Last-Event-ID` is ignored. Success criterion S8: a reconnecting player is fully current from the first delivered message alone, with no replay, no gap and no second request (US5-3).

#### Scenario: Reconnect after a drop

- **WHEN** a new stream is opened after a dropped one
- **THEN** its first message is the current snapshot

#### Scenario: Transition racing registration

- **WHEN** a transition races with stream registration
- **THEN** it is either included in the first snapshot or buffered and delivered after it, never lost

### Requirement: Snapshots delivered only when the player's own view changes (R28)

A snapshot SHALL be delivered to a player only when that player's own view changes (R18), and each message SHALL be identified by that snapshot's version (event `id` = version, event name `snapshot`). A change only the other player can see, such as arranging a fleet during placement, SHALL NOT be delivered, so that player's versions skip.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The content of a private action SHALL never be delivered; only that some action was accepted is inferable, from `expiresAt`. A late publish with a version at or below the last queued or sent version is discarded.

#### Scenario: Private placement change

- **WHEN** one player rearranges their fleet
- **THEN** the opponent receives no event

### Requirement: Deliberate closes (R29)

The server SHALL end a stream deliberately in two cases and say which: `REPLACED` (the same browser opened a newer stream for this game) and `GAME_UNAVAILABLE` (the game has stopped existing for this browser, as for the player who left, while the player who stayed receives an ordinary snapshot). A finished or abandoned game SHALL arrive as an ordinary snapshot and keep the stream open. Shutdown is not one of these cases (R59).

#### Scenario: Second tab

- **WHEN** a second tab of the same browser opens a stream for the same game
- **THEN** the older stream receives `closed` with reason `REPLACED` and stops (US5-4)

#### Scenario: Player left

- **WHEN** a player leaves a finished game
- **THEN** that player's stream closes with `GAME_UNAVAILABLE` while the other receives an ordinary snapshot

### Requirement: Heartbeat, lifetime and slow recipients (R30)

A keep-alive marker (`: keep-alive` comment) SHALL be sent at the published heartbeat interval. A stream SHALL have a maximum lifetime after which it ends without a deliberate-close message so the browser reconnects. A slow or failing recipient SHALL be disconnected rather than allowed to accumulate unbounded work (single coalescing slot holding only the latest unsent snapshot).

#### Scenario: Maximum lifetime

- **WHEN** the time source reaches `stream-max-lifetime-seconds`
- **THEN** the stream ends with no `closed` event

#### Scenario: Stalled writer

- **WHEN** several transitions arrive for a stalled recipient
- **THEN** only the latest snapshot stays pending and a failed write drops the subscriber

### Requirement: One stream per player and game (R31)

At most one event stream SHALL be kept per player and game; a newer one replaces the older. Having a stream open SHALL mark the player as connected but SHALL NOT keep the game alive. Opening the first stream or closing the last changes the visible connected flag and bumps the version once; replacement changes neither.

#### Scenario: Opening a stream does not extend life

- **WHEN** a player opens or reconnects a stream
- **THEN** the idle deadline is not extended

### Requirement: Proof area 8 - live updates

Proof area 8 (owner `EventStreamIT`, `app`) SHALL prove: first message is the current snapshot, a change reaches only the players whose own view changed, a replacement stream closes the older with its reason, a vanished game closes the stream with its reason, heartbeats arrive, and the stream lifetime ends a stream, all by advancing the injected time source via package-visible `tick()`.

#### Scenario: Proof exists

- **WHEN** `EventStreamIT` runs
- **THEN** each listed behaviour is asserted without sleeping
