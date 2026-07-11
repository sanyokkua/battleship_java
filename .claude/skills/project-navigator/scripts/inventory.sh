#!/usr/bin/env bash
# inventory.sh — compact, read-only map of a repository.
#
# Emits: directory tree, total/per-extension file counts, total size, largest files,
#        a file-extension (language) histogram, and total lines of code.
# Respects .gitignore (uses `git ls-files` when inside a git repo) and always skips common
# vendored/generated directories. Degrades gracefully when `tree`, `git`, or `cloc` are missing.
#
# Usage: bash inventory.sh [path]   (defaults to current directory)
set -u

ROOT="${1:-.}"
if [ ! -d "$ROOT" ]; then
  echo "inventory.sh: not a directory: $ROOT" >&2
  exit 2
fi
ROOT="$(cd "$ROOT" && pwd)"

# Directories that are always noise.
PRUNE='node_modules|vendor|dist|build|target|out|.git|.venv|venv|__pycache__|.gradle|.idea|.vscode|bin|obj|.next|.cache|coverage'

echo "=================================================================="
echo "REPOSITORY INVENTORY: $ROOT"
echo "=================================================================="

# ---- Choose the file-listing strategy (gitignore-aware when possible) ----
have_git=0
if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  have_git=1
fi

list_files() {
  if [ "$have_git" -eq 1 ]; then
    # Tracked + untracked-but-not-ignored. Print absolute paths.
    git -C "$ROOT" ls-files --cached --others --exclude-standard -z \
      | while IFS= read -r -d '' f; do printf '%s\n' "$ROOT/$f"; done
  else
    # Fallback: find, pruning noise directories.
    find "$ROOT" -type d -regextype posix-extended -regex ".*/($PRUNE)(/.*)?" -prune -o -type f -print
  fi
}

# Materialize once.
FILES_TMP="$(mktemp)"
trap 'rm -f "$FILES_TMP"' EXIT
list_files | grep -Ev "/($PRUNE)/" > "$FILES_TMP"

if [ "$have_git" -eq 1 ]; then
  echo "(file list: git ls-files — respecting .gitignore)"
else
  echo "(file list: find fallback — git unavailable; pruning common generated dirs)"
fi
echo

# ---- 1. Directory tree ----
echo "----- DIRECTORY TREE -----"
if command -v tree >/dev/null 2>&1; then
  tree -a -L 3 -I "$PRUNE" "$ROOT" 2>/dev/null | head -n 120
else
  echo "(tree not installed — showing pruned 'find' listing, depth 2)"
  find "$ROOT" -maxdepth 2 -type d -regextype posix-extended \
       -regex ".*/($PRUNE)(/.*)?" -prune -o -type d -print 2>/dev/null \
    | sed "s|$ROOT|.|" | sort | head -n 80
fi
echo

# ---- 2. Counts & size ----
total_files=$(wc -l < "$FILES_TMP" | tr -d ' ')
echo "----- COUNTS & SIZE -----"
echo "Total files (excl. ignored/generated): $total_files"
total_bytes=0
while IFS= read -r f; do
  if [ -f "$f" ]; then
    sz=$(wc -c < "$f" 2>/dev/null || echo 0)
    total_bytes=$((total_bytes + sz))
  fi
done < "$FILES_TMP"
# Human-readable size.
if [ "$total_bytes" -ge 1048576 ]; then
  echo "Total size: $((total_bytes / 1048576)) MiB"
elif [ "$total_bytes" -ge 1024 ]; then
  echo "Total size: $((total_bytes / 1024)) KiB"
else
  echo "Total size: ${total_bytes} B"
fi
echo

# ---- 3. Extension / language histogram ----
echo "----- EXTENSION HISTOGRAM (top 20) -----"
sed -E 's|.*/||; s|^[^.]+$|(no-ext)|; s|.*\.|.|' "$FILES_TMP" \
  | sort | uniq -c | sort -rn | head -n 20 \
  | awk '{ printf "  %-12s %5d\n", $2, $1 }'
echo

# ---- 4. Largest files ----
echo "----- LARGEST FILES (top 10) -----"
while IFS= read -r f; do
  [ -f "$f" ] && printf '%s\t%s\n' "$(wc -c < "$f" 2>/dev/null || echo 0)" "$f"
done < "$FILES_TMP" \
  | sort -rn | head -n 10 \
  | awk -F'\t' '{ kb = $1/1024; printf "  %8.1f KiB  %s\n", kb, $2 }' \
  | sed "s|$ROOT/||"
echo

# ---- 5. Lines of code ----
echo "----- LINES OF CODE -----"
if command -v cloc >/dev/null 2>&1; then
  cloc --quiet "$ROOT" 2>/dev/null | tail -n 25
else
  echo "(cloc not installed — using wc total over source-like files)"
  total_loc=0
  while IFS= read -r f; do
    case "$f" in
      *.js|*.jsx|*.ts|*.tsx|*.py|*.java|*.kt|*.go|*.rs|*.cs|*.rb|*.php|*.c|*.h|*.cpp|*.hpp|*.sh|*.scala|*.swift|*.sql|*.yml|*.yaml|*.json|*.md)
        if [ -f "$f" ]; then
          lc=$(wc -l < "$f" 2>/dev/null || echo 0)
          total_loc=$((total_loc + lc))
        fi
        ;;
    esac
  done < "$FILES_TMP"
  echo "  Total lines (source-like files): $total_loc"
fi
echo
echo "=================================================================="
echo "INVENTORY COMPLETE"
echo "=================================================================="
