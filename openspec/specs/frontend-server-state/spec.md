# frontend-server-state Specification

## Purpose
Defines how the Battleship client treats the server as the only authority: the snapshot as the single source of truth, anti-forgery handling, ruleset facts, the version gate, addressing by public game id, action identity, problem mapping, unreadable answers, wire types from the contract only, and the contract and backend fixes found while building (R01-R06, R19, R22, R23, R56, R57). Source: the original workflow feature `003-frontend`.

## Requirements

### Requirement: Client is a view of the server snapshot (R01)

The client SHALL show only state taken from the latest server snapshot and SHALL NOT decide turn, hits, winner, expiry, statistics or action success locally. Placement controls MAY derive advisory legal anchors and orientations from the caller's known fleet and the published ruleset, but the server SHALL validate every command and its latest accepted snapshot SHALL remain authoritative. Display counts SHALL be derived only from the server's board and ruleset (R59).

#### Scenario: Board changes only on the server answer

- **WHEN** the player sends any game action
- **THEN** the board, turn and counts shown change only to what the server's answer or live update reports

#### Scenario: Advisory check disagrees with the server

- **WHEN** an advisory placement check permits a command that the server refuses
- **THEN** the server's refusal and snapshot win and the refusal is explained

### Requirement: Actions follow allowedActions (R02)

The client SHALL offer an action only when the server's current snapshot lists it in `allowedActions`, and SHALL drop unknown `allowedActions` values.

#### Scenario: Action not allowed

- **WHEN** the snapshot does not list FIRE
- **THEN** no fire control is offered

### Requirement: Anti-forgery token handling (R03)

Before its first state-changing request the client SHALL obtain the anti-forgery token from the server's metadata operation and SHALL echo it on every state-changing request. Because an anti-forgery refusal applied nothing, the client SHALL, for every request including creation and join, refresh the token and resend that request exactly once.

#### Scenario: Wrong token

- **WHEN** a state-changing request is answered `403 request-security-rejected`
- **THEN** the client performs one metadata read and resends the request once

### Requirement: Ruleset facts come from the server (R04)

The client SHALL read ruleset facts (board size, fleet, touching rule, extra-turn rule, revealed-water rule) from the server and SHALL hold no fleet or board constants.

#### Scenario: Unknown ruleset advertised

- **WHEN** the server advertises a ruleset the client has never seen
- **THEN** it is rendered from its published facts, with the raw identifier as name if no translation exists

### Requirement: Snapshot version gate (R05)

The client SHALL ignore a snapshot whose version is older than the one shown. A snapshot with the same version SHALL update only `expiresAt`, `serverTime`, `invitationUrl` and `invitationExpiresAt` (the fields the contract excludes from the version). A higher version SHALL replace the shown snapshot. The gate SHALL apply to every source: command answer, presence, invitation, join, create, `getGame` and live stream.

#### Scenario: Older update

- **WHEN** an update with a lower version arrives
- **THEN** it is dropped and the view is unchanged

#### Scenario: Equal version

- **WHEN** an update with the same version arrives
- **THEN** only expiry, server time and invitation fields change

### Requirement: Game addressed by public game id (R06)

A game screen SHALL be addressed by its public game id so that reload and live reconnect rebuild the view from the server. The client SHALL NOT put any secret, session value or player identifier in an address, query, storage or log.

#### Scenario: Reload on a game screen

- **WHEN** the player reloads `/games/:gameId`
- **THEN** the view is rebuilt from the server using the game address

### Requirement: One action in flight with a stable action identifier (R19)

Each user action SHALL get a new action identifier; a retry after a lost answer SHALL reuse it. Only one action per game SHALL be in flight and its control SHALL be disabled until it settles.

#### Scenario: Retry after lost answer

- **WHEN** the answer to an action is lost and the player retries
- **THEN** the same action is resent with the same identifier, never as a new one (US5.6)

### Requirement: Stable translated problem messages (R22)

Every problem code the contract defines SHALL map to a stable, translated message and one next action (retry, refresh, Home or new game). The client SHALL NOT show raw server text, stack traces, cookies or secrets. Rate-limit and busy answers SHALL show the server's wait hint and SHALL NOT be retried before it elapses.

#### Scenario: Rate limited

- **WHEN** the server answers 429 or 503 with a wait hint
- **THEN** a calm "try again shortly" message shows the hint, the retry control stays disabled until it elapses and there is no tight retry loop

#### Scenario: Unknown problem code

- **WHEN** the server answers with a code the client does not know
- **THEN** a status-class fallback message is shown

### Requirement: Unreadable answers are transport failures (R23)

An answer or live update that cannot be read as the contract's type SHALL be treated as a transport failure: the last view SHALL stay, the connection state SHALL show the problem, and the bad value SHALL NOT be stored.

#### Scenario: Proxy error page

- **WHEN** an HTML 502 body, truncated JSON or a JSON object missing `version` arrives
- **THEN** it is rejected and the previous snapshot remains

### Requirement: Wire types only from the contract (R56)

`[agent]` Wire types SHALL come only from the contract (generated from `contracts/openapi.yaml`); the client SHALL NOT add a second hand-written copy of the wire schema. Wire conformance SHALL be proven by the backend's contract assertions and by the real-backend journeys.

#### Scenario: Contract operation renamed

- **WHEN** an operation in the contract is renamed
- **THEN** the frontend typecheck fails

### Requirement: Contract and backend gaps are fixed in place (R57)

A gap or defect found in the contract or backend while building this feature SHALL be fixed in place, with every current dependent and proof updated in the same change. The fixes are: `browser-game-limit` 409 (T001), the lingering test JVM (T002, Assumption 5), Actuator off the public port (T003), and `GameSnapshot.version` excluding the invitation fields (T008).

#### Scenario: Browser at its live-game cap

- **WHEN** a browser that already holds its live game creates or joins
- **THEN** the server answers 409 `browser-game-limit` without `Retry-After`, while a full server still answers 503 with `Retry-After`

#### Scenario: Clean backend verify

- **WHEN** `./mvnw -q verify` finishes
- **THEN** no failsafe dumpstream contains "kill self fork JVM"

#### Scenario: Actuator absent

- **WHEN** the packaged JAR is asked for `/actuator/health`
- **THEN** it answers 404 and `/api/v1/health` still reports ready
