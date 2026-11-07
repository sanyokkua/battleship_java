# Developer guide

This guide is a starting point for changing the application. Read the [project guide](index.md) for the game and the table of contents, [architecture](architecture.md) for the runtime shape, and [local operation](local-operation.md) for running, ports and troubleshooting.

## Repository map

| Path | Responsibility |
| --- | --- |
| [`contracts/`](../contracts/) | OpenAPI source, examples and contract checks. |
| [`backend/domain/`](../backend/domain/) | Java game model and rules, independent of Spring. |
| [`backend/application/`](../backend/application/) | Use cases, in-memory registries, lifecycle and player-specific projections. |
| [`backend/app/`](../backend/app/) | Spring Boot entry point, HTTP controllers, security, SSE and configuration. |
| [`frontend/src/`](../frontend/src/) | React app, screens, gateway, per-game state, preferences and styling. |
| [`frontend/tests/`](../frontend/tests/) and [`frontend/e2e/`](../frontend/e2e/) | Frontend unit/component tests and browser journeys. |
| [`scripts/`](../scripts/) | The product gate (`local-build.sh`), the LAN runner (`local-deploy.sh`) and the Docker image builds. |
| [`config/`](../config/) | nginx and entrypoint files for the Docker images, and the example environment variables ([explained](configuration.md)). |
| [`Dockerfile`](../Dockerfile) | Every image, and the Docker form of the gate, as documented build stages ([explained](configuration.md#dockerfile-stages)). |
| [`specs/`](../specs/) | Feature specifications, plans and tasks. |
| [`docs/intent.md`](intent.md), [`docs/design/`](design/) | Product intent and the visual mockup. |

The frontend starts at [`main.tsx`](../frontend/src/main.tsx); routes, screens and state are described in [frontend](frontend.md). The backend starts at [`BattleshipApplication.java`](../backend/app/src/main/java/ua/kostenko/battleship/app/BattleshipApplication.java). Follow a request from its controller in `app/web`, through an application use case, to domain rules and back through the snapshot projector (see [backend](backend.md)). Tests alongside each module show expected behavior and edge cases.

## First local run

Install Java 25 and Node 24, run `./scripts/local-build.sh` once (it creates the backend JAR and `frontend/dist`), then `./scripts/local-deploy.sh localhost` and open `http://localhost:4173`. Use two independent browser sessions (a private window works) to create and join a game. Details, LAN use and ports: [local operation](local-operation.md).

For frontend work with hot reload, start the packaged backend yourself and Vite's dev server, which proxies `/api` to it:

```sh
BATTLESHIP_PUBLICBASEURL=http://localhost:5173 java -jar backend/app/target/battleship-app.jar
npm --prefix frontend run dev        # http://localhost:5173, proxies /api to the backend
```

The backend is a packaged JAR, not a hot-reloading process: after changing backend code run `(cd backend && ./mvnw -DskipTests package)` and restart it. The dev proxy is described in [frontend](frontend.md#same-origin-and-the-dev-proxy).

## Making a change

1. Find the relevant approved feature specification and existing behavior and tests. The contract and backend own gameplay decisions; avoid inventing client-side rules.
2. For a wire change, edit [`contracts/openapi.yaml`](../contracts/openapi.yaml) and its embedded examples. Update backend and frontend consumers together. Generated Java DTOs in `backend/app/target/generated-sources/openapi` and frontend contract types are build outputs; change the contract, not generated files. `npm run generate` in `frontend/` regenerates its types.
3. Put game rules in the domain, orchestration and caller-specific views in application, and HTTP/security mapping in app. Keep UI state and display in the frontend. Use the server's `allowedActions` to decide which game controls are available.
4. Add or update tests for observable behavior at the appropriate boundary. For a cross-layer change, exercise the real application with a browser journey as well as focused tests where useful.
5. Run the relevant checks while working and the repository gate before claiming the feature is complete. Review the resulting diff and update documentation if behavior or commands changed.

## Checks

Run from the repository root unless a directory is shown:

```sh
./scripts/local-build.sh                    # full product gate (--docker: inside Docker)
(cd contracts && npm ci && npm run check)  # OpenAPI lint, examples and type generation
(cd backend && ./mvnw -q verify && ./mvnw spotless:check)
(cd frontend && npm run check)             # format, lint, types, tests, build, dist
(cd frontend && npm run e2e)               # browser journeys against packaged backend
```

The full gate installs its own dependencies (stages and timeouts: [local operation](local-operation.md#build-and-test-local-buildsh)); run the individual commands above after one gate run, which also builds the JAR the browser journeys need. How the journeys work is in [frontend](frontend.md#test-strategy). A failed or timed-out stage is not a pass.

## Debugging a game

Start with the browser's network requests and the API problem response, then follow the operation from the OpenAPI contract to the matching controller and use case. The response is a player-specific view: compare both sessions before assuming hidden data is missing. If a state-changing call returns 403, check that the browser URL exactly matches `BATTLESHIP_PUBLICBASEURL` and that the request includes the anti-forgery header. For stream or stale-state problems, inspect snapshot versions, the stream events and the gateway's recovery path. A restart clears the game and sessions, so create a new game after restarting.
