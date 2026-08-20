<!--
Sync Impact Report
- Version change: unratified generated scaffold -> 1.0.0
- Modified principles: template principle 1 -> I. Contract Before Implementation;
  template principle 2 -> II. Server Authority and Player Privacy;
  template principle 3 -> III. Simple, Replaceable Boundaries;
  template principle 4 -> IV. Practical Proof of Behavior;
  template principle 5 -> V. Accessible, Secure, Observable Operation
- Added principles: VI. Independent Artifacts and Current Evidence;
  VII. Local Production-Like Readiness; VIII. Proportionate Process
- Added sections: Scope and Technical Baseline; Development Workflow and Quality Gates
- Removed sections: none
- Follow-up TODOs: synchronize the separately maintained AGENTS.md with this newly
  adopted constitution after this scoped constitution update
-->

# Battleship Rewrite Constitution

## Core Principles

### I. Contract Before Implementation

The versioned language-neutral contract is the boundary between independently
delivered products. `contracts/` owns HTTP resources, event schemas, examples,
problem details, security semantics, and compatibility policy. Backend and
frontend work MUST consume that contract; neither may redefine it or create a
second game engine. Cross-boundary behavior MUST be accepted in the contract
before either consumer relies on it. A change to a public request, response,
event, error, header, cookie, or security rule MUST update the versioned
contract, examples, compatibility record, and proving tests before dependent
implementation is accepted.

**EARS rule — Contract change:** When a public wire behavior changes, the
project MUST update and validate the canonical contract before accepting
backend or frontend changes that depend on it.

### II. Server Authority and Player Privacy

The backend owns game rules, command validation, authorization, state
transitions, lifecycle decisions, statistics, and player-safe projections. The
browser is a contract client and MUST NOT infer hidden placement, turn,
winner, legality, expiry, or command success. Anonymous authorization MUST use
high-entropy capability handling with server-side digest storage; a public game
locator or client-supplied seat, role, or player identifier MUST never confer
authority. Each response and event MUST be projected for the authenticated
seat. Capability values, invitation digests, hidden boards, internal command
history, and personal data MUST not appear in URLs, logs, metrics, errors,
shareable representations, or another player's view. The one-time invitation
secret is permitted only in the canonical URL fragment and its documented
explicit-redemption flow.

**EARS rule — Sensitive projection:** When the server returns a snapshot, event,
problem, or command result, it MUST omit information that the authorized seat
is not permitted to know.

### III. Simple, Replaceable Boundaries

The design MUST use the smallest structure that makes responsibilities,
dependencies, and tests clear. Domain rules, application orchestration,
transport/security adapters, realtime delivery, and browser rendering MUST
remain independently understandable and replaceable. The backend's domain
layer MUST remain free of framework, transport, persistence, clock, and
security concerns; application code MUST expose genuine ports; adapters MUST
not decide domain rules. The frontend MUST keep transport behind one gateway,
validate data before it enters server-state storage, and use local state only
for drafts and presentation. New layers, patterns, or abstractions require a
named boundary or repeated behavior that they simplify and MUST NOT duplicate
an existing responsibility.

**EARS rule — New abstraction:** When an abstraction is added, its change
description MUST name the responsibility or boundary it isolates and the
existing responsibility it replaces or composes.

### IV. Practical Proof of Behavior

Implementation MUST follow an evidence-first loop: acceptance behavior and
important failure, race, security, or privacy cases are identified before
coding, a focused proof is made to fail when the change is testable, and the
implementation is then kept small and refactored against the proof. Pure
domain tests MUST prove rules and invariants; contract tests MUST prove the
published wire boundary; application and adapter tests MUST prove concurrency,
security, lifecycle, and projection boundaries; browser tests MUST exercise
real user outcomes where integration matters. Tests MUST favor observable
behavior over private implementation details. Fixed coverage percentages,
pixel-perfect screenshot matching, test-count quotas, and tests that merely
mirror private methods are not completion criteria.

**EARS rule — Behavioral proof:** When observable behavior changes, the owning
feature MUST add or update a focused automated proof for the accepted behavior
and its most important failure or privacy case.

### V. Accessible, Secure, Observable Operation

