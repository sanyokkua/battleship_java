## Notes

Converted from the original workflow `the original 002-backend artifacts` (Status: complete, T001-T038). Original ids T001-T038 and checked state are preserved. Every task ships its own proof; done means a named mutation makes the gate fail (AGENTS.md rule 5). Tasks list: Covers (requirement ids), Depends, key deliverable. Phases follow architectural layers. MVP boundary: T001-T028. The source file contained 38 tasks, all checked.

## 1. Reactor, toolchain and the generator spike

- [x] 1.1 T001 Create the three-module Maven reactor and wrapper (`backend/pom.xml`, `mvnw`, domain/application/app poms): Boot 4.1.1 parent, Java 25, Surefire/Failsafe 3.5.6, `test-jar` sharing, `repackage` for `battleship-app-1.0.0-SNAPSHOT`. Covers R57. Depends: none. Mutation: bad parent version fails resolution.
- [x] 1.2 T002 Generate wire DTOs from the contract (openapi-generator 7.25.0, D28) and prove the spike in `GeneratedModelSpikeTest`: 29 object classes, 7 enums, `GameId`/`DisplayName`/`Instant` inline, four `Command` variants by `type`, 18 `ProblemCode` values round-trip, millisecond UTC format, 13 `allOf` wrappers inline. Covers D1, D28-D30. Depends: T001. Abort condition: fallback to hand-written records for what fails.
- [x] 1.3 T003 [P] Enforce module direction with Maven Enforcer `bannedDependencies` plus `ArchitectureTest` (four bytecode rules over main classes). Covers Constitution III. Depends: T001.
- [x] 1.4 T004 [P] Add Spotless 3.9.0 with palantir-java-format, generated sources excluded. Covers Constitution VI. Depends: T001, T002.
- [x] 1.5 T005 Bootstrap `BattleshipApplication`, `spring.threads.virtual.enabled=true`, `@EnableScheduling`; `ApplicationContextTest`. Covers R57, D6. Depends: T001.
- [x] 1.6 T006 [US7] Bind and validate all 23 configuration keys in `BattleshipProperties` (`@Min(1)`, `@PostConstruct` base-URL check); `ConfigurationValidationIT`. Covers R52, R53, R54, R55 (single source), R56. Depends: T005.

## 2. Domain: rules, rulesets and the transition

- [x] 2.1 T007 [US3] Publish the two rulesets and board primitives (`Coordinate`, `Orientation`, `Seat`, `Phase`, `FleetEntry`, `Ruleset`, `Rulesets`); `RulesetCatalogTest`. Covers R03, R05. Depends: T001.
- [x] 2.2 T008 [US1] [US2] Create and join the game aggregate (`Ship`, `Board`, `PlayerState`, `Shot`, `Outcome`, `Timeline`, `GameState.create`/`withGuest`, ship ids `s01`...); `GameCreationTest`. Covers R12, R20, R21. Depends: T007.
- [x] 2.3 T009 [US3] Implement `PLACE_SHIP` and `REMOVE_SHIP`, `GameCommand`, `Transition`, `Rejection`, `GameRules.apply`; four distinct refusals, no-op placement accepted without version bump; `PlacementRulesTest`. Covers R06, R09, R15, R18, R23. Depends: T008.
- [x] 2.4 T010 [US3] Implement `PLACE_FLEET_RANDOMLY` and `CLEAR_FLEET`, `RandomSource` in domain, attempt-bounded search; `RandomArrangementTest`, `SeededRandomSource`. Covers R21, R26. Depends: T009.
- [x] 2.5 T011 [US3] Implement `READY`, start of play and first turn by ready order with seeded tie-break; `ReadyAndFirstTurnTest`. Covers R11, R12, R14. Depends: T010.
- [x] 2.6 T012 [US4] Implement `FIRE`, `RESIGN` and both endings, extra turn, revealed water, turn and shot-decision sampling; `RulesetRulesTest` (proof area 1). Covers R07, R08, R10, R11, R12, S1. Depends: T011.
- [x] 2.7 T013 [US3] [US4] [US6] Compute and enforce the allowed-action table in `AllowedActions`; `AllowedActionsTest`, `RefusedCommandStabilityTest` (proof area 2). Covers R13, R15, R16. Depends: T012.

## 3. Application: ports, projection, registry, use cases

- [x] 3.1 T014 [P] Declare `TimeSource` and `SecretGenerator` ports with production implementations and `MutableTimeSource`, `FixedSecretGenerator` test doubles. Covers R32, R34, R60. Depends: T005, T010.
- [x] 3.2 T015 [US1] [US2] [US3] [US4] Build `SnapshotProjector` and view records (`SnapshotView`, `BoardView`, `PlayerView`, `ShotView`); `ProjectionPrivacyTest` (proof area 3). Covers R17, R18, R19, R20, R21, R22, S2. Depends: T013, T014.
- [x] 3.3 T016 [US6] Project finished-game statistics in `StatisticsView` from `Timeline`; `StatisticsTest`. Covers R51. Depends: T015.
- [x] 3.4 T017 Move game and session ownership into `application/registry` (`GameRegistry`, `GameSlot`, `SessionRegistry`, `SessionRecord`); remove `GameSlotStore`; retain `GameSlot.contextFor`. Covers R32, R39, R41; revised Decision A1. Depends: T006, T014.
- [x] 3.5 T018 [US1] [US2] Create, join and re-invite use cases; display-name validation; per-browser cap. Covers R32, R33, R34, R42, R43, R45, R62, R64. Depends: T017, T015.
- [x] 3.6 T019 [US5] Serialize every action in `CommandUseCase` under one slot lock; `CommandIdempotencyTest`, `GameSerializationTest` (proof areas 4, 5). Covers R15, R18, R23, R24, R25, S4, S7. Depends: T018.
- [x] 3.7 T020 [US5] Enforce every deadline (inclusive) and the presence throttle; `ExpireGamesUseCase`; `LifetimeBoundaryTest` (proof area 6). Covers R42, R43, R44, R45, R46, R47, R48, R63, S5. Depends: T019.
- [x] 3.8 T021 [US6] Implement leaving in `LeaveGameUseCase` (four branches); `LeaveGameTest`. Covers R12, R16, R19, R62. Depends: T020.

