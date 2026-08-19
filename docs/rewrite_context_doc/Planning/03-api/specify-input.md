# Copy-paste input: API contract

Create feature `001-api-contract` for the production-ready anonymous Battleship rewrite. This feature publishes a language-neutral API contract. It does not implement a backend, controllers, browser screens, persistence, or an OpenAPI-serving application.

## Purpose and users

Two anonymous players need a precise, safe wire contract that lets an independently built web client and an independently built single-process server create, join, play, recover, and finish a private game. The present v2 API treats public session and player identifiers as authority and exposes transport-specific DTOs; this feature replaces that behavioral boundary rather than preserving its paths or shapes.

The contract is the compatibility boundary for later backend and web features. It must make correct client recovery possible after retries, stale snapshots, SSE reconnects, expiry, and a process restart without leaking hidden ships or anonymous credentials.

## Scope and non-goals

Publish contract artifacts only under `contracts/`: a canonical OpenAPI 3.1-compatible description, JSON Schemas, validated examples, an API guide, a compatibility policy and changelog, an SSE protocol guide, and RFC 9457 Problem Details definitions. Describe HTTP resources under `/api/v1` and the public health representation.

Do not create application code, controllers, screens, generated clients, server configuration, a database, frontend assets, or a second game engine. Do not retain compatibility with `/api/v2/game`. Do not specify accounts, spectators, chat, matchmaking, durable history, offline command queues, multiple backend replicas, or recovery of a game after process loss.

## Product policy

- A public `gameId` locates a game but never authenticates, authorizes, or reveals a seat.
- Creating a game selects one of two immutable rulesets and creates the owner seat. The response establishes the owner's anonymous browser session and returns a player-safe initial snapshot plus a canonical invitation URL. The owner capability is never included in that URL or any shareable representation.
- Creation is one deliberate request, not an exactly-once promise. If its response is lost, the client reconciles known state and asks before creating a new game; it does not blindly replay creation. An unreachable orphan expires under the normal bounds.
- The invitation URL contains the public game locator before `#` and a one-time invitation secret only in the fragment. Landing on it has no side effect. The client removes the fragment from visible/history URL before the player explicitly chooses to join, then redeems the secret with a POST body. The successful redemption atomically consumes the invitation, creates the guest browser session, and returns the guest's player-safe snapshot.
- The canonical invitation URL uses the deployment's validated public frontend base URL, including any application subpath. The backend creates it and the frontend shares the exact returned value; the frontend never rebuilds it from `gameId` or other metadata.
- The owner may rotate an unused invitation only while the guest seat is vacant. Rotation immediately revokes the prior invitation and returns one new fragment URL. A consumed, revoked, expired, or competing invitation redemption is a conflict and never allocates a second guest seat.
- The server stores only the invitation digest. An invitation URL is returned only at creation or rotation; if the owner loses that in-memory URL after reload, the waiting room requires an explicit rotation before it can share again. Rotation with an unknown network outcome is reconciled and then repeated only as an explicit replacement, never as a blind retry.
- Each player may place, move, rotate, or remove only their own ships until that player sends `READY` with a complete legal fleet. `READY` is irreversible in this release. When both seats are ready, play begins and the already selected random starting seat is revealed; the random choice is made at game creation, so completing readiness cannot bias it.
- `LEAVE` is available before play and abandons the whole game: both seats become read-only former members, the invitation and every gameplay action become unusable, and each former member can read a safe terminal summary during the short retention window. Pre-play abandonment does not disclose either placement. `RESIGN` is available only during play, is irreversible, and awards the game to the other seat. A normally finished or resigned game reveals both completed boards only to its two former seats. After either terminal result, `LEAVE` revokes only the caller's remaining read membership; it does not change the result or the other former member. No public or invite-based final disclosure exists.
- A repeat target is a conflict, makes no transition, does not consume a turn, and does not increment `gameVersion`.
- A game expires after 15 minutes with neither an accepted command nor accepted explicit visible-tab presence. Reads, polling, SSE establishment/reconnects, heartbeats, failed commands, and rejected presence do not extend that deadline. A visible authenticated player may submit presence at most once per five minutes; it changes no game state or `gameVersion`. Every game also expires two hours after creation. Finished or abandoned games remain readable to their members for five minutes. An invitation expires after 15 minutes or when its game expires, whichever happens first.
- The service is intentionally ephemeral. A known in-process expiry is reported distinctly; after cleanup or a restart a missing game is generic unavailable. Clients compare the current `serverEpoch` and their cached expiry to explain restart versus expiry without guessing.

