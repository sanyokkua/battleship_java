# Implementation Plan: Battleship Backend

**Branch**: `feature/002-backend` · **Spec**: [spec.md](spec.md) · **Contract**: [`contracts/openapi.yaml`](../../contracts/openapi.yaml) · **Date**: 2026-09-21

## Summary

One Spring Boot service that implements every operation of `contracts/openapi.yaml` under `/api/v1`
and nothing else. All state is in memory; the server owns every rule, every projection and every
lifetime. Three Maven modules keep the rules pure, the orchestration testable and the framework at the
edge. Design decisions and rejected alternatives: [research.md](research.md). Domain shape, state
machine and statistics boundaries: [data-model.md](data-model.md). Commands and local smoke checks:
[quickstart.md](quickstart.md).

## Assurance level: Standard

Standard (AGENTS.md rule 2). Evidence is the ten proof areas of spec § *Proof required*, and nothing
beyond them; that section also lists what may not stand in for them. Escalation is an owner decision.

## Layout

```text
backend/
├── pom.xml                  # reactor; spring-boot-starter-parent 4.1.1
├── mvnw  mvnw.cmd  .mvn/wrapper/
├── domain/                  ua.kostenko.battleship.domain
│   └── model/ rules/ command/ transition/
├── application/             ua.kostenko.battleship.application
│   └── usecase/ port/ projection/ result/
└── app/                     ua.kostenko.battleship.app
    ├── web/ security/ registry/ realtime/ config/ observability/
    └── target/generated-sources/openapi/     # generated wire DTOs, package …app.web.dto;
                                              # build output — not committed, not hand-edited,
                                              # not formatted by Spotless
```

| Module | May depend on | Must not contain |
|---|---|---|
| `domain` | JDK only | Spring, Jackson, servlet, clock, random, logging, I/O |
| `application` | `domain` | controllers, cookies, emitters, concrete maps, framework types |
| `app` | `domain`, `application` | game rules, a second projection, any UI asset |

`app` produces the single executable JAR (R57). Direction is enforced by Maven Enforcer
`bannedDependencies` per module and by one ArchUnit test in `app` — an ArchUnit importer scoped to the
modules' **main** classes, so the shared test doubles below do not trip the rules.

Test doubles are shared downstream rather than re-declared: `domain` and `application` each attach a
`maven-jar-plugin` `test-jar` execution, and `application` (on `domain`) and `app` (on both) take
test-scoped `<type>test-jar</type>` dependencies. That is what puts `SeededRandomSource` (declared in
`domain/src/test`) and `MutableTimeSource` (in `application/src/test`) on the classpath of the
downstream tests that use them.

The wire types in `ua.kostenko.battleship.app.web.dto` are **generated** from `contracts/openapi.yaml`
into `app/target/generated-sources/openapi` at `generate-sources` (research.md D1, D28). They carry
Jackson annotations, so they can live in neither `domain` (Constitution III) nor `application` (the
module table above). That is why `application/projection` produces a framework-free `SnapshotView` and
`app/web` copies it into the generated DTO — § *Projection* and § *Adapters*. Generated sources are
build output: not committed, not hand-edited (Constitution VI), and excluded from Spotless.

The rules of the game — R03, R05–R10, R12, R14, R20, R21, R26 — and the statistics boundaries of R51
are constraints on `domain/rules` and `domain/model`; their exact shapes are
[data-model.md](data-model.md), not restated here.

## Stack

Java 25 · Maven 3.9.16 via Wrapper (generated once with the global Maven — wrapper plugin 3.3.4,
script-only distribution — then only `backend/mvnw`) · `spring-boot-starter-parent` 4.1.1 ·
`spring-boot-starter-{web,validation,security,actuator}` · `openapi-generator-maven-plugin` 7.25.0
(`app` only, bound to `generate-sources`; configuration and its reasons are research.md D28) ·
Enforcer 3.6.3 · Spotless 3.9.0 with palantir-java-format · Surefire/Failsafe 3.5.6 (parent-managed) ·
ArchUnit (test scope) · `spring-boot-starter-test` (JUnit 5 + AssertJ). All versions exact, no ranges.

Spring Boot 4.1.1 is the newest GA at the time of planning and is the constitution's baseline as of
v1.2.1; Java stays 25 LTS at patch 25.0.4. 4.2.0 is deliberately not targeted — only `4.2.0-M1` exists
on Maven Central.

