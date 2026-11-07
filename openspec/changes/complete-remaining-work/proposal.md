## Why

Two tasks of the original frontend work are not done: proving frontend coverage and the refreshed Sonar gates with real backend journeys (previously T024), and the owner's check that the GitHub Actions triggers behave as specified (previously T036). They were carried over from the archived `frontend` change so no unfinished work is lost.

## What Changes

- Instrument the Playwright journeys against the packaged backend, merge their coverage with the Vitest LCOV and confirm imported source lines (e.g. a gameplay screen line) in Sonar. No quality gate, exclusion or test rule is relaxed. If mapping is infeasible within budget, the gate failure stays explicit.
- Owner verifies on GitHub: a branch push runs build only; a pull request runs the gate; merging runs nothing on `master`; a stable tag pushes the GHCR image and creates the release; Actions permissions, GHCR visibility and branch protection are set per `docs/ci.md`.

## Impact

- Capability: `local-build-gate` (coverage requirement R53/R63/R64, S9) and `ci-release` (R70-R73).
- Assurance level: Standard. Forecast: 2 tasks, no new dependencies unless coverage mapping needs one (exact version, counted against the file ceiling).
