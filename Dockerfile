# syntax intentionally omitted: no BuildKit-only features are used anywhere in this
# file (no heredocs, no RUN --mount) so it builds identically under `docker build`
# and `podman build`.
#
# Base image tags verified live against Docker Hub on 2026-07-13:
#   - maven:3.9.16-eclipse-temurin-25  (full Maven 3.9.16 + Eclipse Temurin 25 JDK, Ubuntu Noble base)
#   - eclipse-temurin:25-jre           (Eclipse Temurin 25 JRE, floating major-version tag —
#                                        acceptable per project decision: a real version tag,
#                                        not `latest`; re-verify before pinning further, e.g. to a
#                                        Noble/Alpine variant, if stricter reproducibility is needed)

# ---- Build stage --------------------------------------------------------
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app

# Copy only the POM first so the dependency cache layer survives source-only changes.
COPY pom.xml ./
# Best-effort: this cannot fully resolve without the frontend-maven-plugin's own
# Node/npm artifacts and doesn't know about them yet, but it still warms the local
# Maven repo for all Java deps declared in the POM, which is the expensive part to
# re-download on every build.
RUN mvn -B dependency:go-offline || true

COPY src ./src
COPY frontend ./frontend

# Installs Node, runs `npm ci`, builds the frontend (vite), runs backend tests,
# and packages the fat jar — all self-sufficient now that pom.xml has the npm-ci
# execution (see Task 1 fix), no manual `npm install` step required.
RUN mvn -B clean package

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# Temurin JRE images ship no HTTP client at all; curl is installed solely so
# HEALTHCHECK below can probe the app's REST endpoint from inside the container.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Explicit numeric UID/GID so the image doesn't depend on a name existing in
# /etc/passwd of whatever base is used, and runs as non-root. 10001 is used
# (not 1000) because eclipse-temurin's Ubuntu Noble base already ships a
# built-in "ubuntu" user/group at 1000.
RUN groupadd --gid 10001 battleship \
    && useradd --uid 10001 --gid battleship --no-create-home --shell /usr/sbin/nologin battleship

WORKDIR /app
# Wildcard avoids hardcoding the jar's version twice (pom.xml already has it).
COPY --from=build --chown=battleship:battleship /app/target/battleship-*.jar /app/app.jar

USER battleship:battleship

EXPOSE 8080

# This container runs exactly one JVM, so it's safe to raise the default 25%
# MaxRAMPercentage cap — UseContainerSupport (container memory awareness) has
# been the JVM default since JDK 10, no extra flag needed for that part.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:8080/api/v2/game/editions || exit 1

# Exec form keeps the java process as PID 1 so it receives SIGTERM directly from
# `docker stop` / `podman stop` instead of it being swallowed by a shell.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
