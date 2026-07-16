#!/usr/bin/env bash
# detect-stack.sh — infer a repository's stack (language, build tool, frameworks, test tool)
# from its manifests. Read-only. Degrades gracefully when jq/grep features are unavailable.
#
# Usage: bash detect-stack.sh [path]   (defaults to current directory)
set -u

ROOT="${1:-.}"
[ -d "$ROOT" ] || { echo "detect-stack.sh: not a directory: $ROOT" >&2; exit 2; }
ROOT="$(cd "$ROOT" && pwd)"

echo "=================================================================="
echo "STACK DETECTION: $ROOT"
echo "=================================================================="

found=0
report() { found=1; echo "  $1"; }

# Helper: case-insensitive substring search in a file.
hasdep() { [ -f "$1" ] && grep -qiE "$2" "$1" 2>/dev/null; }

# ----- Node.js / TypeScript -----
if [ -f "$ROOT/package.json" ]; then
  echo "----- Node.js / JavaScript / TypeScript -----"
  report "manifest: package.json"
  [ -f "$ROOT/tsconfig.json" ] && report "language: TypeScript (tsconfig.json present)"
  [ -f "$ROOT/pnpm-lock.yaml" ] && report "package manager: pnpm"
  [ -f "$ROOT/yarn.lock" ] && report "package manager: yarn"
  [ -f "$ROOT/package-lock.json" ] && report "package manager: npm"
  grep -q '"workspaces"' "$ROOT/package.json" 2>/dev/null && report "layout: monorepo (workspaces)"
  hasdep "$ROOT/package.json" '"(react)"' && report "framework: React"
  hasdep "$ROOT/package.json" '"next"' && report "framework: Next.js"
  hasdep "$ROOT/package.json" '"express"' && report "framework: Express"
  hasdep "$ROOT/package.json" '"@nestjs/core"' && report "framework: NestJS"
  hasdep "$ROOT/package.json" '"(jest|vitest|mocha)"' && report "tests: $(grep -oiE 'jest|vitest|mocha' "$ROOT/package.json" | head -1)"
  # show key scripts
  echo "  scripts:"; grep -oE '"(build|test|start|dev|lint)"[[:space:]]*:[[:space:]]*"[^"]*"' "$ROOT/package.json" 2>/dev/null | sed 's/^/    /' || true
  echo
fi

# ----- Python -----
if [ -f "$ROOT/pyproject.toml" ] || [ -f "$ROOT/setup.py" ] || ls "$ROOT"/requirements*.txt >/dev/null 2>&1; then
  echo "----- Python -----"
  [ -f "$ROOT/pyproject.toml" ] && report "manifest: pyproject.toml"
  [ -f "$ROOT/setup.py" ] && report "manifest: setup.py"
  ls "$ROOT"/requirements*.txt >/dev/null 2>&1 && report "deps: requirements*.txt"
  for f in "$ROOT/pyproject.toml" "$ROOT"/requirements*.txt; do
    [ -f "$f" ] || continue
    hasdep "$f" 'django' && report "framework: Django"
    hasdep "$f" 'flask' && report "framework: Flask"
    hasdep "$f" 'fastapi' && report "framework: FastAPI"
    hasdep "$f" 'pytest' && report "tests: pytest"
  done
  echo
fi

# ----- JVM (Maven / Gradle) -----
if [ -f "$ROOT/pom.xml" ]; then
  echo "----- JVM (Maven) -----"
  report "manifest: pom.xml"
  grep -q '<modules>' "$ROOT/pom.xml" 2>/dev/null && report "layout: multi-module Maven"
  hasdep "$ROOT/pom.xml" 'spring-boot' && report "framework: Spring Boot"
  hasdep "$ROOT/pom.xml" 'kotlin' && report "language: Kotlin"
  report "build: mvn clean package | test: mvn test"
  echo
fi
if [ -f "$ROOT/build.gradle" ] || [ -f "$ROOT/build.gradle.kts" ]; then
  echo "----- JVM (Gradle) -----"
  report "manifest: build.gradle(.kts)"
  { [ -f "$ROOT/settings.gradle" ] || [ -f "$ROOT/settings.gradle.kts" ]; } && report "layout: multi-project Gradle (settings.gradle)"
  for f in "$ROOT/build.gradle" "$ROOT/build.gradle.kts"; do
    hasdep "$f" 'spring-boot' && report "framework: Spring Boot"
    hasdep "$f" 'kotlin' && report "language: Kotlin"
  done
  report "build: ./gradlew build | test: ./gradlew test"
  echo
fi

# ----- Go -----
if [ -f "$ROOT/go.mod" ]; then
  echo "----- Go -----"
  report "manifest: go.mod"
  report "module: $(head -1 "$ROOT/go.mod" 2>/dev/null | awk '{print $2}')"
  report "go version: $(grep -E '^go [0-9]' "$ROOT/go.mod" 2>/dev/null | awk '{print $2}')"
  report "build: go build ./... | test: go test ./..."
  echo
fi

# ----- Rust -----
if [ -f "$ROOT/Cargo.toml" ]; then
  echo "----- Rust -----"
  report "manifest: Cargo.toml"
  grep -q '\[workspace\]' "$ROOT/Cargo.toml" 2>/dev/null && report "layout: Cargo workspace"
  report "build: cargo build | test: cargo test"
  echo
fi

# ----- .NET -----
if ls "$ROOT"/*.csproj "$ROOT"/*.sln >/dev/null 2>&1; then
  echo "----- .NET -----"
  report "manifest: $(ls "$ROOT"/*.sln "$ROOT"/*.csproj 2>/dev/null | xargs -n1 basename | tr '\n' ' ')"
  report "build: dotnet build | test: dotnet test"
  echo
fi

# ----- Ruby / PHP -----
[ -f "$ROOT/Gemfile" ] && { echo "----- Ruby -----"; report "manifest: Gemfile"; echo; }
[ -f "$ROOT/composer.json" ] && { echo "----- PHP -----"; report "manifest: composer.json"; echo; }

# ----- Container -----
if [ -f "$ROOT/Dockerfile" ] || ls "$ROOT"/docker-compose*.y*ml >/dev/null 2>&1; then
  echo "----- Container / Orchestration -----"
  [ -f "$ROOT/Dockerfile" ] && report "Dockerfile present (base: $(grep -iE '^FROM ' "$ROOT/Dockerfile" 2>/dev/null | head -1 | awk '{print $2}'))"
  ls "$ROOT"/docker-compose*.y*ml >/dev/null 2>&1 && report "docker-compose present"
  echo
fi

if [ "$found" -eq 0 ]; then
  echo "No recognised manifest at the repository root."
  echo "This may be a polyglot monorepo (check subfolders) or a non-standard layout."
fi

echo "=================================================================="
echo "STACK DETECTION COMPLETE"
echo "=================================================================="
