# Local integration high-level solution design and plan input

## Local topology and artifacts

Maintain three separately produced artifacts:

```text
contracts/                 versioned OpenAPI, event schemas, examples, generator configuration
backend/                   Spring Boot service and backend test suite
frontend/                  React static build, runtime configuration template, cached-shell PWA assets
scripts/                   root orchestration, redaction-safe process control, verification helpers
config/local.env.example   non-secret matching frontend/backend local configuration
docs/local-operation.md    commands, health, configuration, restart, expiry, and troubleshooting guide
scripts/verify.sh          one authoritative root verification entry point
```

The backend owns an explicit local HTTP origin and implements the already contracted health/readiness endpoint. The frontend owns a separate Vite development origin and a static-build preview origin. In development, `frontend/vite.config.ts` proxies only documented API, SSE, and health paths to the configured local backend origin. In production-style local mode, the static server serves the built frontend as a separate artifact; the browser reads a runtime configuration document containing only a validated API origin and public application base path. The build must not bake a development proxy URL into production output.

One configuration check joins the frontend origin and public application path and requires that value to equal the backend's public frontend base URL, including a subpath. It also requires the frontend and API origins to use the same hostname so both host-only cookies work across local ports, and requires the backend's credentialed CORS allowlist to contain exactly the frontend origin. `http://localhost:<port>` is accepted only by the local profile; the production security model remains HTTPS. This prevents creation/rotation from returning an invitation that misses the deployed application path.

The PWA service worker caches only immutable frontend shell assets. Runtime configuration is served no-store and remains network-only. The worker neither stores nor replays runtime configuration, API payloads, command requests, SSE events, cookies, invitation fragments, or authenticated browser state.

## Root commands

Document and implement these root scripts with deterministic exit codes and no hidden global tooling:

```text
./scripts/install.sh            validate runtimes; install contracts and frontend lockfiles
./scripts/generate-contract.sh  generate the frontend-owned contract output
./scripts/check-contract.sh     lint the contract and fail on generation drift
./scripts/test-backend.sh       run backend unit and integration tests with backend/mvnw
./scripts/check-frontend.sh     run typecheck, lint, unit/component tests, and static build
./scripts/dev.sh                start backend and Vite development proxy with health reporting
./scripts/local-prod.sh         start backend plus separate static frontend artifact and runtime config
./scripts/e2e-critical.sh       run focused isolated-browser contexts against local production-style artifacts
./scripts/verify.sh             run contract, backend, frontend, and critical E2E stages in that order
```

There is no root npm workspace and no fourth product. The shell entry points call `npm --prefix contracts`, `backend/mvnw`, and `npm --prefix frontend`; a small Node standard-library helper is allowed only when shell would make safe process control harder. Execution consumes the exact product pins already accepted in features 001 and 003, including Node 24.19.0, React/React DOM 19.2.8, Vite 8.2.1, TypeScript 6.0.3, Zod 4.4.3, TanStack Query 5.101.4, Vitest 4.1.10, and Playwright 1.62.1. It neither refreshes nor reselects dependencies. The owning lockfiles contain exact versions without ranges, and commands reject an unsupported runtime before writing generated output or starting tests.

`scripts/verify.sh` is the only completion gate. It creates isolated temporary state for each stage, tears down only processes it started, checks bounded startup timeouts, and reports generated-artifact changes explicitly. It never declares success after a skipped, failed, timed-out, or environment-blocked check.

## Process, configuration, health, and logging

`scripts/dev.sh` and `scripts/local-prod.sh` start children with explicit addresses and ports, wait on the contracted `GET /actuator/health`, stream prefixed logs, and terminate only their own process group on exit. Configuration is read from the checked-in non-secret local example plus environment overrides. The validator accepts only absolute `http://localhost:<port>` origins in the local profile and a normalized public path; it rejects query, fragment, credentials, malformed origins, base-path disagreement, and different API/frontend hostnames. The script prints configuration keys and safe origins but redacts values that could be secrets. The local static server never logs request headers or cookies.

