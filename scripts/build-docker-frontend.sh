#!/usr/bin/env bash
# Builds the frontend image (nginx: static UI + /api proxy), tagged battleship-frontend:local.
# Everything is compiled inside Docker; nothing is pushed. See the Dockerfile for what the image contains.
#
# Run it:   docker run --rm -p 8080:8080 -e BATTLESHIP_BACKEND_UPSTREAM=http://host.docker.internal:8081 \
#             battleship-frontend:local
# (BATTLESHIP_BACKEND_UPSTREAM is the private http://host:port of a running backend; put HTTPS in front for real use.)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
docker build --target frontend --tag battleship-frontend:local .
