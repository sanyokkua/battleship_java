## Purpose

Defines fleet placement for both players: per-cell and per-ship actions, mouse drag and touch selection, atomic move and rotate, advisory checks, the server's blocked-cell hint, refusal wording and Ready confirmation (US3, R17, R18, R62). Source: the original workflow feature `003-frontend`.

## ADDED Requirements

### Requirement: Both players place their fleets (US3)

Players SHALL be able, for each ruleset, to place, move, rotate and remove ships, use random arrangement and clear, then ready. Each board shown SHALL equal the server's answer and illegal moves SHALL show the server's refusal reason.

#### Scenario: Cell and ship menus

- **WHEN** the player activates an empty cell
- **THEN** a compact anchored popup offers the available ships and only orientations valid at that cell
- **WHEN** the player activates a placed ship
- **THEN** a cell-adjacent menu offers move, remove and legal rotation, move visually lifts the ship but preserves its server placement until one atomic command succeeds, cancel restores it, and the actions are reachable by keyboard, touch selection and mouse drag

#### Scenario: Pending and refused actions

- **WHEN** a ship action is pending
- **THEN** that action is disabled and the board changes only to the server's answer
- **WHEN** the server refuses (out of bounds, overlap, touching)
- **THEN** the refusal is explained in words and the board stays as the server last reported it

#### Scenario: Ready

- **WHEN** the server offers Ready and the player readies and confirms that the fleet is then locked
- **THEN** the readiness is shown and is not undoable
- **WHEN** Ready is not offered
- **THEN** the UI says why (fleet incomplete, or already ready and waiting for the opponent)

#### Scenario: Drag and touch

- **WHEN** the player drags an unplaced ship from the fleet tray or a placed ship onto the board by mouse
- **THEN** a compact direction chooser offers only valid directions or explains that no direction fits, single-cell ships need no chooser, touch can select a ship then a cell without accidental drag during scrolling, and the board shows the server's answer

### Requirement: Placement actions and advisory checks (R17)

Placement SHALL offer per cell: place an unplaced ship; per placed ship: move, remove, and rotate only if the alternative orientation is valid; and random arrangement, clear and ready when allowed. Move and rotate SHALL each be one atomic placement, never remove-then-place; cancellation or rejection SHALL preserve the last server placement. Single-cell ships SHALL need no orientation choice and a failed placement SHALL be explained.

#### Scenario: Moving ship's vacated footprint

- **WHEN** a Classic ship that may touch others is moved or rotated
- **THEN** only the moving ship is excluded by identity and a legal direction into its vacated footprint is offered

#### Scenario: Race on the server

- **WHEN** the server answers 409 to a move
- **THEN** the original ship is retained and progress is updated

#### Scenario: Advisory checks

- **WHEN** a destination is evaluated
- **THEN** advisory checks consider board bounds, occupancy, the ruleset's contact rule and the exact ship being moved (by `shipId`), and the server validates the command

### Requirement: Drag and touch reach the same single action (R18)

Mouse drag placement and touch ship-then-cell selection SHALL reach the same single action as the keyboard path. A multi-cell drop SHALL offer its valid directions, and a no-fit drop SHALL leave the ship available with a way to choose another cell. Touch scrolling SHALL NOT start a drag. Placement popups SHALL use green ship shapes and counts, with the target cell and shape in the drop chooser header, and SHALL omit the redundant Choose cell action and explanatory direction footer.

#### Scenario: Scrolling by touch

- **WHEN** the player scrolls by touch over the tray or board
- **THEN** no drag preview starts

#### Scenario: No ship fits

- **WHEN** a ship is dropped on a cell where no direction fits
- **THEN** "No ship fits here." is shown and the ship stays available

### Requirement: Server-projected blocked cells (R62)

`[user]` During `PLACEMENT` the caller's snapshot SHALL include `placementBlockedCells`, the in-bounds empty cells adjacent (including diagonally) to placed ships when the ruleset forbids contact, and the frontend SHALL render those cells as unavailable, including in their accessible names. It SHALL NOT treat this hint as a complete placement oracle.

#### Scenario: Sea Battle blocked cells

- **WHEN** a ship is placed under a no-touch ruleset
- **THEN** its neighbouring empty cells show the diagonal hatch and an unavailable-for-placement name

#### Scenario: Contact allowed

- **WHEN** the ruleset allows contact
- **THEN** `placementBlockedCells` is empty and nothing is marked

#### Scenario: Outside placement

- **WHEN** the phase is not `PLACEMENT`
- **THEN** the field is absent

#### Scenario: Moving a ship

- **WHEN** a ship is being moved
- **THEN** the restrictions shown come from other ships only, excluding the moving ship's footprint and vacated halo
