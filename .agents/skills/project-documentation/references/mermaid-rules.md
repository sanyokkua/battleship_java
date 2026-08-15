# Mermaid Diagram Rules (self-contained)

This skill embeds diagrams (architecture, data-flow, Entity-Relationship) directly — **do not assume a
separate Mermaid skill is installed.** Everything needed to produce render-ready Mermaid is here. (These
rules mirror the standalone `mermaid` skill; that skill is optional and only adds deeper, specialized
diagram support.)

> **Parse is not render.** A diagram can look syntactically fine and still fail to render with
> *"Syntax error in text"* — most often class diagrams. Prefer the simplest construct that conveys the
> meaning, and where possible verify the **rendered** output, not just the source.

## Diagram type — pick one, spell the keyword EXACTLY (case-sensitive)

`flowchart TD` (flow/pipeline/decision) · `sequenceDiagram` (interactions/message order) ·
`erDiagram` (data model) · `classDiagram` (types) · `stateDiagram-v2` (lifecycle, **v2 not v1**) ·
`C4Context` / `C4Container` / `C4Component` (architecture zoom levels; needs Mermaid 9.2+) ·
`gantt` · `pie` · `mindmap` · `gitGraph` (**camelCase — `gitgraph` fails**). A wrong-case keyword fails
with *"No diagram type detected"*. For service docs, default to `flowchart` (architecture/data-flow) and
`erDiagram` (data contracts).

## Size budget

Keep a diagram under ~25–30 nodes. If a system needs more, split into several focused diagrams
(per subsystem / per flow) — large single diagrams are unreadable and mis-render more often.

## Render-reliability rules (the common failure causes)

- **Node IDs:** letters/digits/underscore only, MUST start with a letter; no spaces/hyphens/dots/colons; unique; descriptive (`authService`, not `n1`).
- **Reserved words are never bare IDs:** `end`, `class`, `subgraph`, `graph`, `flowchart`, `default`, `style`, `linkStyle`, `click`, `classDef`, `direction`, `participant`, `actor`, `title`, `section`. **The `end` gotcha:** a bare `end` closes a `subgraph` — use `endNode`/`endState`; quote a literal label `["end"]`.
- **Quote labels** with spaces/punctuation: `A["User Service"]`, `C{"Is Valid?"}`, `D[("Database")]`.
- **HTML-entity-encode** chars that collide with syntax inside labels: `"`→`#34;` `(`→`#40;` `)`→`#41;` `[`→`#91;` `]`→`#93;` `{`→`#123;` `}`→`#125;` `<`→`#60;` `>`→`#62;` `|`→`#124;` `\`→`#92;` `#`→`#35;`; `%%` inside a label → `#37;#37;`.
- **Label length** under ~50 chars; wrap with `<br/>` (never literal `\n`).
- **Flowcharts** declare a direction on line 1: `TD`/`LR`/`BT`/`RL`.
- **Match every block-opener with `end`:** `subgraph`/`end`; sequence `alt`/`opt`/`loop`/`par`/`critical`/`break`/`rect` each need `end`.
- **Edge labels** use pipe or dashed form: `A -->|"Yes"| B` or `A -- "label" --> B`.
- **Comments** only on their own line (never inline); no trailing semicolons.

## Class diagrams — the most fragile (be conservative)

- Generics use `~T~` (`List~String~`), never `<T>` — **but** since these diagrams are embedded in
  Markdown, avoid the generic entirely (the `~…~` is turned into strikethrough by GitHub-Flavored
  Markdown and corrupts the source); use a plain type name.
- The abstract/static classifier (`method()*` / `method()$`) is usually unnecessary — the
  `<<abstract>>`/`<<interface>>` stereotype already conveys it; omit the per-method `*`.
- Avoid two-sided cardinality combined with a relationship label unless you can render-verify; a plain
  labelled association (`A --> B : has`) is safe.

## Markdown-embedding hazards (because diagrams live inside `.md` files here)

Some renderers run fenced content through Markdown before Mermaid, corrupting the source. Keep the
diagram body free of: `~text~` (→ strikethrough), a *pair* of `*` (→ emphasis), `_text_` (→ emphasis),
`[text]` outside quotes (→ link). Stereotypes `<<abstract>>` are fine; bare `<x>` is not.

## Before writing the diagram into a doc

Self-validate: IDs valid/unique/not reserved; labels quoted + encoded; flowchart direction declared;
every block-opener has `end`; arrows valid; no inline comments / trailing semicolons; exact type
keyword; class diagrams kept simple; no Markdown-fragile tokens. Output a fenced ```mermaid block under
a descriptive heading with a one-line caption.
