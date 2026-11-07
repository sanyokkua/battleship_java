## Context

Single OpenAPI 3.1 document that is the whole contract between the backend and any client. Source: the original workflow feature
`the original 001-api-contract artifacts` (finalized 2026-09-20; first plan history in git up to `d33b670`). Where any older document
disagrees with `contracts/openapi.yaml`, the OpenAPI file wins.

## Goals / Non-Goals

**Goals:** one consistent contract, easy to implement in any stack, no duplication, backend does the processing.
**Non-Goals:** accounts, spectators, chat, matchmaking, bots, rankings, durable history, databases, multiple replicas,
recovery after restart, salvo rules, rematch, claiming a win against a vanished opponent, conditional reads, recovery of a
join whose answer was lost before the cookie arrived. Also no application code, generated client package, CI, deployment or runtime in `contracts/`.

## Decisions

- **Assurance level: Lean.** One canonical artifact, standard lint, one guide. Proof from standard tooling only: the linter
  validates every embedded example against its schema. No oracle files, no wording-asserting tests, no symbolic fixtures, no
  release baseline. Escalating beyond Lean is an owner decision.
- **Layout.**
  ```text
  contracts/
  ├── openapi.yaml    # operations, schemas, examples
  ├── README.md       # phases, client rules, privacy, versioning, superseded inputs
  ├── package.json    # pinned @redocly/cli, openapi-typescript, typescript
  ├── package-lock.json
  ├── redocly.yaml    # recommended rules + example validation as errors
  ├── .npmrc
  └── .gitignore      # node_modules/, dist/, .tmp/
  ```
- **Gate** (from `contracts/`): `npm ci` installs pinned tools; `npm run check` = `redocly lint` (structure, references, every
  example matches its schema) + `openapi-typescript` generation into `.tmp/`; `npm run docs` builds optional HTML in `dist/`.
- **Wire decisions** and reasons live in `contracts/openapi.yaml` (`info.description`) and `contracts/README.md`, not duplicated here.
- **Forecast:** 7 files in `contracts/`, 3 in `the original 001-api-contract artifacts/`; 13 tasks (Phases 15-16).
- **Complexity tracking:** nothing to justify; two lint rules are relaxed, each with its reason in `redocly.yaml`.

## Risks / Trade-offs

- Prose edits and some values (secret length, example versions) cannot fail the gate; they were checked by independent re-review.
- The `EventStream` example holds a one-line copy of `SnapshotPlayingAfterHit` inside a string the linter cannot validate; update by hand when that snapshot example changes.
- Java code generation from the contract is not part of this gate; feature 002 proves it.
- the original workflow prerequisite check needs `SPECIFY_FEATURE_DIRECTORY=the original 001-api-contract artifacts` because `the former feature pointer` is machine-local.
- Next unit: squash-merge `feature/001-api-contract` into `rewrite_prod_ready` (owner-approved, postponed), then specify feature 002 (backend).
