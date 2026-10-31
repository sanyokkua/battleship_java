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

The backend compiler enables removal warnings and treats compiler warnings as errors in production
and test sources. Detailed ordinary deprecation diagnostics are not enabled by default because the
generated OpenAPI sources use a deprecated API. To inspect them, run `cd backend && ./mvnw clean
test-compile -Dmaven.compiler.showDeprecation=true`; this diagnostic build can fail on those warnings.
Frontend type checking also covers the browser tests and tool configuration with strict indexed
access and unused-code checks.

Every stage has a 30-minute timeout by default. Set `VERIFY_STAGE_TIMEOUT` to a different positive number of seconds. Any failed or timed-out stage stops the gate with its stage number and name. `VERIFY_FAIL_STAGE=1`, `2`, `3`, or `4` runs the selected stage command and then injects a failure, for checking the gate's failure path.

## Local SonarQube analysis

SonarQube is a supplementary static analysis, not part of `./scripts/verify.sh`. Start the existing local server and provide `SONAR_HOST_URL` and `SONAR_TOKEN` in the environment; never commit credentials. Check availability with `sonar-local status`. From the repository root run:

```sh
(cd backend && ./mvnw clean install org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar -Dsonar.qualitygate.wait=true)
(cd frontend && npm run test:coverage && sonar-local scan)
```

The backend Maven reactor supplies Java bytecode, dependencies, source and test roots; generated OpenAPI sources are excluded. JaCoCo instruments the Maven test JVMs and aggregates coverage across the three modules, including integration tests. The clean build keeps coverage fresh, and `install` updates the local reactor dependencies used by analysis; separately launched application JVMs are not instrumented. The frontend configuration analyzes `src`, excluding generated contract types, and identifies `tests` and `e2e` as test code. `test:coverage` creates LCOV coverage and a test-execution report from Vitest unit and component tests; browser journeys are not included in that coverage. Dependencies and build outputs are outside the analysis scopes. SonarQube imports these reports; it does not run the tests itself.

The dashboard's **Overall Code** tab shows project-wide coverage. **New Code** shows only the current new-code period and can have too few changed lines for a percentage. Local Community Build records one main-branch history per Sonar project; scans of different Git branches share that history. Its edition also limits certain security and branch-analysis features.

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
