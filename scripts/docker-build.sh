#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI not found. Install Docker and try again." >&2
  exit 1
fi

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
image_tag="battleship:0.0.1-SNAPSHOT"

cd "$repo_root"
docker build --tag "$image_tag" "$repo_root"
