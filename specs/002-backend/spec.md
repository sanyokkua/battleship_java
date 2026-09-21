# Feature Specification: Battleship Backend

**Feature Branch**: `feature/002-backend`
**Created**: 2026-09-21
**Status**: Planned — see [plan.md](plan.md)

**Input**: "Create feature `002-backend` for the production-ready anonymous Battleship rewrite. Implement
one server-authoritative, ephemeral backend that conforms to the published `001-api-contract` contract.
The backend has no database, no durable state, no frontend assets, and no alternate public API."
(`docs/rewrite_context_doc/Planning/04-backend/specify-input.md`)

## Authority

`contracts/openapi.yaml` is the source of truth for every externally visible behaviour: operations,
schemas, statuses, problem codes, headers, cookies, events and privacy rules. This specification
describes the behaviour a server must have in order to be that contract's implementation, and does not
restate the contract where the contract is already precise. The seed documents under
`docs/rewrite_context_doc/Planning/04-backend/` are context, not authority; where they and the contract
disagree, the contract wins.

Requirements are `[user]` unless tagged `[agent]` — proposed by the agent and approved by the owner.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — A host creates a game and shares an invitation (Priority: P1)

A person picks a ruleset and a name, gets a private game, and receives one link to send to a friend.

**Independent Test**: Create a game with each ruleset and confirm the answer is a `WAITING` snapshot
that carries a shareable invitation link and an expiry the host can show.

**Acceptance Scenarios**:

1. **Given** a browser with no session, **When** it creates a game with a valid ruleset and name,
   **Then** it becomes the host of a `WAITING` game, receives an anonymous session, and the snapshot
   carries the invitation link and its expiry.
2. **Given** a host in `WAITING`, **When** the invitation has expired or was sent to the wrong person,
   **Then** the host can replace it, the new link is in the snapshot, and the previous link stops
   working immediately.

### User Story 2 — A guest redeems the link and joins (Priority: P1)

A person opens the link, confirms a name, and becomes the second player.

**Independent Test**: Redeem a fresh invitation once and confirm both players see `PLACEMENT`; redeem
the same invitation a second time from a third browser and confirm it is refused identically to a wrong
secret.

**Acceptance Scenarios**:

1. **Given** a valid, unused, unexpired invitation, **When** a second browser joins with a name,
   **Then** the game moves to `PLACEMENT`, that browser receives an anonymous session, and both players
   see each other's names.
2. **Given** a browser that is already this game's guest, **When** it joins again with the same
   invitation, **Then** it receives the current snapshot and nothing changes.
3. **Given** a link that was opened but not confirmed, **When** nothing else happens, **Then** no seat
   is claimed and the invitation stays usable until it expires.

### User Story 3 — Both players arrange fleets privately and declare ready (Priority: P1)

Each player places a fleet without the other seeing anything, then locks it in.

**Independent Test**: Arrange a full fleet by hand and randomly under each ruleset, declare ready on
both sides, and confirm play starts and neither player's snapshot ever contained the other's placement.

**Acceptance Scenarios**:

1. **Given** `PLACEMENT`, **When** a player places, moves, rotates, removes, randomises or clears ships,
   **Then** each accepted action is reflected in that player's own board only, and the opponent's view
   of that board is unchanged.
2. **Given** an arrangement that would leave the board, overlap another ship, or — under the ruleset
   that forbids it — touch another ship even diagonally, **When** it is attempted, **Then** it is
   refused with the reason, and the player's board and the game are exactly as they were.
3. **Given** a fleet that is not completely placed, **When** the player tries to declare ready,
   **Then** the action is not offered and is refused if sent anyway.
4. **Given** a player who has declared ready, **When** that player tries to edit the fleet, **Then** it
   is refused; ready cannot be undone.
5. **Given** both players ready, **When** the second one readies, **Then** play starts and the first
   turn is chosen by the server.

### User Story 4 — Players fire in turns until there is a winner (Priority: P1)

Players shoot at each other's boards under the active ruleset until a fleet is gone or someone resigns.

**Independent Test**: Play a complete game to a destroyed fleet under each ruleset and a second game to
a resignation, and confirm the result, both boards and the statistics.

**Acceptance Scenarios**:

1. **Given** `PLAYING` and it is my turn, **When** I fire at an undisclosed cell, **Then** the answer
   says whether it was a miss, a hit or a sinking, and the turn moves or stays according to the
   ruleset's extra-turn rule.
2. **Given** the ruleset that reveals water around a sunk ship, **When** a ship sinks, **Then** the
   still-undisclosed cells around it become visible as water without anyone firing at them; under the
   other ruleset they stay undisclosed.
