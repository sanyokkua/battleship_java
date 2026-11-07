# Feature Specification: Battleship Frontend and Local Integration

> Script consolidation of 2026-11-07: see the note at the top of [plan.md](plan.md); command names below are those of the day they were written.


**Feature Branch**: `feature/003-frontend`
**Created**: 2026-10-18
**Status**: Implemented; T024 (coverage import into Sonar) still open

**Input**: "Plan the next feature from the legacy frontend seed documents (since removed; intent in `docs/intent.md`). No mocking of the backend by reimplementing its logic
for tests; if that is not possible, integration is part of this feature, so the frontend
is tested against the real working API. Issues found in the API contract or backend may be fixed."

This feature also owns the root integration layer (there is no separate integration feature). Every
optional seed capability (installable shell, drag placement, sound, haptics, emoji, QR) is in scope.

## Authority

`contracts/openapi.yaml` is the source of truth for every wire behaviour; the delivered backend
(`backend/`, feature `002-backend`) is its implementation. Anything else is context, not authority.
The Naval Command Center mockup (`docs/design/naval-command-center-mockup.html`) is the visual and interaction reference: its design tokens (dark and light), component
styling, screen composition and responsive behaviour are reproduced (R58–R61). Its simulated rules, demo
data, preview controls and the elements listed in R60 are not. Exact pixels are not asserted automatically.

Requirements are `[user]` unless tagged `[agent]` — proposed by the agent and approved by the owner.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — A host creates a game and shares the invitation (Priority: P1)

A person sees the rulesets the server offers, picks one and a name, and gets one link to send.

**Independent Test**: In a fresh browser, create a game with each advertised ruleset and confirm the
waiting room shows that ruleset's facts, the invitation lifetime, and working copy-link and QR actions.

**Acceptance Scenarios**:

1. **Given** the create screen, **When** it loads, **Then** every ruleset the server advertises is
   shown as a compact selectable name and fleet badges (count × ship length); View details exposes
   its board size, fleet, touching rule, extra-turn rule and revealed-water rule before submission.
2. **Given** a valid name and ruleset, **When** the host submits, **Then** submit is disabled while the
   request is pending, exactly one creation request is sent, and the waiting room opens only from the
   server's answer.
3. **Given** the waiting room, **When** the host chooses Copy link or Show QR, **Then** both carry
   exactly the invitation link from the host's current view, with success or failure feedback, and the
   secret part never appears as visible text.
4. **Given** the waiting room after a reload, **When** the view loads, **Then** sharing works again from
   the server's view; **When** the server's view carries no invitation link, **Then** the room says the
   invitation expired and sharing is unavailable.
5. **Given** the waiting room, **When** the host chooses Replace invitation and confirms that the old
   link stops working, **Then** the new link from the server's answer is shared from then on.

### User Story 2 — A guest opens the link and joins (Priority: P1)

**Independent Test**: Open a host's link in a second isolated browser, confirm the secret leaves the
address bar before any action, join explicitly, and confirm both browsers reach fleet placement.

**Acceptance Scenarios**:

1. **Given** an invitation link, **When** it opens, **Then** the secret is removed from the address and
   history before the page renders, and nothing is redeemed until the guest activates Join game.
2. **Given** two browsers that open the same link, **When** both join, **Then** exactly one becomes the
   guest and the other sees a safe "invitation unavailable" outcome.
3. **Given** a guest has joined, **When** the host's view updates, **Then** the host moves to fleet
   placement with no action of its own.

### User Story 3 — Both players place their fleets (Priority: P1)

**Independent Test**: For each ruleset, place, move, rotate and remove ships, use random arrangement
and clear, then ready; confirm each board shown equals the server's answer and illegal moves show the
server's refusal reason.

**Acceptance Scenarios**:

1. **Given** placement, **When** the player activates an empty cell, **Then** they are offered the
   available ships and only orientations valid at that cell in a compact anchored popup; **When** they
   activate a placed ship, **Then** a cell-adjacent menu offers move, remove, and legal rotation. Move
   visually lifts the ship but preserves its server placement until one atomic command succeeds; cancel
   restores it. The actions are reachable by keyboard, touch selection and mouse drag.
2. **Given** a ship action is sent, **When** it is pending, **Then** that action is disabled and the
   board changes only to the server's answer; a refusal (out of bounds, overlap, touching) is explained
   in words and the board stays as the server last reported it.
3. **Given** the server offers Ready, **When** the player readies and confirms that the fleet is then
   locked, **Then** the readiness is shown and is not undoable; **When** Ready is not offered, **Then**
   the UI says why (fleet incomplete, or already ready and waiting for the opponent).
4. **Given** placement, **When** the player drags an unplaced ship from the fleet tray or a placed ship
   onto the board by mouse, **Then** a compact direction chooser offers only valid directions, or
   explains that no direction fits; single-cell ships need no chooser. Touch can select a ship then a
   cell without accidental drag during scrolling. The board shows the server's answer.

