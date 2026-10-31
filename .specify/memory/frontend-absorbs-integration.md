# Frontend integration memory

Feature `003-frontend` owns the root integration layer and `scripts/verify.sh`; there is no separate `004-integration` feature. The root gate checks pinned runtime/install requirements, the API contract, backend, frontend, and real-backend browser journeys. A failed or timed-out stage fails the gate.

Local SonarQube scans are supplementary to that gate: Maven analyzes the backend reactor with aggregate JaCoCo coverage, and the CLI analyzes the frontend with Vitest LCOV coverage and test-execution reports. Browser journeys and separately launched backend JVMs are not represented in those coverage reports. See the local operation guide for scope and commands.

See [`docs/local-operation.md`](../../docs/local-operation.md) for local run commands, ports, configuration, lifecycle limits, and troubleshooting.
