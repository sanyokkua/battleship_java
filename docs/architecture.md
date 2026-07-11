# Architecture — battleship_java

This expands on [`docs/index.md` §2](index.md#2-architecture-overview) with the `GameStage` state
machine and two call-by-call sequence flows. It documents the **current** (pre-redesign) system on
branch `feature/redesign-v2`; the in-progress v2 plan lives separately in
[`docs/redesign/README.md`](redesign/README.md) and is not summarized here.

## Layered backend

The backend is a strict top-down layering with no back-references:

- **`web.controllers.rest`** — three `@RestController`s (`GameSessionCommonRestController`,
  `PreparationRestController`, `GameplayRestController`), all under `/api/v2/game`. Controllers do
  request/response DTO mapping only; they hold no business logic.
- **`logic.api`** — `GameControllerApi` / `GameControllerApiImpl` is the single boundary between
  web and engine. `ValidationUtils` performs all input validation here (blank checks, enum
  parsing, coordinate bounds), throwing one of 8 typed exceptions on failure. No Spring MVC type
  (`ResponseEntity`, `@RequestParam`, etc.) appears below this layer.
  `IdGenerator`/`IdGeneratorImpl` mints UUIDv4 session/player/ship IDs.
- **`logic.engine`** — `Game`/`GameImpl` owns the `GameStage` state machine and player
  orchestration; `FieldManagement`/`FieldManagementImpl` owns per-player board state (ship
  placement, shot resolution). Both are framework-agnostic — no Spring annotations. Ruleset
  differences are injected via `GameEditionConfiguration` (`UkrainianGameEditionConfiguration` /
  `MiltonBradleyGameEditionConfiguration`).
- **`logic.persistence`** — `Persistence`/`InMemoryPersistence` is the sole storage: a
  `HashMap<String sessionId, GameState>` with no database, no synchronization, and no eviction.
  Every mutating engine call is followed by a full `save()` of the resulting `GameState`.

## Current frontend structure

`frontend/src/` is a class-component CRA app (no hooks-based rewrite yet — that is v2 scope):

- **`ui/pages/`** — one component per route (7 pages, see the routing table in
  [`docs/index.md` §11.1](index.md#111-repository-layout)). Pages own all `setInterval` polling and
  navigation (`<Navigate>`).
- **`ui/elements/`** — presentational grids/forms (`PrepareField`, `GameplayField`, `ShipsList`,
  `NewGameForm`, `JoinGameForm`, etc.), receiving data and callbacks from their parent page.
- **`services/BackendRequestService.ts`** — the *only* place axios is used; 12 methods, one per
  backend endpoint, with `axios-retry` configured for 3 automatic retries.
- **`services/GameBrowserStorage.ts`** — 3 `localStorage` keys (`player_obj`, `session_str`,
  `gameStage_str`), read once on app mount to restore an in-progress game after a page reload; none
  are ever explicitly cleared.
- **`utils/GameUtils.ts`** / **`utils/StringUtils.ts`** — thin async wrappers and form validation
  (`isValidString`: length > 2).

---

## Diagram 1 — `GameStage` state machine

Five states, four transitions. (Ship-removal resetting a player's `ready` flag does **not** change
`GameStage` — see the note in [`docs/index.md` §6.2](index.md#62-state-transitions) — so it is
omitted from this diagram to keep it a pure stage-transition view.)

```mermaid
stateDiagram-v2
    [*] --> INITIALIZED
    INITIALIZED --> WAITING_FOR_PLAYERS: first player created
    WAITING_FOR_PLAYERS --> PREPARATION: second player created
    PREPARATION --> IN_GAME: both players ready
    IN_GAME --> FINISHED: a player has 0 ships left
    FINISHED --> [*]
```

---

## Diagram 2 — Session setup (creation through entering PREPARATION)

Covers `HomePage` → `NewGamePage`/`JoinGamePage` → session/player creation →
`WaitForPlayersPage` polling → both browsers landing in `PREPARATION`.

```mermaid
sequenceDiagram
    participant p1 as "Player 1 Browser"
    participant api as "REST API"
    participant p2 as "Player 2 Browser"

    p1->>api: POST /sessions #40;edition#41;
    api-->>p1: sessionId #40;201#41;
    p1->>api: POST /sessions/#123;id#125;/players #40;name#41;
    api-->>p1: playerId1 #40;201#41;
    p1->>p1: Navigate to WaitForPlayersPage

    loop every 3s until opponent joins
        p1->>api: GET .../players/#123;p1#125;/opponent
        api-->>p1: opponent not yet joined
    end

    p2->>api: POST /sessions/#123;id#125;/players #40;name#41;
    api-->>p2: playerId2 #40;201#41;
    p2->>p2: Navigate directly to PreparationPage

    p1->>api: GET .../players/#123;p1#125;/opponent
    api-->>p1: opponent.playerName populated
    p1->>p1: stop polling, wait 3s, then navigate to PreparationPage
```

Note the asymmetry: player 1 waits on `WaitForPlayersPage` and polls every 3 seconds; player 2
(the joiner) skips straight to `PreparationPage` since by definition the session already has both
players once they join.

---

## Diagram 3 — Gameplay loop (ship placement/ready through a finished game)

Covers `PreparationPage` ship placement/ready, the transition into `GameplayPage`, a shot and its
result, the 5-second `changesTime` poll, and the transition to `FinishPage` on a winner.

```mermaid
sequenceDiagram
    participant act as "Active Player Browser"
    participant api as "REST API"
    participant opp as "Opponent Browser"

    act->>api: PUT .../ships/#123;shipId#125; #40;place each ship#41;
    act->>api: POST .../players/#123;act#125;/start
    api-->>act: ready#61;true, first-ready becomes active
    opp->>api: POST .../players/#123;opp#125;/start
    api-->>opp: ready#61;true
    Note over api: PREPARATION to IN_GAME #40;both ready#41;
    act->>act: Navigate to GameplayPage

    act->>api: POST .../field/shot #40;row, col#41;
    api-->>act: shotResult#58; MISS

    loop opponent polls every 5s while waiting for their turn
        opp->>api: GET .../changesTime
        api-->>opp: lastId unchanged
    end
    opp->>api: GET .../changesTime
    api-->>opp: lastId changed
    opp->>api: GET .../players/#123;opp#125;/state
    api-->>opp: isPlayerActive#58; true

    opp->>api: POST .../field/shot #40;row, col#41;
    api-->>opp: shotResult#58; DESTROYED #40;opponent#39;s last ship#41;
    api-->>opp: hasWinner#58; true
    opp->>opp: Navigate to FinishPage
    act->>api: GET .../players/#123;act#125;/state
    api-->>act: hasWinner#58; true, winnerPlayerName
    act->>act: Navigate to FinishPage
```

`GameplayPage`'s poll is suspended whenever the local player scores a `HIT`/`DESTROYED` (they keep
shooting immediately, no poll needed) and resumes on a `MISS` or once it becomes the opponent's
turn — see `frontend/src/ui/pages/GameplayPage.tsx`.

---

## Game edition comparison

Both editions use a 10×10 board and exactly 10 ships; only the ship-size distribution (and
therefore total occupied cells) differs.

| Ship Type | Size | Ukrainian — Count | Milton Bradley — Count |
|---|---|---|---|
| PATROL_BOAT | 1 | 4 | — |
| SUBMARINE | 2 | 3 | 4 |
| DESTROYER | 3 | 2 | 3 |
| BATTLESHIP | 4 | 1 | 2 |
| CARRIER | 5 | — | 1 |
| **Total ships** | | **10** | **10** |
| **Total occupied cells** | | **20** | **30** |

Source: `logic/engine/config/UkrainianGameEditionConfiguration.java` and
`MiltonBradleyGameEditionConfiguration.java`.
