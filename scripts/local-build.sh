#!/usr/bin/env bash
# The product gate: installs the exact pinned dependencies, builds everything and runs every test and quality check.
# A stage that fails or runs longer than the timeout fails the whole run (non-zero exit); nothing is skipped.
#
# Usage:  ./scripts/local-build.sh             on this machine (needs Java 25, Node 24, and room for Chromium)
#         ./scripts/local-build.sh --docker    the same script inside a Docker build, no local Java/Node needed
#
# Stages: runtimes -> install -> API contract -> backend (tests + format) -> frontend (format, lint, types, unit
# tests, build) -> browser journeys against the packaged backend JAR.
# Results: backend/app/target/battleship-app.jar and frontend/dist/ (used by scripts/local-deploy.sh).
# LOCAL_BUILD_STAGE_TIMEOUT (seconds, default 1800) bounds each stage.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ${1:-} == --docker ]]; then
    exec docker build --target verify --tag battleship-verify:local .
fi
[[ $# -eq 0 ]] || { echo "Usage: $0 [--docker]" >&2; exit 2; }

timeout=${LOCAL_BUILD_STAGE_TIMEOUT:-1800}
[[ $timeout =~ ^[1-9][0-9]*$ ]] || { echo 'FAILED: LOCAL_BUILD_STAGE_TIMEOUT must be a positive number of seconds.' >&2; exit 2; }
timed_out=$(mktemp)
trap 'rm -f "$timed_out"' EXIT

# Runs one stage in its own process group so a timeout can stop the whole tree (Maven, Node, browsers).
stage() {
    local name=$1 status=0
    shift
    printf '\n== %s ==\n' "$name"
    set -m
    "$@" &
    local pid=$!
    ( sleep "$timeout"; echo yes >"$timed_out"; kill -TERM -- "-$pid"; sleep 5; kill -KILL -- "-$pid" ) 2>/dev/null &
    local watchdog=$!
    wait "$pid" || status=$?
    kill -- "-$watchdog" 2>/dev/null || true
    { wait "$watchdog"; } 2>/dev/null || true
    set +m
    if [[ -s $timed_out ]]; then echo "FAILED: $name timed out after ${timeout}s" >&2; exit 1; fi
    if (( status != 0 )); then echo "FAILED: $name (exit code $status)" >&2; exit 1; fi
}

check_runtimes() {
    java -version 2>&1 | grep -Eq 'version "25[."]' || { echo 'Java 25 is required.' >&2; return 1; }
    [[ $(node -p 'process.versions.node.split(".")[0]') == 24 ]] || { echo 'Node 24 is required.' >&2; return 1; }
}
install_dependencies() {
    npm --prefix contracts ci
    npm --prefix frontend ci
    npm --prefix frontend exec -- playwright install chromium
}
check_backend() { (cd backend && ./mvnw -q verify && ./mvnw spotless:check); }

stage 'Java 25 and Node 24' check_runtimes
stage 'Install exact dependencies' install_dependencies
stage 'API contract' npm --prefix contracts run check
stage 'Backend: tests and format' check_backend
stage 'Frontend: format, lint, types, unit tests, build' npm --prefix frontend run check
stage 'Browser journeys against the packaged backend' npm --prefix frontend run e2e

printf '\nLocal build passed: all stages completed.\n'
