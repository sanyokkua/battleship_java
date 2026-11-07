## Purpose

Defines local SonarQube analysis and repair, and local Docker image builds including the combined HTTPS image for LAN play and the `local-deploy.sh` LAN flow (US9, US10, R63-R69, S9, S10). Source: the original workflow feature `003-frontend` extension; T024 (verified coverage import) remains open.

## ADDED Requirements

### Requirement: A contributor resolves local code quality findings (US9)

A contributor SHALL be able to run fresh backend and frontend SonarQube scans, export both reports, inspect overall issue lists and quality gates, repair source and test defects, then repeat the scans and the repository gate. Both refreshed projects SHALL have passing quality gates and no remaining open actionable issues, or each exception SHALL be reported with evidence and a decision instead of hidden (S9).

#### Scenario: Inspection

- **WHEN** a contributor runs both documented scans
- **THEN** they inspect project settings, active profiles, issue reports, coverage and quality gates, including existing issues outside the new-code period

#### Scenario: Repair

- **WHEN** an actionable finding is repaired
- **THEN** the original behavior and security boundary still hold, a meaningful test proves the changed behavior where needed, and the refreshed overall issue list no longer reports it

#### Scenario: Unrepairable finding

- **WHEN** a quality gate fails or a finding cannot be repaired as reported
- **THEN** it is left visible with its evidence and decision and no issue is silently dismissed or excluded

### Requirement: Fresh Sonar analyses and repair (R63)

`[user]` Fresh local SonarQube analyses SHALL cover the backend and frontend; the contributor SHALL inspect configuration, profiles, overall open findings, coverage reports and quality gates, repair genuine issues in source and tests (security findings considered in context), export both reports and rerun both analyses. Failed or environment-blocked scans and unresolved findings SHALL be reported explicitly.

#### Scenario: Readable CSRF cookie

- **WHEN** a hotspot concerns the JavaScript-readable `XSRF-TOKEN`
- **THEN** it is judged against the double-submit protocol (Secure, SameSite=Strict, separate HttpOnly session cookie) and left open with evidence if the fix would break the contract

#### Scenario: Target

- **WHEN** the repair is finished
- **THEN** the target is no remaining open actionable findings and both gates passing

### Requirement: No metric gaming (R64)

`[user]` Static-analysis success SHALL NOT come from suppressing, dismissing, marking false positive, excluding code, weakening tests, or changing quality-gate criteria merely to improve metrics. Coverage work SHALL use meaningful observable-behavior tests. Frontend browser journeys SHALL NOT be counted as imported Sonar coverage unless a verified coverage path is implemented.

#### Scenario: Unverified coverage path

- **WHEN** merged browser coverage cannot be mapped to source lines reliably
- **THEN** the frontend coverage gate is not claimed

### Requirement: Local Docker images and complete build (R65)

`[user]` Root scripts SHALL build the complete project and local Docker images: frontend only, backend only, and combined UI plus backend (and the owner-approved Render image). Linux image build stages SHALL produce Linux-compatible artifacts on the local machine. Images SHALL be built for the machine's native architecture; any optional cross-platform command SHALL be documented only if verified. No image SHALL be pushed or published by these scripts.

#### Scenario: Complete build

- **WHEN** the complete build script runs
- **THEN** contract, backend and frontend artifacts are produced or a failed stage is reported without a false success

#### Scenario: Image scripts

- **WHEN** the separate frontend and backend image scripts run with Docker
- **THEN** each image contains its own runnable artifact and has documented configuration and responsibility

### Requirement: Combined image on one HTTPS origin (R66)

`[user]` The combined image SHALL serve UI, API and live updates from one HTTPS origin reachable by another LAN device, with its public origin exactly matching the backend's configured public base URL and the TLS certificate and key mounted at run time. The backend SHALL remain private to the container and the proxy SHALL forward live updates without buffering.

#### Scenario: LAN game

- **WHEN** the combined image runs with local HTTPS configuration
- **THEN** the UI, API and live updates share the exact public origin, invitations use that origin, and another LAN device can create and join a game without changing the origin, cookie or anti-forgery protections

#### Scenario: Missing prerequisite

- **WHEN** a prerequisite, certificate, key or the public port is missing or occupied
- **THEN** the run script explains the failed condition and on stop its child processes terminate

#### Scenario: Protections preserved

- **WHEN** the combined image runs
- **THEN** secure cookies, anti-forgery and origin checks are preserved and missing configuration or an occupied port gives a useful failure message

### Requirement: Reliable process lifecycle and image responsibilities (R67)

`[user]` The combined image SHALL start and stop both processes reliably, report health and handle an unexpected child exit. The frontend-only image SHALL accept an API upstream; the backend-only image SHALL accept its exact public origin. Their responsibilities and settings SHALL be documented. A run script SHALL start the locally built combined image with caller-supplied HTTPS settings (superseded by `local-deploy.sh` and the Render image after the 2026-11-07 consolidation).

#### Scenario: Graceful stop

- **WHEN** the container receives a termination signal
- **THEN** both processes stop gracefully and a failing child makes the container exit

### Requirement: Local guide covers images and LAN (R68)

`[user]` The local guide SHALL give exact script commands, image names and tags, ports, TLS and LAN setup, architecture limits, configuration, graceful stop, troubleshooting and the effect of a shared proxy address on per-address rate limits. The documented LAN flow SHALL be exercised through a real browser and backend where the environment permits; localhost-only proof SHALL be labelled as such.

#### Scenario: Untrusted certificate

- **WHEN** no second trusted device is available
- **THEN** the report labels the proof as limited and does not count it as a LAN pass

### Requirement: local-deploy.sh derives the LAN origin (R69)

`[user]` `./scripts/local-deploy.sh [IPv4 | localhost]` SHALL validate the supplied IPv4 as the authoritative LAN address (or detect it), derive the public HTTPS origin using the configured web port, build the backend JAR and web app if missing, and create or reuse a per-IP certificate and key in `.local-tls/<IPv4>/`.

#### Scenario: IPv4 given

- **WHEN** a LAN IPv4 is supplied
- **THEN** the matching HTTPS origin is derived and a per-address certificate with a matching IP SAN is created or reused

#### Scenario: Localhost and no argument

- **WHEN** no argument is supplied
- **THEN** the LAN address is detected
- **WHEN** `localhost` is supplied
- **THEN** plain HTTP is served on this machine only

#### Scenario: Certificate reuse and trust

- **WHEN** a certificate pair exists
- **THEN** reuse requires it to be unexpired, the script tells the operator to install and trust it on each client device, and it does not install trust or alter other devices

#### Scenario: Conflicting public base URL

- **WHEN** `BATTLESHIP_PUBLICBASEURL` conflicts with the derived origin
- **THEN** the script fails

#### Scenario: Render image

- **WHEN** a Render deployment image is needed
- **THEN** it is built by `scripts/build-docker-render.sh`

### Requirement: Local image outcomes (US10, S10)

A contributor SHALL be able to build the complete project and each image locally, run the combined image with a mounted certificate and key, create and join a game from a second device on the same network through the single public origin, observe live updates, and stop gracefully (US10). The complete build and image builds SHALL succeed and the combined image SHALL complete a real same-origin HTTPS game flow where the environment permits (S10).

#### Scenario: Environment limit

- **WHEN** an untrusted certificate, inaccessible LAN host, unavailable Docker daemon or occupied port prevents a step
- **THEN** it is reported as an environment limitation, never as a pass

#### Scenario: Shutdown and invalid setup

- **WHEN** the container is stopped or started with invalid setup
- **THEN** the documented outcomes occur
