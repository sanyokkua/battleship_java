# Local integration launch specification input

## Purpose

Provide one root-owned local developer and verification experience for the separately built anonymous Battleship backend, static frontend, and published contract. It proves that the products integrate through their contract rather than through shared source, preserves invitation privacy, and makes restart and expiry limits explicit.

The scope is local operation only. It excludes CI, deployment, hosting, a database, user accounts, replicas, and multi-repository orchestration.

## User-visible scope

From the repository root, a contributor can install supported dependencies, generate and check the contract, run the backend and frontend together in development, create production-style separate artifacts, run a single verification command, and use concise troubleshooting documentation. The frontend dev server proxies API and SSE traffic to the backend. The production-style local path serves the static frontend separately with validated runtime API configuration.

The local harness demonstrates two isolated browser identities, both server-advertised rulesets, private invitation redemption, expiry, restart loss, idempotent commands, command races, realtime recovery, responsive behaviour, and accessibility. It does not simulate a production host or promise persistence across process restart.

## Named EARS rules

- **Root installation:** When a contributor runs the documented root install command with the selected Java 25 and Node 24.19.0 runtimes, the repository shall install the exact `contracts/` and `frontend/` lockfiles and validate the backend Maven Wrapper without requiring a global Maven or frontend package manager.
- **Contract drift:** When contract source, generated clients, schemas, or fixtures disagree, the root verification command shall fail with an actionable drift message before browser tests run.
- **Development proxy:** When the frontend development command runs with a healthy local backend, browser requests for API and SSE resources shall travel through the Vite proxy and preserve credentialed same-site semantics required by the contract.
- **Production-style local run:** When the contributor runs the production-style local command, the static build and backend shall be distinct artifacts, the frontend shall validate its runtime API configuration before use, and the browser shall not rely on a development proxy.
- **Matching public base:** When local configuration is checked, the frontend origin plus public application path shall equal the backend's public frontend base URL, including any subpath; the frontend and API origins shall use the same hostname so the host-only readable CSRF cookie works, and credentialed CORS shall name exactly the frontend origin.
- **Cross-port CSRF:** When production-style local mode uses separate frontend and API ports on `localhost`, metadata shall set the readable host-only CSRF cookie, the gateway shall echo it on an unsafe credentialed request, and the backend shall accept the exact Origin. A different-hostname configuration shall fail before startup.
- **Health and readiness:** When a local process starts, it shall expose documented health/readiness evidence before dependent checks begin; startup failure shall identify the component, bound address or port, and safe next diagnostic command.
- **Safe logs:** When local commands emit logs, they shall include component, safe correlation identifier, lifecycle outcome, and redacted error category, and shall never print cookies, invitation fragments, credentials, full secret-bearing URLs, player-safe snapshots, or raw command bodies.
- **Restart and expiry:** When the backend restarts or a game expires, the integration harness shall prove that browsers receive the documented unavailable/restart-lost outcome and cannot continue commands from cached state.
- **Verification:** When `scripts/verify.sh` runs, it shall execute contract lint and generation-drift checks, backend tests, frontend static checks and build, and focused isolated-browser end-to-end checks. A skipped or capability-blocked stage shall make the final result unverified rather than green.
- **Coverage selection:** Where a risk applies to both rulesets, browser contexts, viewport modes, and access methods, the suite shall use a named representative matrix that covers each risk without a Cartesian-product explosion.

## Representative acceptance scenarios

| Scenario | Expected outcome | Proving test |
|---|---|---|
| Fresh root setup | Commands install, generate, start, and report healthy components | Clean-worktree local smoke script |
| Generated contract drift | Manual generated-file change or contract mismatch fails before integration | Root drift-check fixture |
| Development and production-style traffic | Dev proxy and built static client both reach validated API/SSE endpoints | Browser smoke in each local mode |
| Host-only CSRF across local ports | Metadata cookie is readable, unsafe request carries the matching header, and a different hostname is rejected | Production-style browser request/header check plus configuration negative test |
| Two guests redeem one invitation | Exactly one isolated browser context claims the guest seat | Focused two-context E2E |
| Replayed or racing command | One command result becomes authoritative and both clients converge by game version | Two-context command-race E2E |
| Distinct gameplay rules | Sea Battle retains the turn after a hit and distinguishes fired water from a sink's revealed moat; Classic changes turn after a hit | One focused live two-context sequence per ruleset |
| TTL/restart | Backend clock tests prove exact boundaries; a short local profile proves normal browser expiry, and a real process restart proves restart loss | Backend lifecycle tests plus expiry/restart E2E with no test-only endpoint |
| Stream recovery | Background/stale/disconnected player reconnects and receives ordered current state | SSE recovery E2E |
| Inclusive responsive journey | A representative desktop, narrow portrait, and short landscape journey remains accessible | Playwright viewport plus accessibility checks |

## Non-goals

- CI providers, release pipelines, cloud hosting, DNS, TLS termination, deployment manifests, or production monitoring.
- A database, accounts, persistent sessions, horizontal scaling, multi-replica consistency, or cross-repository checkout orchestration.
- Full cross-product browser/device testing, pixel-perfect visual snapshots, load testing, or emulation of every unavailable browser feature.
- Offline commands, command queueing, or a claim that the static shell can play a game without a backend.

## Success boundary

The integration feature is successful when a clean local contributor can run the root commands, distinguish development from production-style operation, and obtain trustworthy evidence for the critical private two-player journeys without secrets appearing in logs or artifacts.
