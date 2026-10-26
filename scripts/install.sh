#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'FAILED: %s\n' "$1" >&2
    exit 1
}

run_step() {
    local label="$1"
    shift
    printf '== %s ==\n' "$label"
    "$@" || fail "$label"
}

check_java() {
    local output version major
    output=$(java -version 2>&1) || fail 'Java 25 runtime check'
    version=$(printf '%s\n' "$output" | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n 1)
    major=${version:-unknown}
    [[ "$major" == 25 ]] || { printf 'Expected Java major 25; found %s\n' "$major" >&2; fail 'Java 25 runtime check'; }
}

check_node() {
    local version major
    version=$(node -p 'process.versions.node') || fail 'Node 24 runtime check'
    major=${version%%.*}
    [[ "$major" == 24 ]] || { printf 'Expected Node major 24; found %s\n' "$major" >&2; fail 'Node 24 runtime check'; }
}

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

run_step 'Java 25 runtime check' check_java
run_step 'Node 24 runtime check' check_node
run_step 'Install contract dependencies' npm --prefix contracts ci
run_step 'Install frontend dependencies' npm --prefix frontend ci
run_step 'Install Playwright Chromium' npm --prefix frontend exec -- playwright install chromium
run_step 'Validate Maven wrapper and backend' ./backend/mvnw -q -f backend/pom.xml validate
printf 'Install complete.\n'
