#!/usr/bin/env python3
"""
sync-agent-files.py — keep every agent harness reading the same bytes.

One canonical AGENTS.md. Every other entry point is a symlink, a three-line
import, or a generated artifact. `--check` proves it and exits non-zero on drift.

Usage:
    python3 scripts/sync-agent-files.py            # report only (default)
    python3 scripts/sync-agent-files.py --apply    # create links, regenerate mirrors
    python3 scripts/sync-agent-files.py --check    # assert; exit 1 on any drift  [CI / pre-push]
    python3 scripts/sync-agent-files.py --migrate  # one-time: move .claude/skills -> .agents/skills

Optional config at repo root, .agentsync.json — all keys optional:

    {
      "copilot":     true,      // .github/copilot-instructions.md -> ../AGENTS.md
      "gemini":      false,     // GEMINI.md -> AGENTS.md
      "codex":       true,      // .codex/skills symlink + .codex/agents/*.toml
      "max_lines":   200,       // warn above this
      "hard_lines":  250        // --check fails above this
    }

Stdlib only. Python 3.9+.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
from pathlib import Path

# ── output ────────────────────────────────────────────────────────────────────

USE_COLOR = sys.stdout.isatty() and os.environ.get("NO_COLOR") is None


def _c(code: str, text: str) -> str:
    return f"\033[{code}m{text}\033[0m" if USE_COLOR else text


class Report:
    def __init__(self) -> None:
        self.problems: list[str] = []
        self.actions: list[str] = []
        self.notes: list[str] = []

    def problem(self, msg: str) -> None:
        self.problems.append(msg)
        print(f"  {_c('31', 'FAIL')}  {msg}")

    def action(self, msg: str) -> None:
        self.actions.append(msg)
        print(f"  {_c('32', ' OK ')}  {msg}")

    def note(self, msg: str) -> None:
        self.notes.append(msg)
        print(f"  {_c('33', 'WARN')}  {msg}")

    def ok(self, msg: str) -> None:
        print(f"  {_c('32', ' OK ')}  {msg}")


DEFAULTS = {
    "copilot": True,
    "gemini": False,
    "codex": True,
    "max_lines": 200,
    "hard_lines": 250,
}


def load_config(root: Path) -> dict:
    cfg = dict(DEFAULTS)
    path = root / ".agentsync.json"
    if path.is_file():
        try:
            cfg.update(json.loads(path.read_text(encoding="utf-8")))
        except (json.JSONDecodeError, OSError) as exc:
            print(f"  {_c('33', 'WARN')}  cannot read .agentsync.json ({exc}); using defaults")
    return cfg


def find_root(start: Path) -> Path:
    for candidate in [start, *start.parents]:
        if (candidate / ".git").exists() or (candidate / "AGENTS.md").is_file():
            return candidate
    return start


# ── 1. the canonical file and the Claude bridge ───────────────────────────────

CLAUDE_MD = """@AGENTS.md

<!-- AGENTS.md is the single source of truth. Add rules there, not here.
     This file exists only because Claude Code does not read AGENTS.md natively.
     Below this line: Claude-Code-specific mechanics only (skills, hooks, subagents). -->
