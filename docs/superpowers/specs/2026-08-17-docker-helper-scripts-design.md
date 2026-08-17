# Docker Helper Scripts Design

**Date:** 2026-08-17

**Status:** Approved

## Goal

Provide two reusable, repository-local commands for building the existing
container image and running the Battleship game from that image.

## Design

Use the existing multi-stage `Dockerfile` as the sole application build
definition. `scripts/docker-build.sh` resolves the repository root and runs
`docker build --tag battleship:0.0.1-SNAPSHOT <repo-root>`. This keeps the
frontend build, backend packaging, and runtime image assembly in one Docker
build without duplicating Maven commands on the host.

`scripts/docker-run.sh` requires the local image, maps host port 8080 to the
container's port 8080, attaches to the container logs, and uses `--rm` so
Ctrl-C stops and removes the temporary container. The scripts use Docker CLI
directly; Docker Compose remains an independent alternative.

## Boundaries

- The image tag is fixed at `battleship:0.0.1-SNAPSHOT` to match the existing
  Compose configuration.
- The existing `Dockerfile` and `docker-compose.yml` are unchanged.
- No backend, frontend, REST, SSE, or OpenAPI behavior changes.
- Documentation points users to `./scripts/docker-build.sh` and
  `./scripts/docker-run.sh`, including their combined form.

## Verification

Shell syntax and executable permissions are checked first. A Docker smoke test
builds and inspects the image, starts it through the run script, probes
`GET /api/v2/game/editions`, then stops the attached process and verifies the
temporary container is removed. The repository verification gate is also run
and any host-capability failure is reported separately from product evidence.
