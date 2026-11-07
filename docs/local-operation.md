# Local development and verification

Five scripts live in `scripts/`. Everything else (`Dockerfile`, `config/`) exists only to serve them; each script, image stage and variable is explained in [configuration](configuration.md).

| Script | What it does |
| --- | --- |
| `./scripts/local-build.sh [--docker]` | Installs the exact dependencies, builds, and runs every test and quality gate. |
| `./scripts/local-deploy.sh [IPv4 \| localhost]` | Runs the packaged application so other devices on your LAN can play. |
| `./scripts/build-docker-frontend.sh`, `build-docker-backend.sh`, `build-docker-render.sh [--smoke]` | Build the three Docker images (nothing is pushed). |

## Requirements

Java 25 and Node 24 (major versions only), or Docker with `./scripts/local-build.sh --docker`. The build checks the runtimes, installs the exact contract and frontend lockfiles, installs Playwright Chromium and uses the Maven wrapper.

## Build and test: `local-build.sh`

Run `./scripts/local-build.sh` from any directory; it performs, in order:

1. Java 25 and Node 24 runtime checks.
2. Exact dependency installation (contract, frontend, Playwright Chromium).
3. OpenAPI contract validation.
4. Backend Maven `verify` and the Spotless format check (this also packages `backend/app/target/battleship-app.jar`).
5. Frontend formatting, lint, typecheck, unit tests, build and distribution check (`frontend/dist/`).
6. Browser journeys against the packaged backend.

Any failed stage, or a stage longer than 30 minutes (`LOCAL_BUILD_STAGE_TIMEOUT`, in seconds), fails the run with its name; nothing is skipped. It takes about ten minutes.

`./scripts/local-build.sh --docker` runs the same script inside a Docker build (the `verify` stage of the root `Dockerfile`: JDK 25, Node 24, Chromium), so no local Java or Node is needed. Docker caches a passing result for unchanged sources; pass `--no-cache` to `docker build` yourself to force a rerun. The build produces no artifacts on your machine.

The backend compiler enables removal warnings and treats compiler warnings as errors in production and test sources. Detailed ordinary deprecation diagnostics are not enabled by default because the generated OpenAPI sources use a deprecated API. To inspect them, run `cd backend && ./mvnw clean test-compile -Dmaven.compiler.showDeprecation=true`; this diagnostic build can fail on those warnings. Frontend type checking also covers the browser tests and tool configuration with strict indexed access and unused-code checks.

## Run it on your LAN: `local-deploy.sh`

```sh
./scripts/local-deploy.sh                # LAN address detected automatically, HTTPS
./scripts/local-deploy.sh 192.168.1.50   # or name the address (also if detection picks the wrong network)
./scripts/local-deploy.sh localhost      # this machine only, plain http://localhost:4173
```

