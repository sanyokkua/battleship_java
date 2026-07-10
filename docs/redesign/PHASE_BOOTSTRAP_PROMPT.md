# Phase Bootstrap Prompt (plan-mode)

**How to use:** copy the block below into a new session, change **only** the `PHASE` line to the phase you want to run (e.g. `PHASE = "Phase 4 — Frontend: Vite + dependency install + configuration"`), and send it. Claude will operate in **plan mode**: it investigates, maps the work to the redesign docs, and produces a plan + sub-tickets + testing/verification strategy. It does **not** write code in this step — implementation is dispatched to sub-agents after you approve the plan.

The main session is an **orchestrator only**: it plans, splits work into sub-tickets when needed, dispatches sub-agents to implement each ticket, then verifies acceptance criteria. Each execution unit runs in its own sub-agent.

---

```text
ROLE: You are the orchestrator for one phase of the Battleship redesign. Operate in PLAN MODE.
Do NOT modify code or write files yet. Produce a plan I can approve; implementation will be done by sub-agents you dispatch after approval.

PHASE = "<<< PUT THE PHASE NAME HERE — e.g. Phase 5 — Frontend: build the app (Adapter + Widgets + screens) >>>"

AUTHORITATIVE DOCS (read these first, in full):
- docs/redesign/SPECIFICATION.md        (what changes / what must not; API is frozen; UI/UX rules; acceptance)
- docs/redesign/IMPLEMENTATION_PLAN.md   (find PHASE; read its objective, tasks, sub-tickets, references, acceptance)
- docs/redesign/MOCKUP.html              (canonical visuals — open/inspect the relevant screens)
- docs/redesign/TESTING_PLAN.md          (the tests this phase owns and must make green)

HARD INVARIANTS (never violate):
- Do NOT change the backend REST API surface (paths, verbs, request/response DTOs, status codes) — SPEC §6.
- Do NOT change game logic, EXCEPT a Phase-2 bug fix, which must be documented + regression-tested.
- Keep the app building at the phase boundary. Frontend widgets must use the GameAdapter, never call the network directly.

DO THE FOLLOWING, IN ORDER, THEN STOP AND PRESENT THE PLAN:

1) INVESTIGATE CURRENT CODE
   - Explore the repo relevant to PHASE (backend pom/sources, or frontend src, or docker, etc.).
   - Summarize the current state and anything that conflicts with the target in the docs.

2) READ THE TASK
   - Locate PHASE in IMPLEMENTATION_PLAN.md. Restate its objective, tasks/sub-tickets, deliverables, and acceptance criteria in your own words.

3) MAP FILES & DOC SECTIONS
   - List every file you expect to create/modify/delete.
   - For each, cite the exact SPECIFICATION.md section(s) and/or MOCKUP.html screen(s)/widget(s) that govern it.
   - Note the API endpoints (SPEC §6) or DTO fields involved, if any.

4) ASSESS SIZE — SPLIT IF BIG
   - If the phase is large or touches many areas, break it into ordered, independently-implementable sub-tickets (PHASE-<n>.<m>), each with: goal, files, doc references, test obligations, acceptance criteria, and dependencies on other tickets.
   - If the phase is small, produce a single ticket. (Phases marked [SPLIT] in the plan MUST be split.)

5) PLAN THE CHANGES
   - For each ticket, describe the concrete implementation approach (interfaces, components, config, versions to pin) consistent with the docs.

6) PLAN THE TESTING
   - For each ticket, list the tests to add/run per TESTING_PLAN.md (unit / web / component / mocked-e2e / live-e2e / container as applicable) and the coverage target.

7) PLAN VERIFICATION & ACCEPTANCE
   - State how you will prove the phase's acceptance criteria (build commands, test suites, API-contract oracle re-run, mockup visual match, Docker/Podman run, etc.).
   - Include the exact commands to run.

8) PLAN THE ORCHESTRATION
   - Specify which sub-agent runs which ticket, the execution order, parallel-vs-sequential, and how results roll back up to you (the orchestrator) for verification.
   - Define the rollback/stop condition if a gate fails.

OUTPUT FORMAT:
- A) Current-state summary
- B) Phase restatement (objective + acceptance)
- C) File/doc mapping table
- D) Ticket breakdown (or single ticket) with dependencies
- E) Test plan per ticket
- F) Verification steps + commands
- G) Sub-agent dispatch plan
Then STOP and wait for my approval before any implementation.
```

---

## Orchestration protocol (for the main session, after plan approval)
1. **Dispatch:** for each ticket, launch a sub-agent with a focused prompt containing: the ticket goal, its file list, the governing SPEC/MOCKUP references, its test obligations, and its acceptance criteria. Independent tickets are launched in parallel; dependent tickets wait.
2. **Constrain:** every sub-agent inherits the HARD INVARIANTS above.
3. **Collect & verify:** when a sub-agent reports done, the orchestrator runs the ticket's verification commands and checks acceptance criteria. Only then mark the ticket complete.
4. **Integrate:** ensure the app still builds after each ticket; resolve cross-ticket conflicts.
5. **Gate:** when all tickets are done, run the phase-level verification (TESTING_PLAN gate + SPEC acceptance). Record outcomes and any bug fixes/deviations in the IMPLEMENTATION_PLAN.md changelog.
6. **Proceed:** advance to the next phase only after the gate is green.

## Tips
- Keep sub-agent scope tight (one ticket = one concern) for clean parallelism and easy review.
- Prefer running the API-contract oracle (TESTING_PLAN §2.3) after any backend-touching phase.
- For frontend phases, verify against `MockGameAdapter` first (fast), then the live server (Phase 7) — don't block UI work on a running backend.
