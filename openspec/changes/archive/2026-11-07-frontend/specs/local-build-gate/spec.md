## Purpose

Defines the root integration layer: install checks, development and production-style local runs, readiness reporting, the single verification gate, configuration-only short lifetimes, output hygiene, the run guide and the proof rules that forbid fake backends (US8, R45-R56, S7). Source: the original workflow feature `003-frontend`. Commands reflect the 2026-11-07 script consolidation (`local-build.sh`, `local-deploy.sh`).

## ADDED Requirements

### Requirement: A contributor runs and verifies everything locally (US8)

From a clean clone with the pinned runtimes, the documented commands for development, production-style local run and the single verification gate SHALL work. From a clean clone the documented commands SHALL start development and production-style runs and the single verification command SHALL pass end to end; a deliberately broken stage SHALL make it fail (S7).

#### Scenario: Development

- **WHEN** the backend is running and the development command starts the frontend
- **THEN** the browser reaches the API and live updates through one origin

#### Scenario: Production-style

- **WHEN** the production-style command runs
- **THEN** the built static app and the packaged backend run as separate artifacts behind one origin, with no development address inside the built app

#### Scenario: Verification command

- **WHEN** the verification command runs
- **THEN** it runs the contract check, backend gate, frontend checks and build, and browser journeys against the packaged backend, in that order, and any failed, skipped or blocked stage makes the overall result fail

### Requirement: One root install and runtime check (R45)

One root install command (now the first stage of `./scripts/local-build.sh`) SHALL check the pinned runtimes (Java major 25, Node major 24), install the contract and frontend lockfiles exactly with `npm ci`, install Playwright Chromium, and validate the backend build wrapper, without global tools. Each failure SHALL print the step name and exit non-zero.

#### Scenario: Wrong runtime

- **WHEN** `node` reports major 23
- **THEN** the script exits non-zero naming the runtime step

### Requirement: One development command (R46)

One development command SHALL run the frontend against a locally running backend through one origin, including live updates.

#### Scenario: Live updates in development

- **WHEN** a game is created through the development origin
- **THEN** the first `snapshot` event arrives through the proxy

### Requirement: One production-style local command (R47)

One production-style local command SHALL serve the built frontend and the packaged backend as separate artifacts behind one local same-origin static server that forwards API and live-update traffic unbuffered and serves the app for its deep links. The built frontend SHALL contain no development address. R66 defines the separate LAN container origin.

#### Scenario: Deep link reload

- **WHEN** `/games/abc` or `/join/abc` is reloaded
- **THEN** the app's `index.html` is served

#### Scenario: Dev address in dist

- **WHEN** a `dist/` file contains `http://localhost:8080`
- **THEN** the dist check fails

#### Scenario: Same origin

- **WHEN** a non-container command runs
- **THEN** the hostname is `localhost` and the backend's public base address equals the frontend origin, because the backend accepts state-changing requests only from that exact origin

### Requirement: Readiness and start-up diagnostics (R48)

Each started process SHALL report readiness from the backend health operation before dependent steps run; a start-up failure SHALL name the component, the address and a next diagnostic step. Only processes the command started SHALL be stopped.

#### Scenario: Occupied port

- **WHEN** a required port is busy
- **THEN** the message names the component, the address and how to find the occupant, and nothing else is stopped

### Requirement: One root verification command (R49)

One root verification command (`./scripts/local-build.sh`; `--docker` runs it inside a Docker build) SHALL run, in order: contract check, backend gate, frontend static checks and build, browser journeys against the packaged backend. A failed, skipped, timed-out or blocked stage SHALL fail the run.

#### Scenario: Broken stage

- **WHEN** a stage is forced to fail
- **THEN** the overall exit code is non-zero

### Requirement: Short lifetimes by configuration only (R50)

Short expiry, presence interval and rate limits for browser journeys SHALL be configured through the backend's existing settings at start-up; there SHALL be no test-only endpoint, clock control or backdoor. Restart loss SHALL be proven by stopping and starting the real process.

#### Scenario: Restart

- **WHEN** the backend process is stopped and started mid-game
- **THEN** the client shows "no longer available"

### Requirement: Output hygiene (R51)

Local command output SHALL name the component, lifecycle outcome and the backend's correlation id for failures, and SHALL NOT include cookies, invitation links, secrets, board content or raw request bodies.

#### Scenario: Redaction

- **WHEN** a log line carries `Set-Cookie`, `Cookie`, `#invite=` or `XSRF-TOKEN=` values
- **THEN** the values are removed

### Requirement: Local-run guide (R52)

A short local-run guide (`docs/local-operation.md`) SHALL cover the commands, ports, configuration, restart and expiry limits, and the common failures (port in use, wrong runtime, backend down, expired invite, restart).

#### Scenario: Troubleshooting

- **WHEN** a contributor hits a port conflict
- **THEN** the guide names the failure and the fix

### Requirement: No fake backend (R53)

No test SHALL replace the backend with recorded game data, a fake engine or reimplemented rules. Every flow (create, join, place, play, live update, leave, resign, expiry, restart, recovery) SHALL be proven by browser journeys against the packaged backend using isolated browser contexts for the two players. `route.fulfill` SHALL be forbidden in `e2e/`; the only interceptions SHALL be `route.fetch()` then `route.abort()` (lost answer) and plain `route.abort()` (network drop).

#### Scenario: Lint rule

- **WHEN** an e2e file calls `route.fulfill`
- **THEN** lint fails

### Requirement: Data-free behaviour may be unit-tested (R54)

Behaviour that involves no game data SHALL be allowed to be unit-tested alone, and advisory placement checks SHALL be allowed focused tests, with real-backend journeys establishing integration: translation key parity, preference persistence, sound/haptic no-op on denial, QR rendering of a given string, rejection of an unreadable answer (R23), and the snapshot version gate (decision B1).

#### Scenario: QR

- **WHEN** a string is given to the QR component
- **THEN** a canvas is rendered with its accessible label

### Requirement: Compact journey matrix (R55)

Browser journeys SHALL cover a named compact matrix rather than every combination: each ruleset's defining behaviour once; copy link, QR and replace invitation once; one invitation race; one action race; ready once; leave, resign, abandoned view and leave-results once each; expiry, presence extension and restart once each; one rate-limit refusal under a lowered limit; stream recovery once; the three layouts, touch-only use and keyboard/screen-reader semantics once; offline shell once.

#### Scenario: Matrix coverage

- **WHEN** the journeys run
- **THEN** each named item has a journey in `smoke`, `settings`, `create`, `waiting`, `invitation`, `placement`, `play`, `results`, `leaving`, `lifetime`, `recovery`, `accessibility` or `offline`
