# Feature Specification: Battleship API Contract

**Feature Branch**: `feature/001-api-contract`
**Created**: 2026-08-20 · **Finalized**: 2026-09-20
**Status**: Final — delivered as `contracts/openapi.yaml`

**Input**: "Develop one consistent API that frontend and backend use as a single contract. It should be
simple, understandable, easy to implement with any technology stack, avoid the previous design
mistakes, reflect all game stages and operations without duplication, and do the maximum of processing
on the backend: the UI only reflects the backend state."

This document replaces the first version of the specification (65 requirements, six convergence
rounds; see git history up to `d33b670`). The owner judged that version over-engineered and approved
the simplifications recorded in `contracts/README.md` § *Supersedes planning inputs*. Where any older
document disagrees with `contracts/openapi.yaml`, the OpenAPI file wins.

Provenance: `[user]` = stated by the owner; `[agent]` = proposed by the agent and approved by the owner
on 2026-09-20.

## User Stories

1. **Create and join (P1).** A host picks a ruleset and a name, creates a private game and shares one
   invitation link. A guest opens the link, confirms a name and joins. Opening the link alone claims
   nothing; only one guest can ever join.
2. **Place and play (P1).** Each player arranges a fleet privately (by hand or randomly), declares
   ready, then fires in turns until a fleet is destroyed or someone resigns. The server decides
   legality, turns, results and the winner.
3. **Resume and recover (P2).** After a reload, a lost answer, a dropped stream or a second tab, the
   player gets the current truth from the server and never has to guess whether an action happened.
4. **Finish and leave (P2).** Players see the result, both boards and server-calculated statistics,
   and can leave; leaving before play ends the game for both without revealing anything.
5. **Build against one contract (P3).** A backend or frontend developer in any stack can implement
   against `openapi.yaml` alone.

## Requirements

### Shape of the contract
- **R01** `[user]` The deliverable is one OpenAPI document, `contracts/openapi.yaml`, plus a short guide.
  No application code, no second source of truth, no custom validation framework.
- **R02** `[user]` The client is a view: every action goes to the server and every successful game
  operation returns the same `GameSnapshot`, which is also pushed over SSE.
- **R03** `[user]` The snapshot is caller-relative (`you` / `opponent`) and lists `allowedActions`; a
  client never derives permissions, turn, legality, winner, expiry or statistics.
- **R04** `[agent]` Eleven operations under `/api/v1`: meta, rulesets, health, create, read, join,
  commands, events, invitation, presence, leave.
- **R05** `[user]` Nothing is duplicated: one `version`, one board schema, one cell-state enum, one
  problem document, one response definition per HTTP status.

### Game
- **R06** `[user]` Two rulesets: `sea-battle-10-ship.v1` (10 ships, no touching, extra turn on hit,
  water revealed around a sunk ship) and `hasbro-classic-2002.v1` (5 ships, touching allowed, turn
  always passes). Rulesets are immutable; the API publishes board, fleet and these three rule flags.
- **R07** `[user]` Phases: `WAITING` → `PLACEMENT` → `PLAYING` → `FINISHED`, or `ABANDONED` when a
  player leaves before play.
- **R08** `[agent]` From `PLACEMENT` on, the player's own board lists the whole fleet with
  server-minted `shipId`s, so every ship can be addressed before it is placed.
- **R09** `[agent]` Commands: `PLACE_SHIP` (also an atomic move/rotate), `REMOVE_SHIP`,
  `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY` (irreversible, offered only for a complete fleet),
  `FIRE`, `RESIGN`.
- **R10** `[agent]` `lastShot`, `connected` and `shipsRemaining` are provided so the client needs no
  board diffing or counting. Boards are dense grids that can be drawn as they are.
- **R11** `[user]` `FINISHED` carries `outcome` and server-calculated `statistics` (match durations;
  per player placement time, shots, hits, accuracy, turn and shot-decision timing, fleet condition).

### Privacy and security
- **R12** `[user]` Opponent placement is never sent, except hit and sunk cells, ruleset-revealed water,
  and the full board once `FINISHED`. `ABANDONED` and expired games reveal nothing.
- **R13** `[user]` Identity is an anonymous `HttpOnly` session cookie; `gameId` only locates a game; no
  credential ever appears in a URL, body or script-readable storage. Non-players get `404`.
- **R14** `[user]` The invitation secret travels only in the link fragment and the join body. Every
  refused join gives the same `409 invitation-unavailable`.
- **R15** `[agent]` CSRF uses the de-facto standard `XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header. UI and
  API share one origin, so CORS is not part of the contract. Origin and Fetch-Metadata checks are
  server policy, not request parameters.
- **R16** `[agent]` Requests reject unknown fields, so a client cannot supply a seat, turn, winner or
  result.

### Reliability
- **R17** `[agent]` `version` grows on any change to either player's view and is used only to order
  snapshots; there are no version preconditions.
- **R18** `[user]` A command carries a client-generated `commandId`; resending an accepted one returns
  the current snapshot and is never applied twice.
- **R19** `[agent]` The event stream always starts with the current snapshot; a `closed` event tells
  the client not to reconnect (another tab, or the game is gone). There is no event replay.
- **R20** `[agent]` Leave has no body and is safe to repeat: before play it abandons the game, during
  play it is refused in favour of `RESIGN`, afterwards it drops the caller's access.
- **R21** `[user]` Games live in memory only: 15 minutes idle, 2 hours at most, results readable for
  5 minutes, invitations 15 minutes; visible-tab presence keeps a game alive. Values are published by
  `GET /api/v1/meta` and are not compile-time constants of the contract.

### Errors and evolution
- **R22** `[user]` Errors are RFC 9457 problem documents with one flat, stable `code` list and a status
  map that distinguishes 400/401/403/404/409/410/422/429/5xx. No exception text, secrets or board data.
- **R23** `[user]` The client localizes by `code`; server text is for developers only.
- **R24** `[agent]` Within `/api/v1` only operations, optional fields and headers, command types and
  values of the enums the contract marks open may be added; anything else is `/api/v2`.

### Proof (assurance level: Lean)
- **R25** `[agent]` `cd contracts && npm ci && npm run check` lints the document, validates every
  embedded example against its schema, and generates TypeScript types. Embedded examples cover every
  phase, both rulesets, both ways to finish, abandonment, the event stream and each problem status.

## Out of scope

Accounts, spectators, chat, matchmaking, bots, rankings, durable history, databases, multiple replicas,
recovery after a restart, salvo rules, rematch, claiming a win against a vanished opponent, conditional
reads, recovery of a join whose answer was lost before the cookie arrived.

## Success criteria

- **S1** The gate is green and fails when an example or a reference is broken (mutation evidence in
  `tasks.md`).
- **S2** Both rulesets can be played from create to result using only `openapi.yaml`.
- **S3** No example shows opponent placement outside the allowed cases.
- **S4** Every API-relevant item of `docs/rewrite_context_doc/Previous_Mistakes/ARCHITECTURE-SMELLS-REPORT.md`
  is avoided by construction (checklist in `tasks.md`).