3. **Given** the last ship of a fleet sinks, or a player resigns, **When** that happens, **Then** the
   game finishes, the winner and the reason are stated, both full boards become visible to both
   players, and the statistics are present.

### User Story 5 — A player recovers after a reload, a lost answer, a dropped stream or a second tab (Priority: P2)

Nothing a browser does — reloading, losing an answer, losing a connection, opening a second tab —
leaves a player guessing about the state of the game.

**Independent Test**: For each of the four events, confirm the player is back on the exact current state
with one request and that no action was applied twice or lost.

**Acceptance Scenarios**:

1. **Given** any point in a game, **When** the page is reloaded, **Then** one read returns everything
   needed to continue, including whose turn it is and what the player may do.
2. **Given** an action whose answer was lost, **When** the identical action is sent again, **Then** the
   current state is returned and the action is not applied a second time.
3. **Given** an open event stream that drops, **When** a new one is opened, **Then** its first
   message is the current state, with no gap to fill in and nothing to replay.
4. **Given** a second tab of the same browser opening an event stream for the same game, **When** it
   connects, **Then** the older stream is told it was replaced and stops, and only one stream
   per player and game stays open.
5. **Given** two actions for the same game arriving at the same instant, **When** they are processed,
   **Then** one ordered outcome results, both callers receive a consistent state, and no half-applied
   state is ever visible.

### User Story 6 — Players see the result and statistics, then leave (Priority: P2)

After the game ends, both players can study what happened and then walk away.

**Independent Test**: Finish a game, read the result from both sides, confirm the statistics are
internally consistent, then leave from both sides and confirm the game is gone for both.

**Acceptance Scenarios**:

1. **Given** a finished game, **When** either player reads it, **Then** the outcome, both boards and
   server-calculated statistics are shown, and the result stays readable for the published retention
   period.
2. **Given** a finished or abandoned game, **When** a player leaves, **Then** only that player's access
   ends; the other player's result is untouched.
3. **Given** a game that has not started play, **When** a player leaves, **Then** the game ends for both
   as abandoned and neither placement is ever revealed.
4. **Given** a game in play, **When** a player tries to leave, **Then** it is refused as an action that
   is not allowed; resigning stays offered to both players throughout play, so the game always ends
   with a stated winner rather than vanishing.

### User Story 7 — An operator runs and observes the packaged server locally (Priority: P3)

Someone starts the service on a laptop, sees that it is ready, changes a limit, and can diagnose a
failure from the logs.

**Independent Test**: Start the packaged artifact with default configuration, confirm it reports ready
and publishes the default limits; restart it with a changed limit supplied only as configuration and
confirm the running service reports the changed value.

**Acceptance Scenarios**:

1. **Given** a running service, **When** a shutdown is requested, **Then** it stops accepting new work,
   reports not-ready, ends event streams, allows in-flight actions the configured drain window, and
   exits without pretending games survive.

### Edge Cases

- **Both players declare ready at the same instant** (R11, R25). Play starts once, the first turn is
  decided once, and both players see the same starting state.
- **A game expires in the middle of a request** (R47). The request is refused as expired or missing; it
  never resurrects the game, and no partial transition is applied.
- **A player opens many event streams in a loop** (R31, R61). Each new one replaces the previous one for
  that player and game, the total number of streams stays bounded, and excess attempts are rate limited.
- **A retried action arrives after the game has finished** (R24). The current state is returned and
  nothing is re-applied; a fresh action for a finished game is refused.

## Requirements *(mandatory)*

### Service surface

- **R01** The deliverable is one runnable backend service that implements every operation of
  `contracts/openapi.yaml` under `/api/v1` — `getMeta`, `listRulesets`, `getHealth`, `createGame`,
  `getGame`, `joinGame`, `sendCommand`, `streamGameEvents`, `replaceInvitation`, `sendPresence`,
  `leaveGame` — and exposes no other public surface, no user-interface assets, and no second game engine.
- **R02** Service information reports the contract version the server implements, the server's
  current time, and the six published limits: idle lifetime, absolute game lifetime, result retention,
  invitation lifetime, presence interval and event-stream heartbeat interval. The published values
  are the values the running service actually enforces (see R55).
- **R03** The ruleset listing publishes both rulesets with their board size, fleet composition
  and the three rule flags. Rulesets are immutable; a corrected ruleset is published under a new
  identifier rather than edited.
- **R04** Health reports liveness and readiness: ready once the service accepts work, not ready
  while starting and while shutting down, each with its reason. A service that is full is still ready.

### Rules of the game

