## Purpose

Abuse limits and capacity: rate limiting, concurrent game and stream ceilings, per-browser game cap, request-body limits and the bounded-by-construction rule for every structure hostile callers can grow. Source: spec 002-backend R38, R39, R40, R41, R61, R62. Covers success criterion S11 and the capacity row of the evidence map (`CapacityIT`, `RateLimitIT`).

## ADDED Requirements

### Requirement: Rate limits (R38)

Requests SHALL be rate limited. The contract fixes three: game creation five per minute and join attempts twenty per minute, each per bounded caller-address key, and actions sixty per minute per session. A refusal SHALL state how long to wait, both as a `Retry-After` header and `retryAfterSeconds` in the body (equal, delta-seconds), with `429 rate-limit-exceeded`. Every key used for limiting SHALL be bounded.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** The algorithm is a fixed 60-second window per key per operation class; the caller-address key is the remote address used only for limiting and never logged.

#### Scenario: Sixth game creation in a minute

- **WHEN** the same address creates a sixth game within a minute
- **THEN** `429 rate-limit-exceeded` is returned with equal header and body retry values

### Requirement: Limits on unpinned operations (R61)

`[agent]` The operations the contract leaves unpinned (reading a game, presence, opening an event stream, replacing an invitation and leaving) SHALL also have a default per-session limit, because the rate-limit refusal is reachable on all of them. Defaults: read 120/min, presence 30/min, stream open 30/min, replace invitation 10/min, leave 10/min.

#### Scenario: Limit on read

- **WHEN** a session exceeds the read limit
- **THEN** it receives `429 rate-limit-exceeded`

### Requirement: Capacity ceilings never evict (R39)

The number of concurrent games and concurrent event streams SHALL be capped. Over either cap the new game or stream SHALL be refused as `503 service-unavailable` with a retry hint; no running game and no open stream SHALL ever be evicted to make room, and the service SHALL still report itself ready. Each allocation (a semaphore permit acquired before insertion) SHALL be released exactly once when the game or stream ends.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Success criterion S11: at either ceiling a new game or stream is refused with a retry hint while every running game and stream continues and the service stays ready.

#### Scenario: Game ceiling reached

- **WHEN** the concurrent-game cap is full and a new game is created
- **THEN** `503 service-unavailable` with `Retry-After` is returned and no running game is evicted

#### Scenario: Stream ceiling reached

- **WHEN** the stream cap is full
- **THEN** a new stream is refused `503` while open streams continue and health stays ready

### Requirement: Per-browser live game cap (R62)

`[agent]` One browser SHALL hold at most the configured number of live games at a time (default 1). A create or join refused by this limit SHALL answer `409 browser-game-limit` without a retry hint; only games that have not ended count, so a finished or abandoned game never blocks a new one, and leaving frees the allocation at once. Success criterion S11 (continued): a browser at its own game limit is refused with no retry hint and a game it has finished never counts.

#### Scenario: Second live game

- **WHEN** a browser at its limit creates another game
- **THEN** it is refused `browser-game-limit` with no `Retry-After`

#### Scenario: Finished game does not count

- **WHEN** the browser's game finished or it left
- **THEN** it can create a new game

### Requirement: Body size and content type (R40)

`[agent]` A request body larger than the configured ceiling SHALL be refused `413 payload-too-large`, and a request carrying a body whose content type is not the expected one SHALL be refused `415 unsupported-media-type`. The three body-less actions (replacing the invitation, presence, leaving) require no content type, never check one and accept any body or none; the body-size ceiling still applies to them. Both refusals use codes the contract already defines.

#### Scenario: Oversized body

- **WHEN** a body exceeds `max-request-body-bytes`
- **THEN** the answer is `413 payload-too-large`

#### Scenario: Body-less action with any body

- **WHEN** presence, replace-invitation or leave is sent with or without a body of any content type
- **THEN** no content-type refusal occurs

### Requirement: Everything hostile callers grow is bounded (R41)

Every structure an unauthenticated or hostile caller can grow (repeat-detection memory, rate-limit keys, session records, stream registries, queues and scheduled cleanup work) SHALL be bounded by construction. A full rate-limit table SHALL drop the keys unused for longest rather than refusing callers it has not seen, so a flood of invented keys never locks a real player out; the flood itself stays capped by the ceilings of R39.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Session records are bounded by the sweeper and a hard cap with drop-least-recently-used.

#### Scenario: Flood of invented keys

- **WHEN** the rate-limit table is full of invented keys
- **THEN** the least recently used are dropped and a real player is not locked out
