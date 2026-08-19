# Frontend launch specification input

## Purpose

Build a standalone browser client for anonymous two-player Battleship. It gives a host and a guest a clear, private, accessible path from invitation through fleet placement, live play, and results. The server is the only authority for rules, identity, session state, command outcomes, expiry, and recovery.

The client must preserve the Naval Command Center mockup's information design and interaction intent without preserving its simulated rules or its local fake game engine. The product supports both server-advertised rulesets and displays their server-provided names, fleet facts, board facts, contact policy, and turn policy before a game is created.

## User-visible scope

The application provides these named screen or state surfaces from the mockup:

- Home
- Create game
- Join invitation
- Waiting room
- Fleet placement
- Gameplay
- Results
- Session unavailable
- UI states and overlays

Every surface is available in English and Ukrainian. It supports dark and light themes, an optional decorative emoji mode that never replaces a textual or colour-independent meaning, optional synthesized sound, and supported haptic feedback where the device grants it. Rules and settings are available without leaving an active game. Copy and QR sharing expose the same invitation destination. A cached installable application shell may open when no network exists, but it must state that gameplay commands and authenticated game data are unavailable until the network returns.

## Named EARS rules

### Authoritative session view

- **Server snapshot:** When the client receives a validated snapshot or ordered realtime event from the current `serverEpoch` with a newer game version, it shall render that projection and shall not infer hidden opponent placement, turn, winner, fleet composition, expiry, or command success from local calculations.
- **Epoch boundary:** When metadata, a snapshot, an event, or a problem carries a different `serverEpoch`, the client shall invalidate cached game data, shall not compare versions across epochs, and shall show the restart/unavailable recovery state supported by the safe evidence.
- **Stale or malformed data:** If a network response, event, or runtime configuration fails validation, the client shall keep the last validated view, surface a recoverable connection state, and shall not place the invalid value in application state.
- **Restart and expiry:** When the server reports session expiry, restart loss, revoked membership, or an unavailable session, the client shall clear only game-specific presentation state, show Session unavailable with the server-safe reason and recovery action, and shall not claim that a game can be resumed.
- **Credentialed gateway:** Before the first unsafe request, the client shall call `/api/v1/meta` to establish the readable `BATTLESHIP-XSRF-TOKEN`; all API requests and SSE streams shall include browser credentials, and each unsafe request shall echo that token only in `X-Battleship-CSRF` through the single gateway.

### Invitation privacy

- **Fragment arrival:** When Join invitation opens with an invitation secret in the URL fragment, it shall read the secret locally, remove the fragment from browser history before rendering shareable navigation, and show an explicit Join game action.
- **Invitation redemption:** When the guest selects Join game, the client shall submit the secret exactly through the redemption command and shall not redeem on page load, browser prefetch, QR scan, or link preview.
- **Secret containment:** The client shall never render an invitation secret as text, persist it, log it, place it in a query parameter, include it in telemetry, or expose a capability or cookie value. It may hold the exact canonical fragment URL in memory only for the explicit Copy link and Show QR actions; clipboard and QR output are the only authorized invitation disclosure.
- **Share action:** When a host selects Copy link or Show QR, the client shall use the exact same canonical fragment-bearing invitation URL returned by creation or rotation, shall never reconstruct it from safe metadata, and shall provide success or failure feedback without rendering the secret in surrounding UI.

### Creation and waiting

- **Ruleset selection:** When Create game loads, it shall show both server-advertised rulesets with server metadata before the host can submit a selection.
- **Creation request:** When valid player input and a ruleset are submitted, the client shall show pending state, send one deliberate creation request, and navigate only after an authoritative accepted snapshot is returned. If the outcome is unknown, it shall not replay creation automatically; after reconciliation it may offer an explicit Create another game action and explain that an orphan expires normally.
- **Waiting state:** While Waiting room is current, it shall show invitation status, expiry or replacement state when supplied, and a connection status that distinguishes connected, reconnecting, stale, and checking. Share actions are available only while the server-returned canonical URL remains in memory. After reload or an unknown rotation outcome loses that URL, the room shall offer an explicit Replace invitation action; it shall never pretend to recover or reconstruct the prior secret.
- **Guest arrival:** When a newer snapshot shows the guest has joined, the host shall move to Fleet placement without a local stage guess.
- **Visible presence:** While an authenticated non-terminal game screen is active and the document is visible, the client shall submit presence no more often than once every five minutes. Hidden tabs, background reconnects, polling, and SSE heartbeats shall not send presence. An `extended: false` response shall update the displayed expiry without being treated as an error or a game-state transition.

### Placement and game commands

- **Fleet placement:** When a player activates an empty board cell, the client shall offer candidate ship and orientation actions from the server-advertised fleet metadata and current allowed-command vocabulary; when the player activates a placed ship, it shall offer the corresponding candidate actions such as rotate or remove. The client shall present these as requests for authoritative server validation rather than claiming that local geometry or contact checks make them legal.
- **Atomic placement action:** When a ship action is submitted, the client shall disable that action until its idempotent command resolves and shall render only the returned authoritative snapshot. It shall not model rotate as a client-side remove followed by add.
- **Ready action:** When the authoritative snapshot includes `READY` in the current allowed commands, the client shall enable the Ready action; otherwise it shall use the server-safe state to explain why readiness is unavailable.
- **Shot action:** When the authoritative snapshot includes `FIRE` and the player activates an unresolved target cell, the client shall submit one idempotent shot command, block duplicate activation of that cell while pending, and show the authoritative result and updated snapshot. The server remains responsible for turn and target legality.
- **Version preconditions:** When the client submits a versioned game command or `LEAVE`, it shall derive `expectedVersion` and the matching `If-Match` from the same last validated snapshot and ETag, create one `commandId`, and preserve all three values for an identical retry. On 412 it shall reconcile a fresh snapshot before offering a new command; it shall not apply this retry rule to creation or an invitation rotation with an unknown secret-bearing result.
- **Realtime recovery:** When a realtime stream disconnects, goes stale, or returns from background, the client shall show its connection state, reconnect under the contract policy, and request an authoritative snapshot without replaying a completed command.

