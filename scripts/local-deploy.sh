#!/usr/bin/env bash
# Runs the packaged application on this machine so phones and other computers on your network can play.
# Builds the backend JAR and the web app first if they are missing (./scripts/local-build.sh also creates them).
#
# Usage:  ./scripts/local-deploy.sh [IPv4 | localhost]
#   (no argument)  uses this machine's LAN IPv4 address and serves HTTPS with a self-signed certificate
#   localhost      plain http://localhost only, reachable from this machine only, no certificate
#
# One origin, no CORS: `vite preview` serves the built files and proxies /api (including the live-update stream) to the
# backend, which listens on 127.0.0.1 only. The browser must use EXACTLY the URL printed below, because the backend
# only accepts state-changing requests whose Origin equals BATTLESHIP_PUBLICBASEURL. This script sets that variable
# from the URL for you; you never need to set it yourself (if you do, it must equal the printed URL).
#
# Why HTTPS on the LAN: the session cookie is Secure, which browsers only accept over HTTPS (or on localhost). The
# certificate is created in the git-ignored .local-tls/<IP>/ directory, valid for 30 days. Install and trust
# cert.pem on each device once (some phones also need a "full trust" switch); key.pem never leaves this machine.
# Stop with Ctrl-C. Games live in memory and are lost when the backend stops.
# Settings: BACKEND_PORT (8080), FRONTEND_PORT (4173), other BATTLESHIP_* variables (see config/local.env.example).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

fail() { echo "FAILED: $1" >&2; exit 1; }
host=${1:-}
[[ $# -le 1 ]] || fail "Usage: $0 [IPv4 | localhost]"
if [[ -z $host ]]; then
    host=$(ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || true)
    [[ -n $host ]] || fail 'Could not find the LAN address; pass it explicitly: ./scripts/local-deploy.sh 192.168.1.50'
fi
[[ $host == localhost || $host =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || fail 'The argument must be an IPv4 address or localhost.'

export BACKEND_PORT=${BACKEND_PORT:-8080}
export FRONTEND_PORT=${FRONTEND_PORT:-4173}
if [[ $host == localhost ]]; then
    url="http://localhost:$FRONTEND_PORT"
else
    url="https://$host:$FRONTEND_PORT"
    export FRONTEND_HOST=0.0.0.0
    tls_dir=".local-tls/$host"
    if ! openssl x509 -in "$tls_dir/cert.pem" -noout -checkend 0 >/dev/null 2>&1; then
        mkdir -p "$tls_dir"
        (umask 077 && openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj '/CN=Battleship local' \
            -addext "subjectAltName=IP:$host" -keyout "$tls_dir/key.pem" -out "$tls_dir/cert.pem" 2>/dev/null) ||
            fail 'OpenSSL could not create the certificate.'
    fi
    export FRONTEND_TLS_CERT="$PWD/$tls_dir/cert.pem" FRONTEND_TLS_KEY="$PWD/$tls_dir/key.pem"
fi
[[ -z ${BATTLESHIP_PUBLICBASEURL:-} || $BATTLESHIP_PUBLICBASEURL == "$url" ]] ||
    fail "BATTLESHIP_PUBLICBASEURL is $BATTLESHIP_PUBLICBASEURL but this run's URL is $url; unset it."
export BATTLESHIP_PUBLICBASEURL=$url

jar=backend/app/target/battleship-app.jar
[[ -f $jar ]] || ./backend/mvnw -q -f backend/pom.xml -DskipTests package
if [[ ! -f frontend/dist/index.html ]]; then
    [[ -d frontend/node_modules ]] || npm --prefix frontend ci
    npm --prefix frontend run build
fi

backend= frontend=
trap 'exit 130' INT TERM
trap '[[ -z $frontend ]] || kill "$frontend" 2>/dev/null; [[ -z $backend ]] || kill "$backend" 2>/dev/null; wait 2>/dev/null; true' EXIT
# The vite preview proxy overwrites X-Forwarded-For with the caller's address, so rate limits are per player.
BATTLESHIP_TRUSTFORWARDEDFOR=true SERVER_PORT=$BACKEND_PORT SERVER_ADDRESS=127.0.0.1 java -jar "$jar" &
backend=$!
ready=0
for _ in $(seq 120); do
    curl -fsS --max-time 1 "http://127.0.0.1:$BACKEND_PORT/api/v1/health" 2>/dev/null | grep -q '"ready":true' &&
        { ready=1; break; }
    kill -0 "$backend" 2>/dev/null || fail "The backend stopped; is port $BACKEND_PORT free?"
    sleep 0.5
done
(( ready )) || fail 'The backend did not become ready within 60 seconds.'  # the EXIT trap stops it
(cd frontend && exec node node_modules/vite/bin/vite.js preview) &
frontend=$!

echo
echo "Battleship is running. Open exactly:  $url"
if [[ $host != localhost ]]; then
    echo "First trust the certificate on each device:  $PWD/$tls_dir/cert.pem"
fi
echo 'Press Ctrl-C to stop.'
while kill -0 "$backend" 2>/dev/null && kill -0 "$frontend" 2>/dev/null; do sleep 1; done
fail 'The backend or the web server stopped.'