Health output has component, URL, readiness state, and safe diagnostic. Backend logs use a generated correlation hash and stable problem category. Frontend harness logs name the test and browser context only. Process output is scrubbed before failure summaries are written. The local guide explains port conflicts, wrong Node major, stale generated output, invalid runtime configuration, backend unavailable, blocked browser binding, expired invite, backend restart, and SSE recovery.

## Contract and browser integration

`scripts/check-contract.sh` validates OpenAPI/event schemas, examples, generated TypeScript, runtime schemas, and public problem shapes. It rejects hand edits under generated paths and tests that unknown additive response properties remain tolerated while invalid required values fail safely.

`scripts/e2e-critical.sh` runs against production-style local artifacts and uses isolated browser contexts to preserve cookie-separated anonymous identities. The normal journey uses one host and one guest context. The invitation-race fixture creates an owner through the public contract in a separate request context, discards that setup identity, then races exactly two potential guest browser contexts. Tests drive only the public UI and published contract; there is no test-only HTTP endpoint, local-storage stage override, frontend mock engine, direct registry access, or player identifier used as authority.

Exact 15-minute, two-hour, and five-minute lifecycle boundaries are proved in backend tests with the injected monotonic clock. The live integration expiry journey starts the unchanged packaged JAR with a documented local-test-only shorter idle configuration and waits for normal expiry behavior; outside that profile the default remains 15 minutes. Restart evidence stops and starts the real process. This keeps browser tests fast without a clock-control route or production backdoor.

The suite's representative matrix is intentionally compact:

| Risk | Representative coverage | Proving test |
|---|---|---|
| Distinct ruleset behavior | One focused two-context live sequence per ruleset | Sea Battle hit retains turn and sink reveals separately marked moat; Classic hit changes turn |
| Invitation privacy and seat race | Public setup request, then exactly two isolated potential guests | Fragment stripping and one-winner redemption E2E |
| Cookie and CSRF topology | Same `localhost` hostname on separate ports, then one mismatched-host negative configuration | Meta cookie/header/Origin success and startup rejection |
| Command idempotency and ordering | One active player and one observing player | Duplicate/race command plus game-version convergence E2E |
| Leave and resignation | One pre-play abandonment, one active resignation, and one terminal leave retry | Snapshot/version, `resign-required`, stream close, and replayed 204 E2E |
| TTL and restart loss | Backend clock tests plus one short-profile waiting game and one restarted active game | Exact boundary tests, normal live expiry, and process restart E2E |
| SSE recovery | One backgrounded or disconnected observer | Reconnect plus ordered snapshot E2E |
| Responsive and accessibility | Desktop, narrow portrait, short landscape; keyboard and screen-reader semantics | Playwright viewport and accessibility E2E |
| Safe static shell | Offline static asset request plus disabled command | Service-worker integration test |

This covers high-risk interactions without multiplying every journey by every browser, language, theme, viewport, and ruleset. Unit/component suites cover translation parity, themes, forced colours, reduced motion, audio/haptic no-op behaviour, validation, and error views; browser suites sample the resulting integrated experience.

## Expected artifacts and plan order

1. Add root shell entry points, supported-runtime guard, contract generation/check orchestration, and generated-path ownership checks; do not add a root package or workspace.
2. Consume and check the existing backend health/readiness implementation and the frontend-owned Vite proxy and runtime-configuration loader. Add only the root matching-configuration example and validation, runtime-config injection/serving, and the separate production-style static serving path; do not recreate or take ownership of frontend configuration code.
3. Add process orchestration with bounded startup, safe teardown, structured redacted logs, and local-operation documentation.
4. Add root verification sequencing and stage-specific failure reporting.
5. Add focused two-context E2E fixtures for invitation redemption; the defining live behavior of both rulesets; command race/idempotency; TTL; restart loss; SSE recovery; responsive use; and accessibility.
6. Run all root commands from a clean local state, verify the selected exact dependency pins, inspect generated artifacts, and publish the local evidence alongside troubleshooting outcomes.

## Operational boundaries

Local production-style verification proves contract compatibility and browser behaviour; it does not prove external hosting, TLS termination, CDN caching, CORS deployment policy, replicas, durability, or production observability. A backend restart intentionally invalidates active in-memory games, and the UI and tests must describe that limitation plainly.