- **R05** Exactly two rulesets exist. Their identifiers, boards, fleets and flags are product data,
  published by `listRulesets`, and are defined here:

  | `id` | Board | Fleet (`shipTypeId` × count, length) | `shipsMayTouch` | `extraTurnOnHit` | `revealWaterAroundSunk` |
  |---|---|---|---|---|---|
  | `sea-battle-10-ship.v1` | 10×10 | `ship-4`×1 (4), `ship-3`×2 (3), `ship-2`×3 (2), `ship-1`×4 (1) | `false` | `true` | `true` |
  | `hasbro-classic-2002.v1` | 10×10 | `carrier`×1 (5), `battleship`×1 (4), `destroyer`×1 (3), `submarine`×1 (3), `patrol-boat`×1 (2) | `true` | `false` | `false` |

- **R06** Where `shipsMayTouch` is false, two ships may not be adjacent, not even diagonally, and a
  placement that would touch is refused with the touching reason. Where it is true, ships may touch.
- **R07** Where `extraTurnOnHit` is true, a player who hits or sinks fires again; where it is false,
  the turn passes after every shot, hit or miss.
- **R08** Where `revealWaterAroundSunk` is true, when a ship sinks, every still-undisclosed neighbour
  cell of every cell of that ship — up to eight per cell — becomes visible as water without a shot.
  Where it is false, nothing is revealed.
- **R09** Ships are straight, horizontal or vertical, lie entirely on the board, and never
  overlap. A coordinate outside the ruleset's board, or a ship identifier the fleet does not contain, is
  a field-level validation failure. A ship whose anchor is on the board but which would extend past the
  edge, an overlap, and (where forbidden) a touch are each refused with their own distinct reason.
- **R10** A shot names one cell of the opponent's board. A coordinate outside the ruleset's
  board is a field-level validation failure. A cell that has already been disclosed is refused, costs
  nothing, and does not pass the turn.
- **R11** A game ends when a fleet is completely sunk or when a player resigns; in both cases
  the other player wins and the reason is stated. The player who declared ready first fires first: the
  server records each player's ready moment and compares them, and breaks an exact tie randomly from the
  injectable source of R26. The choice is first visible when play starts.

### Phases and allowed actions

- **R12** A game moves `WAITING` → `PLACEMENT` → `PLAYING` → `FINISHED`, or to `ABANDONED` when
  a player leaves before play starts. There is no other transition and no way back.
