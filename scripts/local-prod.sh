#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
# E2E already builds these artifacts; do not replace files used by its running stacks.
if [ "${BATTLESHIP_E2E_USE_PREBUILT:-0}" != "1" ]; then
    "$root/backend/mvnw" -q -f "$root/backend/pom.xml" -DskipTests package
    npm --prefix "$root/frontend" run build
fi
exec node "$(dirname "$0")/stack.mjs" prod "$@"
