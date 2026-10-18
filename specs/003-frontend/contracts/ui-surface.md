# UI surface and local commands

What this feature exposes to people and to other tooling. The HTTP API is `contracts/openapi.yaml`, not this file.

## Routes (R06, R38)

| Path | Screen(s) | Reads on load |
|---|---|---|
| `/` | Home | `battleship.currentGameId` → offers Return to your game (R07) |
| `/create` | Create game | `getMeta`, `listRulesets` |
| `/join/:gameId` (`#invite=` stripped before render) | Join invitation | `getMeta`; nothing redeemed until Join game (R09) |
| `/games/:gameId` | Waiting room, Fleet placement, Gameplay, Results, Session unavailable — chosen by `snapshot.phase` or the outcome table | `getMeta`, `listRulesets`, `getGame`, then the event stream |
| anything else | Home | — |

`/join/{gameId}#invite={secret}` is the backend's invitation link format (`{public-base-url}/join/…`), so
`public-base-url` must equal the frontend origin (R47). Dialogs (rules, settings, QR, confirmations, ship action,
checking, error) have no route (R37).

## Surfaces and widgets (R38, R58–R61)

Widgets follow the mockup; data comes only from the listed source (derived values: [data-model.md](../data-model.md)
§ *Derived for display only*). Every surface has the header: brand, translated ruleset name on game surfaces,
Refresh (`getGame`, game surfaces only), Settings.

| Surface | Mockup widgets | Source |
|---|---|---|
| Home | title; New game, Return to your game, Game rules, Settings | `battleship.currentGameId` (R07) |
| Create game | ruleset radio cards (field, fleet, ship contact, turn, revealed water); captain name (1–32, contract `DisplayName`); Back, View details (rules dialog), Create game | `listRulesets`; name limits from the contract |
| Join invitation | captain name; Join game; invitation-unavailable state | route `gameId`; in-memory secret (R08–R10) |
| Waiting room | stepper Created/Waiting/Placement/Battle; connection pill; invitation link field showing `invitationUrl` with everything after `#invite=` replaced by `••••••••` (the full link is never in the DOM; Copy link and QR read it from the snapshot); Copy link, Show QR; invitation lifetime countdown and meter; status notice; Replace invitation; Leave game; "Game created" toast | `phase`, `invitationUrl`, `invitationExpiresAt`, `allowedActions`, connection |
| Fleet placement | fleet tray by ship length with counts; fleet-progress tile; opponent status pill; your board; ruleset touching rule as text; Random, Clear, Ready (confirmation); ship-action dialog (ship chips, Horizontal/Vertical, Rotate/Remove, Cancel/Apply) | `yourBoard`, `opponent.ready`, ruleset, `allowedActions` |
| Gameplay | turn pill; two player cards (name, ships left, ship cells left); Enemy waters / Your fleet tabs (narrow); both boards with coordinate labels, hit ×, miss •, sunk, revealed water; footer with connection state and "Session expires in" | `turn`, `you`, `opponent`, both boards, `lastShot`, `expiresAt` |
| Results (`FINISHED`) | Victory/Defeat hero with reason; your fleet-condition tiles intact/damaged/sunk; stat cards Match time, Turns & accuracy, Turn timing (fields below); both revealed boards; result-availability note; Create another game, Leave results | `outcome`, `statistics`, both boards, `expiresAt` |
| Results (`ABANDONED`) | "Game abandoned" message; no boards, no statistics; Leave results (US5.2) | `phase` |
| Session unavailable | status code badge; expired or no-longer-available message (R27); Create new game, Return home | outcome table (plan) |
| Dialogs and overlays | rules (rulesets side by side), settings (theme dark/light/system, emoji, language, sound, haptics, Resign match when allowed), QR with privacy warning, confirmations (ready, replace invitation, leave, resign), checking, error; toast region (success, info, warning, error) | as their surface |

Results stat cards (an absent optional value shows "—"):

| Card | Rows → field |
|---|---|
| Fleet tiles | `statistics.you.fleet.intact` / `.damaged` / `.sunk` |
| Match time | total `match.totalDurationMs`; placement phase `match.placementDurationMs`; your / opponent placement `you`/`opponent.placementDurationMs`; gameplay `match.gameplayDurationMs` |
| Turns & accuracy | your / opponent turns `you`/`opponent.turns.count`; your hits / shots `you.hits` / `you.shots`; your accuracy `you.accuracy` |
| Turn timing | your / opponent total `you`/`opponent.turns.totalMs`; your / opponent average `…turns.averageMs`; your fastest / slowest `you.turns.fastestMs` / `.slowestMs` |

Not reproduced: see spec R60.

## Browser storage

Only `battleship.preferences` and `battleship.currentGameId` in `localStorage` ([data-model.md](../data-model.md)).
Service-worker cache: static build assets only.

## Root commands

All from the repository root; Node 24 and Java 25 (major checked); no global tools.

| Command | Does | Ports | Exit |
|---|---|---|---|
| `./scripts/install.sh` | runtime majors; `npm ci` in `contracts/`, `frontend/`; Playwright Chromium; `backend/mvnw -q validate` | — | non-zero on any failure, naming the step |
| `./scripts/dev.sh` | build JAR if absent, start it, start `vite` | backend 8080, UI `http://localhost:5173` | runs until Ctrl-C; stops only its children |
| `./scripts/local-prod.sh` | package JAR, build `frontend/dist`, start JAR with `BATTLESHIP_PUBLICBASEURL=http://localhost:4173`, start `vite preview` | backend 8080, UI `http://localhost:4173` | as above |
| `./scripts/verify.sh` | `install.sh` → contract check → backend gate → frontend check + build → journeys vs packaged JAR | journey stacks: UI 4173–4176, backend 8080–8083 | 0 only if every stage ran and passed |

Overrides: any `BATTLESHIP_*` backend key from the environment (example values in `config/local.env.example`);
`BACKEND_PORT` (passed to the JAR as `SERVER_PORT`), `FRONTEND_PORT` (read by `stack.mjs` and `vite.config.ts`). Start-up failures print component, address and a next step (R48). Output never
contains cookies, invitation links, boards or request bodies (R51).
