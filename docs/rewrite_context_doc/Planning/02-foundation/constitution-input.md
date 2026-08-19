# Constitution input for the rewrite

## Purpose and consuming command

Use this as the input to the Foundation-phase SpecKit constitution command,
after `specify init` has created the core baseline. The command must create or
update the generated constitution at `.specify/memory/constitution.md`. It must
write simple, durable principles that guide later specifications and plans;
it must not introduce application code, a backlog, or a copy of the old
project's rules.

## Constitution content to adopt

### Contract before implementation

The API contract is the shared boundary between independently delivered backend
and frontend work. A behavior that crosses that boundary must be specified and
accepted in the contract before either side relies on it. Contract changes must
state compatibility, errors, privacy, and how both sides prove the change.

**EARS rule — Contract change:** When a public request, response, event, or
error changes, the project shall update the versioned contract and its examples
before backend or frontend implementation is accepted.

### Server authority and player privacy

The backend owns game rules, command validation, authorization, state
transitions, and player-safe projections. The browser is a client of the
contract, not a second game engine. A player receives only information that the
rules permit that player to know. Anonymous access must use capability handling
that does not expose a player identity or secret in URLs, logs, errors, or
another player's view.

**EARS rule — Sensitive projection:** When the server returns a game snapshot
or event, it shall project it for the authorized player and shall omit hidden
opponent information.

### Simple boundaries

Choose the smallest design that correctly expresses the contract and game
rules. Apply KISS and DRY first. Use SOLID or GRASP only when they make a
concrete responsibility, dependency, or test boundary clearer; do not add
layers, patterns, or abstractions for their own sake. Domain rules, application
orchestration, transport, and browser rendering must remain independently
understandable and replaceable.

**EARS rule — New abstraction:** When adding an abstraction, the change shall
name the repeated behavior or boundary it simplifies and shall not duplicate an
existing responsibility.

### Practical proof of behavior

Tests prove observable behavior and critical boundaries. Prefer deterministic
rule tests, focused contract tests, and browser flows that exercise real user
outcomes. Test names and fixtures should make the rule or risk clear. Avoid
test-style quotas, coverage quotas, and tests that merely mirror implementation
details.

**EARS rule — Behavioral proof:** When a feature adds or changes observable
behavior, it shall include a focused automated proof of the acceptance behavior
and its important failure or privacy case.

### Accessible, secure, observable operation

The browser must support keyboard operation, visible focus, clear status and
error feedback, and semantics appropriate to game controls. Inputs and state
transitions must be validated at the server boundary. Logs must support
diagnosis without recording capability secrets, hidden board data, or personal
information. Failures returned to users must be safe and actionable.

**EARS rule — Operational event:** When the system rejects a command, expires a
game, loses in-memory state after restart, or cannot keep a realtime connection,
it shall provide a safe user outcome and log enough non-sensitive context to
diagnose the event.

### Independent artifacts and current evidence

Specifications, plans, tasks, contracts, tests, docs, and generated artifacts
have distinct purposes and stay current with the behavior they describe. A
completed item is supported by current evidence, not by a checked box or an old
document. Documentation records public behavior, decisions, constraints, and
known operational limits in plain language.

**EARS rule — Evidence update:** When public behavior, a contract, a decision,
or a verified limitation changes, the related artifact shall be updated in the
same work item.

### Local production-like readiness

The rewrite must be runnable and testable locally in a form that resembles the
intended single-process production package. Keep runtime configuration explicit,
bound in-memory resources, and make restart and expiration behavior honest.
No CI, deployment system, database, accounts, chat, spectators, offline
commands, or multi-instance support is part of this baseline.

**EARS rule — Local release check:** When a feature is declared complete, it
shall pass its accepted local checks and demonstrate the applicable packaged
behavior. After the integration feature creates `scripts/verify.sh`, that root
command shall be the final completion gate.

### Proportionate process

Use the shortest process that leaves clear acceptance criteria, a viable plan,
and evidence. Do not add ceremonies, roles, metrics, or documents that do not
help make a decision, protect a boundary, or prove behavior. Resolve a genuine
ambiguity before building it; otherwise advance through the normal workflow.

**EARS rule — Process addition:** When proposing a new required artifact or
check, the proposal shall state the concrete risk it addresses and how the
result will be used.

## Verification of the generated constitution

Confirm that `.specify/memory/constitution.md` is self-contained, uses the
principles above in plain language, and contains named EARS-style rules rather
than generic numeric requirement labels. Confirm it neither imports old
application-specific rules nor commits to excluded capabilities. Record the
command used and review the generated diff before committing Foundation work.
