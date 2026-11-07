# Architecture

How the pieces fit together and how a game flows through them. For the product goals read [intent](intent.md); for the game rules and
the endpoint list read the [project guide](index.md); for the module internals, configuration and build commands read
the [backend guide](backend.md). The wire format is defined only by [`contracts/openapi.yaml`](../contracts/openapi.yaml).

## Purpose in one paragraph

Two anonymous players play Battleship in browsers. The **backend decides everything** (rules, turns, outcomes and what each
player may see). The **frontend only draws** the caller-relative snapshot the backend returns and enables the actions listed in
its `allowedActions`. The two meet only at the OpenAPI contract. Nothing is stored on disk: games live in the backend's memory.

## System context

```mermaid
flowchart LR
    p1["Player 1 browser"]
    p2["Player 2 browser"]
    edge["Web server on the same origin<br/>vite preview, nginx, or Vite dev server"]
    be["Backend: Spring Boot JAR<br/>REST + Server-Sent Events"]
    mem[("Process memory<br/>games, sessions, streams")]

    p1 -->|"HTTPS: static files, /api/v1/*, event stream"| edge
    p2 -->|"HTTPS"| edge
    edge -->|"/api/ proxied, X-Forwarded-For set"| be
    be --- mem
```

- There is **no database, queue or external service**. Restarting the backend ends every game and session.
- The browser sees **one origin**. The web server serves the built UI and proxies `/api/` to the backend, so there is no CORS.
- Server-to-browser updates use one Server-Sent Events (SSE) stream per player; everything the player does is an ordinary POST.

## Modules and dependency rule

```mermaid
flowchart TB
    contract["contracts/openapi.yaml<br/>the only coupling"]
    subgraph backend ["backend (Maven, Java 25)"]
        direction TB
        app["app<br/>Spring HTTP, security, SSE, config"]
        application["application<br/>use cases, registries, projection"]
        domain["domain<br/>game model and rules (plain Java)"]
        app --> application
        application --> domain
        app --> domain
    end
    fe["frontend (React, TypeScript)<br/>screens, gateway, session"]

    contract -.->|"openapi-generator at build: wire DTOs"| app
    contract -.->|"openapi-typescript: src/contract/generated/api.ts"| fe
    fe -->|"HTTP and SSE"| app
```

Dependencies point **inward** (`app` to `application` to `domain`), never back. Two mechanisms enforce it, so a wrong import fails the build:

- Maven Enforcer `bannedDependencies` in each module's `pom.xml` ([domain](../backend/domain/pom.xml), [application](../backend/application/pom.xml), [app](../backend/app/pom.xml)).
- [`ArchitectureTest`](../backend/app/src/test/java/ua/kostenko/battleship/app/ArchitectureTest.java) (ArchUnit): `domain` may not use Spring, Jackson, `jakarta`, `java.io`, logging, `Clock` or `Random`; `application` may not use Spring, Jackson or `jakarta`, nor `app`.

