#!/usr/bin/env bash
# find-entrypoints.sh — locate manifests and likely application entry points in a repository.
#
# Read-only. Surfaces dependency/build manifests (which identify the stack) and common entry-point
# files. Respects .gitignore via `git ls-files` when available; otherwise prunes generated dirs.
#
# Usage: bash find-entrypoints.sh [path]   (defaults to current directory)
set -u

ROOT="${1:-.}"
[ -d "$ROOT" ] || { echo "find-entrypoints.sh: not a directory: $ROOT" >&2; exit 2; }
ROOT="$(cd "$ROOT" && pwd)"
PRUNE='node_modules|vendor|dist|build|target|out|.git|.venv|venv|__pycache__|.gradle|.idea|.vscode'

have_git=0
if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  have_git=1
fi

# All candidate files, gitignore-aware, as paths relative to ROOT.
all_files() {
  if [ "$have_git" -eq 1 ]; then
    git -C "$ROOT" ls-files --cached --others --exclude-standard
  else
    ( cd "$ROOT" && find . -type d -regextype posix-extended -regex ".*/($PRUNE)(/.*)?" -prune -o -type f -print | sed 's|^\./||' )
  fi
}

FILES_TMP="$(mktemp)"; trap 'rm -f "$FILES_TMP"' EXIT
all_files | grep -Ev "^($PRUNE)/|/($PRUNE)/" > "$FILES_TMP"

echo "=================================================================="
echo "MANIFESTS & ENTRY POINTS: $ROOT"
echo "=================================================================="

print_section() {
  local title="$1"; shift
  local pattern="$1"; shift
  local hits
  hits=$(grep -E "$pattern" "$FILES_TMP" 2>/dev/null | sort)
  if [ -n "$hits" ]; then
    echo "----- $title -----"
    echo "$hits" | sed 's|^|  |'
    echo
  fi
}

# --- Dependency / build manifests (identify the stack) ---
print_section "DEPENDENCY / BUILD MANIFESTS" \
  '(^|/)(package\.json|pnpm-workspace\.yaml|pyproject\.toml|setup\.py|setup\.cfg|requirements[^/]*\.txt|Pipfile|go\.mod|Cargo\.toml|pom\.xml|build\.gradle(\.kts)?|settings\.gradle(\.kts)?|[^/]+\.csproj|[^/]+\.sln|Gemfile|composer\.json)$'

# --- Container / orchestration ---
print_section "CONTAINER / ORCHESTRATION" \
  '(^|/)(Dockerfile|[^/]*\.dockerfile|docker-compose[^/]*\.ya?ml|entrypoint\.sh|Procfile)$'

# --- Common entry-point source files ---
print_section "LIKELY ENTRY-POINT FILES" \
  '(^|/)(main\.(go|rs|py|ts|js)|index\.(ts|js)|__main__\.py|app\.py|wsgi\.py|asgi\.py|manage\.py|Program\.cs)$'

# --- JVM main classes / Spring Boot apps (content scan) ---
echo "----- JVM ENTRY POINTS (main / Spring Boot) -----"
jvm_hits=""
while IFS= read -r rel; do
  case "$rel" in
    *.java|*.kt)
      if grep -lqE 'public static void main|@SpringBootApplication' "$ROOT/$rel" 2>/dev/null; then
        jvm_hits="$jvm_hits  $rel"$'\n'
      fi
      ;;
  esac
done < "$FILES_TMP"
if [ -n "$jvm_hits" ]; then printf '%s' "$jvm_hits"; else echo "  (none found)"; fi
echo

echo "=================================================================="
echo "Tip: read the manifest first — its name/scripts/dependencies identify the stack and commands."
echo "=================================================================="
