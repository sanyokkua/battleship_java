#!/usr/bin/env bash
# Builds the Render image: frontend + backend in ONE linux/amd64 image that serves plain HTTP on $PORT behind
# Render's HTTPS edge. Nothing is pushed; publishing to Docker Hub and deploying are manual (docs/render-deployment.md).
#
# Usage:  ./scripts/build-docker-render.sh [--smoke] [image:tag]        (default tag: battleship-render:local)
#   --smoke   after building, start the image locally and check what can be checked without Render: it refuses a bad
#             PORT / BATTLESHIP_PUBLICBASEURL, runs as a non-root user, answers health on a non-default port, falls
#             back to the SPA shell for deep links, sends the security headers and
#             revalidates the shell. It does NOT prove public HTTPS,
#             cookies, gameplay or Render's proxy. Needs curl and a free port 18081.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

smoke=0
if [[ ${1:-} == --smoke ]]; then smoke=1; shift; fi
image=${1:-battleship-render:local}
(( $# <= 1 )) || { echo "Usage: $0 [--smoke] [image:tag]" >&2; exit 2; }

docker buildx build --platform linux/amd64 --target render --tag "$image" --load .
[[ $(docker image inspect --format '{{.Architecture}}' "$image") == amd64 ]] || { echo "$image is not linux/amd64" >&2; exit 1; }
echo "Built $image (linux/amd64)."
(( smoke )) || exit 0

# --- smoke checks ------------------------------------------------------------------------------------------------
port=18081
origin=https://example.test
name="battleship-render-smoke-$$"
trap 'docker rm --force "$name" >/dev/null 2>&1 || true' EXIT
fail() { echo "SMOKE FAILED: $1" >&2; exit 1; }

# Settings errors must stop the container immediately, before anything listens.
out=$(docker run --rm -e PORT=8080 -e BATTLESHIP_PUBLICBASEURL=$origin "$image" 2>&1) && fail 'PORT=8080 was accepted'
[[ $out == *'must not be 8080'* ]] || fail "PORT=8080 was not reported clearly: $out"
out=$(docker run --rm -e BATTLESHIP_PUBLICBASEURL=http://example.test "$image" 2>&1) && fail 'an http public URL was accepted'
[[ $out == *'BATTLESHIP_PUBLICBASEURL must be'* ]] || fail "bad public URL was not reported clearly: $out"

docker run --detach --name "$name" -p "127.0.0.1:$port:$port" -e PORT=$port -e BATTLESHIP_PUBLICBASEURL=$origin \
    "$image" >/dev/null
for _ in $(seq 120); do
    curl -fsS --max-time 2 "http://127.0.0.1:$port/api/v1/health" 2>/dev/null | grep -q '"ready":true' && break
    [[ $(docker inspect -f '{{.State.Running}}' "$name") == true ]] || { docker logs "$name" >&2; fail 'container exited'; }
    sleep 1
done
curl -fsS --max-time 2 "http://127.0.0.1:$port/api/v1/health" | grep -q '"ready":true' || fail 'health never became ready'

[[ $(docker exec "$name" id -u) != 0 ]] || fail 'the container runs as root'
curl -fsS "http://127.0.0.1:$port/smoke/deep/link" | grep -Fq '<div id="root"></div>' || fail 'no SPA fallback'
headers=$(curl -fsSI "http://127.0.0.1:$port/")
for header in content-security-policy referrer-policy x-content-type-options x-frame-options; do
    grep -qi "^$header:" <<<"$headers" || fail "missing $header"
done
for path in / /sw.js /manifest.webmanifest; do
    grep -qi '^cache-control: no-cache' < <(curl -fsSI "http://127.0.0.1:$port$path") || fail "$path is not sent with no-cache"
done
echo "SMOKE PASSED: $image"
