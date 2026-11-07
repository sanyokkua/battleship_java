## Context

One Spring Boot service implements every operation of `contracts/openapi.yaml` under `/api/v1` and nothing else. All state is in memory; the server owns every rule, projection and lifetime. Three Maven modules keep rules pure, orchestration testable and the framework at the edge. Authority order: contract, then spec (delta specs in this change), then this design. Source: the original workflow `the original 002-backend plan documents`.

### Goals / Non-goals

- Goals: a server-authoritative anonymous Battleship backend with proofs in ten areas; all limits externally configurable and published as enforced; a local backend-only executable.
- Non-goals: see proposal.md Out of scope. Assurance level is Standard (AGENTS.md rule 2); escalation is an owner decision.

### User stories (all covered by delta specs)

- US1 (P1) host creates a game and shares an invitation: `backend-sessions`, `backend-lifetimes`.
- US2 (P1) guest redeems the link and joins: `backend-sessions`.
- US3 (P1) both players arrange fleets privately and declare ready: `backend-game-rules`, `backend-snapshots`, `backend-actions`.
- US4 (P1) players fire in turns until there is a winner: `backend-game-rules`.
- US5 (P2) recovery after reload, lost answer, dropped stream or second tab (and simultaneous actions): `backend-actions`, `backend-snapshots`, `backend-realtime`.
- US6 (P2) result and statistics, then leave: `backend-statistics`, `backend-actions`, `backend-lifetimes`.
- US7 (P3) operator runs and observes the packaged server locally (graceful shutdown scenario): `backend-operations`.
- Edge cases: simultaneous ready (R11, R25), expiry mid-request (R47), stream loops (R31, R61), retry after finish (R24).

### Key entities

Game (match between two anonymous players, in memory); Ruleset (immutable published rules); Board (grid plus ships, owner and opponent projections); Ship (id, type, length, placement, intact/damaged/sunk); Player (name, ready, stream open, ships afloat); Snapshot (the only representation of a game that leaves the server); Invitation (one-time high-entropy secret, own expiry, replaceable by host); Session (anonymous browser identity owning seats across games); Action (one attempt with a caller-generated id); Statistics (durations, shots, hits, accuracy, turn and shot-decision timings, fleet condition).

## Decisions

### Layout and module rules

```text
backend/
  pom.xml                  reactor; spring-boot-starter-parent 4.1.1
  mvnw mvnw.cmd .mvn/wrapper/
  domain/       ua.kostenko.battleship.domain       model/ rules/ command/ transition/
  application/  ua.kostenko.battleship.application usecase/ port/ projection/ registry/ result/
  app/          ua.kostenko.battleship.app          web/ security/ realtime/ config/ observability/
                target/generated-sources/openapi/   generated wire DTOs (app.web.dto), build output
```

| Module | May depend on | Owns | Must not contain |
|---|---|---|---|
| domain | JDK only | raw game types and rules | Spring, Jackson, servlet, clock, random, logging, I/O |
| application | domain | in-memory game and session registries, use cases, player-safe projection | controllers, cookies, emitters, framework types |
| app | domain, application | Spring HTTP and SSE adapters, composition | game rules, authoritative game or session state, a second projection, UI assets |

`app` produces the single executable JAR (R57). Direction is enforced by Maven Enforcer `bannedDependencies` per module and one ArchUnit test scoped to main classes. Test doubles are shared downstream through `test-jar` executions: `SeededRandomSource` (domain/src/test) and `MutableTimeSource` (application/src/test). Generated DTOs carry Jackson annotations so they live in neither domain nor application; therefore the projector returns the framework-free `SnapshotView` and `SnapshotDtoAssembler` in `app/web` copies it 1:1 into the generated `GameSnapshot` (D31).

### Stack

