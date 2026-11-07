## Purpose

Defines the GitHub Actions pipeline extension (owner decision 2026-11-07): a build-only branch path, a pull-request gate, and a stable semantic-version tag release of the Render image to GHCR, hardened and documented (R70-R78, S11). Workflows live in `.github/workflows/`, call the repository's own scripts and `Dockerfile`, and add no script. The default branch is `master`; the `rewrite_prod_ready` and `feature/**` pull-request targets are temporary until the rewrite merges. Deploying to Render stays manual.

## ADDED Requirements

### Requirement: Branch pushes build only (R70)

`[user]` A push to any branch except `master` SHALL run a build only: the backend JAR and the frontend `dist/`, with no tests and no gate. Tag pushes SHALL NOT trigger it.

#### Scenario: Feature branch push

- **WHEN** a branch other than `master` is pushed
- **THEN** the backend package and frontend build run without tests

#### Scenario: Tag push

- **WHEN** a tag is pushed
- **THEN** the branch build does not run

### Requirement: Pull requests run the full gate (R71)

`[user]` A pull request targeting `master`, `rewrite_prod_ready` or `feature/**` SHALL run the build and then the full gate `./scripts/local-build.sh`. On failure the Playwright report, test results and backend test reports SHALL be kept as downloadable artifacts. Fork pull requests SHALL get a read-only token and no secrets.

#### Scenario: Failing gate

- **WHEN** the gate fails
- **THEN** the Playwright report, test results and backend test reports are downloadable artifacts

### Requirement: Nothing runs on master pushes (R72)

`[user]` A push to `master`, including the merge of `rewrite_prod_ready`, SHALL trigger nothing.

#### Scenario: Merge to master

- **WHEN** `rewrite_prod_ready` is merged to `master`
- **THEN** no workflow starts

### Requirement: Stable tag releases the Render image (R73)

`[user]` Pushing a tag `vX.Y.Z` (strict stable semantic version, no suffix) SHALL build the Render image (`render` Dockerfile target, `linux/amd64`), run `scripts/build-docker-render.sh --smoke`, push it to GHCR with semantic-version tags, and create a GitHub Release. Deploying to Render SHALL stay manual.

#### Scenario: Stable tag

- **WHEN** `v1.2.3` is pushed
- **THEN** the image is smoke-checked, pushed to GHCR with semver tags, and a release with the digest is created

#### Scenario: Re-run

- **WHEN** the same tag is re-run
- **THEN** no duplicate release is made and a changed digest is reported

#### Scenario: Pre-release tag

- **WHEN** a tag with a suffix such as `-rc` is pushed
- **THEN** the release workflow does not run

#### Scenario: Release contents

- **WHEN** the release is created
- **THEN** it carries generated notes, the image reference, its immutable digest and the Render instructions (the exact public HTTPS `BATTLESHIP_PUBLICBASEURL`, one instance, link to `docs/render-deployment.md`)

### Requirement: Least-privilege permissions (R74)

`[agent]` Every job SHALL declare its own least-privilege `permissions` (default none; `contents: write` and `packages: write` only in the release job), and only `GITHUB_TOKEN` SHALL be used with no other secret.

#### Scenario: Missing permissions block

- **WHEN** a job has no `permissions` block
- **THEN** the lint check fails

### Requirement: Pinned actions and no persisted credentials (R75)

`[agent]` Third-party actions SHALL be pinned by full commit SHA with a version comment, and checkouts SHALL NOT persist credentials.

#### Scenario: Unpinned action

- **WHEN** an action is referenced by a mutable tag
- **THEN** the lint check fails

### Requirement: Release only from master ancestry (R76)

`[agent]` Before building or pushing anything the release job SHALL prove the tagged commit is an ancestor of `origin/master`, and SHALL fail with a message naming the tag and commit otherwise.

#### Scenario: Tag on a feature commit

- **WHEN** the tagged commit is not on `master`
- **THEN** the job fails naming the tag and commit and pushes nothing

### Requirement: Timeouts and concurrency (R77)

`[agent]` Every job SHALL have a timeout, and runs SHALL be grouped by ref or tag so a superseded branch or pull-request run is cancelled and a release run is never cancelled mid-push.

#### Scenario: Superseded run

- **WHEN** a newer push supersedes a branch run
- **THEN** the older run is cancelled while a release run is not

### Requirement: CI guide (R78)

`[agent]` `docs/ci.md` SHALL describe the three paths (branch build, PR gate, tag release), the release procedure, the required repository settings (workflow permissions, GHCR package visibility, branch protection with the gate as required check) and using the GHCR image in Render, and SHALL link to `docs/render-deployment.md` and `docs/configuration.md` instead of repeating them.

#### Scenario: Release procedure

- **WHEN** an owner reads the guide
- **THEN** the release steps and repository settings are described

### Requirement: Workflow lint and owner trigger verification (S11)

Workflow files SHALL pass `actionlint` and `zizmor` clean; a deliberately unpinned action, a missing `permissions` block or a wrong tag pattern SHALL make that check fail. Trigger behaviour on GitHub itself (PR, branch push, test tag) SHALL be verified by the owner after the first run (T036) and reported as such, never as local evidence.

#### Scenario: Wrong tag pattern

- **WHEN** a workflow uses a wrong tag pattern
- **THEN** the lint check fails
