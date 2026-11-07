## Purpose

Defines creating a game, sharing the invitation, joining by link, invitation privacy, the remembered current game and presence (US1, US2, R07-R16). Source: the original workflow feature `003-frontend`.

## ADDED Requirements

### Requirement: Host creates a game and shares the invitation (US1)

A person SHALL see the rulesets the server offers, pick one and a name, and receive one link to send. Independent test: in a fresh browser create a game with each advertised ruleset and confirm the waiting room shows that ruleset's facts, the invitation lifetime, and working copy-link and QR actions.

#### Scenario: Rulesets listed before submission

- **WHEN** the create screen loads
- **THEN** every ruleset the server advertises is shown as a compact selectable name with fleet badges (count x ship length), and View details exposes board size, fleet, touching rule, extra-turn rule and revealed-water rule before submission

#### Scenario: Single creation request

- **WHEN** the host submits a valid name and ruleset
- **THEN** submit is disabled while pending, exactly one creation request is sent and the waiting room opens only from the server's answer

#### Scenario: Sharing from the waiting room

- **WHEN** the host chooses Copy link or Show QR
- **THEN** both carry exactly the invitation link from the host's current view, with success or failure feedback, and the secret part never appears as visible text

#### Scenario: Waiting room after reload

- **WHEN** the waiting room view loads after a reload
- **THEN** sharing works again from the server's view; and when the view carries no invitation link the room says the invitation expired and sharing is unavailable

#### Scenario: Replace invitation

- **WHEN** the host chooses Replace invitation and confirms that the old link stops working
- **THEN** the new link from the server's answer is shared from then on

### Requirement: Guest opens the link and joins (US2)

A guest SHALL be able to open a host's link in a second isolated browser, see the secret leave the address bar before any action, join explicitly, and reach fleet placement together with the host.

#### Scenario: Secret removed before anything is redeemed

- **WHEN** an invitation link opens
- **THEN** the secret is removed from the address and history before the page renders and nothing is redeemed until the guest activates Join game

#### Scenario: Two browsers race

- **WHEN** two browsers open the same link and both join
- **THEN** exactly one becomes the guest and the other sees a safe "invitation unavailable" outcome

#### Scenario: Host follows the guest

- **WHEN** a guest has joined and the host's view updates
- **THEN** the host moves to fleet placement with no action of its own

### Requirement: Remembered current game (R07)

`[agent]` The public id of the browser's current game SHALL be remembered across tab closes so Home offers Return to your game, and SHALL be forgotten when the server reports the game gone or after leaving. A refusal for a live game the client does not know SHALL be distinguishable from "server busy" and SHALL NOT be retried (contract and backend amended under R57).

#### Scenario: Return to your game

- **WHEN** the browser already holds a live game and the player opens Home
- **THEN** Home offers Return to your game instead of a refused second create or join

#### Scenario: Unknown live game

- **WHEN** storage is cleared and a second create is refused with `browser-game-limit`
- **THEN** the message says the player can play again once that game expires and the request is not retried

#### Scenario: Refusal wording

- **WHEN** the browser holds a live game the client does not know (lost creation answer, cleared storage)
- **THEN** the refusal tells the player they can play again once that game expires

### Requirement: Invitation secret read once from the address (R08)

On arrival with an invitation link, the client SHALL read the secret once into memory and remove it from the address and history before rendering anything shareable.

#### Scenario: Back navigation

- **WHEN** the guest navigates away and back
- **THEN** the URL still lacks the secret

### Requirement: Redemption only on explicit Join (R09)

Redemption SHALL happen only on explicit Join game activation, never on load, prefetch, QR scan or preview.

#### Scenario: Page loaded without action

- **WHEN** the join page is loaded and the guest does nothing
- **THEN** no join request is sent

### Requirement: Secret never exposed (R10)

The invitation secret SHALL NOT be rendered as text, stored, logged or sent anywhere except the join request, and SHALL be dropped from memory once the join settles. In every journey the secret SHALL NOT appear in the address after arrival, in history, in browser storage or in any captured log (S2).

#### Scenario: Secret scan

- **WHEN** a journey completes a join
- **THEN** a scan of URL, history, `localStorage`, `sessionStorage`, console and captured stack output finds no secret, `Set-Cookie` or cookie value

### Requirement: Copy and QR use the server's link (R11)

Copy link and Show QR SHALL use exactly the invitation link in the host's current view; the client SHALL NOT build one.

#### Scenario: Link field

- **WHEN** the waiting room shows the link field
- **THEN** everything after `#invite=` is masked and the full link is never in the DOM

### Requirement: Invitation status, expiry and replacement (R12)

While waiting, the host SHALL see invitation status and expiry. The invitation SHALL count as expired when the host's view has no invitation link, and the client SHALL NOT compare clocks to decide it. Replace invitation SHALL be offered whenever the server allows it and SHALL be sent only after the host confirms that the current link stops working.

#### Scenario: Invitation lapses while waiting

- **WHEN** the invitation expires while the game is still waiting
- **THEN** the server's view has no link, the room shows "invitation expired" without a reload, and Replace invitation restores sharing

### Requirement: Creation sent once (R13)

Creation SHALL be sent once per deliberate submit and SHALL NOT be resent automatically; an unknown outcome SHALL be explained and the player SHALL be able to return Home, where R07 applies.

#### Scenario: Creation answer lost

- **WHEN** the creation answer is lost
- **THEN** the player is told the outcome is unknown, no second create is sent, and if the server did create the game this browser cannot start another until that orphan expires and the refusal says so

### Requirement: Host moves to placement on the guest joining (R14)

The host SHALL move from waiting to placement only when a newer snapshot shows the guest joined.

#### Scenario: No guest yet

- **WHEN** no newer snapshot shows a guest
- **THEN** the host stays in the waiting room

### Requirement: Join conflict shows one outcome (R15)

A join refused with a conflict SHALL show one "invitation unavailable" outcome because the contract does not distinguish its causes; a gone answer SHALL follow R27.

#### Scenario: Join answer lost

- **WHEN** the join answer was lost before the session arrived and the guest retries
- **THEN** the guest sees invitation unavailable (unrecoverable by contract)

#### Scenario: Join 410

- **WHEN** a join is answered 410
- **THEN** it follows the game-expired outcome and not invitation unavailable

### Requirement: Presence at the published interval (R16)

While the page is visible and the server allows presence, the client SHALL send presence at most once per the server's published presence interval; hidden tabs, reconnects and heartbeats SHALL send none. A changed expiry SHALL be shown; an unchanged one SHALL NOT be an error.

#### Scenario: Hidden tab

- **WHEN** the tab is hidden
- **THEN** no presence is sent and the timer restarts, not fires, on return
