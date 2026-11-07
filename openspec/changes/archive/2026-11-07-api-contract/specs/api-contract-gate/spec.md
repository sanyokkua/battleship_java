## Purpose

Defines the Lean proof that the contract is valid and that the gate can fail.

## ADDED Requirements

### Requirement: Lean contract gate (R25) [agent]
`cd contracts && npm ci && npm run check` SHALL lint the document, validate every embedded example against its schema and generate TypeScript types. Embedded examples SHALL cover every phase, both rulesets, both ways to finish, abandonment, the event stream and each problem status.

#### Scenario: Clean gate
- **WHEN** the gate runs on the committed contract
- **THEN** it exits 0 with no warnings and generates the types

### Requirement: Gate fails on breakage (S1)
The gate SHALL be green and SHALL fail when an example or a reference is broken, with mutation evidence recorded in `tasks.md`.

#### Scenario: Broken example
- **WHEN** an example cell state is changed to `FOG`
- **THEN** the linter fails

#### Scenario: Broken reference
- **WHEN** a `$ref` points to `#/components/schemas/Bord`
- **THEN** the linter fails with an unresolved reference

### Requirement: Previous design mistakes avoided (S4)
Every API-relevant item of the legacy architecture-smells report (`docs/intent.md`, Lessons carried forward) SHALL be avoided by construction, with the checklist in `tasks.md`.

#### Scenario: Checklist review
- **WHEN** the S4 checklist is reviewed
- **THEN** each item maps to a contract property, such as no credentials in URLs or monotonic `version`
