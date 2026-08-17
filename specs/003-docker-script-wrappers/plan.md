# Implementation Plan: Docker Helper Scripts

**Branch**: `feature/docker-script-wrappers` | **Date**: 2026-08-17 | **Spec**: [spec.md](./spec.md)

**Goal:** Add two executable Docker CLI wrappers that build and run the
existing Battleship image with no application behavior changes.

**Architecture:** Keep the existing multi-stage `Dockerfile` as the only build
definition. The build wrapper resolves the repository root and creates the
fixed local image tag; the run wrapper validates that tag, publishes port 8080,
and runs in the foreground with automatic cleanup.

**Tech Stack:** Bash, Docker CLI, existing Maven/Node multi-stage Dockerfile.

## Global Constraints

- Do not modify `Dockerfile` or `docker-compose.yml`.
- Use image tag `battleship:0.0.1-SNAPSHOT` and port mapping `8080:8080`.
- Resolve the build context from the script location, not the caller's cwd.
- Preserve unrelated worktree changes and do not commit directly to `master`.
- Use one task branch and one commit per planned task.

## Task Order

1. Add and approve the design/specification artifacts.
2. Add test-first shell behavior checks and implement both scripts.
3. Update user-facing build/run documentation.
4. Run syntax, Docker smoke, repository-gate, diff, and closeout checks.

## Interfaces

```text
scripts/docker-build.sh  # no arguments; creates battleship:0.0.1-SNAPSHOT
scripts/docker-run.sh    # no arguments; requires image and serves localhost:8080
```

## Test Plan

- Use a shell test harness with an exported Docker CLI stub to verify command
  selection, repository-root context, local-image validation, and cleanup
  flags without requiring a full image build for every assertion.
- Run the real Docker build and image inspection once.
- Run the real container through `docker-run.sh`, poll
  `/api/v2/game/editions`, stop it, and verify the `--rm` cleanup behavior.
- Run `scripts/verify.sh` and report its exact exit result.