- **R13** Every snapshot states exactly what its recipient may do at that moment. This table is the
  single authority for the offered set: the server computes it per player per moment, a client never
  derives permission from the phase, the turn or anything else, and nothing outside the table is ever
  offered.

  | Situation | `allowedActions` |
  |---|---|
  | `WAITING`, the host | `NEW_INVITATION`, `SEND_PRESENCE`, `LEAVE` |
  | `PLACEMENT`, not ready, fleet incomplete | `PLACE_SHIP`, `REMOVE_SHIP`, `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `SEND_PRESENCE`, `LEAVE` |
  | `PLACEMENT`, not ready, fleet complete | the above **+** `READY` |
  | `PLACEMENT`, already ready | `SEND_PRESENCE`, `LEAVE` |
  | `PLAYING`, the player whose turn it is | `FIRE`, `RESIGN`, `SEND_PRESENCE` |
  | `PLAYING`, the other player | `RESIGN`, `SEND_PRESENCE` |
  | `FINISHED` or `ABANDONED`, either player | `LEAVE` |

- **R14** During placement the four fleet-editing actions stay available until the player
  declares ready; declaring ready is offered only when the whole fleet is placed, cannot be undone, and
  starts play once both players have done it.
- **R15** A refused action changes nothing: the game's state and its version are exactly what
  they were before the attempt.
- **R16** Leaving is allowed only when it is offered, and `LEAVE` is never offered during play, which is
  what makes this refusal consistent with R13 rather than circular. Before play, leaving ends the game
  for both as abandoned with nothing revealed (a game nobody joined is simply removed); during play it
  is refused in favour of resigning; after the game is over it removes only the caller's access.
  Afterwards that browser is told the game does not exist, including for a repeated leave.

### The snapshot and player privacy

- **R17** Every successful game operation answers with the same caller-relative snapshot, and
  the identical document is what the event stream delivers. There is no second representation of a game.
- **R18** A player's **view** of a game is that player's snapshot excluding `serverTime` and
  `expiresAt`. The version rises by one when either player's view changes, and never otherwise — not on
  a read, not on a refused action, and not on an accepted action that changes no view (R23).
  `serverTime` and `expiresAt` are always current in whatever snapshot the server produces, so every
  response carries fresh values regardless of the version. The version exists only to order snapshots
  that arrive by different routes; it is never a precondition for an action, and its numbers may skip,
  because a player is only sent the versions their own view reached (R28).

  `contracts/openapi.yaml` now states the same rule: `GameSnapshot.version` grows when either player's
  **view** changes, that view being their snapshot excluding `serverTime` and `expiresAt`. Its earlier
  wording — the version grows "whenever anything in either player's snapshot changes" — would, read
  literally, bump the version on every read, because `serverTime` is part of every snapshot. It was
  amended in this feature alongside the `Phase.PLAYING` first-turn wording; both are description-level
  changes that leave the wire JSON byte-identical, so `001-api-contract` R24 permits them within v1.
- **R19** A player is never sent the opponent's placement, with exactly three exceptions: cells
  that were hit or sunk, water that the ruleset revealed around a sunk ship, and the opponent's complete
  board once the game is finished. An abandoned or expired game discloses no part of the opponent's
  placement at all; each player still sees their own board (R20).
- **R20** A player's own board is complete and drawable as it stands, with no undisclosed cells.
  From placement onwards it lists the entire fleet, including ships that are not on the board yet.
- **R21** `[agent]` Each ship is given a server-generated identifier when its fleet is created, so that
  a ship can be addressed before it has ever been placed. An identifier is unique within its own
  player's board — the two boards may use the same values, since each player has their own field — is
  stable for the whole game, survives clearing and random rearrangement unchanged, and carries no hidden
  information. Any value of the contract's identifier shape is acceptable; the contract's examples are
  illustrations, not a required format.
- **R22** The snapshot carries the most recent accepted shot, whether each player currently has
  an open event stream, and how many ships each player has left, so that a client needs no diffing,
  counting or rule knowledge of its own. The connection flag changes the opponent's view like anything
  else and is delivered at once; a stream replaced by a newer one from the same player is not a
  disconnection and produces no flag change, so a reload never flickers.

### Actions and repeat safety

- **R23** Seven actions change a game: place a ship (which also moves and rotates an already
  placed ship atomically), remove a ship, arrange the whole fleet at random, clear the fleet, declare
  ready, fire, and resign. Placing a ship exactly where it already is, and removing a ship that is not
  placed, both succeed and change nothing: they are accepted, so they move the idle deadline (R42) and
  their identifier is recorded (R24), but no view changes, so the version does not rise (R18).
- **R24** Every action carries a caller-generated identifier. Re-sending an identifier that was
  already accepted returns the current state without applying anything again; only the identifier is
  compared, not the rest of the request. An accepted identifier is remembered for the whole life of its
  game, so a retry is never applied a second time however late it arrives. That memory is bounded by
  construction rather than by a window: a game lives at most the absolute lifetime and its actions are
  rate limited, so the number of identifiers one game can accumulate has a fixed ceiling.
- **R25** Actions for one game are applied in a single order: authorization, repeat detection,
  the transition, the version increase and the notification of both players happen as one indivisible
  step. Actions for different games never wait on each other.
- **R26** `[agent]` Arranging the fleet at random produces an arrangement that is legal under the active
  ruleset and replaces whatever was there. The randomness comes from an injectable source, so a specific
  arrangement can be reproduced in a proof rather than only smoke-tested, and the same source breaks a
  tied first turn (R11). The search is attempt-bounded: after the configured number of failed attempts
  it gives up, refuses the action as an ordinary failure and leaves the fleet exactly as it was, so a
  search can never occupy its game indefinitely.

### Live updates

- **R27** An event stream's first message is always the caller's current snapshot, so
  connecting and reconnecting need no other request and there is nothing to replay.
- **R28** A snapshot is delivered to a player only when that player's own view of the game
  changes (R18), and each message is identified by that snapshot's version. A change only the other
  player can see — arranging a fleet during placement — is not delivered, so that player's versions
  skip. The *content* of a private action is never delivered; only that some action was accepted is
  inferable, from `expiresAt`.
- **R29** The server ends a stream deliberately in two cases and says which: the same
  browser opened a newer stream for this game, or the game has stopped existing for this browser —
  which is the case for the player who left, while the player who stayed receives an ordinary snapshot.
  A finished or abandoned game arrives as an ordinary snapshot and keeps the stream open. Shutdown is
  not one of these cases; R59 states what happens then.
- **R30** A keep-alive marker is sent at the published heartbeat interval; a stream has a
  maximum lifetime after which it ends without a deliberate-close message; a slow or failing recipient is
  disconnected rather than allowed to accumulate unbounded work.
- **R31** At most one event stream is kept per player and game; a newer one replaces the
  older one. Having a stream open marks the player as connected but does not keep the game alive.

### Identity, authorization and request security

- **R32** Identity is an anonymous, script-unreadable browser session, issued when a browser
  creates or joins its first game and reused for every game of that browser. Its value is high-entropy
  and generated from a cryptographically secure source, and the server retains only a digest of it, so
  a copy of the server's memory does not yield a usable session (Constitution II). No credential ever
  appears in a URL, a game locator, a snapshot, a log or script-readable storage.
- **R33** A game identifier locates a game and grants nothing. Authentication is evaluated first: a
  caller with no session, or with a session the server does not know, receives `session-required`. A
  caller with a valid session who is not one of a game's players receives the same answer as for a game
  that never existed, indistinguishable in status, body and timing-independent content. `createGame` and
  `joinGame` never answer `session-required` — they issue a session.
- **R34** The invitation secret is a high-entropy value generated from a cryptographically
  secure source. It travels only in the link fragment and in the join request body, is consumed
  atomically by the first successful join, and is invalidated by replacement, abandonment, expiry or
  use. Every refusal of a join — unknown game, wrong, used, replaced or expired secret, seat taken, or
  the host itself — is the same answer. Membership is checked before the secret: a browser that already
  holds this game's guest seat receives the current state whatever secret it presents, so a reload after
  the host replaced the invitation never locks the guest out of their own game.
- **R35** Every state-changing request must echo the readable anti-forgery token that the
  service information request establishes; a missing or wrong token is refused as a rejected request.
- **R36** The service additionally refuses state-changing requests that browser-set origin and
  fetch-metadata headers show to be cross-site. Absent headers are not a refusal: a caller that is not a
  browser — a script, a command-line tool, an integration test — is admitted and still has to present
  the anti-forgery token of R35. The interface and the API share one origin, so cross-origin sharing is
  not configured at all.
- **R37** Requests reject unknown fields, so a caller can never supply a seat, a turn, a winner
  or a result. Field-level failures are reported per field with a machine-readable rule. Every response
  is marked not to be stored by caches.
- **R64** A display name is trimmed of surrounding whitespace and normalised to Unicode NFC before it is
  validated; control characters are rejected; length is counted in code points after normalisation and
  must lie within the bounds the contract's `DisplayName` schema states. A name that fails any of these
  is a field-level validation failure (R37) and no game or seat is created.

### Abuse limits and capacity

- **R38** Requests are rate limited. The contract fixes three: game creation five per minute and join
  attempts twenty per minute, each per bounded caller-address key, and actions sixty per minute per
  session. A refusal states how long to wait, both as a header and in the body, and every key used for
  limiting is bounded.
- **R61** `[agent]` The operations the contract leaves unpinned — reading a game, presence, opening an
  event stream, replacing an invitation and leaving — also have a default limit per session, because the
  rate-limit refusal is reachable on all of them (defaults in R52).
- **R39** The number of concurrent games and the number of concurrent event streams are capped. Over
  either cap the new game or the new stream is refused as unavailable with a retry hint; no running game
  and no open stream is ever evicted to make room, and the service still reports itself ready. Each
  allocation is released exactly once when the game or stream ends.
- **R62** `[agent]` One browser additionally holds at most the configured number of live games at a
  time, so a single browser cannot fill the server. Only games that have not ended count, so a finished
  or abandoned game never blocks a new one, and leaving frees the allocation at once.
- **R40** `[agent]` A request body larger than the configured ceiling is refused as too large, and a
  request that carries a body whose content type is not the expected one is refused as an unsupported
  type. The three body-less actions — replacing the invitation, presence and leaving — require no
  content type, never check one, and accept any body or none; the body-size ceiling still applies to
  them. Both refusals use codes the contract already defines.
- **R41** Every structure an unauthenticated or hostile caller can grow — repeat-detection
  memory, rate-limit keys, session records, stream registries, queues and scheduled cleanup work —
  is bounded by construction. A full rate-limit table drops the keys unused for longest rather than
  refusing callers it has not seen before, so a flood of invented keys never locks a real player out;
  the flood itself stays capped by the ceilings of R39.

### Lifetimes

Every deadline below is reached inclusively: a game, invitation, result, throttle or stream whose
deadline equals the current instant has already expired. The same comparison applies to all of them.

- **R42** A game expires after the configured idle period measured from the latest of: its
  creation, the guest joining, an accepted action, or an accepted presence signal. Any accepted action
  counts, including one that changes nothing (R23); the absolute lifetime of R43 is what bounds a client
  that holds a game open that way. Reads, opening or reconnecting an event stream, replacing the
  invitation, keep-alive markers and refused actions never extend a game's life.
- **R43** A game in progress never plays on past the configured absolute lifetime measured from
  creation, whatever activity it sees. The ceiling governs play, not the result: a game that finishes
  just before it is reached still keeps its result for the full retention period of R44.
- **R44** A finished or abandoned game stays readable for the configured retention period, measured from
  the moment it ended, and is then removed.
- **R45** An invitation expires after the configured invitation lifetime or when its game
  expires, whichever comes first. While the game is waiting, the host may replace it; the previous link
  stops working the instant the new one is issued.
- **R46** Each player may extend a game's idle deadline by presence at most once per configured
  presence interval, and the two players are throttled independently, so one player's signal never
  absorbs the other's. Calls that come sooner return the current state and change nothing, including the
  version; an accepted signal moves the deadline, and because the deadline is not part of a view (R18)
  that too leaves the version alone — the caller still receives a fresh `expiresAt` in its own answer.
  Presence is an authenticated, forgery-protected request.
- **R47** Expiry is checked on every access to a game and also swept in the background at the
  configured interval, so a late request can never resurrect an expired game.
- **R63** An expired game is remembered for the same retention period as a finished one, measured from
  the expiry instant. While it is remembered, only its own two players are told that it expired; every
  other caller receives the same "no such game" answer as for a game that never existed, so an expiry
  never confirms to a stranger that the game was real. Once that period has passed, its own players
  receive that answer too.
- **R48** All state is in memory. A restart ends every game and every session; there is no
  persistence, no recovery and no claim of continuity.

### Failures

- **R49** Every failure is a problem document with a stable machine-readable code from the
  contract's list, mapped as follows — `malformed-request` 400; `session-required` 401;
  `request-security-rejected` 403; `game-unavailable` 404; `invitation-unavailable`,
  `action-not-allowed`, `placement-out-of-bounds`, `placement-overlap`, `placement-touching` and
  `target-already-fired` 409; `game-expired` 410; `payload-too-large` 413; `unsupported-media-type` 415;
  `validation-failed` 422; `rate-limit-exceeded` 429; `internal-error` 500; `service-unavailable` 503.
  Health is the only operation whose unavailable answer is not a problem document.
- **R50** A problem document carries a correlation identifier that ties it to the logs, and
  never carries a secret, a cookie, board data, the request body, an exception message or a stack trace.
  Its human-readable text is English developer text; a client localizes by the code.

### Statistics

- **R51** A finished game carries the statistics the contract defines, calculated by the server
  from the transitions it accepted and the injected time source of R60. The contract fixes every field,
  its unit, its clock boundaries and its rounding, and this spec does not restate them; it adds only
  that every identity the contract states must hold in the delivered values, that an abandoned game
  carries no statistics at all, and that a client formats these values and never recomputes them.

### Configuration

- **R52** Every numeric limit is external application configuration with a documented default —
  never a compile-time constant. This table is the single source of those defaults; `plan.md`
  § *Configuration* maps each row to its property key and states no defaults of its own.

  | Setting | Default |
  |---|---|
  | Idle lifetime | 900 s (15 min) |
  | Absolute game lifetime | 7200 s (2 h) |
  | Result retention | 300 s (5 min) |
  | Invitation lifetime | 900 s (15 min) |
  | Presence interval | 300 s (5 min) |
  | Event-stream heartbeat interval | 15 s |
  | Event-stream maximum lifetime | 1200 s (20 min) |
  | Rate limit — create game | 5 / min per caller address |
  | Rate limit — join | 20 / min per caller address |
  | Rate limit — actions | 60 / min per session |
  | Rate limit — read a game `[agent]` | 120 / min per session |
  | Rate limit — presence `[agent]` | 30 / min per session |
  | Rate limit — open an event stream `[agent]` | 30 / min per session |
  | Rate limit — replace invitation `[agent]` | 10 / min per session |
  | Rate limit — leave `[agent]` | 10 / min per session |
  | Maximum concurrent games | 100 |
  | Maximum concurrent event streams | 200 |
  | Maximum live games per browser `[agent]` | 1 |
  | Maximum request body size `[agent]` | 16 KiB |
  | Random-arrangement attempt limit `[agent]` | 1000 attempts |
  | Expiry sweep interval `[agent]` | 30 s |
  | Shutdown drain window `[agent]` | 5 s |
  | Public interface base URL | `http://localhost:5173` |

