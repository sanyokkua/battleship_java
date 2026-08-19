# Copy-paste input: anonymous Battleship backend

Create feature `002-backend` for the production-ready anonymous Battleship rewrite. Implement one server-authoritative, ephemeral backend that conforms to the published `001-api-contract` contract. The backend has no database, no durable state, no frontend assets, and no alternate public API.

## Purpose and scope

Players need a private, correct two-player game that remains responsive and recoverable while one process lives, yet is honest that restart, redeploy, or host loss destroys all games. The backend must replace the existing ID-as-authorization, mutable aggregate, unbounded lock/SSE registry, and direct DTO exposure with a bounded, player-safe service.

Deliver one executable backend-only application JAR that can be started and checked locally. Its internal module and folder design belongs to the companion HLSD/plan input, not to this behavior specification.

## Non-goals

Do not add a database, cache-as-authority, message broker, distributed lock, external session store, multiple replicas, frontend bundle, generated frontend client, account system, spectator mode, chat, or durable replay/history. Do not change the published contract except through a separate contract feature.

## EARS requirements

### Domain correctness and rules

- **Two rulesets:** The system shall accept exactly the two initial rulesets from the contract and enforce their distinct fleet, adjacency, turn-retention, contact, and shot-result semantics.
- **Shot meanings:** When a sea-battle ship sinks, the system shall represent automatically revealed moat water separately from a fired shot. When a classic ruleset player fires a hit or a miss, the system shall change the turn after that one shot.
- **Illegal command stability:** If a command is illegal for the phase, actor, selected ruleset, coordinate, fleet, finished state, or repeated target, the system shall preserve the current game state and version.
- **Start and disclosure:** When both players have irreversibly readied valid fleets, the system shall start play using the random starting seat selected at creation. When normal play or resignation becomes terminal, the system shall allow final full-board disclosure only through each former member's player-safe terminal projection. Pre-play abandonment shall not disclose either placement.
- **Authoritative statistics:** For normal finish or resignation, the system shall calculate the contract's duration, turn, shot, hit, accuracy, shot-decision timing, and fleet-condition statistics from accepted transitions and injected time. It shall use the documented guest-join and accepted-fire timing boundaries and shall not create fleet-derived statistics for pre-play abandonment.

### Application boundary and concurrency

- **Player-safe output:** When any HTTP or SSE result is produced, the system shall derive the authenticated seat from the presented capability and return only that seat's contract-defined player-safe projection.
- **Per-game serialization:** When commands for the same game arrive concurrently, the system shall serialize their authorization, deduplication, version check, transition, version increment, result recording, and event enqueue as one per-game operation. Commands for unrelated games shall not share a game mutation lock.
- **Versioned transition:** A newly created game shall start at `gameVersion: 0`. When guest join, invitation-status rotation, pre-play abandonment, or a game command changes the shared player-safe game view, the system shall increment `gameVersion` exactly once, save the immutable state, and enqueue newer player-safe full snapshots. An accepted command shall also record and return its bounded deduplication result. Presence, reads, failures, and revoking one member after any terminal result shall not increment the version. No blocking network write or unbounded work shall occur while holding the game mutation lock.
- **Command retry:** When an identical retry arrives, the system shall return the deduplicated result before checking version freshness. If its normalized payload differs, the system shall reject it as a conflict. If a new command has missing or stale preconditions, the system shall produce the contract's 428 or 412 result without changing state.
- **Leave retry:** The system shall apply `commandId`, normalized-content, `expectedVersion`, `If-Match`, duplicate-before-precondition, and 428/412 rules to the separate leave use case. It shall keep at most two bounded leave receipts per seat so a pre-play abandonment result and a later terminal revocation remain independently retryable; an identical retry after revocation returns the prior safe 204 result without restoring authority, and every other operation with that revoked capability remains unauthorized.

### Anonymous security and HTTP behavior

- **Capability generation:** The system shall generate high-entropy anonymous seat capabilities with a cryptographically secure source and store only their server-side digests. It shall never authorize a game operation from a public game ID or client-supplied seat/role field.
- **Web request defenses:** The system shall issue, verify, and revoke the exact host-only Secure HttpOnly SameSite cookie required by the contract, and shall enforce the separate CSRF cookie/header, exact configured frontend Origin, and documented Fetch Metadata policy for unsafe requests.
- **Invite redemption:** The system shall redeem a fragment-originated invitation secret only through its documented POST body, atomically consume it, bind one guest seat, and revoke it on rotation, abandonment, expiry, or use.
- **Public URL configuration:** The system shall validate one configured public frontend base URL, preserve its application subpath, and use it as the sole source for canonical invitation URLs returned by creation and rotation. It shall reject a base with credentials, query, fragment, an unsafe scheme outside the local profile, or a hostname different from the configured public API hostname.
- **Abuse limits:** The system shall enforce the contract's initial limits of five creations per minute per bounded source key, twenty invitation attempts per minute per bounded source key, and sixty commands per minute per capability. Snapshot reads, visible presence, and stream opens shall also use bounded abuse controls and the one-stream-per-seat rule. Every attacker-keyed rate-limit structure shall be bounded and shall return the contract's problem without revealing membership or secret state.
- **Safe adapter output:** The system shall return only contract-defined RFC 9457 problems and response headers. It shall mark authenticated/sensitive responses non-cacheable and shall not serialize a capability, digest, hidden board, raw command history, exception stack trace, or implementation detail.

