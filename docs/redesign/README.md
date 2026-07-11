# Battleship — Redesign & Modernization (docs)

This folder is the **single source of truth** for the Battleship redesign + modernization effort. Read the documents in this order.

| # | File | What it is |
|---|---|---|
| 1 | [`SPECIFICATION.md`](./SPECIFICATION.md) | **Mini-spec** — scope, what changes / what must not, target architecture, dependency update/install/remove lists, the frozen backend API, the Adapter + Widgets frontend design, the full UI/UX spec (design tokens, cell states, per-screen rules, adaptivity, accessibility), packaging targets, acceptance criteria. |
| 2 | [`MOCKUP.html`](./MOCKUP.html) | **Canonical visual reference** (open in a browser). All screens + the **Feedback** catalog (toasts/dialog/validation); use the screen chips, the 📱/🖥️ device toggle, and the **EN / УКР** language toggle. Shows game-mode cards, the no-go moat around ships, and ship removal. Visuals here win over prose. |
| 3 | [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | **11 ordered phases**, each with objective, spec/mockup references, tasks (and sub-tickets for large phases), deliverables, per-phase testing/verification, and acceptance criteria. |
| 4 | [`TESTING_PLAN.md`](./TESTING_PLAN.md) | **All test types** (backend unit/web/contract, frontend unit/component, mocked & live Playwright e2e, container smoke, a11y, cross-browser), coverage targets, phase ownership, CI wiring. |
| 5 | [`PHASE_BOOTSTRAP_PROMPT.md`](./PHASE_BOOTSTRAP_PROMPT.md) | **Reusable plan-mode prompt** — change only the phase name to start a phase. Claude investigates, maps files to the docs, splits big phases into sub-tickets, and plans changes + testing + verification. Implementation runs via sub-agents; the main session orchestrates. |

## The 11 phases (execution order)
1. Backend → Java 25 + Spring Boot 4.1 + dependency upgrades (no API/logic change).
2. Backend → close test gaps, fix any bugs found. **[split into sub-tickets]**
3. Frontend → remove outdated code & deprecated dependencies.
4. Frontend → install dependencies, set up Vite + configuration.
5. Frontend → build the app: **Adapter** (all backend calls) + **Widgets** (consume the adapter). **[split]**
6. Frontend → automated unit + component/UI testing against a **mock adapter**.
7. Live e2e → automated single game against a running server (Playwright).
8. Final verification & validation (full stack, API-contract oracle).
9. Docker/Podman packaging (multi-stage, best practices, compose).
10. Container verification — runs & is playable under **both Docker and Podman**.
11. Documentation finalization & branch wrap-up.

## Ground rules (apply to every phase)
- **Backend API: no breaking changes** — the only permitted change is the additive `ExceptionDto.errorCode` for i18n (SPEC §6, §8.8.4).
- **Game logic is frozen** — the only exception is a Phase-2 bug fix, which must be documented and regression-tested.
- **Frontend widgets never call the network directly** — they go through the `GameAdapter` port (SPEC §7).
- **No hard-coded user-facing strings** — everything is localized (English + Ukrainian, SPEC §8.8).
- **Visuals** follow `MOCKUP.html`; **data/behavior** follow `SPECIFICATION.md` + the backend API.
- The app must **build at every phase boundary**; each phase must pass its `TESTING_PLAN.md` gate before the next begins.

## What v3 added (2026-07-11)
Aligned the docs to the current codebase (`docs/index.md`, `docs/architecture.md`) and addressed review gaps: explicit **game-mode (edition) selection** (both editions have 10 ships — Ukrainian sizes 1–4, Milton Bradley 2–5); the **no-go moat** around placed ships (rendered from `CellDto.isAvailable`) and **obvious two-way ship removal** (tap board + tray ✕); a full **notifications / dialogs / inline-validation** system (SPEC §8.7); and **English + Ukrainian localization** with a runtime language switch (SPEC §8.8), enabled by one small additive backend field (`errorCode`).

## Confirmed decisions
Vite (replaces deprecated CRA) · custom CSS design system (drops the Bootstrap stack) · function components + hooks · Adapter/port for backend access · **react-i18next (en/uk)** · Vitest + RTL + Playwright · `eclipse-temurin:25-jre` runtime. Rationale and version pins: SPEC §2.3 and §11.
