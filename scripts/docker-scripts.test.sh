#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd -- "$script_dir/.." && pwd -P)"
test_root="$(mktemp -d)"
log_file="$test_root/docker.log"
trap 'rm -rf "$test_root"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local expected="$1"
  local actual_file="$2"
  grep -F -- "$expected" "$actual_file" >/dev/null || fail "expected '$expected' in $actual_file"
}

docker() {
  printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"

  if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
    [[ "${FAKE_DOCKER_IMAGE_EXISTS:-1}" == "1" ]]
  fi
}

export -f docker
export FAKE_DOCKER_LOG="$log_file"

missing_docker_error="$test_root/missing-docker.err"
if env -i PATH="/usr/bin:/bin" /bin/bash "$script_dir/docker-build.sh" 2>"$missing_docker_error"; then
  fail "docker-build.sh should fail when Docker is unavailable"
fi
assert_contains "Docker CLI not found" "$missing_docker_error"

: > "$log_file"
(cd "$test_root" && /bin/bash "$script_dir/docker-build.sh")
assert_contains "build --tag battleship:0.0.1-SNAPSHOT $repo_root" "$log_file"

missing_image_error="$test_root/missing-image.err"
export FAKE_DOCKER_IMAGE_EXISTS=0
if /bin/bash "$script_dir/docker-run.sh" 2>"$missing_image_error"; then
  fail "docker-run.sh should fail when the local image is missing"
fi
assert_contains "battleship:0.0.1-SNAPSHOT was not found" "$missing_image_error"

: > "$log_file"
export FAKE_DOCKER_IMAGE_EXISTS=1
/bin/bash "$script_dir/docker-run.sh"
assert_contains "image inspect battleship:0.0.1-SNAPSHOT" "$log_file"
assert_contains "run --rm --publish 8080:8080 battleship:0.0.1-SNAPSHOT" "$log_file"

echo "Docker script tests passed."
