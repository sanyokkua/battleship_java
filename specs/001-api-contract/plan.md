# Implementation Plan: Battleship API Contract

**Branch**: `feature/001-api-contract` · **Spec**: [spec.md](spec.md) · **Finalized**: 2026-09-20

## Summary

Publish one OpenAPI 3.1 document that is the whole contract between the backend and any client. The
first plan (10 work packages, 109 tasks, a custom validation product) was replaced on 2026-09-20 by the
owner-approved finalization; its history is in git up to `d33b670`.

## Assurance level: Lean

One canonical artifact, standard lint, one guide. Proof comes from standard tooling only: the linter
validates every embedded example against its schema. No oracle files, no tests that assert wording, no
symbolic fixtures, no release baseline (the previous git tag is the baseline once a release exists).
Escalating beyond Lean is an owner decision.

## Layout

```text
contracts/
├── openapi.yaml        # the contract: operations, schemas, examples
├── README.md           # guide: phases, client rules, privacy, versioning, superseded inputs
├── package.json        # pinned @redocly/cli, openapi-typescript, typescript
├── package-lock.json
├── redocly.yaml        # recommended rules + example validation as errors
├── .npmrc
└── .gitignore          # node_modules/, dist/, .tmp/
```

No application code, generated client package, CI, deployment or runtime lives here.

## Gate

| Command (from `contracts/`) | Proves |
|---|---|
| `npm ci` | pinned tools install |
| `npm run check` | `redocly lint` (structure, references, every example matches its schema) and `openapi-typescript` generation into `.tmp/` |
| `npm run docs` | optional static HTML reference in `dist/` |

## Design decisions

All wire decisions and their reasons are in `contracts/openapi.yaml` (`info.description`) and
`contracts/README.md`. The ones that differ from the earlier planning inputs are listed in
`contracts/README.md` § *Supersedes planning inputs*.

## Forecast

Files: 7 in `contracts/`, 3 in `specs/001-api-contract/`. Tasks: 13 (Phases 15-16 in `tasks.md`).

## Complexity Tracking

Nothing to justify. Two lint rules are relaxed, each with its reason next to it in `redocly.yaml`.