Consequence: rules and use cases run in plain unit tests, and the HTTP layer can change without touching them. Generated DTOs carry Jackson annotations, so they live in `app` and are filled from the framework-free `SnapshotView` of `application` ([details](backend.md#dto-generation)).

## Game phases and `allowedActions`

The server computes the set of actions each seat may perform right now ([`AllowedActions`](../backend/domain/src/main/java/ua/kostenko/battleship/domain/rules/AllowedActions.java)) and puts it in every snapshot. The UI enables exactly those. A command whose action is not in the set is refused with `action-not-allowed` before any rule runs.

```mermaid
stateDiagram-v2
    [*] --> WAITING: host creates game
    WAITING --> PLACEMENT: guest joins with invitation secret
    WAITING --> [*]: host leaves (game removed)
    PLACEMENT --> PLAYING: both seats READY
    PLACEMENT --> ABANDONED: either seat leaves
    PLAYING --> FINISHED: last ship sunk, or RESIGN
    FINISHED --> [*]: forgotten after result retention
    ABANDONED --> [*]: forgotten after result retention
```

Expiry is not a phase. A game in `WAITING`, `PLACEMENT` or `PLAYING` that reaches its idle timeout or maximum duration is *expired*: its players get `410 game-expired` for a while, then it is forgotten (see [flow 5](#5-expiry-and-abandon)).

| Phase | Host may | Guest may |
|---|---|---|
| `WAITING` | `NEW_INVITATION`, `SEND_PRESENCE`, `LEAVE` | nothing (no guest yet) |
| `PLACEMENT`, not ready | `PLACE_SHIP`, `REMOVE_SHIP`, `PLACE_FLEET_RANDOMLY`, `CLEAR_FLEET`, `SEND_PRESENCE`, `LEAVE`; plus `READY` once every ship is placed | same as host |
| `PLACEMENT`, ready | `SEND_PRESENCE`, `LEAVE` | same as host |
| `PLAYING` | the seat on turn: `FIRE`, `RESIGN`, `SEND_PRESENCE`; the other seat: `RESIGN`, `SEND_PRESENCE` | same as host |
| `FINISHED`, `ABANDONED` | `LEAVE` | `LEAVE` |

## Flows

### 1. Create a game, invite, join

```mermaid
sequenceDiagram
    autonumber
    participant H as Host browser
    participant G as Guest browser
    participant B as Backend

    H->>B: GET /api/v1/meta
    B-->>H: limits + Set-Cookie XSRF-TOKEN (readable by JS)
    H->>B: POST /api/v1/games {rulesetId, displayName}<br/>header X-XSRF-TOKEN
    B-->>H: 201 snapshot (phase WAITING, invitationUrl) + Set-Cookie __Host-battleship_session (HttpOnly)
    Note over H: invitationUrl = {public-base-url}/join/{gameId}#invite={secret}
    H-->>G: shares the link or QR code (out of band)
    Note over G: The secret is in the URL fragment, so the browser never sends it by itself.<br/>The page reads it, removes it from the address bar, keeps it in memory.
    G->>B: GET /api/v1/meta
    B-->>G: Set-Cookie XSRF-TOKEN
    G->>B: POST /api/v1/games/{id}/join {invitationSecret, displayName}<br/>only after the visitor confirms
    B-->>G: 200 snapshot (phase PLACEMENT) + Set-Cookie session
    B-->>H: SSE snapshot (host now sees the guest)
```

- A link scanner that merely fetches the page cannot take the seat: the fragment never reaches the server and joining needs an explicit POST.
- The server stores only SHA-256 digests of the session value and of the invitation secret. The invitation is cleared when used, replaced, expired, or when the host leaves.
- Use cases: [`CreateGameUseCase`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/CreateGameUseCase.java), [`JoinGameUseCase`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/JoinGameUseCase.java).

### 2. Placement and ready

```mermaid
sequenceDiagram
    autonumber
    participant P as Player (either seat)
    participant B as Backend
    participant O as Opponent browser

    P->>B: POST .../commands {commandId, PLACE_SHIP | PLACE_FLEET_RANDOMLY | REMOVE_SHIP | CLEAR_FLEET}
    B-->>P: 200 snapshot with own ships and new allowedActions
    Note over B,O: Placement is private. The opponent's view is not changed by it.
    P->>B: POST .../commands {READY} (offered once every ship is placed)
    B-->>P: 200 snapshot (ready)
    B-->>O: SSE snapshot (opponent is ready)
    Note over B: When the second seat is ready: phase PLAYING.<br/>The seat that became ready first moves first. If both timestamps are equal, a coin flip decides.
```

### 3. A shot

```mermaid
sequenceDiagram
    autonumber
    participant A as Attacker browser
    participant B as Backend
    participant S as Game slot (locked)
    participant D as Defender browser

    A->>B: POST .../commands {commandId, FIRE, target}
    B->>B: filters: size, origin, session, XSRF, rate limit
    B->>S: lock the game's slot
    S->>S: authorize seat, skip if commandId already accepted
    S->>S: GameRules.apply: new immutable GameState, version + 1
    S->>S: store state, remember commandId, move idle deadline
    S-->>B: state and context captured, lock released
    B->>B: project one snapshot per changed seat
    B-->>A: 200 snapshot (result, new turn)
    B-->>D: SSE event "snapshot", id = version
```

- The state change happens under a **per-game lock**; projecting and publishing happen **after** the lock is released.
- Replaying the same `commandId` (for example after a lost answer) applies nothing twice and returns the current snapshot.
- Both seats' views change on every accepted shot, so both streams receive a new snapshot with a higher `version`. Clients ignore any snapshot whose version is not newer.

### 4. Reconnect and recovery

```mermaid
sequenceDiagram
    autonumber
    participant C as Browser
    participant B as Backend

    C-xB: stream drops (network, tab sleep, stream max lifetime)
    Note over B: Last stream of the seat closed: opponent sees it disconnected (version + 1)
    C->>B: EventSource reconnects automatically: GET .../events
    B-->>C: first event is always the current snapshot (no replay, Last-Event-ID is ignored)
    alt reconnect refused or stream ended CLOSED
        C->>B: GET /api/v1/games/{id}
        B-->>C: 200 snapshot, or 401 / 404 / 410 (game is gone for this browser)
        C->>B: GET .../events (open a new stream if the game is still there)
    end
    alt event "closed" received
        Note over C: reason REPLACED (another tab took over) or GAME_UNAVAILABLE: do not reconnect
    end
```

Client rules (ordering by `version`, one command at a time, presence timer) are in the [contract guide](../contracts/README.md#client-rules). Server side: [SSE hub](backend.md#live-updates-sse-hub).

### 5. Expiry and abandon

```mermaid
flowchart TB
    live["Game is live<br/>(WAITING, PLACEMENT, PLAYING)"]
    live -->|"command, presence or join moves the idle deadline"| live
    live -->|"idle timeout or max game duration reached"| exp["Expired, retained<br/>players get 410 game-expired,<br/>strangers get 404"]
    live -->|"last ship sunk or resign"| fin["FINISHED, retained"]
    live -->|"leave during PLACEMENT"| aband["ABANDONED, retained"]
    live -->|"host leaves during WAITING"| gone["Removed at once"]
    exp -->|"result retention elapsed"| forgot["Forgotten: 404 for everyone"]
    fin -->|"result retention elapsed"| forgot
    aband -->|"result retention elapsed"| forgot
    forgot -->|"sweeper removes the slot from memory"| gone
```

Which answer a request gets depends only on deadlines compared at request time ([`ExpiryPolicy`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/ExpiryPolicy.java)); the periodic sweeper only frees memory and closes streams. Durations are configurable: see the [configuration reference](backend.md#configuration-reference).

## Security model

| Concern | Mechanism | Where |
|---|---|---|
| Who is this browser? | Random 32-byte session value in cookie `__Host-battleship_session` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, no `Domain`). The server keeps only its SHA-256 digest. | [`SessionCookie`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/SessionCookie.java), [`SessionRegistry`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/SessionRegistry.java) |
| Which seat in a game? | The game slot stores the digests of its host and guest sessions. Knowing a `gameId` grants nothing. A stranger gets `404 game-unavailable`, indistinguishable from a missing game. | [`GameSlot`](../backend/application/src/main/java/ua/kostenko/battleship/application/registry/GameSlot.java), [`ExpiryPolicy.authorize`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/ExpiryPolicy.java) |
| Joining | One-time 32-byte invitation secret, compared as digests in constant time, delivered in the URL fragment. | [`JoinGameUseCase`](../backend/application/src/main/java/ua/kostenko/battleship/application/usecase/JoinGameUseCase.java) |
| Forged requests (CSRF) | Every unsafe request must echo the readable `XSRF-TOKEN` cookie in `X-XSRF-TOKEN`. Requests marked `Sec-Fetch-Site: cross-site` or `same-site`, or with an `Origin` other than the origin of `battleship.public-base-url`, are refused with 403. | [`SecurityConfig`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/SecurityConfig.java), [`SiteIsolationFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/SiteIsolationFilter.java) |
| Hidden information | The opponent's ships are never sent, except hit and sunk cells, water the ruleset reveals around a sunk ship, and the whole board once `FINISHED`. Done in `SnapshotProjector`, not in the UI. | [`SnapshotProjector`](../backend/application/src/main/java/ua/kostenko/battleship/application/projection/SnapshotProjector.java) |
| Abuse | Per-operation rate limits (address for create and join, session for the rest), request body cap, caps on games, sessions, streams and live games per browser. | [backend guide](backend.md#rate-limiting) |
| Caching | Every response carries `Cache-Control: no-store`. | [`CacheControlFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/web/CacheControlFilter.java) |
| Browser hardening | The web server in front sets `Content-Security-Policy`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options`, `X-Frame-Options: DENY`. | [`config/nginx-server.conf.template`](../config/nginx-server.conf.template), `securityHeaders` in [`frontend/vite.config.ts`](../frontend/vite.config.ts) |
| Client address | Per-player rate limits; the [trust model](configuration.md#x-forwarded-for-from-browser-to-rate-limit) says which proxy sets `X-Forwarded-For` and when the backend believes it. | [`nginx-server.conf.template`](../config/nginx-server.conf.template), [`RateLimitFilter`](../backend/app/src/main/java/ua/kostenko/battleship/app/security/RateLimitFilter.java) |
| Logs | Records carry a correlation id and the `gameId` only; never names, cookies, secrets, boards, bodies or addresses. | [backend guide](backend.md#logging) |

The session cookie needs a secure context, so the app must be opened over HTTPS or on `localhost`, and with exactly the URL configured as `battleship.public-base-url`.

## Deployment topologies

```mermaid
flowchart TB
    subgraph local ["scripts/local-deploy.sh (your machine / LAN)"]
        b1["Browser"] -->|"https://LAN-IP:4173 or http://localhost:4173"| v["vite preview<br/>static files + /api proxy"]
        v -->|"127.0.0.1:8080"| j1["java -jar battleship-app.jar"]
    end
    subgraph dev ["Frontend development"]
        b2["Browser"] -->|"http://localhost:5173"| vd["vite dev server<br/>hot reload + /api proxy"]
        vd --> j2["java -jar, started by hand"]
    end
    subgraph docker ["Docker images (scripts/build-docker-*.sh)"]
        b3["Browser"] -->|"your HTTPS in front"| n1["battleship-frontend<br/>nginx :8080"]
        n1 -->|"BATTLESHIP_BACKEND_UPSTREAM"| j3["battleship-backend :8080"]
    end
    subgraph render ["Render image (scripts/build-docker-render.sh)"]
        b4["Browser"] -->|"HTTPS"| re["Render edge"]
        re -->|"HTTP on PORT"| n2["nginx in the same container"]
        n2 -->|"127.0.0.1:8080"| j4["backend in the same container"]
    end
```

All topologies keep UI and API on one origin. Every one of them needs `BATTLESHIP_PUBLICBASEURL` to equal the URL typed in the browser. Single backend instance only, because state is in memory.

Commands, ports, certificates and troubleshooting: [local operation](local-operation.md). Building, pushing and running the Render image: [Render deployment](render-deployment.md). The `Dockerfile` stages are commented in [`Dockerfile`](../Dockerfile).
