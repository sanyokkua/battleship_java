#!/usr/bin/env bash
# Builds the backend image (the API only, on a small Java runtime), tagged battleship-backend:local.
# Everything is compiled inside Docker; nothing is pushed. See the Dockerfile for what the image contains.
#
# Run it:   docker run --rm -p 8081:8080 -e BATTLESHIP_PUBLICBASEURL=https://your.host battleship-backend:local
# (BATTLESHIP_PUBLICBASEURL must be the exact origin the browser uses; other BATTLESHIP_* settings keep their
# defaults, see config/local.env.example. Keep this port private behind the frontend image or another HTTPS proxy.)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
docker build --target backend --tag battleship-backend:local .
