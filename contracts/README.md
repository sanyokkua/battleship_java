# Battleship API contract

`openapi.yaml` is the whole contract between the Battleship backend and any user interface. It is one
OpenAPI 3.1 file: operations, schemas and real examples. Nothing else in this folder defines behaviour.

The idea behind it: **the backend is the model, the client is a view.** Every player action goes to the
server; every successful game operation answers with the same caller-relative `GameSnapshot`; the
client draws that snapshot and enables exactly the actions in `allowedActions`. A client holds no game
state and no game rules, so it can be replaced without touching the backend.

## Commands

```bash
cd contracts
npm ci
npm run check   # lint (every embedded example is validated against its schema) + TypeScript types
npm run docs    # optional: static HTML reference in dist/index.html
```

Node 24 or newer. Tool versions are pinned in `package-lock.json`. `dist/` and `.tmp/` are ignored.
Any other stack can consume `openapi.yaml` directly with its own generator.

## A game, phase by phase

| Phase | What happens | Typical `allowedActions` |
|---|---|---|
| `WAITING` | Host created the game and shares `invitationUrl`. | `NEW_INVITATION`, `SEND_PRESENCE`, `LEAVE` |
| `PLACEMENT` | Guest joined. Each player arranges the fleet privately, then `READY`. | `PLACE_SHIP`, `REMOVE_SHIP`, `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `READY` (fleet complete), `SEND_PRESENCE`, `LEAVE` |
| `PLAYING` | Both ready. `turn` says who fires. | `FIRE` (your turn), `RESIGN`, `SEND_PRESENCE` |
| `FINISHED` | Fleet destroyed or resignation. Both boards, `outcome`, `statistics`. | `LEAVE` |
| `ABANDONED` | Someone left before play. Nothing is revealed. | `LEAVE` |

The table is orientation only. The server decides; clients read `allowedActions`.

## Client rules

1. **Order by `version`.** Show a snapshot only if its `version` is greater than the one on screen. This
   orders HTTP answers and stream events alike.
2. **One command at a time per game.** If an answer is lost, resend the identical body (same
   `commandId`); it is never applied twice.
3. **On `409` from a game you play, re-read** `GET /api/v1/games/{gameId}` and show it. `401`, `404` and
   `410` on a game mean it is gone for this browser.
4. **Stream:** `closed` event means call `EventSource.close()` and do not reconnect (`REPLACED`:
   another tab took over; `GAME_UNAVAILABLE`: read the game once to learn why). Any other end
   reconnects automatically and starts with the current snapshot; if `EventSource` ends `CLOSED` (a
   reconnect was refused), read the game and, if it is still there, open a new stream.
5. **Presence:** while `SEND_PRESENCE` is allowed and the tab is visible, call presence every
   `limits.presenceIntervalSeconds`. Never from a hidden tab.
6. **Invitation page:** read `#invite=…` from the URL, remove the fragment from the address bar, keep the
   secret in memory only, and join only when the visitor confirms. Never rebuild or store the link.
7. **Storage:** a browser may remember `gameId` values and display preferences. Snapshots, secrets and
   pending commands are never stored.
8. **Text:** `displayName` is untrusted plain text. Problem `code` is localized by the client; `title`
   and `detail` are for developers.

## Privacy and security

- The opponent's placement is never sent, except hit and sunk cells, water the ruleset reveals around a
  sunk ship, and the full board once the game is `FINISHED`. `ABANDONED` and expired games reveal
  nothing. A browser that is not a player of a game gets `404`.
- Identity is the `HttpOnly` cookie `__Host-battleship_session`; `gameId` grants nothing.
- Every `POST` echoes the `XSRF-TOKEN` cookie in `X-XSRF-TOKEN`.
- The UI and the API share one origin (bundled, reverse proxy, or a dev-server proxy), so there is no
  CORS. The session cookie needs a secure context: use HTTPS or `localhost`.
- The server keeps everything in memory. A restart ends all games and sessions; clients then get `401` or
  `404` and treat both as gone.

## Versioning

`info.version` follows semantic versioning. Inside `/api/v1`: operations, optional fields and headers,
command types and values of the lists `openapi.yaml` marks open may be added; clients handle unknowns
as its § Compatibility says; requests reject unknown fields. Everything else ships as `/api/v2`.
Compare against the previous git tag when changing the file.

## Deferred on purpose

Claiming a win against a vanished opponent, rematch, recovering a join whose answer was lost before the
cookie arrived, conditional reads (ETag), salvo rules. All can be added without breaking v1.

## Supersedes planning inputs

The documents under `docs/rewrite_context_doc/Planning/` were written before this contract was
finalized. Where they differ, `openapi.yaml` wins:

| Planning input said | Contract says |
|---|---|
| `commandId` + `expectedVersion` + `If-Match`, `428`/`412` | `commandId` only; `version` is for ordering, not a precondition |
| `gameVersion` and `snapshotRevision`, `serverEpoch`, ETag/`304`, `Last-Event-ID` replay | one `version`; every stream starts with the current snapshot |
| Seat-keyed data (`OWNER`/`GUEST`, `callerSeat`, `terminalBoards`) | caller-relative `you` / `opponent`; `opponentBoard` is revealed when `FINISHED` |
| Sparse board cells, ruleset embedded in each snapshot | dense `grid`, `rulesetId` |
| `MOVE_SHIP`, `ROTATE_SHIP` | `PLACE_SHIP` relocates atomically; plus `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET` |
| Versioned leave envelope with retained receipts, `200`/`204` | body-less `POST …/leave` → `204`; `404` afterwards means already left |
| `GET …/snapshot`, `POST …/invite/rotate`, `GET /actuator/health` | `GET /api/v1/games/{gameId}`, `POST …/invitation`, `GET /api/v1/health` |
| Invitation URL only in create/rotate responses, secret kept as digest | `invitationUrl` in the host's `WAITING` snapshot (secret held in server memory) |
| `BATTLESHIP-XSRF-TOKEN` / `X-Battleship-CSRF`, credentialed CORS | `XSRF-TOKEN` / `X-XSRF-TOKEN`, same origin, no CORS |
| Problem `conflictReason`, `recovery`, no `title` | flat `code` list, standard RFC 9457 `title`/`detail` for developers |
| No shot result, no unplaced ships, no opponent status | `lastShot`, whole fleet in `yourBoard.ships`, `connected`, `shipsRemaining` |
