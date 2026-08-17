# Feature Specification: Docker Helper Scripts

**Feature Branch**: `feature/docker-script-wrappers`

**Created**: 2026-08-17

**Status**: Implemented

**Input**: User request for reusable scripts to build and run the Battleship
Docker image.

**Spec Kit Artifacts**: `specs/003-docker-script-wrappers/`

## Scope and Context

The repository already contains a multi-stage `Dockerfile` that builds the
frontend and backend into a runnable image, plus a Compose configuration using
image `battleship:0.0.1-SNAPSHOT` and port 8080. Users currently need to type
the Maven build and Spring Boot run commands manually.

## In Scope

- A build script at `scripts/docker-build.sh`.
- A run script at `scripts/docker-run.sh`.
- Direct Docker CLI usage with the fixed local image tag
  `battleship:0.0.1-SNAPSHOT`.
- Documentation of individual and combined script commands.
- Clear missing-Docker and missing-image failures.
- Shell, image, container startup, HTTP smoke, and repository-gate evidence.

## Out of Scope

- Changes to `Dockerfile` or `docker-compose.yml`.
- Docker Compose orchestration changes.
- Changes to application code, REST/SSE contracts, OpenAPI, or runtime game
  behavior.
- Background container management, volumes, databases, or deployment setup.

## Functional Requirements

### FR-001 Build the local image

`scripts/docker-build.sh` MUST work when invoked from any current directory,
MUST fail clearly when Docker is unavailable, and MUST invoke the existing
Dockerfile with the repository root as build context and tag the result as
`battleship:0.0.1-SNAPSHOT`.

### FR-002 Run the local image

`scripts/docker-run.sh` MUST fail clearly when the required local image does
not exist, then MUST run that image in the foreground with host port 8080
published to container port 8080.

### FR-003 Clean up stopped containers

The run script MUST pass `--rm` so stopping the foreground process removes the
temporary container.

### FR-004 Document the workflow

`README.md` and `docs/index.md` MUST document these commands:

```shell
./scripts/docker-build.sh
./scripts/docker-run.sh
```

They MUST also document the combined command:

```shell
./scripts/docker-build.sh && ./scripts/docker-run.sh
```

### FR-005 Preserve application behavior

The implementation MUST leave `Dockerfile`, `docker-compose.yml`, application
source, API contracts, and unrelated worktree changes untouched.

## Acceptance Scenarios

1. Given Docker is installed, when a user runs `./scripts/docker-build.sh`
   from the repository root, then a local image named
   `battleship:0.0.1-SNAPSHOT` is created.
2. Given the build script has created the image, when a user runs
   `./scripts/docker-run.sh`, then the game is reachable at
   `http://localhost:8080` and the script remains attached to container logs.
3. Given the run process is stopped with Ctrl-C, then its temporary container
   is removed automatically.
4. Given the local image is missing, when a user runs `./scripts/docker-run.sh`,
   then the script exits non-zero with an instruction to run the build script.
5. Given the scripts are invoked from outside the repository, then the build
   script still uses the repository root as Docker build context.

## Verification Requirements

- `bash -n scripts/docker-build.sh scripts/docker-run.sh` passes.
- Both requested scripts have executable permissions.
- Docker image inspection confirms the fixed tag.
- `GET /api/v2/game/editions` succeeds against the container.
- The repository verification result and any environment limitation are
  recorded in the feature verification evidence.