**Deliberately not added.** JaCoCo and any coverage gate — excluded as a criterion by Constitution IV
and spec § *Proof required*. Checkstyle, PMD, Error Prone, NullAway — a large analyzer stack is
rejected by `00-decisions-and-evidence.md`, and Error Prone is unvalidated on JDK 25. Caffeine — the
one scheduled sweeper already prunes the two bounded maps. Testcontainers, WireMock, PIT, Jazzer,
jqwik — no database, no external HTTP, and generative evidence is not a completion criterion.
Mockito — tests use real collaborators plus a mutable `TimeSource` and a seeded `RandomSource`, which is
what Constitution IV asks for (research.md D23); that preference is the whole justification and stands
on its own. `Previous_Mistakes/ARCHITECTURE-SMELLS-REPORT.md` records a Byte Buddy self-attach failure,
but it was observed on a different toolchain and is not re-verified here, so it carries no weight in
this decision.
`Standards_and_recommendations/` pins JDK 21, Spring Boot 3.x and Gradle; the constitution's baseline
governs (research.md D24).

## Runtime design

### Registry and slots

`GameRegistry` holds `ConcurrentHashMap<GameId, GameSlot>` plus two `Semaphore`s sized from
`max-concurrent-games` and `max-concurrent-streams`. A permit is acquired **before** insertion and
released exactly once on removal; `map.size()` is never admission control. Over a ceiling the answer
is `503 service-unavailable` with `Retry-After`; nothing running is evicted and readiness stays true
(R39, R04, S11).

`GameSlot`'s fields are [data-model.md](data-model.md) § *Registry state*. Its lock is a
`ReentrantLock`, never `synchronized`, which pins virtual threads; it holds at most one subscriber per
seat.

`SessionRegistry` maps a SHA-256 digest to an anonymous session record. Only the digest is stored, so
a copy of server memory yields no usable session (R32). Bounded by the sweeper and a hard cap with
drop-least-recently-used, as are the rate-limit buckets (R41).

### Adapters (R01, R02)

| Operation | Controller | Use case |
|---|---|---|
| `getMeta` | `MetaController` | — reads `BattleshipProperties` + `TimeSource` |
| `listRulesets` | `RulesetController` | — reads the `domain/rules` constants |
| `getHealth` | `HealthController` | — reads the availability probe |
| `createGame` | `GameController` | `CreateGameUseCase` |
| `getGame` | `GameController` | `GetGameUseCase` |
| `joinGame` | `JoinController` | `JoinGameUseCase` |
| `sendCommand` | `CommandController` | `CommandUseCase` |
| `streamGameEvents` | `EventsController` | `SubscribeUseCase` |
| `replaceInvitation` | `InvitationController` | `ReplaceInvitationUseCase` |
| `sendPresence` | `PresenceController` | `PresenceUseCase` |
| `leaveGame` | `LeaveController` | `LeaveGameUseCase` |

`joinGame` answers `200` for a browser that already holds the guest seat, which is the membership-first
order R34 requires. `Meta.serverTime` and every snapshot's `serverTime` come from `TimeSource.now()`.
`DisplayNameNormalizer` in `domain/model` implements R64. `Meta.apiVersion` is the constant
**`1.0.0`**, equal to `contracts/openapi.yaml` `info.version`: it is not a configuration key and is
never parsed from the YAML at runtime, but bumped by hand when the contract's `info.version` is.

Every controller that answers with a game returns the generated `GameSnapshot` DTO, assembled from the
projector's `SnapshotView` by `SnapshotDtoAssembler` in `app/web` (§ *Projection*). Inbound bodies —
`CreateGameRequest`, `JoinGameRequest`, `CommandRequest` and the four `Command` variants — are the
generated request types too, so Bean Validation and Jackson bind straight onto them.

### Command path (R25)

A command runs: filters (session, CSRF, site isolation, rate limit) → `CommandUseCase` → the slot lock.
Under the lock, in one indivisible step: expiry check, membership, `commandId` seen?, the pure
transition, the state replacement, the version bump and the record of which seats' views changed.
No projection, JSON write, log sink or SSE write happens while the lock is held; the caller's snapshot
and the per-seat deliveries are produced after it is released. Different games never contend. A
refused action leaves state and version untouched (R15).

**Idempotency** is the `Set<UUID>` alone. R24 returns the *current* state on a repeat, so no result is
stored. It is bounded by construction: 60 actions/min × the 2 h ceiling.

