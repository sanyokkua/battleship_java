---
description: Bootstrap a redesign phase using the plan-mode orchestrator prompt in docs/redesign/PHASE_BOOTSTRAP_PROMPT.md
argument-hint: <phase-number>
---

Phase argument: $1

1. Read `docs/redesign/IMPLEMENTATION_PLAN.md`. Find the heading matching `## Phase $1 — ...` and extract its full title text — that is the PHASE value.
   - If `$1` is empty, not a number, or no matching phase heading exists (valid range: 1–11), STOP: list the 11 phases from `docs/redesign/README.md` ("The 11 phases (execution order)") and ask the user which one to bootstrap. Do not guess.
2. Read `docs/redesign/PHASE_BOOTSTRAP_PROMPT.md` in full.
3. If not already in plan mode, enter plan mode now (this mirrors the source prompt's "Operate in PLAN MODE. Do NOT modify code or write files yet.").
4. Follow the ROLE/prompt block from PHASE_BOOTSTRAP_PROMPT.md exactly, with `PHASE` set to the title extracted in step 1. That means, in order:
   - Read the AUTHORITATIVE DOCS it lists in full (SPECIFICATION.md, IMPLEMENTATION_PLAN.md, MOCKUP.html, TESTING_PLAN.md).
   - Respect the HARD INVARIANTS it lists (frozen backend API except the additive `ExceptionDto.errorCode`; frozen game logic except a documented, regression-tested Phase-2 bug fix; app must build at phase boundaries; widgets never call the network directly; no hard-coded user-facing strings).
   - Do steps 1–8 from that prompt (investigate current code; read the task; map files & doc sections; assess size and split into sub-tickets if large; plan the changes; plan the testing; plan verification & acceptance; plan the orchestration).
   - Produce the OUTPUT FORMAT (A–G) it specifies.
5. Stop and wait for the user's approval before dispatching any implementation sub-agents.
