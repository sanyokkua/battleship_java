# Feature Specification: QR Session Sharing

**Feature Branch**: `feature/qr-session-sharing`

**Created**: 2026-08-16

**Status**: Ready for implementation

**Input**: User description: "Add QR code generation to share the session. Use the approved `docs/superpowers/specs/2026-08-16-qr-session-sharing-design.md` design as the basis for the specification."

**Spec Kit Artifacts**: `specs/001-qr-session-sharing/`

## Scope and Context

### Problem

A player who has created a game session and is waiting for an opponent can
currently copy the raw session ID or a join link. On a second device, manually
copying or typing that information is inconvenient and error-prone. The
waiting room needs a QR representation of the existing join destination so an
opponent can scan it and arrive at the correct join screen.

The QR code is an alternative representation of the existing join link. It
must not introduce a second invite format, change session validity, or require
a server-side sharing feature.

### In Scope

- A `Show QR code` action in the waiting-room share card beside the existing
  Copy ID and Copy link actions.
- An on-demand QR presentation using the existing accessible overlay behavior.
- Browser-side generation and successful rendering of a QR code from the canonical absolute
  join URL, including loading and failure states.
- English and Ukrainian labels and status messages with matching translation
  keys.
- Focused acceptance checks for URL correctness, lazy generation,
  popup behavior, dismissal, failure handling, and preservation of existing
  sharing actions.

### Out of Scope

- Server-side endpoints or service-contract changes, session-state changes,
  persistence, authentication, or a new route.
- QR downloads, image export, QR customization, or error-correction controls.
- QR actions on preparation, gameplay, or results screens.
- Additional copy or sharing controls inside the QR presentation.
- Changes to how sessions are created, joined, expired, or authorized.
- QR-specific masking, encryption, expiry, or authorization changes for the
  session identifier. The identifier is intentionally shareable through the
  existing Copy link and QR destinations; existing session validation and
  authorization remain authoritative.

## Clarifications

### Session 2026-08-16

- Q: What should determine the public deployment path used in both Copy link and QR URLs when the app is hosted below the domain root? → A: Option A — Use one explicit application base-path setting shared by the router and URL helper.
- Q: Should the session ID be treated as an intentionally shareable capability with no additional QR-specific privacy or security controls? → A: Option A — Treat the session ID as intentionally shareable and rely on existing session validation and authorization.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Show a session QR code on demand (Priority: P1)

As a player waiting for an opponent, I want to open a QR code for my current
game so that my opponent can scan it without manually entering the session ID.

**Why this priority**: This is the smallest valuable end-to-end slice: it
adds the requested sharing action while reusing the existing waiting-room
session and join flow.

For this feature, a valid waiting-room session is an existing guarded
waiting-room context with a non-empty session ID, a present player identity,
the reachable waiting status `WAITING_FOR_PLAYERS`, and resolved initial
waiting-room loading. The existing waiting-room guard and lifecycle remain
the authority for validity; the QR action is withheld for missing session or
player data, any other status, or a defensive render without a valid context.
A valid-session acceptance fixture is evaluated after its initial waiting-room
loading state has resolved.

**Independent Test**: A focused waiting-room acceptance check and a browser
journey can render a valid waiting session, activate `Show QR code`, and prove
that the resulting presentation contains the completed QR code and scan
instruction.

**Acceptance Scenarios**:

1. **Given** a player is in the waiting room with a valid session, **When**
   the player selects `Show QR code`, **Then** an accessible QR presentation
   opens and begins generation.
2. **Given** QR generation succeeds, **When** generation completes, **Then**
   the presentation shows one QR code and a localized `Scan to join`
   instruction.
3. **Given** the waiting room is initially rendered, **When** the player has
not selected `Show QR code`, **Then** no QR presentation, QR visual, or QR
loading state is visible.

### User Story 2 - Scan the same public destination as Copy link (Priority: P1)

As an opponent scanning the code, I want it to open the same public join
destination as the waiting-room Copy link action so that the session ID is
preserved across local, LAN, and hosted deployments.

**Why this priority**: A QR code that opens the wrong host, port, deployment
path, or session is not a usable share mechanism. URL correctness is therefore
part of the primary value rather than a later enhancement.

**Independent Test**: A URL acceptance check and waiting-room browser check can
exercise an origin with a port and an explicit application base path, inspect the
QR payload, and compare it byte-for-byte with the URL supplied to Copy link.

**Acceptance Scenarios**:

