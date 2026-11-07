# Quickstart: validate 003-frontend

Details: [plan.md](plan.md) § *Gate*, [contracts/ui-surface.md](contracts/ui-surface.md). Commands changed on 2026-11-07
(see the note in plan.md); the current ones are below.

## Prerequisites

Java 25 and Node 24 (any patch), macOS or Linux shell, free ports 8080 and 4173 (journeys also use 4174-4176, 4193-4201 and 8081-8083, 8193-8201; see `docs/local-operation.md`).

## Run

```bash
./scripts/local-build.sh     # gate: exact installs, contract, backend, frontend, journeys
./scripts/local-deploy.sh    # packaged app on the LAN (HTTPS); `localhost` for http://localhost:4173 only
```

Open exactly the URL `local-deploy.sh` prints (use `localhost`, never `127.0.0.1` for a local run - the backend refuses other origins).

Expected from `local-build.sh`: stages printed in order (runtimes, install, contract, backend, frontend, journeys), exit 0.

## Manual smoke (production-style run)

| Step | Expected |
|---|---|
| Browser A: Create game, pick each ruleset in turn | Ruleset facts shown before submit; waiting room with Copy link / Show QR |
| Browser B (separate profile or private window): open the link | Address bar shows `/join/<id>` with no `#invite`; nothing happens until Join game |
| Both place fleets (dialog, keyboard, drag, random) and Ready | Boards change only after the server answers; refusals worded |
| Play to the end | Sea Battle: hit keeps the turn, sunk ship's surroundings shown as revealed water; Classic: turn passes |
| Results | Outcome, both boards, server turn and shot-decision statistics (including absent values after a zero-shot resignation); Leave results → Home |
| Stop and restart the backend mid-game | "No longer available — … server restarted" screen with New game |
| Go offline after one load, reopen | Shell opens, says a connection is needed, actions disabled |
| Compare each screen with the mockup at phone portrait, phone landscape and laptop sizes, dark and light | Same widgets and styling as the mockup (spec R58–R60); narrow shows board tabs, landscape two compact boards |
| With touch emulation, place a fleet (tap and drag) and fire | Every action works by touch; nothing needs hover |

## Proof map

Automated evidence for each requirement: [plan.md](plan.md) § *Validation*.
