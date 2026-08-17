#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI not found. Install Docker and try again." >&2
  exit 1
fi

image_tag="battleship:0.0.1-SNAPSHOT"

if ! docker image inspect "$image_tag" >/dev/null 2>&1; then
  echo "$image_tag was not found. Run ./scripts/docker-build.sh first." >&2
  exit 1
fi

docker run --rm --publish 8080:8080 "$image_tag"
