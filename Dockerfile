# Battleship images. Everything is built inside Docker, so no local Java or Node is needed.
#
#   target    used by                          what it is
#   --------  -------------------------------  ----------------------------------------------------------
#   verify    scripts/local-build.sh --docker  the full gate (tests, lint, browser journeys); not shipped
#   frontend  scripts/build-docker-frontend.sh nginx: static UI + /api proxy to a backend you point it at
#   backend   scripts/build-docker-backend.sh  the API only, on a small custom Java runtime
#   render    scripts/build-docker-render.sh   frontend + backend in ONE image, HTTP on $PORT (Render)
#
# Base images are pinned by tag AND digest (the tag is for people, the digest is what is pulled). To upgrade, pull
# the new tag, copy its digest (docker buildx imagetools inspect <tag>) and rebuild.
#
# Build stages run on the machine's own CPU ($BUILDPLATFORM): a JAR and a folder of JS/CSS do not depend on the
# CPU type, so an amd64 image built on an Apple-silicon laptop does not need slow emulation for Maven or npm.

# -- node-base: the one place Node 24 is pinned; stage `dist` builds on it and stage `verify` copies it ---------
FROM --platform=$BUILDPLATFORM node:24-bookworm-slim@sha256:01d566e8d94d8c619591d78652babfccd3c6992a3dffa9ed5ab39e11cf6c70e9 AS node-base

# -- dist: the built web app (frontend/dist) ---------------------------------------------------------------------
# `npm run build` first generates the TypeScript API types from the contract, so the contract is copied too.
# Package files are copied before the sources so the (slow) `npm ci` layer is reused when only code changes.
FROM node-base AS dist
WORKDIR /work/frontend
COPY contracts/openapi.yaml /work/contracts/openapi.yaml
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# -- jar: the packaged backend (backend/app/target/battleship-app.jar) -------------------------------------------
# Tests are skipped here: they run in the verify stage / scripts/local-build.sh, not on every image build.
# The generated DTOs come from the contract, so it is copied too.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS jar
WORKDIR /work
COPY contracts/openapi.yaml contracts/openapi.yaml
COPY backend/ backend/
RUN cd backend && ./mvnw -q -DskipTests package

# -- jre: a Java runtime cut down to the modules this JAR needs ---------------------------------------------------
# A full JRE image is ~300 MB; the modules the application really uses are a fraction of that. jdeps lists them
# from the JAR and its libraries, jlink builds a runtime with only those (plus jdk.unsupported, which some libraries
# load reflectively and jdeps cannot see). Alpine/musl like the final images. Runs on the TARGET CPU because the
# runtime is native code.
FROM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS jre
COPY --from=jar /work/backend/app/target/battleship-app.jar /tmp/app.jar
RUN cd /tmp && jar xf app.jar BOOT-INF/lib \
 && modules=$(jdeps --ignore-missing-deps -q --recursive --multi-release 25 --print-module-deps \
      --class-path 'BOOT-INF/lib/*' app.jar) \
 && jlink --add-modules "$modules,jdk.unsupported" --strip-debug --no-man-pages --no-header-files \
      --compress=zip-9 --output /jre

# -- verify: the whole quality gate inside Docker (scripts/local-build.sh --docker) ------------------------------
# Java 25 comes from the base image, Node 24 is copied from the official Node image, and Chromium (with its system
# libraries) is installed for the Playwright journeys. The layer that installs Chromium depends only on the
# package files, so it is cached. The final RUN is the same script people run locally, so the two cannot drift.
# Nothing from this stage is shipped.
FROM eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 AS verify
COPY --from=node-base /usr/local/ /usr/local/
WORKDIR /repo
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc frontend/
RUN cd frontend && npm ci && npx playwright install --with-deps chromium
COPY . .
RUN ./scripts/local-build.sh

# -- frontend: static UI + /api proxy (nginx, runs as the non-root user "nginx") ----------------------------------
# The unprivileged nginx image listens on a high port and needs no root. At start-up it fills
# /etc/nginx/templates/*.template from the environment: PORT and BATTLESHIP_BACKEND_UPSTREAM (required, the private
# backend address such as http://backend:8080). Put HTTPS in front for any non-localhost use.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS frontend
ENV PORT=8080
COPY --from=dist /work/frontend/dist/ /usr/share/nginx/html/
COPY config/nginx-server.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/" || exit 1

# -- backend: the API on the jlink runtime, running as a non-root user -------------------------------------------
# Plain Alpine (no Java, no package manager use) plus the small runtime from stage `jre`. The shell form of the
# entrypoint refuses to start without BATTLESHIP_PUBLICBASEURL, because a wrong or missing value would make every
# state-changing request fail with 403. MaxRAMPercentage lets the JVM use most of the container's memory limit.
# Keep port 8080 private behind HTTPS/nginx.
FROM alpine:3.24@sha256:3f4d6b32e26703c6082839234f43769d5046b3ac7c910033911d1e71d8cd630a AS backend
RUN adduser -D -u 10001 battleship
COPY --from=jre /jre /opt/java
COPY --from=jar /work/backend/app/target/battleship-app.jar /opt/battleship/app.jar
USER battleship
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s CMD wget -q -O - "http://127.0.0.1:${SERVER_PORT}/api/v1/health" | grep -q '"ready":true' || exit 1
ENTRYPOINT ["/bin/sh", "-c", ": \"${BATTLESHIP_PUBLICBASEURL:?set BATTLESHIP_PUBLICBASEURL to the exact public UI origin}\"; exec /opt/java/bin/java -XX:MaxRAMPercentage=75 -jar /opt/battleship/app.jar"]

# -- render: frontend + backend in one image for Render ------------------------------------------------------------
# Based on the same unprivileged nginx image as `frontend` (so it is Alpine, non-root, no apt or curl), with the
# jlink runtime and the JAR added. config/render-entrypoint.sh runs the backend privately on 127.0.0.1:8080 and
# nginx on $PORT, and requires BATTLESHIP_PUBLICBASEURL (the exact public https origin). Render terminates HTTPS and
# forwards plain HTTP, so nginx-real-ip.conf takes the player's address from the edge's X-Forwarded-For.
# MaxRAMPercentage=75 (in the entrypoint) leaves room for nginx and the JVM's non-heap memory on small plans.
FROM frontend AS render
COPY --from=jre /jre /opt/java
COPY --from=jar /work/backend/app/target/battleship-app.jar /opt/battleship/app.jar
COPY config/nginx-real-ip.conf /etc/nginx/conf.d/00-real-ip.conf
COPY --chmod=755 config/render-entrypoint.sh /opt/battleship/entrypoint.sh
ENV PATH=/opt/java/bin:$PATH \
    PORT=10000 \
    BATTLESHIP_BACKEND_UPSTREAM=http://127.0.0.1:8080
EXPOSE 10000
HEALTHCHECK --interval=10s --timeout=3s --start-period=75s CMD wget -q -O - "http://127.0.0.1:${PORT}/api/v1/health" | grep -q '"ready":true' || exit 1
ENTRYPOINT ["/opt/battleship/entrypoint.sh"]