1. **Given** the waiting room is served at
   `http://192.168.9.1:8080`, **When** a QR code is generated for session `S`,
   **Then** its payload is the absolute destination
   `http://192.168.9.1:8080/join?id=S`, including the port and encoded session
   identifier.
2. **Given** the application is served below a configured public deployment
   path, **When** a QR code is generated, **Then** the payload stays below
   that path and uses the public protocol, host, optional port, `/join` route,
   and encoded `id` parameter.
3. **Given** the Copy link action and QR presentation are available, **When**
   the player uses either action, **Then** both actions target exactly the
   same absolute join URL.
4. **Given** an opponent opens the QR destination, **When** the join screen
   loads, **Then** it receives the same session ID that was encoded by the
   waiting player.

### User Story 3 - Use and dismiss the presentation accessibly (Priority: P2)

As a player using a keyboard, assistive technology, or a narrow mobile
viewport, I want the QR presentation to behave like the application's other
accessible overlays so that I can understand its state and close it reliably.

**Why this priority**: Sharing must remain usable across the supported input
and viewport modes, and the feature must not weaken the existing overlay
accessibility contract.

**Independent Test**: A presentation acceptance check and browser journey can verify
the dialog semantics, localized labels, loading and error messages, focus
behavior, and all supported dismissal paths.

**Acceptance Scenarios**:

1. **Given** the QR presentation is open, **When** the player selects Close,
   presses Escape, or clicks the backdrop, **Then** it closes and focus is
   restored according to the existing overlay behavior.
2. **Given** QR generation is loading, **When** the player views the
   presentation, **Then** a localized loading message is visible and an
   explicit Close action remains usable.
3. **Given** QR library loading or QR rendering fails, **When** the failure is
   reported, **Then** a localized error message and usable Close action are
   shown, with no pending or rejected QR rendering presented as a successful
   code.
4. **Given** the viewport is narrow, **When** the QR presentation is open,
   **Then** the QR presentation fits without horizontal overflow and follows
   the existing mobile bottom-sheet behavior.
5. **Given** the player switches between English and Ukrainian, **When** the
   waiting-room share card and QR presentation are rendered, **Then** every
   new user-facing string has a corresponding translation key in both
   languages.

### Edge Cases and Failure Behavior

- A missing or invalid waiting-room session does not expose a QR action; the
  existing waiting-room lifecycle and error handling remain authoritative.
- At the canonical-URL helper boundary, any non-empty session identifier is
  serialized as a query-parameter value rather than concatenated into an
  unsafe or ambiguous URL. Defensive helper fixtures include spaces, `&`, `+`,
  `/`, `%`, and non-ASCII characters. Browser and join-screen journeys continue
  to use session IDs accepted by the existing UUID validation and do not
  broaden that validation contract.
- A public origin with a non-default port retains that port; a hosted origin
  retains its protocol and hostname; the explicit application base path is neither
  dropped nor duplicated.
- Closing or unmounting the presentation while QR work is pending must not
  update the closed or unmounted presentation with stale success or error
  state. Reopening starts from a valid current state.
- A failed QR generation operation results in the localized
  failure state and never a pending or rejected QR presentation claimed as
  ready.
- Existing Copy ID behavior, Copy link availability and feedback,
  waiting-state polling, and navigation remain unchanged when QR generation
  is unused or unavailable. At the root deployment, the Copy link destination
  remains byte-for-byte compatible with current behavior; at a configured
  subpath, its intentional base-path correction is the canonical URL required
  by FR-005 and shared with QR by FR-006.
- The QR presentation must remain usable on narrow screens and with keyboard
  focus; it must not introduce horizontal scrolling.
- Base-path boundaries are explicit: an empty setting or `/` normalizes to `/`;
  a non-root setting has exactly one leading `/` and no trailing `/`; internal
  path segments are preserved; the normalized path appears exactly once before
  `/join` and contains no query, hash, or current route suffix. A configured
  value containing a query, hash, or current-route suffix is invalid: shared
  normalization raises the typed configuration error, the build exits non-zero,
  and no share URL is emitted rather than silently stripping the suffix.
- The logical normalized base path is the shared source for routing and join
  URLs. Asset delivery derives its base as `/` for root or the logical path with
  one trailing `/` for a non-root deployment; no consumer applies a second
  independent normalization. An empty or whitespace session ID is invalid and
  produces no canonical share URL.
- While QR work is pending, the QR area remains hidden. A rejected generation
  or late result is never presented as ready; only successful completion for
  the current open presentation may enter the ready state. Pixel-level
  decoding of the generated QR image is outside this feature's contract.
