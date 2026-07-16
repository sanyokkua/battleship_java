#!/usr/bin/env bash
# find-config.sh — locate configuration, infrastructure-as-code, and CI files in a repository.
#
# Read-only. Respects .gitignore via `git ls-files` when available; otherwise prunes generated dirs.
#
# Usage: bash find-config.sh [path]   (defaults to current directory)
set -u

ROOT="${1:-.}"
[ -d "$ROOT" ] || { echo "find-config.sh: not a directory: $ROOT" >&2; exit 2; }
ROOT="$(cd "$ROOT" && pwd)"
PRUNE='node_modules|vendor|dist|build|target|out|.git|.venv|venv|__pycache__|.gradle|.idea|.vscode'

have_git=0
if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  have_git=1
fi

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
echo "CONFIGURATION / IaC / CI FILES: $ROOT"
echo "=================================================================="

section() {
  local title="$1"; shift
  local pattern="$1"; shift
  local hits
  hits=$(grep -E "$pattern" "$FILES_TMP" 2>/dev/null | sort)
  echo "----- $title -----"
  if [ -n "$hits" ]; then echo "$hits" | sed 's|^|  |'; else echo "  (none found)"; fi
  echo
}

# --- Application configuration ---
section "APPLICATION CONFIG" \
  '(^|/)(application[^/]*\.(ya?ml|properties)|appsettings[^/]*\.json|config[^/]*\.(ya?ml|json|toml)|settings\.(py|toml)|[^/]*\.env(\.[^/]+)?|\.env(\.[^/]+)?)$'

# --- Continuous integration ---
section "CONTINUOUS INTEGRATION" \
  '(^|/)(\.github/workflows/[^/]+\.ya?ml|\.gitlab-ci\.yml|\.circleci/config\.yml|azure-pipelines\.yml|bitbucket-pipelines\.yml|\.travis\.yml|\.drone\.yml)$'

# --- Infrastructure as code (technology-agnostic) ---
section "INFRASTRUCTURE AS CODE" \
  '(^|/)(infra|deploy|iac|ci)/.*\.(ya?ml|yml|json|tf|tf\.json|template)$|(^|/)[^/]*\.tf$|(^|/)(cloudformation|cfn)/.*\.(ya?ml|json)$'

# --- Container / orchestration config ---
section "CONTAINER / ORCHESTRATION CONFIG" \
  '(^|/)(Dockerfile|docker-compose[^/]*\.ya?ml|kustomization\.ya?ml|Chart\.ya?ml|values[^/]*\.ya?ml|k8s|kubernetes)/?'

# --- Code quality / tooling config ---
section "CODE QUALITY / TOOLING" \
  '(^|/)(\.eslintrc[^/]*|eslint\.config\.[^/]+|\.prettierrc[^/]*|ruff\.toml|\.editorconfig|tsconfig[^/]*\.json|renovate\.json|\.tool-versions|\.nvmrc|\.python-version)$'

echo "=================================================================="
echo "Tip: environment-specific config (e.g. *-prod) and the deploy/CI files are the"
echo "     authoritative current truth; one-time setup scripts are often stale."
echo "=================================================================="
