# UI surface and local commands

What this feature exposes to people and to other tooling. The HTTP API is `contracts/openapi.yaml`, not this file.

## Routes (R06, R38)

| Path | Screen(s) | Reads on load |
|---|---|---|
| `/` | Home | `battleship.currentGameId` → offers Return to your game (R07) |
| `/create` | Create game | `getMeta`, `listRulesets` |
| `/join/:gameId` (`#invite=` stripped before render) | Join invitation | `getMeta`; nothing redeemed until Join game (R09) |
| `/games/:gameId` | Waiting room, Fleet placement, Gameplay, Results, Session unavailable — chosen by `snapshot.phase` or the outcome table | `getGame`, then the event stream |
| anything else | Home | — |

`/join/{gameId}#invite={secret}` is the backend's invitation link format (`{public-base-url}/join/…`), so
`public-base-url` must equal the frontend origin (R47). Dialogs (rules, settings, QR, confirmations, ship action,
checking, error) have no route (R37).

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
`BACKEND_PORT`, `FRONTEND_PORT` (read by `stack.mjs` and `vite.config.ts`). Start-up failures print component, address and a next step (R48). Output never
contains cookies, invitation links, boards or request bodies (R51).