Java 25 (25.0.4); Maven 3.9.16 via Wrapper (generated once with the global Maven, wrapper plugin 3.3.4, script-only); `spring-boot-starter-parent` 4.1.1 with `starter-{web,validation,security,actuator}`; `openapi-generator-maven-plugin` 7.25.0 (app only, `generate-sources`); Enforcer 3.6.3; Spotless 3.9.0 with palantir-java-format; Surefire/Failsafe 3.5.6; ArchUnit and `spring-boot-starter-test` (test scope); Jackson 2.21.5 runtime modules for generated models. All versions exact. Spring Boot 4.2.0 deliberately not targeted (only `4.2.0-M1` existed).

Deliberately not added: JaCoCo or any coverage gate (Constitution IV), Checkstyle, PMD, Error Prone, NullAway, Caffeine (the sweeper prunes the two bounded maps), Testcontainers, WireMock, PIT, Jazzer, jqwik, Mockito (real collaborators plus mutable `TimeSource` and seeded `RandomSource`, D23). The generic `Standards_and_recommendations/` pins (JDK 21, Boot 3.x, Gradle, `/health/live`, snake_case ids, coverage gate, Testcontainers) are superseded by the constitution (D24).

### Runtime design

- Registry and slots: `application/registry.GameRegistry` holds `ConcurrentHashMap<GameId, GameSlot>` and a `Semaphore` sized from `max-concurrent-games`; a permit is acquired before insertion and released exactly once on removal; `map.size()` is never admission control. Over a ceiling the answer is `503 service-unavailable` with `Retry-After`; nothing is evicted and readiness stays true (R39, R04, S11). `GameSlot` lock is a `ReentrantLock` (never `synchronized`, which pins virtual threads) and holds framework-free connection flags, never a subscriber. `SessionRegistry` maps a SHA-256 digest to a session record, bounded by the sweeper and a hard cap with drop-least-recently-used (R32, R41).
- Adapters: `MetaController` (getMeta), `RulesetController`, `HealthController`, `GameController` (createGame, getGame), `JoinController`, `CommandController`, `EventsController`, `InvitationController`, `PresenceController`, `LeaveController`, each calling its use case (`CreateGameUseCase`, `GetGameUseCase`, `JoinGameUseCase`, `CommandUseCase`, `SubscribeUseCase`, `ReplaceInvitationUseCase`, `PresenceUseCase`, `LeaveGameUseCase`). `joinGame` answers 200 for an existing live guest, `410 game-expired` for the same guest on an expired retained game, otherwise `409 invitation-unavailable`. `Meta.apiVersion` is the constant `1.0.0` bumped by hand with the contract. `DisplayNameNormalizer` (domain/model) implements R64. Inbound bodies are the generated request types.
- Command path (R25): filters (session, CSRF, site isolation, rate limit) then `CommandUseCase` then the slot lock. Under the lock, in one step: expiry check, membership, `commandId` seen?, pure transition, state replacement, version bump, record of seats whose view changed. No projection, JSON write, log or SSE write under the lock. Idempotency is `Set<UUID>` of accepted ids alone (D9), bounded by 60 actions/min times the 2 h ceiling.
- Projection: `SnapshotProjector.project(GameState, Seat, SnapshotContext)` is the only decider of disclosure and `allowedActions` for HTTP and SSE. `SnapshotContext` carries `serverTime`, `expiresAt`, `invitationUrl?`, `invitationExpiresAt?` and each seat's `connected` flag, built by `GameSlot.contextFor(Seat, Instant)` (D27). A use case captures immutable `GameState` and context under the lock and projects after release.
- Realtime: Spring MVC `SseEmitter` with `spring.threads.virtual.enabled=true` (D6). The hub installs a pending subscriber before the use case takes the game lock; under the lock the use case authorizes, updates connection state and captures state; after unlocking it projects and queues the first `snapshot` event, then drains newer buffered updates. A late publish at or below the last queued or sent version is discarded. Event `id` is the version. One emitter per (session, game); a newer one closes the older with `closed`/`REPLACED` (no flag change); `closed`/`GAME_UNAVAILABLE` only when the game stops existing for the browser. One scheduler sends heartbeats; `stream-max-lifetime-seconds` ends a stream with no `closed`; a subscriber keeps only the latest unsent snapshot and is dropped if unwritable. The hub serializes through the same `SnapshotDtoAssembler` and Spring `ObjectMapper` as controllers. Heartbeat scheduler and expiry sweeper expose package-visible `tick()` reading the injected `TimeSource`; `@Scheduled` drives `tick()` only in production (R60).
- Request security: one stateless `SecurityFilterChain`, `permitAll`, CORS never configured, CSRF via `CookieCsrfTokenRepository` customised to `XSRF-TOKEN`, `Path=/`, `Secure`, `SameSite=Strict`, script-readable, with a plain `CsrfTokenRequestAttributeHandler` (`csrfRequestAttributeName=null`) because the default XOR handler masks the token (D26). `MetaController` resolves the token eagerly. Own filters: `SessionCookieFilter` (`__Host-battleship_session`), `SiteIsolationFilter`, `RateLimitFilter` (fixed 60 s window per key per operation class, D16), `RequestSizeFilter`, `CorrelationIdFilter`. CSRF (403) is evaluated before session (401). Jackson rejects unknown fields; Bean Validation failures and unknown fields map to `422 validation-failed` with `Violation{field: JSON Pointer, rule}`. `replaceInvitation`, `sendPresence`, `leaveGame` declare no `consumes`.
- Join ordering (R34, R63): `JoinGameUseCase` checks membership first under the slot lock; only a live nonmember reaching an available seat has its invitation digest compared in constant time and atomically consumed. Per-browser cap (R62): create and join are refused when `SessionRecord.liveGames` holds `max-live-games-per-browser` entries; a game id leaves the set when the game ends or the player leaves. (Note: plan text says `503 service-unavailable`; spec R62 and S11 say `409 browser-game-limit` and the spec governs.)
- Leaving (R16): `LeaveGameUseCase` returns no payload; controller answers 204. `WAITING` host removes the game; `PLACEMENT` replaces state with `ABANDONED`; `PLAYING` refused `409 action-not-allowed`; after the end only that seat's session digest is cleared.
- Failures (R49, R50): one `@RestControllerAdvice` maps every outcome to the generated `Problem` (`application/problem+json`) using the contract's code-to-status table; use cases throw framework-free `ApplicationFailure` (code string, optional field/rule, optional retry delay); `CommandUseCase` converts a domain `Rejection` via `wireCode()`; Spring Security's `AccessDeniedHandler` uses the same problem writer.
- Lifetimes (R42-R48, R63): absolute `Instant` deadlines compared inclusively; expiry checked on every slot access before rules; a `@Scheduled` sweeper reclaims memory every `sweep-interval-seconds` via `ExpireGamesUseCase`; only creation, guest join, an accepted action and accepted presence move the idle deadline; `terminalRetentionDeadline` is set from the instant a game ended or expired.
- Configuration and observability (R52-R59): one validated `@ConfigurationProperties(prefix="battleship")` record `BattleshipProperties` with nested `RateLimits`. Keys: `idle-timeout-seconds`, `max-game-duration-seconds`, `result-retention-seconds`, `invitation-lifetime-seconds`, `presence-interval-seconds`, `heartbeat-seconds` (these first six equal the contract `Limits`), `stream-max-lifetime-seconds`, `max-concurrent-games`, `max-concurrent-streams`, `max-live-games-per-browser`, `max-request-body-bytes`, `random-arrangement-attempts`, `sweep-interval-seconds`, `shutdown-drain-seconds`, `public-base-url`, `rate-limit.{create-game,join,commands,read-game,presence,stream-open,replace-invitation,leave}-per-minute`. Defaults are spec R52's only. Constraint annotations plus a `@PostConstruct` URL check fail start-up naming the setting. Logging uses Spring Boot 4 built-in structured output with correlation id and `gameId`. `GET /api/v1/health` is hand-written over `ApplicationAvailability`; Actuator is off the public surface. `server.shutdown=graceful`, readiness flips to `DRAINING` first, streams close with no `closed` event, events endpoint answers `503` while draining.
- Ports: `TimeSource.now()`, `RandomSource` (declared in domain, because `GameRules.apply` takes it), `SecretGenerator` (`SecureRandom`: 128-bit base64url game ids `^[A-Za-z0-9_-]{22}$`, 256-bit session values, 256-bit invitation secrets `^[A-Za-z0-9_-]{43}$`), `SnapshotPublisher` (`publish(String gameId, Seat, SnapshotView)` and `unavailable(String gameId, Seat)`, implemented by the SSE hub, called after releasing the lock). Game ids are plain strings. Superseded fifth port `GameSlotStore` (Decision A1).