The script builds the backend JAR and `frontend/dist` if missing, starts the private backend and `vite preview`, and prints the URL; open exactly that URL. What it does step by step (HTTPS certificate, `BATTLESHIP_PUBLICBASEURL`, ports, binding, `X-Forwarded-For` handling) is described once in [configuration](configuration.md#local-deploysh-step-by-step) and the [trust model](configuration.md#x-forwarded-for-from-browser-to-rate-limit).

**On the LAN.** Install and trust the printed `cert.pem` on **each** device before opening the URL (phones differ; some need an extra full-trust switch); `key.pem` stays on this machine. A changed IP address gets a new certificate and URL. The host's firewall must allow the port; a successful `localhost` check does not prove LAN reachability.

Games remain in memory and are lost when you stop the script. Export `BACKEND_PORT` / `FRONTEND_PORT` to change ports and any `BATTLESHIP_*` variable to change limits before running the script ([variable reference](configuration.md#environment-variable-reference)).

## Docker images

The three `build-docker-*.sh` scripts build `battleship-frontend:local` (nginx: UI plus `/api` proxy, needs `BATTLESHIP_BACKEND_UPSTREAM` to run), `battleship-backend:local` (the API, needs `BATTLESHIP_PUBLICBASEURL` to run) and `battleship-render:local` (both in one `linux/amd64` image, HTTP on `$PORT`). All build in Linux stages from the repository root and push nothing. Stages, headers, health checks, client-address handling and run-time variables are in [configuration](configuration.md#scripts-and-images); keep the backend port private behind HTTPS or nginx.

The Render image is an intentional, owner-approved manual artifact: local builds are manual, the GitHub Actions release publishes it to GHCR ([CI pipeline](ci.md)), and deploying to Render is manual; the repository has no hosting or deployment infrastructure. For the AMD64 build, registry publication, Render service setup, runtime limits, verification and troubleshooting, see the [Render deployment guide](render-deployment.md).

`./scripts/build-docker-render.sh --smoke` also checks the built image; the checks are listed in [configuration](configuration.md#build-docker-rendersh---smoke).

## Repository verification

`./scripts/local-build.sh` is the repository gate described above; run it before declaring work complete. Capture the evidence (the stage output), and report skipped, timed-out, failed and environment-blocked checks distinctly.

## Local SonarQube analysis

SonarQube is a supplementary static analysis, not part of `./scripts/local-build.sh`. Start the existing local server and provide `SONAR_HOST_URL` and `SONAR_TOKEN` in the environment; never commit credentials. Check availability with `sonar-local status`. From the repository root run:

```sh
(cd backend && ./mvnw clean install org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar -Dsonar.qualitygate.wait=true)
(cd frontend && npm run test:coverage && npm run test:browser-coverage && sonar-local scan)
```

The backend Maven reactor supplies Java bytecode, dependencies, source and test roots; generated OpenAPI sources are excluded. JaCoCo instruments the Maven test JVMs and aggregates coverage across the three modules, including integration tests. The clean build keeps coverage fresh, and `install` updates the local reactor dependencies used by analysis; separately launched application JVMs are not instrumented. The frontend configuration analyzes `src`, excluding generated contract types, and identifies `tests` and `e2e` as test code. `test:coverage` creates Vitest unit/component coverage and the test-execution report. `test:browser-coverage` builds the app without minification and with hidden source maps, runs the existing Playwright journeys against the packaged backend, maps Chromium coverage to source, and merges it into `coverage/lcov.info`. It removes the previous LCOV before the browser run, so a failed journey cannot leave a stale merged report. The coverage build's source maps stay in ignored local `dist/`; a normal product build remains minified and omits them. Dependencies and build outputs are outside the analysis scopes. SonarQube imports the reports; it does not run the tests itself.

The dashboard's **Overall Code** tab shows project-wide coverage. **New Code** shows only the current new-code period and can have too few changed lines for a percentage. Local Community Build records one main-branch history per Sonar project; scans of different Git branches share that history. Its edition also limits certain security and branch-analysis features.

After each scan, export results from the repository root with a separate output directory per project:

```sh
SONAR_PROJECT_KEY=local:battleship-backend SONAR_OUTPUT_DIR=.sonar-reports/backend sonar-local report
SONAR_PROJECT_KEY=local:battleship-frontend SONAR_OUTPUT_DIR=.sonar-reports/frontend sonar-local report
```

Scans wait for server processing and the Quality Gate. If the gate fails, still export the report to distinguish a completed analysis from a scanner failure. Inspect findings against the source and run relevant tests after fixes. Reports and scanner work files are ignored by Git.

## Ports and host name

Use the exact URL `local-deploy.sh` prints (`localhost` for a local run) so the request origin matches the backend security policy. The web server proxies to the backend over `127.0.0.1` only.

| Ports (web / backend) | Use |
| --- | --- |
| 4173 / 8080 | `local-deploy.sh` and the default browser-journey stack |
| 4174-4176 / 8081-8083 | Browser-journey stacks with short lifetimes, lowered limits or a backend restart |
| 4193-4201 / 8193-8201 | Browser-journey stacks for the pulse, accessibility and placement journeys |
| 5173 | `npm --prefix frontend run dev` (hot reload; backend started by hand) |

The journeys, `local-deploy.sh` and the Vite config read `FRONTEND_PORT` and `BACKEND_PORT`; the web server and backend are started with the matching `SERVER_PORT` and `BATTLESHIP_PUBLICBASEURL`. The ports of each journey stack are chosen in the test that uses it (`frontend/e2e/`), so stop other processes on these ports before running the journeys.

## Runtime configuration and game lifetime

Backend settings use `BATTLESHIP_*` environment variables; defaults and meanings are in [configuration](configuration.md#localenvexample). The default lifecycle is 15 minutes idle, two hours absolute, and five minutes of terminal-result retention. Games are held in memory and are lost when the backend restarts. Invitations expire after 15 minutes by default.

The journeys configure shorter lifetimes and rate limits through the same existing settings when starting their dedicated backend stacks. They do not use a test-only endpoint or clock control.

## Common failures

- **Port already in use:** stop the process that owns the port, or choose another `FRONTEND_PORT` / `BACKEND_PORT` for `local-deploy.sh`. Browser journeys use their listed fixed ports; stop other stacks first.
- **Wrong runtime:** install Java 25 and Node 24, or use `./scripts/local-build.sh --docker`.
- **Backend does not become ready:** read the backend output above the error and confirm the packaged JAR builds and the port is free.
- **Invitation expired:** create a new game and invitation. Invitations have a bounded lifetime.
- **Game disappeared after restart:** games are ephemeral and are not restored after a backend restart.
- **State-changing request returns 403:** open the app through exactly the printed URL; the backend compares the request `Origin` with `BATTLESHIP_PUBLICBASEURL`. On a LAN device also check that it trusts the certificate. Do not open the app through `127.0.0.1` or another host name.
- **Another device cannot connect:** check the LAN IP the script printed, the host firewall and that both are on the same network.
