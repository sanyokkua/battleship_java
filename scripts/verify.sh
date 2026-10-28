#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

stage_timeout=${VERIFY_STAGE_TIMEOUT:-1800}
fail_stage=${VERIFY_FAIL_STAGE:-}

if [[ ! "$stage_timeout" =~ ^[1-9][0-9]*$ ]]; then
    printf 'FAILED: VERIFY_STAGE_TIMEOUT must be a positive whole number of seconds.\n' >&2
    exit 2
fi
if [[ -n "$fail_stage" && ! "$fail_stage" =~ ^[1-4]$ ]]; then
    printf 'FAILED: VERIFY_FAIL_STAGE must be 1, 2, 3, or 4.\n' >&2
    exit 2
fi

fail() {
    printf 'FAILED: %s\n' "$1" >&2
    exit 1
}

run_stage() {
    local number="$1"
    local name="$2"
    shift 2
    local status=0

    printf '\n[%s/5] %s\n' "$number" "$name"
    if node scripts/verify-timeout.mjs "$stage_timeout" bash -euo pipefail -c '
        stage_number=$1
        shift
        "$@"
        if [[ "${VERIFY_FAIL_STAGE:-}" == "$stage_number" ]]; then
            printf "Injected failure after stage %s completed its command.\n" "$stage_number" >&2
            exit 97
        fi
    ' verify-stage "$number" "$@"; then
        printf 'Passed: %s\n' "$name"
    else
        status=$?
        if ((status == 124)); then
            fail "stage $number ($name) timed out after ${stage_timeout}s"
        fi
        fail "stage $number ($name) failed with exit code $status"
    fi
}

run_stage 0 'Install and validate pinned runtimes' ./scripts/install.sh
run_stage 1 'Validate the API contract' npm --prefix contracts run check
run_stage 2 'Verify and format-check the backend' bash -euo pipefail -c 'cd backend && ./mvnw -q verify && ./mvnw spotless:check'
run_stage 3 'Check and build the frontend' npm --prefix frontend run check
run_stage 4 'Run browser journeys against the packaged backend' npm --prefix frontend run e2e

printf '\nVerification passed: all five stages completed.\n'
