# Current Architecture Smells and Problems

## Purpose

This is a concise, source-backed reference of the architectural smells, correctness risks, security weaknesses, and operational limitations identified in the current Battleship application.

The report is intended to prevent repeating these decisions during a rewrite. It describes the current design; it is not an implementation plan and does not claim that the full verification gate is green.

## Highest-risk findings

| Severity | Finding |
|---|---|
| Critical | `sessionId` and `playerId` function as bearer credentials. There is no authentication or authorization layer; anyone who obtains the IDs can impersonate a player. [docs/index.md](docs/index.md#L80) |
| Critical | State consistency depends on a manually maintained per-session lock map. Locks are created before session existence is checked and are never removed, allowing unbounded memory growth. [GameControllerApiImpl.java](src/main/java/ua/kostenko/battleship/battleship/logic/api/impl/GameControllerApiImpl.java#L79) |
| Critical | The domain aggregate is mutable and leaks its internal collections and state. `GameState` is only syntactically immutable because its object graph remains mutable. [GameImpl.java](src/main/java/ua/kostenko/battleship/battleship/logic/engine/GameImpl.java#L41) |
| Critical | The frontend mock is a second game engine rather than a transport fake and already diverges from production behavior. [MockGameAdapter.ts](frontend/src/adapters/MockGameAdapter.ts#L21) |
| High | SSE broadcasting runs synchronously on the request thread, rebuilds full snapshots through multiple reads, has no backpressure, and retains infinite-timeout emitters. [SessionEventBroadcaster.java](src/main/java/ua/kostenko/battleship/battleship/web/sse/SessionEventBroadcaster.java#L76) |
| High | There is no monotonic state revision. The backend uses wall-clock strings, while the frontend does not compare revisions and can apply stale responses over newer state. [GameState.java](src/main/java/ua/kostenko/battleship/battleship/logic/engine/models/records/GameState.java#L39), [useSessionEvents.ts](frontend/src/hooks/useSessionEvents.ts#L67) |
| High | Public DTOs expose engine records directly. `CellDto.ship` is an engine `Ship`, leaking domain representation into the web contract. [CellDto.java](src/main/java/ua/kostenko/battleship/battleship/web/api/dtos/entities/CellDto.java#L39) |
| High | Client commands are not atomic or serialized. Rotation is remove-then-add, and gameplay clicks remain possible while a shot request is pending. [PreparationScreen.tsx](frontend/src/screens/PreparationScreen.tsx#L269), [GameplayScreen.tsx](frontend/src/screens/GameplayScreen.tsx#L257) |

## Backend architectural smells

- `GameControllerApiImpl` is a god service. It owns validation, locking, persistence sequencing, domain orchestration, exception translation, event publication, and response projections. [GameControllerApiImpl.java](src/main/java/ua/kostenko/battleship/battleship/logic/api/impl/GameControllerApiImpl.java#L75)

- The logic layer depends directly on Spring's `ApplicationEventPublisher`, so the declared framework-independent layering is not actually framework-independent. [GameControllerApiImpl.java](src/main/java/ua/kostenko/battleship/battleship/logic/api/impl/GameControllerApiImpl.java#L6)

- Persistence exposes mutable `Game` objects and delegates atomicity to callers. A future caller can bypass the service lock. [Persistence.java](src/main/java/ua/kostenko/battleship/battleship/logic/persistence/Persistence.java#L25)

- `InMemoryPersistence` has no TTL, capacity limit, cleanup policy, or lifecycle management. The production bean is hard-wired to this implementation. [InMemoryPersistence.java](src/main/java/ua/kostenko/battleship/battleship/logic/persistence/InMemoryPersistence.java#L31), [BeansConfiguration.java](src/main/java/ua/kostenko/battleship/battleship/web/config/BeansConfiguration.java#L36)

- Reads are intentionally unlocked, but SSE constructs a payload from several independent reads. A payload can combine stage, timestamp, opponent state, and board state from different revisions. [SessionEventBroadcaster.java](src/main/java/ua/kostenko/battleship/battleship/web/sse/SessionEventBroadcaster.java#L127)

- `getNumberOfUndamagedCells()` counts every unshot water cell instead of only unshot ship cells. The public contract describes an alive-ship-cell count. [FieldManagementImpl.java](src/main/java/ua/kostenko/battleship/battleship/logic/engine/FieldManagementImpl.java#L211)

- The frontend mock correctly filters ship cells, so tests can pass while production returns different values. [MockGameAdapter.ts](frontend/src/adapters/MockGameAdapter.ts#L127)

- `hasShot` represents both player shots and automatically marked moat cells after ship destruction. This conflates distinct domain events and forces the UI to infer meaning. [FieldManagementImpl.java](src/main/java/ua/kostenko/battleship/battleship/logic/engine/FieldManagementImpl.java#L244)

- `Cell` combines placement availability, ship visibility, and combat state. One model is serving domain state, persistence state, and multiple API projections.

- `GameControllerApi` returns engine objects instead of application DTOs and commands. [GameControllerApi.java](src/main/java/ua/kostenko/battleship/battleship/logic/api/GameControllerApi.java#L3)

- Board dimensions are static engine constants consumed directly by web mapping code, preventing genuine per-edition or future variable-board support. [ControllerUtils.java](src/main/java/ua/kostenko/battleship/battleship/web/api/ControllerUtils.java#L27)

- Validation is duplicated across `ValidationUtils`, domain utilities, DTO binding, and frontend services. There is no complete Bean Validation or framework binding-error contract. [ValidationExceptionHandler.java](src/main/java/ua/kostenko/battleship/battleship/web/exceptions/ValidationExceptionHandler.java#L29)

- All domain failures are returned as HTTP 400, including not-found, conflict, wrong-stage, and wrong-turn cases. This weakens HTTP semantics and forces clients to depend on custom error codes.

- Internal exception messages are returned directly to clients. [InternalExceptionHandler.java](src/main/java/ua/kostenko/battleship/battleship/web/exceptions/InternalExceptionHandler.java#L29)

- Ship IDs are generated inside a static domain utility with `UUID.randomUUID()`, bypassing the injected `IdGenerator` and hurting deterministic tests and replay. [ShipUtils.java](src/main/java/ua/kostenko/battleship/battleship/logic/engine/utils/ShipUtils.java#L40)

- Identity is placed in SSE URL paths, making future authentication, authorization, and revocation harder.

## Frontend architectural smells

- `GameAdapter` is too broad: lifecycle, preparation, gameplay, polling markers, and SSE transport are all one interface. [GameAdapter.ts](frontend/src/adapters/GameAdapter.ts#L22)

- `HttpGameAdapter` adds little value over `BackendRequestService`; transport concerns are split across two layers without a strong abstraction boundary. [HttpGameAdapter.ts](frontend/src/adapters/HttpGameAdapter.ts#L38)

- API types are manually duplicated in TypeScript instead of being generated from OpenAPI. Important fields remain plain strings, and SSE JSON is cast without runtime validation. [ApplicationTypes.ts](frontend/src/logic/ApplicationTypes.ts#L43), [HttpGameAdapter.ts](frontend/src/adapters/HttpGameAdapter.ts#L140)

- Backend and frontend error catalogs drift. The backend emits codes such as `OPPONENT_NOT_FOUND`, `PLAYER_NOT_FOUND`, `SESSION_FULL`, and `SHIPS_NOT_ALL_PLACED`, while the frontend allowlist omits them. [ValidationExceptionHandler.java](src/main/java/ua/kostenko/battleship/battleship/web/exceptions/ValidationExceptionHandler.java#L60), [errorMapping.ts](frontend/src/widgets/feedback/errorMapping.ts#L7)

- `axiosRetry` is applied globally, including mutation requests. A network ambiguity can replay a placement, removal, ready, or shot operation without idempotency protection. [BackendRequestService.ts](frontend/src/services/BackendRequestService.ts#L24)

- Client state is split across independent localStorage keys. Corrupt player JSON can crash parsing, and session/player/stage values can become inconsistent. [GameBrowserStorage.ts](frontend/src/services/GameBrowserStorage.ts#L42)

- Routing trusts locally persisted stage instead of revalidating the server. Stale, expired, or tampered browser state can select the wrong screen. [StageGuard.tsx](frontend/src/routing/StageGuard.tsx#L29)

- Hooks implement ad-hoc server-state management without central caching, cancellation, request sequencing, or stale-response protection. Fallback errors are often only logged. [useSessionEvents.ts](frontend/src/hooks/useSessionEvents.ts#L75)

- `PreparationScreen` uses a single mutable promise resolver for action completion. Concurrent actions can overwrite the resolver and leave one caller waiting indefinitely. [PreparationScreen.tsx](frontend/src/screens/PreparationScreen.tsx#L196)

- Rotation is a client-side two-request transaction. If removal succeeds and placement fails, the ship is permanently removed. [PreparationScreen.tsx](frontend/src/screens/PreparationScreen.tsx#L278)

- Gameplay has no in-flight shot guard. A rapid double click can issue multiple shots before the first response updates state. [GameplayScreen.tsx](frontend/src/screens/GameplayScreen.tsx#L257)

- After a successful shot, the hook performs a separate GET. If the shot commits but the GET fails, the UI reports failure and a retry can produce `CELL_ALREADY_SHOT`. [useGameplay.ts](frontend/src/hooks/useGameplay.ts#L79)

- Fleet composition, total ship count, board size, and size-to-type mappings are duplicated across backend configuration, mock adapter, preparation screen, results screen, and i18n helpers. [editionCompositions.ts](frontend/src/i18n-support/editionCompositions.ts#L11), [ResultsScreen.tsx](frontend/src/screens/ResultsScreen.tsx#L14)

- Gameplay progress bars infer the maximum from the first observed state. Reloading mid-game produces an incorrect baseline. [GameplayScreen.tsx](frontend/src/screens/GameplayScreen.tsx#L61)

- The mock SSE implementation invokes callbacks synchronously and silently succeeds for unknown sessions or players, unlike the real asynchronous HTTP/SSE path. [MockGameAdapter.ts](frontend/src/adapters/MockGameAdapter.ts#L494)

- The frontend supports configurable subpaths for assets and routing, but API and SSE URLs remain root-relative and the backend fallback controller only handles root deployment. [vite.config.ts](frontend/vite.config.ts#L8), [BackendRequestService.ts](frontend/src/services/BackendRequestService.ts#L39), [IndexController.java](src/main/java/ua/kostenko/battleship/battleship/web/controllers/IndexController.java#L25)

- There is no top-level React error boundary. A malformed localStorage payload or unhandled render exception can take down the entire SPA. [App.tsx](frontend/src/App.tsx#L50)

## Delivery and operational smells

- Docker builds with `mvn clean package`, not the full verification gate, so integration tests, OpenAPI generation, and verification checks are not necessarily part of image creation. [Dockerfile](Dockerfile#L27)

- The project has no CI pipeline, no CORS configuration, and no persistence beyond one JVM. These are documented scope decisions, but they limit the application to a controlled single-instance environment. [README.md](README.md#L181)

- Maven pins Node 24.18.0, but `frontend/package.json` declares no supported Node engine range. Direct frontend development can therefore use an incompatible runtime. [README.md](README.md#L47)

## Verification limitations from the audit

- The working tree was clean during the audit.
- The frontend production build passed.
- The unrestricted frontend test run reported 510 passing tests and one default-timeout failure in the ready-flow test. The same test passed in isolation with a longer timeout, so this was not classified as a confirmed product defect.
- Frontend lint was blocked by a broken installed ESLint dependency: `../../shared/text-table` was missing.
- Maven tests were blocked by Java 25 Mockito/Byte Buddy self-attach on the host.
- The full `scripts/verify.sh` gate was not claimable as green.

## Rewrite priorities

1. Replace ID-as-authorization with anonymous player capability tokens.
2. Make the game aggregate immutable and enforce per-session atomic commands.
3. Put TTL, capacity, cleanup, and connection ownership into one bounded session-store component.
4. Define monotonic game versions and authoritative snapshots.
5. Separate domain models, application commands, and API DTOs.
6. Generate frontend types and clients from the API contract.
7. Replace the second frontend game engine with contract fixtures or a fake transport.
8. Make mutations idempotent and return the authoritative updated view.
9. Move realtime delivery off the request thread and add ordering, reconnect, and backpressure rules.
10. Remove duplicated fleet and board configuration from the frontend.
11. Add server revalidation, request cancellation, command serialization, and a top-level error boundary.
12. Make resource limits, expiry behavior, restart loss, and verification gates explicit product and operational contracts.
