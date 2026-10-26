#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
"$root/backend/mvnw" -q -f "$root/backend/pom.xml" -DskipTests package
npm --prefix "$root/frontend" run build
exec node "$(dirname "$0")/stack.mjs" prod "$@"