"""


def first_meaningful_line(text: str) -> str:
    for line in text.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("<!--"):
            return stripped
    return ""


def check_canonical(root: Path, cfg: dict, rep: Report, apply: bool) -> None:
    print(_c("1", "\nCanonical file"))

    agents = root / "AGENTS.md"
    if not agents.is_file():
        rep.problem("AGENTS.md is missing. It is the single source of truth; create it first.")
        return
    if agents.is_symlink():
        rep.problem("AGENTS.md is a symlink. The canonical file must be a regular file.")
        return

    lines = agents.read_text(encoding="utf-8").count("\n") + 1
    if lines > cfg["hard_lines"]:
        rep.problem(
            f"AGENTS.md is {lines} lines (hard ceiling {cfg['hard_lines']}). "
            "Move content to .claude/rules/ with paths: frontmatter, a skill, or a hook."
        )
    elif lines > cfg["max_lines"]:
        rep.note(f"AGENTS.md is {lines} lines (target <= {cfg['max_lines']}). Adherence degrades with length.")
    else:
        rep.ok(f"AGENTS.md is {lines} lines")

    print(_c("1", "\nClaude Code bridge"))
    claude = root / "CLAUDE.md"

    if claude.is_symlink():
        rep.problem(
            "CLAUDE.md is a symlink. Claude Code refuses to write through symlinks "
            "(breaks /init, /memory, /doctor) and Windows needs Admin to create one. "
            "Replace it with a regular file whose first line is '@AGENTS.md'."
        )
        if apply:
            claude.unlink()
            claude.write_text(CLAUDE_MD, encoding="utf-8")
            rep.action("replaced the CLAUDE.md symlink with the import stub")
        return

    if not claude.exists():
        if apply:
            claude.write_text(CLAUDE_MD, encoding="utf-8")
            rep.action("created CLAUDE.md with the @AGENTS.md import")
        else:
            rep.problem("CLAUDE.md is missing. Claude Code does not read AGENTS.md natively.")
        return

    text = claude.read_text(encoding="utf-8")
    if first_meaningful_line(text) != "@AGENTS.md":
        rep.problem("CLAUDE.md's first non-comment line is not '@AGENTS.md'; AGENTS.md will not load.")
        return

    body = "\n".join(
        ln for ln in text.splitlines()[1:] if ln.strip() and not ln.strip().startswith("<!--") and "-->" not in ln
    )
    if len(body.splitlines()) > 20:
        rep.note(
            f"CLAUDE.md carries {len(body.splitlines())} lines below the import. "
            "Only Claude-specific mechanics belong here; anything true under Codex goes in AGENTS.md."
        )
    else:
        rep.ok("CLAUDE.md imports AGENTS.md")


# ── 2. read-only symlink mirrors ──────────────────────────────────────────────


def sync_symlink(root: Path, link_rel: str, target_rel: str, rep: Report, apply: bool) -> None:
    link = root / link_rel
    resolved_target = (link.parent / target_rel).resolve()

    if link.is_symlink():
        if (link.parent / os.readlink(link)).resolve() == resolved_target:
            rep.ok(f"{link_rel} -> {target_rel}")
            return
        rep.problem(f"{link_rel} points at {os.readlink(link)}, expected {target_rel}")
        if apply:
            link.unlink()
            link.symlink_to(target_rel)
            rep.action(f"repointed {link_rel} -> {target_rel}")
        return

    if link.exists():
        rep.problem(f"{link_rel} is a real file or directory; it must be a symlink to {target_rel} (drift risk)")
        return

    if apply:
        link.parent.mkdir(parents=True, exist_ok=True)
        try:
            link.symlink_to(target_rel)
            rep.action(f"linked {link_rel} -> {target_rel}")
        except OSError as exc:
            rep.problem(f"cannot create {link_rel}: {exc} (on Windows, enable Developer Mode)")
    else:
        rep.problem(f"{link_rel} is missing (should link to {target_rel})")


def check_never_mirrored(root: Path, rep: Report) -> None:
    for path in (".codex/settings.json", ".codex/hooks", ".agents/settings.json", ".agents/hooks"):
        if (root / path).exists():
            rep.problem(f"{path} exists. settings.json and hooks/ are Claude-specific and must never be mirrored.")


# ── 3. generated: .claude/agents/*.md -> .codex/agents/*.toml ─────────────────

FRONTMATTER = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?(.*)\Z", re.DOTALL)
GENERATED_BANNER = "# GENERATED by scripts/sync-agent-files.py — do not edit. Source: .claude/agents/{name}.md\n"


def parse_agent(path: Path) -> tuple[dict, str]:
    """Return (frontmatter dict, body). Flat `key: value` only — no nested YAML."""
    match = FRONTMATTER.match(path.read_text(encoding="utf-8"))
    if not match:
        return {}, path.read_text(encoding="utf-8")
    meta: dict[str, str] = {}
    for line in match.group(1).splitlines():
        if line.strip().startswith("#") or ":" not in line or line.startswith((" ", "\t", "-")):
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip().strip("'\"")
    return meta, match.group(2)


def toml_basic(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
    return f'"{escaped}"'


def toml_multiline(value: str) -> str:
    # A literal `"""` would terminate the string; escape the final quote of any run.
    escaped = value.replace("\\", "\\\\").replace('"""', '""\\"')
    if escaped.endswith('"'):
        escaped = escaped[:-1] + '\\"'
    return f'"""\n{escaped}\n"""'


def render_toml(name: str, meta: dict, body: str) -> str:
    out = [GENERATED_BANNER.format(name=name), f"name = {toml_basic(meta.get('name', name))}"]
    if "description" in meta:
        out.append(f"description = {toml_basic(meta['description'])}")
    if "model" in meta:
        out.append(f"model = {toml_basic(meta['model'])}")
    if "tools" in meta:
        tools = [t.strip() for t in meta["tools"].split(",") if t.strip()]
        out.append("tools = [" + ", ".join(toml_basic(t) for t in tools) + "]")
    out.append(f"developer_instructions = {toml_multiline(body.strip())}")
    return "\n".join(out) + "\n"


def sync_codex_agents(root: Path, rep: Report, apply: bool) -> None:
    src_dir = root / ".claude" / "agents"
    dst_dir = root / ".codex" / "agents"

    if not src_dir.is_dir():
        rep.ok("no .claude/agents/ — nothing to convert")
        return

    sources = sorted(p for p in src_dir.glob("*.md") if p.is_file())
    if apply:
        dst_dir.mkdir(parents=True, exist_ok=True)

    expected = set()
    for src in sources:
        expected.add(src.stem + ".toml")
        dst = dst_dir / (src.stem + ".toml")
        meta, body = parse_agent(src)
        rendered = render_toml(src.stem, meta, body)

        if dst.is_file() and dst.read_text(encoding="utf-8") == rendered:
            rep.ok(f".codex/agents/{dst.name} up to date")
            continue
        if apply:
            dst.write_text(rendered, encoding="utf-8")
            rep.action(f"regenerated .codex/agents/{dst.name}")
        else:
            rep.problem(f".codex/agents/{dst.name} is stale or missing (source: .claude/agents/{src.name})")

    if dst_dir.is_dir():
        for orphan in sorted(dst_dir.glob("*.toml")):
            if orphan.name in expected:
                continue
            if apply:
                orphan.unlink()
                rep.action(f"removed orphaned .codex/agents/{orphan.name}")
            else:
                rep.problem(f".codex/agents/{orphan.name} has no source in .claude/agents/")


# ── 4. skills: one real directory, everything else a link ─────────────────────


def _same_tree(a: Path, b: Path) -> bool:
    """True when two skill directories are byte-identical."""
    files_a = sorted(p.relative_to(a) for p in a.rglob("*") if p.is_file())
    files_b = sorted(p.relative_to(b) for p in b.rglob("*") if p.is_file())
    if files_a != files_b:
        return False
    return all((a / rel).read_bytes() == (b / rel).read_bytes() for rel in files_a)


def sync_skills(root: Path, cfg: dict, rep: Report, apply: bool, migrate: bool) -> None:
    print(_c("1", "\nSkills"))
    canonical = root / ".agents" / "skills"
    claude = root / ".claude" / "skills"

    claude_real = claude.is_dir() and not claude.is_symlink()
    canonical_real = canonical.is_dir() and not canonical.is_symlink()

    if claude_real and canonical_real:
        c_names = {p.name for p in claude.iterdir() if p.is_dir()}
        a_names = {p.name for p in canonical.iterdir() if p.is_dir()}
        if migrate:
            conflicts = [n for n in sorted(c_names & a_names) if not _same_tree(claude / n, canonical / n)]
            if conflicts:
                rep.problem(
                    "these skills exist in BOTH trees with different content, and migrating would "
                    f"destroy one copy: {conflicts}. Diff them, keep the correct version in "
                    ".agents/skills/, delete the other, then re-run --migrate. "
                    "(Nothing was moved.)"
                )
                return
            for name in sorted(c_names - a_names):
                shutil.move(str(claude / name), str(canonical / name))
                rep.action(f"moved {name} -> .agents/skills/")
            for name in sorted(c_names & a_names):
                shutil.rmtree(claude / name)
                rep.action(f"removed .claude/skills/{name} (byte-identical to the .agents/ copy)")
            if not any(claude.iterdir()):
                claude.rmdir()
                claude.symlink_to("../.agents/skills")
                rep.action("linked .claude/skills -> ../.agents/skills")
            else:
                rep.problem(".claude/skills still has entries after migration; resolve by hand")
        else:
            rep.problem(
                f"both .claude/skills ({len(c_names)}) and .agents/skills ({len(a_names)}) are real "
                f"directories — this is exactly how mirrors drift. "
                f"Only in .claude: {sorted(c_names - a_names) or 'none'}. "
                f"Only in .agents: {sorted(a_names - c_names) or 'none'}. "
                "Reconcile the differing copies, then re-run with --migrate."
            )
        return

    if claude_real and not canonical.exists():
        if migrate:
            canonical.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(claude), str(canonical))
            claude.symlink_to("../.agents/skills")
            rep.action("moved .claude/skills -> .agents/skills and linked it back")
        else:
            rep.note(".claude/skills is the only real directory. Run --migrate to make .agents/skills canonical.")
        return

    if not canonical.exists():
        rep.ok("no skills directory yet")
        return

    sync_symlink(root, ".claude/skills", "../.agents/skills", rep, apply)
    if cfg["codex"]:
        sync_symlink(root, ".codex/skills", "../.agents/skills", rep, apply)


# ── main ──────────────────────────────────────────────────────────────────────


def main() -> int:
    parser = argparse.ArgumentParser(description="Keep every agent harness reading the same bytes.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="create links and regenerate mirrors")
    mode.add_argument("--check", action="store_true", help="assert only; exit 1 on drift (CI / pre-push)")
    mode.add_argument("--migrate", action="store_true", help="one-time: move .claude/skills -> .agents/skills")
    parser.add_argument("--root", type=Path, default=None, help="repo root (default: nearest .git or AGENTS.md)")
    args = parser.parse_args()

    root = (args.root or find_root(Path.cwd())).resolve()
    cfg = load_config(root)
    rep = Report()
    apply = args.apply or args.migrate

    print(_c("1", f"sync-agent-files  ·  {root}"))

    check_canonical(root, cfg, rep, apply)

    print(_c("1", "\nRead-only mirrors"))
    if cfg["copilot"]:
        sync_symlink(root, ".github/copilot-instructions.md", "../AGENTS.md", rep, apply)
    if cfg["gemini"]:
        sync_symlink(root, "GEMINI.md", "AGENTS.md", rep, apply)
    check_never_mirrored(root, rep)

    sync_skills(root, cfg, rep, apply, args.migrate)

    if cfg["codex"]:
        print(_c("1", "\nGenerated: .codex/agents"))
        sync_codex_agents(root, rep, apply)

    print()
    if rep.problems:
        verb = "remain" if apply else "found"
        print(_c("31", f"{len(rep.problems)} problem(s) {verb}."))
        if not apply:
            print("Run with --apply to fix what is fixable automatically.")
        return 1
    if rep.notes and args.check:
        print(_c("33", f"{len(rep.notes)} warning(s); nothing blocking."))
        return 0
    print(_c("32", "In sync."))
    return 0


if __name__ == "__main__":
    sys.exit(main())