Every user-facing flow MUST remain operable by keyboard, touch, pointer, and
assistive technology where the device supports them. Controls MUST have
semantic names, visible focus, usable dialogs, text or symbol alternatives to
colour and emoji, responsive layouts, and understandable status and error
feedback. English and Ukrainian user-visible resources MUST retain key parity.
Optional sound and haptic feedback are best-effort side effects and MUST never
block rendering, commands, navigation, or results. Server boundaries MUST
validate input and enforce the published cookie, CSRF, exact-Origin, Fetch
Metadata, CORS, rate-limit, and cache policies. Health and diagnostics MUST be
useful without exposing secrets or game data. Logs MUST be structured and
redacted, with enough stable context to diagnose failures without recording
capabilities, invitation fragments, cookies, full boards, raw command bodies,
or unnecessary personal information.

**EARS rule — Operational event:** When a command is rejected, a game expires,
state is lost on restart, or realtime delivery fails, the system MUST provide
a safe user outcome and a non-sensitive diagnostic record.

### VI. Independent Artifacts and Current Evidence

Specifications, plans, tasks, contracts, generated outputs, source code, tests,
configuration, and operating documentation have distinct purposes and MUST
remain synchronized with the behavior they describe. Generated contract code
MUST be changed only by its generation path. Public behavior, architectural
decisions, constraints, and verified limitations MUST be documented in the
same work item that changes them. A checked task or an old report is not proof:
completion claims MUST cite fresh results from the applicable commands and
must distinguish passed, skipped, failed, and environment-blocked checks.

**EARS rule — Evidence update:** When public behavior, a contract, a durable
decision, or a verified limitation changes, the owning artifact and its current
evidence MUST be updated in the same work item.

### VII. Local Production-Like Readiness

The baseline is local development and local production-style verification, not
hosting, CI, or deployment. The resulting shape MUST nevertheless be ready for
minimal hosting changes: runtime configuration is explicit and validated,
frontend and backend artifacts remain separate, development proxy addresses
are not baked into static output, the public application path is preserved,
security defaults are deployment-aware, resources are bounded, and health,
logging, shutdown, and troubleshooting are documented. The backend is
intentionally ephemeral and in-memory. The accepted lifecycle baseline is a
15-minute idle lifetime, a two-hour absolute lifetime, five-minute terminal
retention, and explicit restart loss; the UI and tests MUST describe these
limits rather than implying persistence.

The root integration feature owns the single authoritative `scripts/verify.sh`
gate. Before that feature exists, each feature MUST use only the focused build,
test, lint, and local-run commands named by its approved plan. No command may
declare a skipped, timed-out, or capability-blocked stage green.

**EARS rule — Local release check:** When a feature is declared complete, it
MUST pass its accepted local checks and demonstrate the applicable packaged or
production-style local behavior; the root gate is required once integration
has created it.

### VIII. Proportionate Process

The project MUST use the shortest workflow that leaves clear acceptance
criteria, a viable dependency-ordered plan, and trustworthy evidence. A new
required artifact, role, ceremony, or check MUST identify the concrete risk it
addresses and how its result will be used. Genuine ambiguity MUST be resolved
before implementation; where approved artifacts are silent on behavior that
matters, work MUST pause for a decision with a recommended default. Unrelated
worktree changes MUST be preserved. The protected `rewrite_prod_ready` branch
MUST not receive direct development commits; feature work uses focused branches
and reviewable commits, with final integration remaining an authorized
developer action.

**EARS rule — Process addition:** When a mandatory process step is proposed,
the proposal MUST state its protected boundary, failure risk, owner, and
acceptance evidence before the step becomes required.

## Scope and Technical Baseline

The rewrite is one monorepo with three independently buildable products and
one root integration layer:

- `contracts/` is the authoritative OpenAPI, JSON (or Yaml) Schema, event, example, and
  compatibility product.
- `backend/` is a Java/Spring Boot Maven reactor with domain, application, and
  application-adapter modules, producing one executable backend-only JAR.
- `frontend/` is an independent TypeScript/React/Vite static application that
  consumes the contract through a single gateway and does not ship a local
  rules engine or frontend assets inside the backend JAR.