### User Story 4 — Play to the end and see the results (Priority: P1)

**Independent Test**: Play one full game per ruleset in two browsers and confirm turn changes, shot
results, sunk ships and revealed water follow that ruleset, and the results screen shows the server's
statistics.

**Acceptance Scenarios**:

1. **Given** it is the player's turn, **When** they fire at an unknown cell, **Then** one shot is sent,
   that cell is blocked while pending, and the result, turn and both boards come from the server.
2. **Given** a Sea Battle hit, **Then** the same player keeps the turn and a sunk ship's surrounding
   water is shown distinct from fired water; **given** a Classic hit, **Then** the turn passes.
3. **Given** the opponent fires, **When** the update arrives live, **Then** the player's own board,
   turn and a short announcement update without a reload.
   [user] On the narrow single-board layout, show and pulse the last shot on the board it affected;
   when that shot changes the turn, leave its result visible for one second before switching to the
   board for the next player. A retained turn does not trigger a board switch.
4. **Given** the game finishes, **Then** results show win or loss, both revealed boards, fleet
   condition (intact, damaged, sunk), and four statistic groups — match time (total, placement,
   gameplay, each player's placement), turns and accuracy (turns, shots, hits, accuracy), and turn
   timing (total, average, fastest, slowest), and [user] shot-decision timing (each player's total,
   average, fastest and slowest accepted-shot decisions) — all as supplied by the server.
5. **Given** results, **When** the player chooses Leave results, **Then** their access to that result
   ends and they return Home.

### User Story 5 — Leaving, expiry, restart and connection loss are understandable (Priority: P2)

**Independent Test**: Against the real server: leave before play, resign during play, let a game expire
under a short configured idle time, restart the server mid-game, and cut the live connection; confirm
each ends in an honest, recoverable screen.

**Acceptance Scenarios**:

1. **Given** waiting or placement, **When** the player leaves and confirms, **Then** the game is
   abandoned, the leaver returns Home, and cancelling keeps the view.
2. **Given** the opponent left before play, **When** the update arrives, **Then** the remaining player
   sees that the game was abandoned, with no boards revealed, and can leave the result.
3. **Given** play, **When** the player chooses to leave, **Then** they are told it means resigning, and
   after confirmation the opponent wins.
4. **Given** the game expired, **When** the player acts or the view refreshes while the server still
   remembers it, **Then** Session unavailable says the game expired; **given** the server restarted or
   no longer knows the game or session, **Then** it says the game is no longer available and that a
   server restart is one possible cause. Both offer a new game; nothing claims the game can be resumed.
5. **Given** the live connection drops or the tab returns from background, **Then** the connection
   state is shown, the view is refreshed from the server, and no completed action is repeated.
6. **Given** an action whose answer was lost, **When** the player retries, **Then** the same action is
   resent as the same action, never as a new one.

### User Story 6 — Settings, rules and inclusive use (Priority: P2)

**Independent Test**: Run one full journey at desktop, narrow portrait and short landscape sizes using
keyboard only, by touch only and with an accessibility scanner; switch language, theme, emoji, sound
and haptics mid-game; compare the captured screens with the mockup.

**Acceptance Scenarios**:

1. **Given** any screen, **When** the player changes language (English/Ukrainian), theme
   (dark/light/system), emoji, sound or haptics, **Then** the change applies immediately, survives a
   reload, and never interrupts the game.
2. **Given** sound or haptics are denied or unsupported, **Then** play and results are unaffected.
3. **Given** a narrow portrait screen, **Then** one board shows at a time behind a keyboard-operable tab
   control while turn and action status stay visible; **given** a short landscape screen, **Then** both
   compact boards stay usable without horizontal scrolling.
4. **Given** any modal dialog (rules, settings, QR, confirmation, error), **Then** it is
   labelled, keeps focus inside, closes on Escape where safe, and returns focus to its opener.
5. **Given** each surface at phone portrait, phone landscape and laptop sizes in dark and light themes,
   **Then** it shows the widgets R59 lists for it, styled with the mockup's tokens.
6. **Given** a touch-only or a mouse-only device, **Then** every action of the journey is reachable and
   none depends on hover.

### User Story 7 — The installed app opens offline honestly (Priority: P3)

**Independent Test**: Load the built app once, go offline, reopen it, and confirm the shell opens with a
clear "network required" state and no game action is queued.

**Acceptance Scenarios**:

1. **Given** the app was loaded once, **When** it opens without a network, **Then** the shell renders and
   says games need a connection; game actions are disabled, not queued.
2. **Given** a new version is deployed, **When** the app detects it, **Then** the player is asked before
   it updates.

### User Story 8 — A contributor runs and verifies everything locally (Priority: P2)

**Independent Test**: From a clean clone with the pinned runtimes, run the documented commands for
development, production-style local run and the single verification gate.

**Acceptance Scenarios**:

1. **Given** the backend is running, **When** the development command starts the frontend, **Then** the
   browser reaches the API and live updates through one origin.
2. **Given** the production-style command, **Then** the built static app and the packaged backend run as
   separate artifacts behind one origin, with no development address inside the built app.
3. **Given** the verification command, **Then** it runs the contract check, backend gate, frontend
   checks and build, and browser journeys against the packaged backend, in that order; any failed,
   skipped or blocked stage makes the overall result fail.

### User Story 9 — A contributor resolves local code quality findings (Priority: P2)

**Independent Test**: Run fresh backend and frontend SonarQube scans, export both reports, inspect
the overall issue lists and quality gates, repair source and test defects, then repeat the scans and
the repository gate.

**Acceptance Scenarios**:

1. **Given** the existing local SonarQube projects, **When** a contributor runs both documented scans,
   **Then** they inspect project settings, active profiles, issue reports, coverage and quality gates,
   including existing issues outside the new-code period.
2. **Given** an actionable finding, **When** it is repaired, **Then** the original behavior and security
   boundary still hold, a meaningful test proves the changed behavior where needed, and the refreshed
   overall issue list no longer reports it.
3. **Given** a quality-gate failure, **When** the contributor addresses the failing measure, **Then**
   both refreshed gates pass. Any finding that cannot be repaired as reported is left visible with its
   evidence and decision; no issue is silently dismissed or excluded.

### User Story 10 — A contributor builds and runs local images (Priority: P2)

**Independent Test**: Build the complete project and each image on the local machine. Start the
combined image using a trusted local HTTPS name and a mounted certificate and key. From a second
device on the same network, create and join a game through the single public origin, observe live
updates, and stop the container gracefully.

**Acceptance Scenarios**:

1. **Given** the required build tools, **When** the complete build script runs, **Then** contract,
   backend and frontend artifacts are produced or a failed stage is reported without a false success.
2. **Given** Docker, **When** the separate frontend and backend image scripts run, **Then** each image
   contains its own runnable artifact and has documented configuration and responsibility.
3. **Given** the combined image and local HTTPS configuration, **When** it runs, **Then** the UI, API and
   live updates share the exact public origin; invitations use that origin; another LAN device can use
   the game without changing the origin, cookie or anti-forgery protections.
4. **Given** a missing prerequisite, certificate, key, or an occupied public port, **When** the run
   script starts, **Then** it explains the failed condition. On stop, its child processes terminate.
5. **Given** a LAN IPv4 address, **When** a contributor runs `./scripts/local-deploy.sh <IPv4>`,
   **Then** the script validates that address, derives the matching HTTPS origin, builds the backend JAR and
   web app if missing, and creates or reuses a per-address certificate with a matching IP SAN. With no
   argument it detects the LAN address; `localhost` serves plain HTTP on this machine only.

### Edge Cases

- Creation answer lost: no automatic resend; the player is told the outcome is unknown; if the server
  did create the game, this browser cannot start another until that orphan expires, and the refusal says
  so (R07, R13).
- Join answer lost before the session arrived: unrecoverable by contract; the guest sees invitation
  unavailable on retry (R15).
- An update older than the one shown arrives: ignored; one at the same version updates only expiry and
  server time (R05).
- A refusal arrives for an action no longer allowed: the view refreshes from the server and the refusal
  is explained (R20).
- Too many requests or server busy: a calm "try again shortly" with the server's wait hint; no tight
  retry loop (R22).
- Invitation expired while the game is still waiting: the server's view has no link; Replace invitation
  restores sharing (R12).
- The browser already holds a live game (the server allows one per browser): Home offers Return to your
  game instead of a refused second create or join (R07).
- Reload on any game screen: the view is rebuilt from the server using the game address (R06).

## Requirements *(mandatory)*

### Server authority and data

- **R01** The client is a view: every state shown comes from the latest server snapshot; it never
  decides turn, hits, winner, expiry, statistics or action success locally. Placement controls may
  derive advisory legal anchors and orientations from the caller’s known fleet and published ruleset;
  the server validates every command and its latest accepted snapshot remains authoritative. Display
  counts are derived from the server’s board and ruleset (R59).
- **R02** An action is offered only when the server's current snapshot lists it as allowed.
- **R03** Before its first state-changing request the client obtains the anti-forgery token from the
  server's metadata operation and echoes it on every state-changing request. An anti-forgery refusal
  applied nothing, so for every request — creation and join included — the client refreshes the token
  and resends once.
- **R04** Ruleset facts (board, fleet, touching, extra turn, revealed water) are read from the server;
  the client holds no fleet or board constants.
- **R05** A snapshot with an older version than the one shown is ignored; one with the same version
  updates only expiry and server time (the contract excludes them from the version).
- **R06** A game's screen is addressed by its public game id, so reload and live reconnect rebuild it
  from the server. No secret, session value or player identifier is ever put in an address, query,
  storage or log.
- **R07** `[agent]` The public id of the browser's current game is remembered across tab closes so Home
  offers Return to your game; it is forgotten when the server reports the game gone or after leaving.
  Reason: the server admits one live game per browser, so without it a closed tab blocks the player from
  playing until the game expires. When the browser holds a live game the client does not know (lost
  creation answer, cleared storage), the server's refusal must be distinguishable from "server busy",
  must not be retried, and must tell the player they can play again once that game expires; the
  contract and backend are amended for this under R57 (today both cases answer the same busy code).

### Invitation privacy

- **R08** On arrival with an invitation link, the client reads the secret once into memory and removes
  it from the address and history before rendering anything shareable.
- **R09** Redemption happens only on explicit Join game activation — never on load, prefetch, QR scan or
  preview.
- **R10** The secret is never rendered as text, stored, logged or sent anywhere except the join request;
  it is dropped from memory once the join settles.
- **R11** Copy link and Show QR use exactly the invitation link in the host's current view; the client
  never builds one.
- **R12** While waiting, the host sees invitation status and expiry. The invitation counts as expired
  when the host's view has no invitation link; the client never compares clocks to decide it. Replace
  invitation is offered whenever the server allows it and is sent only after the host confirms that the
  current link stops working.

### Creation, joining and presence

- **R13** Creation is sent once per deliberate submit and is never resent automatically; an unknown
  outcome is explained; the player can return Home, where R07 applies.
- **R14** The host moves from waiting to placement only when a newer snapshot shows the guest joined.
- **R15** A join refused with a conflict shows one "invitation unavailable" outcome (the contract does
  not distinguish its causes); a gone answer follows R27.
- **R16** While the page is visible and the server allows presence, the client sends presence at most
  once per the server's published presence interval; hidden tabs, reconnects and heartbeats send none.
  A changed expiry is shown; an unchanged one is not an error.

### Placement, play and commands

- **R17** Placement offers per cell: place an unplaced ship (choose ship and orientation); per placed
  ship: move, remove, and rotate only if the alternative orientation is valid. Random arrangement, clear
  and ready are offered when allowed. Advisory checks consider board bounds, occupancy, the ruleset’s
  contact rule and the exact ship being moved; the server validates commands. Move and rotate are one
  atomic placement each, never remove-then-place; cancellation or rejection preserves the last server
  placement. Single-cell ships need no orientation choice. A failed placement is explained.
- **R18** Mouse drag placement and touch ship-then-cell selection reach the same single action as the
  keyboard path. A multi-cell drop offers its valid directions, and a no-fit drop leaves the ship
  available with a way to choose another cell. Touch scrolling must not start a drag. Placement popups
  use green ship shapes and counts, with the target cell and shape in the drop chooser header; omit
  the redundant Choose cell action and explanatory direction footer.
- **R19** Each action gets a new action identifier; a retry after a lost answer reuses it. Only one
  action per game is in flight; its control is disabled until it settles.
- **R20** On a conflict refusal (other than invitation unavailable, which has no game to read) the
  client refreshes the snapshot and shows the server's view plus a
  worded reason for the refusal code.
- **R21** Fire is offered only on unknown cells of the opponent board while the server allows it; one shot
  is pending at a time.
  [user] A new shot's affected cell is visibly highlighted with the approved pulse. In the narrow
  tabbed layout, a turn-changing shot stays visible for one second before the active board changes:
  opponent's turn shows the player's fleet; the player's turn shows enemy waters. Retained turns do
  not cause a switch. Newer shots supersede pending transitions; reduced-motion users see a static
  highlight rather than animation. Both-board layouts remain usable.
- **R22** Every problem code the contract defines maps to a stable, translated message and one next
  action (retry, refresh, Home or new game); no raw server text, stack trace, cookie or secret is shown.
  Rate-limit and busy answers show the server's wait hint and are not retried before it elapses.
- **R23** An answer or live update that cannot be read as the contract's type is treated as a transport
  failure: the last view stays, the connection state shows the problem, and the bad value is never
  stored.

### Live updates and recovery

- **R24** While a game screen is open the client listens to the game's live update stream and shows
  connection state: connected, reconnecting, offline; while an action's outcome is unknown the view shows
  checking.
- **R25** When the stream drops or the tab returns from background, the client reads the game from the
  server and reopens the stream if the read succeeds. It never repeats a completed action.
- **R26** When the server closes the stream because another tab took over, the client reports it and does
  not reconnect; when it closes it because the game is unavailable, the client reads the game once and
  does not reopen the stream.
- **R27** Session unavailable distinguishes only what the server's answer distinguishes: "game expired"
  (the gone answer) versus "no longer available — the game ended, you left, or the server restarted"
  (session-required or not-found answers). It offers Home and new game, and clears game-specific
  presentation state. It never names a cause the answer does not carry.

### Leaving

- **R28** Leave from waiting or placement asks for confirmation, then sends leave; Home is shown only
  after the server accepts or reports the caller already left (not-found after leave is success, not
  Session unavailable).
- **R29** Leave during play explains that it means resigning and sends resign after confirmation.
- **R30** Leave results ends the caller's access to a finished or abandoned result and returns Home.

### Feedback, settings and results

- **R31** Every board change is understandable without colour or animation: text, symbol or pattern,
  plus a concise live announcement. [user] Empty water and ship cells use cell styling/patterns rather than
  repeated visible glyphs; ordinary water and ships show no white dots or square glyphs.
- **R32** Optional synthesized sound for miss, hit and sunk, and optional haptics, play once per new
  shot result from the server; failure, denial, background or reduced-motion never blocks anything.
- **R33** Language (English, Ukrainian), theme (dark, light, system), emoji mode, sound and haptics
  apply immediately and persist; nothing else is persisted except the game id of R07. Emoji is decorative and never the only
  carrier of meaning. [user] Emoji mode is separate and off by default; when enabled, emoji may appear
  only in a transient firing or miss/empty-cell animation. There are no static emoji elsewhere in the UI.
- **R34** English and Ukrainian resources have identical keys (Constitution V).
- **R35** `[agent]` Ruleset and ship names are translated by the server's ruleset and ship-type
  identifiers, falling back to the raw identifier for an unknown one; the contract has no name field.
- **R36** Results render the server's outcome, both revealed boards, fleet condition and every
  statistic listed in US4 scenario 4 (mapped to fields in `contracts/ui-surface.md`); no statistic is
  computed locally (R01's display counts are not statistics).
- **R37** Rules and settings open as dialogs from any screen without leaving the game.

- **R38** The surfaces are Home, Create game, Join invitation, Waiting room, Fleet placement, Gameplay,
  Results, Session unavailable, and the dialogs/status overlays (rules, settings, QR, confirmations —
  ready, replace invitation, leave, resign — checking, error), placement popups and toasts. Home offers
  create, Return to your game (R07), rules and settings. A header on every surface offers Refresh (reads
  the open game from the server) and Settings; Settings offers Resign match while the server allows
  resigning.

### Inclusive responsive interaction

- **R39** Three layouts, chosen by available size as in the mockup: wide shows both boards side by
  side with turn, players and connection; narrow shows one board behind a keyboard-operable Enemy
  waters / Your fleet tab control, with turn and action status outside the tabs; short landscape shows
  two compact boards without tabs. No layout scrolls horizontally or hides an essential control.
- **R40** Every essential action works by keyboard, touch, mouse and screen reader, at browser zoom,
  in forced colours, with reduced motion (no animation plays) and enlarged text. Board cells have coordinate labels and
  state descriptions.
- **R41** Every modal dialog is labelled, traps and restores focus, and closes on Escape where dismissal is
  safe. Placement popups are nonmodal and labelled; opening one moves focus into it, and Escape or Cancel
  dismisses it and restores focus to its opener. Outside interaction dismisses it without preventing focus
  from moving to the chosen control. Popups stay visible near viewport edges without clipping or obscuring
  essential controls.

### Installable shell

- **R42** The app is installable; its offline cache holds only the static shell and assets — never API
  answers, live updates, invitation links or game state — and it never queues an action.
- **R43** Offline, the shell states that games need a connection and disables game actions.
- **R44** `[agent]` A newer version is applied only after the player agrees.

### Local integration

- **R45** One root install command checks the pinned runtimes, installs the contract and frontend
  lockfiles exactly, and validates the backend build wrapper, without global tools.
- **R46** One development command runs the frontend against a locally running backend through one
  origin, including live updates.
- **R47** One production-style local command serves the built frontend and the packaged backend as
  separate artifacts behind one local same-origin static server that also forwards API and live-update
  traffic unbuffered and serves the app for its deep links (join and game addresses); the built
   frontend contains no development address. For the existing non-container commands the hostname is
   `localhost`; the backend's public base address equals the frontend origin, because the backend
   accepts state-changing requests only from that exact origin. R66 defines the separate LAN container
   origin.
- **R48** Each started process reports readiness from the backend health operation before dependent
  steps run; a start-up failure names the component, the address and a next diagnostic step. Only
  processes the command started are stopped.
- **R49** One root verification command runs, in order: contract check, backend gate, frontend static
  checks and build, browser journeys against the packaged backend. A failed, skipped, timed-out or
  blocked stage fails the run.
- **R50** Short expiry, presence interval and rate limits for browser journeys are configured through
  the backend's existing settings at start-up; there is no test-only endpoint, clock control or backdoor. Restart loss is proven by
  stopping and starting the real process.
- **R51** Local command output names the component, lifecycle outcome and the backend's correlation id
  for failures, and never includes cookies, invitation links or secrets, board content or raw request
  bodies.
- **R52** A short local-run guide covers the commands, ports, configuration, restart and expiry limits,
  and the common failures (port in use, wrong runtime, backend down, expired invite, restart).

### Proof rules

- **R53** No test replaces the backend with recorded game data, a fake engine or reimplemented rules.
  Every flow — create, join, place, play, live update, leave, resign, expiry, restart, recovery — is
  proven by browser journeys against the packaged backend, using isolated browser contexts for the two
  players.
- **R54** Behaviour that involves no game data may be unit-tested alone; advisory placement checks may also
  have focused tests, with real-backend journeys establishing integration: translation key parity,
  preference persistence, sound/haptic no-op on denial, QR rendering of a given string, rejection of an
  unreadable answer (R23).
- **R55** Browser journeys cover a named compact matrix rather than every combination: each ruleset's
  defining behaviour once; copy link, QR and replace invitation once; one invitation race; one action
  race; ready once; leave, resign, abandoned view and leave-results once each; expiry, presence
  extension and restart once each; one rate-limit refusal under a lowered limit; stream recovery once;
  the three layouts, touch-only use and keyboard/screen-reader semantics once; offline shell once.
- **R56** `[agent]` Wire types come only from the contract; the client adds no second hand-written copy
  of the wire schema. Wire conformance is proven by the backend's contract assertions and by the
  journeys of R53.

### Contract and backend fixes

- **R57** A gap or defect found in the contract or backend while building this feature is fixed in
  place, with every current dependent and proof updated in the same change.

### Visual design

- **R58** Styling uses the mockup's semantic design tokens for both themes: surfaces, text, borders,
  accent, danger, warning, success, sea, ship, miss, hit, sunk, focus ring, radii, spacing, control
  heights and motion durations. Theme `system` follows the operating system's colour scheme.
- **R59** Each surface presents the mockup's widgets for it, fed only by server data; the per-surface
  list and each widget's data source are in `contracts/ui-surface.md` § *Surfaces and widgets*.
  [user] Board water and ship cells have no repeated visible text glyphs; non-colour state cues remain
  available through cell patterns, hit/sunk marks, accessible names and live announcements. Emoji mode
  is limited to the transient firing/miss animation described in R33.
- **R60** Not reproduced from the mockup: invitation token text, Copy token, a paste-token join field,
  Join game on Home (joining is by link or QR only), a display game number, the snapshot-version footer, and the mockup's preview,
  simulation and demo controls.
- **R61** Mouse, touch and pen reach the same actions; no action or information depends on hover.
  Controls are at least the mockup's control height; board cells share the available width.
- **R62** [user] During `PLACEMENT`, the caller's snapshot includes `placementBlockedCells`: the server-projected
  in-bounds empty cells adjacent horizontally, vertically or diagonally to placed ships when that ruleset forbids
  ship contact. It is an empty list when contact is allowed or no ship has been placed, and absent outside
  `PLACEMENT`. Normally the frontend renders those cells as unavailable, including in their accessible names. While
  moving a ship it displays the restrictions from other ships, excluding the moving ship’s footprint
  and vacated halo; it never treats this display hint as a complete placement oracle.

### SonarQube and local images

- **R63** [user] Fresh local SonarQube analyses cover the backend and frontend. The contributor inspects
  their project configuration, profiles, overall open findings, coverage reports and quality gates;
  repairs genuine issues in source and tests, including security findings considered in context; exports
  both reports; and reruns both analyses. The target is no remaining open actionable findings and both
  gates passing. Failed or environment-blocked scans and unresolved findings are reported explicitly.
- **R64** [user] Static-analysis success never comes from suppressing, dismissing, marking false positive,
  excluding code, weakening tests, or changing quality-gate criteria merely to improve metrics.
  Coverage work uses meaningful observable-behavior tests. Frontend browser journeys are not counted
  as imported Sonar coverage unless a verified coverage path is implemented.
- **R65** [user] Root scripts build the complete project and three local Docker images: frontend only,
  backend only, and combined UI plus backend. Linux image build stages produce Linux-compatible
  artifacts on the local machine. Images are built for the machine's native architecture; any optional
  cross-platform command is documented only if verified. No image is pushed or published.
- **R66** [user] The combined image serves UI, API and live updates from one HTTPS origin reachable by
  another LAN device. Its public origin exactly matches the backend's configured public base URL;
  the TLS certificate and key are mounted at run time and trusted by the client devices. The backend
  remains private to the container, and the proxy forwards live updates without buffering. This path
  preserves secure cookies, anti-forgery and origin checks, and gives useful failure messages for
  missing configuration or an occupied port.
- **R67** [user] The combined image starts and stops both processes reliably, reports health and handles
  an unexpected child exit. The frontend-only image accepts an API upstream; the backend-only image
  accepts its exact public origin. Their responsibilities and settings are documented. A run script
  starts the locally built combined image with caller-supplied HTTPS settings.
- **R68** [user] The local guide gives exact script commands, image names and tags, ports, TLS and LAN
  setup, architecture limits, configuration, graceful stop, troubleshooting and the effect of a shared
  proxy address on per-address rate limits. The documented LAN flow is exercised through a real browser
  and backend where the environment permits; localhost-only proof is labelled as such.
- **R69** [user] `./scripts/local-deploy.sh [IPv4 | localhost]` validates the supplied IPv4 as the
  authoritative LAN address (or detects it), derives the public HTTPS origin using the configured web port,
  builds the backend JAR and web app if missing, and creates or reuses a per-IP certificate/key pair in
  `.local-tls/<IPv4>/`. Reuse requires an unexpired certificate. The script tells the operator to install and
  trust the certificate on each client device; it does not install trust or alter other devices. A
  `BATTLESHIP_PUBLICBASEURL` that conflicts with the derived origin fails. For a Render deployment the
  equivalent image is built by `scripts/build-docker-render.sh`.

## Agent-proposed requirements

| Id | Why | Cost | Cheaper alternative | Recommendation |
|---|---|---|---|---|
| R07 | Server admits one live game per browser; a closed tab otherwise locks the player out until expiry | One stored public id, one Home action, one journey step; one contract problem code + backend mapping + its proof for the unknown-game case | Explain the busy answer only (player cannot tell it from overload) | Now |
| R35 | Contract has no ruleset or ship name field | Translation keys per known id | Show raw ids | Now |
| R44 | An unannounced service-worker update can swap code mid-game | One prompt; reused from prior art | Update silently on next load | Now |
| R56 | "Real backend only" cannot prove a hand-written schema; it would duplicate the contract | None (removes work) | Hand-written runtime schemas | Now |

## Key Entities

- **Snapshot** — the server's caller-relative view of one game: phase, version, allowed actions, both
  boards, players, turn, last shot, expiry, invitation link (host, waiting), outcome and statistics.
- **Ruleset** — server-advertised board size, fleet and three rule flags.
- **Action** — a command with its action identifier, pending until the server answers.
- **Connection state** — connected, reconnecting or offline, per open game.
- **Preferences** — language, theme, emoji, sound, haptics; persisted.
- **Remembered game id** — the public id of the browser's current game (R07); persisted, not a secret.

## Success Criteria *(mandatory)*

- **S1** Two people in separate browsers can complete a full game under each advertised ruleset against
  the real backend, and each ruleset's distinct turn and revealed-water behaviour is visible.
- **S2** In every journey, the invitation secret never appears in the address after arrival, in
  history, in browser storage, or in any captured log.
- **S3** Expiry, server restart, a dropped live connection and a lost action answer each end in a
  screen that states what the server reported and a next step; no journey shows a stale board as current after
  recovery.
- **S4** At desktop, narrow portrait and short landscape sizes the full journey completes by keyboard,
  with no horizontal scrolling and no automated WCAG 2.1 AA violation rated serious or critical.
- **S5** Turning sound or haptics on, off, or denied never changes the outcome or timing of any journey.
- **S6** Offline, the previously loaded shell opens within 3 seconds and no game action is sent or
  queued.
- **S7** From a clean clone, the documented commands start development and production-style runs, and
  the single verification command passes end to end; a deliberately broken stage makes it fail.
- **S8** Screenshots of every screen, dialog and status overlay listed in R38 at phone portrait, phone
  landscape and laptop sizes in both themes are captured by the journeys, and the owner's comparison with
  the mockup is recorded.
- **S9** Both refreshed SonarQube projects have passing quality gates and no remaining open actionable
  issues, or each exception is reported with evidence and a decision instead of hidden.
- **S10** The complete build and three local image builds succeed, and the combined image completes a
  real same-origin HTTPS game flow from another LAN device where the environment permits; process
  shutdown and invalid setup produce the documented outcomes.

## Assumptions

1. The pinned toolchain and exact dependency versions are chosen in `plan.md` from the seed and from
   working prior art in the owner's projects; they are not part of this specification.
2. Browser journeys run in one desktop browser engine (Chromium). The backend's cookies are always
   Secure and `__Host-` prefixed; over plain `http://localhost` this relies on the browser treating
   localhost as a secure context, which not every engine does.
3. The backend's default public base address (`http://localhost:5173`) matches the development
   frontend origin; the production-style run sets it to its own origin through configuration.
4. The two documented rulesets are what the server currently advertises; the UI renders whatever the
   server lists.
5. A backend issue noticed while closing 002 — the forked test JVM lingers 30 s after the backend tests
   finish — is fixed under R57 when the root gate makes it visible.

## Extension: GitHub Actions pipeline

Owner decision 2026-11-07: the pipeline is an extension of this feature (no new feature id, no new branch);
constitution 2.1.0 permits it. The default branch is `master`; the `rewrite_prod_ready` and `feature/**` pull-request
targets are temporary until the rewrite merges. Workflows live in `.github/workflows/`, call the repository's own
scripts and `Dockerfile`, and add no script.

- **R70** [user] A push to any branch except `master` runs a build only: the backend JAR and the frontend `dist/`,
  with no tests and no gate. Tag pushes do not trigger it.
- **R71** [user] A pull request targeting `master`, `rewrite_prod_ready` or `feature/**` runs the build and then the
  full gate `./scripts/local-build.sh`. On failure the Playwright report, test results and backend test reports
  are kept as downloadable artifacts. Fork pull requests get a read-only token and no secrets.
- **R72** [user] A push to `master`, including the merge of `rewrite_prod_ready`, triggers nothing.
- **R73** [user] Pushing a tag `vX.Y.Z` (strict stable semantic version, no suffix) builds the Render image (`render`
  Dockerfile target, `linux/amd64`), runs the existing `scripts/build-docker-render.sh --smoke` checks, pushes it to
  GHCR with semantic-version tags, and creates a GitHub Release with generated notes, the image reference, its immutable
  digest and the Render instructions (the exact public HTTPS `BATTLESHIP_PUBLICBASEURL`, one instance, link to
  `docs/render-deployment.md`). Re-running the same tag is safe: an existing release is not duplicated and a changed
  digest is reported instead of silently overwritten. Deploying to Render stays manual.
- **R74** [agent] Every job declares its own least-privilege `permissions` (default none; `contents: write` and
  `packages: write` only in the release job); only `GITHUB_TOKEN` is used and no other secret exists.
- **R75** [agent] Third-party actions are pinned by full commit SHA with a version comment; checkouts do not persist
  credentials.
- **R76** [agent] Before building or pushing anything the release job proves the tagged commit is an ancestor of
  `origin/master`, and fails with a message naming the tag and commit otherwise.
- **R77** [agent] Every job has a timeout; runs are grouped by ref or tag so a superseded branch or pull-request run
  is cancelled, and a release run is never cancelled mid-push.
- **R78** [agent] `docs/ci.md` describes the three paths (branch build, PR gate, tag release), the release procedure, the required repository
  settings (workflow permissions, GHCR package visibility, branch protection with the gate as required check) and
  using the GHCR image in Render; it links to `docs/render-deployment.md` and `docs/configuration.md` instead of
  repeating them.
- **S11** Workflow files pass `actionlint` and `zizmor` clean; a deliberately unpinned action, a missing `permissions`
  block or a wrong tag pattern makes that check fail. Trigger behaviour on GitHub itself (PR, branch push, test tag)
  is verified by the owner after the first run and reported as such.

**Agent-proposed block (R74–R78) and its cost.** Aggregate cost: no extra files beyond the two workflows and
`docs/ci.md`; roughly 25 added workflow lines and one doc page; no dependency.

| Id | Why | Cost | Cheaper alternative | Recommendation |
|---|---|---|---|---|
| R74 | A compromised or buggy step cannot write packages or contents outside the release job | A few `permissions` lines | Repository default token permissions | Now |
| R75 | Mutable action tags are a supply-chain risk in a job that holds `packages: write` | SHA plus comment per action | Version tags | Now |
| R76 | A tag on a feature commit would publish an unreviewed image | One `git merge-base --is-ancestor` step | Convention only | Now |
| R77 | Prevents hung runners, wasted minutes and a half-pushed release | A few lines | Defaults | Now |
| R78 | The release steps and repository settings cannot be inferred from the workflow files | One page | Comments in the workflow | Now |

**Deferred, not built:** provenance and SBOM attestations, pre-release tags (`-rc`, `-beta`), a Playwright browser
cache, automatic retries, re-running the gate on release (the release trusts that the commit on `master` passed the
pull-request gate; branch protection with a required check is the recommended mitigation), Dependabot.

## Out of scope

Everything the constitution's launch baseline excludes (database, accounts, chat, spectators, rankings,
payments, analytics, ads, offline play, command queues, scaling, deployment, hosting). Local-only
Docker build and run scripts under R65–R68 and the GitHub Actions pipeline under R70–R78 are inside this feature;
neither establishes deployment or hosting, and deploying to Render stays manual. Also: automated
pixel comparison, a cross-browser/device matrix, load testing, and the capabilities the contract defers —
claiming a win against a vanished opponent, rematch, a list of my games, recovering a join whose answer
was lost, conditional reads and salvo rules.
