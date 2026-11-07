## Purpose

Defines the installable shell, its static-only offline cache, the honest offline state and consented updates (US7, R42-R44, S6). Source: the original workflow feature `003-frontend`.

## ADDED Requirements

### Requirement: The installed app opens offline honestly (US7)

After the built app has been loaded once, going offline and reopening SHALL open the shell with a clear "network required" state and SHALL NOT queue any game action. Offline, the previously loaded shell SHALL open within 3 seconds and no game action SHALL be sent or queued (S6).

#### Scenario: Offline open

- **WHEN** the app opens without a network after one load
- **THEN** the shell renders and says games need a connection, and game actions are disabled, not queued

#### Scenario: New version

- **WHEN** a new version is deployed and the app detects it
- **THEN** the player is asked before it updates

### Requirement: Installable shell with static-only cache (R42)

The app SHALL be installable; its offline cache SHALL hold only the static shell and assets, never API answers, live updates, invitation links or game state, and it SHALL NOT queue an action.

#### Scenario: Cache content

- **WHEN** the caches are inspected
- **THEN** none holds an `/api` URL

### Requirement: Offline state disables game actions (R43)

Offline, the shell SHALL state that games need a connection and SHALL disable game actions, including fire controls and Refresh.

#### Scenario: Offline on a game screen

- **WHEN** the browser goes offline on a game screen
- **THEN** fire controls and Refresh are disabled and no request is sent

### Requirement: Update only with consent (R44)

`[agent]` A newer version SHALL be applied only after the player agrees.

#### Scenario: Update prompt

- **WHEN** `dist/` is rebuilt while the same origin keeps serving
- **THEN** an update prompt appears and the code is not swapped until the player accepts
