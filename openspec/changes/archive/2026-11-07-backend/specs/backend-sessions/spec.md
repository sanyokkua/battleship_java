## Purpose

Anonymous identity, authorization, invitations and request security: the browser session, indistinguishable refusals, the invitation secret and join ordering, anti-forgery, cross-site refusal, strict request bodies and display-name validation. Source: spec 002-backend R32-R37 and R64. Covers User Stories US1 (create and share) and US2 (join), success criterion S6 and proof area 7 (request security and authorization), and the Key Entities Session, Invitation and Player. Constitution II applies (no credential in URL, log or script-readable storage).

## ADDED Requirements

### Requirement: Anonymous script-unreadable browser session (R32)

Identity SHALL be an anonymous, script-unreadable browser session, issued when a browser creates or joins its first game and reused for every game of that browser. Its value SHALL be high-entropy, generated from a cryptographically secure source, and the server SHALL retain only a digest of it so a copy of server memory yields no usable session.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** No credential SHALL ever appear in a URL, a game locator, a snapshot, a log or script-readable storage. The cookie is `__Host-battleship_session` (`HttpOnly; Secure; SameSite=Strict; Path=/`, no `Domain`, 256-bit value, SHA-256 digest stored).

#### Scenario: Create game issues a session

- **WHEN** a browser with no session creates a game with a valid ruleset and name
- **THEN** it becomes host of a `WAITING` game, receives an anonymous session and the snapshot carries the invitation link and expiry (US1-1)

#### Scenario: Session reuse

- **WHEN** the same browser creates or joins another game
- **THEN** its existing session is reused

### Requirement: Authorization and indistinguishable refusals (R33)

A game identifier SHALL locate a game and grant nothing. Authentication SHALL be evaluated first: a caller with no session, or an unknown session, receives `session-required`. A caller with a valid session who is not one of a game's players SHALL receive the same answer as for a game that never existed, indistinguishable in status, body and timing-independent content.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** `createGame` and `joinGame` SHALL never answer `session-required` since they issue a session. Success criterion S6: a non-player cannot distinguish a game from one that never existed, by status, body or disclosed detail, and the same holds for every refused join.

#### Scenario: No session

- **WHEN** a protected operation is called without a valid session
- **THEN** the answer is `401 session-required` and the cookie is cleared

#### Scenario: Stranger versus unknown game

- **WHEN** a valid-session non-player reads a real game and then a never-existing game
- **THEN** both answers are identical `404 game-unavailable`

### Requirement: Invitation secret and join ordering (R34)

The invitation secret SHALL be high-entropy from a cryptographically secure source. It SHALL travel only in the link fragment and the join request body, be consumed atomically by the first successful join, and be invalidated by replacement, abandonment, expiry or use. Membership SHALL be checked before the secret.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** A browser already holding this game's guest seat SHALL receive the current state with any schema-valid secret while the game is live, so a reload after replacement never locks the guest out. If that game expired and is still remembered, the existing guest SHALL receive `410 game-expired`. Every other join refusal (unknown or forgotten game, wrong, used, replaced or expired secret, seat taken, the host itself, or an expired game for a non-guest) SHALL be `409 invitation-unavailable`. The invitation digest is compared in constant time.

#### Scenario: First redeem

- **WHEN** a second browser joins a valid, unused, unexpired invitation with a name
- **THEN** the game moves to `PLACEMENT`, that browser receives a session and both see each other's names (US2-1)

#### Scenario: Second redeem from a third browser

- **WHEN** a used invitation is redeemed again by another browser
- **THEN** it is refused `409 invitation-unavailable` identically to a wrong secret

#### Scenario: Existing guest re-joins

- **WHEN** the existing guest joins again with any schema-valid secret
- **THEN** it receives the current snapshot and nothing changes (US2-2)

#### Scenario: Opened but not confirmed

- **WHEN** a link is opened but not confirmed
- **THEN** no seat is claimed and the invitation stays usable until expiry (US2-3)

#### Scenario: Expired game retention

- **WHEN** an existing guest rejoins an expired but retained game
- **THEN** the answer is `410 game-expired`, while a visitor who never held the seat gets `409 invitation-unavailable` (US2-4)

#### Scenario: Host replaces invitation

- **WHEN** the host replaces the invitation in `WAITING`
- **THEN** the new link is in the snapshot and the previous link stops working immediately (US1-2)

### Requirement: Anti-forgery token (R35)

Every state-changing request SHALL echo the readable anti-forgery token (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header, raw value, `Secure`, `SameSite=Strict`, `Path=/`) that the service information request establishes; a missing or wrong token SHALL be refused as `403 request-security-rejected`. The CSRF check is evaluated before the session check on unsafe methods.

#### Scenario: Missing token

- **WHEN** an unsafe request omits the token header
- **THEN** it is refused `403 request-security-rejected`

#### Scenario: Meta establishes token

- **WHEN** `getMeta` is called
- **THEN** the response always sets the anti-forgery cookie

### Requirement: Cross-site refusal for browsers only (R36)

The service SHALL additionally refuse state-changing requests whose browser-set `Origin` and fetch-metadata headers show a cross-site source. Absent headers SHALL NOT be a refusal: a non-browser caller is admitted and still has to present the anti-forgery token. The interface and API share one origin so cross-origin sharing SHALL NOT be configured at all.

#### Scenario: Cross-site request

- **WHEN** an unsafe request carries `Sec-Fetch-Site: cross-site`
- **THEN** it is refused `403 request-security-rejected`

#### Scenario: Script without origin headers

- **WHEN** an unsafe request has no origin or fetch-metadata headers but a valid token
- **THEN** it is admitted

### Requirement: Strict request bodies and no-store responses (R37)

Requests SHALL reject unknown fields so a caller can never supply a seat, a turn, a winner or a result. Field-level failures SHALL be reported per field with a machine-readable rule (JSON Pointer `field` and `rule`, `422 validation-failed`). Every response SHALL be marked not to be stored by caches (`Cache-Control: no-store`).

#### Scenario: Unknown field

- **WHEN** a body contains an unknown field
- **THEN** the answer is `422 validation-failed` with rule `UNKNOWN_FIELD`

### Requirement: Display name validation (R64)

A display name containing any control, bidirectional-override or invisible formatting character (except the zero-width joiner used by emoji) SHALL be rejected, including at its edges. Otherwise it SHALL be trimmed of surrounding whitespace and normalised to Unicode NFC before length validation; length SHALL be counted in code points after normalisation within the bounds of the contract's `DisplayName` schema.

#### Scenario: Further rules of this requirement

- **WHEN** this requirement applies
- **THEN** A failing name SHALL be a field-level validation failure (R37) and no game or seat is created.

#### Scenario: Control character

- **WHEN** a name contains a control or bidi-override character
- **THEN** a 422 field failure is returned and nothing is created

#### Scenario: Normalisation

- **WHEN** a name has surrounding whitespace and decomposed characters
- **THEN** it is trimmed and NFC-normalised before length is counted

### Requirement: Proof area 7 - request security and authorization

Proof area 7 (owners `RequestSecurityIT`, `AuthorizationIT`, `RateLimitIT`, `app`) SHALL prove session issuance and reuse, the anti-forgery check, the cross-site refusal, a non-browser caller admitted without origin headers, non-players and strangers receiving the indistinguishable answer, and every refused join answering identically.

#### Scenario: Proof exists

- **WHEN** the integration tests run
- **THEN** each listed behaviour is asserted and fails when removed