- No existing service or delivery artifact should change unexpectedly. Any
  unexpected difference requires inspection before the feature can proceed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The waiting-room share card MUST provide a `Show QR code` action
  beside the existing Copy ID and Copy link actions.
- **FR-002**: The QR presentation MUST be opened only after the player selects
  `Show QR code`; the initial waiting-room render MUST not show a QR visual,
  QR loading state, or QR error state.
- **FR-003**: In the ready state, the QR presentation MUST contain one
  accessible QR image, a short localized scan instruction, and an explicit
  localized Close action. It MUST NOT add download, export, or duplicate-copy
  controls. Loading and error states MUST keep the QR image hidden.
- **FR-004**: The QR code MUST be generated in the player's browser only when
  the QR presentation opens, rather than during the initial waiting-room
  render.
- **FR-005**: The QR payload MUST be one complete absolute public join URL
  containing the current protocol, public hostname or IP address, port when
  present, the explicit application base path shared by the router and URL
  helper, the `/join` route, and the canonically serialized `id` query
  parameter.
- **FR-006**: Copy link and QR generation MUST consume the same canonical URL
  result so that they always produce the same join destination.
- **FR-007**: The QR presentation MUST use the existing accessible overlay
  accessibility contract, including dialog semantics, focus trapping,
  keyboard Escape dismissal, backdrop dismissal, focus restoration, an
  explicit Close action, centered desktop presentation, and mobile
  bottom-sheet presentation.
- **FR-008**: The QR presentation MUST expose distinct localized loading,
  ready, and generation-error states. It MUST never treat a pending or rejected
  generation operation as a successful result.
- **FR-009**: QR work that finishes after the presentation closes or its owner
  unmounts MUST be ignored and MUST NOT mutate stale UI state.
- **FR-010**: New user-facing strings MUST be present in English and Ukrainian
  with exact translation-key parity.
- **FR-011**: When the QR action is not used, the existing Copy ID behavior,
  Copy link availability and feedback, waiting-state polling, and navigation
  behavior MUST remain unchanged. The root-deployment Copy link destination
  MUST remain byte-for-byte compatible with current behavior. For a configured
  subpath, the only permitted Copy link destination change is the canonical
  base-path-aware URL required by FR-005 and shared byte-for-byte with QR by
  FR-006.

### Compatibility and Boundary Requirements

- Existing session creation, joining, waiting-state transitions, Copy ID,
  Copy link interaction/feedback, navigation, and authorization behavior
  remain unchanged. Copy link destination compatibility follows FR-011's
  explicit root-versus-configured-subpath rule.
- QR generation is local to the player's browser and does not create a new
  server-side sharing flow, persisted data, or external sharing service.
- The existing join destination remains the destination for both sharing
  actions, and the current session identifier remains the authority for the
  join flow.
- One configured public deployment path is shared by asset delivery, routing,
  and join-URL construction. It is normalized once; the logical path appears
  exactly once in routes and join URLs, while asset delivery alone uses its derived
  trailing-slash asset form.
- The existing accessible overlay contract remains authoritative for dialog
  semantics, focus, dismissal, desktop presentation, and mobile presentation.
- A ready QR image exposes an image role and a localized accessible label;
  loading exposes a localized status, failure exposes a localized alert, and
  the Close action remains labeled and usable in every open state.
- The ready QR is successfully generated and rendered as a square, and at
  viewport widths down to 320 CSS pixels it scales proportionally without
  horizontal overflow. Exact QR generator options are implementation-contract
  decisions rather than user-facing requirements.
- Acceptance evidence covers the waiting-room action, lazy generation, URL
  equality, success and failure states, dismissal, focus behavior, locale
  parity, preserved sharing controls/interactions, and root-deployment Copy
  link compatibility.

## Key Entities and State

- **Session share context**: the active waiting-room session identifier and
  its canonical absolute public join URL. The waiting room owns this context
  and supplies the URL to sharing actions.
- **QR presentation state**: the presentation's open/closed lifecycle and its
  QR status: not requested, loading, ready, or error. The presentation owns its
  display state while it is open.
- **Canonical join URL**: the single absolute destination shared by Copy link
  and QR generation. It includes the public origin, configured deployment
  path, `/join` route, and encoded session identifier.
- **Localized share messages**: the paired English and Ukrainian labels,
  title, scan instruction, loading message, error message, and Close label
  shown by the waiting room and QR presentation.

## Success Criteria *(mandatory)*

Success criteria MUST be measurable and tied to evidence.

