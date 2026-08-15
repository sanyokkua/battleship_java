# Dual-Output Workflow — Temporary vs. Committed Documentation

This skill writes to **one of two destinations**, chosen by the `output_target` parameter. The
analysis, section layout, and validation are identical for both; only *where the files land* and
*whether they are meant to be committed* differ. This reference defines each target precisely.

---

## Decision: which target?

| Ask | Choose |
|---|---|
| "Document this for the team / for the repo / for review" | `committed` (default) |
| "Initialize project documentation", "write the README and docs" | `committed` |
| "I just need scratch notes", "analyze this so another step can use it", "throwaway", "don't commit this" | `temporary` |
| Unsure | `committed` — it is the durable, reviewable deliverable; the user can always discard it |

State the chosen target explicitly in your completion report.

---

## Target A — `committed` (default): maintained docs under `docs/` + `README.md`

Intended for humans, code review, and version control.

### Output layout

```
<repo>/
├── README.md              → overview + quick start + links into docs/ (use assets/readme-template.md)
└── docs/
    ├── index.md           → the full project documentation (use assets/project-doc-template.md)
    ├── architecture.md    → (optional) deeper architecture write-up + diagrams
    └── diagrams/          → (optional) generated .mmd / .drawio / exported images
```

For a small project, a single `docs/index.md` plus an updated `README.md` is sufficient. Split into
`architecture.md` / `diagrams/` only when the content warrants it.

### Rules

1. **Confirm before overwriting.** If `docs/` or `README.md` already exists, summarize what is there
   and confirm with the user before replacing it. Prefer updating in place over clobbering.
2. **Do NOT commit or push.** Write the files and stop. Leave staging/committing/pushing to the user.
3. **Keep it co-located with code** so the docs are found without a separate system.
4. **No secrets.** Reference configuration keys/paths, never secret values.
5. **Embed at least one diagram** (architecture or data-flow) following `references/mermaid-rules.md` (embedded — no separate Mermaid skill required).

---

## Target B — `temporary`: git-ignored `.agent-docs/` working folder

Intended as scratch/working memory — extraction tables, raw notes, intermediate analysis that another
agent step consumes. Never committed.

### Output layout

```
<repo>/
└── .agent-docs/           → hidden, git-ignored working folder
    ├── index.md           → same structure as the committed doc (use the same template)
    ├── extraction/        → raw extraction notes (entry points, exit points, config chain tables)
    └── diagrams/          → working diagrams
```

### Rules

1. **Ensure it is git-ignored.** Before writing, confirm `.gitignore` contains an entry that ignores
   the folder (e.g. `/.agent-docs/`). If `.gitignore` exists but lacks it, append the entry. If there
   is no `.gitignore`, create one containing `/.agent-docs/`.

   ```bash
   # add the ignore entry if missing (run from the repo root)
   grep -qxF '/.agent-docs/' .gitignore 2>/dev/null || echo '/.agent-docs/' >> .gitignore
   ```

2. **Never commit it.** It is working memory; do not stage, commit, or push it, and do not suggest the
   user do so.
3. **Same content quality.** Even though it is throwaway, follow the same per-section requirements and
   the "no invented facts / mark gaps TODO" rule — downstream steps rely on its accuracy.
4. **Safe to regenerate.** Treat the folder as disposable; overwriting it freely is expected (no
   overwrite confirmation needed, unlike `committed`).

---

## Shared rules (both targets)

- Gather facts first with the bundled scripts (`inventory.sh`, `detect-stack.sh`, `git-metadata.sh`).
- Use the same template (`assets/project-doc-template.md`) for the main document regardless of target.
- Every entry point, exit point, and business-logic flow found in code must be documented and traceable
  to a file (`path#symbol`).
- Mark unknowns `TODO: confirm` rather than inventing.
- Document the configuration chain by key/path, never by secret value.
- Finish with a completion report stating: target used, files written, diagrams generated, and any
  sections left as TODO.
