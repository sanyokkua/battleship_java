# Tasks: Battleship API Contract

**Status**: complete (2026-09-20) · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

## Phases 1–14 (T001–T109) — superseded

The first implementation delivered a multi-file contract plus a custom validation product across 14
phases, six of them convergence rounds. The full task list and its evidence are in git history
(`specs/001-api-contract/tasks.md` at commit `d33b670`).

Final audit before replacement (2026-09-20): all 109 checked tasks had their named deliverables (260
paths verified, 0 missing), `contracts/` was committed, both checklists were fully checked. The audit
also showed why the loop never ended: every rule existed in three copies (OpenAPI, a 548-line oracle,
guide prose) bound together by tests, 20 fixtures were symbolic rather than real responses, and the
proof machinery (~8,000 lines) was three times the size of the contract. Reading the wire shapes
directly found defects the harness had not: no way to obtain a `shipId` for an unplaced ship, no shot
result on the wire, browser-forbidden headers declared as parameters, `const` version and lifecycle
values, and spurious version conflicts during simultaneous placement.

The owner decided to replace that product with one OpenAPI file (spec R01). T001–T109 are therefore
closed as **superseded**, not re-verified.

- [x] TD001 Per-row fixture conformance over the problem catalogue — **closed, obsolete**: the
  catalogue, fixtures and per-code named responses no longer exist; each status has one response whose
  embedded examples are schema-validated by the linter.
- [x] TD002 Manifest `operationId`/`status` binding — **closed, obsolete**: there is no manifest;
  examples live on the operation and response they belong to.

## Phase 15: Finalization (assurance level Lean)

- [x] F01 Write `contracts/openapi.yaml`: single file, 11 operations, caller-relative `GameSnapshot`,
  one response per status, two security schemes (R01–R05, R12–R24).
- [x] F02 Embed real, schema-valid examples: meta, rulesets, waiting, placement start / in progress,
  playing after hit / after sunk with revealed water / classic rules, finished by victory / by
  resignation, abandoned, event stream, one problem per status (R25).
- [x] F03 Lean gate: `package.json` (`lint`, `types`, `docs`, `check`), `redocly.yaml` with example
  validation as errors (R25).
- [x] F04 Delete `contracts/{openapi/, schemas/, examples/, validation/, baselines/, guides/, docs/,
  compatibility-policy.md, changelog.md}` (R01, R05).
- [x] F05 `contracts/README.md`: phases, client rules, privacy, versioning, deferred items, table of
  superseded planning inputs.
- [x] F06 Lean `spec.md`, `plan.md`, `tasks.md`; delete `research.md`, `data-model.md`, `quickstart.md`,
  `contracts/`, `checklists/`.
- [x] F07 Banner in `docs/rewrite_context_doc/Planning/{00-decisions-and-evidence.md, 03-api, 04-backend,
  05-frontend, 06-integration}` pointing at the authoritative contract; constitution 1.1.0 (Principle II
  allows the unused invitation secret in process memory).
- [x] F08 `AGENTS.md` contract section and root `README.md` rewritten for the new layout and gate.
- [x] F09 One bounded independent review (unusable flow, placement leak, example/description mismatch).

## Phase 16: Review fixes (2026-09-20)

An independent API review (two passes) found no Critical or High issues, 2 Medium and 6 Low, fixed below.

- [x] F10 Compatibility policy: open lists and client fallback; § Errors scoped to listed operations
  with the health exception (`openapi.yaml` § Errors, § Compatibility; README § Versioning; spec R24).
- [x] F11 Event stream: refused reconnect, `EventSource.close()` on `closed`, `lastShot` repeat rule
  (`openapi.yaml` `streamGameEvents`, `Shot`; README rule 4).
- [x] F12 `leaveGame` lists `410`; examples corrected: `you.connected` false in create/join bodies,
  43-character invitation secret, versions 2 and 8 keep the sequence possible, opponent placement
  65 s agrees with the placement example (`openapi.yaml`).
- [x] F13 Prose fixes: `409` rule scoped to games you play (client rule, `getGame`, `Conflict`),
  `expiresAt` counts from creation, rate limits stated as defaults (`openapi.yaml`; README rule 3).