### Projection

`SnapshotProjector.project(GameState, Seat, SnapshotContext)` in `application/projection` is the only
thing that decides what one player may see, for HTTP and SSE alike (R17). It returns a **`SnapshotView`**
— framework-free records (`SnapshotView`, `BoardView`, `PlayerView`, `ShotView`, `StatisticsView`,
shapes in [data-model.md](data-model.md)) — rather than the wire type, because `application` may contain
no framework type and the wire DTOs are generated with Jackson annotations (research.md D31).
`SnapshotContext` is an application record carrying what lives outside the aggregate — `serverTime`,
`expiresAt`, `invitationUrl?`, `invitationExpiresAt?` and each seat's `connected` flag — which
`app/registry` builds from the slot; the projector never sees `GameSlot`, so the module direction holds.
`shipsRemaining` is derived here, like `Ship.cells` and `status`. It is the sole place that decides
disclosure: the opponent grid carries only `UNKNOWN`/`MISS`/`HIT`/`SUNK`/`REVEALED_WATER` and `ships` lists only sunk ships until `FINISHED`;
`ABANDONED` and expired reveal nothing (R19). `allowedActions` is computed here from the R13 table —
never derived by a client.

`SnapshotView` never leaves the server. `SnapshotDtoAssembler` in `app/web` copies it field for field
into the generated `GameSnapshot`, and that one document is what goes on the wire for HTTP and SSE
alike — so R17 holds: there is no second *representation* of a game, only one internal carrier and one
generated wire type. The assembler is a mechanical 1:1 copy and takes no decisions of its own; proof
area 10 owns its correctness. Because the DTOs are generated, a contract change regenerates them and breaks the
assembler's compilation — which is what retires the wire-drift risk that hand-written DTOs carried.

`Transition.viewChanged` carries the seats whose **view** changed in the sense R18 defines: the
snapshot excluding `serverTime` and `expiresAt`. An accepted command that changes no view (R23) still
moves the idle deadline but bumps no version and pushes to nobody.

### Realtime (R27–R31)

Spring MVC `SseEmitter` with `spring.threads.virtual.enabled=true`; a virtual thread per stream makes
a bounded async executor and drain tasks unnecessary. Registration captures the current snapshot under
the slot lock and sends it as the first `snapshot` event, so there is nothing to replay and
`Last-Event-ID` is ignored. Event `id` is the snapshot `version`. A snapshot reaches a player only
when that player's own view changed, so versions skip (R28). One emitter per (session, game): a newer
one closes the older with `closed`/`REPLACED`, which is not a disconnection and changes no `connected`
flag (R22). `closed`/`GAME_UNAVAILABLE` only when the game stops existing for that browser. Heartbeat
comment every `heartbeat-seconds` from one scheduler; `stream-max-lifetime-seconds` ends the stream
with no `closed` event; a subscriber keeps at most the latest unsent snapshot and is dropped if it
cannot be written.

The hub receives a `SnapshotView` from `SnapshotPublisher` and serializes it through the **same**
`SnapshotDtoAssembler` and the same Spring-configured `ObjectMapper` as the controllers. That shared
path — not a parallel writer — is why the `snapshot` event's `data:` is byte-identical to what
`getGame` returns for the same caller at the same version.

**Proving scheduled work without sleeping (R60).** The heartbeat scheduler and the expiry sweeper each
expose a package-visible `tick()` that reads the injected `TimeSource` and does the whole of the work.
`@Scheduled` drives `tick()` in production and nowhere else; every proof calls `tick()` directly after
advancing the `TimeSource`. No proof waits on a scheduler, and nothing in this feature depends on real
elapsed time.

### Request security (R32–R37, R64)

One `SecurityFilterChain`: stateless, `permitAll`, CORS never configured, and CSRF through a
`CookieCsrfTokenRepository` customised to the contract's cookie — name `XSRF-TOKEN`, `Path=/`,
`Secure`, `SameSite=Strict`, script-readable — paired with a plain `CsrfTokenRequestAttributeHandler`
whose `csrfRequestAttributeName` is `null`. The plain handler is required: Spring's default
`XorCsrfTokenRequestAttributeHandler` masks the token per response, so a client echoing the raw
cookie value in `X-XSRF-TOKEN`, which is exactly what the contract prescribes, would be refused.
`MetaController` resolves the `CsrfToken` eagerly so `GET /api/v1/meta` always emits the cookie (R35).
Three own filters run beside it:

| Filter | Does |
|---|---|
| `SessionCookieFilter` | reads `__Host-battleship_session` (`HttpOnly; Secure; SameSite=Strict; Path=/`, no `Domain`, 256-bit value), digests it with SHA-256 and resolves the session; unknown or malformed → `401 session-required` and clear the cookie. `createGame`/`joinGame` issue instead of rejecting (R32, R33). |
| `SiteIsolationFilter` | rejects unsafe requests whose `Origin` or `Sec-Fetch-Site`/`-Mode` show a cross-site browser source. Absent headers are admitted — a non-browser caller still needs the CSRF token (R36). |
| `RateLimitFilter` | fixed 60-second window per key per operation class; on refusal `429 rate-limit-exceeded` with `Retry-After` and `retryAfterSeconds` equal (R38, R61). |

A failed CSRF check and a failed site-isolation check both answer `403 request-security-rejected`;
Spring Security's `AccessDeniedHandler` is pointed at the same `Problem` writer as the advice, so
there is one problem-document shape (R35, R36, R49).

Jackson rejects unknown fields; Bean Validation failures and unknown fields map to
`422 validation-failed` with one `Violation{field: JSON Pointer, rule}` each (R37). Every response
carries `Cache-Control: no-store`. A `RequestSizeFilter` refuses a body over
`battleship.max-request-body-bytes` with `413 payload-too-large`, and a wrong body content type maps
to `415 unsupported-media-type`; `replaceInvitation`, `sendPresence` and `leaveGame` declare no
`consumes` and never reach the content-type check (R40).

**Join ordering (R34).** `JoinGameUseCase` runs one order under the slot lock: membership, then expiry,
then a constant-time compare of the invitation digest and its atomic consumption. Every refusal returns
the one answer `409 invitation-unavailable`.

**Per-browser cap (R62).** `CreateGameUseCase` and `JoinGameUseCase` refuse with
`503 service-unavailable` when `SessionRecord.liveGames` already holds
`battleship.max-live-games-per-browser` entries. A game id leaves that set the instant the game
reaches `FINISHED` or `ABANDONED` or the player leaves.

**Leaving (R16).** `LeaveGameUseCase` succeeds with no payload; `LeaveController` answers `204`.
Before play it replaces the state with `ABANDONED`
— or removes a `WAITING` game outright. During play it is refused `409 action-not-allowed`, because
`LEAVE` is never in `allowedActions` then. After the game is over it clears only that seat's session
digest from the slot, so that browser reads `404` from then on.

### Failures (R49, R50)

One `@RestControllerAdvice` maps every outcome to the **generated `Problem`**
(`application/problem+json`) using exactly the contract's code→status table. The contract's schema is
named `Problem` and D1 generates every component model, so there is no hand-written copy to drift
(research.md D19). A `CorrelationIdFilter`
puts a 16-hex id in MDC and into every problem body. No secret, cookie, board, request body, exception
message or stack trace ever reaches a problem document.

### Lifetimes (R42–R48, R63)

All deadlines are absolute `Instant`s compared **inclusively** — `deadline <= now` means expired.
Expiry is checked on every access to a slot before anything else, and a `@Scheduled` sweeper runs
every `sweep-interval-seconds` purely to reclaim memory, so a late request can never resurrect a game.
The sweeper's work lives in a package-visible `tick()` driven by the injected `TimeSource`; `@Scheduled`
calls it only in production, and proofs call it directly (§ *Realtime*, R60).
Only creation, the guest joining, an accepted action and an accepted presence signal move the idle
deadline; reads, stream opens, heartbeats, invitation replacement and refused actions never do.
`terminalRetentionDeadline` is set from the instant a game ended or expired, whichever happened. An
expired slot answers `410 game-expired` to its own two session digests and `404 game-unavailable` to
every other caller; after `terminalRetentionDeadline` its own players get `404` too (R63).

### Configuration and observability (R52–R59)

One validated `@ConfigurationProperties(prefix = "battleship")` record, `BattleshipProperties`, with
one nested `RateLimits` record. Every row of spec R52 is one key below; **defaults are R52's and are
not restated here**. An environment variable wins because Spring orders that property source above the
file, and relaxed binding maps the names (R53):