### Lifecycle, capacity, and realtime

- **Capacity permits:** The system shall admit no more than the configured initial 100 games and 200 active streams, reject overload without evicting a live game, and release each capacity allocation exactly once on removal. These values are conservative starting configuration, not a claim of measured capacity.
- **Idle activity:** The system shall expire a game after 15 minutes without an accepted command or accepted explicit visible-tab presence, whichever last occurred. Reads, snapshot polling, SSE connects/reconnects, SSE heartbeats, and failed commands shall not refresh idle life.
- **Presence throttle:** While a visible authenticated player submits presence no more than once per five minutes, the system shall extend idle life without changing `gameVersion`. An earlier well-formed call shall return the current expiry with `extended: false`; it shall not extend the deadline. Abusive presence traffic shall use the bounded 429 rate-limit result.
- **Fixed lifetimes:** The system shall enforce a two-hour absolute game lifetime, retain finished/abandoned games for five minutes, and expire invitations after 15 minutes or game expiry, whichever comes first.
- **Expiry race:** The system shall check lifecycle validity on every relevant access and sweep for cleanup using a monotonic elapsed-time source. A late request shall not resurrect an expired game.
- **Process epoch:** The system shall generate a new `serverEpoch` for each process start, persist no prior games, and make restart loss observable through metadata and the contract's unavailable behavior.
- **Stream registration:** When an authenticated SSE connection opens, the system shall atomically bind it to the seat and capture an initial player-safe snapshot. It shall permit one live stream per seat, replace an older stream, send full snapshots with event ID equal to `gameVersion`, heartbeat every 15 seconds, cap streams at 20 minutes, and keep replay/outbound queues bounded.
- **Slow stream:** When SSE delivery is slow, failing, or superseded, the system shall close it rather than retaining unbounded work. It shall perform stream I/O outside the game mutation lock and recover correctness through snapshot reads rather than depending on replay.
- **Graceful shutdown:** When graceful shutdown begins, the system shall stop admission, report non-readiness, attempt a bounded draining notice/stream close, wait briefly for in-flight commands, and terminate without claiming game continuity.
- **Leave lifecycle:** When `LEAVE` is accepted before play, the system shall abandon the shared game, increment `gameVersion` once, revoke the invitation and gameplay authority, publish the safe abandoned snapshot, retain only safe read access for both former members during terminal retention, and disclose no placement. During play the leave endpoint shall return the contract's `resign-required` conflict without changing state. When `LEAVE` is accepted after abandonment, normal finish, or resignation, it shall revoke only the caller's read membership, close that caller's stream, and return 204 without changing `gameVersion`, the terminal result, or the other member's access.

### Operations and verification

- **Packaged runtime:** The system shall run as one executable application JAR using Java 25 LTS, Spring Boot 4.1.0, and its Maven 3.9.16 Wrapper. It shall not bundle or serve frontend assets.
- **Health exposure:** The system shall expose only non-sensitive liveness/readiness information through the contracted public health resource and protect metrics/configuration surfaces.
- **Log redaction:** The system shall emit structured, redacted logs with correlation IDs, event outcome, game correlation hash, latency, and version while never logging capabilities, invitation secrets, cookies, fragments, full secret-bearing URLs, full boards, or unnecessary player names.
- **Local expiry profile:** The normal application profile shall keep the fixed 15-minute idle lifetime. A clearly named local-integration profile may shorten that one duration to prove live expiry against the packaged JAR; it shall expose no clock-control endpoint, shall not alter lifecycle code, and shall not activate implicitly.
- **Behavior evidence:** The system shall be proved by pure domain/application tests and adapter integration tests covering contract conformance, security, race conditions, privacy, SSE, lifecycle, and shutdown. It shall not use fake database tooling because it has no database, and it shall not treat mechanical coverage/style quotas as product evidence.

## Acceptance scenarios and proving tests

| Scenario | Proving test |
| --- | --- |
| Both rulesets accept legal fleets and exhibit their different contact, turn, and moat behavior. | Pure domain examples, invariant/property tests, and mutation-sensitive edge tests. |
| Simultaneous commands for one game linearize, while distinct games progress independently. | Application race test with injected barriers and deterministic state assertions. |
| A lost command response is retried, an identifier is maliciously reused, and a stale version is supplied. | Use-case and adapter tests prove dedupe-before-version, conflict, 428, and 412 outcomes. |
| Public IDs, missing CSRF, cross-origin unsafe requests, leaked invitation attempts, and wrong-seat actions occur. | Security integration matrix proves cookie/CSRF/origin/authentication/authorization and redaction behavior. |
| A browser reconnects, misses events, opens a replacement stream, and observes a server restart. | SSE integration tests prove initial snapshots, IDs, bounded recovery, stream replacement, and epoch handling. |
| Idle, presence, absolute lifetime, finishing, invite expiration, capacity exhaustion, and shutdown boundaries occur. | Lifecycle tests with injected monotonic time, permits, and controlled shutdown prove no resurrection or live-game eviction. |
| Two internal games vary only in undiscovered opponent ships. | Projection privacy test proves identical opponent-facing serialization until final disclosure. |

## Completion evidence

The backend is complete only when it conforms to the pinned contract bundle, packages as one backend-only JAR, and has fresh test evidence for the critical scenarios above. Contract ownership remains with `001-api-contract`; backend work may add conformance fixtures but must refer a wire change back to that feature.
