# Feature Specification: QR Session Sharing

**Feature Branch**: `feature/qr-session-sharing`

**Created**: 2026-08-16

**Status**: Draft

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
- An on-demand QR presentation in the existing accessible Sheet behavior.
- Browser-side generation of a readable QR code from the canonical absolute
  join URL, including loading and failure states.
- English and Ukrainian labels and status messages with matching translation
  keys.
- Focused component and browser tests for URL correctness, lazy generation,
  popup behavior, dismissal, failure handling, and preservation of existing
  sharing actions.

### Out of Scope

- Backend endpoints, REST or OpenAPI changes, session-state changes,
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
and a current status in the existing waiting states (`INITIALIZED` or
`WAITING_FOR_PLAYERS`). The existing waiting-room guard and lifecycle remain
the authority for validity; the QR action is withheld for missing session or
player data, a non-waiting status, or a defensive render without a valid
context. A valid-session acceptance fixture is evaluated after its initial
waiting-room loading state has resolved.

**Independent Test**: A focused waiting-room component test and a mock-browser
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
   not selected `Show QR code`, **Then** no QR presentation, QR canvas, or QR
   loading state is visible.

### User Story 2 - Scan the same public destination as Copy link (Priority: P1)

As an opponent scanning the code, I want it to open the same public join
destination as the waiting-room Copy link action so that the session ID is
preserved across local, LAN, and hosted deployments.

**Why this priority**: A QR code that opens the wrong host, port, deployment
path, or session is not a usable share mechanism. URL correctness is therefore
part of the primary value rather than a later enhancement.

**Independent Test**: A URL-builder test and waiting-room browser test can
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
Sheets so that I can understand its state and close it reliably.

**Why this priority**: Sharing must remain usable across the supported input
and viewport modes, and the feature must not weaken the existing overlay
accessibility contract.

**Independent Test**: A Sheet/widget test and mock-browser journey can verify
the dialog semantics, localized labels, loading and error messages, focus
behavior, and all supported dismissal paths.

**Acceptance Scenarios**:

1. **Given** the QR presentation is open, **When** the player selects Close,
   presses Escape, or clicks the backdrop, **Then** it closes and focus is
   restored according to the existing Sheet behavior.
2. **Given** QR generation is loading, **When** the player views the
   presentation, **Then** a localized loading message is visible and an
   explicit Close action remains usable.
3. **Given** QR library loading or QR rendering fails, **When** the failure is
   reported, **Then** a localized error message and usable Close action are
   shown, with no empty QR area presented as a successful code.
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
- A session identifier containing characters that require escaping is encoded
  as a query parameter and is not concatenated into an unsafe or ambiguous
  URL.
- A public origin with a non-default port retains that port; a hosted origin
  retains its protocol and hostname; the explicit application base path is neither
  dropped nor duplicated.
- Closing or unmounting the presentation while QR work is pending must not
  update the closed or unmounted presentation with stale success or error
  state. Reopening starts from a valid current state.
- A failed library load or canvas rendering operation results in the localized
  failure state and never a blank QR presentation claimed as ready.
- The existing Copy ID, Copy link, waiting-state polling, and navigation
  behavior remain unchanged when QR generation is unused or unavailable.
- The QR presentation must remain usable on narrow screens and with keyboard
  focus; it must not introduce horizontal scrolling.
- Base-path boundaries are explicit: an empty setting or `/` normalizes to `/`;
  a non-root setting has exactly one leading `/` and no trailing `/`; internal
  path segments are preserved; the normalized path appears exactly once before
  `/join` and contains no query, hash, or current route suffix.
- While QR work is pending, the QR area remains hidden. A partial, empty, late,
  or failed result is never presented as ready; only a successful result for
  the current open presentation may enter the ready state.
- No backend or generated API artifact should change. Any unexpected change
  to `docs/openapi.json` requires inspection before the feature can proceed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The waiting-room share card MUST provide a `Show QR code` action
  beside the existing Copy ID and Copy link actions.
- **FR-002**: The QR presentation MUST be opened only after the player selects
  `Show QR code`; the initial waiting-room render MUST not show a QR canvas,
  QR loading state, or QR error state.
- **FR-003**: The QR presentation MUST contain the QR code area, a short
  localized scan instruction, and an explicit localized Close action. It MUST
  NOT add download, export, or duplicate-copy controls.