### Feedback, settings, and results

- **Visual feedback:** When an authoritative command or event changes a board, the client shall make the new state understandable through text, symbol, colour-independent marks, and a concise live announcement without relying on animation alone.
- **Sound and haptics:** When a user has enabled feedback and the browser permits it, the client shall synthesize the contract-defined miss, hit, and destroyed feedback from authoritative transitions and may use supported haptics. Failure, permission denial, reduced-motion preference, or unavailable hardware shall not block rendering, commands, navigation, or results.
- **Settings:** When the player changes language, theme, emoji mode, or feedback preferences, the client shall update the visible application immediately and persist only those non-secret preferences.
- **Results:** When a finished snapshot is received, the client shall show win or loss, both permitted board projections, fleet condition, phase durations, turn/shot/hit/accuracy values, and accepted shot-decision timing supplied by the server; it shall not derive statistics from a fixed fleet constant or local clock.
- **Leave confirmation:** When a player attempts to leave placement or a waiting session, the client shall request confirmation and, after confirmation, send one versioned pre-play `LEAVE` with a stable `commandId`. When a player attempts to leave during gameplay, it shall explain the loss and send `RESIGN` after confirmation. Cancelling shall preserve the view. The client shall return Home and clear local non-secret game presentation only after an authoritative accepted or reconciled outcome; while the outcome is unknown it shall show checking and retry only the same identity or reconcile.
- **Leave results:** When a former member chooses Leave results after normal finish, resignation, or abandonment, the client shall send one versioned terminal `LEAVE` and clear the game view only after the server returns or safely replays 204. A failure or unknown outcome shall retain the same `commandId` for retry and shall not fabricate successful revocation.

### Inclusive responsive interaction

- **Desktop layout:** Where usable width permits, Gameplay shall show own and target boards together with turn, player, and connection context.
- **Narrow portrait layout:** Where usable width is narrow, Gameplay shall expose a keyboard-operable tab control that shows one board at a time without hiding current turn or command status.
- **Short landscape layout:** Where a short landscape viewport permits two compact boards, Gameplay shall keep both usable without horizontal document scrolling or obscured controls.
- **Input parity:** When a user uses keyboard, touch, mouse, pen, screen reader, browser zoom, forced colours, reduced motion, or an enlarged text setting, every essential action shall remain operable and understandable.
- **Placement input parity:** Where pointer or touch drag is supported, Fleet placement may offer direct drag as an enhancement, but every drag result shall invoke the same atomic server command and every placement action shall remain available through the cell/action dialog and keyboard path.
- **Overlay behaviour:** When a rules, settings, QR, confirmation, placement, or error overlay opens, it shall have a labelled dialog or sheet semantics, managed focus, Escape and explicit-close behaviour where dismissal is safe, and focus restoration to its opener.

## Representative acceptance scenarios

| Scenario | Expected outcome | Proving test |
|---|---|---|
| Host creates either advertised ruleset | Metadata is visible, create is pending once, and Waiting room receives an authoritative host view | Component test with contract fixtures; isolated-browser E2E for each ruleset |
| Guest opens a QR invitation | Fragment disappears from history before Join game; no redemption happens until activation | Browser integration test with a fragment fixture and network spy |
| Two guest contexts race to redeem | One accepted guest receives a member view; the other receives a safe unavailable/conflict outcome | Two isolated browser contexts against the integration harness |
| Player places, rotates, removes, and readies | Candidate actions come from contract metadata, server conflicts remain safe, each command is single-flight, and the final board comes from the returned snapshot | Component command-state tests and contract-backed E2E |
| Opponent shot arrives after a stale stream | Reconnect/refetch accepts only a newer version and preserves the current board while recovering | Gateway/realtime tests with ordered and stale fixtures |
| Narrow portrait and short landscape battle | Tabs or compact dual boards stay keyboard and touch operable without horizontal overflow | Playwright viewport and accessibility checks |
| Theme, locale, emoji, sound, and haptic capability vary | Essential state remains textually available; no denied capability produces an app failure | Component tests plus browser capability stubs |
| Server expiry or restart loss occurs | Session unavailable explains the safe next action and retains no secret | Contract fixture and isolated-browser E2E |

## Non-goals

- Client-side game-rule enforcement, a second engine, local victory calculation, or hidden-board inference.
- Offline gameplay, queued commands, command replay after offline use, or cached authenticated snapshots.
- Accounts, player registration, chat, spectators, rankings, payments, analytics, advertisements, or social sharing.
- Pixel-perfect screenshot matching. Visual checks protect hierarchy, state visibility, overflow, contrast, focus, and responsive behaviour.
- Backend, database, deployment, hosting, CI, multi-replica, or persistence design.

## Success boundary

The feature is successful when both rulesets can be understood and played through all named surfaces using authoritative validated data; invitation secrets do not leak; recovery, expiry, restart loss, and command races are understandable; and the full journey is usable on the stated input, accessibility, and viewport conditions.