- **R53** Each setting in R52 is overridable both by environment variable and by the
  configuration file, with the environment variable taking precedence.
- **R54** Every setting is validated when the service starts. An absent, unparseable, negative,
  zero or otherwise nonsensical value stops the start-up and names the offending setting; it never
  surfaces later as a runtime failure.
- **R55** The limits the service publishes are read from the running configuration. There is no
  second copy of a limit anywhere, and changing a setting changes both the enforced behaviour and the
  published value.
- **R56** The public interface base URL is validated at start-up — absolute, a permitted scheme,
  no credentials, no query, no fragment — and is the sole source of the invitation link the host shares.
  The service never infers it from a request header.

### Operation

- **R57** The feature delivers one executable, backend-only artifact that starts locally with
  default configuration and reports itself ready. It bundles and serves no user-interface assets.
- **R58** Logs are structured, carry a correlation identifier and enough context to diagnose a
  failure, and never carry a session cookie, an invitation secret, a link fragment, a full secret-bearing
  URL, board content, a raw request body or an unnecessary player name. Diagnostic surfaces expose
  nothing sensitive and no game content.
- **R59** On shutdown the service stops admitting work, reports itself not ready, closes event
  streams without a reason message and answers their reconnection attempts as unavailable, allows
  in-flight actions the configured drain window to finish, and exits without claiming that any game
  survives.