### Domain model (condensed data-model)

Immutable records: `GameState(rulesetId, phase, version, host, guest?, turn?, lastShot?, outcome?, timeline)`; `PlayerState(displayName, ready, board, shotsFired)`; `Board(fleet, incomingShots, revealedWater)`; `Ship(shipId, shipTypeId, length, anchor?, orientation?, hits)` with derived cells and status; `Coordinate(rowIndex, columnIndex)` zero-based; `Ruleset`; `Shot(by, target, result, sunkShipId?)`; `Outcome(winner, reason)`; `Timeline`; `Transition(next, versionBumped, viewChanged, rejection?)`; `Rejection`. `Seat` is HOST or GUEST internally and mapped to YOU or OPPONENT only in the projector. `GameRules.apply(GameState, Seat actor, GameCommand, Instant now, RandomSource random)` returns `Transition`, reads no clock and creates no randomness. Version starts at 0, is per game, monotonic, never reset.

State machine: WAITING (createGame) to PLACEMENT (joinGame); WAITING removed on host leave; PLACEMENT to PLAYING (second READY); PLACEMENT to ABANDONED (leave by either); PLAYING to FINISHED (last ship sunk or RESIGN); FINISHED and ABANDONED removed after retention. Expiry is a registry decision, not a phase.

Invariants asserted after every accepted transition: (1) ships wholly on board, straight, non-overlapping; (2) where `shipsMayTouch` is false no two ships within Chebyshev distance 1; (3) fleet multiset equals the ruleset's; (4) hits subset of cells, hits and incoming shots only grow; (5) own-board cells carry exactly one of SHIP, HIT, SUNK, MISS, REVEALED_WATER, WATER, never UNKNOWN; (6) turn present exactly in PLAYING, outcome and statistics exactly in FINISHED; (7) version rises by exactly one when a transition changes at least one view; (8) a refused command returns the input state by identity; (9) ready never returns to false; (10) own board has no UNKNOWN cell; (11) two states differing only in the opponent's undiscovered ships project identically for that opponent until FINISHED.

