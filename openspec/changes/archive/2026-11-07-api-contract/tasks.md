## Phases 1-14 (T001-T109) - superseded

The first implementation delivered a multi-file contract plus a custom validation product across 14 phases, six of them
convergence rounds. Full list and evidence: git history (`the original 001-api-contract artifacts` at commit `d33b670`). A final audit
(2026-09-20) found all 109 checked tasks had their deliverables, but every rule existed in three copies (OpenAPI, a 548-line
oracle, guide prose), 20 fixtures were symbolic, and the proof machinery (~8,000 lines) was three times the contract. The owner
replaced it with one OpenAPI file (R01); T001-T109 are closed as **superseded**, not re-verified.

- [x] TD001 Per-row fixture conformance over the problem catalogue - closed, obsolete: the catalogue, fixtures and per-code named responses no longer exist; each status has one response whose embedded examples are schema-validated by the linter.
- [x] TD002 Manifest `operationId`/`status` binding - closed, obsolete: there is no manifest; examples live on the operation and response they belong to.

## Phase 15: Finalization (assurance level Lean)

- [x] F01 Write `contracts/openapi.yaml`: single file, 11 operations, caller-relative `GameSnapshot`, one response per status, two security schemes (R01-R05, R12-R24).
- [x] F02 Embed real, schema-valid examples: meta, rulesets, waiting, placement start / in progress, playing after hit / after sunk with revealed water / classic rules, finished by victory / by resignation, abandoned, event stream, one problem per status (R25).
- [x] F03 Lean gate: `package.json` (`lint`, `types`, `docs`, `check`), `redocly.yaml` with example validation as errors (R25).
- [x] F04 Delete `contracts/{openapi/, schemas/, examples/, validation/, baselines/, guides/, docs/, compatibility-policy.md, changelog.md}` (R01, R05).
- [x] F05 `contracts/README.md`: phases, client rules, privacy, versioning, deferred items, table of superseded planning inputs.
- [x] F06 Lean `spec.md`, `plan.md`, `tasks.md`; delete `research.md`, `data-model.md`, `quickstart.md`, `contracts/`, `checklists/`.
- [x] F07 Banner in the legacy planning documents (since removed) pointing at the authoritative contract; constitution 1.1.0 (Principle II allows the unused invitation secret in process memory).
- [x] F08 `AGENTS.md` contract section and root `README.md` rewritten for the new layout and gate.
- [x] F09 One bounded independent review (unusable flow, placement leak, example/description mismatch).

## Phase 16: Review fixes (2026-09-20)

An independent API review (two passes) found no Critical or High issues, 2 Medium and 6 Low, fixed below.

- [x] F10 Compatibility policy: open lists and client fallback; Errors scoped to listed operations with the health exception (`openapi.yaml` Errors, Compatibility; README Versioning; R24).
- [x] F11 Event stream: refused reconnect, `EventSource.close()` on `closed`, `lastShot` repeat rule (`openapi.yaml` `streamGameEvents`, `Shot`; README rule 4).
- [x] F12 `leaveGame` lists `410`; examples corrected: `you.connected` false in create/join bodies, 43-character invitation secret, versions 2 and 8 keep the sequence possible, opponent placement 65 s agrees with the placement example (`openapi.yaml`).
- [x] F13 Prose fixes: `409` rule scoped to games you play (client rule, `getGame`, `Conflict`), `expiresAt` counts from creation, rate limits stated as defaults (`openapi.yaml`; README rule 3).

## Evidence (2026-09-20, Node v24.21.0)

- Gate: `cd contracts && npm ci && npm run check` exit 0, 0 warnings, `.tmp/api.d.ts` generated; `npm run docs` built `dist/index.html`.
- Mutations that fail the gate (S1): example cell state `FOG`; `expiresAt` removed from the waiting example; `$ref` to `#/components/schemas/Bord`; request example `{ type: READY, winner: YOU }`.
- Phase 16 mutations (exit 1 each): `leaveGame` `'410'` `$ref` to `Gon`; `SnapshotWaiting` `you.connected: "no"`; `SnapshotWaiting` `invitationUrl: 42`.
- Independent review (F09): no placement leak (S3); both rulesets walked from create to leave (S2).
- S4 checklist (previous mistakes avoided): credentials never in URLs; standalone wire schemas; seven distinct cell states; board and fleet from `GET /rulesets` and snapshot; monotonic `version`; atomic `PLACE_SHIP` relocation; `commandId` makes retried shot safe; full status map; problem documents carry no exception text; one `code` list; each SSE event one complete snapshot; no second game engine in the client.
