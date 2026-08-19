# Copy-paste input: backend plan

Read `specs/002-backend/spec.md` and the published `001-api-contract` bundle first. Plan the backend implementation only. It must conform to that contract as written and must not create frontend code, modify contract semantics, or introduce persistence.

## Module and dependency design

Build a Maven reactor rooted at `backend/` with exactly these production modules:

```text
backend/
  pom.xml
  mvnw
  mvnw.cmd
  .mvn/wrapper/
  domain/
    pom.xml
    src/main/java/ua/kostenko/battleship/domain/
      model/                    immutable game values
      rules/                    the two ruleset policies and invariants
      command/                  domain command values
      transition/               pure transition results and events
    src/test/java/ua/kostenko/battleship/domain/
  application/
    pom.xml
    src/main/java/ua/kostenko/battleship/application/
      usecase/                  create, join, command, leave, presence, query
      projection/               exclusive player-safe view construction
      port/                     genuine time, security, registry and publish ports
      result/                   transport-neutral use-case outcomes
    src/test/java/ua/kostenko/battleship/application/
  app/
    pom.xml
    src/main/java/ua/kostenko/battleship/app/
      web/                      REST, RFC 9457 and request/response mapping
      security/                 cookies, CSRF, Origin, Fetch Metadata, limits
      registry/                 bounded in-memory slots and lifecycle
      realtime/                 SSE registration, replay and delivery
      config/                   validated runtime properties and composition
      observability/            redacted logs, health and protected metrics
      BattleshipApplication.java
    src/main/resources/
      application.yaml
      application-local-integration.yaml
    src/test/java/ua/kostenko/battleship/app/
```

Pin Java 25 LTS and the Maven 3.9.16 Wrapper. After the reactor root POM exists, use the Foundation-verified global Maven exactly once to invoke Maven Wrapper Plugin 3.3.4 with Maven 3.9.16 and the script-only distribution; review the generated wrapper properties and scripts, then use only `backend/mvnw` for backend and root workflows. Use `spring-boot-starter-parent` 4.1.0 at the reactor root and keep Spring MVC, Spring Security, Validation, Actuator, Jackson, SLF4J, and Logback in `app` only. Keep its managed Maven Enforcer 3.6.3 and Surefire/Failsafe 3.5.6; upstream 3.6 test plugins remain milestone releases. Pin Spotless Maven Plugin 3.9.0 as the sole Java formatter/check and enforce the runtime/module dependency directions with Maven. Do not add JaCoCo unless a later evidence need uses its report; never add a coverage quota. Prefer Boot-managed compatible versions instead of overriding individual framework components without evidence.

`domain` has no Spring, servlet, Jackson, persistence, cookie, logging, clock, random, or transport dependency. `application` depends on `domain` and defines use cases, projections, and only genuine ports for time, secure capabilities, IDs, session authority, publishing, and observability. `app` depends on both lower modules and contains all Spring Boot, HTTP, SSE, security, in-memory registry, scheduler, rate limiting, configuration, logging, health, and composition code. Enforce these directions with build-visible architecture/dependency tests. Package only `app` as the one executable JAR; do not copy frontend assets into its resources.

Keep packages purpose-oriented within each module: domain values/rules/commands/transitions/events/invariants; application use-cases/projections/ports/results; app adapters/security/registry/realtime/config/observability/boot. An adapter may map contract DTOs to application requests but may not serialize a domain aggregate or decide rules. The projection service is the exclusive source for HTTP and SSE views.

## State and flow design

Represent the domain aggregate as deeply immutable values containing the public locator, ruleset ID, phase, prepared fleets/boards, accepted shots/outcomes, readiness, turn, terminal outcome, `gameVersion`, stored first-turn choice, and the minimal accepted timing facts needed by the contract's statistics. Define explicit policies per published ruleset for geometry, fleet, contact, legal placement, one-shot allocation, turn behavior, repeat-shot rejection, win, and final disclosure. Keep automatic sea-battle moat marking as a distinct public result value rather than overloading a shot marker. The domain receives monotonic elapsed values through application input and never reads a clock itself.

Use an application transition flow for each command:

```text
authenticate capability and derive seat
→ find/lock the session slot
→ recheck slot identity, draining state, and expiry
→ normalize/fingerprint command
→ deduplicate commandId before version evaluation
→ validate expectedVersion and If-Match agreement
→ authorize and apply pure transition
→ replace immutable state and increment gameVersion once
→ record bounded player-safe result and replay snapshot
→ enqueue newest player-safe snapshots
→ unlock
→ perform HTTP completion/SSE I/O outside the lock
```

Creation uses an injected secure source to select the first seat and mint high-entropy owner/invitation values; the pure domain receives values, never produces them. A new game starts at `gameVersion: 0`. Join and rotation execute under the same slot serialization: join turns one unused invitation into one guest membership; rotation is owner-only while the guest seat is vacant and replaces/revokes the digest atomically. Join, invitation-status rotation, pre-play abandonment, and accepted game commands each advance the shared player-safe version exactly once and enqueue projections. Presence, reads, failures, and terminal member revocation do not. If a host loses the only returned invitation URL, it must explicitly rotate before sharing; an unknown rotation response is reconciled and followed only by an explicit replacement, never a blind replay. Before play, `leave` uses the versioned leave envelope, transitions the game to abandoned, revokes invitation/gameplay authority, publishes the safe snapshot, keeps both former memberships read-only, and discloses no placement. During play, the leave endpoint returns `resign-required`; the versioned `RESIGN` command creates the terminal winner result and permits the same former-member full-fleet disclosure as a normal finish. A terminal leave revokes only the caller's read membership and closes its stream; at most two receipts per seat retain the pre-play and terminal leave results until game removal. Retain terminal player-safe views for the configured retention period only.

## Registry, lifecycle, and bounded resources

Implement a deliberately explicit `SessionRegistry` rather than using cache eviction as the lifecycle authority. A slot owns a short per-game lock, current immutable state, capability digests, invitation digest/state, bounded per-seat command result ring, bounded replay ring, at-most-one subscriber per seat, monotonic activity/creation/terminal deadlines, and closing state. Use a global permit mechanism for game admission and a distinct bounded stream permit; acquire before insertion/allocation and release once during idempotent removal. At the starting configuration, 100 games and 200 streams are externalized, measured before raising, and rejected with the contract problem rather than evicting live sessions.

Use an injected monotonic time source for elapsed decisions and a wall clock only for human-facing timestamps. Check expiry before any activity touch and on every applicable access, then run a periodic idempotent sweeper as reclamation. Only accepted commands or accepted visible-tab presence update the idle deadline. Presence is an authenticated CSRF-protected operation, limited per seat to one extension every five minutes, and does not change the aggregate/version/event stream. An earlier well-formed call returns the current expiry with `extended: false` and does not touch the deadline; abusive traffic returns the bounded 429 problem. Reads, polling, stream open/reconnect, heartbeat, failed command, invalid invitation, and rejected presence never extend life. Enforce 15-minute idle, two-hour absolute, five-minute terminal, and invite deadlines exactly at their boundary; include the chosen inclusive/exclusive comparison in one shared lifecycle policy and test it.

Bound every attacker-controlled structure: command dedupe, replay, per-seat coalescing queue, rate-limit buckets/keys, request body, stream count, executor queue, and cleanup work. A new SSE subscription replaces an existing subscription for that seat; it captures and queues the current player-safe snapshot atomically with registration, has a 20-minute lifetime, sends a 15-second comment heartbeat, and closes slow/erroring subscribers. Replays are an optimization; the latest full snapshot is always the convergence fallback. No blocking emit, JSON write, log sink, or external operation runs while the session lock is held.

## Adapter, security, and operations design

Implement REST/SSE adapters solely from the pinned OpenAPI contract. Map Bean Validation/parsing failures and application outcomes to RFC 9457 problems consistently. Implement snapshot ETag/conditional GET, command `If-Match` checks, no-store headers, `Last-Event-ID`, event IDs equal to `gameVersion`, and `serverEpoch` consistently in metadata, snapshots, events, and relevant problems.