### Testability of time

- **R60** All time-dependent behaviour is driven by an injected time source. Idle expiry, the
  absolute ceiling, result retention, invitation expiry, the presence throttle, the event-stream
  lifetime and every duration in the statistics of R51 are proven by advancing that source. No proof in
  this feature may depend on real elapsed time or on sleeping, and the whole suite runs in milliseconds.

## Key Entities

- **Game** — one match between two anonymous players: its ruleset, phase, both boards, whose turn it is,
  its version, its deadlines and, once over, its outcome and statistics. Games are independent of one
  another and exist only in memory.
- **Ruleset** — an immutable, published set of rules: board size, fleet composition, whether ships may
  touch, whether a hit grants another shot, and whether water is revealed around a sunk ship.
- **Board** — one player's complete grid of cell states plus the ships the viewer is allowed to know
  about. Two projections exist of every board: the owner's and the opponent's.
- **Ship** — one vessel of a fleet: its stable identifier, its type, its length, where it sits (if
  anywhere), and whether it is intact, damaged or sunk.
- **Player** — a participant in one game: a display name, whether the fleet is locked in, whether an
  event stream is open, and how many ships are still afloat.
- **Snapshot** — everything one player may see of one game at one moment, from that player's point of
  view. It is the only representation of a game that ever leaves the server.
