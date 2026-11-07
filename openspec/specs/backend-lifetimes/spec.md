# backend-lifetimes Specification

## Purpose
Every deadline in a game's life: idle expiry, absolute lifetime, result retention, invitation expiry, presence throttle, expiry sweeping, expired-game memory and the in-memory-only guarantee. Source: spec 002-backend R42-R48 and R63 plus the inclusive-boundary rule. Covers User Story US5, success criterion S5 and proof area 6 (lifetimes), and the edge case "a game expires in the middle of a request".

## Requirements

### Requirement: Deadlines are inclusive

Every deadline (game, invitation, result, throttle, stream) SHALL be reached inclusively: an object whose deadline equals the current instant has already expired, using one shared comparison `deadline <= now`.

#### Scenario: Boundary instant

- **WHEN** the time source is one millisecond before a deadline and then exactly at it
- **THEN** the object is alive and then expired

### Requirement: Idle expiry (R42)

A game SHALL expire after the configured idle period measured from the latest of its creation, the guest joining, an accepted action or an accepted presence signal. Any accepted action counts, including one that changes nothing (R23); the absolute lifetime bounds a client that holds a game open that way.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Reads, opening or reconnecting an event stream, replacing the invitation, keep-alive markers and refused actions SHALL never extend a game's life. Success criterion S5: a game left alone expires after the configured idle period, proven by advancing the injected time source.

#### Scenario: Read does not extend

- **WHEN** a player only reads the game
- **THEN** the idle deadline is not moved

#### Scenario: No-op accepted action extends

- **WHEN** an accepted action changes no view
- **THEN** the idle deadline moves

### Requirement: Absolute lifetime (R43)

A game in progress SHALL never play on past the configured absolute lifetime measured from creation, whatever its activity. The ceiling governs play, not the result: a game that finishes just before it is reached still keeps its result for the full retention period.

#### Scenario: Ceiling reached

- **WHEN** the absolute deadline is reached during play
- **THEN** the game expires despite activity

### Requirement: Result retention (R44)

A finished or abandoned game SHALL stay readable for the configured retention period, measured from the moment it ended, and then be removed. Success criterion S5 (continued): the result stays readable for exactly the retention period (US6-1).

#### Scenario: Retention window

- **WHEN** the retention period elapses
- **THEN** the game is removed and reads answer not found

### Requirement: Invitation expiry and replacement (R45)

An invitation SHALL expire after the configured invitation lifetime or when its game expires, whichever comes first. While the game is waiting the host MAY replace it; the previous link SHALL stop working the instant the new one is issued.

#### Scenario: Expired invitation

- **WHEN** the invitation lifetime elapses
- **THEN** joining with it is refused `invitation-unavailable`

### Requirement: Presence throttle (R46)

Each player MAY extend a game's idle deadline by presence at most once per configured presence interval, and the two players SHALL be throttled independently. Calls sooner SHALL return the current state and change nothing, including the version; an accepted signal moves the deadline, and because the deadline is not part of a view the version stays unchanged while the caller still receives a fresh `expiresAt`. Presence is an authenticated, forgery-protected request.

#### Scenario: Early presence

- **WHEN** a player sends presence before the interval elapsed
- **THEN** the current state is returned and nothing changes

#### Scenario: Independent throttles

- **WHEN** one player's presence is accepted
- **THEN** the other player's presence is still accepted

### Requirement: Expiry on access and by sweep (R47)

Expiry SHALL be checked on every access to a game and also swept in the background at the configured interval, so a late request can never resurrect an expired game. A request racing expiry is refused as expired or missing and never applies a partial transition. The sweeper's `tick()` reads the injected time source.

#### Scenario: Late request

- **WHEN** a request arrives after the deadline but before a sweep
- **THEN** it is refused and the game is not resurrected

### Requirement: Expired game memory (R63)

An expired game SHALL be remembered for the same retention period as a finished one, measured from the expiry instant. While remembered, protected game operations SHALL return `410 game-expired` to its own players and `404 game-unavailable` to other callers. After the period its own players receive 404 too.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** `joinGame` uses its invitation-refusal shape: an existing guest receives `410 game-expired` during retention, the host and nonmembers receive `409 invitation-unavailable`, and after retention the forgotten game answers `409` even to the former guest (R34).

#### Scenario: Own player during retention

- **WHEN** an expired game's player reads it within retention
- **THEN** the answer is `410 game-expired`

#### Scenario: Stranger

- **WHEN** another caller reads the expired game
- **THEN** the answer is `404 game-unavailable`

### Requirement: Memory-only state (R48)

All state SHALL be in memory. A restart SHALL end every game and every session; there is no persistence, no recovery and no claim of continuity.

#### Scenario: Restart

- **WHEN** the service restarts
- **THEN** no game or session survives

### Requirement: Proof area 6 - lifetimes

Proof area 6 (owners `LifetimeBoundaryTest`, `StatisticsTest`, `application`) SHALL prove idle expiry, the absolute ceiling, result retention, invitation expiry, the presence throttle and the stream lifetime each at its inclusive boundary (alive at deadline minus 1 ms, expired at deadline) by advancing the injected time source, together with the statistics durations of R51.

#### Scenario: Proof exists

- **WHEN** the application tests run
- **THEN** every deadline is proven without sleeping