| Spec R52 row | Key |
|---|---|
| Idle lifetime | `idle-timeout-seconds` |
| Absolute game lifetime | `max-game-duration-seconds` |
| Result retention | `result-retention-seconds` |
| Invitation lifetime | `invitation-lifetime-seconds` |
| Presence interval | `presence-interval-seconds` |
| Event-stream heartbeat interval | `heartbeat-seconds` |
| Event-stream maximum lifetime | `stream-max-lifetime-seconds` |
| Maximum concurrent games | `max-concurrent-games` |
| Maximum concurrent event streams | `max-concurrent-streams` |
| Maximum live games per browser | `max-live-games-per-browser` |
| Maximum request body size | `max-request-body-bytes` |
| Random-arrangement attempt limit | `random-arrangement-attempts` |
| Expiry sweep interval | `sweep-interval-seconds` |
| Shutdown drain window | `shutdown-drain-seconds` |
| Public interface base URL | `public-base-url` |
| Rate limit — create game | `rate-limit.create-game-per-minute` |
| Rate limit — join | `rate-limit.join-per-minute` |
| Rate limit — actions | `rate-limit.commands-per-minute` |
| Rate limit — read a game | `rate-limit.read-game-per-minute` |
| Rate limit — presence | `rate-limit.presence-per-minute` |
| Rate limit — open an event stream | `rate-limit.stream-open-per-minute` |
| Rate limit — replace invitation | `rate-limit.replace-invitation-per-minute` |
| Rate limit — leave | `rate-limit.leave-per-minute` |

The first six top-level keys are exactly the contract's `Limits` object, which is why `getMeta` can
read them straight off this record — a limit exists once (R55). Constraint annotations plus a
`@PostConstruct` check on the public interface base URL (absolute, permitted scheme, no credentials,
query or fragment) fail the start-up and name the setting (R54, R56). Logging uses Spring Boot 4's
built-in structured output — no extra encoder dependency — carrying the correlation id and the
`gameId`, which the contract says grants nothing, and never a cookie, secret, fragment, board, body or
player name (R58). `GET /api/v1/health` is a hand-written controller backed by an
`ApplicationAvailability` probe; Actuator stays off the public surface. `server.shutdown=graceful` plus
the configured drain window, readiness flipped to `DRAINING` first and streams closed with no `closed`
event; once readiness is `DRAINING` the events endpoint answers reconnection attempts
`503 service-unavailable`, which is what stops the browser's automatic retry (R29, R59).

### Ports

`TimeSource.now()` (R60 — `Clock.systemUTC()` in production, mutable double in every test; no test
sleeps), `RandomSource` (fleet arrangement and the R11 tie-break, seedable), `SecretGenerator`
(`SecureRandom`: 128-bit base64url game ids matching `^[A-Za-z0-9_-]{22}$`, 256-bit session values,
256-bit invitation secrets matching `^[A-Za-z0-9_-]{43}$`), `GameSlotStore`
(`<T> T withSlot(GameId, Function<Slot,T>)`, `insert`, `remove` — implemented by
`app/registry.GameRegistry`, with `InMemoryGameSlotStore` as the second implementation),
`SnapshotPublisher` (`publish(GameId, Seat, SnapshotView)`, implemented by the SSE hub). Five ports,
each with a real second implementation in tests. Four of them are declared in `application/port`;
**`RandomSource` is declared in `domain`**, because `GameRules.apply(…, RandomSource)` takes it and
`domain` may not reference `application` — putting it in `application` inverts the module direction
and fails `ArchitectureTest`.

`GameSlotStore` is how an `application` use case takes the slot lock without naming an `app` type.
`GameSlot` lives in `app/registry` while `CommandUseCase` lives in `application/usecase`, and under
the module table above `application` may not reference `app`; without the port that call cannot
compile. It also keeps proof areas 4, 5 and 6 as `application` tests, run against the in-memory
double.

**`Slot`** is the port-facing view `withSlot` hands its function — the one shape an `application` use
case sees of a game's mutable state, implemented by `app/registry.GameSlot`. Its members are
[data-model.md](data-model.md) § *Registry state*, and they include
`SnapshotContext contextFor(Seat, Instant)`, which is how an `application` use case obtains the
context the projector needs without naming an `app` type (research.md D27 — the projector still never
sees `GameSlot`). A use case therefore captures the `GameState` **and** the `SnapshotContext` inside
`withSlot` — both immutable records, so neither costs a projection under the lock — and projects each
seat's snapshot after the lock is released (§ *Command path*).

