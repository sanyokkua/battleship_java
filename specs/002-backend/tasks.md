# Tasks: Battleship Backend

**Feature**: `002-backend` · **Branch**: `feature/002-backend` · **Date**: 2026-09-21
**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)
**Data model**: [data-model.md](data-model.md) · **Run guide**: [quickstart.md](quickstart.md)
**Contract (authority)**: [`contracts/openapi.yaml`](../../contracts/openapi.yaml)
**Governance**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) v1.2.1 · [`AGENTS.md`](../../AGENTS.md)

**Assurance level**: Standard (plan.md § *Assurance level*).
**Status**: not started. `backend/` does not exist at this HEAD.
**Contract amendments**: the three changes spec.md § *Out of scope* authorises are **already applied
in this worktree and uncommitted** — the `Phase.PLAYING` description (R11's ready-order rule), the
`GameSnapshot.version` description, and five lines of 3.1-only syntax normalized for the generator
(`const` → single-value `enum`, `examples: [x]` → `example: x`; research.md D1). `contracts/` must be
green on its own gate — `cd contracts && npm ci && npm run check` reporting
`Your API description is valid` — **before T002**, which reads the amended file.

## How to run these tasks

One task per session. Each block is self-contained: it names what to build, the requirements it
covers, the documents and contract lines to read first, the files it touches, the proof it ships in
the same change, the command that runs that proof, and the mutation that must make the proof fail.

```text
- [ ] Tnnn [P?] [US?] <action> in <exact path>
  - **Delivers**   one behaviour, stated concretely
  - **Covers**     R-ids · S-ids · proof area · data-model / research section
  - **Read first** the minimum authority for this task
  - **Files**      created / modified
  - **Proof**      test class and its enumerated assertions
  - **Verify**     exact command from backend/
  - **Mutation**   the named removal that must make the proof fail (AGENTS.md rule 5)
  - **Depends on** earlier task ids only
```

- `[P]` — may be done in parallel with the other `[P]` tasks of the same phase (different files, no
  shared dependency). Everything else is strictly sequential.
- `[US1]`…`[US7]` — the user story of [spec.md](spec.md) § *User Scenarios & Testing* the task serves.
- **Every task ships its own proof.** There is no test phase and no task whose only deliverable is a
  test (plan.md § *Validation*; Constitution IV, *"code with no caller and no test is not accepted as
  complete"*). This deliberately departs from `.specify/templates/tasks-template.md:12`, which makes
  tests an optional separate sub-phase.
- **Done means a named mutation fails the gate** (AGENTS.md § *Scope control* rule 5). Record the
  command output showing the assertion failing when the guarded behaviour is removed. A green test
  that cannot fail is not evidence, and a checked box is not evidence.
- **Every `-pl` command carries `-am`.** `domain` and `application` are `1.0.0-SNAPSHOT`, the gate is
  `verify` and never `install`, and `~/.m2` does not exist on this machine, so a single-module build
  has nothing to resolve its siblings from. `-pl domain` needs no `-am` — it has no upstream sibling.
- **A focused `-Dtest=` / `-Dit.test=` run across a multi-module reactor also needs**
  `-Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`. With `-am` the
  reactor builds the upstream modules too, and those contain no test matching the filter, which would
  otherwise fail the run. The **Verify** lines below state the `-pl … -am` part; append these two flags
  whenever a filtered run reports "No tests were executed" in an upstream module.
- Phases are the architectural layers of plan.md § *Forecast*, not one-phase-per-user-story. A layer
  order is the only order in which no task depends on a later one: US1 cannot be delivered over the
  wire before the domain aggregate, the projector and the session filter exist. Story traceability is
  carried by the `[US#]` labels instead.

## Decisions this task list settles

Three points where `plan.md` / `research.md` were silent or self-contradictory. Owner-approved
2026-09-21. **All three amendments are applied**: `plan.md` and `research.md` at this HEAD already
state the decision, so no task below carries the amendment as work (Constitution VI — the owning
artifact is updated in the same work item, which is this one).

| # | Decision | Amends | Applied |
|---|---|---|---|
| A1 | **A fifth port for registry access.** `GameSlotStore` in `application/port` — `<T> T withSlot(GameId, Function<Slot,T>)`, `insert`, `remove` — implemented by `app/registry.GameRegistry`, with an `InMemoryGameSlotStore` test double. `Slot`, the port-facing view `withSlot` hands its function, is part of the same port and is shaped by data-model.md § *Registry state*; its `SnapshotContext contextFor(Seat, Instant)` is how an `application` use case obtains the projector's context without naming an `app` type (research.md D27). `plan.md` § *Ports* lists four, but `GameSlot` lives in `app/registry` while `CommandUseCase` lives in `application/usecase` and must take the slot lock; without a port that call cannot compile under the module table. Keeps `application` framework-free, keeps `ArchitectureTest` green, and keeps proof areas 4, 5 and 6 as `application` tests | `plan.md` § *Ports* (four → five) | ✅ `plan.md` § *Ports* now lists five and states why the fifth exists |
| A2 | **38 tasks across 7 phases, not the earlier forecast of 32 across 6.** That forecast already flagged itself as needing re-approval under AGENTS.md rule 3. The six extra come from splitting the two coarsest groups — domain rules and web adapters — and from separating packaging and wire conformance into their own phase, so each task fits one fresh session. No new files: the same work, re-cut | `plan.md` § *Forecast*; `AGENTS.md` § *Scope control* rule 3 | ✅ `plan.md` § *Forecast* now forecasts 38 tasks across 7 phases and records the re-approval |
| A3 | **The generated `Problem`, not a hand-written `ProblemDto`.** D1 reversed D19's premise: every component model is generated, and the contract's schema is named `Problem` (there is no `ProblemDto` in the contract). A hand-written copy would be exactly the unchecked second wire type that generation exists to eliminate. D19's real rejection — Spring's `ProblemDetail`, whose `type`/`instance` defaults and dynamic properties the contract does not define — still stands | `research.md` D19; `plan.md` § *Failures* | ✅ `research.md` D19 and `plan.md` § *Failures* + § *Request security* now name the generated `Problem` |

## Package and path conventions

| Module | Root package | Source root |
|---|---|---|
| `domain` | `ua.kostenko.battleship.domain` | `backend/domain/src/main/java/ua/kostenko/battleship/domain/` |
| `application` | `ua.kostenko.battleship.application` | `backend/application/src/main/java/ua/kostenko/battleship/application/` |
| `app` | `ua.kostenko.battleship.app` | `backend/app/src/main/java/ua/kostenko/battleship/app/` |

Tests mirror the main tree under `src/test/java/…`. `*Test` runs under Surefire, `*IT` under Failsafe.
Generated DTOs land in `backend/app/target/generated-sources/openapi`, package
`ua.kostenko.battleship.app.web.dto` — build output: never committed, never hand-edited, not formatted
by Spotless (Constitution VI; AGENTS.md § *Definition of Done*).

---

## Phase 1: Reactor, toolchain and the generator spike

**Purpose**: a buildable three-module reactor whose module directions are enforced, whose wire DTOs
generate from the contract, and whose configuration surface exists. No game behaviour yet.

**Checkpoint**: `./mvnw -q verify` is green from `backend/`, and every later task has a place to put
code and a command to prove it.

- [ ] T001 Create the Maven reactor and wrapper in `backend/pom.xml`, `backend/mvnw`, `backend/domain/pom.xml`, `backend/application/pom.xml`, `backend/app/pom.xml`
  - **Delivers** A three-module Maven reactor that builds. Root `backend/pom.xml` inherits `spring-boot-starter-parent` **4.1.1**, sets `<java.version>25</java.version>`, declares modules `domain`, `application`, `app` in that order, and pins Surefire/Failsafe explicitly to **3.5.6**, overriding the parent's managed version, with Failsafe bound to `integration-test` + `verify`. `domain` has no dependencies beyond the JDK and `spring-boot-starter-test` (test scope). `application` depends on `domain`. `app` depends on both plus `spring-boot-starter-{web,validation,security,actuator}`. `app` declares `spring-boot-maven-plugin` with the `repackage` goal and produces the single executable JAR with `finalName` `battleship-app-1.0.0-SNAPSHOT` — T005's `spring-boot:run` and T037's `java -jar` both need it, so it exists from the first build. Version `1.0.0-SNAPSHOT` throughout. All versions exact, no ranges.
    **Shared test doubles across modules.** `SeededRandomSource` is declared in `domain/src/test` (T010) and reused by `application` (T015, T016, T019) and `app` (T028); `MutableTimeSource` is declared in `application/src/test` (T014) and reused by `app` (T020, T028, T032, T037). Test classes are not on a downstream module's test classpath by default, so `domain` and `application` each attach a `maven-jar-plugin` `test-jar` execution, and `application` (on `domain`) and `app` (on `domain` **and** `application`) declare the matching test-scoped `<type>test-jar</type>` dependencies. **T015** is the first consumer; without this, T014's "`SeededRandomSource` is reused, not re-declared" cannot be honoured.
  - **Covers** plan.md § *Layout*, § *Stack* · Constitution § *Scope and Technical Baseline* (three modules, Java 25.0.4, Maven 3.9.16 Wrapper, Spring Boot 4.1.1) · R57 (the artifact this pom will produce)
  - **Read first** plan.md § *Layout* and § *Stack* (incl. "Deliberately not added" — do **not** add JaCoCo, Checkstyle, PMD, Error Prone, NullAway, Caffeine, Testcontainers, WireMock, PIT, Jazzer, jqwik or Mockito) · quickstart.md § *Prerequisites* · research.md D5, D24
  - **Files** `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`, `backend/{domain,application,app}/pom.xml` (the `test-jar` executions in `domain`/`application`, the matching test-scoped `test-jar` dependencies in `application`/`app`, and `spring-boot-maven-plugin` `repackage` in `app`), one placeholder `package-info.java` per module so each compiles
  - **Proof** The build itself. `./mvnw -q verify` reaches `BUILD SUCCESS` across all three modules. The wrapper is generated **once** with the global Maven (`mvn -N wrapper:wrapper -Dmaven=3.9.16`, wrapper plugin 3.3.4, script-only distribution); from then on only `backend/mvnw` is used.
  - **Verify** `cd backend && ./mvnw -q verify` — then `./mvnw -v` reports Maven 3.9.16 and Java 25.0.4
  - **Mutation** Set the parent version to a non-existent `4.1.99`; `verify` must fail resolving the parent. Restore.
  - **Depends on** nothing
  - **Note** `~/.m2` does not exist on this machine (quickstart.md § *Prerequisites*). The first run is a cold download of the Maven distribution and the whole Spring Boot 4.1.1 tree. Expect minutes; that is not a build problem.

- [ ] T002 Generate the wire DTOs from the contract and prove the spike's abort condition in `backend/app/pom.xml` and `backend/app/src/test/java/ua/kostenko/battleship/app/web/dto/GeneratedModelSpikeTest.java`
  - **Delivers** `openapi-generator-maven-plugin` **7.25.0** in the `app` module only, bound to `generate-sources`, reading `../../contracts/openapi.yaml` and emitting models into `target/generated-sources/openapi`, package `ua.kostenko.battleship.app.web.dto`. Configuration is research.md D28 **verbatim**: `generatorName=java`, `library=native`, `generateModels=true`, `generateApis=false`, `generateModelTests=false`, `generateSupportingFiles=false`, `useJakartaEe=true`, `serializationLibrary=jackson`, `dateLibrary=java8`, `enumPropertyNaming=UPPERCASE`, `disallowAdditionalPropertiesIfNotPresent=false`, and `typeMappings`/`importMappings` sending `Instant` → `java.time.OffsetDateTime`.
  - **Covers** research.md D1, D28, D29, D30 · plan.md § *Forecast* → *Task 1 — the generator spike, with an abort condition* · Constitution VI (generated output is changed only by its generation path)
  - **Read first** research.md D1, D28, D29, D30 · plan.md § *Layout* (the generated-sources note) and § *Risks* · AGENTS.md § *Definition of Done* → *Generation path* · `contracts/openapi.yaml` `components.schemas` (39 schemas) and `Command` (L1278)
  - **Files** `backend/app/pom.xml` (plugin block), `backend/app/src/test/java/.../GeneratedModelSpikeTest.java`
  - **Proof** `GeneratedModelSpikeTest` asserts the five open questions D28 lists, each as its own test method:
    1. **The 39 component schemas emit exactly as this manifest says** — which is 36 types, not 39, and the split matters because assertion 4 depends on it.
       **29 object schemas emit as classes**, each asserted loadable by name: `Coordinate`, `Ship`, `Board`, `Player`, `Shot`, `Outcome`, `DurationAggregate`, `FleetSummary`, `PlayerStatistics`, `MatchStatistics`, `GameStatistics`, `GameSnapshot`, `FleetEntry`, `Ruleset`, `RulesetList`, `Limits`, `Meta`, `Health`, `CreateGameRequest`, `JoinGameRequest`, `PlaceShipCommand`, `RemoveShipCommand`, `FireCommand`, `SimpleCommand`, `Command`, `CommandRequest`, `StreamClosed`, `Violation`, `Problem`.
       **7 enum types emit**: `Orientation`, `Side`, `Phase`, `Action`, `CellState`, `ShipStatus`, `ProblemCode`.
       The remaining **three — `GameId`, `DisplayName` and `Instant` — are `type: string` aliases and must *not* mint classes**: they are expected to inline as `String`, `String` and (via D28's `typeMappings`) `java.time.OffsetDateTime`. Assert no class of those three names exists in the generated package; assertion 4 is the `Instant` half of the same claim.
    2. **The four `Command` variants deserialize by their `type` tag**, including all four values that map to `SimpleCommand` (`PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY`, `RESIGN`) — research.md D30.
    3. **`ProblemCode`'s 17 kebab-case values round-trip** through Jackson: `malformed-request`, `session-required`, `request-security-rejected`, `game-unavailable`, `invitation-unavailable`, `action-not-allowed`, `placement-out-of-bounds`, `placement-overlap`, `placement-touching`, `target-already-fired`, `game-expired`, `payload-too-large`, `unsupported-media-type`, `validation-failed`, `rate-limit-exceeded`, `internal-error`, `service-unavailable`.
    4. **The contract's schema named `Instant` does not shadow `java.time.Instant`** — `GameSnapshot.getServerTime()` returns `OffsetDateTime`, and a value serializes as `2026-09-20T12:00:00.000Z`.
    5. **The 13 one-element `allOf` wrappers inline** rather than minting wrapper classes (`Ship.anchor`, `Ship.orientation`, `PlayerStatistics.turns`, `PlayerStatistics.shotDecisions`, `GameSnapshot.{serverTime, opponent, turn, expiresAt, invitationExpiresAt, yourBoard, opponentBoard, outcome, statistics}`); `StreamClosed` is emitted despite nothing `$ref`ing it; `Ruleset.board`'s inline object yields a usable nested type.
  - **Verify** `cd backend && ./mvnw -q -pl app -am test -Dtest=GeneratedModelSpikeTest` — then `ls app/target/generated-sources/openapi/src/main/java/ua/kostenko/battleship/app/web/dto/`
  - **Mutation** Rename `Coordinate` to `Coord` in a scratch copy of `contracts/openapi.yaml` outside the repository and point the plugin at it; the spike test must fail to compile or to load the class. Restore.
  - **Depends on** T001
  - **⚠ Abort condition** If a numbered assertion cannot be made to pass on JDK 25, **stop and record the outcome here as a decision** — the named fallback is models-only for the schemas that do generate plus hand-written records for the rest, *not* a silent retreat to research.md D1's rejected alternative. Nothing after this task assumes the spike succeeded; the assembler of T025 is the only consumer of the generated shapes.
  - **Note** This is plan.md's "Task 1". It is T002 only because a Maven plugin cannot run without the pom that T001 creates. Nothing between them depends on generated types.

- [ ] T003 [P] Enforce module direction with Maven Enforcer and ArchUnit in `backend/pom.xml` and `backend/app/src/test/java/ua/kostenko/battleship/app/ArchitectureTest.java`
  - **Delivers** Maven Enforcer **3.6.3** `bannedDependencies` per module, plus one ArchUnit test in `app` that proves the direction in bytecode. `domain` may reference the JDK only — no Spring, Jackson, servlet, clock, random, logging or I/O type. `application` may reference `domain` and the JDK — no controller, cookie, emitter, concrete map in its API, or framework type. `app` may reference both and must contain no game rule, no second projection and no UI asset.
  - **Covers** plan.md § *Layout* (the module table) · Constitution III (*Simple, Replaceable Boundaries*) · research.md D22 · plan.md § *Validation* row "Module dependency direction"
  - **Read first** plan.md § *Layout* module table · Constitution III · research.md D22, D31 (why the projector returns `SnapshotView` and not a DTO)
  - **Files** `backend/pom.xml` (enforcer executions), `backend/app/pom.xml` (ArchUnit test-scope dependency), `backend/app/src/test/java/.../ArchitectureTest.java`
  - **Proof** `ArchitectureTest` — four rules, over an importer scoped to the three modules' **main** classes only (`ImportOption.DoNotIncludeTests`, plus a location filter that excludes the `test-jar` artifacts T001 puts on this module's test classpath — the shared doubles are test code and rules 1 and 2 do not govern them): (1) no class under `..domain..` depends on `org.springframework..`, `com.fasterxml..`, `jakarta..`, `java.time.Clock`, `java.util.Random` or `java.io..`; (2) no class under `..application..` depends on `org.springframework..`, `com.fasterxml..` or `jakarta..`; (3) `..domain..` does not depend on `..application..` or `..app..`; (4) `..application..` does not depend on `..app..`.
  - **Verify** `cd backend && ./mvnw -q verify` (Enforcer runs at `validate`, ArchUnit under Surefire in `app`)
  - **Mutation** Two steps, because the two guards react to different things and neither substitutes for the other. **(a)** Add `spring-boot-starter` as a compile dependency of `domain/pom.xml`; Enforcer's `bannedDependencies` must fail the build at `validate`. Restore. **(b)** With the ban temporarily lifted and that dependency still present, add `import org.springframework.stereotype.Component;` and the annotation to a `domain` class; `ArchitectureTest` must fail. Restore both. A bare import with no dependency declared is a **compile** error, not an Enforcer failure — which is why (a) exists.
  - **Depends on** T001

- [ ] T004 [P] Add Spotless with palantir-java-format in `backend/pom.xml`
  - **Delivers** Spotless **3.9.0** with `palantir-java-format`, `spotless:check` bound inside `verify`, and `app/target/generated-sources/**` excluded so generated DTOs are never reformatted (Constitution VI).
  - **Covers** plan.md § *Stack* · research.md D22 · Constitution VI (generated output changed only by its generation path)
  - **Read first** plan.md § *Stack* · AGENTS.md § *Definition of Done* → *Generation path*
  - **Files** `backend/pom.xml`
  - **Proof** `./mvnw spotless:check` is clean on the whole reactor, and stays clean when the generated sources exist (run it after T002's generation, not before).
  - **Verify** `cd backend && ./mvnw spotless:check` — `./mvnw spotless:apply` is the fix-up
  - **Mutation** Collapse the indentation of one `domain` file; `spotless:check` must fail naming that file. `spotless:apply` restores it. Then, to make the exclusion's proof capable of failing: temporarily add `app/target/generated-sources/openapi/**/*.java` to Spotless's `includes` **with the exclusion still in place**, mis-indent a file there, and confirm `spotless:check` stays **clean** — then remove the exclusion and confirm the same run now fails. Restore both. (Spotless's default Java target is `src/{main,test}/java`, so mis-editing a generated file without widening `includes` first leaves `spotless:check` clean whether the exclusion exists or not — an assertion that cannot fail.)
  - **Depends on** T001, T002

- [ ] T005 Bootstrap the Spring Boot application in `backend/app/src/main/java/ua/kostenko/battleship/app/BattleshipApplication.java`
  - **Delivers** The `@SpringBootApplication` entry point, `spring.threads.virtual.enabled=true` in `backend/app/src/main/resources/application.yaml`, and `@EnableScheduling` for the sweeper and heartbeat schedulers that T020 and T031 attach to. Server port 8080 (default). No controller, no security configuration yet.
  - **Covers** R57 (the runnable artifact) · plan.md § *Realtime* (virtual thread per stream) · research.md D6
  - **Read first** plan.md § *Realtime* first paragraph · research.md D6, D7 (why `ReentrantLock`, never `synchronized` — virtual threads pin) · quickstart.md § *Commands*
  - **Files** `backend/app/src/main/java/.../BattleshipApplication.java`, `backend/app/src/main/resources/application.yaml`
  - **Proof** `ApplicationContextTest` (`@SpringBootTest`) — the context loads, and `Environment.getProperty("spring.threads.virtual.enabled")` is `true`.
  - **Verify** `cd backend && ./mvnw -q -pl app -am test -Dtest=ApplicationContextTest` — then `./mvnw -pl app -am spring-boot:run` prints `Started BattleshipApplication`
  - **Mutation** Set `spring.threads.virtual.enabled=false`; `ApplicationContextTest` must fail.
  - **Depends on** T001

- [ ] T006 [US7] Bind and validate every configuration setting in `backend/app/src/main/java/ua/kostenko/battleship/app/config/BattleshipProperties.java`
  - **Delivers** One validated `@ConfigurationProperties(prefix = "battleship")` **record** with one nested `RateLimits` record, and the defaults written into `application.yaml`. **All 23 keys**, mapped from spec R52's rows by plan.md § *Configuration* — the defaults are R52's and are stated in exactly one place (this file plus `application.yaml`):
    `idle-timeout-seconds` 900 · `max-game-duration-seconds` 7200 · `result-retention-seconds` 300 · `invitation-lifetime-seconds` 900 · `presence-interval-seconds` 300 · `heartbeat-seconds` 15 · `stream-max-lifetime-seconds` 1200 · `max-concurrent-games` 100 · `max-concurrent-streams` 200 · `max-live-games-per-browser` 1 · `max-request-body-bytes` 16384 · `random-arrangement-attempts` 1000 · `sweep-interval-seconds` 30 · `shutdown-drain-seconds` 5 · `public-base-url` `http://localhost:5173` · `rate-limit.create-game-per-minute` 5 · `rate-limit.join-per-minute` 20 · `rate-limit.commands-per-minute` 60 · `rate-limit.read-game-per-minute` 120 · `rate-limit.presence-per-minute` 30 · `rate-limit.stream-open-per-minute` 30 · `rate-limit.replace-invitation-per-minute` 10 · `rate-limit.leave-per-minute` 10.
    Every numeric key carries `@Min(1)` (absent, unparseable, negative and zero all stop start-up naming the setting). A `@PostConstruct` check validates `public-base-url`: absolute, scheme `http` or `https`, no user-info credentials, no query, no fragment.
    The first six keys are exactly the contract's `Limits` object, so `getMeta` reads them straight off this record — a limit exists once (R55). No limit may be duplicated anywhere else in the codebase.
  - **Covers** R52, R53, R54, R55 (the single-source half), R56 · proof area 9 (first half) · research.md D25
  - **Read first** spec.md R52 (the defaults table — this is their single source), R53, R54, R55, R56 · plan.md § *Configuration and observability* (the key mapping table; it states no defaults of its own) · research.md D25 · `contracts/openapi.yaml` `Limits` (L1147, all six `integer, minimum: 1`)
  - **Files** `backend/app/src/main/java/.../config/BattleshipProperties.java`, `backend/app/src/main/resources/application.yaml`, `backend/app/src/test/java/.../config/ConfigurationValidationIT.java`
  - **Proof** `ConfigurationValidationIT` — (1) a default start-up binds all 23 keys to exactly the R52 values above, asserted key by key; (2) `BATTLESHIP_IDLETIMEOUTSECONDS=0` stops start-up and the failure message names `idle-timeout-seconds`; the same for a negative and for an unparseable value; (3) `public-base-url` fails start-up naming the setting for each of: relative (`/join`), `ftp://host`, `http://user:pw@host`, `http://host?a=b`, `http://host#frag`; (4) **R53** — an environment variable beats the same key in `application.yaml` (`BATTLESHIP_IDLETIMEOUTSECONDS=60` wins over the file's 900).
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=ConfigurationValidationIT`
  - **Mutation** Drop `@Min(1)` from `idleTimeoutSeconds`; assertion (2) must fail. Drop the fragment check from the `@PostConstruct`; assertion (3)'s fragment case must fail.
  - **Depends on** T005
  - **Note** This lands the *validation* half of proof area 9 ahead of Phase 6, where plan.md § *Forecast* attributes the whole area. The *published-and-enforced* half (R55/S12, `PublishedLimitsIT`) stays in T037, where both sides exist to be compared.

**Checkpoint**: `./mvnw -q verify` green · DTOs generate and load · module direction enforced in two
independent ways · the application starts · every limit is external, validated and defaulted.

---

## Phase 2: Domain — rules, rulesets and the transition

**Purpose**: the whole game as pure, framework-free, deeply immutable records. `GameRules.apply` is
the only transition; it reads no clock and creates no randomness of its own.
**Proof areas carried**: 1 (ruleset correctness), 2 (action legality and state stability).

**Shared contract for every task in this phase.** All types are `record`s, deeply immutable, with
collections stored via `List.copyOf` / `Map.copyOf` / `Set.copyOf`. The single transition is
`GameRules.apply(GameState state, GameCommand command, Instant now, RandomSource random)` returning
`Transition`. A refused command returns the input state **by identity** (data-model invariant 8).
`Seat` is `HOST` | `GUEST` and is never mapped to the contract's `YOU`/`OPPONENT` here — that happens
only in the projector (T015).

**Checkpoint**: a whole game can be played end to end as a sequence of `GameRules.apply` calls, under
both rulesets, with no Spring, no clock and no I/O anywhere in `domain`.

- [ ] T007 [US3] Publish the two rulesets and the board primitives in `backend/domain/src/main/java/ua/kostenko/battleship/domain/`
  - **Delivers** `model/Coordinate` (`int rowIndex`, `int columnIndex`, zero-based; row 0 is the top row, column 0 the left), `model/Orientation` (`HORIZONTAL` extends from the anchor to the right, `VERTICAL` extends downwards), `model/Seat` (`HOST`, `GUEST`), `model/Phase` (`WAITING`, `PLACEMENT`, `PLAYING`, `FINISHED`, `ABANDONED`); `rules/FleetEntry` (`shipTypeId`, `int length`, `int count`), `rules/Ruleset` (`id`, `int rows`, `int columns`, `List<FleetEntry> fleet`, `boolean shipsMayTouch`, `boolean extraTurnOnHit`, `boolean revealWaterAroundSunk`), and `rules/Rulesets` holding the two immutable constants of spec R05 with a lookup by id. A corrected ruleset is published under a **new** id, never edited (R03).
  - **Covers** R03, R05 · data-model.md § *Rulesets* · proof area 1 (the data half)
  - **Read first** spec.md R05 (the table is the single source of this product data) · data-model.md § *Rulesets* · `contracts/openapi.yaml` `Ruleset` (L1103: `id` pattern `^[a-z0-9-]+\.v[0-9]+$`, `board.rows`/`columns` `integer, minimum: 1, maximum: 100`), `FleetEntry` (L1090: `shipTypeId` pattern `^[a-z0-9-]{2,32}$`, `length` `minimum: 1`, `count` `minimum: 1`)
  - **Files** `domain/model/{Coordinate,Orientation,Seat,Phase}.java`, `domain/rules/{FleetEntry,Ruleset,Rulesets}.java`, `domain/src/test/java/.../rules/RulesetCatalogTest.java`
  - **Proof** `RulesetCatalogTest` — exactly two rulesets, asserted field by field against R05:
    - `sea-battle-10-ship.v1` — 10×10; fleet `ship-4`×1 length 4, `ship-3`×2 length 3, `ship-2`×3 length 2, `ship-1`×4 length 1 (10 ships, 20 cells); `shipsMayTouch=false`, `extraTurnOnHit=true`, `revealWaterAroundSunk=true`
    - `hasbro-classic-2002.v1` — 10×10; fleet `carrier`×1 length 5, `battleship`×1 length 4, `destroyer`×1 length 3, `submarine`×1 length 3, `patrol-boat`×1 length 2 (5 ships, 17 cells); `shipsMayTouch=true`, `extraTurnOnHit=false`, `revealWaterAroundSunk=false`
    - both ids match `^[a-z0-9-]+\.v[0-9]+$`; every `shipTypeId` matches `^[a-z0-9-]{2,32}$`; the three flags differ between the two rulesets in all three positions
    - the constants are unmodifiable: mutating the returned fleet list throws
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest=RulesetCatalogTest`
  - **Mutation** Change `hasbro-classic-2002.v1`'s `shipsMayTouch` to `false`; the flag-difference assertion must fail.
  - **Depends on** T001

- [ ] T008 [US1] [US2] Create and join the game aggregate in `backend/domain/src/main/java/ua/kostenko/battleship/domain/model/`
  - **Delivers** The state records and the two non-command transitions.
    `Ship` (`shipId`, `shipTypeId`, `int length`, `Coordinate anchor?`, `Orientation orientation?`, `Set<Coordinate> hits`) with **derived, never stored** `cells()` (anchor + orientation + length; empty while unplaced) and `status()` (`INTACT` no hits / `DAMAGED` some / `SUNK` all cells hit).
    `Board` (`List<Ship> fleet` — the whole fleet from `PLACEMENT` on, placed or not; `Set<Coordinate> incomingShots` — cells fired at **on** this board; `Set<Coordinate> revealedWater`).
    `PlayerState` (`displayName`, `boolean ready`, `Board board`, `List<Shot> shotsFired` — shots made **by** this player).
    `Shot` (`Seat by`, `Coordinate target`, `ShotResult result`, `sunkShipId?` present only when `SUNK`), `ShotResult` (`MISS`, `HIT`, `SUNK`), `Outcome` (`Seat winner`, `Reason reason` ∈ `FLEET_DESTROYED` | `RESIGNATION`), `Timeline` (the instants and samples of data-model.md § *Statistics*, all empty at creation).
    `GameState` (`rulesetId`, `Phase phase`, `long version`, `PlayerState host`, `PlayerState guest?`, `Seat turn?`, `Shot lastShot?`, `Outcome outcome?`, `Timeline timeline`) with `GameState.create(Ruleset, String hostName)` → `WAITING`, `version` **0**, no guest, no fleets; and `GameState.withGuest(String guestName, Instant now)` → `PLACEMENT`, `version` 1, both fleets created, `timeline.guestJoinedAt = now`.
    **Fleet creation** assigns `shipId` `s01`, `s02`, … in fleet order (longest type first as R05 lists them), once, at fleet creation; ids are unique within their own player's board, stable for the whole game, survive clearing and random rearrangement, and carry no hidden information. The two boards may use the same values.
  - **Covers** R12 (the first transition), R20, R21 · data-model.md § *Domain*, invariants 3 and 4 · research.md D21
  - **Read first** spec.md R12, R20, R21 · data-model.md § *Domain* (the type table) and § *State machine* · research.md D21 · `contracts/openapi.yaml` `Ship` (L803: `shipId` pattern `^[A-Za-z0-9_-]{1,32}$`, `cells` empty while not placed, `anchor`/`orientation` absent while not placed), `ShipStatus` (L800), `Shot` (L866), `Outcome` (L885)
  - **Files** `domain/model/{Ship,Board,PlayerState,Shot,ShotResult,Outcome,Timeline,GameState}.java`, `domain/src/test/java/.../model/GameCreationTest.java`
  - **Proof** `GameCreationTest` — (1) `create` yields `WAITING`, `version` 0, `guest` absent, `turn` absent, `outcome` absent; (2) `withGuest` yields `PLACEMENT`, `version` 1, `guestJoinedAt` set to the passed instant; (3) both fleets are created at join with ids `s01`…`s10` (sea-battle) and `s01`…`s05` (hasbro) in fleet order, every id matching `^[A-Za-z0-9_-]{1,32}$`; (4) the fleet multiset equals the ruleset's, placed or not (invariant 3); (5) `cells()` derives from anchor + orientation + length for both orientations and is empty while unplaced; (6) `status()` is `INTACT`/`DAMAGED`/`SUNK` for zero / some / all cells hit; (7) every record is deeply immutable — mutating a collection handed in or returned throws.
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest=GameCreationTest`
  - **Mutation** Assign ship ids from a counter that resets per call so the two boards' ids diverge from fleet order; assertion (3) must fail.
  - **Depends on** T007

- [ ] T009 [US3] Implement `PLACE_SHIP` and `REMOVE_SHIP` in `backend/domain/src/main/java/ua/kostenko/battleship/domain/`
  - **Delivers** The command and transition machinery, plus the first two commands.
    `command/GameCommand` — a sealed interface with `PlaceShip(shipId, anchor, orientation)` and `RemoveShip(shipId)` for now; `transition/Transition` (`GameState next`, `boolean versionBumped`, `Set<Seat> viewChanged`, `Rejection?`); `transition/Rejection` (the `ProblemCode` the refusal maps to and, for a validation failure, the JSON Pointer to the offending field plus the `Violation.rule` value; present exactly when the command was refused); `rules/GameRules.apply(...)` dispatching on the command type.
    `PLACE_SHIP` **relocates and rotates atomically** — no remove-then-add. Placing a ship exactly where it already is, and removing a ship that is not placed, both **succeed and change nothing**: accepted (so the caller records the `commandId` and moves the idle deadline) but `versionBumped=false` and `viewChanged` empty.
    **Four distinct refusals**, never collapsed (R09): a coordinate outside the ruleset's board or an unknown `shipId` → `validation-failed` (422) with rule `OUT_OF_RANGE` / `UNKNOWN_VALUE` and the JSON Pointer to the field (e.g. `/command/anchor/rowIndex`); a ship whose anchor is on the board but which would extend past the edge → `placement-out-of-bounds`; an overlap with another ship → `placement-overlap`; where `shipsMayTouch` is false, a ship within Chebyshev distance 1 of another → `placement-touching`.
  - **Covers** R06, R09, R15, R18, R23 · data-model.md § *Rule details* (first two bullets), invariants 1, 2, 7, 8 · proof area 2 (the stability half)
  - **Read first** spec.md R06, R09, R15, R18, R23 · data-model.md § *Rule details* · `contracts/openapi.yaml` `PlaceShipCommand` (L1220, `additionalProperties: false`, `required: [type, shipId, anchor, orientation]`), `RemoveShipCommand` (L1238), `Violation` (L1315, `field` is a JSON Pointer; `rule` ∈ `REQUIRED, UNKNOWN_FIELD, INVALID_TYPE, INVALID_FORMAT, TOO_SHORT, TOO_LONG, OUT_OF_RANGE, UNKNOWN_VALUE`), `Coordinate` (L739, `rowIndex`/`columnIndex` 0–99 — **wider than any ruleset board**, so out-of-board is this task's 422, not a schema failure), `sendCommand` description
  - **Files** `domain/command/GameCommand.java`, `domain/transition/{Transition,Rejection}.java`, `domain/rules/GameRules.java`, `domain/src/test/java/.../rules/PlacementRulesTest.java`
  - **Proof** `PlacementRulesTest` — (1) a legal placement is accepted, `versionBumped=true`, `viewChanged` = that seat only (the opponent cannot see a placement); (2) re-placing the same ship at a different anchor moves it atomically — the old cells are free in the same `Transition`, never an intermediate state; (3) placing a ship exactly where it already is, and removing an unplaced ship, are both **accepted** with `versionBumped=false` and `viewChanged` empty; (4) each of the four refusals is produced by its own scenario and carries its own distinct code — the 422 cases also carry the right JSON Pointer and rule; (5) `placement-touching` fires under `sea-battle-10-ship.v1` for a diagonal neighbour and does **not** fire under `hasbro-classic-2002.v1` (R06's difference); (6) every refusal returns the input `GameState` **by identity** (`assertThat(t.next()).isSameAs(before)`) and leaves `version` untouched (invariants 7, 8).
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest=PlacementRulesTest`
  - **Mutation** Collapse `placement-overlap` and `placement-touching` into one code; assertion (4) must fail. Return a rebuilt copy instead of the input instance on refusal; assertion (6) must fail.
  - **Depends on** T008

- [ ] T010 [US3] Implement `PLACE_FLEET_RANDOMLY` and `CLEAR_FLEET` in `backend/domain/src/main/java/ua/kostenko/battleship/domain/`
  - **Delivers** `domain/RandomSource` — a JDK-only interface declared **in `domain`**, because `GameRules.apply(..., RandomSource)` takes it (data-model.md § *Domain*); `application` and `app` reuse this one declaration rather than minting a second (DRY; Constitution IX). Two more `GameCommand` variants: `PlaceFleetRandomly` and `ClearFleet`.
    `PLACE_FLEET_RANDOMLY` draws from `RandomSource`, places **longest ships first**, produces an arrangement legal under the active ruleset (including the adjacency rule where `shipsMayTouch` is false), and **replaces whatever was there**. It is **attempt-bounded**: after `random-arrangement-attempts` failed attempts it gives up and refuses as an ordinary failure, leaving the fleet **exactly** as it was — a search can never occupy its game indefinitely. The attempt limit reaches `apply` as a parameter of the command or the rules call, never as a compile-time constant (R52).
    `CLEAR_FLEET` unplaces every ship while keeping each `shipId` unchanged (R21 — ids survive clearing).
  - **Covers** R21 (ids survive clear and re-randomise), R26 · data-model.md § *Rule details* (`PLACE_FLEET_RANDOMLY`) · research.md D14
  - **Read first** spec.md R26, R21, R52 (the `random-arrangement-attempts` row) · data-model.md § *Rule details* · research.md D14 (why `RandomSource` is separate from `SecretGenerator`) · plan.md § *Ports*
  - **Files** `domain/RandomSource.java`, `domain/command/GameCommand.java` (two variants added), `domain/rules/GameRules.java`, `domain/src/test/java/.../rules/RandomArrangementTest.java`, `domain/src/test/java/.../SeededRandomSource.java` (test double)
  - **Proof** `RandomArrangementTest` — (1) a seeded `RandomSource` reproduces one exact arrangement twice, asserted cell by cell (this is what R26 means by "reproduced in a proof rather than only smoke-tested"); (2) the arrangement is legal under **both** rulesets — every ship wholly on the board, straight, no overlap, and under `sea-battle-10-ship.v1` no two ships within Chebyshev distance 1; (3) the fleet multiset still equals the ruleset's (invariant 3); (4) re-randomising replaces the previous arrangement and keeps every `shipId`; (5) with the attempt limit set to 1 and a `RandomSource` that always returns a colliding coordinate, the command is **refused** as an ordinary failure and the fleet is identical to before by identity; (6) `CLEAR_FLEET` unplaces every ship, keeps every `shipId`, and is accepted with `viewChanged` = that seat only.
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest=RandomArrangementTest`
  - **Mutation** Remove the attempt bound (loop forever until success); assertion (5) must hang or fail — cap the test with a JUnit timeout so it fails rather than hangs.
  - **Depends on** T009

- [ ] T011 [US3] Implement `READY`, the start of play and the first turn in `backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/GameRules.java`
  - **Delivers** The `Ready` command variant. `READY` is accepted **only when the whole fleet is placed**; otherwise it is refused (and it is not offered — T013 owns the offering). It **cannot be undone**: `ready` never returns to false, and the four fleet-editing commands are refused once that seat is ready. The **second** `READY` moves `PLACEMENT` → `PLAYING`.
    **First turn (R11)**: the player whose `readyAt` is earlier fires first; an **exact** tie is broken from `RandomSource`. The choice is first visible when play starts, as `turn`.
    Timeline sampling: `readyAt[seat]` on each accepted `READY`; `playStartedAt` on the second; `turnStartedAt` initialised to `playStartedAt`.
  - **Covers** R11 (the ready-order rule), R14, R12 (`PLACEMENT`→`PLAYING`) · data-model.md § *Rule details* (*First turn*), § *Statistics* (`readyAt`, `playStartedAt`), invariants 6 and 9 · research.md D2
  - **Read first** spec.md R11, R14, R12 · data-model.md § *Rule details*, § *Statistics* sampling table · research.md D2 (this **overrides** the seed document's "random at game creation"; declaring ready first *is* an advantage, accepted deliberately) · `contracts/openapi.yaml` `Phase` description (L761, amended by this feature to state the ready-order rule)
  - **Files** `domain/command/GameCommand.java` (`Ready`), `domain/rules/GameRules.java`, `domain/model/Timeline.java`, `domain/src/test/java/.../rules/ReadyAndFirstTurnTest.java`
  - **Proof** `ReadyAndFirstTurnTest` — (1) `READY` with an incomplete fleet is refused and the state is returned by identity; (2) `READY` with a complete fleet is accepted, sets `ready`, records `readyAt`, and `viewChanged` includes **both** seats (the opponent's `Player.ready` flag changed); (3) a second `READY` from the same seat is refused, and every fleet-editing command from a ready seat is refused with `action-not-allowed`; (4) the second `READY` moves to `PLAYING`, sets `playStartedAt`, and sets `turn` to the seat whose `readyAt` is **earlier** — asserted in both directions (host first, then guest first); (5) with identical `readyAt` values, the tie is broken from a seeded `RandomSource` — two different seeds pick the two different seats, deterministically; (6) `turn` is present **exactly** in `PLAYING` and absent in every other phase (invariant 6).
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest=ReadyAndFirstTurnTest`
  - **Mutation** Replace the ready-order comparison with a fixed `HOST`; assertion (4)'s guest-first case must fail.
  - **Depends on** T010

- [ ] T012 [US4] Implement `FIRE`, `RESIGN` and both endings in `backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/GameRules.java`
  - **Delivers** The `Fire(target)` and `Resign` command variants, and the whole of play.
    `FIRE` names one cell of the **opponent's** board. A coordinate outside the ruleset's board is a `validation-failed` (422) with rule `OUT_OF_RANGE` (R10). A cell already disclosed is refused `target-already-fired`: it **costs nothing and does not pass the turn** (data-model.md § *Rule details*). Otherwise the shot resolves to `MISS`, `HIT` or `SUNK`, records `lastShot` (with `sunkShipId` present only when `SUNK`), appends to `shotsFired`, and grows `incomingShots`.
    **Turn**: under `extraTurnOnHit=true` a hit or a sink keeps the turn; otherwise every shot passes it (R07).
    **Revealed water**: under `revealWaterAroundSunk=true`, when a ship sinks, **every still-`UNKNOWN` neighbour of every cell of that ship — up to eight per cell —** becomes `REVEALED_WATER` without a shot; under `false` nothing is revealed (R08).
    **Endings**: the last ship of a fleet sinking, and `RESIGN`, are each **atomic** with the move to `FINISHED` — there is no observable "sunk but still playing" state. Both set `Outcome{winner, FLEET_DESTROYED|RESIGNATION}` and `timeline.finishedAt`. `RESIGN` is available to **both** players throughout play, so the game always ends with a stated winner rather than vanishing (R16).
    Timeline sampling: one `turns` sample per turn — from gaining the right to fire until it passes or the game ends; `turnStartedAt` moves to the instant of each pass, and the holder's open turn is closed at `finishedAt`. One `shotDecisions` sample per accepted `FIRE` — from being able to fire until the shot; reset at every `turnStartedAt` **and** after each accepted `FIRE` that retains the turn; a refused `FIRE` does **not** reset it.
  - **Covers** R07, R08, R10, R11 (both endings), R12 (`PLAYING`→`FINISHED`) · S1 · **proof area 1 owner** · data-model.md § *Rule details* (`FIRE`, *Turn*, *End*), § *Statistics* (`turns`, `shotDecisions`, `finishedAt`)
  - **Read first** spec.md R07, R08, R10, R11, R12 and § *Proof required* area 1 · data-model.md § *Rule details*, § *Statistics* · `contracts/openapi.yaml` `FireCommand` (L1250), `SimpleCommand` (L1263, `RESIGN`), `CellState` (L789: `UNKNOWN, WATER, SHIP, MISS, HIT, SUNK, REVEALED_WATER`), `Shot.result` (L879), `Outcome.reason` (L893), `sendCommand` description
  - **Files** `domain/command/GameCommand.java` (`Fire`, `Resign`), `domain/rules/GameRules.java`, `domain/model/Timeline.java`, `domain/src/test/java/.../rules/RulesetRulesTest.java`
  - **Proof** `RulesetRulesTest` — the owner of **proof area 1**, which spec.md § *Proof required* defines as "legal and illegal placements under both rulesets, the adjacency difference, the extra-turn difference, the revealed-water difference, and both ways a game ends":
    1. **A complete game to a destroyed fleet under `sea-battle-10-ship.v1`** — 20 hits sink 10 ships; the game reaches `FINISHED` with `FLEET_DESTROYED` and the right winner (S1).
    2. **A complete game to a destroyed fleet under `hasbro-classic-2002.v1`** — 17 hits sink 5 ships (S1).
    3. **Adjacency difference** — the diagonal placement refused in ruleset 1 is accepted in ruleset 2 (re-asserted here at ruleset level; T009 owns the refusal codes).
    4. **Extra-turn difference** — under ruleset 1 a hit keeps the turn and a miss passes it; under ruleset 2 both pass it.
    5. **Revealed-water difference** — sinking a ship under ruleset 1 turns every still-`UNKNOWN` neighbour of every one of its cells into `REVEALED_WATER` (asserted cell by cell, including the 8-neighbour corners and the clipping at a board edge); under ruleset 2 nothing changes.
    6. **Both endings** — fleet destroyed, and resignation by each seat in turn; each atomic with `FINISHED`, each with its `Outcome`, each setting `finishedAt`.
    7. `target-already-fired` on a `MISS`, a `HIT`, a `SUNK` and a `REVEALED_WATER` cell: refused, state returned by identity, **turn not consumed**.
    8. Timeline: `turns.count` equals the number of turns actually taken, `shotDecisions.count` equals the number of accepted `FIRE`s, and a refused `FIRE` does not reset the shot-decision clock.
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest=RulesetRulesTest`
  - **Mutation** Reveal water unconditionally (ignore `revealWaterAroundSunk`); assertion (5)'s ruleset-2 case must fail. Consume the turn on `target-already-fired`; assertion (7) must fail.
  - **Depends on** T011

- [ ] T013 [US3] [US4] [US6] Compute and enforce the allowed-action table in `backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/AllowedActions.java`
  - **Delivers** `AllowedActions.of(GameState, Seat)` returning the exact set spec R13's table prescribes, and the enforcement in `GameRules.apply`: a command whose action is **not** in that seat's offered set at that moment is refused `action-not-allowed`, with the state returned by identity and the version untouched. Nothing outside the table is ever offered; a client never derives permission from the phase, the turn or anything else.
    The table, verbatim from R13:

    | Situation | `allowedActions` |
    |---|---|
    | `WAITING`, the host | `NEW_INVITATION`, `SEND_PRESENCE`, `LEAVE` |
    | `PLACEMENT`, not ready, fleet incomplete | `PLACE_SHIP`, `REMOVE_SHIP`, `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `SEND_PRESENCE`, `LEAVE` |
    | `PLACEMENT`, not ready, fleet complete | the above **+** `READY` |
    | `PLACEMENT`, already ready | `SEND_PRESENCE`, `LEAVE` |
    | `PLAYING`, the player whose turn it is | `FIRE`, `RESIGN`, `SEND_PRESENCE` |
    | `PLAYING`, the other player | `RESIGN`, `SEND_PRESENCE` |
    | `FINISHED` or `ABANDONED`, either player | `LEAVE` |

    `LEAVE` is **never** offered during play — which is what makes T021's refusal consistent with R13 rather than circular (R16). It lives in `domain` (not the projector) so that proof area 2 is a `domain` test; the projector of T015 calls it.
  - **Covers** R13, R15, R16 (the never-offered half) · **proof area 2 owners** · data-model.md § *Allowed actions*, invariants 7 and 8
  - **Read first** spec.md R13 (the table is the single authority), R15, R16 and § *Proof required* area 2 · data-model.md § *Allowed actions* · plan.md § *Projection* (last sentence: `allowedActions` is computed from the R13 table, never derived by a client) · `contracts/openapi.yaml` `Action` (L772, ten values; the first seven are command types, the last three are the `invitation`, `presence` and `leave` operations)
  - **Files** `domain/rules/AllowedActions.java`, `domain/rules/GameRules.java`, `domain/src/test/java/.../rules/AllowedActionsTest.java`, `domain/src/test/java/.../rules/RefusedCommandStabilityTest.java`
  - **Proof** Two classes, the owners of **proof area 2** ("the offered-action table proven cell by cell, every action attempted in every phase it is not allowed in, and each refusal leaving the game and its version exactly as they were"):
    - `AllowedActionsTest` — all **seven rows** above asserted as exact set equality, for both seats where the row distinguishes them, under both rulesets where the fleet size matters. Includes the two boundary rows: fleet incomplete (no `READY`) vs. fleet complete (`READY` offered), and `PLAYING` turn-holder vs. other player.
    - `RefusedCommandStabilityTest` — a matrix: each of the seven command types attempted in each phase where it is **not** offered. Every one is refused `action-not-allowed`; every one returns the input `GameState` by identity; `version` is unchanged in every cell of the matrix. Plus: `LEAVE` is absent from `allowedActions` in `PLAYING` for both seats, while `RESIGN` is present for both.
  - **Verify** `cd backend && ./mvnw -q -pl domain test -Dtest='AllowedActionsTest,RefusedCommandStabilityTest'`
  - **Mutation** Add `LEAVE` to the two `PLAYING` rows; `AllowedActionsTest` and the last `RefusedCommandStabilityTest` assertion must both fail.
  - **Depends on** T012

**Checkpoint**: proof areas 1 and 2 are owned and green. A whole game plays under both rulesets as pure
function calls. `domain` still has zero framework, clock, random or I/O dependencies — `ArchitectureTest`
(T003) proves it on every run.

---

## Phase 3: Application — ports, projection, registry, use cases

**Purpose**: everything that decides *what one player may see* and *in what order things happen*,
still framework-free. `application` may reference `domain` and the JDK only.
**Proof areas carried**: 3 (player privacy), 4 (repeat safety), 5 (concurrency), 6 (lifetimes).

**Checkpoint**: a whole game can be driven through the use cases with a mutable `TimeSource` and a
seeded `RandomSource`, with privacy, idempotency, linearization and every deadline proven — and still
no HTTP anywhere.

- [ ] T014 [P] Declare the time and secret ports in `backend/application/src/main/java/ua/kostenko/battleship/application/port/`
  - **Delivers** `TimeSource.now()` returning `Instant` and `SecretGenerator`, plus their production implementations in `app/config` and their test doubles. `SystemTimeSource` wraps `Clock.systemUTC()`. `SecureRandomSecretGenerator` uses `SecureRandom` and produces: **128-bit base64url game ids** matching `^[A-Za-z0-9_-]{22}$`; **256-bit session values**; **256-bit invitation secrets** matching `^[A-Za-z0-9_-]{43}$`. Test doubles: `MutableTimeSource` (a settable `Instant`, advanced by the test — **no test in this feature ever sleeps or depends on real elapsed time**, R60) and `FixedSecretGenerator`. `SeededRandomSource` (T010) already implements `domain.RandomSource`; it is reused, not re-declared.
    Each port has a real second implementation, which is what justifies the interface at all (Constitution IX; plan.md § *Ports*).
  - **Covers** R32 (high-entropy session from a cryptographically secure source), R34 (invitation secret), R60 · research.md D12, D14
  - **Read first** spec.md R60, R32, R34 · plan.md § *Ports* · research.md D12 (one `TimeSource`, wall-clock instants, no second monotonic source), D14 (why `SecretGenerator` and `RandomSource` are separate: arrangements must be reproducible, secrets must never be) · `contracts/openapi.yaml` `GameId` (L720, `^[A-Za-z0-9_-]{22}$`, "Random 128-bit value, base64url"), `session` security scheme (L567, "Random, at least 256 bits")
  - **Files** `application/port/{TimeSource,SecretGenerator}.java`, `app/config/{SystemTimeSource,SecureRandomSecretGenerator}.java`, `application/src/test/java/.../{MutableTimeSource,FixedSecretGenerator}.java`, `application/src/test/java/.../MutableTimeSourceTest.java`, `app/src/test/java/.../SecretGeneratorTest.java`
    `MutableTimeSource` and `FixedSecretGenerator` are published to downstream modules by `application`'s `test-jar` execution (T001); `app`'s tests take them from there rather than re-declaring them.
  - **Proof** `SecretGeneratorTest` — (1) 1000 generated game ids all match `^[A-Za-z0-9_-]{22}$` and are all distinct; (2) 1000 invitation secrets all match `^[A-Za-z0-9_-]{43}$` and are all distinct; (3) session values decode to at least 32 bytes; (4) two generators constructed identically still produce **different** values (the generator is not reproducible — the inverse of `RandomSource`). Plus `MutableTimeSourceTest` — `now()` returns exactly what was set, and `advance(Duration)` moves it by exactly that much.
  - **Verify** `cd backend && ./mvnw -q -pl application,app -am test -Dtest='SecretGeneratorTest,MutableTimeSourceTest'`
  - **Mutation** Generate game ids from `java.util.Random` seeded with a constant; assertion (4) must fail.
  - **Depends on** T005, T010

- [ ] T015 [US1] [US2] [US3] [US4] Build the projector — the one place that decides disclosure — in `backend/application/src/main/java/ua/kostenko/battleship/application/projection/`
  - **Delivers** `SnapshotProjector.project(GameState, Seat, SnapshotContext)` and the framework-free view records it returns: `SnapshotView`, `BoardView`, `PlayerView`, `ShotView` (and `StatisticsView` in T016). They are deeply immutable, take no decision of their own, and **never leave the server**; `SnapshotDtoAssembler` (T025) copies them into the generated `GameSnapshot`. Their fields are those of the contract schema each is copied into.
    `SnapshotContext` is an `application` record carrying what lives outside the aggregate — `serverTime`, `expiresAt`, `invitationUrl?`, `invitationExpiresAt?` and each seat's `connected` flag — built by `app/registry` from the slot. **The projector never sees `GameSlot`**, so the module direction holds (research.md D27).
    This is the **sole** place that decides disclosure (R17, R19):
    - the caller's own board is complete and drawable, listing the whole fleet including unplaced ships, with **no `UNKNOWN` cell**; its six states are disjoint and cover the board: `SHIP` (a ship cell not yet hit), `HIT` (a ship cell hit while its ship is afloat), `SUNK` (a cell of a sunk ship), `MISS` (an incoming shot on an empty cell), `REVEALED_WATER` (an empty cell disclosed around a sunk ship), `WATER` (every other empty cell);
    - the opponent grid carries only `UNKNOWN`, `MISS`, `HIT`, `SUNK`, `REVEALED_WATER`, and its `ships` lists **only sunk ships**, until `FINISHED` — when the opponent's complete board becomes visible;
    - `ABANDONED` and expired games disclose **no part** of the opponent's placement at all; each player still sees their own board;
    - `Seat` is resolved to the contract's caller-relative `Side` (`YOU` / `OPPONENT`) **here** — no view record carries `HOST`/`GUEST`;
    - `allowedActions` comes from `AllowedActions.of` (T013), never re-derived;
    - `shipsRemaining` is derived here — fleet size minus sunk ships — as are `Ship.cells` and `status` (R22);
    - `lastShot` and both `connected` flags are carried so a client needs no diffing, counting or rule knowledge of its own (R22);
    - `invitationUrl` and `invitationExpiresAt` appear **only** for the host and **only** in `WAITING`;
    - `opponent` is absent while no guest has joined; `turn` only in `PLAYING`; `outcome` and `statistics` only in `FINISHED`.
    `serverTime` and `expiresAt` ride on `SnapshotView` but are **not** part of a *view* in the R18 sense: they are always current, which is exactly why they are excluded from what moves `version`.
  - **Covers** R17, R18 (the view definition), R19, R20, R21, R22 · S2 · **proof area 3 owner** · data-model.md § *Projection view*, invariants 5, 10, 11 · research.md D27, D31
  - **Read first** spec.md R17–R22 and § *Proof required* area 3 · plan.md § *Projection* · data-model.md § *Projection view* and invariants 5, 10, 11 · research.md D27, D31 · `contracts/openapi.yaml` `GameSnapshot` (L995, `required: [gameId, version, serverTime, rulesetId, phase, you, allowedActions, expiresAt, yourBoard, opponentBoard]`), `Board` (L832, `grid[rowIndex][columnIndex]`, always complete), `Player` (L850, `required: [displayName, ready, connected, shipsRemaining]`), `Ship` (L803), `Shot` (L866), `Side` (L757), `CellState` (L789)
  - **Files** `application/projection/{SnapshotView,BoardView,PlayerView,ShotView,SnapshotContext,SnapshotProjector}.java`, `application/src/test/java/.../projection/ProjectionPrivacyTest.java`
  - **Proof** `ProjectionPrivacyTest` — the owner of **proof area 3**:
    1. **The headline assertion.** Build two `GameState`s that differ **only** in the opponent's undiscovered ship placement, project both for that opponent in `PLACEMENT`, `PLAYING` and `ABANDONED`, and assert the two `SnapshotView`s are **equal**. Their JSON serializations are asserted byte-identical in T025, once a serializer exists. Repeat after a `HIT` that does not sink: still identical apart from the hit cell.
    2. Under `FINISHED`, the same two states project **differently** — the full board is now disclosed on purpose.
    3. The caller's own board contains **no `UNKNOWN` cell** in any phase from `PLACEMENT` on, and lists the entire fleet including unplaced ships (invariant 10, R20).
    4. The opponent grid's cell states are a subset of `{UNKNOWN, MISS, HIT, SUNK, REVEALED_WATER}` before `FINISHED`, and `opponentBoard.ships` contains only ships whose status is `SUNK`.
    5. `ABANDONED` discloses nothing of the opponent's placement while the caller still sees their own board.
    6. `Seat` never appears: every `Side` in the view is `YOU` or `OPPONENT`, and the same `GameState` projected for `HOST` and for `GUEST` swaps them.
    7. `shipsRemaining` equals fleet size minus sunk ships for both players, at three points of a game.
    8. `invitationUrl` / `invitationExpiresAt` are present for the host in `WAITING` and absent for the guest, in every other phase, and for both once a guest joins.
    9. `allowedActions` equals `AllowedActions.of(state, seat)` exactly — the projector adds and removes nothing.
    10. Optionality: `opponent` absent before a guest joins; `turn` present exactly in `PLAYING`; `outcome` present exactly in `FINISHED`.
  - **Verify** `cd backend && ./mvnw -q -pl application -am test -Dtest=ProjectionPrivacyTest`
  - **Mutation** Include unsunk opponent ships in `opponentBoard.ships`; assertions (1) and (4) must fail. Emit `SHIP` for an undiscovered opponent cell; assertion (1) must fail.
  - **Depends on** T013, T014

- [ ] T016 [US6] Project the finished game's statistics in `backend/application/src/main/java/ua/kostenko/battleship/application/projection/StatisticsView.java`
  - **Delivers** `StatisticsView` (and with it the `MatchStatistics`, `PlayerStatistics`, `DurationAggregate` and `FleetSummary` shapes) computed from `Timeline` and the accepted transitions only — never re-measured, never taken from a second clock. Present **exactly** in `FINISHED`; **absent entirely** for `ABANDONED`. The boundaries are data-model.md § *Statistics* and are not restated in code comments:

    | Field | Boundary |
    |---|---|
    | `match.totalDurationMs` | `guestJoinedAt` to `finishedAt` |
    | `match.placementDurationMs` | `guestJoinedAt` to `playStartedAt` |
    | `match.gameplayDurationMs` | `playStartedAt` to `finishedAt` |
    | `you/opponent.placementDurationMs` | `guestJoinedAt` to `readyAt[seat]` |
    | `shots` | accepted `FIRE` commands by that seat |
    | `hits` | those whose result was `HIT` or `SUNK` |
    | `accuracy` | `hits / shots`, 4 decimals; **absent** when `shots` is 0 |
    | `turns` | one sample per turn: from gaining the right to fire until it passes or the game ends |
    | `shotDecisions` | one sample per accepted `FIRE`: from being able to fire until the shot |
    | `fleet` | that seat's own fleet at `finishedAt`: `intact` = no hits, `damaged` = some hits not sunk, `sunk` = all cells hit |

    `averageMs` is `round(totalMs / count)`; `fastestMs`, `slowestMs` and `averageMs` are **absent** when `count` is 0.
  - **Covers** R51 · **proof area 6 co-owner** · data-model.md § *Statistics*
  - **Read first** spec.md R51 (the spec adds only that the contract's identities must hold in the delivered values, that an abandoned game carries no statistics at all, and that a client formats and never recomputes them) · data-model.md § *Statistics* · `contracts/openapi.yaml` `GameStatistics` (L984), `MatchStatistics` (L967, `totalDurationMs = placementDurationMs + gameplayDurationMs`), `PlayerStatistics` (L932, `accuracy` optional `number` 0–1, `shotDecisions.count` equals `shots`), `DurationAggregate` (L894, `averageMs`/`fastestMs`/`slowestMs` optional, absent when `count` is 0), `FleetSummary` (L915, `intact + damaged + sunk = total`)
  - **Files** `application/projection/StatisticsView.java`, `application/projection/SnapshotProjector.java` (wire-in), `application/src/test/java/.../projection/StatisticsTest.java`
  - **Proof** `StatisticsTest` — a whole game played through `GameRules` with a `MutableTimeSource` advanced by known amounts, so every duration is an exact expected number:
    1. **The four identities the contract asserts** — `total = placement + gameplay`; `you.turns.totalMs + opponent.turns.totalMs = match.gameplayDurationMs`; `shotDecisions.count = shots`; `intact + damaged + sunk = total`.
    2. Each of the four match/player durations equals its boundary pair to the millisecond.
    3. `accuracy` is `hits / shots` to 4 decimals, and is **absent** when `shots` is 0.
    4. `averageMs = round(totalMs / count)`; all three of `averageMs`, `fastestMs`, `slowestMs` are **absent** when `count` is 0.
    5. The holder's open turn is closed at `finishedAt` — the turn totals still satisfy identity 2 when the game ends mid-turn, for both a fleet-destroyed and a resignation ending.
    6. A refused `FIRE` does not reset the shot-decision clock and does not count toward `shots`.
    7. `statistics` is **absent entirely** for `ABANDONED` and for every non-`FINISHED` phase.
  - **Verify** `cd backend && ./mvnw -q -pl application -am test -Dtest=StatisticsTest`
  - **Mutation** Measure `match.totalDurationMs` from game creation instead of `guestJoinedAt`; identity 1 must fail.
  - **Depends on** T015

- [ ] T017 Build the registry behind a port in `backend/application/src/main/java/ua/kostenko/battleship/application/port/GameSlotStore.java` and `backend/app/src/main/java/ua/kostenko/battleship/app/registry/`
  - **Delivers** **Decision A1** (see § *Decisions this task list settles*). `GameSlotStore` in `application/port` — `<T> T withSlot(GameId, Function<Slot,T>)`, `insert`, `remove` — is how `application` use cases reach state that lives in `app`. `GameRegistry` in `app/registry` implements it; `InMemoryGameSlotStore` is the test double. `plan.md` § *Ports* already lists five and states why (Decision A1 is applied, not pending).
    **`Slot` is part of this port and is delivered here** — the interface, also in `application/port`, that `withSlot` hands its function. It is the only shape an `application` use case ever sees of a game's mutable state; its members are [data-model.md](data-model.md) § *Registry state*, the `Slot` row. Those are: `GameState state()` and `replace(GameState)`; `Set<UUID> acceptedCommandIds()`; the four deadlines and `Map<Seat, Instant> presenceNotBefore()`; the session and invitation digests, `unusedInvitationSecret?()` and the setters T018–T021 need; and **`SnapshotContext contextFor(Seat, Instant now)`**, which is how an `application` use case obtains the projector's context without naming an `app` type. `GameSlot` implements `Slot`; `SnapshotContextFactory` (T025) is `GameSlot`'s implementation of `contextFor`, so research.md D27 still holds — the projector never sees `GameSlot`. Because `GameState` and `SnapshotContext` are both immutable records, a use case captures the pair inside `withSlot` and projects **after** the lock is released (plan.md § *Command path*, § *Ports*).
    `GameRegistry` holds `ConcurrentHashMap<GameId, GameSlot>` plus **two `Semaphore`s** sized from `max-concurrent-games` and `max-concurrent-streams`. A permit is acquired **before** insertion and released **exactly once** on removal; `map.size()` is never admission control (research.md D8). Over a ceiling the answer is a refusal carrying a retry hint; **nothing running is evicted** and readiness stays true (R39).
    `GameSlot`'s fields are data-model.md § *Registry state*: `ReentrantLock` (never `synchronized` — it pins virtual threads, research.md D7), `GameState`, `hostSessionDigest`, `guestSessionDigest?`, `invitationDigest?`, `unusedInvitationSecret?`, `Set<UUID> acceptedCommandIds`, `idleDeadline`, `absoluteDeadline`, `invitationDeadline`, `Map<Seat, Instant> presenceNotBefore`, `terminalRetentionDeadline?`.
    **Not delivered here**: the `Map<Seat, Subscriber>` field data-model.md § *Registry state* also lists. `Subscriber` does not exist until **T029**, the first task that has one; T029 adds the type and the field together (Constitution IX — no port or field ahead of its first real consumer).
    `SessionRegistry` maps a **SHA-256 digest** to a `SessionRecord` (`digest`, `createdAt`, `lastSeenAt`, `Set<GameId> liveGames`). **Only the digest is stored**, so a copy of server memory yields no usable session (R32). Bounded by a hard cap with **drop-least-recently-used** — a flood of invented keys never locks a real player out (R41).
    `unusedInvitationSecret` is the only plaintext secret held, permitted by Constitution II so the host's `WAITING` snapshot can carry `invitationUrl`; it is dropped the instant the invitation is used, replaced or expires.
  - **Covers** R32 (digest-only storage), R39, R41 · data-model.md § *Registry state* · research.md D7, D8, D15 · **Decision A1**
  - **Read first** plan.md § *Registry and slots* · data-model.md § *Registry state* · spec.md R32, R39, R41 · research.md D7, D8, D15 · Constitution II (the `unusedInvitationSecret` exception)
  - **Files** `application/port/{GameSlotStore,Slot}.java`, `app/registry/{GameRegistry,GameSlot,SessionRegistry,SessionRecord}.java`, `app/src/test/java/.../registry/GameRegistryTest.java`, `application/src/test/java/.../InMemoryGameSlotStore.java`
  - **Proof** `GameRegistryTest` — (1) a permit is acquired before insertion: with `max-concurrent-games=1`, a second insert is refused **and the map still holds exactly the first game**; (2) a permit is released exactly once — 10 000 insert/remove cycles at ceiling 1 never leak a permit and never double-release (a second `remove` of the same id is a no-op); (3) **nothing is evicted** to make room — the running game is still readable and unchanged after the refusal; (4) the same for the stream semaphore; (5) session values are never stored — the registry's contents contain the SHA-256 digest and not the value, for a value the test knows; (6) LRU: fill `SessionRegistry` past its cap and assert the **least recently seen** record is the one dropped and the most recently seen survives; (7) `withSlot` holds the `ReentrantLock` for the duration of the function and releases it on a thrown exception; (8) no `synchronized` **method** exists in `app/registry` — an ArchUnit rule over the `SYNCHRONIZED` modifier. ArchUnit cannot see `synchronized` **blocks** (they are bytecode monitor instructions, absent from its class model), so no assertion is claimed for them; the no-`synchronized`-block rule stands as a stated convention of this task's **Delivers**, enforced by review. (9) `withSlot` hands its function a `Slot`, never a `GameSlot`: the compiled signature of `GameSlotStore.withSlot` names only `application` types.
  - **Verify** `cd backend && ./mvnw -q -pl app -am test -Dtest=GameRegistryTest`
  - **Mutation** Replace the semaphore with `map.size() < limit`; assertion (2)'s concurrent variant must fail. Store the session value instead of its digest; assertion (5) must fail. Declare one `app/registry` method `synchronized`; assertion (8) must fail.
  - **Depends on** T006, T014

- [ ] T018 [US1] [US2] Create, join and re-invite in `backend/application/src/main/java/ua/kostenko/battleship/application/usecase/`
  - **Delivers** Three use cases, each complete.
    `CreateGameUseCase` — normalises the display name (see below), mints a game id and an invitation secret from `SecretGenerator`, stores the invitation's **digest** plus the `unusedInvitationSecret`, issues an anonymous session when the browser presented none and reuses it otherwise, records `hostSessionDigest`, sets `idleDeadline` and `absoluteDeadline` from creation and `invitationDeadline` from `invitation-lifetime-seconds`, and adds the game to `SessionRecord.liveGames`. Refuses `service-unavailable` when `SessionRecord.liveGames` already holds `max-live-games-per-browser` entries (R62) and when the games semaphore is exhausted (R39).
    `JoinGameUseCase` — runs **one order under the slot lock** (R34): **membership first**, then expiry, then a **constant-time** compare of the invitation digest and its **atomic** consumption. A browser that already holds this game's guest seat receives the current state **whatever secret it presents**, so a reload after the host replaced the invitation never locks the guest out of their own game. **Every** other refusal — unknown game, wrong / used / replaced / expired secret, seat taken, or the host itself — returns the one answer `invitation-unavailable`. On success it applies `GameState.withGuest`, drops `unusedInvitationSecret`, records `guestSessionDigest`, moves the idle deadline, and adds the game to the guest's `liveGames`. Same per-browser and capacity refusals as create.
    `ReplaceInvitationUseCase` — host only, `WAITING` only; mints a new secret and digest, and **the previous link stops working the instant the new one is issued**. Replacing does **not** move the idle deadline (R42).
    **Display-name normalisation (R64)**, in `domain/model/DisplayNameNormalizer`: trim surrounding whitespace, normalise to Unicode **NFC**, reject control characters, then count length **in code points after normalisation** and require `minLength: 1, maxLength: 32`. A name that fails any of these is a field-level validation failure and **no game or seat is created**.
  - **Covers** R32 (session issue and reuse), R34, R42/R43 (the deadlines these paths set), R45, R62, R64 · data-model.md § *Registry state*
  - **Read first** spec.md R32, R33, R34, R45, R62, R64 · plan.md § *Adapters* (`joinGame` answers 200 for a browser that already holds the guest seat — the membership-first order R34 requires), § *Request security* → *Join ordering*, → *Per-browser cap* · `contracts/openapi.yaml` `createGame` (201 + `Location` + `Set-Cookie`), `joinGame` (200; repeated join by the existing guest → 200; every other refusal → `409 invitation-unavailable`), `CreateGameRequest` (L1199), `JoinGameRequest` (L1209), `DisplayName` (L725: `minLength: 1`, `maxLength: 32`, no pattern; description states trim + NFC + control-character rejection + code-point counting), `GameSnapshot.invitationUrl` (shape `{site}/join/{gameId}#invite={invitationSecret}`)
  - **Files** `application/usecase/{CreateGameUseCase,JoinGameUseCase,ReplaceInvitationUseCase}.java`, `domain/model/DisplayNameNormalizer.java`, `application/src/test/java/.../usecase/{JoinOrderingTest,InvitationLifecycleTest,DisplayNameNormalizerTest}.java`
  - **Proof**
    - `JoinOrderingTest` — (1) a fresh, unused, unexpired invitation is redeemed once: `PLACEMENT`, both names visible, `guestJoinedAt` set; (2) the **same** invitation presented by a third browser is refused **identically to a wrong secret** — the same `ProblemCode` and the same refusal payload, asserted field by field. `application` has no HTTP, so this is asserted in `ProblemCode` terms; the status-code half is T026 `JoinIT` (4); (3) the **six** refusal paths (unknown game, wrong secret, used secret, replaced secret, expired secret, the host itself) all produce the identical `invitation-unavailable` answer; (4) **membership beats the secret**: the existing guest re-joining with a stale, replaced or garbage secret gets the current state and nothing changes; (5) consumption is atomic — two threads on a `CyclicBarrier` redeeming the same secret produce exactly one guest and one refusal; (6) a link opened but not confirmed claims no seat and leaves the invitation usable.
    - `InvitationLifecycleTest` — (1) replacement invalidates the previous secret **immediately**; (2) `unusedInvitationSecret` is dropped on use, on replacement and on expiry, and is never present once a guest has joined; (3) the invitation expires at `invitation-lifetime-seconds` **or** when its game expires, whichever comes first (advanced via `MutableTimeSource`, inclusive boundary); (4) replacement does not move the idle deadline; (5) per-browser cap — with `max-live-games-per-browser=1`, a second create and a second join are both refused `service-unavailable`, and a **finished** game no longer counts.
    - `DisplayNameNormalizerTest` — trimming; NFC (a decomposed accented letter normalises and counts as one code point); control characters rejected (NUL, U+001F, U+007F); length counted in code points **after** normalisation, so a 32-code-point name containing astral characters is accepted and 33 is refused; empty after trimming is refused; each failure yields a field-level validation failure and no game.
  - **Verify** `cd backend && ./mvnw -q -pl application -am test -Dtest='JoinOrderingTest,InvitationLifecycleTest,DisplayNameNormalizerTest'`
  - **Mutation** Check the secret before membership; `JoinOrderingTest` assertion (4) must fail. Give the "seat taken" case its own code; assertion (3) must fail.
  - **Depends on** T017, T015
  - **Note** The dependency on **T015** is real, not bookkeeping: all three use cases here project a snapshot for their caller, so `SnapshotProjector` and `SnapshotView` must exist. It transitively closes T019 (which dispatches all seven commands and so needs `AllowedActions` from T013, reached through T015) and T021.

- [ ] T019 [US5] Serialize every action through one indivisible step in `backend/application/src/main/java/ua/kostenko/battleship/application/usecase/CommandUseCase.java`
  - **Delivers** The command path of plan.md § *Command path*. Under the slot lock, **in one indivisible step**: expiry check, membership, `commandId` seen?, the pure `GameRules.apply` transition, the state replacement, the version bump, and the record of which seats' views changed. **No projection, JSON write, log sink or SSE write happens while the lock is held**; the caller's snapshot and the per-seat deliveries are produced after it is released. What the function returns from inside `withSlot` is the pair (`GameState`, `SnapshotContext contextFor(seat, now)`) — both immutable records, so capturing them costs no projection — and the per-seat `SnapshotProjector.project` calls run on that captured pair once the lock is gone (T017's `Slot`; plan.md § *Ports*). Different games never contend — `withSlot` locks one slot, never a shared structure.
    **Idempotency** is the `Set<UUID> acceptedCommandIds` **alone**. R24 returns the *current* state on a repeat, so no result is stored. Only the `commandId` is compared, never the rest of the request. An accepted identifier is remembered for the whole life of its game; the set is bounded **by construction** — 60 actions/min times the 2 h absolute ceiling — not by a window.
    A **refused** action changes nothing: state and version are exactly what they were (R15), and its `commandId` is **not** recorded. An **accepted** action that changes no view (R23) moves the idle deadline and records its `commandId`, but bumps no version and pushes to nobody.
    `Transition.viewChanged` carries the seats whose **view** changed in the R18 sense: the snapshot excluding `serverTime` and `expiresAt`.
  - **Covers** R15, R18, R23, R24, R25 · S4, S7 · **proof areas 4 and 5 owners** · research.md D9
  - **Read first** spec.md R24, R25, R23, R18, R15 and § *Proof required* areas 4 and 5 · plan.md § *Command path* · research.md D7, D9 · data-model.md invariant 7 · `contracts/openapi.yaml` `CommandRequest` (L1294, `required: [commandId, command]`, `commandId` `format: uuid`, `additionalProperties: false`; "only `commandId` is compared, not the rest of the body"), `sendCommand` description
  - **Files** `application/usecase/CommandUseCase.java`, `application/src/test/java/.../usecase/{CommandIdempotencyTest,GameSerializationTest}.java`
  - **Proof**
    - `CommandIdempotencyTest` — owner of **proof area 4**: (1) a re-sent `commandId` returns the **current** state and applies nothing — proven immediately, and again **40 accepted actions later**, so the memory is not a window (S4); (2) a **different** `commandId` for the same intent applies again where the rules allow it (a second `PLACE_SHIP` at a new anchor) and is refused where they do not (a second `READY`); (3) only the `commandId` is compared — a repeat carrying a *different* command body still returns the current state and applies nothing; (4) a **refused** action's `commandId` is not recorded, so re-sending it later reaches the rules again; (5) a retried action arriving **after** the game finished returns the current state and re-applies nothing, while a **fresh** action for a finished game is refused; (6) an accepted action that changes no view (re-placing a ship where it is) records its `commandId` and moves the idle deadline but does **not** bump the version.
    - `GameSerializationTest` — owner of **proof area 5**, staged with a `CyclicBarrier` and asserting the **set of allowed outcomes, not one scheduling**: (1) two simultaneous `FIRE`s on one game produce one ordered outcome, no lost update, and no partially applied state observable by either player (S7); (2) both players declaring `READY` at the same instant start play **once** and settle the first turn **once** — both callers see the same starting state; (3) actions on **different** games do not block one another (two games, barriered, both complete); (4) after every concurrent run, `version` rose by exactly the number of view-changing transitions — never more, never less (invariant 7); (5) **no projection or serialization happens while the slot lock is held** — asserted by instrumenting `SnapshotProjector` (and the `InMemoryGameSlotStore` double) to fail the test the moment either is entered under the lock. There is no publisher here: `CommandUseCase` returns the transition result carrying `viewChanged`, and the publish call is wired in **T029**, its first real consumer (Constitution IX — no port ahead of one, and an `application` port with no production bean would break the Spring context for T025-T028).
  - **Verify** `cd backend && ./mvnw -q -pl application -am test -Dtest='CommandIdempotencyTest,GameSerializationTest'`
  - **Mutation** Record the `commandId` before the transition so refusals are remembered; `CommandIdempotencyTest` assertion (4) must fail. Project the snapshot inside `withSlot`; `GameSerializationTest` assertion (5) must fail.
  - **Depends on** T018

- [ ] T020 [US5] Enforce every deadline and the presence throttle in `backend/application/src/main/java/ua/kostenko/battleship/application/usecase/`
  - **Delivers** One shared **inclusive** comparison — `deadline <= now` means expired — applied to every deadline (research.md D13). Expiry is checked on **every access to a slot, before anything else**, and a sweeper runs every `sweep-interval-seconds` **purely to reclaim memory**, so a late request can never resurrect a game (R47). The sweeper's whole body is a **package-visible `tick()`** that reads the injected `TimeSource`; `@Scheduled` calls `tick()` and does nothing else. That is what makes `ExpirySweeperTest` provable without sleeping: it advances the `MutableTimeSource` and calls `tick()` directly, never waiting on a scheduler (R60; plan.md § *Realtime*, § *Lifetimes*).
    **Idle deadline (R42)**: moved **only** by creation, the guest joining, an accepted action and an accepted presence signal — including an accepted action that changes nothing (R23). Reads, opening or reconnecting an event stream, replacing the invitation, keep-alive markers and refused actions **never** move it.
    **Absolute ceiling (R43)**: a game in progress never plays past `max-game-duration-seconds` from creation, whatever activity it sees. The ceiling governs play, not the result: a game that finishes just before it is reached still keeps its result for the full retention period.
    **Terminal retention (R44, R63)**: `terminalRetentionDeadline` is set from the instant the game **ended or expired, whichever happened**. An expired slot answers `game-expired` (410) to its **own two session digests** and `game-unavailable` (404) to every other caller; after `terminalRetentionDeadline` its own players get 404 too — so an expiry never confirms to a stranger that the game was real.
    **`PresenceUseCase` (R46)**: each player may extend the idle deadline by presence at most once per `presence-interval-seconds`, and the two players are throttled **independently** via `Map<Seat, Instant> presenceNotBefore`, so one player's signal never absorbs the other's. A call that comes sooner returns the current state and changes nothing, **including the version**. An accepted signal moves the deadline — and because the deadline is not part of a view (R18), that too leaves the version alone; the caller still receives a fresh `expiresAt` in its own answer.
    **All state is in memory (R48)**: a restart ends every game and every session. There is no persistence, no recovery and no claim of continuity.
  - **Covers** R42, R43, R44, R45 (the game-expiry half), R46, R47, R48, R63 · S5 · **proof area 6 owner** · research.md D13
  - **Read first** spec.md § *Lifetimes* preamble (every deadline is reached **inclusively**) and R42–R48, R63, R46, and § *Proof required* area 6 · plan.md § *Lifetimes* · research.md D13 · `contracts/openapi.yaml` `Gone` (410 `game-expired`), `NotFound` (404 `game-unavailable`), `sendPresence`
  - **Files** `application/usecase/{PresenceUseCase,ExpiryPolicy}.java`, `app/registry/ExpirySweeper.java`, `application/src/test/java/.../usecase/{LifetimeBoundaryTest,PresenceThrottleTest}.java`, `app/src/test/java/.../registry/ExpirySweeperTest.java`
  - **Proof**
    - `LifetimeBoundaryTest` — owner of **proof area 6**, every assertion driven by advancing `MutableTimeSource`, **never by sleeping** (R60). Each of the six deadlines is proven **at its inclusive boundary: at `deadline - 1ms` the game is alive, at `deadline` it has expired**: (1) idle lifetime; (2) absolute game lifetime; (3) result retention; (4) invitation lifetime; (5) presence throttle; (6) stream lifetime (the policy; T031 proves the stream behaviour). Plus: (7) each of the four idle-moving events moves the deadline and each of the five non-moving events does not, asserted one by one; (8) a game that finishes just before the absolute ceiling keeps its result for the **full** retention period from `finishedAt`; (9) R63's three-way answer, in `ProblemCode` terms because `application` has no HTTP — an expired game answers `game-expired` to each of its own two digests, `game-unavailable` to a third session, and `game-unavailable` to its own two after `terminalRetentionDeadline` (T025/T027 assert the 410/404 mapping over the wire); (10) a request arriving **after** expiry but **before** the sweep still gets the expired answer, never a resurrected game (S5) — expiry is decided by the deadline comparison, never by whether the sweep has run.
    - `ExpirySweeperTest` (`app`) — the half of the sweeper that needs the **real** `GameRegistry` and its semaphores, which the `InMemoryGameSlotStore` double does not have, which is why it cannot live in `LifetimeBoundaryTest`. Every assertion advances the `MutableTimeSource` and calls `ExpirySweeper.tick()` directly; none waits on `@Scheduled`: (1) the sweeper reclaims an expired slot **and releases its semaphore permit** — proven by inserting again at `max-concurrent-games=1` immediately afterwards and succeeding; (2) a slot that has not expired is left untouched by a sweep; (3) the permit is released **exactly once** — a slot swept twice does not leak a second permit; (4) the sweep is memory reclamation only: it changes no answer a request would have received either side of it.
    - `PresenceThrottleTest` — (1) an accepted signal moves the idle deadline and leaves the version unchanged; (2) a second signal from the same seat before `presence-interval-seconds` returns the current state and changes nothing including the version; (3) the two seats throttle **independently** — the guest's signal is accepted while the host is still throttled; (4) the throttle boundary is inclusive; (5) the caller's own answer carries a fresh `expiresAt` even when nothing else changed.
  - **Verify** `cd backend && ./mvnw -q -pl application,app -am test -Dtest='LifetimeBoundaryTest,PresenceThrottleTest,ExpirySweeperTest'` — **both** modules, because this task creates a file in each and a gate that never builds one of them is not a gate
  - **Mutation** Change the comparison to exclusive (`deadline < now`); every boundary assertion's "at `deadline`" case must fail. Share one `presenceNotBefore` across both seats; `PresenceThrottleTest` assertion (3) must fail. Skip the permit release in the sweeper; `ExpirySweeperTest` assertion (1) must fail. Read `Instant.now()` inside `tick()` instead of the injected `TimeSource`; every `ExpirySweeperTest` assertion must fail.
  - **Depends on** T019

- [ ] T021 [US6] Implement leaving in `backend/application/src/main/java/ua/kostenko/battleship/application/usecase/LeaveGameUseCase.java`
  - **Delivers** Four branches, and nothing else (R16):
    - **`WAITING`** — the host leaves: the game is **removed outright**, not shown as abandoned. `ABANDONED` is reachable only from `PLACEMENT` (data-model.md § *State machine*).
    - **`PLACEMENT`** — either player leaves: the state is replaced with `ABANDONED`, **nothing is revealed** (neither placement, ever), and no statistics are produced.
    - **`PLAYING`** — refused `action-not-allowed`, because `LEAVE` is never in `allowedActions` then (T013) — which is what makes this refusal consistent with R13 rather than circular. `RESIGN` stays offered to both players throughout play, so the game always ends with a stated winner rather than vanishing.
    - **`FINISHED` / `ABANDONED`** — clears **only that seat's** session digest from the slot, so that browser reads `game-unavailable` from then on, **including for a repeated leave**. The other player's result is untouched and stays readable for the full retention period.
    In every branch that ends the caller's access, the game id leaves `SessionRecord.liveGames` **at once**, freeing the per-browser allocation (R62).
  - **Covers** R16, R62 (the release half), R12 (`PLACEMENT` to `ABANDONED`), R19 (an abandoned game discloses nothing) · data-model.md § *State machine*
  - **Read first** spec.md R16, R12, R19, R62 · plan.md § *Request security* → *Leaving* · data-model.md § *State machine* · `contracts/openapi.yaml` `leaveGame` (204 no content; during `PLAYING` → `409 action-not-allowed`; repeated leave → 404)
  - **Files** `application/usecase/LeaveGameUseCase.java`, `application/src/test/java/.../usecase/LeaveGameTest.java`
  - **Proof** `LeaveGameTest` — every outcome stated in `ProblemCode` terms, because `application` has no HTTP; the status mapping is T027 `CommandControllerIT` (7). (1) `WAITING` host leave removes the game: the slot is gone, the semaphore permit released, and the host now reads `game-unavailable`; (2) `PLACEMENT` leave by **each** player in turn yields `ABANDONED`, and projecting it for either seat reveals **no part** of the opponent's placement and carries no statistics; (3) `PLAYING` leave is refused `action-not-allowed` for **both** seats, with the state returned by identity and the version untouched, while `RESIGN` is accepted for both; (4) after `FINISHED`, one player's leave **succeeds with no payload**, that browser then reads `game-unavailable` **including on a repeated leave**, and the **other** player still reads the full result; (5) the game id leaves `liveGames` in every branch that ends access, and a browser at `max-live-games-per-browser` can immediately create a new game afterwards.
  - **Verify** `cd backend && ./mvnw -q -pl application -am test -Dtest=LeaveGameTest`
  - **Mutation** Move a `WAITING` game to `ABANDONED` instead of removing it; assertion (1) must fail. Clear both digests on a post-game leave; assertion (4)'s "other player" case must fail.
  - **Depends on** T020

**Checkpoint**: proof areas 3, 4, 5 and 6 are owned and green. A whole game runs through the use cases
with a mutable clock and a seeded random source, in milliseconds, with no sleeps. `application` still
references only `domain` and the JDK — `ArchitectureTest` proves it.

---

## Phase 4: Web adapters, problems, session and CSRF

**Purpose**: the contract's **ten synchronous operations** over HTTP, and the one document that goes
on the wire. The eleventh, `streamGameEvents`, is Phase 5 (T029) — it needs the SSE hub.
**Proof areas carried**: 7 (request security and authorization), and the groundwork for 10.

**Ordering note.** plan.md § *Forecast* groups security into phase 6. This list moves the **session and
CSRF filters ahead of the game controllers** (T024 before T025), because `getGame` and `sendCommand`
cannot be delivered working without them and no task may depend on a later one. Rate limiting, site
isolation, body size, logging, packaging and shutdown stay in Phase 6, where nothing depends on them.

**Shared contract for every task in this phase.** Every controller that answers with a game returns the
**generated** `GameSnapshot` DTO, assembled from the projector's `SnapshotView` by
`SnapshotDtoAssembler` (T025). Inbound bodies — `CreateGameRequest`, `JoinGameRequest`,
`CommandRequest` and the four `Command` variants — are the **generated** request types, so Bean
Validation and Jackson bind straight onto them. Hand-writing any wire type is a contract violation
(Constitution VI; Decision A3).

**Checkpoint**: a whole game is playable over HTTP with `curl` exactly as quickstart.md § *Smoke*
describes.

- [ ] T022 Map every failure to one problem document in `backend/app/src/main/java/ua/kostenko/battleship/app/web/ProblemAdvice.java`
  - **Delivers** **Decision A3**: the wire type is the **generated `Problem`**, not a hand-written `ProblemDto`. `research.md` D19 and `plan.md` § *Failures* already say so (A3 is applied, not pending). D19's actual rejection — Spring's `ProblemDetail`, whose `type`/`instance` defaults and dynamic properties the contract does not define — still stands and is not reintroduced.
    One `@RestControllerAdvice` maps every outcome to a `Problem` served as `application/problem+json`, using **exactly** the contract's code-to-status table (R49), all seventeen rows:

    | Code | Status |
    |---|---|
    | `malformed-request` | 400 |
    | `session-required` | 401 |
    | `request-security-rejected` | 403 |
    | `game-unavailable` | 404 |
    | `invitation-unavailable`, `action-not-allowed`, `placement-out-of-bounds`, `placement-overlap`, `placement-touching`, `target-already-fired` | 409 |
    | `game-expired` | 410 |
    | `payload-too-large` | 413 |
    | `unsupported-media-type` | 415 |
    | `validation-failed` | 422 |
    | `rate-limit-exceeded` | 429 |
    | `internal-error` | 500 |
    | `service-unavailable` | 503 |

    `getHealth` is the only operation whose unavailable answer is **not** a problem document — its 503 carries a `Health` body (research.md D20); the advice must not intercept it.
    A `CorrelationIdFilter` puts a **16-hex** id in MDC and into every problem body as `correlationId`. **No secret, cookie, board, request body, exception message or stack trace ever reaches a problem document** (R50). The human-readable `title` is English developer text; a client localizes by `code`.
    Jackson is configured `FAIL_ON_UNKNOWN_PROPERTIES=true`, so a caller can never supply a seat, a turn, a winner or a result (R37). Unknown fields and Bean Validation failures both map to `422 validation-failed` with one `Violation{field: JSON Pointer, rule}` each; malformed JSON maps to `400 malformed-request`. **Every** response carries `Cache-Control: no-store`.
  - **Covers** R37, R49, R50 · **Decision A3** · research.md D19 (amended), D20, D29
  - **Read first** spec.md R37, R49, R50 · plan.md § *Failures*, § *Request security* (last paragraph) · research.md D19, D29 (closed Java enums are correct here: this is the server, it authors every value, and an unknown value on a *request* must be refused 422) · `contracts/openapi.yaml` `components.responses` (all ten), `Problem` (L1345, `required: [title, status, code]`; optional `type`, `detail`, `correlationId` `maxLength: 64`, `violations`, `retryAfterSeconds` `minimum: 1`), `Violation` (L1315), `ProblemCode` (L1325, the 17 values)
  - **Files** `app/web/ProblemAdvice.java`, `app/web/JacksonConfig.java`, `app/web/CacheControlFilter.java`, `app/observability/CorrelationIdFilter.java`, `app/src/test/java/.../web/ProblemMappingIT.java`, `app/src/test/java/.../web/ProblemProbeController.java`
    **`ProblemProbeController` is a test fixture, not a mock of the system under test.** No contract endpoint exists yet — T023–T027 and T036 deliver them — so the probe is what gives the advice something to advise on. It is a `@TestConfiguration`-registered controller, visible only to `ProblemMappingIT`, with one path per application failure it must raise and one path that binds a generated `CreateGameRequest` body (available since T002). The **system under test** is the real `ProblemAdvice` + `JacksonConfig` + `CacheControlFilter` + `CorrelationIdFilter`; the probe only supplies the throw sites and a body to bind. Once the real controllers exist, T038 `WireConformanceIT` re-asserts every failure body against the contract's response components over the real operations, so the probe is never the last word on any of them.
  - **Proof** `ProblemMappingIT` — (1) each of the **17** codes is produced by a scenario or a directly thrown application failure and answers with its table status and `application/problem+json`; (2) an unknown field in each of `CreateGameRequest`, `JoinGameRequest` and `CommandRequest` yields 422 with rule `UNKNOWN_FIELD` and a JSON Pointer naming that field; (3) a Bean Validation failure yields 422 with the right rule and pointer (`/command/anchor/rowIndex` for an out-of-range coordinate); (4) malformed JSON yields 400 `malformed-request`; (5) every problem body carries a 16-hex `correlationId` that also appears in the log record for that request; (6) **redaction** — a failure triggered by a request carrying a session cookie, an invitation secret and a full board finds none of those three strings, nor any stack frame or exception class name, anywhere in the response body; (7) every response, success and failure alike, carries `Cache-Control: no-store`. Assertions (2), (3), (4) and (6) run against `ProblemProbeController`'s paths, which is the only place a request body can be bound at this point in the list.
    Not asserted here: the `getHealth` 503 exception — that endpoint does not exist until T023 and cannot answer 503 until T036, which is where that assertion lives. T023 asserts the cheap counterpart (health 200 is a `Health` body).
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=ProblemMappingIT`
  - **Mutation** Map `game-expired` to 404 instead of 410; assertion (1) must fail. Set `FAIL_ON_UNKNOWN_PROPERTIES=false`; assertion (2) must fail. Put `exception.getMessage()` into `detail`; assertion (6) must fail.
  - **Depends on** T002, T006

- [ ] T023 Serve service information, rulesets and health in `backend/app/src/main/java/ua/kostenko/battleship/app/web/`
  - **Delivers** The three unauthenticated operations (`security: []` in the contract — no session, no CSRF).
    `MetaController` → `GET /api/v1/meta` → `Meta{apiVersion, serverTime, limits}`. `apiVersion` matches `^1\.[0-9]+\.[0-9]+$` and is the contract version this server implements: the constant **`1.0.0`**, equal to `contracts/openapi.yaml` `info.version` (plan.md § *Adapters*). It is **not** a configuration key and is **not** parsed from the YAML at runtime — it is a `static final String` bumped by hand when the contract's `info.version` is. `serverTime` is `TimeSource.now()`. `limits` is read **straight off `BattleshipProperties`** — the first six keys are exactly the contract's `Limits` object, so a limit exists once and the published value is the enforced value (R55).
    `RulesetController` → `GET /api/v1/rulesets` → `RulesetList` built from the `domain/rules` constants (T007). Both rulesets with their board size, fleet composition and the three flags (R03).
    `HealthController` → `GET /api/v1/health` → `Health{live, ready, reason?}`, hand-written over an `ApplicationAvailability` probe. Actuator stays **off** the public surface (research.md D20). `reason` is `STARTING` or `DRAINING` and is present only when `ready` is false. **A service that is full is still ready** (R04, R39). The 503 form carries a `Health` body, not a problem document. T036 adds the DRAINING behaviour; this task delivers `live`/`ready` and `STARTING`.
  - **Covers** R01 (three of eleven operations), R02, R03, R04, R55 (the single-source half)
  - **Read first** spec.md R01, R02, R03, R04, R55 · plan.md § *Adapters* (first three rows), § *Configuration and observability* · research.md D20 · `contracts/openapi.yaml` `getMeta` (L98 — the `Set-Cookie` header it must emit is T024's), `listRulesets`, `getHealth` (200 and 503 both `Health`; no `default` response), `Meta` (L1175), `Limits` (L1147), `Ruleset` (L1103), `RulesetList` (L1139), `FleetEntry` (L1090), `Health` (L1187)
  - **Files** `app/web/{MetaController,RulesetController,HealthController}.java`, `app/src/test/java/.../web/MetaAndRulesetsIT.java`
  - **Proof** `MetaAndRulesetsIT` — (1) `GET /api/v1/meta` returns 200 with `apiVersion` matching `^1\.[0-9]+\.[0-9]+$` **and equal to exactly `1.0.0`** — the `info.version` of `contracts/openapi.yaml`, asserted as a literal so a contract version bump that the server does not follow fails here — a `serverTime` equal to the injected `TimeSource`, and all six limits equal to the running `BattleshipProperties` values; (2) starting with `BATTLESHIP_IDLETIMEOUTSECONDS=60` changes `meta.limits.idleTimeoutSeconds` to 60 with no code change (the publication half of S12; T037 adds the enforcement half); (3) `GET /api/v1/rulesets` returns both rulesets, asserted field by field against R05 — ids, board, fleet entries, the three flags; (4) `GET /api/v1/health` returns 200 `{"live":true,"ready":true}` once started, and omits `reason` — a `Health` body, **not** a problem document, which is the counterpart to the 503 form T036 asserts; (5) all three operations succeed with **no session cookie and no CSRF token**; (6) all three carry `Cache-Control: no-store`; (7) `getMeta` and `listRulesets` carry the contract's `default: Problem` response shape — forcing a failure on each (an unsupported method) yields the advice's `Problem` document with its correlation id, while `getHealth`, which declares **no** `default` response, is excluded from that assertion (research.md D20).
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=MetaAndRulesetsIT` — then `curl -s localhost:8080/api/v1/meta | jq .limits`
  - **Mutation** Hard-code `idleTimeoutSeconds: 900` in `MetaController` instead of reading the properties; assertion (2) must fail. This is the assertion that guards R55's "there is no second copy of a limit anywhere".
  - **Depends on** T022, T007

- [ ] T024 Build the security chain — session and anti-forgery — in `backend/app/src/main/java/ua/kostenko/battleship/app/security/`
  - **Delivers** One `SecurityFilterChain`: stateless, `permitAll` (authorization is the session filter's and the use cases' job, not a URL matcher's), and **CORS never configured at all** — the interface and the API share one origin (R36).
    **`SessionCookieFilter`** reads `__Host-battleship_session` (`HttpOnly; Secure; SameSite=Strict; Path=/`, **no `Domain`**, no expiry attribute, 256-bit value), digests it with SHA-256 and resolves the session against `SessionRegistry`. A missing, unknown or malformed cookie on a protected operation answers `401 session-required` **and clears the cookie**. `createGame` and `joinGame` **never** answer 401 — they issue a session instead (R32, R33).
    **Authorization (R33)**: a game identifier locates a game and grants nothing. Authentication is evaluated **first**. A caller with a valid session who is **not** one of a game's players receives the **same answer as for a game that never existed** — indistinguishable in status, body and timing-independent content.
    **CSRF (R35, research.md D26)**: `CookieCsrfTokenRepository` customised to the contract's cookie — name `XSRF-TOKEN`, `Path=/`, `Secure`, `SameSite=Strict`, **script-readable** (not `HttpOnly`, and **no** `__Host-` prefix) — paired with a **plain** `CsrfTokenRequestAttributeHandler` whose `csrfRequestAttributeName` is `null`. The plain handler is **required**: Spring's default `XorCsrfTokenRequestAttributeHandler` masks the token per response, so a client echoing the raw cookie value in `X-XSRF-TOKEN` — exactly what the contract prescribes — would be refused. `withHttpOnlyFalse()` alone sets neither `Secure` nor `SameSite`, so it is not enough. `MetaController` resolves the `CsrfToken` **eagerly** so `GET /api/v1/meta` always emits the cookie. A missing or wrong token answers `403 request-security-rejected`.
    Spring Security's `AccessDeniedHandler` is pointed at the **same** `Problem` writer as the advice (T022), so there is exactly one problem-document shape (R35, R49).
  - **Covers** R32, R33, R35 · **proof area 7** (the pre-dispatch half: session rejection and clearing, authentication before authorization, the anti-forgery check). S6 and session issuance are covered by T025/T027/T029, which grow `AuthorizationIT` · research.md D3, D26
  - **Read first** spec.md R32, R33, R35 and § *Proof required* area 7 · plan.md § *Request security* (the filter table and the paragraph on the plain handler) · research.md D3, D26 · `contracts/openapi.yaml` `securitySchemes.session` (L567, verbatim cookie attributes; "Create and join never answer 401: they issue a new session when the cookie is missing, unknown or malformed"), `securitySchemes.csrf` (L577, header `X-XSRF-TOKEN`, value of the readable `XSRF-TOKEN` cookie set by `GET /api/v1/meta`), `getMeta` `Set-Cookie` header (L98: `XSRF-TOKEN=<value>; Path=/; Secure; SameSite=Strict`), `Unauthorized` (401), `Forbidden` (403), `NotFound` (404)
  - **Files** `app/security/{SecurityConfig,SessionCookieFilter}.java`, `app/web/MetaController.java` (eager token resolution), `app/src/test/java/.../security/{AuthorizationIT,RequestSecurityIT}.java`
  - **Proof** Both classes are **grown across tasks**, the pattern this list already uses for `RequestSecurityIT` (T024/T033) and `EventStreamIT` (T029–T031). The controllers behind `getGame`, `sendCommand`, `streamGameEvents`, `replaceInvitation`, `sendPresence`, `leaveGame`, `createGame` and `joinGame` do not exist until T025–T027 and T029, so this task proves what the filter chain decides **before dispatch** — which is genuinely most of proof area 7's session-and-anti-forgery half. Each later task named below adds its own assertions to the same class.
    - `AuthorizationIT` part 1 of 4 (T025, T027 and T029 add parts 2–4) — everything the session filter answers without a controller, asserted against the six protected **paths** rather than their operations: (1) no session cookie on `GET /api/v1/games/{id}`, `POST …/commands`, `GET …/events`, `POST …/invitation`, `POST …/presence` and `POST …/leave` answers `401 session-required` **and** the response clears the cookie; the same for an unknown digest and for a malformed value; (2) the create and join paths are **never** 401'd for a missing cookie — they reach dispatch (T025 upgrades this to a real 201/200 and asserts the issued cookie's attributes); (3) authentication is evaluated **before** authorization: no session plus a `gameId` that never existed yields **401, not 404**; (4) the `XSRF-TOKEN` cookie and its attributes are present on `GET /meta`.
    - `RequestSecurityIT` part 1 of 2 (T033 adds part 2) — (1) `GET /api/v1/meta` always emits the `XSRF-TOKEN` cookie with `Path=/; Secure; SameSite=Strict` and **without** `HttpOnly`; (2) **the masking trap** — a POST echoing the **raw cookie value** in `X-XSRF-TOKEN` is **not** refused 403; this is the assertion that guards research.md D26, and T025 upgrades it to a real `201` once `createGame` exists; (3) a POST with a missing token, an empty token and a token from a different session each answer `403 request-security-rejected` with the same problem shape the advice produces, on **every** POST path; (4) CSRF applies to every POST path including the three body-less ones; (5) no `Access-Control-Allow-Origin` header is ever emitted, on any response, because CORS is not configured.
    **Not asserted here**: session **issuance** attributes and reuse across two games (T025), and **S6** — the indistinguishable answer for a non-player, which needs a real game to exist and every protected operation to answer. T027 owns S6 across the five protected synchronous operations; T029 adds the `streamGameEvents` row. `plan.md` § *Validation* is unchanged: `AuthorizationIT` remains S6's owning class.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test='AuthorizationIT,RequestSecurityIT'`
  - **Mutation** Replace the plain handler with Spring's default `XorCsrfTokenRequestAttributeHandler`; `RequestSecurityIT` assertion (2) must fail. Answer 404 for a missing session on an unknown game; `AuthorizationIT` assertion (3) must fail.
  - **Depends on** T023, T017

- [ ] T025 Assemble the snapshot and serve create and read in `backend/app/src/main/java/ua/kostenko/battleship/app/web/`
  - **Delivers** `SnapshotDtoAssembler` — a **mechanical 1:1 copy** of `SnapshotView` into the generated `GameSnapshot`, taking no decisions of its own. The mapping is total: `SnapshotView` to `GameSnapshot`, `BoardView` to `Board`, `PlayerView` to `Player`, `ShotView` to `Shot`, `StatisticsView` to `GameStatistics` and with it `MatchStatistics`, `PlayerStatistics`, `DurationAggregate`, `FleetSummary`. **That one document is what goes on the wire for HTTP and SSE alike** — there is no second representation of a game (R17). Because the DTOs are generated, a contract change regenerates them and **breaks this assembler's compilation**, which is what retires the wire-drift risk.
    `GameController` → `POST /api/v1/games` (`createGame`): **201** with a `Location: /api/v1/games/{gameId}` header and a `Set-Cookie` session header when the browser presented no valid one; body is the `WAITING` snapshot carrying `invitationUrl` and `invitationExpiresAt` for the host. The invitation URL is built **solely** from `public-base-url` in the shape `{site}/join/{gameId}#invite={invitationSecret}`; the service **never infers it from a request header** (R56).
    `GameController` → `GET /api/v1/games/{gameId}` (`getGame`): 200 with the caller-relative snapshot. A read **never** moves the idle deadline and **never** bumps the version (R18, R42).
    `app/registry` builds the `SnapshotContext` from the slot — `serverTime`, `expiresAt`, `invitationUrl?`, `invitationExpiresAt?` and each seat's `connected` flag — and hands it to the projector, so the projector never sees `GameSlot` (research.md D27).
  - **Covers** R01 (two more operations), R17, R18 (a read bumps nothing), R32 (session issuance and reuse), R56 · S3 (one read restores everything) · **proof area 7** (the issuance half, `AuthorizationIT` part 2) · groundwork for **proof area 10**
  - **Read first** spec.md R01, R17, R18, R56 · plan.md § *Projection* (the assembler paragraph), § *Adapters* · data-model.md § *Projection view* (the 1:1 mapping table) · research.md D27, D31 · `contracts/openapi.yaml` `createGame` (201, `Location`, `Set-Cookie`, 400/403/422/429/503), `getGame` (200, 401/404/410/429), `GameSnapshot` (L995), `GameId` parameter ("Public locator of a game. Knowing it grants nothing.")
  - **Files** `app/web/{SnapshotDtoAssembler,GameController}.java`, `app/registry/SnapshotContextFactory.java` (`GameSlot`'s implementation of `Slot.contextFor` — T017), `app/src/test/java/.../web/GameControllerIT.java`, `app/src/test/java/.../security/{AuthorizationIT,RequestSecurityIT}.java` (assertions added)
  - **Proof** `GameControllerIT` — (1) `createGame` under each ruleset returns 201, a `Location` header matching `/api/v1/games/[A-Za-z0-9_-]{22}`, a session `Set-Cookie`, and a `WAITING` snapshot whose `invitationUrl` is exactly `{public-base-url}/join/{gameId}#invite={43-char secret}` (US1 acceptance 1); (2) the invitation URL ignores `Host`, `X-Forwarded-Host` and `Origin` request headers entirely — changing them changes nothing (R56); (3) `getGame` returns the caller-relative snapshot for each seat, and the **same** `GameState` yields `YOU`/`OPPONENT` swapped between them; (4) **S3** — after any point of a game, a single `getGame` returns everything needed to continue: `phase`, `turn`, `allowedActions`, both boards, `lastShot`, both `connected` flags and `shipsRemaining`; (5) two consecutive reads return the **same** `version` and a **later** `serverTime` — a read bumps nothing but the clock is always current (R18); (6) the assembler's serialized output for the two privacy fixtures of `ProjectionPrivacyTest` is **byte-identical**, closing that test's deferred JSON assertion; (7) an unknown `gameId` shape (not 22 base64url characters) answers 404, not 500.
    Plus, added to the classes T024 started, now that `createGame` and `getGame` answer:
    - `AuthorizationIT` part 2 — (1) session **issuance**: `createGame` with no cookie answers 201 and issues `__Host-battleship_session` with exactly the contract's attributes (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, no `Domain`, no expiry attribute); (2) **reuse across two games**: the same browser creates a second game and is recognised on both, with no new cookie issued (R32's "reused for every game of that browser").
    - `RequestSecurityIT` — the raw-cookie-echo assertion T024 could only state as "not 403" is upgraded to a real **201**, which is what research.md D26 actually claims.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test='GameControllerIT,AuthorizationIT,RequestSecurityIT'`
  - **Mutation** Build the invitation URL from the `Host` header; assertion (2) must fail. Bump the version on a read; assertion (5) must fail.
  - **Depends on** T024, T018

- [ ] T026 [US2] [US1] Serve join and invitation replacement in `backend/app/src/main/java/ua/kostenko/battleship/app/web/`
  - **Delivers** `JoinController` → `POST /api/v1/games/{gameId}/join` (`joinGame`): 200 with the snapshot and a `Set-Cookie` session header when the browser presented no valid one. It answers **200** for a browser that already holds the guest seat, which is the membership-first order R34 requires. Every refusal is `409 invitation-unavailable`.
    `InvitationController` → `POST /api/v1/games/{gameId}/invitation` (`replaceInvitation`): 200 with the host's snapshot carrying the **new** `invitationUrl`. **No request body at all** — the operation declares no `consumes`, never checks a content type, and accepts any body or none (R40).
  - **Covers** R01 (two more operations), R34, R45 · US1 acceptance 2, US2 acceptances 1–3
  - **Read first** spec.md R34, R45, R01 · plan.md § *Adapters*, § *Request security* → *Join ordering* · `contracts/openapi.yaml` `joinGame` (200 + `Set-Cookie`; 400/403/409/422/429/503; "repeated join by the existing guest → 200; every other refusal collapses to `409 invitation-unavailable`"), `replaceInvitation` (**no request body**; 200; 401/403/404/409/410/429), `JoinGameRequest` (L1209)
  - **Files** `app/web/{JoinController,InvitationController}.java`, `app/src/test/java/.../web/JoinIT.java`
  - **Proof** `JoinIT` — (1) US2 acceptance 1: a second browser redeems a fresh link, the game moves to `PLACEMENT`, that browser receives a session, and **both players see each other's names**; (2) US2 acceptance 2: the same browser joining again with the same invitation gets 200, the current snapshot, and nothing changes — including the version; (3) US2 acceptance 3: a link opened but not confirmed claims no seat and leaves the invitation usable until it expires; (4) a third browser redeeming the used link gets `409 invitation-unavailable`, **byte-identical** to the answer for a wrong secret, an expired secret and a replaced secret; (5) US1 acceptance 2: the host replaces the invitation, the new link is in the host's snapshot, and the previous link **stops working immediately**; (6) the guest reloading after a replacement still reads its own game (membership beats the secret); (7) `replaceInvitation` succeeds with no body, with an empty body and with a JSON body, and **never** answers 415.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=JoinIT`
  - **Mutation** Declare `consumes = application/json` on `replaceInvitation`; assertion (7) must fail. Give the used-secret case a distinct code; assertion (4) must fail.
  - **Depends on** T025

- [ ] T027 [US3] [US4] [US5] [US6] Serve commands, presence and leaving in `backend/app/src/main/java/ua/kostenko/battleship/app/web/`
  - **Delivers** `CommandController` → `POST /api/v1/games/{gameId}/commands` (`sendCommand`): 200 with the caller's snapshot. The inbound body is the generated `CommandRequest`, so the four `Command` variants bind by their `type` discriminator and unknown fields are refused 422 (T022). Accepted, refused and repeated actions all behave exactly as `CommandUseCase` (T019) defines.
    `PresenceController` → `POST /api/v1/games/{gameId}/presence` (`sendPresence`): 200 with the caller's snapshot. **No request body.**
    `LeaveController` → `POST /api/v1/games/{gameId}/leave` (`leaveGame`): **204, no content, no body** — the only operation that does not answer with a snapshot. **No request body.**
    All three declare no `consumes`, never check a content type and accept any body or none (R40).
  - **Covers** R01 (the last three synchronous operations), R23, R24 (over the wire), R33, R46, R16 · **S6 owner** · **proof area 7** (the indistinguishable-answer half, `AuthorizationIT` part 3) · US3, US4, US6 acceptances
  - **Read first** spec.md R01, R23, R24, R46, R16 · plan.md § *Adapters*, § *Command path* · `contracts/openapi.yaml` `sendCommand` (200; 400/401/403/404/409/410/422/429; "only `commandId` is compared"; a coordinate outside the board or an unknown `shipId` is **422** with `OUT_OF_RANGE`/`UNKNOWN_VALUE`, while `placement-out-of-bounds` is **only** for a ship extending past the edge), `sendPresence` (**no body**), `leaveGame` (**204**, no body; `PLAYING` → `409 action-not-allowed`; repeated leave → 404), `CommandRequest` (L1294), `Command` (L1278, the discriminator mapping), `PlaceShipCommand`/`RemoveShipCommand`/`FireCommand`/`SimpleCommand`
  - **Files** `app/web/{CommandController,PresenceController,LeaveController}.java`, `app/src/test/java/.../web/CommandControllerIT.java`, `app/src/test/java/.../security/AuthorizationIT.java` (the S6 assertions added)
  - **Proof** `CommandControllerIT` — (1) each of the **seven** command types is accepted over the wire in a phase where it is offered, and each returns the caller's current snapshot; (2) the four `SimpleCommand` `type` values (`PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY`, `RESIGN`) each bind through the discriminator correctly; (3) the **422 vs 409 split** — a coordinate outside the board and an unknown `shipId` answer 422 with `OUT_OF_RANGE`/`UNKNOWN_VALUE` and a JSON Pointer, while a ship extending past the edge answers `409 placement-out-of-bounds`; (4) a repeated `commandId` returns the current state over the wire and applies nothing (R24 end to end); (5) an action not in `allowedActions` answers `409 action-not-allowed` and the next read shows an unchanged `version`; (6) presence returns 200 and a fresh `expiresAt` with an unchanged `version`, and a throttled repeat changes nothing; (7) leave returns **204 with no body**, a repeated leave returns 404, and leaving during `PLAYING` returns `409 action-not-allowed`; (8) all three body-less operations succeed with no body and never answer 415.
    Plus, added to `AuthorizationIT` as part 3 — **T027 owns S6**, because this is the first task at which all five protected **synchronous** operations answer: a valid-session browser that is not a player receives, for a **real** game and for a `gameId` that never existed, answers **identical in status, body and headers**, asserted byte for byte, on `getGame`, `sendCommand`, `replaceInvitation`, `sendPresence` and `leaveGame`. `streamGameEvents` is the sixth row and is added by T029, which is when it exists. The owning class stays `AuthorizationIT`, so plan.md § *Validation* is unchanged.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test='CommandControllerIT,AuthorizationIT'`
  - **Mutation** Return a body from `leaveGame`; assertion (7) must fail. Map an out-of-board coordinate to `placement-out-of-bounds`; assertion (3) must fail. Answer `game-unavailable` for a real game and a distinct body for an unknown id; `AuthorizationIT`'s S6 assertions must fail.
  - **Depends on** T026, T019, T020, T021

- [ ] T028 [US4] [US5] Prove a whole game over the wire in `backend/app/src/test/java/ua/kostenko/battleship/app/GameJourneyIT.java`
  - **Delivers** The server-level HTTP journey that success criteria S1 and S3 have no other owner for, plus the reusable fixtures T038 builds on. It is **not** a browser journey — those belong to `004-integration` (spec.md § *Out of scope*). Whatever gaps this journey exposes in T023–T027 are fixed **in this task**, which is what makes it a deliverable and not a test-only task.
    The journey is the automated form of quickstart.md § *Smoke: a whole game over the wire*: two cookie jars, `GET /meta` for the CSRF token, `createGame`, take the `#invite=` fragment, `joinGame` from the second jar, then drive both sides through `PLACE_FLEET_RANDOMLY`, `READY` and `FIRE` to a finished game.
  - **Covers** S1, S3 · R01 (the ten synchronous operations exercised together; `streamGameEvents` joins them in T029-T031) · plan.md § *Validation* row "A whole game over the wire, and reload recovery"
  - **Read first** quickstart.md § *Smoke: a whole game over the wire* · spec.md S1, S3, US4 and US5 acceptance scenarios · plan.md § *Validation* (the last three rows are **not** proof areas; this one exists because S1 and S3 have no other owner)
  - **Files** `app/src/test/java/.../GameJourneyIT.java`, plus fixes to the T023–T027 controllers as the journey requires
  - **Proof** `GameJourneyIT` — (1) **S1 under `sea-battle-10-ship.v1`**: create, share, join, arrange, ready, play to a destroyed fleet, read the result and the statistics from **both** sides, leave from both; (2) **S1 under `hasbro-classic-2002.v1`**: the same, and the journey observes the adjacency, extra-turn and revealed-water differences over the wire; (3) a second game ending in **resignation**, with the outcome and statistics read from both sides (US4 acceptance 3); (4) **S3** — at each of five points (`WAITING`, `PLACEMENT` empty, `PLACEMENT` half-arranged, `PLAYING` mid-game, `FINISHED`) a fresh client holding only the session cookie issues **one** `getGame` and has everything needed to continue; (5) the whole journey runs with a `MutableTimeSource`, so it completes in milliseconds and never sleeps (R60); (6) statistics read over the wire satisfy the four contract identities (re-asserted end to end, complementing T016's unit-level proof).
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=GameJourneyIT`
  - **Mutation** Omit `turn` from the `PLAYING` snapshot; assertion (4) must fail.
  - **Depends on** T027

**Checkpoint**: the **session and anti-forgery half** of proof area 7 is owned and green —
`AuthorizationIT` parts 1–3 (T024's pre-dispatch refusals, T025's session issuance and reuse, T027's
**S6**) and `RequestSecurityIT` part 1. S6's sixth row (`streamGameEvents`) lands in T029, and the
cross-site, body-size and rate-limit half is Phase 6 (T032, T033), so the area is not complete here.
All **ten synchronous operations** answer and a whole game is playable with `curl`. Realtime, abuse limits and packaging are still missing —
deliberately, and nothing so far depends on them.

---

## Phase 5: Realtime

**Purpose**: live updates over Server-Sent Events, delivering the **same** `GameSnapshot` document the
HTTP operations return.
**Proof area carried**: 8 (live updates).

**Shared contract for every task in this phase.** Spring MVC `SseEmitter` with
`spring.threads.virtual.enabled=true` (T005): a virtual thread per stream makes a bounded async
executor and drain tasks unnecessary (research.md D6). Every `snapshot` event carries one complete
`GameSnapshot` JSON document and its `id` is that snapshot's `version` (R17, research.md D11). There is
**no replay ring and no `Last-Event-ID` handling** — the contract does not mention the header, and the
first event is always the current snapshot, so there is nothing to replay (R27, research.md D10).

**Checkpoint**: proof area 8 is owned and green; a second tab replaces the first cleanly and a reload
is current from the first delivered message alone.

- [ ] T029 [US5] Open the event stream and deliver per-seat snapshots in `backend/app/src/main/java/ua/kostenko/battleship/app/realtime/`
  - **Delivers** `SnapshotPublisher.publish(GameId gameId, Seat seat, SnapshotView view)` — one of `plan.md` § *Ports*' five, declared in `application/port` and implemented by the SSE hub in `app/realtime`, so `CommandUseCase` (T019) can publish without referencing `app`. It carries the framework-free `SnapshotView`, never a wire type, because it is declared in `application`. **The hub is what turns that view into bytes, and it does so through the very same `SnapshotDtoAssembler` (T025) and the same Spring-configured `ObjectMapper` the controllers use** — one injected serialization path, not a parallel writer. That shared path is what makes proof (3) below true rather than hopeful: the SSE `data:` is byte-identical to `getGame`'s body because it is produced by the same two objects. This is the port's **first real consumer**, which is why it is declared here and not in Phase 3: an `application` port with no production bean would break the Spring context for T025-T028 (Constitution IX). Wiring the publish call into `application/usecase/CommandUseCase.java` — after the slot lock is released, never under it (T019 proof 5) — is part of this task.
    `Subscriber` in `app/realtime` — `openedAt`, the seat, the emitter and a single coalescing slot for the latest unsent snapshot (data-model.md § *Registry state*) — and with it the `Map<Seat, Subscriber>` field on `GameSlot`, at most one subscriber per seat. T017 deliberately left both out: this is the first task that has a `Subscriber` to put there. `EventsController` → `GET /api/v1/games/{gameId}/events` (`streamGameEvents`), `text/event-stream`.
    **Registration captures the current snapshot under the slot lock** and sends it as the first `snapshot` event, so connecting and reconnecting need no other request and there is nothing to replay (R27). `Last-Event-ID` is ignored.
    A snapshot reaches a player **only when that player's own view changed** (R18, R28), so versions skip: a change only the other player can see — arranging a fleet during placement — is not delivered. The *content* of a private action is never delivered; only that some action was accepted is inferable, from `expiresAt`.
    Opening a stream acquires a permit from the **streams** semaphore (T017); over the ceiling the answer is `503 service-unavailable` with `Retry-After` (T034 owns the wire form). Opening or reconnecting a stream **never** moves the idle deadline (R42), and having a stream open does **not** keep the game alive (R31).
  - **Covers** R17 (one representation, HTTP and SSE alike), R27, R28, R33 (the sixth S6 row), R39 (the streams ceiling) · **proof area 8 owner, part 1** · **S6 co-owner** (the `streamGameEvents` row) · research.md D10, D11
  - **Read first** spec.md R27, R28, R17, R31, R42 and § *Proof required* area 8 · plan.md § *Realtime* · research.md D6, D10, D11 · `contracts/openapi.yaml` `streamGameEvents` (L402-453: `snapshot` events carry `GameSnapshot`, `id` is the snapshot `version`, the first event is always the current snapshot; 401/404/410/429/503), `components.examples.EventStream` (L2097-2108 — the exact wire framing: `event: snapshot` / `id: 24` / `data: {...}`)
  - **Files** `application/port/SnapshotPublisher.java`, `app/realtime/{SseHub,Subscriber}.java`, `app/registry/GameSlot.java` (the `Map<Seat, Subscriber>` field added), `application/usecase/CommandUseCase.java` (the publish call wired in, outside the lock), `app/web/EventsController.java`, `app/src/test/java/.../realtime/EventStreamIT.java`, `app/src/test/java/.../security/AuthorizationIT.java` (the sixth S6 row added)
  - **Proof** `EventStreamIT` part 1 — (1) **S8**: the first delivered message is the caller's **current** snapshot, complete, with no second request and nothing to replay — asserted at five points of a game; (2) the framing matches the contract's example exactly: `event: snapshot`, `id:` equal to the snapshot's `version`, `data:` one complete `GameSnapshot` document; (3) the delivered document is **byte-identical** to what `getGame` returns for the same caller at the same version (R17 — there is no second representation); (4) **R28** — the host arranging a fleet during `PLACEMENT` delivers nothing to the guest, so the guest's version numbers **skip**; a `READY` (which changes the opponent's `Player.ready`) is delivered to both; (5) a `Last-Event-ID` request header changes nothing — the first event is still the current snapshot; (6) opening a stream does **not** move the idle deadline and does **not** keep the game alive; (7) the hub serializes through the injected `SnapshotDtoAssembler` and `ObjectMapper` — asserted by changing one Jackson setting on the shared `ObjectMapper` and observing **both** the SSE payload and the `getGame` body change together.
    Plus, added to `AuthorizationIT` as part 4 — the **sixth S6 row**: a valid-session non-player opening `GET …/events` on a real game and on a `gameId` that never existed receives answers identical in status, body and headers. T027 owns S6 and asserts the five synchronous operations; this completes the set.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test='EventStreamIT,AuthorizationIT'` — the three "parts" of `EventStreamIT` are assertion groups of **one** class, not methods: T029, T030 and T031 each add their own assertions to the shared class, and each runs the whole class
  - **Mutation** Deliver every transition to both seats regardless of `viewChanged`; assertion (4) must fail. Send a synthetic "connected" event first instead of the snapshot; assertion (1) must fail.
  - **Depends on** T028

- [ ] T030 [US5] Close streams deliberately and track connection state in `backend/app/src/main/java/ua/kostenko/battleship/app/realtime/SseHub.java`
  - **Delivers** The server ends a stream deliberately in **exactly two** cases and says which (R29):
    - **`REPLACED`** — the same browser opened a newer stream for this game. At most one stream is kept per (session, game); the newer one closes the older with a `closed` event carrying `{"reason":"REPLACED"}`. **This is not a disconnection and changes no `connected` flag**, so a reload never flickers (R22, R31).
    - **`GAME_UNAVAILABLE`** — the game has stopped existing for this browser: expired, removed, or the player left. The player who **stayed** receives an ordinary snapshot instead.
    A **finished or abandoned** game arrives as an **ordinary snapshot and keeps the stream open** — it is not one of the two close cases. Shutdown is not one either (T036 owns R59).
    **`connected` flags (R22)**: having a stream open marks that player connected. The flag changes the opponent's view like anything else and is delivered at once. A stream **replaced** by a newer one from the same player produces **no** flag change.
  - **Covers** R22 (the connection flag), R29, R31 · **proof area 8 owner, part 2** · US5 acceptance 4
  - **Read first** spec.md R22, R29, R31, US5 acceptance 4 · plan.md § *Realtime* · `contracts/openapi.yaml` `streamGameEvents` description (`closed` events carry `StreamClosed`; do not reconnect after one; `REPLACED` = another tab of this browser; `GAME_UNAVAILABLE` = the game stopped existing for this browser, call `getGame` once; `FINISHED` and `ABANDONED` arrive as **ordinary snapshots** and keep the stream open), `StreamClosed` (L1307, `reason` ∈ `REPLACED`, `GAME_UNAVAILABLE`)
  - **Files** `app/realtime/SseHub.java`, `app/registry/GameSlot.java` (the at-most-one-per-seat replacement rule on the `Map<Seat, Subscriber>` field T029 added), `app/src/test/java/.../realtime/EventStreamIT.java`
  - **Proof** `EventStreamIT` part 2 — (1) **US5 acceptance 4**: a second tab of the same browser opens a stream for the same game; the older one receives `event: closed` / `data: {"reason":"REPLACED"}` and stops, and exactly **one** stream per player and game remains open; (2) that replacement produces **no** `connected` flag change — the opponent receives no snapshot at all from it; (3) a player leaving after the game ends closes **that** player's stream with `GAME_UNAVAILABLE`, while the player who stayed receives an **ordinary snapshot**; (4) an expired game closes its players' streams with `GAME_UNAVAILABLE`; (5) a game reaching `FINISHED` and a game reaching `ABANDONED` each arrive as **ordinary snapshots** and the stream **stays open**; (6) opening a stream sets `connected` true and delivers the change to the opponent at once; closing it sets `connected` false and delivers that too; (7) `connected` is never true for a player with no open stream, across a full journey.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=EventStreamIT` — the whole class, T029's assertions included: the parts are assertion groups, not methods named `part2`
  - **Mutation** Emit `closed`/`GAME_UNAVAILABLE` when a game reaches `FINISHED`; assertion (5) must fail. Toggle `connected` on a replacement; assertion (2) must fail.
  - **Depends on** T029

- [ ] T031 [US5] Bound every stream in `backend/app/src/main/java/ua/kostenko/battleship/app/realtime/SseHub.java`
  - **Delivers** Three bounds, all configured, none hard-coded (R30, R41):
    - **Heartbeat** — a `: keep-alive` **comment line** (not a named event, carrying no meaning) every `heartbeat-seconds`, from **one** scheduler shared by all streams. Like the sweeper (T020), `HeartbeatScheduler`'s whole body is a **package-visible `tick()`** reading the injected `TimeSource`, which also enforces the maximum-lifetime bound below; `@Scheduled` calls `tick()` in production and nowhere else. That is what lets proofs (1) and (2) advance the `MutableTimeSource` and call `tick()` directly instead of waiting on wall time, which R60 forbids (plan.md § *Realtime*).
    - **Maximum lifetime** — `stream-max-lifetime-seconds` from `Subscriber.openedAt` ends the stream **with no `closed` event**, so the browser's `EventSource` reconnects by itself.
    - **Slow or failing recipient** — a subscriber keeps **at most the latest unsent snapshot** (a single coalescing slot, not a queue) and is **dropped** if it cannot be written, rather than allowed to accumulate unbounded work.
    Every dropped or ended stream releases its semaphore permit **exactly once** (R39).
  - **Covers** R30, R41 (the stream registry and its queues are bounded by construction), R39 (permit release) · **proof area 8 owner, part 3**
  - **Read first** spec.md R30, R41, R52 (the `heartbeat-seconds` and `stream-max-lifetime-seconds` rows) · plan.md § *Realtime* (last sentence) · data-model.md § *Registry state* (`Subscriber`: `openedAt`, latest unsent snapshot, single slot, coalescing) · `contracts/openapi.yaml` `streamGameEvents` description (heartbeat is comment lines `: keep-alive` every `heartbeatSeconds`; a stream ending **without** `closed` is reconnected by the browser)
  - **Files** `app/realtime/{SseHub,Subscriber,HeartbeatScheduler}.java`, `app/src/test/java/.../realtime/EventStreamIT.java`
  - **Proof** `EventStreamIT` part 3 — (1) with `heartbeat-seconds` set small, a `: keep-alive` **comment** arrives at each interval and is not a named event; (2) advancing to `stream-max-lifetime-seconds` ends the stream **with no `closed` event** (inclusive boundary: alive at `deadline - 1ms`, ended at `deadline`); (3) a subscriber that cannot be written is dropped, and its permit is released — proven by reopening at the streams ceiling immediately afterwards; (4) coalescing — with a stalled writer, several transitions in a row leave **one** pending snapshot, the latest, not a queue of them; (5) a heartbeat does **not** move the idle deadline (R42); (6) after 100 open-and-drop cycles at ceiling 1, a new stream still opens — no permit leak.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=EventStreamIT` — the whole class, T029's and T030's assertions included: the parts are assertion groups, not methods named `part3`
  - **Mutation** Replace the single coalescing slot with an unbounded queue; assertion (4) must fail. Send the heartbeat as a named event; assertion (1) must fail. Read `Instant.now()` inside `tick()` instead of the injected `TimeSource`; assertions (1) and (2) must fail.
  - **Depends on** T030

**Checkpoint**: proof area 8 is owned and green. Every stream is bounded in count, in lifetime and in
buffered work.

---

## Phase 6: Abuse limits, hardening and operation

**Purpose**: everything that keeps a hostile or careless caller from degrading the service, and
everything an operator needs.
**Proof areas carried**: 7 (the remaining half), 9 (packaging, configuration and redaction).

**Checkpoint**: proof areas 7 and 9 are owned and green; every structure a hostile caller can grow is
bounded by construction (R41).

- [ ] T032 Rate-limit every operation in `backend/app/src/main/java/ua/kostenko/battleship/app/security/RateLimitFilter.java`
  - **Delivers** A **fixed 60-second window per key per operation class** (research.md D16 — every limit in R52 is "per minute", and the window end gives an exact `Retry-After`). Eight classes, each reading its own configured limit:

    | Operation class | Key | Default |
    |---|---|---|
    | `createGame` | caller address | 5 / min |
    | `joinGame` | caller address | 20 / min |
    | `sendCommand` | session | 60 / min |
    | `getGame` | session | 120 / min |
    | `sendPresence` | session | 30 / min |
    | `streamGameEvents` (open) | session | 30 / min |
    | `replaceInvitation` | session | 10 / min |
    | `leaveGame` | session | 10 / min |

    The **"bounded caller-address key"** is `request.getRemoteAddr()`, used **only** for rate limiting and **never written to a log record** (research.md D17). On refusal: `429 rate-limit-exceeded` with a `Retry-After` header whose value **equals** `Problem.retryAfterSeconds` in the body, both in delta-seconds, never an HTTP-date.
    **Bounded by construction (R41)**: the bucket table has a hard cap and **drops the keys unused for longest** rather than refusing callers it has not seen before, so a flood of invented keys never locks a real player out; the flood itself stays capped by the ceilings of R39. The same sweeper that prunes expired games prunes stale buckets (research.md D15) — no second scheduler, no Caffeine.
  - **Covers** R38, R61, R41 (the rate-limit half), R52 (the eight rate-limit rows) · **proof area 7** (the abuse half) · research.md D15, D16, D17
  - **Read first** spec.md R38, R61, R41, R52 (the eight rate-limit rows), assumption 4 · plan.md § *Request security* (the `RateLimitFilter` row) · research.md D15, D16, D17 · `contracts/openapi.yaml` `TooManyRequests` (429 `rate-limit-exceeded` with the `RetryAfter` header), `components.headers.RetryAfter` (L714: "Seconds to wait; equals `retryAfterSeconds` in the body", `integer, minimum: 1`), the three limits stated in prose on `createGame` (5/min per client address), `joinGame` (20/min per client address) and `sendCommand` (60/min per session)
  - **Files** `app/security/{RateLimitFilter,FixedWindowBuckets}.java`, `app/registry/ExpirySweeper.java` (bucket pruning added), `app/src/test/java/.../security/RateLimitIT.java`
  - **Proof** `RateLimitIT` — (1) **each of the eight classes** refuses at exactly its configured limit and admits the call before it, driven by `MutableTimeSource` so the window is exact, asserted class by class (an enumerated manifest, not "all operations"); (2) the `Retry-After` header equals `retryAfterSeconds` in the body, both delta-seconds and both at least 1; (3) the window boundary is **inclusive** with the rest of the system (T020) — at the window end the counter resets; (4) the two address-keyed classes are keyed by caller address and the six session-keyed classes by session — two sessions from one address throttle independently on commands but share the create limit; (5) **R41** — flooding the table with 10 x its cap of invented keys drops the least-recently-used and a real player's existing key **still works**; (6) a refused request changes nothing: the game's version and state are untouched.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=RateLimitIT`
  - **Mutation** Drop the **newest** key instead of the least recently used when the table is full; assertion (5) must fail. Emit `Retry-After` as an HTTP-date; assertion (2) must fail.
  - **Depends on** T031

- [ ] T033 Refuse cross-site, oversized and mistyped requests in `backend/app/src/main/java/ua/kostenko/battleship/app/security/`
  - **Delivers** Three refusals that complete the request-security surface.
    **`SiteIsolationFilter` (R36)** rejects unsafe (state-changing) requests whose `Origin` or `Sec-Fetch-Site` / `Sec-Fetch-Mode` headers show a **cross-site browser source**, answering `403 request-security-rejected` through the same problem writer as CSRF. **Absent headers are admitted** — a caller that is not a browser (a script, a command-line tool, an integration test) is let through and still has to present the anti-forgery token of R35. CORS remains unconfigured: the interface and the API share one origin.
    **`RequestSizeFilter` (R40)** refuses a body larger than `max-request-body-bytes` with `413 payload-too-large`. The ceiling applies to **every** request including the three body-less operations.
    **Content type (R40)**: a request carrying a body whose content type is not the expected one is refused `415 unsupported-media-type`. `replaceInvitation`, `sendPresence` and `leaveGame` declare **no `consumes`** and never reach the content-type check (already delivered in T026/T027; re-asserted here).
  - **Covers** R36, R40 · **proof area 7** (the cross-site half)
  - **Read first** spec.md R36, R40, R52 (the `max-request-body-bytes` row) and § *Proof required* area 7 · plan.md § *Request security* (the `SiteIsolationFilter` row and the `RequestSizeFilter` paragraph) · `contracts/openapi.yaml` `info.description` § Security ("the server additionally rejects unsafe requests whose `Origin` or Fetch Metadata headers show a cross-site source"), `Forbidden` (403), `components.responses.Problem` (the `default` response covers 413 and 415)
  - **Files** `app/security/{SiteIsolationFilter,RequestSizeFilter}.java`, `app/src/test/java/.../security/RequestSecurityIT.java`
  - **Proof** `RequestSecurityIT` part 2 — (1) a POST with `Origin: https://evil.example` answers `403 request-security-rejected`; the same for `Sec-Fetch-Site: cross-site` and for `Sec-Fetch-Site: same-site` on a different origin; (2) `Sec-Fetch-Site: same-origin` and `Sec-Fetch-Site: none` are **admitted**; (3) **a non-browser caller with no `Origin` and no `Sec-Fetch-*` headers is admitted** and still needs the CSRF token — this is the assertion that guards R36's "absent headers are not a refusal"; (4) a body of `max-request-body-bytes + 1` answers `413 payload-too-large` on `createGame`, `joinGame`, `sendCommand` **and** on each of the three body-less operations; (5) `Content-Type: text/plain` on `sendCommand` answers `415 unsupported-media-type`; (6) the three body-less operations **never** answer 415 whatever content type is sent; (7) the 403, 413 and 415 bodies are all the same `Problem` shape the advice produces, each with its correlation id.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=RequestSecurityIT`
  - **Mutation** Refuse requests with no `Origin` header; assertion (3) must fail — the integration tests themselves would go red, which is the point.
  - **Depends on** T032

- [ ] T034 Refuse at every ceiling without evicting anything in `backend/app/src/main/java/ua/kostenko/battleship/app/web/`
  - **Delivers** The wire form of the three capacity refusals, which T017 and T029 enforce internally but which have had no HTTP answer until now: the **games** ceiling (`max-concurrent-games`), the **streams** ceiling (`max-concurrent-streams`) and the **per-browser** cap (`max-live-games-per-browser`). Each answers `503 service-unavailable` with a `Retry-After` header equal to the body's `retryAfterSeconds`.
    **No running game and no open stream is ever evicted to make room**, and **the service still reports itself ready** — a service that is full is still ready (R04, R39). Each allocation is released **exactly once** when the game or stream ends, and a finished or abandoned game **never blocks a new one** (R62).
  - **Covers** R39, R62, R04 (full is still ready) · S11 · plan.md § *Validation* row "Capacity and per-browser ceilings" · research.md D8
  - **Read first** spec.md R39, R62, R04, S11 · plan.md § *Registry and slots*, § *Request security* → *Per-browser cap* · research.md D8 · `contracts/openapi.yaml` `ServiceUnavailable` (503 `service-unavailable` with `Retry-After`), `createGame` and `joinGame` (both list 503), `streamGameEvents` (lists 503)
  - **Files** `app/web/{GameController,JoinController,EventsController}.java` (the 503 mapping), `app/src/test/java/.../CapacityIT.java`
  - **Proof** `CapacityIT` — the owner of **S11**: (1) at `max-concurrent-games`, a new `createGame` answers `503 service-unavailable` with `Retry-After` equal to `retryAfterSeconds`; (2) **every running game is still readable and unchanged** after that refusal — nothing was evicted; (3) at `max-concurrent-streams`, a new `streamGameEvents` answers 503 and **every open stream keeps delivering**; (4) `GET /api/v1/health` reports `ready: true` throughout both ceilings; (5) a browser at `max-live-games-per-browser` is refused 503 on both `createGame` and `joinGame`; (6) that browser's **finished** game no longer counts, and leaving frees the allocation **at once** — the next create succeeds immediately; (7) after 500 create-and-remove cycles at ceiling 1, creation still succeeds — no permit leak.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=CapacityIT`
  - **Mutation** Evict the least recently used game to admit a new one; assertion (2) must fail. Report `ready: false` when the games semaphore is exhausted; assertion (4) must fail.
  - **Depends on** T033

- [ ] T035 Log structurally and redact absolutely in `backend/app/src/main/java/ua/kostenko/battleship/app/observability/`
  - **Delivers** **Spring Boot 4's built-in structured logging** — no extra encoder dependency (research.md D18). Every record carries the **correlation id** (the same 16-hex value the problem body carries, T022) and the **`gameId`**, which the contract says grants nothing.
    A log record **never** carries: a session cookie or its value, an invitation secret, a link fragment, a full secret-bearing URL, board content, a raw request body, or an unnecessary player name (R58). The caller address used for rate limiting is **never** written to a log record (research.md D17). Diagnostic surfaces expose nothing sensitive and no game content.
    Operational events get a **safe user outcome and a non-sensitive diagnostic record** (Constitution V's EARS rule): a rejected command, a game expiring, state lost on restart, and a realtime delivery failure.
  - **Covers** R58 · S9 · **proof area 9** (the redaction third) · research.md D17, D18
  - **Read first** spec.md R58, S9, R50 and § *Proof required* area 9 · plan.md § *Configuration and observability* (the logging sentence) · research.md D17, D18 · Constitution V (*Accessible, Secure, Observable Operation*)
  - **Files** `app/observability/{LoggingConfig,OperationalEvents}.java`, `app/src/main/resources/application.yaml` (structured logging), `app/src/test/java/.../observability/LogRedactionIT.java`
  - **Proof** `LogRedactionIT` — capture **every** log record emitted during a complete `GameJourneyIT`-style journey that also triggers each failure class (401, 403, 404, 409, 410, 413, 415, 422, 429, 503), then assert the captured text contains **none** of: (1) the session cookie name's value or the raw session string; (2) the invitation secret; (3) the `#invite=` fragment or any URL containing it; (4) any cell of either board, or the word `SHIP` in a board context; (5) a raw request body; (6) a player's display name; (7) the caller address. Plus: (8) **every** record carries a correlation id, and the id in a problem body matches the id in that request's records; (9) records are structured (parseable as JSON, one object per record, with stable field names); (10) each of the four operational events of Constitution V's EARS rule produces exactly one diagnostic record and a safe user outcome.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=LogRedactionIT`
  - **Mutation** Log the full request body at DEBUG in the command path and run at DEBUG; assertion (5) must fail. Log `remoteAddr` in the rate-limit filter; assertion (7) must fail.
  - **Depends on** T034

- [ ] T036 [US7] Drain and shut down honestly in `backend/app/src/main/java/ua/kostenko/battleship/app/`
  - **Delivers** `server.shutdown=graceful` plus the configured `shutdown-drain-seconds` window. On shutdown, in this order (R59): readiness flips to **`DRAINING`** **first**; new work stops being admitted; event streams are closed **with no `closed` event** (shutdown is **not** one of R29's two deliberate-close cases); in-flight actions get the drain window to finish; the process exits **without claiming that any game survives** (R48 — a restart ends every game and every session; there is no persistence, no recovery and no claim of continuity).
    Once readiness is `DRAINING`, the events endpoint answers reconnection attempts `503 service-unavailable`, which is what stops the browser's automatic retry (R29's note, R59). `HealthController` (T023) now reports `ready: false` with `reason: DRAINING`, and `reason: STARTING` while starting.
  - **Covers** R04 (the reason half), R29 (shutdown is not a deliberate close), R48, R59 · **proof area 9** (the shutdown third) · US7 acceptance 1
  - **Read first** spec.md R59, R04, R29, R48, US7 acceptance 1 · plan.md § *Configuration and observability* (last three sentences) · `contracts/openapi.yaml` `Health` (L1187, `reason` ∈ `STARTING`, `DRAINING`, present when `ready` is false), `streamGameEvents` (a stream ending **without** `closed` is reconnected by the browser; `EventSource` gives up permanently when a reconnect is refused with any status)
  - **Files** `app/web/HealthController.java`, `app/config/GracefulShutdownConfig.java`, `app/src/main/resources/application.yaml`, `app/src/test/java/.../GracefulShutdownIT.java`
  - **Proof** `GracefulShutdownIT` — (1) during start-up, `GET /api/v1/health` reports `live: true, ready: false, reason: STARTING`; (2) on shutdown, readiness flips to `ready: false, reason: DRAINING` **before** anything else observable; (3) open event streams end **with no `closed` event** — the captured stream shows no `event: closed` line; (4) a reconnection attempt while `DRAINING` answers `503 service-unavailable`; (5) an in-flight action started before shutdown completes within `shutdown-drain-seconds`; (6) a new `createGame` after `DRAINING` begins is refused; (7) no log record, response or diagnostic surface claims that any game survives the restart; (8) **moved here from T022** — the `getHealth` 503 answer carries a `Health` body with `ready: false, reason: DRAINING`, **not** a problem document: `Content-Type` is `application/json`, not `application/problem+json`, and the body has no `code`, `title` or `status` field. This is the only place in the feature where a 503 health answer occurs, which is why the assertion lives here (research.md D20).
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=GracefulShutdownIT`
  - **Mutation** Send `closed`/`GAME_UNAVAILABLE` on shutdown; assertion (3) must fail. Flip readiness **after** closing the streams; assertion (2) must fail. Let `ProblemAdvice` intercept the health 503; assertion (8) must fail.
  - **Depends on** T035

**Checkpoint**: proof area 7 is complete (`AuthorizationIT`, `RequestSecurityIT`, `RateLimitIT`).
Proof area 9 has its **configuration** (T006, `ConfigurationValidationIT`), **redaction** (T035,
`LogRedactionIT`) and **shutdown** (T036, `GracefulShutdownIT`) parts green; its **packaging**
(`PackagedArtifactIT`) and **published-and-enforced limits** (`PublishedLimitsIT`) parts remain, and
land in T037. Every structure a hostile caller can grow is bounded, and shutdown is honest.

---

## Phase 7: Packaging and wire conformance

**Purpose**: the deliverable artifact, and the proof that what goes on the wire is what the contract
published.
**Proof areas carried**: 9 (the packaging third), 10 (wire conformance).

- [ ] T037 [US7] Deliver the packaged, backend-only artifact in `backend/app/pom.xml` and `backend/app/src/test/java/ua/kostenko/battleship/app/`
  - **Delivers** One executable, backend-only JAR that starts locally with default configuration and reports itself ready. **It bundles and serves no user-interface assets** (R57): there is no static resource handler, no `index.html`, no `webjars`, and `GET /` answers 404. Whatever it takes to make that true — excluding stray static resource handling, confirming the fat JAR's contents — is this task's work. The `spring-boot-maven-plugin` `repackage` goal that produces the executable JAR is **not** introduced here: T001 declares it, because T005's `spring-boot:run` and this task's `java -jar` both depend on it and T005 comes first.
    It also closes **S12**, the half of R55 that T023 could only publish: a limit changed by configuration alone is both **published** by `getMeta` **and enforced** by the running service, with no code change.
  - **Covers** R55 (the enforcement half), R57 · S10, S12 · **proof area 9** (the packaging third)
  - **Read first** spec.md R57, R55, S10, S12, US7 · plan.md § *Gate* (the `java -jar` row) · quickstart.md § *Commands* and § *Smoke: the service is up and publishes its limits* · Constitution VII (*Local Production-Like Readiness*)
  - **Files** `app/src/main/resources/application.yaml`, `app/src/test/java/.../PackagedArtifactIT.java`, `app/src/test/java/.../PublishedLimitsIT.java`, and `app/pom.xml` only if excluding a stray resource requires it — the `repackage` goal is already there from T001
  - **Proof**
    - `PackagedArtifactIT` — owner of **S10**: (1) `java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` starts from **default** configuration and `GET /api/v1/health` reports `{"live":true,"ready":true}`; (2) `GET /` answers **404** — no UI asset is served; the same for `/index.html`, `/static/x.js` and `/assets/x.css`; (3) the JAR's entry list contains no `.html`, `.css` or client `.js` asset; (4) the packaged run answers all three unauthenticated operations identically to the `spring-boot:run` form.
    - `PublishedLimitsIT` — owner of **S12**: for **each** of the six published limits, start with the value changed by environment variable alone and assert **both** that `meta.limits` reports the new value **and** that the running service enforces it — `idleTimeoutSeconds=60` expires an idle game at 60 s (via `MutableTimeSource`), `invitationLifetimeSeconds` expires the invitation, `presenceIntervalSeconds` moves the throttle, `heartbeatSeconds` changes the keep-alive interval, `resultRetentionSeconds` changes when the result goes 404, `maxGameDurationSeconds` changes the absolute ceiling. Plus: a changed limit that is **not** published (for example `max-concurrent-games`) is still enforced.
  - **Verify** `cd backend && ./mvnw -q -DskipTests package && java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` — then `./mvnw -q -pl app -am verify -Dit.test='PackagedArtifactIT,PublishedLimitsIT'`
  - **Mutation** Add `src/main/resources/static/index.html`; `PackagedArtifactIT` assertions (2) and (3) must fail. Hard-code 900 in the expiry policy instead of reading `idle-timeout-seconds`; `PublishedLimitsIT`'s enforcement half must fail.
  - **Depends on** T036

- [ ] T038 Assert every wire body against the contract in `backend/app/src/test/java/ua/kostenko/battleship/app/WireConformanceIT.java`
  - **Delivers** **Proof area 10**, and the fixes it forces. Generation fixes the **shape** of the wire types; it does **not** prove that `SnapshotDtoAssembler` (T025) fills them all in. This task drives every operation's success **and** failure path and asserts each response body **field by field** against the contract's embedded examples, then fixes whatever the comparison exposes — missing assembler fields, wrong optionality, wrong enum spelling. Those fixes are the deliverable; the IT is their proof.
    It **reuses** the fixtures of `GameJourneyIT` (T028) and the security ITs (T024, T033) rather than duplicating the journeys.
  - **Covers** R01 (every operation's wire boundary), R17, R49 (every failure body) · **proof area 10 owner**
  - **Read first** spec.md § *Proof required* area 10 ("every operation's success and failure bodies asserted field by field against the contract's examples, so the published wire boundary is proven rather than assumed") · plan.md § *Validation* (the staging note for area 10) · research.md D1 (why generation retires wire drift and completeness is what remains) · `contracts/openapi.yaml` `components.examples` — the twelve named examples: `Meta`, `Rulesets`, `SnapshotWaiting`, `SnapshotPlacementStart`, `SnapshotPlacementInProgress`, `SnapshotPlayingAfterHit`, `SnapshotPlayingAfterSunk`, `SnapshotPlayingClassic`, `SnapshotFinishedByVictory`, `SnapshotFinishedByResignation`, `SnapshotAbandoned`, `EventStream`
  - **Files** `app/src/test/java/.../WireConformanceIT.java`, plus assembler and controller fixes as the comparison requires
  - **Proof** `WireConformanceIT` — an enumerated manifest, not "every operation":
    1. **Success bodies** — one assertion per operation: `getMeta` against the `Meta` example; `listRulesets` against `Rulesets`; `getHealth`; `createGame` against `SnapshotWaiting` (plus the `Location` and `Set-Cookie` headers); `getGame` against `SnapshotPlacementStart`, `SnapshotPlacementInProgress`, `SnapshotPlayingAfterHit`, `SnapshotPlayingAfterSunk`, `SnapshotPlayingClassic`, `SnapshotFinishedByVictory`, `SnapshotFinishedByResignation` and `SnapshotAbandoned` at the matching points of a staged game; `joinGame`; `sendCommand`; `replaceInvitation`; `sendPresence`; `leaveGame` (204, **empty** body); `streamGameEvents` against the `EventStream` example's framing.
    2. **Failure bodies** — one assertion per response component: `BadRequest` 400, `Unauthorized` 401, `Forbidden` 403, `NotFound` 404, `Conflict` 409 (once per its six codes), `Gone` 410, `Unprocessable` 422 (with `violations`), `TooManyRequests` 429 (with `Retry-After`), `ServiceUnavailable` 503 (with `Retry-After`), and the `default` `Problem` for 413, 415 and 500.
    3. **Field-by-field, not shape-only** — every field the contract marks required is present, every field it marks optional is present or absent exactly per its stated condition, and no field the contract does not define appears.
    4. **Assembler completeness** — for a `SnapshotView` with every optional field populated, the assembled `GameSnapshot` carries **all** of them; a snapshot with each optional absent carries none of them.
    5. **Enum spellings** — every value emitted for `Phase`, `Side`, `Action`, `CellState`, `ShipStatus`, `Shot.result`, `Outcome.reason`, `Health.reason`, `StreamClosed.reason`, `Violation.rule` and `ProblemCode` is one of the contract's listed values, spelled exactly.
    6. **One representation (R17)** — the SSE `snapshot` payload and the `getGame` body for the same caller at the same version are **byte-identical**.
  - **Verify** `cd backend && ./mvnw -q -pl app -am verify -Dit.test=WireConformanceIT` — then the full gate, `./mvnw -q verify`
  - **Mutation** Drop `shipsRemaining` from the assembler's `Player` copy; assertions (1), (3) and (4) must fail. Emit `ProblemCode` in `UPPER_SNAKE` instead of kebab-case; assertion (5) must fail.
  - **Depends on** T037

**Checkpoint**: all ten proof areas are owned and green, every success criterion has an owner, and
`./mvnw -q verify` is the single command that proves it.

---

## Dependencies and execution order

### The critical path

```text
T001 -> T002 -> T004
  |       |
  |       +-> T022 -> T023 -> T024 -> T025 -> T026 -> T027 -> T028 -> T029 -> T030 -> T031
  |                     ^       ^       ^                ^
  +-> T003              |       |       |                |
  +-> T005 -> T006 -----+       |       |                |
  |            |                |       |                |
  |            +-> T017 --------+-------+                |
  +-> T007 -> T008 -> T009 -> T010 -> T011 -> T012 -> T013
                                |                       |
                                +-> T014 ---------------+-> T015 -> T016
                                                             |
                                                             +-> T018 -> T019 -> T020 -> T021
                                                             |     ^                       |
                                                    T017 ----+-----+           (feeds T025/T027)

T031 -> T032 -> T033 -> T034 -> T035 -> T036 -> T037 -> T038
```

`T015 -> T018` is a real edge, not bookkeeping: `CreateGameUseCase`, `JoinGameUseCase` and
`ReplaceInvitationUseCase` each project a snapshot for their caller. It transitively closes T019 (which
dispatches all seven commands, so it needs `AllowedActions` from T013) and T021.

Every **Depends on** in this file names a **lower** task id. There is no forward dependency: a task
never needs something a later task builds.

### Parallel opportunities

Small, because the list is deliberately a chain. The `[P]` tasks:

- **T003** and **T004** — independent build-tooling additions on different pom sections; either order.
- **T014** — the ports, independent of the Phase 2 chain once T010 has declared `RandomSource`.

Everything else is sequential. Two people could split the tree after T006: one takes the domain chain
T007-T013, the other T017 and the web foundations T022-T023; they converge at T015.

### MVP boundary

**T001 through T028** is the minimum viable server: a whole game creatable, shareable, joinable,
arrangeable and playable to a stated winner over HTTP, with privacy, idempotency, linearization and
every lifetime proven, and with session and anti-forgery security in place.

What is missing at that boundary, deliberately: live updates (T029-T031), abuse limits and capacity
refusals (T032-T034), logging redaction and shutdown (T035-T036), packaging and wire conformance
(T037-T038). Nothing in T001-T028 depends on any of them.

---

## Coverage maps

### The ten proof areas (spec.md § *Proof required*; plan.md § *Validation*)

| # | Area | Owning task | Owning test class | Module |
|---|---|---|---|---|
| 1 | Ruleset correctness | T012 | `RulesetRulesTest` | `domain` |
| 2 | Action legality and state stability | T013 | `AllowedActionsTest`, `RefusedCommandStabilityTest` | `domain` |
| 3 | Player privacy | T015 | `ProjectionPrivacyTest` | `application` |
| 4 | Repeat safety | T019 | `CommandIdempotencyTest` | `application` |
| 5 | Concurrency | T019 | `GameSerializationTest` | `application` |
| 6 | Lifetimes | T016, T020 | `StatisticsTest`, `LifetimeBoundaryTest` | `application` |
| 7 | Request security and authorization | T024, T025, T027, T032, T033 | `AuthorizationIT`, `RequestSecurityIT`, `RateLimitIT` | `app` |
| 8 | Live updates | T029, T030, T031 | `EventStreamIT` | `app` |
| 9 | Packaging, configuration and redaction | T006, T035, T036, T037 | `ConfigurationValidationIT`, `LogRedactionIT`, `GracefulShutdownIT`, `PackagedArtifactIT`, `PublishedLimitsIT` | `app` |
| 10 | Wire conformance | T038 | `WireConformanceIT` | `app` |
| — | Capacity and per-browser ceilings | T034 | `CapacityIT` | `app` |
| — | A whole game over the wire, and reload recovery | T028 | `GameJourneyIT` | `app` |
| — | Module dependency direction | T003 | `ArchitectureTest` | `app` |

The last three rows are **not** proof areas. `CapacityIT` and `GameJourneyIT` exist because success
criteria S1, S3 and S11 have no other owner; `ArchitectureTest` is a build guard, not acceptance
evidence.

### Success criteria

| Criterion | Owning task | Owner |
|---|---|---|
| S1 complete game under each ruleset | T012, T028 | `RulesetRulesTest` (rules) + `GameJourneyIT` (over the wire) |
| S2 no placement leak | T015 | `ProjectionPrivacyTest` |
| S3 one request restores everything after reload | T028 | `GameJourneyIT` |
| S4 an action sent twice applies once | T019 | `CommandIdempotencyTest` |
| S5 idle, absolute and retention deadlines | T020 | `LifetimeBoundaryTest` |
| S6 non-player cannot distinguish a game from one that never existed | T027 (T029 adds the `streamGameEvents` row) | `AuthorizationIT` |
| S7 simultaneous actions linearize | T019 | `GameSerializationTest` |
| S8 reconnect is current from the first message | T029 | `EventStreamIT` |
| S9 nothing sensitive in logs or problems | T035 | `LogRedactionIT` |
| S10 packaged artifact starts and serves no UI | T037 | `PackagedArtifactIT` |
| S11 ceilings refuse with a retry hint, evict nothing, stay ready | T034 | `CapacityIT` |
| S12 every limit changeable by configuration, enforced and published | T037 | `PublishedLimitsIT` |

### Requirements

| Task | Requirements |
|---|---|
| T001 | — (Constitution § *Scope and Technical Baseline*) |
| T002 | — (research.md D1, D28, D29, D30) |
| T003 | — (Constitution III) |
| T004 | — (Constitution VI) |
| T005 | R57 (the runnable artifact) |
| T006 | R52, R53, R54, R55, R56 |
| T007 | R03, R05 |
| T008 | R12, R20, R21 |
| T009 | R06, R09, R15, R18, R23 |
| T010 | R21, R26 |
| T011 | R11, R12, R14 |
| T012 | R07, R08, R10, R11, R12 |
| T013 | R13, R15, R16 |
| T014 | R32, R34, R60 |
| T015 | R17, R18, R19, R20, R21, R22 |
| T016 | R51 |
| T017 | R32, R39, R41 |
| T018 | R32, R33, R34, R42, R43, R45, R62, R64 |
| T019 | R15, R18, R23, R24, R25 |
| T020 | R42, R43, R44, R45, R46, R47, R48, R63 |
| T021 | R12, R16, R19, R62 |
| T022 | R37, R49, R50 |
| T023 | R01, R02, R03, R04, R55 |
| T024 | R32, R33, R35 |
| T025 | R01, R17, R18, R32, R56 |
| T026 | R01, R34, R45 |
| T027 | R01, R16, R23, R24, R33, R46 |
| T028 | R01 |
| T029 | R17, R27, R28, R31, R33, R39, R42 |
| T030 | R22, R29, R31 |
| T031 | R30, R39, R41 |
| T032 | R38, R41, R52, R61 |
| T033 | R36, R40 |
| T034 | R04, R39, R62 |
| T035 | R58 |
| T036 | R04, R29, R48, R59 |
| T037 | R55, R57 |
| T038 | R01, R17, R49 |

All 64 requirements R01-R64 appear at least once. R60 is additionally a constraint on **every** test in
this feature: no proof may depend on real elapsed time or on sleeping, and the whole suite runs in
milliseconds.

### User stories

| Story | Priority | Tasks | Independently testable once |
|---|---|---|---|
| US1 — host creates a game and shares an invitation | P1 | T008, T015, T018, T025, T026 | T026 |
| US2 — guest redeems the link and joins | P1 | T008, T018, T026 | T026 |
| US3 — both players arrange fleets privately and declare ready | P1 | T007, T009, T010, T011, T013, T015, T027 | T027 |
| US4 — players fire in turns until there is a winner | P1 | T012, T013, T015, T027, T028 | T028 |
| US5 — recovery after reload, lost answer, dropped stream, second tab | P2 | T019, T020, T028, T029, T030, T031 | T031 |
| US6 — players see the result and statistics, then leave | P2 | T013, T016, T021, T027 | T027 |
| US7 — operator runs and observes the packaged server locally | P3 | T006, T036, T037 | T037 |

---

## Gate

None of these exist yet; they are created by this feature (plan.md § *Gate*).

| From | Command | Proves |
|---|---|---|
| `backend/` | `./mvnw -q verify` | DTO generation from `contracts/openapi.yaml`, compile, Enforcer, Spotless check, Surefire units, ArchUnit, Failsafe integration tests |
| `backend/` | `./mvnw spotless:apply` | formatting fix-up (`spotless:check` runs inside `verify`) |
| `backend/` | `./mvnw -pl app -am spring-boot:run` | local run with default configuration |
| `backend/` | `java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` | packaged artifact starts and reports ready (R57, S10) |
| `contracts/` | `npm ci && npm run check` | the contract's own gate, unchanged and still owned by `001-api-contract` |

Expect `BUILD SUCCESS` with Surefire and Failsafe both reporting `Failures: 0, Errors: 0, Skipped: 0`.
**A skipped, timed-out or capability-blocked stage is never green** (Constitution VII).

There is no repository-wide `scripts/verify.sh` yet; it belongs to `004-integration` (Constitution VII).
Until it exists, only the commands above may be cited as this feature's evidence, and no command from
historical `master` work may be borrowed.

---

## Evidence

*Filled in during implementation. A checked box is not evidence; a command's fresh output is. Record
the passing run **and** the mutation run for each task, and distinguish passed, skipped, failed and
environment-blocked checks (Constitution VI; AGENTS.md § *Scope control* rule 5).*

| Task | Date | Command | Result | Mutation proof |
|---|---|---|---|---|
| | | | | |

## Known limitations

*Filled in during implementation.*

## Next unit

`$speckit-analyze` has run across this package and its findings are applied (`AGENTS.md` § *The loop*
step 6). Start with **T001**.