## 4. Web adapters, problems, session and CSRF

- [x] 4.1 T022 Map every failure to the generated `Problem` in `ProblemAdvice` (Decision A3). Covers R37, R49, R50. Depends: T002, T006, T018.
- [x] 4.2 T023 Serve `getMeta`, `listRulesets`, `getHealth` (unauthenticated). Covers R01, R02, R03, R04, R55. Depends: T022, T007.
- [x] 4.3 T024 Build the security chain: stateless `SecurityFilterChain`, `SessionCookieFilter`, CSRF cookie and plain handler; `RequestSecurityIT`, `AuthorizationIT`. Covers R32, R33, R35. Depends: T023, T017.
- [x] 4.4 T025 Assemble `SnapshotDtoAssembler` and serve `createGame` and `getGame`; `GameControllerIT`. Covers R01, R17, R18, R32, R56, S3. Depends: T024, T018.
- [x] 4.5 T026 [US2] [US1] Serve `joinGame` and `replaceInvitation`; `JoinIT`. Covers R01, R34, R45, R63. Depends: T025.
- [x] 4.6 T027 [US3] [US4] [US5] [US6] Serve `sendCommand`, `sendPresence`, `leaveGame`; `CommandControllerIT`; S6 owner. Covers R01, R16, R23, R24, R33, R46, S6. Depends: T026, T019, T020, T021.
- [x] 4.7 T028 [US4] [US5] Prove a whole game over the wire in `GameJourneyIT`. Covers R01, S1, S3. Depends: T027.

## 5. Realtime

- [x] 5.1 T029 [US5] Open the event stream and deliver per-seat snapshots; `SnapshotPublisher`, `SseHub`; `EventStreamIT` part 1. Covers R17, R27, R28, R31, R33, R39, R42, S6 (stream row), S8. Depends: T028.
- [x] 5.2 T030 [US5] Close streams deliberately (`REPLACED`, `GAME_UNAVAILABLE`) and track connection state via `SubscribeUseCase`; `EventStreamIT` part 2. Covers R22, R29, R31. Depends: T029.
- [x] 5.3 T031 [US5] Bound every stream: heartbeat, maximum lifetime, coalescing slot, exactly-once permit release; `EventStreamIT` part 3. Covers R30, R39, R41. Depends: T030.

## 6. Abuse limits, hardening and operation

- [x] 6.1 T032 Rate-limit every operation class in `RateLimitFilter` (fixed 60 s window, bounded LRU table); `RateLimitIT`. Covers R38, R41, R52, R61. Depends: T031.
- [x] 6.2 T033 Refuse cross-site, oversized and mistyped requests (`SiteIsolationFilter`, `RequestSizeFilter`, 415); `RequestSecurityIT`. Covers R36, R40. Depends: T032.
- [x] 6.3 T034 Refuse at every capacity ceiling without evicting; `CapacityIT`. Covers R04, R39, R62, S11. Depends: T033.
- [x] 6.4 T035 Log structurally and redact absolutely; `LogRedactionIT`. Covers R58, S9. Depends: T034.
- [x] 6.5 T036 [US7] Drain and shut down honestly (`DRAINING` first, no `closed` event, 503 on reconnect); `GracefulShutdownIT`. Covers R04, R29, R48, R59. Depends: T035.

## 7. Packaging and wire conformance

- [x] 7.1 T037 [US7] Deliver the packaged backend-only artifact; `PackagedArtifactIT`, `PublishedLimitsIT`. Covers R55, R57, S10, S12. Depends: T036.
- [x] 7.2 T038 Assert every wire body against the contract in `WireConformanceIT` (proof area 10) and fix what it exposes. Covers R01, R17, R49. Depends: T037.

## 8. Coverage maps (condensed)

- [x] 8.1 Proof areas 1-10 each have an owning task and test class (see design.md Validation): 1 T012, 2 T013, 3 T015, 4 T019, 5 T019, 6 T016/T020, 7 T024/T025/T027/T032/T033, 8 T029-T031, 9 T006/T035/T036/T037, 10 T038.
- [x] 8.2 Success criteria S1-S12 each have an owning task: S1 T012/T028, S2 T015, S3 T028, S4 T019, S5 T020, S6 T027 (T029), S7 T019, S8 T029, S9 T035, S10 T037, S11 T034, S12 T037.
- [x] 8.3 All 64 requirements R01-R64 appear in at least one task; user stories map to tasks: US1 T008/T015/T018/T025/T026, US2 T008/T018/T026, US3 T007/T009-T011/T013/T015/T027, US4 T012/T013/T015/T027/T028, US5 T019/T020/T028-T031, US6 T013/T016/T021/T027, US7 T006/T036/T037.
