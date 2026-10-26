#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
jar="$root/backend/app/target/battleship-app-1.0.0-SNAPSHOT.jar"
if [ ! -f "$jar" ]; then
    "$root/backend/mvnw" -q -DskipTests package
fi
exec node "$(dirname "$0")/stack.mjs" dev "$@"