- **Invitation** — a one-time, high-entropy secret that lets exactly one browser take the guest seat of
  one game, with its own expiry and the ability to be replaced by the host.
- **Session** — an anonymous browser identity that owns the seats that browser holds across games; the
  only thing that authorizes anything.
- **Action** — one attempt by one player to change one game, carrying a caller-generated identifier that
  makes re-sending it safe.
- **Statistics** — the server's measurement of a finished game: durations, shots, hits, accuracy, turn
  and shot-decision timings, and fleet condition.

## Success Criteria *(mandatory)*

- **S1** A complete game can be played from creation to a stated winner under each of the two rulesets,
  and the two rulesets demonstrably differ in adjacency, extra turn and revealed water.
- **S2** Across every phase, no player ever receives the opponent's placement outside the three allowed
  cases; an abandoned or expired game discloses nothing.
- **S3** After a reload at any point in a game, a single request returns everything needed to continue,
  including whose turn it is and what the player may do.
- **S4** An action that is sent twice is applied once; the second answer shows the game's current state,
  whatever was retried and however many actions later it arrives.
- **S5** A game left alone expires after the configured idle period, never plays on past the configured
  absolute lifetime, and its result stays readable for exactly the configured retention period measured
  from the moment it ended — all by advancing the injected time source, not by waiting.
- **S6** A browser that is not a player of a game cannot distinguish that game from one that does not
  exist, by status, body or disclosed detail; the same holds for every refused join.
- **S7** Two actions for one game arriving simultaneously produce one ordered outcome, with no lost
  update and no partially applied state observable by either player.
- **S8** A reconnecting player is fully current from the first delivered message alone, with no replay,
  no gap and no second request.
- **S9** No log record, problem document or diagnostic surface contains a cookie, an invitation secret,
  a link fragment, board content or a raw request body.
- **S10** The packaged artifact starts from default configuration on a developer machine and reports
  itself ready, serving no user-interface assets.
- **S11** At either configured ceiling, a new game or a new event stream is refused with a retry hint
  while every running game and open stream continues, and the service still reports itself ready. A
  browser already at its own game limit is refused too, and a game it has finished never counts.
- **S12** Every limit in R52 can be changed by configuration alone, with no code change, and the running
  service both enforces and publishes the changed value.

## Proof required (the ten areas)

