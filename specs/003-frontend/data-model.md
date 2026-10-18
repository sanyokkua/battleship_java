# Data Model: client-side state

The client stores no game model of its own. Wire shapes (`GameSnapshot`, `Meta`, `Ruleset`, `Problem`,
`StreamClosed`, `Command`) are the generated types from `contracts/openapi.yaml` and are not restated here.

## Game session (per open game, in memory)

| Field | Type | Source / rule |
|---|---|---|
| `gameId` | contract `GameId` | route `/games/:gameId` (R06) |
| `snapshot` | `GameSnapshot \| undefined` | last accepted snapshot; see *Version rule* |
| `clockOffsetMs` | number | `serverTime − Date.now()` at receipt; display only |
| `connection` | `connected \| reconnecting \| offline` | *Connection* below (R24) |
| `checking` | boolean | the pending action's answer was lost — see *Action slot* (R24) |
| `action` | `{ commandId: uuid, kind: Action, body } \| undefined` | single-flight slot (R19) |
| `notice` | `{ messageKey, retryAfterSeconds? } \| undefined` | last refusal, worded (R20, R22) |
| `lastAnnounced` | `{ by, target } \| undefined` | dedupes announcement/sound per `lastShot` (R31, R32) |

### Version rule (R05)

For a decoded snapshot `s` and current `c`:
- `c` absent or `s.version > c.version` → replace.
- `s.version == c.version` → keep `c`, take `s.expiresAt`, `s.serverTime`, `s.invitationUrl` and
  `s.invitationExpiresAt` (all excluded from `version`; the last two after contract fix 4, research D16).
- `s.version < c.version` → drop.

Applies to every source: command, presence, invitation, join, create, `getGame`, SSE `snapshot`.

### Action slot (R19, R21)

`idle → pending(commandId)`: user activates an allowed action; new `commandId = crypto.randomUUID()`.
`pending → idle`: that action's own HTTP answer (snapshot or problem). An SSE snapshot updates the board but
does not settle the slot. `pending → checking`: a network failure, or no answer within 10 s (the request is
aborted); the retry control resends the **same** body and `commandId`. Only one action per game; fire also marks its target cell
pending. Presence, invitation replacement and leave are not commands and carry no `commandId`; they still disable
their own control while pending.

### Connection (R24–R26)

| From | Event | To | Effect |
|---|---|---|---|
| — | open game screen | `reconnecting` | open `EventSource` |
| `reconnecting` | first `snapshot` event | `connected` | — |
| `connected` | `error`, `readyState` CONNECTING | `reconnecting` | browser retries itself |
| any but `offline` | `error`, `readyState` CLOSED; or tab visible | `reconnecting` | `getGame`; reopen if OK; on network error, 502 or unreadable answer retry after 1, 2, 4, then every 8 s |
| any | `offline` event (`navigator.onLine` false) | `offline` | close stream; game actions disabled (R43) |
| `offline` | `online` event | `reconnecting` | as the CLOSED row |
| any | `closed` `REPLACED` | terminal (other tab) | close; never reopen |
| any | `closed` `GAME_UNAVAILABLE` | terminal | close; one `getGame` → outcome table (plan) |

A network error while `navigator.onLine` is true never means `offline`; it keeps or puts the game in
`reconnecting`.

### Presence (R16)

Timer per open game, period `meta.limits.presenceIntervalSeconds`. Sends only while `document.visibilityState ==
'visible'` and `SEND_PRESENCE ∈ allowedActions`. Restarts (does not fire) on visibility return and reconnect.
Uses no action slot.

## Invitation secret (R08–R10)

Module variable in memory only: set in `main.tsx` from `#invite=` on `/join/:gameId`, fragment removed before
render; read once by the Join submit; cleared when the join settles (success or failure). Never in state that is
rendered, stored, logged or put in a URL.

## Persisted (localStorage, non-secret) (R07, R33)

| Key | Value | Cleared when |
|---|---|---|
| `battleship.preferences` | `{ locale: 'en'\|'uk', theme: 'dark'\|'light'\|'system', emoji, sound, haptics }` | never |
| `battleship.currentGameId` | public `gameId` of this browser's live game | server reports it gone (410/401/404), after leave or leave-results |

Nothing else is persisted: no snapshot, no invitation link, no cookie value. The service worker caches static
assets only (R42).

## Derived for display only (R01, R59)

| Value | From |
|---|---|
| Ruleset facts | `listRulesets` (R04) |
| Ship and ruleset names | i18n by id, raw id fallback (R35) |
| Countdowns (game, invitation) | `expiresAt` / `invitationExpiresAt − (Date.now() + clockOffsetMs)` |
| Invitation meter | remaining invitation time ÷ `meta.limits.invitationLifetimeSeconds` |
| Fleet tray | unplaced `yourBoard.ships` (empty `cells`) grouped by `length` |
| Fleet progress | placed ships ÷ `yourBoard.ships.length` |
| Ships left (player card) | `you`/`opponent.shipsRemaining` ÷ fleet total (Σ `ruleset.fleet[].count`) |
| Ship cells left (player card) | Σ `length × count` over `ruleset.fleet` − cells in `HIT` or `SUNK` on that player's board |
| Waiting-room stepper | `phase`: `WAITING` → Waiting, `PLACEMENT` → Placement, `PLAYING`/later → Battle |
| Result availability note | `expiresAt` of the finished snapshot |

None of these decides legality, turn, expiry or success (R01).