Rule details: PLACE_SHIP relocates and rotates atomically; the four refusal reasons are distinct; PLACE_FLEET_RANDOMLY places longest ships first, gives up after the attempt limit with `random-arrangement-failed`; FIRE on a disclosed cell is `target-already-fired`; first turn by ready order with random tie-break; ending is atomic with FINISHED. Statistics boundaries are in the `backend-statistics` spec.

Registry state: `GameRegistry` (map plus capacity semaphore); `GameSlot` (`ReentrantLock`, `GameState`, host and guest session digests, invitation digest, `unusedInvitationSecret?` as the only plaintext secret held so the host's WAITING snapshot can carry `invitationUrl`, dropped when used, replaced or expired; `Set<UUID> acceptedCommandIds`; idle, absolute and invitation deadlines; `presenceNotBefore` per seat; `terminalRetentionDeadline?`; `connectedStreams` seat to newest stream number so a replaced stream's late close changes nothing); `SessionRegistry`/`SessionRecord` (digest, createdAt, lastSeenAt, `liveGames`); `Subscriber` in `app/realtime` (emitter, seat, openedAt, single coalescing slot).

### Research decisions (D1-D31)

- D1 wire types are generated from the contract (models only, package `app.web.dto`), retiring wire drift; fallback was hand-written records for what does not generate. D2 first turn decided by ready order, exact tie from `RandomSource` (overrides seed doc's random at creation; ready-first is an accepted advantage). D3 Spring Security with one minimal chain plus three own filters. D5 three modules (constitution mandate). D6 Spring MVC plus `SseEmitter` on virtual threads (WebFlux and bounded executors rejected). D7 one `ReentrantLock` per slot (not `synchronized`, global lock, `compute`, or per-game executors). D8 `Semaphore` admission (not `map.size()` or Caffeine eviction). D9 idempotency as `Set<UUID>`, no result ring. D10 first event is current snapshot, `Last-Event-ID` ignored, no replay ring. D11 full `GameSnapshot` per event, id = version. D12 one `TimeSource.now()` returning `Instant`. D13 inclusive deadline comparison. D14 `RandomSource` separate from `SecretGenerator`. D15 plain concurrent maps pruned by the one sweeper plus hard cap with drop-LRU. D16 fixed 60 s window rate limit (token bucket, sliding window, GCRA, Bucket4j, Resilience4j rejected). D17 caller key is `request.getRemoteAddr()`, used only for limiting, never logged. D18 Spring Boot 4 built-in structured logging. D19 generated `Problem` (hand-written `ProblemDto` and Spring `ProblemDetail` rejected). D20 hand-written health controller (503 is a `Health` body). D21 ship ids `s01`...`s10`. D22 static analysis limited to Spotless, Enforcer, ArchUnit. D23 real collaborators plus mutable time and seeded random. D24 generic standards superseded by constitution. D25 one `BattleshipProperties` record with nested `RateLimits`. D26 plain CSRF attribute handler and customised cookie. D27 `SnapshotContext` built by application registry; subscribers stay in `app/realtime`. D28 generator configuration: `generatorName=java`, `library=native`, `generateModels=true`, `generateApis=false`, `generateModelTests=false`, supporting files only `JSON.java`, `RFC3339DateFormat.java`, `AbstractOpenApiSchema.java`, `useJakartaEe=true`, `serializationLibrary=jackson`, `dateLibrary=java8`, `enumPropertyNaming=MACRO_CASE`, `disallowAdditionalPropertiesIfNotPresent=false`, `openApiNullable=false`, `supportUrlQuery=false`, `Instant` mapped to `OffsetDateTime`; unknown fields enforced by Jackson `FAIL_ON_UNKNOWN_PROPERTIES=true`. D29 closed Java enums for the six open contract enums. D30 `Command` oneOf left as is (inbound only; four `type` values map to `SimpleCommand`). D31 the projector returns framework-free `SnapshotView`.

### Validation (evidence map)

Every task delivers one behaviour and its proof in the same change; there is no test phase (departing from the the original workflow template). `*Test` runs under Surefire, `*IT` under Failsafe; no test sleeps.

| # | Proof area | Owner | Module |
|---|---|---|---|
| 1 | Ruleset correctness | RulesetRulesTest | domain |
| 2 | Action legality and state stability | AllowedActionsTest, RefusedCommandStabilityTest | domain |
| 3 | Player privacy | ProjectionPrivacyTest | application |
| 4 | Repeat safety | CommandIdempotencyTest | application |
| 5 | Concurrency | GameSerializationTest | application |
| 6 | Lifetimes | LifetimeBoundaryTest, StatisticsTest | application |
| 7 | Request security and authorization | RequestSecurityIT, AuthorizationIT, RateLimitIT | app |
| 8 | Live updates | EventStreamIT | app |
| 9 | Packaging, configuration and redaction | ConfigurationValidationIT, PublishedLimitsIT, LogRedactionIT, PackagedArtifactIT, GracefulShutdownIT | app |
| 10 | Wire conformance | WireConformanceIT | app |
| - | Capacity and per-browser ceilings | CapacityIT | app |
| - | Whole game over the wire, reload recovery | GameJourneyIT | app |
| - | Module dependency direction (build guard) | ArchitectureTest | app |

Success criterion owners: S1 RulesetRulesTest + GameJourneyIT; S2 ProjectionPrivacyTest; S3 GameJourneyIT; S4 CommandIdempotencyTest; S5 LifetimeBoundaryTest; S6 AuthorizationIT; S7 GameSerializationTest; S8 EventStreamIT; S9 LogRedactionIT; S10 PackagedArtifactIT; S11 CapacityIT; S12 PublishedLimitsIT. Area 3 compares byte-identical opponent snapshots; area 5 uses a `CyclicBarrier` and asserts the set of allowed outcomes; area 6 proves each deadline at deadline minus 1 ms and at deadline; area 10 asserts bodies field by field and proves the assembler fills every field. Done means a named mutation fails the gate (AGENTS.md rule 5).

### Gate

From `backend/`: `./mvnw -q verify` (DTO generation, compile, Enforcer, Spotless check, Surefire, ArchUnit, Failsafe), `./mvnw spotless:apply` (fix-up), `java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` (R57, S10). From `contracts/`: `npm ci && npm run check`. A skipped, timed-out or capability-blocked stage is never green. Every `-pl` command carries `-am`; filtered runs add `-Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`.

### Forecast and spike

38 tasks across 7 phases (1 Reactor/toolchain/spike 6; 2 Domain 7; 3 Application 8; 4 Web adapters 7; 5 Realtime 3; 6 Abuse limits, hardening, operation 5; 7 Packaging and wire conformance 2), about 95 files. Above the 20-task line of AGENTS.md rule 3, re-approved by the owner on 2026-09-21 (Decision A2). Task 1 (tasks T002) is the generator spike with an abort condition: the 39 component schemas emit as 29 object classes and 7 enums, with `GameId`, `DisplayName`, `Instant` inlining; the four `Command` variants deserialize by `type`; `ProblemCode`'s 18 kebab-case values round-trip; the plugin runs clean on JDK 25. On failure the named fallback is hand-written records recorded as a decision.

### Quickstart (condensed)

Prerequisites: Java 25 and Node 24 on PATH (verified on Corretto 25.0.4.1, Maven 3.9.16, Node 24.21.0); after the wrapper exists only `backend/mvnw`. Commands: `cd contracts && npm ci && npm run check`; `cd backend && ./mvnw -q verify`; `./mvnw spotless:check`; `./mvnw -q -DskipTests package && java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar`. Smoke: `curl localhost:8080/api/v1/health` gives `{"live":true,"ready":true}`; `/api/v1/meta` shows six limits; `/api/v1/rulesets` shows ids; `GET /` is 404. R55/S12 check: restart with `BATTLESHIP_IDLETIMEOUTSECONDS=60` and confirm `meta.limits.idleTimeoutSeconds` is 60 and idle expiry follows; R54 check: value `0` fails start-up naming the setting. Whole-game smoke: `GET meta` into a cookie jar, read `XSRF-TOKEN`, `POST /api/v1/games` with `X-XSRF-TOKEN` and JSON, take the `#invite=` fragment, repeat with a second jar to `POST .../join`, then drive `POST .../commands` (`PLACE_FLEET_RANDOMLY`, `READY`, `FIRE`) while `curl -N .../events` streams. Automated form: `GameJourneyIT`.

## Risks / Trade-offs

- Wire drift is retired, not mitigated: a contract change not followed by the assembler fails the build; completeness remains proof area 10's job.
- Jackson 2 generated models versus Spring Boot 4's Jackson 3 default converter: DTOs must route through a Jackson 2 mapper (known limitation, verified at controller wiring).
- Generator spike verified on JDK 25 with OpenAPI Generator 7.25.0.
- Java 25 plus Spring Boot 4.1.1 is ahead of recorded standards; Surefire/Failsafe on JDK 25 is proven by the first passing `verify`.
- Declaring ready first is an advantage (D2), accepted deliberately.
- Known limitations: T003's four ArchUnit rules did not prove the pre-A1 ban on maps in application, superseded by T017's migration proof; `spring-boot:run -am` fails on the parent aggregator, so package-and-run is the smoke check; historical T008/T010 formatting debt corrected by T011.
- Next unit recorded at conversion: delivered (T001-T038 complete).