- **SC-001**: In the acceptance fixture set, every fixture admitted by the
  existing waiting-room guard with a non-empty session ID, present player
  identity, reachable status `WAITING_FOR_PLAYERS`, and resolved initial
  loading shows the QR action, and 0 QR presentations or QR loading states are
  present before that action is selected. Missing-context, other-status, and
  unresolved-loading fixtures are separate withheld-action cases.
- **SC-002**: In URL acceptance tests covering a hostname, an IP address, a
  non-default port, and an explicit application base path, 100% of generated QR
  payloads are absolute and match the Copy link destination exactly.
- **SC-003**: In each root and configured-subpath browser acceptance run,
  opening the generated destination reaches the existing join screen with the
  adapter-issued session ID pre-filled exactly. The transport/prefill journey
  does not submit an opaque mock-adapter ID. In the same runs, a separate
  direct-route fixture using an existing valid UUID proves the join screen's
  validation and submission boundary remain unchanged.
- **SC-004**: The QR presentation passes all three supported dismissal paths
  (Close, Escape, and backdrop), restores focus according to the existing
  accessible overlay contract, and shows no stale state after close/unmount in
  acceptance checks.
- **SC-005**: English and Ukrainian resource-key parity tests report identical
  key sets, and both locales provide visible action, title, instruction,
  loading, error, and Close text.
- **SC-006**: Existing Copy ID, Copy link availability/feedback,
  waiting-state polling, and navigation tests remain green with the QR action
  unused; root-deployment Copy link equality remains green, while configured
  subpath URL equality is proven separately as the intended FR-005/FR-006
  correction.
- **SC-007**: Existing game creation, joining, waiting, navigation, and service
  behavior remain unchanged in full regression validation, and any unexpected
  delivery-artifact difference is reviewed before release.

## Assumptions and Clarifications

- The current waiting-room session identifier remains the source of truth for
  the QR payload and remains valid while the creator waits for an opponent.
- The current browser origin represents the public address reachable by the
  scanning opponent, including protocol, hostname/IP, and port; a separate
  public-host override is not supported.
- Invalid base-path configuration raises the typed configuration error and
  fails the build non-zero rather than producing a potentially misleading
  share URL.
- The single build-time application base-path setting is the source of truth
  for asset delivery, router resolution, and join-URL construction. Empty or
  `/` means the logical root path `/`; otherwise normalization adds exactly one
  leading slash, removes trailing slashes, preserves internal segments, and
  excludes query strings, fragments, and the current route. The current
  root-served deployment therefore uses `/` before `/join`, while a subpath
  build derives a trailing slash only for asset delivery.
- Empty or whitespace session IDs are rejected by canonical URL construction;
  the waiting-room action is withheld and no Copy-link/QR URL is emitted for
  such a defensive context.
- `JoinGameScreen` remains responsible for consuming the `id` query parameter;
  this feature does not change join validation or navigation. The mock-browser
  create-and-share journey uses the opaque ID actually issued by the existing
  mock adapter to prove canonical transport and exact prefill, then stops
  before submission. A separate direct-route browser fixture and focused
  join-screen test use an existing valid UUID to prove validation/submission
  behavior is unchanged. Escaped-character cases remain defensive canonical-
  URL helper/consumer fixtures only and are not valid end-to-end join inputs.
- The session ID is an intentionally shareable capability equivalent to the
  existing Copy link value. QR sharing adds no masking, encryption, expiry, or
  authorization rule; existing session validation and authorization remain
  authoritative.
- Successful QR generation completion is the observable readiness boundary;
  pending and rejected operations remain hidden or show an error. The feature
  does not require pixel-level QR decoding as a separate acceptance measure.
- No unresolved clarification is required before planning.

## Evidence and Documentation Impact

- Proving evidence: focused URL, QR presentation, waiting-room, localization,
  and mock-browser acceptance checks, plus the repository regression gate.
- Delivery impact: existing service behavior and packaged delivery artifacts
  remain unchanged apart from the intended browser sharing experience; any
  unexpected artifact difference is reviewed before release.
- Documentation: the approved design remains the detailed design reference;
  update `README.md`, `docs/index.md`, or `docs/architecture.md` only if the
  implemented user-facing behavior makes their documented context inaccurate.
- Feature artifacts: this `spec.md` and `checklists/requirements.md` are
  prerequisites for implementation planning.

## Design Reference

The approved design for this feature is recorded in
[`docs/superpowers/specs/2026-08-16-qr-session-sharing-design.md`](../../docs/superpowers/specs/2026-08-16-qr-session-sharing-design.md).