- `scripts/`, local configuration examples, integration tests, and local
  operation documentation own cross-product orchestration and verification;
  they are not a fourth application product or a root npm workspace.

The primary implementation languages are Java and TypeScript. The approved
baseline is Java 25 LTS with Maven 3.9.16 Wrapper and Spring Boot 4.1.0, plus
Node 24.19.0 LTS/npm with the approved React, Vite, TypeScript, contract,
testing, accessibility, and browser tooling versions recorded by the owning
feature plan and lockfile. Dependencies MUST use exact versions in the owning
lockfile and MUST pass compatibility checks before acceptance. A later version
or framework substitution requires evidence and an approved plan change; it
MUST NOT be introduced as an incidental upgrade.

The launch baseline excludes a database, durable session store, cache or
broker as authority, accounts, chat, spectators, rankings, payments,
analytics, advertisements, offline gameplay or command queues, horizontal
scaling, multiple replicas, CI workflows, deployment manifests, hosting
automation, and provider-specific infrastructure. Excluding those capabilities
does not permit insecure local shortcuts: local configuration MUST model the
same origin, public-path, credential, redaction, and resource-boundary
assumptions needed for a later hosting choice.

## Development Workflow and Quality Gates

The approved feature order is `001-api-contract`, `002-backend`,
`003-frontend`, and `004-integration`. Each feature MUST have its own
`specs/<feature>/spec.md`, `plan.md`, and `tasks.md`; the plan MUST be complete
before feature code is written. The normal loop is:

`orient -> specify -> clarify -> checklist? -> plan -> tasks -> analyze ->
handoff/guard -> implement -> converge -> cleanup? -> verify -> close`

The prerequisite checker is workflow evidence, not product health. Before a
registered feature is built, the active feature and required artifacts MUST be
confirmed with the repository's prerequisite command. The plan MUST name the
affected product's exact build, test, lint, and local-run commands. Commands,
tests, or architecture from historical `master` work MUST not be borrowed as
rewrite evidence.

The authority order is: this constitution for project governance; the active
feature's approved specification, plan, tasks, and contract artifacts for
feature decisions; and current source, configuration, tests, workflows, and
generated output for delivered behavior and verification. Historical planning
inputs and the former application are context, not compatibility promises. A
cross-artifact conflict MUST be resolved before implementation or explicitly
recorded as an approved decision.

Every closeout MUST report the real worktree state, completed tasks, commands
run, evidence obtained, honest limitations, and the next applicable command or
decision. Focused sub-agents MAY be used for substantive inspection,
implementation, debugging, research, verification, and review when the
execution environment provides them; the main agent or responsible developer
retains final judgment about conformance and the next step.

## Governance

This constitution is the project's governing document. A feature specification
or agent instruction MAY add detail within its scope but MUST NOT silently
weaken a constitutional rule. When the constitution and an instruction
conflict, work MUST stop at the conflict and the governing change MUST be
resolved through the amendment process.

`AGENTS.md` is the canonical repository-wide operational instruction file. It
MUST reference this constitution and remain synchronized when durable workflow,
architecture, commands, security, verification, or branch policy changes.
`AGENTS.md` MAY define agent mechanics and concise operational traps, but it
MUST NOT override this document. Any change to either file MUST be reviewed for
consistency with the other and with the active Spec Kit workflow.

Amendments require a written change, the evidence or decision motivating it,
an impact review against active specifications, plans, tasks, templates, and
`AGENTS.md`, and approval by the project owner before integration. The change
MUST update the Sync Impact Report at the top of this file. Versioning follows
semantic governance rules: MAJOR for incompatible principle removal or
redefinition, MINOR for a new principle or materially expanded mandatory
section, and PATCH for wording, clarification, or non-semantic correction.

Each feature review, analysis, implementation closeout, and integration review
MUST check the applicable principles, boundary ownership, current evidence,
redaction, and documented limitations. A temporary exception MUST record its
scope, reason, risk, owner, expiry or removal condition, and compensating proof;
an undocumented exception is not accepted. Compliance review does not replace
behavioral tests or the local verification commands.

**Version**: 1.0.0 | **Ratified**: 2026-08-20 | **Last Amended**: 2026-08-20