## Evidence (2026-09-20, Node v24.21.0)

**Gate.** `cd contracts && npm ci && npm run check` → exit 0: "Your API description is valid", 0
warnings; `openapi-typescript` generated `.tmp/api.d.ts`. `npm run docs` built `dist/index.html`.

**The gate can fail** — each mutation applied to a temporary copy, linted, then discarded:

| Mutation | Linter result |
|---|---|
| Example cell state `FOG` | `must be equal to one of the allowed values "UNKNOWN", "WATER", …` |
| `expiresAt` removed from the waiting example | `must have required property 'expiresAt'` |
| `$ref` to `#/components/schemas/Bord` | `Can't resolve $ref` |
| Request example `{ type: READY, winner: YOU }` | `command property must NOT have additional properties winner` |

**Independent review (F09, one round).** No placement leak found: every non-`FINISHED` example shows
only `UNKNOWN`/`MISS`/`HIT`/`SUNK`/`REVEALED_WATER` and only sunk ships; `ABANDONED` shows nothing.
Both rulesets were walked from create to leave, including reload, lost answer, second tab, stream
closed, expiry, replaced invitation and leave before play. Statistics arithmetic, `shipsRemaining`,
`sunkShipId` resolution and revealed-water cells were checked and hold. Fixed from the review: create
and join never answer `401` (they issue a new session) and `401` on a game means gone; example
placement durations, a `ready` flag and example timestamps made consistent; five ambiguous sentences
tightened (`GAME_UNAVAILABLE`, fleet-editing actions during placement, `422` vs
`placement-out-of-bounds`, only `commandId` is compared, which neighbours become `REVEALED_WATER`).

**Previous mistakes avoided** (`ARCHITECTURE-SMELLS-REPORT.md`): credentials never in URLs (cookie
session; `gameId` is a locator) · wire schemas are standalone, no engine objects · seven distinct cell
states instead of one shot flag · board and fleet come from `GET /rulesets` and the snapshot ·
monotonic `version` orders every snapshot · `PLACE_SHIP` relocates atomically (no remove-then-add) ·
`commandId` makes a retried shot safe · full status map instead of all-400 · problem documents carry no
exception text · one `code` list in one place · each SSE event is one complete snapshot · no second
game engine in the client (`PLACE_FLEET_RANDOMLY` is a server command, `allowedActions` drives the UI).

**Phase 16 gate.** With F10–F13 in place: `cd contracts && npm ci && npm run check` (Node v24.21.0) →
exit 0: "Woohoo! Your API description is valid.", 0 warnings; `openapi-typescript` 7.13.0 generated
`.tmp/api.d.ts`. `npm run docs` → exit 0, built `dist/index.html`.

**The mechanically checkable Phase 16 edits are guarded** — each mutation applied to a scratch copy
outside the repository, linted with the pinned `redocly` and `redocly.yaml`, exit 1 each. The prose
edits (F10, F11, F13), the secret's length and the example versions cannot fail the gate; they were
checked by an independent re-review instead.

| Mutation | Linter result |
|---|---|
| `leaveGame` `'410'` `$ref` to `#/components/responses/Gon` | `Can't resolve $ref` (1 error) |
| `SnapshotWaiting` `you.connected: "no"` | `` `connected` property type must be boolean `` (4 errors) |
| `SnapshotWaiting` `invitationUrl: 42` | `` `invitationUrl` property type must be string `` (4 errors) |

## Known limitations

- The `EventStream` example carries a one-line copy of `SnapshotPlayingAfterHit` inside a string, which the
  linter cannot validate; when that snapshot example changes, update the copy by hand.
- `bash .specify/scripts/bash/check-prerequisites.sh` needs `SPECIFY_FEATURE_DIRECTORY=specs/001-api-contract`
  because `.specify/feature.json` is machine-local and absent in this checkout; with it, the check passes.
- Java code generation from the contract is not part of this gate; feature 002 proves it.

## Next unit

Squash-merge `feature/001-api-contract` into `rewrite_prod_ready` (owner-approved step, postponed by the
owner), then specify feature 002 (backend) against `contracts/openapi.yaml`.