## Rulesets

The system shall publish exactly these initial stable ruleset identifiers and their complete machine-readable ruleset summaries:

- `sea-battle-10-ship.v1`: a 10 by 10 board; fleet lengths 4 once, 3 twice, 2 three times, and 1 four times; horizontal or vertical ships; no overlapping or orthogonally or diagonally touching ships; exactly one shot; a hit or sink keeps the turn; after a sink, automatically revealed surrounding water is represented distinctly from a player-fired shot result.
- `hasbro-classic-2002.v1`: a 10 by 10 board; Carrier 5, Battleship 4, Destroyer 3, Submarine 3, and Patrol Boat 2; horizontal or vertical non-overlapping ships; contact between ships is allowed; exactly one shot; the turn changes after either a hit or a miss.

The system shall not use broad labels such as `UKRAINIAN` or `MILTON_BRADLEY` as ruleset identifiers. A ruleset correction shall receive a new immutable identifier rather than changing an in-progress game's rules.

## EARS requirements

### Published contract and compatibility

- **Canonical contract:** The system shall publish one canonical OpenAPI 3.1-compatible contract and companion schemas/examples that describe the same resources, headers, cookies, event envelopes, and problems.
- **Version layers:** The system shall document a semantic contract version separately from the `/api/v1` major and from per-game `gameVersion`.
- **Additive compatibility:** When a published contract changes additively, the system shall preserve existing fields and event meanings, tolerate unknown additive response fields, and record the change in the changelog.
- **Breaking compatibility:** When a wire change is breaking, the system shall introduce a new API major and define a migration window rather than silently changing v1.
- **Safe problems:** The system shall define RFC 9457 Problem Details with stable application codes, correlation identifiers, retry guidance where relevant, and no secret, stack trace, board, or internal state disclosure.

### Resources, snapshots, and commands