- **FR-004**: The QR code MUST be generated in the player's browser only when
  the QR presentation opens, rather than during the initial waiting-room
  render.
- **FR-005**: The QR payload MUST be one complete absolute public join URL
  containing the current protocol, public hostname or IP address, port when
  present, the explicit application base path shared by the router and URL
  helper, the `/join` route, and the URL-encoded `id` query parameter.
- **FR-006**: Copy link and QR generation MUST consume the same canonical URL
  result so that they always produce the same join destination.
- **FR-007**: The QR presentation MUST use the existing Sheet interaction and
  accessibility contract, including dialog semantics, focus trapping,
  keyboard Escape dismissal, backdrop dismissal, focus restoration, an
  explicit Close action, centered desktop presentation, and mobile
  bottom-sheet presentation.
- **FR-008**: The QR presentation MUST expose distinct localized loading,
  ready, and generation-error states. It MUST never display an empty QR area
  as a successful result.
- **FR-009**: QR work that finishes after the presentation closes or its owner
  unmounts MUST be ignored and MUST NOT mutate stale UI state.
- **FR-010**: New user-facing strings MUST be present in English and Ukrainian
  with exact translation-key parity.
- **FR-011**: The existing Copy ID, Copy link, waiting-state polling, and
  navigation behavior MUST remain unchanged when the QR action is not used.

### Backend/API Requirements

This feature does not affect `src/main/java`.

- Controller and route: no change; the existing join route remains the
  destination.
- Application API, engine/state, persistence, exceptions/status mapping, and
  SSE/event behavior: no change.
- OpenAPI: `docs/openapi.json` is expected to remain unchanged because no
  backend contract is added or modified; any unexpected diff must be reviewed.

### Frontend Requirements

- Adapter contract: no `GameAdapter` method, DTO, HTTP behavior, or mock
  adapter behavior changes.
- HTTP/mock behavior: no direct network request is needed for QR generation;
  the feature uses the current waiting-room session context and join URL.
- State/lifecycle: the waiting-room screen owns the QR open/closed state and
  canonical join URL; the canonical URL helper consumes the explicit
  application base path shared by the router; a focused QR presentation owns
  its idle/loading/ready/error display state and ignores stale asynchronous
  completion.
- Configuration source: one build-time application base-path setting is the
  authoritative value for asset delivery, router resolution, and canonical
  join-URL construction. It is normalized once and no consumer may define a
  separate base path.
- Screens/widgets/design: the waiting-room screen adds the action; a focused
  QR widget renders the presentation and QR result; the existing Sheet remains
  the shared overlay boundary.
- Localization/accessibility/responsiveness: English and Ukrainian strings
  must remain in parity; the presentation must expose an accessible title,
  QR description, scan instruction, loading/error status, and Close action;
  the QR display must fit supported narrow viewports. Its nominal square
  display is 300 CSS pixels with margin 2 modules and error
  correction level M; at viewport widths down to 320 CSS pixels it scales down
  proportionally within the Sheet content area rather than introducing
  horizontal overflow.
- Performance/lifecycle: selecting `Show QR code` MUST open the Sheet before
  QR generation completes and MUST expose the localized loading state while
  package loading or rendering is pending. QR work starts only after the Sheet
  opens; no numeric QR-generation-time SLA is promised by this feature.
- Browser tests: cover the waiting-room action, lazy generation, canonical
  absolute URL, successful and failed generation, Sheet dismissal, focus
  behavior, localization parity, and unchanged copy actions.

### Contract and Boundary Requirements

- Backend layer affected: `none`.
- Frontend layers affected: `screen`, `widget`, `service`, and `design` only
  where needed to reuse existing Sheet behavior; adapters and backend layers
  remain unchanged.
- Allowed dependency direction: the waiting-room screen supplies session
  context and the canonical URL to the QR widget; the widget does not access
  session storage, adapters, network clients, or game state directly.
- New dependency: the browser QR-generation dependency described by the
  approved design (`qrcode` with its matching type support) may be added as a
  runtime/development dependency during planning and implementation. No
  external service or server-side dependency is permitted.
- Persistence/auth/external-service changes: `none`.

## Key Entities and State