`SnapshotPublisher` carries the framework-free `SnapshotView`, not a wire type, because it is declared
in `application`. The SSE hub in `app/realtime` runs that view through the **same**
`SnapshotDtoAssembler` and the same Spring `ObjectMapper` the controllers use, which is what makes the
SSE payload byte-identical to `getGame`'s body rather than merely similar (R17; § *Realtime*).

## Validation

**Every task delivers one behaviour and the proof of that behaviour in the same change.** There is no
test phase and no task whose only deliverable is a test. A task is not complete until its own proof
exists and fails when the behaviour is removed (AGENTS.md § *Scope control* rules 4 and 5;
Constitution IV, *"code with no caller and no test is not accepted as complete"*). This deliberately
departs from `.specify/templates/tasks-template.md:12`, which makes tests an optional separate
sub-phase.

The two tables below are therefore the evidence map — **where each proof ends up** — not a separate
slice of work: spec § *Proof required* and Constitution IV both require the ten areas to have named
owners. Each proof area is owned by one or more test classes. `*Test` runs under Surefire, `*IT` under Failsafe.
No test sleeps: time moves only by advancing the injected `TimeSource` (R60).

| # | Area | Owner | Module |
|---|---|---|---|
| 1 | Ruleset correctness | `RulesetRulesTest` | `domain` |
| 2 | Action legality and state stability | `AllowedActionsTest`, `RefusedCommandStabilityTest` | `domain` |
| 3 | Player privacy | `ProjectionPrivacyTest` | `application` |
| 4 | Repeat safety | `CommandIdempotencyTest` | `application` |
| 5 | Concurrency | `GameSerializationTest` | `application` |
| 6 | Lifetimes | `LifetimeBoundaryTest`, `StatisticsTest` | `application` |
| 7 | Request security and authorization | `RequestSecurityIT`, `AuthorizationIT`, `RateLimitIT` | `app` |
| 8 | Live updates | `EventStreamIT` | `app` |
| 9 | Packaging, configuration and redaction | `ConfigurationValidationIT`, `PublishedLimitsIT`, `LogRedactionIT`, `PackagedArtifactIT`, `GracefulShutdownIT` | `app` |
| 10 | Wire conformance | `WireConformanceIT` | `app` |
| — | Capacity and per-browser ceilings | `CapacityIT` | `app` |
| — | A whole game over the wire, and reload recovery | `GameJourneyIT` | `app` |
| — | Module dependency direction | `ArchitectureTest` | `app` |

How three of them are staged:

- Area 3 builds two states differing only in the opponent's undiscovered ships and asserts the
  opponent-facing snapshots are byte-identical.
- Area 5 stages simultaneous commands with a `CyclicBarrier` and asserts the set of allowed outcomes,
  not one scheduling.
- Area 6 proves each deadline at its inclusive boundary: at `deadline - 1ms` the game is alive, at
  `deadline` it has expired.
- Area 10 drives every operation's success and failure path and asserts each response body field by
  field against the contract's examples; it reuses the fixtures of `GameJourneyIT` and the security
  ITs rather than duplicating the journeys. It is also what proves `SnapshotDtoAssembler` copies every
  field of a `SnapshotView` into the generated DTO — generation fixes the *shape* of the wire types,
  not that the assembler fills them all in.

The last three rows are not proof areas. `CapacityIT` and `GameJourneyIT` exist because success
criteria S1, S3 and S11 have no other owner; `GameJourneyIT` is a server-level HTTP journey, not a
browser one — browser journeys belong to `004-integration`. `ArchitectureTest` is a build guard for
the module boundaries, not acceptance evidence.

| Success criterion | Owner |
|---|---|
| S1 complete game under each ruleset | `RulesetRulesTest` (rules) + `GameJourneyIT` (over the wire) |
| S2 no placement leak | `ProjectionPrivacyTest` |
| S3 one request restores everything after reload | `GameJourneyIT` |
| S4 an action sent twice applies once | `CommandIdempotencyTest` |
| S5 idle, absolute and retention deadlines | `LifetimeBoundaryTest` |
| S6 non-player cannot distinguish a game from one that never existed | `AuthorizationIT` |
| S7 simultaneous actions linearize | `GameSerializationTest` |
| S8 reconnect is current from the first message | `EventStreamIT` |
| S9 nothing sensitive in logs or problems | `LogRedactionIT` |
| S10 packaged artifact starts and serves no UI | `PackagedArtifactIT` |
| S11 ceilings refuse with a retry hint, evict nothing, stay ready | `CapacityIT` |
| S12 every limit changeable by configuration, enforced and published | `PublishedLimitsIT` |

