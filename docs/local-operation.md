# Local development and verification

## Requirements

Run `./scripts/install.sh` from the repository root. It checks Java 25 and Node 24, installs the exact contract and frontend lockfiles, installs Playwright Chromium, and validates the Maven wrapper.

## Commands

Run the application against the real backend with `./scripts/dev.sh`. It serves Vite and the API through one origin at `http://localhost:5173` and starts the backend on port 8080.

Run the production-style local application with `./scripts/local-prod.sh`. It builds the frontend and backend, then serves the static frontend and packaged backend through one origin at `http://localhost:4173`.

Run the repository gate with `./scripts/verify.sh`. It performs, in order:

1. Runtime and dependency installation checks.
2. OpenAPI contract validation.
3. Backend Maven verification and Spotless formatting check.
4. Frontend formatting, lint, typecheck, unit tests, build, and distribution checks.
5. Browser journeys against the packaged backend.

Every stage has a 30-minute timeout by default. Set `VERIFY_STAGE_TIMEOUT` to a different positive number of seconds. Any failed or timed-out stage stops the gate with its stage number and name. `VERIFY_FAIL_STAGE=1`, `2`, `3`, or `4` runs the selected stage command and then injects a failure, for checking the gate's failure path.

## Local SonarQube analysis

SonarQube is a supplementary static analysis, not part of `./scripts/verify.sh`. Start the existing local server and provide `SONAR_HOST_URL` and `SONAR_TOKEN` in the environment; never commit credentials. Check availability with `sonar-local status`. From the repository root run:

```sh
(cd backend && ./mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar -Dsonar.qualitygate.wait=true)
(cd frontend && sonar-local scan)
```

The backend Maven reactor supplies Java bytecode, dependencies, source and test roots; generated OpenAPI sources are excluded. The frontend configuration analyzes `src`, excluding generated contract types, and identifies `tests` and `e2e` as test code. Dependencies and build outputs are outside these scopes. Neither build currently generates a coverage report for SonarQube, so coverage is not imported.

After each scan, export results from the repository root with a separate output directory per project:

```sh
SONAR_PROJECT_KEY=local:battleship-backend SONAR_OUTPUT_DIR=.sonar-reports/backend sonar-local report
SONAR_PROJECT_KEY=local:battleship-frontend SONAR_OUTPUT_DIR=.sonar-reports/frontend sonar-local report
```

Scans wait for server processing and the Quality Gate. If the gate fails, still export the report to distinguish a completed analysis from a scanner failure. Inspect findings against the source and run relevant tests after fixes. Reports and scanner work files are ignored by Git.

## Ports and host name

Use `localhost` in the browser so the request origin matches the backend security policy. The Vite proxy uses `127.0.0.1` only for its private backend connection.

| Ports       | Use                                                                    |
| ----------- | ---------------------------------------------------------------------- |
| 5173 / 8080 | Development frontend / backend                                         |
| 5174 / 8084 | Development stack smoke check                                          |
| 4173 / 8080 | Production-style frontend / backend; the default browser-journey stack |
| 4174 / 8081 | Short-lifetime browser-journey stack                                   |
| 4175 / 8082 | Lowered-limit browser-journey stack                                    |
| 4176 / 8083 | Backend-restart browser-journey stack                                  |
| 4177 / 8085 | Production-style stack smoke check                                     |

The stack command uses `FRONTEND_PORT` and `BACKEND_PORT` for local ports. The backend's `SERVER_PORT` and `BATTLESHIP_PUBLICBASEURL` are set by the stack to match the selected backend port and exact `localhost` frontend origin.

## Runtime configuration and game lifetime

Backend settings use `BATTLESHIP_*` environment variables. The full local example is in [`config/local.env.example`](../config/local.env.example); export the values you need before running a local command. The default lifecycle is 15 minutes idle, two hours absolute, and five minutes of terminal-result retention. Games are held in memory and are lost when the backend restarts. Invitations expire after 15 minutes by default.

The journeys configure shorter lifetimes and rate limits through the same existing settings when starting their dedicated backend stacks. They do not use a test-only endpoint or clock control.

## Common failures

- **Port already in use:** stop the process that owns the required port, or choose another `FRONTEND_PORT` / `BACKEND_PORT` for a local stack. Browser journeys use their listed fixed ports.
- **Wrong runtime:** install Java 25 and Node 24, then rerun `./scripts/install.sh`.
- **Backend does not become ready:** check the reported backend address and startup output; confirm the packaged JAR builds and that the port is free.
- **Invitation expired:** create a new game and invitation. Invitations have a bounded lifetime.
- **Game disappeared after restart:** games are ephemeral and are not restored after a backend restart.
- **State-changing request returns 403:** open the app through its configured `http://localhost:<port>` origin. Do not open the frontend through `127.0.0.1` or use a public-base URL that differs from that origin.
