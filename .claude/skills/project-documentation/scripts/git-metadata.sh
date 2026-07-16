#!/usr/bin/env bash
# git-metadata.sh — repository metadata for documentation context.
#
# Read-only. Emits: repo name, remote URL, default/current branch, latest tag, commit count,
# recent activity (last commit date, commits in last 90 days), and top recent contributors.
# Degrades gracefully when the path is not a git repository or `git` is unavailable.
#
# Usage: bash git-metadata.sh [path]   (defaults to current directory)
set -u

ROOT="${1:-.}"
[ -d "$ROOT" ] || { echo "git-metadata.sh: not a directory: $ROOT" >&2; exit 2; }
ROOT="$(cd "$ROOT" && pwd)"

echo "=================================================================="
echo "GIT METADATA: $ROOT"
echo "=================================================================="

if ! command -v git >/dev/null 2>&1; then
  echo "git is not installed — metadata unavailable."
  echo "Repository name (folder): $(basename "$ROOT")"
  echo "=================================================================="
  exit 0
fi

if ! git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a git repository."
  echo "Repository name (folder): $(basename "$ROOT")"
  echo "=================================================================="
  exit 0
fi

G() { git -C "$ROOT" "$@" 2>/dev/null; }

# --- Name & remote ---
remote_url="$(G config --get remote.origin.url)"
if [ -n "$remote_url" ]; then
  name="$(basename "${remote_url%.git}")"
else
  name="$(basename "$ROOT")"
fi
echo "Repository name : $name"
echo "Remote URL      : ${remote_url:-<none>}"

# --- Default / current branch ---
default_branch="$(G symbolic-ref --short refs/remotes/origin/HEAD | sed 's@^origin/@@')"
[ -z "$default_branch" ] && default_branch="$(G remote show origin | sed -n 's/.*HEAD branch: //p')"
current_branch="$(G rev-parse --abbrev-ref HEAD)"
echo "Default branch  : ${default_branch:-<unknown>}"
echo "Current branch  : ${current_branch:-<unknown>}"

# --- Tag & counts ---
latest_tag="$(G describe --tags --abbrev=0)"
commit_count="$(G rev-list --count HEAD)"
echo "Latest tag      : ${latest_tag:-<none>}"
echo "Total commits   : ${commit_count:-0}"

# --- Activity ---
last_commit="$(G log -1 --format='%cd (%h) %s' --date=short)"
recent_90="$(G rev-list --count --since='90 days ago' HEAD)"
echo "Last commit     : ${last_commit:-<none>}"
echo "Commits (90d)   : ${recent_90:-0}"

# --- Top recent contributors ---
echo "----- TOP CONTRIBUTORS (last 12 months) -----"
contribs="$(G shortlog -sn --no-merges --since='12 months ago' HEAD | head -n 8)"
if [ -n "$contribs" ]; then
  echo "$contribs" | sed 's/^/  /'
else
  echo "  (no commits in the last 12 months — showing all-time top)"
  G shortlog -sn --no-merges HEAD | head -n 8 | sed 's/^/  /'
fi

echo "=================================================================="
echo "GIT METADATA COMPLETE"
echo "=================================================================="