## Gate

None of these exist yet; they are created by this feature.

| Command (from `backend/`) | Proves |
|---|---|
| `./mvnw -q verify` | DTO generation from `contracts/openapi.yaml`, compile, Enforcer, Spotless check, Surefire units, ArchUnit, Failsafe integration tests |
| `./mvnw spotless:apply` | formatting fix-up (`spotless:check` runs inside `verify`) |
| `./mvnw -pl app -am spring-boot:run` | local run with default configuration |
| `java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` | packaged artifact starts and reports ready (R57, S10) |

From `contracts/`: `npm ci && npm run check` stays the contract's own gate.

## Forecast

38 tasks across 7 phases, each task delivering one behaviour and its own proof (§ *Validation*):

| Phase | Tasks | Proof areas the phase's tasks carry |
|---|---|---|
| 1 — Reactor, toolchain and the generator spike | 6 | — (incl. Task 1, the generator spike below) |
| 2 — Domain: rules, rulesets and the transition | 7 | 1, 2 |
| 3 — Application: ports, projection, registry, use cases | 8 | 3, 4, 5, 6 |
| 4 — Web adapters, problems, session and CSRF | 7 | 7 (the session and anti-forgery half), groundwork for 10 |
| 5 — Realtime | 3 | 8 |
| 6 — Abuse limits, hardening and operation | 5 | 7 (the remaining half), 9 (configuration, redaction, shutdown) |
| 7 — Packaging and wire conformance | 2 | 9 (packaging and published limits), 10 |
| **Total** | **38** | |

≈95 files under `backend/` (≈60 main, ≈25 test, 10 build) — unchanged by the count: the 32→38 split
re-cuts the same work into units that each fit one session, it does not add files. Generated sources
are build output: not counted, not committed, not formatted by Spotless.

This is above the 20-task line of AGENTS.md § *Scope control* rule 3, so it needed re-approval — the
older approval was for 34 tasks under a hand-written-DTO design and did not carry over. The owner
approved **38** on 2026-09-21; tasks.md § *Decisions this task list settles* records that as
Decision A2.

### Task 1 — the generator spike, with an abort condition

`tasks.md` starts by generating the models from the amended contract and compiling them, before
anything depends on them. It is delivered as tasks.md **T002**, not T001, only because a Maven plugin
cannot run without the pom T001 creates; nothing between the two depends on generated types. "Task 1"
here and `T002` there are two numbering schemes for the same first real step.

It passes only if the 39 component schemas emit as the manifest in T002 states — **29 object schemas
as classes and 7 enum types**, with `GameId`, `DisplayName` and `Instant` inlining as `String` /
`OffsetDateTime` rather than minting classes — or the gaps are named and hand-written; the four
`Command` variants deserialize by their `type` tag; `ProblemCode`'s kebab-case values round-trip; and
the plugin runs clean on JDK 25. The open questions it settles are listed in research.md D28.

**If it fails**, the fallback is models-only for the schemas that do generate plus hand-written records for
the rest, recorded as a decision — not a silent retreat to the rejected alternative of research.md D1.
Nothing later in this plan assumes the spike succeeded.

## Risks

- **Wire drift is retired,** not mitigated: the DTOs are generated from `contracts/openapi.yaml`, so a
  contract change that the assembler does not follow fails the build (§ *Projection*). What remains is
  completeness, which proof area 10 owns.
- **The generator on this toolchain is unproven.** `openapi-generator-maven-plugin` 7.25.0 has not been
  run here on JDK 25 against this contract, and `~/.m2` does not exist on this machine, so nothing about
  it can be verified before the build exists. That is exactly why it is Task 1 with a named abort
  condition and a recorded fallback (§ *Forecast*), rather than an assumption the rest of the plan rests
  on.
- **Java 25 + Spring Boot 4.1.1** is ahead of the repository's recorded standards and of the previous
  application's verified toolchain. Surefire/Failsafe behaviour on JDK 25 is proven by the first
  passing `verify`, not assumed.

## Complexity Tracking

Nothing to justify; § *Forecast* carries the substance of the scope decision.