- **Fixed route surface:** The system shall define exactly this initial method/path surface: `GET /api/v1/meta`, `GET /api/v1/rulesets`, `POST /api/v1/games`, `POST /api/v1/games/{gameId}/join`, `GET /api/v1/games/{gameId}/snapshot`, `POST /api/v1/games/{gameId}/commands`, `GET /api/v1/games/{gameId}/events`, `POST /api/v1/games/{gameId}/invite/rotate`, `POST /api/v1/games/{gameId}/presence`, `POST /api/v1/games/{gameId}/leave`, and public `GET /actuator/health`. A metadata read shall establish or refresh the separate browser-readable CSRF cookie needed before the first unsafe request, shall use `Cache-Control: no-store`, and shall not create a game membership. No alternate v1 aliases shall be published.
- **Snapshot authority:** The system shall define a full player-safe snapshot as the authoritative representation returned by successful creation, join, snapshot reads, accepted commands, terminal transitions, and SSE snapshot events.
- **Snapshot content:** Every snapshot shall include `contractMajor`, `serverEpoch`, `gameId`, `gameVersion`, selected ruleset summary, phase, caller-derived seat, allowed commands, turn/outcome where applicable, expiry, the caller's complete board, and only opponent information legally revealed by the selected ruleset.
- **Result statistics:** A normal-finish or resignation snapshot shall include server-calculated match, placement, per-seat placement, and gameplay durations; per-seat turn intervals, shots, hits, accuracy, accepted shot-decision total/average/fastest/slowest time, and untouched/damaged/destroyed fleet counts. Match timing starts when the guest joins and placement begins; each accepted fire ends one shot-decision sample and a retained turn starts the next sample. An abandoned pre-play summary shall omit fleet-derived statistics and hidden placement.
- **Projection privacy:** The system shall prohibit capability values, invitation digests, raw opponent placement before final disclosure, internal command history, subscriber state, lock state, and abuse-control state from snapshots, command responses, events, problems, and shareable representations.
- **Command vocabulary:** The system shall define command payloads for `PLACE_SHIP`, `MOVE_SHIP`, `ROTATE_SHIP`, `REMOVE_SHIP`, `READY`, `FIRE`, and `RESIGN`, including phase, actor, ruleset, coordinate, orientation, and fleet validation behavior.
- **Visible version:** A newly created game shall use `gameVersion: 0`. When guest join, invitation-status rotation, pre-play abandonment, or a game command changes the shared player-safe game view, the system shall increment `gameVersion` exactly once and make newer projections available to both remaining members. Presence, reads, failed operations, and revoking one member after any terminal result shall not increment it.
- **Command precondition:** When a client sends a mutating command, it shall send a unique `commandId`, `expectedVersion`, and `If-Match` entity tag that names the same version. A missing precondition shall produce 428 and disagreeing body/header versions shall produce a validation problem.
- **Command retry:** When the same `commandId` is retried with the same normalized command content, the system shall return its recorded authoritative result before evaluating version freshness. When that identifier is reused with different normalized content, the system shall return a conflict. When a new command has a stale version, the system shall return 412 and direct the client to reconcile the snapshot.
- **Secret outcome recovery:** If creation or invitation rotation has an unknown transport outcome, the contract shall direct the client to reconcile and require an explicit user replacement action rather than claiming an idempotent replay of a secret-bearing result.
- **Conditional snapshot:** The system shall define ETags for snapshots and conditional snapshot reads with `If-None-Match`, including a no-change response. Authenticated snapshots and command/invitation responses shall be marked non-cacheable.
- **Leave phases:** The `POST /api/v1/games/{gameId}/leave` request shall carry `commandId` and `expectedVersion` with a matching `If-Match`. Before play it shall return the newly versioned abandoned snapshot to the caller and publish it to the other former member. During play it shall return a conflict directing the player to the `RESIGN` game command. After abandonment, normal finish, or resignation it shall revoke only the caller's retained read membership, close that caller's stream, return 204, and leave `gameVersion` unchanged.
- **Leave retry:** An identical leave retry shall return the recorded snapshot or 204 result before ordinary membership and version checks, including after terminal revocation. Reusing its `commandId` with different content shall be a conflict; a new missing/stale precondition shall use the same 428/412 rules as a versioned game command. The server may retain only the two bounded per-seat leave receipts until game removal. A client with an unknown leave outcome shall retry the same identity or reconcile; it shall not invent success.

### Anonymous security and invitations

- **Capability authentication:** The system shall authenticate a seat using a high-entropy anonymous capability whose server representation is a digest. It shall derive seat membership server-side and reject client-supplied player, role, turn, winner, or ownership authority.
- **Cookie transport:** The system shall require the host-only, Secure, HttpOnly, SameSite=Strict `__Host-battleship_session` cookie at `Path=/` for the anonymous capability. It shall use a separate host-only, Secure, SameSite=Strict `BATTLESHIP-XSRF-TOKEN` cookie at `Path=/` without HttpOnly solely to echo in `X-Battleship-CSRF`. The configured frontend and API origins shall use the same hostname; local ports may differ.
- **Unsafe request defense:** When a request changes state, redeems an invitation, rotates an invitation, records presence, or leaves, the system shall require the CSRF header and an exact configured frontend Origin. It shall also reject browser requests whose Fetch Metadata identifies a cross-site or incompatible unsafe request; same-origin/same-site browser requests and non-browser requests that still pass CSRF and exact-Origin checks follow the documented policy. Safe reads and SSE shall not require the CSRF header.
- **Secret locations:** The system shall never place a seat capability in a URL, query string, fragment, browser storage, log, metric, shareable response body, or SSE URL. The narrow exception is the one-time invitation secret in the canonical invitation URL fragment; it is neither a seat capability nor valid after redemption, rotation, revocation, or expiry.
- **Authorization errors:** If a caller lacks a valid anonymous session, the system shall return 401. If an authenticated caller lacks membership or action authority, it shall return 403. If a caller cannot prove membership for a missing game, it shall receive a generic 404.