Acceptance evidence for this feature is a focused proof in each of these ten areas. Each is named
because it is a way the game can actually break. `plan.md` § *Validation* names the owner of each.

1. **Ruleset correctness** — legal and illegal placements under both rulesets, the adjacency difference,
   the extra-turn difference, the revealed-water difference, and both ways a game ends.
2. **Action legality and state stability** — the offered-action table proven cell by cell, every action
   attempted in every phase it is not allowed in, and each refusal leaving the game and its version
   exactly as they were.
3. **Player privacy** — two games differing only in the opponent's undiscovered ships produce identical
   opponent-facing snapshots, until and unless the game finishes.
4. **Repeat safety** — a re-sent action identifier returns the current state and applies nothing however
   many actions later it arrives; a different identifier for the same intent applies again where the
   rules allow it and is refused where they do not.
5. **Concurrency** — simultaneous actions on one game linearize; actions on different games do not block
   one another; both players ready at once starts play once and settles the first turn once.
6. **Lifetimes** — idle expiry, the absolute ceiling, result retention, invitation expiry, the presence
   throttle and the stream lifetime, each proven at its inclusive boundary by advancing the injected
   time source (R60), together with the statistics durations of R51.
7. **Request security and authorization** — session issuance and reuse, the anti-forgery check, the
   cross-site refusal, a non-browser caller admitted without origin headers, non-players and strangers
   receiving the indistinguishable answer, and every refused join answering identically.
8. **Live updates** — first message is the current snapshot, a change reaches only the players whose own
   view changed, a replacement stream closes the older one with its reason, a vanished game closes
   the stream with its reason, heartbeats arrive, and the stream lifetime ends a stream.
9. **Packaging, configuration and redaction** — the packaged artifact starts and reports ready; an
   invalid setting stops start-up; a changed setting is both enforced and published; shutdown drains and
   reports not-ready; and log records contain none of the forbidden content.
10. **Wire conformance** — every operation's success and failure bodies asserted field by field against
    the contract's examples, so the published wire boundary is proven rather than assumed
    (Constitution IV).

Explicitly **not** completion criteria, consistent with Constitution IV: a coverage percentage, a test
count, property or generative testing, mutation-score evidence, a memory soak run, or a short-idle
timing profile. None of these are required by this feature, and none of them may be used in place of the
ten proofs above. `plan.md` and `quickstart.md` cite this paragraph rather than repeating it.

## Assumptions

1. `contracts/openapi.yaml` is complete and correct enough to implement against. If a gap is found
   while building, it is fixed in the contract directly, with every dependent updated in the same
   change (Constitution I); it is not worked around here.
2. The default public interface base URL `http://localhost:5173` reflects the development-server proxy
   arrangement the contract describes. `003-frontend` and `004-integration` may change the default; the
   value is configuration either way, so no code changes when they do.
3. The `[agent]` defaults in R52 — the body ceiling, the five unpinned rate limits, the capacity
   ceilings, the per-browser game cap, the arrangement attempt bound, the sweep interval and the drain
   window — are conservative starting configuration, not measured capacity claims. Each sits well above
   the client behaviour the contract prescribes, and each is changeable without a code change.
4. "Bounded caller-address key" means whatever stable, bounded key the deployment can derive for a
   caller; the plan decides how it is derived. The requirement is that the structure keyed by it cannot
   grow without limit.

## Out of scope

Everything the constitution's launch baseline excludes: a database, a durable session or result store, a
cache or broker as authority, accounts, chat, spectators, matchmaking, bots, rankings, durable history,
payments, analytics, advertisements, offline play, command queues, horizontal scaling, multiple
replicas, continuous-integration workflows, deployment manifests and hosting automation.

Also out of scope for this feature specifically: any user interface or user-interface asset
(`003-frontend`); cross-product orchestration, the repository-wide verification gate and end-to-end
browser journeys (`004-integration`); and any change to `contracts/` beyond the three amendments this
feature makes under the `001-api-contract` R24 additive rule — the `Phase.PLAYING` first-turn wording,
the `GameSnapshot.version` wording of R18, and normalizing five lines of OpenAPI-3.1-only syntax
(`const` → single-value `enum`, `examples: [x]` → `example: x`) so that the wire types can be generated.
All three are description- or constraint-level and leave the wire JSON byte-identical; the contract
product is otherwise owned by `001-api-contract`.

The capabilities the contract itself defers are not built: claiming a win against a vanished opponent,
rematch, recovering a join whose answer was lost before the session arrived, conditional reads and salvo
rules. Where the seed documents under `docs/rewrite_context_doc/Planning/` describe a different API,
`contracts/README.md` § *Supersedes planning inputs* records the delta.
