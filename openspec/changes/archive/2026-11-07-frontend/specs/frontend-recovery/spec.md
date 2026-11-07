## Purpose

Defines live updates, connection state, recovery after drops, leaving and resigning, expiry and server restart, so that each ends in an honest, recoverable screen (US5, R24-R30, S3). Source: the original workflow feature `003-frontend`.

## ADDED Requirements

### Requirement: Leaving, expiry, restart and connection loss are understandable (US5)

Against the real server, leaving before play, resigning during play, expiry under a short configured idle time, server restart mid-game and a cut live connection SHALL each end in an honest, recoverable screen. Expiry, restart, a dropped connection and a lost action answer SHALL each end in a screen that states what the server reported and a next step, and no journey SHALL show a stale board as current after recovery (S3).

#### Scenario: Leave before play

- **WHEN** a player in waiting or placement leaves and confirms
- **THEN** the game is abandoned and the leaver returns Home; cancelling keeps the view

#### Scenario: Opponent left

- **WHEN** the opponent left before play and the update arrives
- **THEN** the remaining player sees the game was abandoned with no boards revealed and can leave the result

#### Scenario: Resign

- **WHEN** a player chooses to leave during play
- **THEN** they are told it means resigning and after confirmation the opponent wins

#### Scenario: Expired or unknown game

- **WHEN** the game expired and the player acts or the view refreshes while the server still remembers it
- **THEN** Session unavailable says the game expired
- **WHEN** the server restarted or no longer knows the game or session
- **THEN** it says the game is no longer available and that a server restart is one possible cause; both offer a new game and nothing claims the game can be resumed

#### Scenario: Connection drop

- **WHEN** the live connection drops or the tab returns from background
- **THEN** the connection state is shown, the view is refreshed from the server and no completed action is repeated

#### Scenario: Lost answer retry

- **WHEN** an action's answer was lost and the player retries
- **THEN** the same action is resent as the same action, never as a new one

### Requirement: Live stream and connection state (R24)

While a game screen is open the client SHALL listen to the game's live update stream and SHALL show connection state: connected, reconnecting, offline; while an action's outcome is unknown the view SHALL show checking.

#### Scenario: Offline

- **WHEN** the browser goes offline
- **THEN** the stream closes, the state shows offline and game actions are disabled

#### Scenario: Network error while online

- **WHEN** a network error occurs while `navigator.onLine` is true
- **THEN** the state is reconnecting, not offline

### Requirement: Recovery without repeating actions (R25)

When the stream drops or the tab returns from background, the client SHALL read the game from the server and reopen the stream if the read succeeds. It SHALL NOT repeat a completed action. A failed read without a definite problem SHALL be retried with backoff (1, 2, 4, then every 8 seconds) until it succeeds or returns 401, 404 or 410, and a stream that fails again after a successful read SHALL reopen on the same backoff reset by its first received event.

#### Scenario: Refused stream open

- **WHEN** stream opens are refused by a lowered limit
- **THEN** reopening backs off and never loops tightly

### Requirement: Stream closed by the server (R26)

When the server closes the stream because another tab took over, the client SHALL report it and SHALL NOT reconnect; when it closes it because the game is unavailable, the client SHALL read the game once and SHALL NOT reopen the stream.

#### Scenario: Second tab

- **WHEN** a second tab in the same context opens the game
- **THEN** the first shows "open in another tab" and opens no new stream

### Requirement: Session unavailable distinguishes only what the server distinguishes (R27)

Session unavailable SHALL distinguish only "game expired" (the gone answer) versus "no longer available - the game ended, you left, or the server restarted" (session-required or not-found answers). It SHALL offer Home and new game, SHALL clear game-specific presentation state, and SHALL NOT name a cause the answer does not carry.

#### Scenario: Retention elapsed

- **WHEN** the game has expired and the retention period then elapses
- **THEN** the screen first says expired and later says no longer available

### Requirement: Leave from waiting or placement (R28)

Leave from waiting or placement SHALL ask for confirmation, then send leave; Home SHALL be shown only after the server accepts or reports the caller already left (not-found after leave is success, not Session unavailable).

#### Scenario: Already left

- **WHEN** leave is answered 404 after the caller's own leave
- **THEN** Home is shown

### Requirement: Leave during play is resigning (R29)

Leave during play SHALL explain that it means resigning and SHALL send resign after confirmation. Settings SHALL offer Resign match while the server allows resigning.

#### Scenario: Resign dialog

- **WHEN** the player opens Resign match
- **THEN** the dialog says it means resigning and sends `RESIGN` only after confirmation

### Requirement: Leave results ends access (R30)

Leave results SHALL end the caller's access to a finished or abandoned result and return Home, forgetting the remembered game id.

#### Scenario: Leave results

- **WHEN** the player leaves a finished result
- **THEN** `leaveGame` is sent, Home is shown and the remembered id is cleared