### Events, recovery, lifecycle, and health

- **One stream per seat:** The system shall define one authenticated SSE stream per seat at the game events resource. A newer stream shall replace the older stream for that seat.
- **Initial stream snapshot:** When an SSE stream opens, the system shall deliver an immediate full player-safe snapshot. Each snapshot event shall use `gameVersion` as its event identifier and carry `serverEpoch`.
- **Stream bounds:** While a stream is healthy, the system shall emit heartbeat comments every 15 seconds, cap a stream at 20 minutes, bound replay and outbound delivery, and disconnect slow clients instead of accumulating an unbounded queue.
- **Replay recovery:** When `Last-Event-ID` is available, the system shall replay retained contiguous authorized snapshots when possible; otherwise it shall send the current full snapshot. Clients shall discard duplicate or stale versions, accept a higher full snapshot as recovery from a gap, and treat an epoch change as restart evidence.
- **Degraded recovery:** When SSE is unavailable or the tab becomes visible, the system shall support conditional snapshot recovery. The contract shall state that a client may use adaptive polling only as degraded recovery and must not issue a new command while the outcome of the preceding command is unknown.
- **Overload errors:** When capacity is exhausted or shutdown is draining, the system shall reject new work with 503 and bounded retry guidance without evicting a live game. When a rate limit is reached, it shall return 429 and bounded retry guidance.
- **Initial rate limits:** The initial documented rate policy shall allow at most five game creations per minute per bounded source key, twenty invitation attempts per minute per bounded source key, and sixty game commands per minute per capability. Limits shall not reveal whether a game, invitation, or membership exists.
- **Visible presence:** When visible-tab presence is submitted, the system shall return the current idle expiry and whether this call extended it. At most one accepted extension per seat in five minutes is allowed; an earlier well-formed call returns `extended: false` without changing `gameVersion`, while abusive traffic is rate-limited with 429.
- **Expiry and restart:** When an in-process game has expired, the system shall return 410 with `serverEpoch`. When a game has disappeared because of cleanup or restart, it shall return generic 404. The public metadata resource shall expose the current `serverEpoch` and only non-sensitive deployment/contract metadata.
- **Public health:** The system shall define a public health response that reports liveness/readiness without exposing configuration, active-game data, metrics, secrets, or implementation internals.

## Acceptance scenarios and proving tests

| Scenario | Proving test |
| --- | --- |
| A client creates each published ruleset and receives a player-safe owner snapshot, a cookie security contract, and a fragment-only invitation URL. | OpenAPI/schema example validation and contract assertions for create responses/cookies. |
| Two concurrent invitation redemptions use the same secret; exactly one obtains the guest snapshot and the other receives the documented conflict. | Conformance fixture for atomic one-time invitation outcomes. |
| A command response is lost, then its identical `commandId`/payload is retried with the old version. | Contract fixture proves the recorded result is returned; a changed payload with the same ID is a conflict. |
| A player requests a snapshot with its ETag, receives no-change semantics, then processes an SSE gap or duplicate. | HTTP and SSE protocol examples validate ETag, `Last-Event-ID`, event-id/version, full-snapshot recovery, and epoch behavior. |
| A player-safe view is compared for two games differing only in hidden opponent placement. | Privacy fixture proves the caller-facing JSON is identical before final disclosure. |
| A sea-battle sink produces distinct fired-shot and auto-revealed-moat values, while a classic hit ends the turn. | Ruleset examples and schema fixtures assert the distinct public outcomes. |
| A stale command, missing precondition, repeat shot, expired game, restarted missing game, rate limit, and draining service occur. | Problem Details examples prove statuses 412, 428, 409, 410, 404, 429, and 503 with stable codes and no secrets. |

## Completion evidence

The resulting feature must be independently consumable by a backend and a web client, lintable/validatable without either implementation, and explicit enough that the backend feature can conform without choosing new user-visible policy. Keep contract ownership exclusive: this feature owns the published wire boundary; the later backend feature owns its internal implementation and conformance, not a divergent contract.
