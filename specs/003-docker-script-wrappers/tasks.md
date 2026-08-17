---
description: "Dependency-ordered task ledger for Docker helper scripts"
---

# Tasks: Docker Helper Scripts

**Input**: Design documents from `specs/003-docker-script-wrappers/`.

**Prerequisites**: `spec.md` and `plan.md` define the approved direct-Docker
workflow. The existing `Dockerfile` and `docker-compose.yml` are authoritative
for image construction and Compose usage.

## Task Rules

- Each task has one task branch and one commit.
- Keep task checkboxes aligned with implementation and proving evidence.
- Preserve unrelated worktree changes.
- Do not modify `Dockerfile` or `docker-compose.yml`.

## Phase 1: Specification

- [x] T001 Create the approved design document and the feature `spec.md`,
  `plan.md`, and `tasks.md` artifacts. Prove with `git diff --check` and
  inspection of all four documents.

## Phase 2: Scripts

- [x] T002 Add test-first shell checks and implement executable
  `scripts/docker-build.sh` and `scripts/docker-run.sh`. Prove with the shell
  test harness, `bash -n`, and executable-bit checks.

## Phase 3: Documentation

- [x] T003 Update `README.md` and `docs/index.md` with individual and combined
  Docker helper commands. Prove with targeted text search and `git diff --check`.

## Phase 4: Verification and Closeout

- [x] T004 Build and inspect the image, run the container smoke test, run
  `scripts/verify.sh`, record results in `verification.md`, review the complete
  diff, and leave final integration to the user.
