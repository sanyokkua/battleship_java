## 1. Coverage and Sonar gate

- [ ] 1.1 Prove frontend coverage and refreshed Sonar gates with real backend journeys. Done when a named gameplay line appears covered in generated LCOV and the refreshed Sonar report, a real-backend journey still passes, both final gates and issue lists are recorded, and no gate/exclusion/test rule was relaxed. Verify: `cd frontend && npm run test:coverage && npm run e2e`, then the scan/export commands in `docs/local-operation.md` and `./scripts/local-build.sh`. Mutation: disable browser coverage collection for one named gameplay journey; the line proof must fail without a falsely green import.

## 2. GitHub trigger verification (owner)

- [ ] 2.1 Owner verifies triggers on GitHub: push a branch (build only); open a pull request (gate, artifacts on a forced failure); merge (nothing runs on `master`); push a test tag after approval (GHCR image, release body, re-run safe); set Actions permissions, GHCR visibility and branch protection from `docs/ci.md`. The owner records the run links.
