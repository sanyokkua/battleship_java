#!/usr/bin/env python3
"""
sync-agent-files.py — keep the two agent skill trees holding the same skills.

Two agents work here, Codex and Claude Code. Both read AGENTS.md natively, so
there is nothing to mirror for instructions; the script only checks that the
file is a regular file of sane length. Spec Kit renders skills once per agent
into two real directories, .agents/skills (Codex) and .claude/skills (Claude
Code). They must hold the same skills with the same content apart from that
rendering: three Claude-only frontmatter keys and the `/speckit-` vs
`$speckit-` invocation prefix.

Usage:
    python3 scripts/sync-agent-files.py            # same as --check
    python3 scripts/sync-agent-files.py --check    # read-only; exit 1 on any problem  [pre-push]
    python3 scripts/sync-agent-files.py --apply    # copy skills that exist in only one tree

Content drift between two copies of a skill is never resolved automatically.

Stdlib only. Python 3.9+.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

TREES = (".agents/skills", ".claude/skills")
MAX_LINES = 200
CLAUDE_ONLY_KEYS = ("argument-hint:", "user-invocable:", "disable-model-invocation:")
# `/speckit-x` or `$speckit-x` as an invocation, never as part of a path such as `.claude/skills/speckit-x`.
INVOCATION = re.compile(r"(?<![\w/.-])[/$](speckit-)")


class Report:
    def __init__(self) -> None:
        self.counts = {"OK": 0, "FIXED": 0, "WARN": 0, "FAIL": 0}

    def line(self, marker: str, msg: str) -> None:
        self.counts[marker] += 1
        print(f"  {marker:<5}  {msg}")


def check_instructions(root: Path, rep: Report) -> None:
    print("\nInstructions")
    agents = root / "AGENTS.md"
    if agents.is_symlink() or not agents.is_file():
        rep.line("FAIL", "AGENTS.md must exist at the repository root as a regular file, not a symlink")
        return
    lines = len(agents.read_text(encoding="utf-8", errors="replace").splitlines())
    if lines > MAX_LINES:
        rep.line("WARN", f"AGENTS.md is {lines} lines (target <= {MAX_LINES}); adherence degrades with length")
    else:
        rep.line("OK", f"AGENTS.md is {lines} lines; Codex and Claude Code both read it natively")


def normalise(data: bytes) -> bytes | str:
    """Strip the per-agent rendering from a text file; binary files are compared as bytes."""
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        return data
    head, sep, body = text.partition("\n---\n") if text.startswith("---\n") else ("", "", text)
    head = "\n".join(ln for ln in head.split("\n") if not ln.startswith(CLAUDE_ONLY_KEYS))
    return INVOCATION.sub(r"@\1", head + sep + body)


def drift(a: Path, b: Path) -> list[str]:
    """Relative paths that are missing from one copy of a skill or differ after normalisation."""
    files_a = {p.relative_to(a).as_posix() for p in a.rglob("*") if p.is_file()}
    files_b = {p.relative_to(b).as_posix() for p in b.rglob("*") if p.is_file()}
    differing = {r for r in files_a & files_b if normalise((a / r).read_bytes()) != normalise((b / r).read_bytes())}
    return sorted((files_a ^ files_b) | differing)


def real_trees(root: Path, rep: Report) -> bool:
    usable = True
    for tree in TREES:
        path = root / tree
        linked = path.is_symlink() or path.resolve() != root / tree
        if path.is_dir() and not linked:
            continue
        state = "reached through a symlink" if linked else "missing"
        rep.line("FAIL", f"{tree} is {state}; two real trees are expected, one rendered per agent")
        usable = False
    return usable


def sync_missing(root: Path, rep: Report, apply: bool, name: str, have: str, lack: str) -> None:
    if not apply:
        rep.line("FAIL", f"{name} is missing from {lack} (present in {have}); run --apply to copy it")
        return
    try:
        shutil.copytree(root / have / name, root / lack / name)
        rep.line("FIXED", f"copied {name} verbatim from {have} to {lack}")
    except OSError as exc:
        rep.line("FAIL", f"cannot copy {name} to {lack}: {exc}")


def check_skills(root: Path, rep: Report, apply: bool) -> None:
    print("\nSkills")
    if not real_trees(root, rep):
        return
    first, second = ({p.name for p in (root / tree).iterdir() if (p / "SKILL.md").is_file()} for tree in TREES)
    for name in sorted(first - second):
        sync_missing(root, rep, apply, name, TREES[0], TREES[1])
    for name in sorted(second - first):
        sync_missing(root, rep, apply, name, TREES[1], TREES[0])

    matching = 0
    for name in sorted(first & second):
        files = drift(root / TREES[0] / name, root / TREES[1] / name)
        if files:
            rep.line("FAIL", f"content drift in {name} ({', '.join(files)}); never resolved automatically: "
                             "edit both copies or regenerate with Spec Kit")
        else:
            matching += 1
    if matching:
        rep.line("OK", f"{matching} skill(s) match in both trees apart from the per-agent rendering")


def main() -> int:
    parser = argparse.ArgumentParser(description="Check that both agent skill trees hold the same skills.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="read-only; exit 1 on any problem (default)")
    mode.add_argument("--apply", action="store_true", help="copy skills that exist in only one tree, then report")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="repository root (default: the parent of this script's directory)")
    args = parser.parse_args()

    root = args.root.resolve()
    rep = Report()
    print(f"sync-agent-files  ·  {root}")
    check_instructions(root, rep)
    check_skills(root, rep, args.apply)

    fail, warn, fixed = rep.counts["FAIL"], rep.counts["WARN"], rep.counts["FIXED"]
    verb = "remain" if args.apply else "found"
    print(f"\n{fail} problem(s) {verb}, {warn} warning(s), {fixed} fixed." if fail or warn or fixed else "\nIn sync.")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
