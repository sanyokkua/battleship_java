# Cleanup execution prompt

Use this prompt for the cleanup task. It is a standalone input for the cleanup
stage immediately before the Foundation phase. It does not authorize SpecKit
initialization, agent configuration, implementation, commits, pushes, or any
change outside the reviewed removal.

```text
You are performing the tracked-file cleanup for the Battleship rewrite.

Read first:
- docs/rewrite_context_doc/Planning/01-cleanup/cleanup-manifest.md
- AGENTS.md, if it is still present
- git status --short --branch

Your only authorized output is a reviewed, staged Git removal of tracked paths
outside LICENSE and docs/rewrite_context_doc/. Do not commit. Do not run
specify init. Do not create or edit agent configuration. Do not touch untracked
files.

PREVIEW BOUNDARY — read-only
1. Confirm the current branch. It must be rewrite_prod_ready or exactly
   feature/rewrite-foundation-cleanup. For the task branch, confirm that both
   rewrite_prod_ready and feature/rewrite-foundation are ancestors. Abort if
   any check fails.
2. Treat rewrite_prod_ready as protected: you may preview there, but you may
   not apply a deletion there. Applying requires a
   feature/rewrite-foundation-cleanup, created from the
   feature/rewrite-foundation parent based on rewrite_prod_ready.
3. Inspect git status. Abort if tracked paths have staged or unstaged changes.
   List all untracked paths, including ignored ones, state that they are
   preserved, and leave them alone.
4. Make a temporary manifest outside the repository:
   manifest_path="$(mktemp -t battleship-rewrite-cleanup.XXXXXX)"
   untracked_path="$(mktemp -t battleship-rewrite-untracked.XXXXXX)"
   git ls-files -z -- ':!LICENSE' ':!docs/rewrite_context_doc/**' > "$manifest_path"
   git ls-files -z --others > "$untracked_path"
   tr '\0' '\n' < "$manifest_path"
   printf 'Deletion manifest: %s\n' "$manifest_path"
   shasum -a 256 "$manifest_path"
   printf 'Preserved untracked manifest: %s\n' "$untracked_path"
   shasum -a 256 "$untracked_path"
   tr '\0' '\n' < "$untracked_path"
5. Review the printed list. Abort if it is empty, contains LICENSE, contains a
   path under docs/rewrite_context_doc/, or contains any path whose removal is
   not expected. Do not narrow the list yourself.
6. Report the exact candidate list, both temporary paths and SHA-256 digests,
   and the preserved untracked paths. Stop here. Ask for explicit approval to
   apply this exact deletion manifest.

APPROVAL BOUNDARY
Do not run git rm, stage another path, or delete any filesystem path unless the
user explicitly approves the exact manifest printed in the preview. Approval
does not authorize a commit, a push, SpecKit initialization, or an untracked
file cleanup.

APPLY BOUNDARY — after explicit approval only
1. Re-check that the current branch is exactly
   feature/rewrite-foundation-cleanup and that it descends from the named
   parent and protected base. Abort on any other branch.
2. Set approved_manifest_path and approved_untracked_path to the exact literal
   temporary paths printed in the approved preview. Verify both reported
   SHA-256 digests. Abort if a file is missing or either digest differs.
3. Apply only the previously reviewed temporary deletion manifest:
   git rm --pathspec-from-file="$approved_manifest_path" --pathspec-file-nul
4. Verify, without committing:
   git diff --cached --name-status
   git diff --cached --name-only -- LICENSE docs/rewrite_context_doc
   current_staged_path="$(mktemp -t battleship-rewrite-current-staged.XXXXXX)"
   git diff --cached --name-only -z --diff-filter=D > "$current_staged_path"
   cmp "$approved_manifest_path" "$current_staged_path"
   current_untracked_path="$(mktemp -t battleship-rewrite-current-untracked.XXXXXX)"
   git ls-files -z --others > "$current_untracked_path"
   cmp "$approved_untracked_path" "$current_untracked_path"
   git status --short
5. Abort and report if a preserved path appears in the staged diff or if any
   untracked path was changed. Otherwise report that the staged removal is
   ready for the cleanup task's normal review and commit.

Safety constraints:
- Preserve .git/, LICENSE, and every byte and path under
  docs/rewrite_context_doc/.
- Remove all other tracked paths only through reviewed git rm.
- Never run rm, git clean, git reset --hard, or a broad scripted deletion.
- Never delete, move, stage, or overwrite untracked files automatically.
- Do not commit or push.
```

The expected result is a staged, human-reviewed removal manifest whose diff
contains all and only old tracked project material. The Foundation phase starts
only after the cleanup task is independently reviewed and committed on its
dedicated branch.
