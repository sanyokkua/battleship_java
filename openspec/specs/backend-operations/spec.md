# backend-operations Specification

## Purpose
Running the backend: external validated configuration, published limits, public base URL, the packaged backend-only artifact, redacted structured logging, graceful shutdown and injected time. Source: spec 002-backend R52-R60. Covers User Story US7 (operator), success criteria S9, S10 and S12, proof area 9 (packaging, configuration and redaction) and the Out of scope boundaries.

## Requirements

### Requirement: Every limit is external configuration with defaults (R52)

Every numeric limit SHALL be external application configuration with a documented default, never a compile-time constant.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Defaults: idle lifetime 900 s; absolute game lifetime 7200 s; result retention 300 s; invitation lifetime 900 s; presence interval 300 s; event-stream heartbeat 15 s; event-stream maximum lifetime 1200 s; rate limit create game 5/min per caller address; join 20/min per caller address; actions 60/min per session; read a game 120/min `[agent]`; presence 30/min `[agent]`; open an event stream 30/min `[agent]`; replace invitation 10/min `[agent]`; leave 10/min `[agent]`; maximum concurrent games 100; maximum concurrent event streams 200; maximum live games per browser 1 `[agent]`; maximum request body 16 KiB `[agent]`; random-arrangement attempt limit 1000 `[agent]`; expiry sweep interval 30 s `[agent]`; shutdown drain window 5 s `[agent]`; public interface base URL `http://localhost:5173`. The keys live under prefix `battleship` (23 keys).

#### Scenario: Defaults bound

- **WHEN** the service starts with no overrides
- **THEN** every key binds to its default

### Requirement: Environment beats file (R53)

Each setting SHALL be overridable both by environment variable and by the configuration file, with the environment variable taking precedence.

#### Scenario: Override

- **WHEN** `BATTLESHIP_IDLETIMEOUTSECONDS=60` is set while the file says 900
- **THEN** 60 is used

### Requirement: Start-up validation (R54)

Every setting SHALL be validated when the service starts. An absent, unparseable, negative, zero or otherwise nonsensical value SHALL stop start-up and name the offending setting; it never surfaces later as a runtime failure.

#### Scenario: Zero value

- **WHEN** `BATTLESHIP_IDLETIMEOUTSECONDS=0`
- **THEN** start-up fails naming `idle-timeout-seconds`

### Requirement: Published limits equal enforced limits (R55)

The limits the service publishes SHALL be read from the running configuration with no second copy of a limit anywhere, so changing a setting changes both enforced behaviour and published value. Success criterion S12: every limit in R52 can be changed by configuration alone, with the running service both enforcing and publishing the changed value.

#### Scenario: Changed idle limit

- **WHEN** idle lifetime is configured to 60 s
- **THEN** `getMeta` shows 60 and an idle game expires after 60 s

### Requirement: Public base URL is the sole invitation source (R56)

The public interface base URL SHALL be validated at start-up (absolute, permitted scheme `http`/`https`, no credentials, no query, no fragment) and SHALL be the sole source of the invitation link the host shares. The service SHALL never infer it from a request header.

#### Scenario: Invalid URL

- **WHEN** the base URL is relative, `ftp://`, carries credentials, a query or a fragment
- **THEN** start-up fails naming the setting

#### Scenario: Forged Host header

- **WHEN** a request sends a different `Host`/`X-Forwarded-Host`/`Origin`
- **THEN** the invitation URL is unchanged

### Requirement: One executable backend-only artifact (R57)

The feature SHALL deliver one executable, backend-only artifact that starts locally with default configuration and reports itself ready, bundling and serving no user-interface assets. Success criterion S10: the packaged artifact starts from default configuration on a developer machine and reports ready, serving no UI assets.

#### Scenario: Packaged start

- **WHEN** `java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` runs
- **THEN** health reports ready and `GET /` is 404

### Requirement: Structured redacted logs (R58)

Logs SHALL be structured, carry a correlation identifier and enough context to diagnose a failure, and SHALL never carry a session cookie, an invitation secret, a link fragment, a full secret-bearing URL, board content, a raw request body or an unnecessary player name. Diagnostic surfaces SHALL expose nothing sensitive and no game content; the caller address is never logged.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Success criterion S9: no log record, problem document or diagnostic surface contains a cookie, invitation secret, link fragment, board content or raw request body.

#### Scenario: Journey logs

- **WHEN** a full journey triggering every failure class runs
- **THEN** captured logs contain none of the forbidden content and every record has a correlation id

### Requirement: Graceful shutdown (R59)

On shutdown the service SHALL stop admitting work, report itself not ready (`DRAINING`) first, close event streams without a reason message and answer their reconnection attempts as unavailable, allow in-flight actions the configured drain window to finish, and exit without claiming that any game survives (US7-1).

#### Scenario: Shutdown order

- **WHEN** a shutdown is requested
- **THEN** readiness flips first, streams end with no `closed` event, reconnects get `503`, and in-flight actions finish within the drain window

### Requirement: Time is injected (R60)

All time-dependent behaviour SHALL be driven by an injected time source. Idle expiry, the absolute ceiling, result retention, invitation expiry, the presence throttle, the event-stream lifetime and every statistics duration SHALL be proven by advancing that source. No proof SHALL depend on real elapsed time or on sleeping, and the whole suite SHALL run in milliseconds.

#### Scenario: No sleeping

- **WHEN** any lifetime proof runs
- **THEN** it advances the mutable time source and calls `tick()` directly

### Requirement: Proof area 9 and out-of-scope boundaries

Proof area 9 (owners `ConfigurationValidationIT`, `PublishedLimitsIT`, `LogRedactionIT`, `PackagedArtifactIT`, `GracefulShutdownIT`) SHALL prove that the packaged artifact starts and reports ready, an invalid setting stops start-up, a changed setting is both enforced and published, shutdown drains and reports not-ready, and logs contain none of the forbidden content.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** Out of scope: a database, durable store, cache or broker as authority, accounts, chat, spectators, matchmaking, bots, rankings, history, payments, analytics, ads, offline play, command queues, horizontal scaling, CI workflows, deployment manifests; any UI; and the deferred contract capabilities (claiming a win against a vanished opponent, rematch, recovering a lost-answer join, conditional reads, salvo rules). Not completion criteria: a coverage percentage, test count, property testing, mutation-score evidence, a soak run or a short-idle timing profile.

#### Scenario: Excluded evidence

- **WHEN** acceptance evidence is assembled
- **THEN** it consists of the ten proofs and not of coverage or test counts
