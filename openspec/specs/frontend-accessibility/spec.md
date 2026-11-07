# frontend-accessibility Specification

## Purpose
Defines the three responsive layouts and inclusive interaction: keyboard, touch, mouse, screen reader, zoom, forced colours, reduced motion, labelled and focus-managed dialogs and popups, and hover independence (US6, R39-R41, R61, S4). Source: the original workflow feature `003-frontend`.

## Requirements

### Requirement: Inclusive use across layouts and input modes (US6)

A full journey SHALL complete at desktop, narrow portrait and short landscape sizes by keyboard only, by touch only and with an accessibility scanner. At desktop, narrow portrait and short landscape sizes the full journey SHALL complete by keyboard with no horizontal scrolling and no automated WCAG 2.1 AA violation rated serious or critical (S4).

#### Scenario: Narrow portrait

- **WHEN** the screen is narrow portrait
- **THEN** one board shows at a time behind a keyboard-operable tab control while turn and action status stay visible

#### Scenario: Short landscape

- **WHEN** the screen is short landscape
- **THEN** both compact boards stay usable without horizontal scrolling

#### Scenario: Input modes

- **WHEN** the device is touch-only or mouse-only
- **THEN** every action of the journey is reachable and none depends on hover

### Requirement: Three layouts (R39)

The client SHALL choose three layouts by available size as in the mockup: wide shows both boards side by side with turn, players and connection; narrow shows one board behind a keyboard-operable Enemy waters / Your fleet tab control with turn and action status outside the tabs; short landscape shows two compact boards without tabs. No layout SHALL scroll horizontally or hide an essential control.

#### Scenario: Viewport matrix

- **WHEN** the viewport is 390x844, 844x390 or 1280x800
- **THEN** the matching layout shows with no horizontal overflow

### Requirement: Every essential action works in every mode (R40)

Every essential action SHALL work by keyboard, touch, mouse and screen reader, at browser zoom, in forced colours, with reduced motion (no animation plays) and with enlarged text. Board cells SHALL have coordinate labels and state descriptions.

#### Scenario: Cell label

- **WHEN** a screen reader reaches a board cell
- **THEN** it announces the coordinate and state, for example "A1, hit"

#### Scenario: Forced colours

- **WHEN** forced colours suppress background images
- **THEN** water, ship and miss remain distinguishable through patterns and accessible names

### Requirement: Dialogs and popups manage focus (R41)

Every modal dialog SHALL be labelled, trap and restore focus, and close on Escape where dismissal is safe. Placement popups SHALL be nonmodal and labelled; opening one SHALL move focus into it, and Escape or Cancel SHALL dismiss it and restore focus to its opener. Outside interaction SHALL dismiss it without preventing focus from moving to the chosen control. Popups SHALL stay visible near viewport edges without clipping or obscuring essential controls.

#### Scenario: Settings dialog

- **WHEN** the Settings dialog opens, Escape is pressed
- **THEN** focus stayed inside while open, the dialog closes and focus returns to its opener

#### Scenario: Unsafe dismissal

- **WHEN** an action is pending in a confirmation
- **THEN** Escape does not dismiss it

### Requirement: No dependence on hover; consistent pointer input (R61)

Mouse, touch and pen SHALL reach the same actions, and no action or information SHALL depend on hover. Controls SHALL be at least the mockup's control height (2.75rem) with `touch-action: manipulation`; board cells SHALL share the available width.

#### Scenario: Control size

- **WHEN** a Home control is measured
- **THEN** its minimum height is 2.75rem
