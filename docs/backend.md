# Backend guide

How the backend is built, how it works inside, and how to configure, build and test it. For the big picture, the flows and the
security model read [architecture](architecture.md) first. For the endpoints and wire fields use the
[contract](../contracts/openapi.yaml) and its [guide](../contracts/README.md); they are not repeated here.

Stack: Java 25, Spring Boot 4.1.1 (`web`, `validation`, `security`, `actuator` starters), Maven through the wrapper
[`backend/mvnw`](../backend/mvnw), virtual threads enabled (`spring.threads.virtual.enabled`). The authoritative design is in
archived backend change design (`openspec/changes/archive/*-backend/design.md`) and [`openspec/specs/`](../openspec/specs/).

## Contents

- [Modules](#modules)
- [Rules engine](#rules-engine)
- [Use cases](#use-cases)
- [Registry and locking](#registry-and-locking)
- [Request pipeline](#request-pipeline)
- [Live updates (SSE hub)](#live-updates-sse-hub)
- [Rate limiting](#rate-limiting)
- [Lifetimes and expiry](#lifetimes-and-expiry)
- [Errors](#errors)
- [DTO generation](#dto-generation)
- [Configuration reference](#configuration-reference)
- [Logging](#logging)
- [Build, run, test](#build-run-test)
- [Testing approach](#testing-approach)

## Modules

Three Maven modules under [`backend/`](../backend/pom.xml). Each may use only the one(s) to its left in the table; the rule is enforced
(see [architecture](architecture.md#modules-and-dependency-rule)).

| Module | Package | Contains | Knows nothing about |
|---|---|---|---|
| [`domain`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/) | `...domain` | Game model and rules | Spring, JSON, clocks, randomness sources, I/O |
| [`application`](../backend/application/src/main/java/ua/kostenko/battleship/application/) | `...application` | Use cases, in-memory registries, caller-relative projection, ports | HTTP, cookies, SSE, JSON |
| [`app`](../backend/app/src/main/java/ua/kostenko/battleship/app/) | `...app` | Spring Boot adapters and wiring | Game rules |

Key classes:

| Where | Class | Role |
|---|---|---|
| domain/model | [`GameState`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/model/GameState.java) | Immutable record: ruleset id, phase, `version`, host and guest [`PlayerState`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/model/PlayerState.java), turn, last shot, outcome, timeline |
| domain/model | `Phase`, `Seat`, `Board`, `Ship`, `Coordinate`, `Shot`, `Outcome`, `Timeline` | Value types; `Timeline` records the timestamps used for statistics |
| domain/rules | [`GameRules`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/GameRules.java) | `apply(state, seat, command, now, random)` returns a `Transition` |
| domain/rules | [`Rulesets`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/Rulesets.java), `Ruleset`, `FleetEntry` | The two built-in rulesets |
| domain/rules | [`AllowedActions`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/AllowedActions.java) | Which actions a seat may take in the current state |
| domain/command, transition | `GameCommand` (sealed), [`Transition`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/transition/Transition.java), `Rejection` | Input and output of the rules |
| domain | [`RandomSource`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/RandomSource.java) | One-method port for randomness (injected, so tests can seed it) |
| application/usecase | `*UseCase` classes | One class per operation (below) |
| application/registry | [`GameRegistry`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/GameRegistry.java), [`GameSlot`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/GameSlot.java), [`SessionRegistry`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/SessionRegistry.java), `SessionRecord` | All mutable state |
| application/projection | [`SnapshotProjector`](../backend/application/src/main/java/ua/kostenko/battleship/application/projection/SnapshotProjector.java), `SnapshotView` and friends | Builds the player-safe view for one seat |
| application/port | `TimeSource`, `SecretGenerator`, `SnapshotPublisher` | Interfaces the use cases need from outside |
| application/result | [`ApplicationFailure`](../backend/application/src/main/java/ua/kostenko/battleship/application/result/ApplicationFailure.java) | Framework-free refusal carrying a problem code |
| app (root) | [`BattleshipApplication`](../backend/app/src/main/java/ua/kostenko/battleship/app/BattleshipApplication.java) | Entry point; creates the registries, projector and one bean per use case |
| app/web | Controllers, [`ProblemAdvice`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/ProblemAdvice.java), [`SnapshotDtoAssembler`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/SnapshotDtoAssembler.java), [`JacksonConfig`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/JacksonConfig.java) | HTTP in and out |
| app/security | Filters, [`SecurityConfig`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/SecurityConfig.java), [`FixedWindowBuckets`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/FixedWindowBuckets.java) | Request protection |
| app/realtime | [`SseHub`](../backend/app/src/main/java/ua/kostenko/battleship/app/realtime/SseHub.java), `Subscriber`, `HeartbeatScheduler` | Event streams |
| app/config | [`BattleshipProperties`](../backend/app/src/main/java/ua/kostenko/battleship/app/config/BattleshipProperties.java), `ExpirySweeper`, `GracefulShutdownConfig`, `SystemTimeSource`, `SecureRandomSecretGenerator`, `SecureRandomSource` | Settings, schedulers, real clock and randomness |
| app/observability | `CorrelationIdFilter`, `OperationalEvents`, `LoggingConfig` | Logging support |

The real implementations of the ports live in `app/config` (`SystemTimeSource`, `SecureRandomSecretGenerator`, `SecureRandomSource`) and `SseHub` implements `SnapshotPublisher`.
The two small interfaces `TimeSource` and `RandomSource` are what make the time-dependent and random-dependent behavior testable.

## Rules engine

Everything is a pure function of immutable values: `GameRules.apply(state, actor, command, now, random)` returns a
[`Transition`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/transition/Transition.java) holding the next `GameState`, whether the version was bumped, which seats' views changed, and an optional `Rejection`.

Steps inside `apply`:

1. Map the command (`PlaceShip`, `RemoveShip`, `PlaceFleetRandomly`, `ClearFleet`, `Ready`, `Fire`, `Resign`) to its `Action`.
2. If the action is not in `AllowedActions.of(state, actor)`, return a refusal `action-not-allowed`.
3. Run the command's rule. A refusal returns the **unchanged** state with no version bump and no changed seats; an accepted change bumps `version` by one and lists the seats whose view changed. (`Transition` rejects any combination where those two disagree.)

What the rules do:

- **Placement**: a ship must fit inside the board (`placement-out-of-bounds`), not overlap another (`placement-overlap`), and, when the ruleset forbids touching, not touch another (`placement-touching`). Coordinates outside the board answer `validation-failed` with rule `OUT_OF_RANGE`. Placing an already placed ship moves it; an unknown `shipId` answers `validation-failed` (`UNKNOWN_VALUE`). A command that changes nothing (same position, removing an unplaced ship, an identical random result) is accepted as a no-op: no version bump and no changed views.
- **Random placement**: ships are placed longest first at random anchor and orientation. Every rejected candidate counts; at `battleship.random-arrangement-attempts` the command fails with `random-arrangement-failed` and changes nothing.
- **Ready**: needs a complete fleet. When both seats are ready the phase becomes `PLAYING`; the seat that became ready first moves first, and a tie is decided by `RandomSource`.
- **Fire**: refuses a target that was already fired at or revealed (`target-already-fired`). A hit marks the cell; a ship with all cells hit is `SUNK`. If every ship is sunk the phase becomes `FINISHED` with outcome `FLEET_DESTROYED`. Otherwise the turn passes to the opponent, unless the ruleset grants another turn on a hit. Rulesets with `revealWaterAroundSunk` mark the free cells around a sunk ship as revealed water.
- **Resign**: phase `FINISHED`, outcome `RESIGNATION`, the opponent wins.

The two rulesets are plain data in [`Rulesets`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/Rulesets.java); adding a variant means adding a new immutable entry with a new id.

| Id | Board | Fleet | Ships may touch | Extra turn on hit | Water around sunk ship revealed |
|---|---|---|---|---|---|
| `sea-battle-10-ship.v1` | 10x10 | 1x4, 2x3, 3x2, 4x1 cells (10 ships) | no | yes | yes |
| `hasbro-classic-2002.v1` | 10x10 | carrier 5, battleship 4, destroyer 3, submarine 3, patrol boat 2 | yes | no | no |

`DisplayNameNormalizer` validates names (1 to 32 code points, invisible characters rejected except the emoji zero-width joiner) and is shared by create and join.

## Use cases

One class per operation in [`application/usecase`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/). Each is created once in `BattleshipApplication` with the durations it needs from `BattleshipProperties`.

| Use case | Endpoint | What it does |
|---|---|---|
| `CreateGameUseCase` | `POST /games` | Validates ruleset and name, reuses the presented session if known or issues a new one, settles the caller's expired games, then (under the session registry lock) enforces the per-browser limit and inserts the game. Returns the host's snapshot with the invitation URL. |
| `JoinGameUseCase` | `POST /games/{id}/join` | Checks the invitation (game live and `WAITING`, no guest, not expired, secret digest equal), admits the guest, clears the invitation, publishes the host's new view. A guest who repeats the call gets their own snapshot back. |
| `GetGameUseCase` | `GET /games/{id}` | Authorizes and projects; changes no deadline and no version. |
| `CommandUseCase` | `POST /games/{id}/commands` | Authorizes, ignores a repeated `commandId`, calls `GameRules.apply`, stores the new state, moves the idle deadline, starts result retention on finish, then projects and publishes. |
| `PresenceUseCase` | `POST /games/{id}/presence` | Moves the idle deadline, at most once per `presence-interval-seconds` per seat. Needs `SEND_PRESENCE` in `allowedActions`. |
| `ReplaceInvitationUseCase` | `POST /games/{id}/invitation` | Host only, while `WAITING`: new secret, new deadline, version + 1, publishes. |
| `LeaveGameUseCase` | `POST /games/{id}/leave` | `WAITING`: removes the game. `PLACEMENT`: phase `ABANDONED`, retention starts, the stayer is notified. `FINISHED` or `ABANDONED`: the leaving seat is forgotten. |
| `SubscribeUseCase` | `GET /games/{id}/events` | Registers a stream for a seat and maintains the `connected` flag shown to the opponent (see [SSE hub](#live-updates-sse-hub)). |
| `ExpireGamesUseCase` | (scheduler) | Sweeps expired and forgotten games out of memory. |

Common shape of every use case that changes a game (look at `CommandUseCase` for the clearest example):

1. Compute the caller's session digest.
2. `games.withSlot(gameId, ...)`: take the slot lock, authorize via `ExpiryPolicy.authorize`, change state, and **capture** the immutable state plus a `SnapshotContext` (time, expiry, invitation URL, connected flags).
3. Release the lock.
4. Project the captured state for each seat that needs a view.
5. Call `SnapshotPublisher.publish`.

Projection and publishing never run under a game lock, so a slow SSE client cannot stall a game.

## Registry and locking

All mutable state is in memory and bounded.

- [`GameRegistry`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/GameRegistry.java): a `ConcurrentHashMap<gameId, GameSlot>` plus a `Semaphore` with `max-concurrent-games` permits. Inserting without a permit throws `CapacityExceededException` (answered as `503 service-unavailable`, `Retry-After: 1`). The permit is returned when the slot is removed.
- [`GameSlot`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/GameSlot.java): one game. It holds the current `GameState`, the host and guest **session digests**, the invitation digest and (until used) the invitation secret, the set of accepted `commandId`s, the deadlines, per-seat presence throttle, and the connected stream per seat. It owns a `ReentrantLock`.
- `withSlot(gameId, action)` is the only way to touch a slot: it finds it, locks it, re-checks that it was not removed meanwhile, runs the action, and unlocks. One lock per game means two games never block each other and two commands for one game run one after the other.
- [`SessionRegistry`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/SessionRegistry.java): maps the SHA-256 digest of a session value to a `SessionRecord` (last seen time and the ids of its live games). It enforces `max-live-games-per-browser` and `max-concurrent-sessions`. When the table is full it evicts the longest-idle session that has no live game; if none can be evicted the request fails with `503`.
- Lock order, to avoid deadlock: expired games of the caller are settled **before** the session registry lock is taken (`ExpiryPolicy.settleExpiredGamesOf`), and releasing a session's game while holding a slot lock deliberately avoids the registry lock.
- Secrets: [`SecureRandomSecretGenerator`](../backend/app/src/main/java/ua/kostenko/battleship/app/config/SecureRandomSecretGenerator.java) makes game ids from 16 random bytes and session values and invitation secrets from 32 random bytes, all URL-safe Base64.

## Request pipeline

Every request passes the same filters in this order (servlet filters first, then the Spring Security chain in [`SecurityConfig`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/SecurityConfig.java)). A refusal is written by the filter as a [problem document](#errors) before any controller runs, so it changes no game state.

| # | Filter | Does |
|---|---|---|
| 1 | [`CorrelationIdFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/observability/CorrelationIdFilter.java) | Gives the request a random id and puts the `gameId` from the path in the log context |
| 2 | [`CacheControlFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/CacheControlFilter.java) | `Cache-Control: no-store` on every response |
| 3 | [`DrainingFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/DrainingFilter.java) | While shutting down: `503 service-unavailable` for opening a stream and for every non-GET under `/api/v1/games` |
| 4 | [`RequestSizeFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/RequestSizeFilter.java) | `413 payload-too-large` above `max-request-body-bytes` (also for chunked bodies) |
| 5 | [`SiteIsolationFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/SiteIsolationFilter.java) | Unsafe methods only: `403 request-security-rejected` for cross-site or foreign-`Origin` requests |
| 6 | [`SessionCookieFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/SessionCookieFilter.java) | For game routes except create and join: no known session means `401 session-required` and the cookie is cleared |
| 7 | Spring `CsrfFilter` | Unsafe methods must carry `X-XSRF-TOKEN` equal to the `XSRF-TOKEN` cookie (plain, unmasked token) |
| 8 | [`RateLimitFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/RateLimitFilter.java) | `429 rate-limit-exceeded` with `Retry-After` |

Because the rate limit comes last, a request refused earlier does not spend quota. The security chain is stateless (no HTTP session), has no URL authorization (the session filter plus the use cases decide who may do what) and no CORS.

Controllers are thin: they map the generated DTO to a domain command or use-case argument, call one use case, and map the `SnapshotView` back with `SnapshotDtoAssembler`. Create and join set the session cookie only when the session value differs from the one presented. `GET /meta` resolves the CSRF token, which makes Spring issue the `XSRF-TOKEN` cookie. `GET /health` answers `ready: true`, or 503 with reason `STARTING` or `DRAINING`.

## Live updates (SSE hub)

[`SseHub`](../backend/app/src/main/java/ua/kostenko/battleship/app/realtime/SseHub.java) implements `SnapshotPublisher` and owns every open stream.

Opening a stream (`open`):

1. Create the emitter and register a pending `Subscriber` **before** locking the game. A change that races with registration is either in the first snapshot or buffered and sent after it.
2. `SubscribeUseCase.open` runs under the game lock: authorizes (a stranger gets 404, an expired game 410), asks the hub to **admit** the stream, records the stream number for the seat and captures the state. If this is the seat's first stream, `connected` becomes true and the version is bumped once.
3. Admission takes a **permit** from a semaphore of `max-concurrent-streams`. If none is left the answer is `503 service-unavailable` with `Retry-After` of `stream-retry-after-seconds`. A stream that replaces the seat's older one takes over that stream's permit.
4. Activate the subscriber with the captured snapshot as its first event, tell the opponent (if the seat just connected), and close the older stream of the same seat with `event: closed`, reason `REPLACED`. At most one stream exists per game and seat.

Delivery rules:

- **Framing**: `event: snapshot`, `id: <version>`, `data: <GameSnapshot JSON>`, built with the same assembler and mapper as HTTP answers. A deliberate close is `event: closed` with a `StreamClosed` document and no `id`.
- **Bounded buffer**: a [`Subscriber`](../backend/app/src/main/java/ua/kostenko/battleship/app/realtime/Subscriber.java) keeps only its latest unsent snapshot, and accepts only strictly newer versions, so event ids never go backwards and a slow client cannot make memory grow.
- **Writers**: each draining stream writes on its own virtual thread, never on the thread that published. A client that stops reading stalls only itself. A failed write ends that stream (and logs `realtime-delivery-failed`).
- **Heartbeat**: [`HeartbeatScheduler`](../backend/app/src/main/java/ua/kostenko/battleship/app/realtime/HeartbeatScheduler.java) ticks every second; the hub sends a `: keep-alive` comment to each stream every `heartbeat-seconds`, which is also how a silently vanished client is detected.
- **Maximum lifetime**: a stream older than `stream-max-lifetime-seconds` is ended without a `closed` event, so the browser reconnects by itself.
- **Game gone**: when a game stops existing for a seat (it left, expired, or was removed) the hub closes that seat's stream with `closed`, reason `GAME_UNAVAILABLE`.
- **Connected flag**: when a seat's last stream ends, `SubscribeUseCase.close` bumps the version once and the opponent receives a snapshot with the seat shown as disconnected. Closing a replaced stream changes nothing, because only the newest stream number marks the seat connected.
- **No replay**: `Last-Event-ID` is ignored; the first event is always the current snapshot.
- **Shutdown**: `closeAll()` ends every stream without a `closed` event (see [`GracefulShutdownConfig`](../backend/app/src/main/java/ua/kostenko/battleship/app/config/GracefulShutdownConfig.java)): readiness turns to draining, new work is refused, streams end, then the server waits up to `shutdown-drain-seconds` for requests already running.

## Rate limiting

[`RateLimitFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/RateLimitFilter.java) limits eight operations. Metadata, rulesets and health are not limited.

| Operation | Key | Property (per window) |
|---|---|---|
| `POST /games` | caller address | `create-game-per-minute` |
| `POST /games/{id}/join` | caller address | `join-per-minute` |
| `POST /games/{id}/commands` | session | `commands-per-minute` |
| `GET /games/{id}` (and `HEAD`) | session | `read-game-per-minute` |
| `POST /games/{id}/presence` | session | `presence-per-minute` |
| `GET /games/{id}/events` | session | `stream-open-per-minute` |
| `POST /games/{id}/invitation` | session | `replace-invitation-per-minute` |
| `POST /games/{id}/leave` | session | `leave-per-minute` |

How a check works ([`FixedWindowBuckets`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/FixedWindowBuckets.java)):

1. The bucket id is the operation name plus the key. For session keys the key is the session's SHA-256 digest, never the cookie value.
2. A bucket's window starts at its first call and lasts 60 seconds (fixed, not configurable). At the window end the counter has already reset.
3. Within the limit the call passes. Over it, the response is `429` with `Retry-After` set to the whole seconds left (at least 1), repeated in the body.
4. The table holds at most `rate-limit.table-capacity` buckets. When full, the bucket unused for longest is dropped instead of refusing a new key, so a flood of invented keys cannot lock real players out. The sweeper also prunes ended windows.

**Which address is used.** By default the TCP peer address. With `battleship.trust-forwarded-for=true` it is the **last** entry of the last `X-Forwarded-For` header: the entry the trusted proxy appended itself. Earlier entries are client-supplied and ignored. Turn this on only behind exactly one proxy that overwrites the header (see the [trust model](configuration.md#x-forwarded-for-from-browser-to-rate-limit)). Without it, all players behind one proxy share one create and join bucket.

## Lifetimes and expiry

Deadlines live in the `GameSlot` and are compared in one place, [`ExpiryPolicy`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/ExpiryPolicy.java) (a deadline is reached when `now >= deadline`).

| Deadline | Set when | Moved by | On reaching it |
|---|---|---|---|
| Idle deadline (`idle-timeout-seconds`) | create | join, each accepted new command, presence (throttled to once per `presence-interval-seconds` per seat) | game expires |
| Absolute deadline (`max-game-duration-seconds`) | create | never | game expires |
| Invitation deadline (`invitation-lifetime-seconds`) | create, new invitation | new invitation | join answers `409 invitation-unavailable`; the snapshot stops showing the URL |
| Retention deadline (`result-retention-seconds`) | game finishes or is abandoned; or, for an expired game, counted from its expiry | never | game is forgotten |

The play deadline is the earlier of the idle and absolute deadlines. A request sees one of three states:

- **Live**: normal.
- **Expired, retained**: its two players get `410 game-expired`; everyone else gets `404 game-unavailable`. Expiry also releases both players' live-game allocation, so they can start another game.
- **Forgotten**: `404 game-unavailable` for everyone.

[`ExpirySweeper`](../backend/app/src/main/java/ua/kostenko/battleship/app/config/ExpirySweeper.java) runs every `sweep-interval-seconds`: it settles and removes forgotten games, tells the hub to close affected streams, logs `game-expired` for games that expired unfinished, and prunes rate-limit buckets. It only reclaims memory; what a request is told never depends on whether the sweep has run.

## Errors

Every failure is one `application/problem+json` document with a stable `code`, built from the generated `Problem` DTO. Titles are fixed text per code; exception messages, stack traces and request content never reach the response.

Flow of an error:

1. A use case or the rules throw an [`ApplicationFailure`](../backend/application/src/main/java/ua/kostenko/battleship/application/result/ApplicationFailure.java) with a code string (and optionally a field, a rule and a retry delay). The application layer knows no HTTP.
2. [`ProblemAdvice`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/ProblemAdvice.java) maps the code to a status and title through one table, and adds `correlationId` and `retryAfterSeconds` (also sent as `Retry-After`). Unknown codes become `500 internal-error`.
3. Malformed JSON is `400 malformed-request`; unknown fields, wrong types and bean-validation failures are `422 validation-failed` with a `violations` list of RFC 6901 pointers and rule names; wrong media type is `415`; an unknown path is `404 game-unavailable`; anything unexpected is `500 internal-error` (logged with its stack, answered sanitized).
4. Filters write the same document through `ProblemAdvice.writeProblem`, so there is one shape.

| Status | Codes |
|---|---|
| 400 | `malformed-request` |
| 401 | `session-required` |
| 403 | `request-security-rejected` |
| 404 | `game-unavailable` |
| 409 | `invitation-unavailable`, `browser-game-limit`, `action-not-allowed`, `placement-out-of-bounds`, `placement-overlap`, `placement-touching`, `random-arrangement-failed`, `target-already-fired` |
| 410 | `game-expired` |
| 413 | `payload-too-large` |
| 415 | `unsupported-media-type` |
| 422 | `validation-failed` |
| 429 | `rate-limit-exceeded` |
| 500 | `internal-error` |
| 503 | `service-unavailable` |

Meaning and examples of each code: the contract. The health endpoint's 503 carries a `Health` body, not a problem document.

## DTO generation

The wire types in package `ua.kostenko.battleship.app.web.dto` are **generated, never written or committed**.

1. `openapi-generator-maven-plugin` runs in `app` at the `generate-sources` phase, reading [`contracts/openapi.yaml`](../contracts/openapi.yaml) (see [`backend/app/pom.xml`](../backend/app/pom.xml)).
2. It writes models and three helper classes (`JSON`, `RFC3339DateFormat`, `AbstractOpenApiSchema`) to `backend/app/target/generated-sources/openapi`. It generates no API classes and no client; the controllers are hand-written.
3. Build output only: excluded from Spotless and from coverage, and fixed by editing the contract and rebuilding.
4. A strict Jackson mapper bean in [`JacksonConfig`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/JacksonConfig.java) is shared by the HTTP converter, the filters' problem writer and the SSE hub, so the wire format is identical everywhere and unknown request fields are rejected.
5. Because the DTOs have Jackson annotations they cannot sit in `domain` or `application`. `SnapshotProjector` builds a framework-free `SnapshotView`; `SnapshotDtoAssembler` copies it into the generated `GameSnapshot`.

To change the wire format: edit `openapi.yaml` and its embedded example, then update the backend and [frontend](../frontend/package.json) (`npm run generate`) consumers in the same change.

## Configuration reference

All settings are the `battleship.*` properties bound to [`BattleshipProperties`](../backend/app/src/main/java/ua/kostenko/battleship/app/config/BattleshipProperties.java); defaults are in [`application.yaml`](../backend/app/src/main/resources/application.yaml).

How to set one:

1. Pick the property, for example `battleship.max-concurrent-games`.
2. As an environment variable, use Spring's relaxed binding: upper case, remove the dashes, join nesting levels with `_`. Example: `BATTLESHIP_MAXCONCURRENTGAMES=50`, `BATTLESHIP_RATELIMIT_JOINPERMINUTE=10`. ([`config/local.env.example`](../config/local.env.example) lists them this way.)
3. Command-line and system properties work too (`--battleship.max-concurrent-games=50`).
4. Start the backend. Every integer must be present and at least 1, and `public-base-url` must be valid, otherwise it **refuses to start** with a message naming the property.

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `battleship.idle-timeout-seconds` | `BATTLESHIP_IDLETIMEOUTSECONDS` | 900 | A game with no accepted command, join or presence for this long expires |
| `battleship.max-game-duration-seconds` | `BATTLESHIP_MAXGAMEDURATIONSECONDS` | 7200 | Hard ceiling on a game's life from creation |
| `battleship.result-retention-seconds` | `BATTLESHIP_RESULTRETENTIONSECONDS` | 300 | How long a finished, abandoned or expired game stays readable to its players |
| `battleship.invitation-lifetime-seconds` | `BATTLESHIP_INVITATIONLIFETIMESECONDS` | 900 | Life of an invitation (restarted by a new invitation) |
| `battleship.presence-interval-seconds` | `BATTLESHIP_PRESENCEINTERVALSECONDS` | 300 | A seat's presence extends the idle deadline at most once per interval; also published to clients as the interval to call presence |
| `battleship.heartbeat-seconds` | `BATTLESHIP_HEARTBEATSECONDS` | 15 | Interval of the SSE `: keep-alive` comment; published to clients |
| `battleship.stream-max-lifetime-seconds` | `BATTLESHIP_STREAMMAXLIFETIMESECONDS` | 1200 | A stream older than this ends silently; the browser reconnects |
| `battleship.max-concurrent-games` | `BATTLESHIP_MAXCONCURRENTGAMES` | 100 | Games in memory; more answers 503 |
| `battleship.max-concurrent-sessions` | `BATTLESHIP_MAXCONCURRENTSESSIONS` | 200 | Browser sessions in memory; idle ones without a game are evicted first |
| `battleship.max-concurrent-streams` | `BATTLESHIP_MAXCONCURRENTSTREAMS` | 200 | Open event streams; more answers 503 |
| `battleship.stream-retry-after-seconds` | `BATTLESHIP_STREAMRETRYAFTERSECONDS` | 1 | `Retry-After` sent when the stream ceiling is reached |
| `battleship.max-live-games-per-browser` | `BATTLESHIP_MAXLIVEGAMESPERBROWSER` | 1 | Live games one session may be in; more answers `409 browser-game-limit` |
| `battleship.max-request-body-bytes` | `BATTLESHIP_MAXREQUESTBODYBYTES` | 16384 | Largest accepted request body; more answers 413 |
| `battleship.random-arrangement-attempts` | `BATTLESHIP_RANDOMARRANGEMENTATTEMPTS` | 1000 | Rejected candidates allowed before random placement fails |
| `battleship.sweep-interval-seconds` | `BATTLESHIP_SWEEPINTERVALSECONDS` | 30 | Delay between memory sweeps |
| `battleship.shutdown-drain-seconds` | `BATTLESHIP_SHUTDOWNDRAINSECONDS` | 5 | Shutdown wait for running requests; also `Retry-After` while draining. Feeds `spring.lifecycle.timeout-per-shutdown-phase` |
| `battleship.public-base-url` | `BATTLESHIP_PUBLICBASEURL` | `http://localhost:5173` | The one public origin: base of invitation links and the only `Origin` accepted on unsafe requests. Absolute `http(s)`, with a host, no credentials, query or fragment. Must equal the URL typed in the browser |
| `battleship.trust-forwarded-for` | `BATTLESHIP_TRUSTFORWARDEDFOR` | false | Use the last `X-Forwarded-For` entry as caller address (see [rate limiting](#rate-limiting)) |
| `battleship.rate-limit.table-capacity` | `BATTLESHIP_RATELIMIT_TABLECAPACITY` | 10000 | Most buckets kept |
| `battleship.rate-limit.create-game-per-minute` | `BATTLESHIP_RATELIMIT_CREATEGAMEPERMINUTE` | 5 | Create game, per address |
| `battleship.rate-limit.join-per-minute` | `BATTLESHIP_RATELIMIT_JOINPERMINUTE` | 20 | Join, per address |
| `battleship.rate-limit.commands-per-minute` | `BATTLESHIP_RATELIMIT_COMMANDSPERMINUTE` | 60 | Commands, per session |
| `battleship.rate-limit.read-game-per-minute` | `BATTLESHIP_RATELIMIT_READGAMEPERMINUTE` | 120 | Game reads, per session |
| `battleship.rate-limit.presence-per-minute` | `BATTLESHIP_RATELIMIT_PRESENCEPERMINUTE` | 30 | Presence, per session |
| `battleship.rate-limit.stream-open-per-minute` | `BATTLESHIP_RATELIMIT_STREAMOPENPERMINUTE` | 30 | Stream opens, per session |
| `battleship.rate-limit.replace-invitation-per-minute` | `BATTLESHIP_RATELIMIT_REPLACEINVITATIONPERMINUTE` | 10 | New invitations, per session |
| `battleship.rate-limit.leave-per-minute` | `BATTLESHIP_RATELIMIT_LEAVEPERMINUTE` | 10 | Leave, per session |

`GET /meta` returns six of these (idle timeout, maximum duration, result retention, invitation lifetime, presence interval, heartbeat) so clients follow the server's values.

Other Spring settings used: `logging.structured.format.console: ecs`, `server.shutdown: graceful`, `spring.web.resources.add-mappings: false` (the backend serves no UI). Standard Spring variables such as `SERVER_PORT` (default 8080) and `SERVER_ADDRESS` are what the scripts use to bind the backend to `127.0.0.1`.

## Logging

- Console output is structured JSON in Elastic Common Schema format (`logging.structured.format.console: ecs`).
- Each request has a random 16-hex `correlationId` in the log context, plus the `gameId` when the path names one. The same id is returned in problem documents so a user report can be matched to the log. Scheduler ticks get an id of their own.
- [`OperationalEvents`](../backend/app/src/main/java/ua/kostenko/battleship/app/observability/OperationalEvents.java) emits exactly four diagnostic events: `command-rejected` (with the problem code), `game-expired`, `state-lost-on-restart` (once at start-up) and `realtime-delivery-failed`.
- Records never contain names, cookies, secrets, boards, request bodies or client addresses; `LogRedactionIT` checks this.

## Build, run, test

From the repository root. Java 25 is required.

| Goal | Command |
|---|---|
| Backend gate (compile, unit tests, integration tests, format check) | `cd backend && ./mvnw -q verify && ./mvnw spotless:check` |
| Build the runnable JAR without tests | `cd backend && ./mvnw -DskipTests package` (result: `backend/app/target/battleship-app.jar`) |
| Run it on `http://localhost:5173` as the public origin | `BATTLESHIP_PUBLICBASEURL=http://localhost:5173 java -jar backend/app/target/battleship-app.jar` |
| Run UI and backend together for use | `./scripts/local-deploy.sh localhost` (or a LAN IP; see [local operation](local-operation.md)) |
| Whole product gate (contract, backend, frontend, browser journeys) | `./scripts/local-build.sh` |

How `verify` works: the lifecycle generates the DTOs, compiles with `-Werror`, runs the unit tests (Surefire), packages the JAR, runs the `*IT` tests (Failsafe) and runs Spotless (palantir-java-format) in the `verify` phase. After a backend change, rebuild the JAR and restart: the packaged backend is not hot-reloading ([developer guide](development.md)). The Docker images are described in [local operation](local-operation.md#docker-images).

## Testing approach

Tests use real collaborators and check observable behavior; mocks are avoided. The only test doubles are the ports whose real versions are non-deterministic.

| Level | Where | What it proves |
|---|---|---|
| Domain unit | [`domain/src/test`](../backend/domain/src/test/java/ua/kostenko/battleship/domain/) | Rules, rulesets, placement, ready and first turn, allowed actions, and that a refused command leaves state unchanged. Randomness comes from `SeededRandomSource`. |
| Application unit | [`application/src/test`](../backend/application/src/test/java/ua/kostenko/battleship/application/) | Every use case against the real registries with `MutableTimeSource`, `FixedSecretGenerator` and `RecordingPublisher`: idempotency, invitation lifecycle, lifetime boundaries, leave, presence throttle, projection privacy. |
| Architecture | [`ArchitectureTest`](../backend/app/src/test/java/ua/kostenko/battleship/app/ArchitectureTest.java) | The dependency rule (ArchUnit). |
| Integration (`*IT`) | [`app/src/test`](../backend/app/src/test/java/ua/kostenko/battleship/app/) | The real Spring application on a random port with real HTTP: controllers, problem mapping, authorization, security headers and filters, rate limits, SSE streams and races, capacity limits, configuration validation, log redaction, graceful shutdown. |
| Contract conformance | `WireConformanceIT` with `Contract` | Every operation's success and failure bodies compared field by field with `openapi.yaml` read as data. |
| Packaged JAR | `PackagedArtifactIT`, `PublishedLimitsIT` via `PackagedService` | Starts the executable JAR with `java -jar` and a controlled environment, so real property binding and packaging are exercised. |

The browser journeys in `frontend/e2e` then run against the packaged backend (see the [developer guide](development.md#checks)); there is no fake engine anywhere.

Rule for new work: a behavior change comes with a test at the lowest level that can observe it; a wire change also updates the contract example so conformance keeps passing.
