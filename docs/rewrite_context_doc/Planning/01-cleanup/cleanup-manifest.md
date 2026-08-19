# Rewrite cleanup manifest

## Purpose and phase

This is the cleanup input for the rewrite launch. It is consumed before the
SpecKit foundation command in
`Planning/02-foundation/prerequisites-and-speckit-init.md`. It authorizes a
reviewed removal of the old application only; it does not initialize SpecKit,
create agent files, or add application code.

## Preserve exactly

Keep these repository paths exactly as they are:

- `.git/` and all of its contents.
- `LICENSE` at the repository root.
- `docs/rewrite_context_doc/` and every file and directory below it.

All other **tracked** paths are cleanup candidates. This includes the old
implementation, old specifications, generated histories, documentation outside
the preserved context, build configuration, and project agent configuration.
The candidate set is defined by Git, not by a hand-maintained list, so a new
tracked file cannot be missed.

Untracked files are never part of this manifest. List all of them during the
preview, including paths currently hidden by ignore rules, but neither delete,
move, stage, nor `git clean` them automatically. Their owner must decide what
to do with them separately. Capturing ignored paths matters because the cleanup
removes the old tracked ignore file; the proof must compare path identity, not
the before/after ignore classification.

## Required branch and clean-state checks

`rewrite_prod_ready` is the protected base. Do not commit directly on it. Create
the parent branch `feature/rewrite-foundation` from that base, then create the
task branch `feature/rewrite-foundation--cleanup`. The cleanup may be previewed
on the protected base, but it may be applied only on that exact task branch.

Before creating a deletion manifest, abort if any of these are true:

- Git has no current branch, the branch is neither `rewrite_prod_ready` nor
  `feature/rewrite-foundation--cleanup`, or the task branch does not descend
  from both `rewrite_prod_ready` and `feature/rewrite-foundation`.
- A tracked file has staged or unstaged changes. Resolve or intentionally stash
  them outside this procedure; do not mix them with the cleanup.
- `LICENSE` or any path under `docs/rewrite_context_doc/` appears in the
  generated candidate list.
- The manifest is empty or contains an unexpected path. Stop for human review;
  do not improvise a narrower or broader cleanup.

Untracked paths alone do not fail the check. They must be shown clearly as
preserved paths.

## Exact tracked deletion manifest

Create the manifest outside the repository, with NUL separators so every Git
path is represented without ambiguity:

```sh
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
```

The printed output is the exact tracked deletion list. Review it before any
write. The command intentionally has no `git rm` side effect.

The resulting list must contain every tracked path outside the preservation
set, including old code, tests, build files, old specs, generated records,
documentation, and agent configuration. It must not contain the preserved
license or rewrite context. `.git/` is not a tracked path and is never passed
to `git rm`.

## Apply only after explicit approval

Record both printed temporary paths and both SHA-256 digests in the preview
report. After the deletion list has been reviewed and explicitly approved,
first ensure the current branch is the exact cleanup task branch described
above. In the apply shell, set `approved_manifest_path` and
`approved_untracked_path` to the exact literal paths from that report and verify
both digests. Do not regenerate either list. Then apply the approved deletion
manifest:

```sh
shasum -a 256 "$approved_manifest_path" "$approved_untracked_path"
git rm --pathspec-from-file="$approved_manifest_path" --pathspec-file-nul
git diff --cached --name-status
git diff --cached -- LICENSE docs/rewrite_context_doc
current_staged_path="$(mktemp -t battleship-rewrite-current-staged.XXXXXX)"
git diff --cached --name-only -z --diff-filter=D > "$current_staged_path"
cmp "$approved_manifest_path" "$current_staged_path"
current_untracked_path="$(mktemp -t battleship-rewrite-current-untracked.XXXXXX)"
git ls-files -z --others > "$current_untracked_path"
cmp "$approved_untracked_path" "$current_untracked_path"
git status --short
```

`git rm` is the required recoverable removal mechanism. Do not use `rm`,
`git clean`, `git reset --hard`, a bulk filesystem delete, or an automatic
command that acts on untracked files. Do not commit as part of this manifest;
the dedicated cleanup task owns its review, verification, and commit.

## Verification before handoff

Before handing cleanup to the foundation task, verify all of the following:

- `git diff --cached --name-only -z --diff-filter=D` is byte-identical to the
  reviewed NUL-separated manifest.
- `git diff --cached --name-only -- LICENSE docs/rewrite_context_doc` prints
  nothing.
- The current all-untracked-path list, including ignored files, is
  byte-identical to the approved untracked manifest.
- `LICENSE` remains readable and `docs/rewrite_context_doc/` still contains
  this manifest and the other launch-kit files.
- `git status --short` shows no deletion or modification outside the staged,
  reviewed tracked cleanup; any untracked files are still present and unstaged.

The cleanup result is a minimal repository with the preserved context and
license. The next allowed action is the foundation procedure; no old project
convention survives by implication.
