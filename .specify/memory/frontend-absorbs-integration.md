# Frontend integration memory

Feature `003-frontend` owns the root integration layer and `scripts/local-build.sh` (the gate; `--docker` runs it inside Docker); there is no separate `004-integration` feature. The root gate checks pinned runtime/install requirements, the API contract, backend, frontend, and real-backend browser journeys. A failed or timed-out stage fails the gate.

Local SonarQube scans are supplementary to that gate: Maven analyzes the backend reactor with aggregate JaCoCo coverage, and the CLI analyzes the frontend with merged Vitest and browser LCOV coverage and test-execution reports. Separately launched backend JVMs are not represented in backend coverage. See the local operation guide for scope and commands.

See [`docs/local-operation.md`](../../docs/local-operation.md) for local run commands, ports, configuration, lifecycle limits, and troubleshooting.

`scripts/` holds only `local-build.sh` (gate), `local-deploy.sh` (packaged backend + `vite preview` on one origin, HTTPS on
the LAN because the session cookie is `Secure`) and `build-docker-{frontend,backend,render}.sh`. Docker images are built
in Linux stages from the root `Dockerfile` (hardened: non-root, Alpine, pinned by digest, jlink Java runtime) and
share one nginx template with the security headers and a replaced `X-Forwarded-For`. Ports and the JAR name
(`battleship-app.jar`, Maven `finalName`) have one source each; the e2e stack fixture is `frontend/e2e/fixtures/stack.ts`.

The Render image (`build-docker-render.sh [--smoke]`, `linux/amd64`) is an intentional, owner-approved manual artifact:
Render deployment is manual and the repository has no hosting or deployment infrastructure; GitHub Actions (build, PR gate, tag release to GHCR) is a 003-frontend extension, see `docs/ci.md`.
Render terminates HTTPS and forwards HTTP to `PORT`; the image requires the exact public HTTPS
`BATTLESHIP_PUBLICBASEURL`. The backend is in-memory; use one instance. Until the backend's default-off
trusted-`X-Forwarded-For` property is enabled in the images, create/join address limits are shared behind nginx. See
`docs/render-deployment.md`.

Placement controls may derive advisory valid destinations and orientations from the caller’s visible fleet and published ruleset. The backend remains authoritative for placement, and the UI handles rejected commands and newer snapshots.
