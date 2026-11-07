#!/bin/sh
# Entrypoint of the Render image: validates the settings, starts the backend (private, 127.0.0.1:8080), waits until
# it is ready, then starts nginx on $PORT. If either process dies, the container stops so Render restarts it.
set -eu

fail() { printf 'FAILED: %s\n' "$1" >&2; exit 1; }

printf '%s' "${BATTLESHIP_PUBLICBASEURL:-}" | grep -Eq '^https://[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]{1,5})?$' ||
    fail 'BATTLESHIP_PUBLICBASEURL must be the exact public https://host[:port] origin.'
printf '%s' "$PORT" | grep -Eq '^[0-9]{1,5}$' && [ "$PORT" -ge 1 ] && [ "$PORT" -le 65535 ] && [ "$PORT" -ne 8080 ] ||
    fail 'PORT must be between 1 and 65535 and must not be 8080.'

stop() {
    trap - TERM INT
    kill -TERM "$backend" "$proxy" 2>/dev/null || true
    wait 2>/dev/null || true
}
trap 'stop; exit 0' TERM INT

# Spring reads SERVER_PORT / SERVER_ADDRESS; the backend must stay private behind nginx.
# nginx is the only caller and overwrites X-Forwarded-For, so the backend may key rate limits on it.
BATTLESHIP_TRUSTFORWARDEDFOR=true SERVER_PORT=8080 SERVER_ADDRESS=127.0.0.1 java -XX:MaxRAMPercentage=75 -jar /opt/battleship/app.jar &
backend=$!
proxy=$backend

attempts=0
until wget -q -O - http://127.0.0.1:8080/api/v1/health 2>/dev/null | grep -q '"ready":true'; do
    kill -0 "$backend" 2>/dev/null || fail 'backend exited before becoming ready.'
    attempts=$((attempts + 1))
    [ "$attempts" -lt 120 ] || { stop; fail 'backend did not become ready within 60 seconds.'; }
    sleep 0.5
done

# The nginx image's own entrypoint fills the config template from the environment, then runs nginx.
/docker-entrypoint.sh nginx -g 'daemon off;' &
proxy=$!
printf '[render] ready at %s (HTTP on port %s)\n' "$BATTLESHIP_PUBLICBASEURL" "$PORT"

status=0
wait -n "$backend" "$proxy" || status=$?
stop
fail "backend or nginx exited unexpectedly (status $status)."