- **Session share context**: the active waiting-room session identifier and
  its canonical absolute public join URL. The waiting-room screen owns this
  context and passes the URL to sharing actions.
- **QR presentation state**: the presentation's open/closed lifecycle and its
  QR status: not requested, loading, ready, or error. The QR widget owns the
  display state while it is mounted.
- **Canonical join URL**: the single absolute destination shared by Copy link
  and QR generation. It includes the public origin, configured deployment
  path, `/join` route, and encoded session identifier.
- **Localized share messages**: the paired English and Ukrainian labels,
  title, scan instruction, loading message, error message, and Close label
  shown by the waiting room and QR presentation.

## Success Criteria *(mandatory)*

Success criteria MUST be measurable and tied to evidence.

- **SC-001**: In the focused waiting-room acceptance tests, the valid-session
  population consists of every acceptance fixture whose existing waiting-room
  guard admits the screen, whose session ID and player identity are present,
  whose status is `INITIALIZED` or `WAITING_FOR_PLAYERS`, and whose initial
  waiting-room loading state has resolved. 100% of that population shows the
  QR action, and 0 QR presentations or QR loading states are present before
  that action is selected. Missing-context, non-waiting, and unresolved-loading
  fixtures are separate withheld-action cases.
- **SC-002**: In URL acceptance tests covering a hostname, an IP address, a
  non-default port, and an explicit application base path, 100% of generated QR
  payloads are absolute and match the Copy link destination exactly.
- **SC-003**: In the browser acceptance journey, scanning/opening the generated
  destination reaches the existing join screen with the original session ID
  pre-filled in 100% of tested cases.
- **SC-004**: The QR presentation passes all three supported dismissal paths
  (Close, Escape, and backdrop), restores focus according to the existing
  Sheet contract, and shows no stale state after close/unmount in focused
  component tests.
- **SC-005**: English and Ukrainian resource-key parity tests report identical
  key sets, and both locales provide visible action, title, instruction,
  loading, error, and Close text.
- **SC-006**: Existing Copy ID, Copy link, waiting-state polling, and
  navigation tests remain green with the QR action unused.
- **SC-007**: `scripts/verify.sh` passes before implementation is declared
  complete, or the exact capability limitation is recorded. No backend API
  or OpenAPI change is expected.

## Assumptions and Clarifications

- The current waiting-room session identifier remains the source of truth for
  the QR payload and remains valid while the creator waits for an opponent.
- The current browser origin represents the public address reachable by the
  scanning opponent, including protocol, hostname/IP, and port.
- The single build-time application base-path setting is the source of truth
  for asset delivery, router resolution, and join-URL construction. Empty or
  `/` means the root path `/`; otherwise normalization adds exactly one leading
  slash, removes trailing slashes, preserves internal segments, and excludes
  query strings, fragments, and the current route. The current root-served
  deployment therefore uses `/` before `/join`.
- `JoinGameScreen` remains responsible for consuming the `id` query parameter;
  this feature does not change join validation or navigation.
- The session ID is an intentionally shareable capability equivalent to the
  existing Copy link value. QR sharing adds no masking, encryption, expiry, or
  authorization rule; existing session validation and authorization remain
  authoritative.
- The approved design's `qrcode` dependency and canvas rendering approach are
  implementation constraints for planning, while QR appearance and behavior
  are judged by the observable requirements above.
- No unresolved clarification is required before planning.

## Evidence and Documentation Impact

- Proving tests: focused URL/helper tests, QR widget/waiting-room component
  tests, localization parity tests, the mock-browser QR-sharing journey, and
  the repository `scripts/verify.sh` gate.
- Generated artifacts: no OpenAPI change is expected; inspect any
  `docs/openapi.json` diff produced by verification. The packaged frontend and
  runnable JAR remain the delivery artifacts.
- Documentation: the approved design remains the detailed design reference;
  update `README.md`, `docs/index.md`, or `docs/architecture.md` only if the
  implemented user-facing behavior makes their documented context inaccurate.
- Feature artifacts: this `spec.md` and
  `checklists/requirements.md` are required before `$speckit-plan`.

## Design Reference

The approved design for this feature is recorded in
[`docs/superpowers/specs/2026-08-16-qr-session-sharing-design.md`](../../docs/superpowers/specs/2026-08-16-qr-session-sharing-design.md).