Use Spring Security in `app` for the exact host-only `__Host-battleship_session` cookie with `Path=/`, Secure, HttpOnly, and SameSite=Strict. Use the distinct host-only, `Path=/`, Secure, SameSite=Strict `BATTLESHIP-XSRF-TOKEN` cookie without HttpOnly and require its value in `X-Battleship-CSRF` for unsafe requests. `GET /api/v1/meta` establishes or refreshes that CSRF token without creating membership. Require the configured frontend and API origins to use the same hostname; local ports may differ. Require CSRF validation and the exact configured frontend Origin for unsafe endpoints. Add one small Fetch Metadata filter that rejects browser requests identified as cross-site or with an incompatible unsafe mode/destination; an absent metadata header is allowed only for non-browser clients that still pass the CSRF and Origin checks. Configure credentialed CORS for configured trusted origins only, never wildcard credentials. Use a cryptographic generator for capabilities/invites, retain only digests, perform constant-time comparisons where applicable, clear/revoke on lifecycle events, and never accept capability data in a URL, query, SSE URL, or browser-facing snapshot. Add cache, referrer, content-type, frame, and transport-security headers appropriate to the deployment profile, with production HSTS enabled only once HTTPS is configured.

Configure validated public frontend and API origins with the same hostname, plus the frontend's normalized application subpath, when constructing creation/rotation invitation URLs. Reject credentials, query, fragment, unsafe production schemes, and mismatched hostnames. Add contract and subpath examples so adapters never infer the base from request headers. Also configure a process-unique `serverEpoch`, bounded asynchronous/SSE execution, scheduled cleanup, graceful draining, public liveness/readiness with component details hidden, and protected metrics/config surfaces. During shutdown, mark non-ready and stop admission before closing streams and allowing a bounded in-flight command period; do not fake persistence. Use structured logs with correlation ID, safely hashed game correlation, action/outcome/latency/version, and redaction tests that reject cookies, capabilities, invitation fragments, full URLs, full boards, and unnecessary names. Keep metrics low-cardinality: counts/latency/capacity/expiry/dedupe/stream/rate-limit outcomes, JVM/executor health, and epoch; never label metrics by game ID, credential, raw IP, coordinate, or unbounded name.

## Test and local-operation plan

Start with pure domain test fixtures and test-first implementation. Use explicit board fixtures for examples, property/stateful tests for invariants, and injected deterministic IDs/clocks/non-security random sources; retain failing property seeds. Test every ruleset policy independently and assert input immutability, deterministic transitions, legal/illegal command stability, turn/final disclosure, and hidden-information projections.

Then add application tests using the real bounded in-memory registry and controlled clock/permits, covering create/join races, one-time invites, rotation, command serialization, duplicate-before-precondition behavior, unrelated-game concurrency, exact expiry boundaries, permit release, cleanup idempotence, presence limitation, terminal retention, and restart epoch behavior. Add adapter integration tests for OpenAPI conformance, cookies/CSRF/Origin/CORS, status/problems, ETags, rate limits, redaction, SSE subscribe-mutate/reconnect/replay miss/replacement/slow consumer, and graceful shutdown. Use two isolated browser contexts only when proving end-to-end cookie privacy is materially needed; the test pyramid remains domain-heavy.

Plan local operations around `backend/mvnw`: module compile/test commands, app packaging/run command, contract-conformance command using the pinned contract path, and a backend-only smoke command that starts the packaged JAR and checks public readiness. Keep the normal idle duration fixed at 15 minutes. Provide one explicit, opt-in `local-integration` profile that may shorten only that duration for a packaged-JAR expiry journey; it uses the same lifecycle path, has no HTTP clock/test control, and never activates by default. Exact boundary tests still use the injected monotonic clock below the adapter. Add a constrained-memory/load-soak profile that measures retained heap at the configured game/stream ceiling before any capacity increase. Do not use a fake database tool, containerized database, sleep-based timing, mechanical coverage threshold, or style quota as a substitute for these proofs.

## Expected task sequence and outputs

Order tasks so lower-layer decisions are complete before adapters: reactor and dependency guards; immutable domain/ruleset/projection model; deterministic domain tests; application requests/results/use cases; bounded registry/lifecycle/idempotency; capability/invitation/rate-limit services; REST/Problem Details/ETag adapters; SSE/security/configuration/health/logging; integration/race/privacy/lifecycle verification; backend-only packaging and local operational evidence. Identify shared canonical files and serial dependencies before marking tasks parallel.

The outputs are one backend-only executable JAR, a reproducible Maven test/package workflow, configuration with safe defaults and documented overrides, conformance evidence against the pinned contract, and focused operational evidence for bounded ephemeral behavior. The plan must leave contract artifacts owned by `001-api-contract` and frontend work to its own feature.
