# Verification: Docker Helper Scripts

**Feature branch:** `feature/docker-script-wrappers`

**Date:** 2026-08-17

## Acceptance Evidence

| Requirement | Proving command | Result |
|---|---|---|
| Build wrapper resolves the repository root and uses the fixed image tag | `bash scripts/docker-scripts.test.sh` | PASS |
| Run wrapper validates the image and uses the requested flags | `bash scripts/docker-scripts.test.sh` | PASS |
| Shell syntax is valid | `bash -n scripts/docker-build.sh scripts/docker-run.sh scripts/docker-scripts.test.sh` | PASS |
| Requested wrappers are executable | `stat -f '%Sp %N' scripts/docker-build.sh scripts/docker-run.sh` | PASS; both are `-rwxr-xr-x` |
| Local image is created | `./scripts/docker-build.sh` | PASS; tagged `battleship:0.0.1-SNAPSHOT` |
| Image metadata matches the runtime contract | `docker image inspect battleship:0.0.1-SNAPSHOT` | PASS; exposes `8080/tcp` and contains the expected healthcheck |
| Container serves the game endpoint | `./scripts/docker-run.sh` plus `curl -fsS http://127.0.0.1:8080/api/v2/game/editions` | PASS |
| Temporary container is removed on stop | Docker smoke harness using the exact container ID | PASS; `--rm` cleanup verified |
| Full repository gate | `scripts/verify.sh` | PASS; Maven 380 tests, frontend 511 tests, mock-browser 58 tests, live E2E 2 tests |

## Boundary Evidence

- `Dockerfile` unchanged.
- `docker-compose.yml` unchanged.
- `docs/openapi.json` unchanged after Maven verification.
- No backend, frontend, REST, SSE, or game behavior changes were made.
- The working tree was clean after verification before this evidence file was
  added.

## Environment Notes

The first Docker attempt correctly failed because the configured Colima
runtime was stopped and the sandbox could not access the Docker socket. Colima
was started with approval, and the unchanged build and smoke commands were
rerun with host access. The product checks passed after that capability setup.

The gate emitted existing-style warnings from Java/Mockito, npm audit output,
and six frontend Fast Refresh lint warnings, but reported zero lint errors and
exited successfully.
