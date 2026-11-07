# frontend-shell Specification

## Purpose
Defines the application surfaces, header, settings, preferences, translations, dialogs availability and the visual design taken from the Naval Command Center mockup (US6 settings, R33-R35, R37, R38, R58-R60, S8). Source: the original workflow feature `003-frontend`.

## Requirements

### Requirement: Settings, rules and preferences apply immediately (US6)

Changing language (English/Ukrainian), theme (dark/light/system), emoji, sound or haptics on any screen SHALL apply immediately, survive a reload and never interrupt the game. Independent test: run one full journey at desktop, narrow portrait and short landscape sizes using keyboard only, by touch only and with an accessibility scanner; switch language, theme, emoji, sound and haptics mid-game; compare captured screens with the mockup.

#### Scenario: Mid-game switch

- **WHEN** the player switches to Ukrainian and the light theme
- **THEN** it applies without reload and survives a reload

#### Scenario: Denied feedback

- **WHEN** sound or haptics are denied or unsupported
- **THEN** play and results are unaffected

#### Scenario: Mockup parity

- **WHEN** each surface is shown at phone portrait, phone landscape and laptop sizes in dark and light themes
- **THEN** it shows the widgets R59 lists for it, styled with the mockup's tokens

### Requirement: Persisted preferences only (R33)

Language (English, Ukrainian), theme (dark, light, system), emoji mode, sound and haptics SHALL apply immediately and persist; nothing else SHALL be persisted except the game id of R07. Emoji SHALL be decorative and never the only carrier of meaning. `[user]` Emoji mode SHALL be separate and off by default; when enabled, emoji MAY appear only in a transient firing or miss/empty-cell animation, with no static emoji elsewhere in the UI.

#### Scenario: Corrupt storage

- **WHEN** the stored preferences are corrupt or `localStorage` throws
- **THEN** defaults are used

### Requirement: Translation key parity (R34)

English and Ukrainian resources SHALL have identical keys (Constitution V).

#### Scenario: Missing key

- **WHEN** a key exists in one locale only
- **THEN** the parity test fails

### Requirement: Ruleset and ship names by identifier (R35)

`[agent]` Ruleset and ship names SHALL be translated by the server's ruleset and ship-type identifiers, falling back to the raw identifier for an unknown one, because the contract has no name field.

#### Scenario: Unknown identifier

- **WHEN** a ruleset or ship identifier has no translation
- **THEN** the raw identifier is shown

### Requirement: Rules and settings from any screen (R37)

Rules and settings SHALL open as dialogs from any screen without leaving the game.

#### Scenario: Rules mid-game

- **WHEN** the player opens Game rules during play
- **THEN** the dialog opens over the game and closing it returns to the same view

### Requirement: Surfaces and header (R38)

The surfaces SHALL be Home, Create game, Join invitation, Waiting room, Fleet placement, Gameplay, Results, Session unavailable, plus dialogs and status overlays (rules, settings, QR, confirmations for ready, replace invitation, leave and resign, checking, error), placement popups and toasts. A header on every surface SHALL offer Refresh (reads the open game from the server) and Settings.

#### Scenario: Refresh

- **WHEN** the player chooses Refresh on a game surface
- **THEN** the game is read from the server through the version gate

#### Scenario: Home and Settings

- **WHEN** Home is shown
- **THEN** it offers create, Return to your game (R07), rules and settings, and Settings offers Resign match while the server allows resigning

#### Scenario: Screenshots (S8)

- **WHEN** the journeys run
- **THEN** every screen, dialog and status overlay is captured at phone portrait, phone landscape and laptop sizes in both themes and the owner's comparison with the mockup is recorded

### Requirement: Mockup design tokens (R58)

Styling SHALL use the mockup's semantic design tokens for both themes: surfaces, text, borders, accent, danger, warning, success, sea, ship, miss, hit, sunk, focus ring, radii, spacing, control heights and motion durations. Theme `system` SHALL follow the operating system's colour scheme.

#### Scenario: System theme

- **WHEN** the theme is System and the OS scheme changes
- **THEN** `html[data-theme]` flips without reload

#### Scenario: Accent token

- **WHEN** a theme is active
- **THEN** `--accent` equals `#3becb0` in dark and `#1d7fa8` in light

### Requirement: Per-surface widgets from server data (R59)

Each surface SHALL present the mockup's widgets for it, fed only by server data; the per-surface list and each widget's data source are in the design's UI surface contract. `[user]` Board water and ship cells SHALL have no repeated visible text glyphs; non-colour state cues SHALL remain available through cell patterns, hit/sunk marks, accessible names and live announcements. Emoji mode SHALL be limited to the transient firing/miss animation of R33.

#### Scenario: Waiting room widgets

- **WHEN** the waiting room is shown
- **THEN** it shows the five ruleset facts, stepper, connection pill, masked link field, Copy link, Show QR, lifetime countdown and meter, notice, Replace invitation and Leave game

### Requirement: Mockup elements not reproduced (R60)

The client SHALL NOT reproduce the mockup's invitation token text, Copy token, a paste-token join field, Join game on Home (joining is by link or QR only), a display game number, the snapshot-version footer, or the mockup's preview, simulation and demo controls.

#### Scenario: Home

- **WHEN** Home is shown
- **THEN** it has no Join game control
