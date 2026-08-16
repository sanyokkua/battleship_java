# Research: QR Session Sharing

## Decision: Use one build-time application base-path setting

**Decision**: Add VITE_APP_BASE_PATH, defaulting to /, and normalize it in
one environment-agnostic frontend configuration/helper module. Empty or /
becomes the logical `/`; non-root values receive exactly one leading slash,
lose trailing slashes, preserve internal segments, and reject query, hash, or
an end-anchored reserved terminal suffix equal to `/join`, `/game/wait`,
`/game/preparation`, `/game/gameplay`, or `/game/results` after trailing-slash
removal. Near matches such as `/battleship/game/wait-assets` remain valid.
Invalid values raise `AppBasePathConfigError` with code
`INVALID_APP_BASE_PATH` rather than being silently stripped, and invalid builds
terminate non-zero. Configure Vite's asset base as `/` for root or the
logical path plus one trailing slash for a subpath; pass the logical path to
BrowserRouter and use that same logical path when constructing the absolute
join URL. A browser-only `appConfig.ts` exports `APP_BASE_PATH`, normalized
from Vite's `import.meta.env.BASE_URL`, for both runtime consumers. Empty or
whitespace session IDs return `null` without emitting a URL.

**Rationale**: The specification requires the router and URL helper to agree
when the app is served below the host root. A single setting prevents the QR
payload from pointing at a path the router does not serve. The default keeps
the existing root-served app behavior unchanged. The helper can accept an
explicit origin/base path in unit tests without mutating global browser state.

**Alternatives considered**:

- Continue using /join inline: rejected because it drops a configured
  deployment path and duplicates URL logic between Copy link and QR.
- Derive only from window.location.pathname: rejected because the current
  screen path (/game/wait) is not the application base path.
- Use separate router and QR constants: rejected because they can drift.
- Use an external runtime/deployment service setting: rejected because the
  bundled SPA needs the base path at build time for Vite asset URLs and no
  external service is in scope.

## Decision: Client-side lazy canvas generation with qrcode

**Decision**: Add qrcode as a runtime dependency and @types/qrcode as the
matching development type dependency. Dynamically import it only when
QrCodeSheet is open and call toCanvas with the approved generator defaults:
300 CSS-pixel nominal width, margin 2, and error-correction level M. On
supported viewport widths down to 320 CSS pixels, scale the square output
proportionally within the Sheet content area without horizontal overflow.

**Rationale**: This follows the approved design and the local dev.tools
reference (src/pages/qr/QrPage.tsx, qrcode 1.5.x). It keeps QR generation in
the browser, avoids backend/API changes, and avoids loading the QR package on
the initial waiting-room render.

**Alternatives considered**:

- Server-side QR generation: rejected because it adds an endpoint, session/API
  coupling, deployment state, and out-of-scope backend work.
- Eager import/render on WaitScreen: rejected because the initial screen must
  not load or render QR work.
- toDataURL plus an image: rejected because the approved design specifies a
  canvas and the widget needs only an on-screen QR, not export functionality.
- A second modal implementation: rejected because the existing Sheet already
  supplies dialog semantics, focus trapping/restoration, Escape, backdrop, and
  responsive presentation.

## Decision: Effect-local stale-result protection

**Decision**: The QR widget's generation effect will set a local active/cancelled
flag in its cleanup. It will check that flag and the current canvas before
committing loading, ready, or error outcomes. Closing or unmounting therefore
ignores late dynamic-import and canvas promises; reopening starts a fresh state
transition.

**Rationale**: qrcode.toCanvas and dynamic import() are asynchronous and do not
provide a shared cancellation protocol. A component-local guard is sufficient
for the lifecycle boundary and is easy to prove with a deferred mock in Vitest.
The widget must hide the canvas until the renderer reports successful
completion, so a pending or rejected render is never announced as ready.

**Alternatives considered**:

- AbortController: rejected because neither the dynamic import nor the renderer
  accepts an abort signal.
- Keep completed results after close: rejected because reopening could expose
  stale output for a changed URL and the spec requires a valid current state.
- Treat any mounted canvas as ready: rejected because a pending or rejected
  rendering operation must produce the localized error state.

## Decision: Test the URL at the generator boundary and the flow in mock-browser

**Decision**: Mock qrcode.toCanvas in focused widget/waiting-room tests and
assert the exact absolute URL supplied to it, including encoded session ID and
base path. Use the existing mock-adapter Playwright setup for stable visible UI,
responsive Sheet behavior, dismissal, ready-state reopen, localization, and
the join-screen journey. Keep timing-sensitive pending behavior and
deterministic module/render failures in deferred focused tests.

**Rationale**: A canvas does not expose its encoded payload to Playwright or a
DOM assertion. The toCanvas call is the narrow, deterministic boundary for
proving payload equality with Copy link. Browser evidence then covers the
actual rendered UI and real router/join behavior without adding test-only
production hooks or cross-browser clipboard permissions.

The canonical serializer is `URLSearchParams.set`, not `encodeURIComponent`.
Focused helper/consumer fixtures include escaped characters and assert the
WHATWG serialized string plus byte-for-byte Copy-link/QR equality. The real
mock-browser create/share journey derives its expected destination from the
observed origin, configured logical base path, and opaque session ID issued by
the existing mock adapter, then proves route resolution and exact join-field
prefill without submission. That construction is routing evidence, not a
second proof of the consumer payload already owned by focused assertions.
A separate direct-route browser fixture and focused join-screen test use a
valid UUID to prove validation/submission behavior remains unchanged.

**Alternatives considered**:

- Decode the canvas in Playwright: rejected because it adds a decoder and
  browser-specific image processing to a frontend journey that does not need it.
- Add a production data-qr-url attribute solely for tests: rejected because it
  expands the public DOM contract without user value.
- Change or alias mock-adapter session IDs: rejected because test setup must not
  change existing adapter behavior or broaden the frontend-only feature scope.
- Use the live packaged-JAR suite for this feature: rejected because the
  feature adds no backend or real REST/SSE behavior; the repository gate still
  runs the live suite for regression evidence.

## Decision: Preserve the existing shareability boundary

**Decision**: Treat the session ID as an intentionally shareable capability,
equivalent to the existing Copy link value. QR sharing adds no masking,
encryption, expiry, or authorization rule; existing session validation and
authorization remain authoritative.

**Rationale**: The QR code is explicitly an alternative representation of the
existing join link. Adding a second secrecy or access-control model would
change session semantics and violate the frontend-only scope.

## Decision: Define the valid waiting-session population

**Decision**: The valid acceptance population consists of waiting-room
fixtures admitted by the existing guard, with a non-empty session ID, player
identity, the reachable `WAITING_FOR_PLAYERS` status, and resolved initial
waiting-room loading. Missing-context, other-status, and unresolved-loading
fixtures withhold the QR action.

**Rationale**: This preserves the current lifecycle authority and makes the
100%/0% SC-001 targets reproducible without introducing QR-specific session
validation.

## Research status

All technical-context unknowns and clarification decisions are resolved. No
unresolved clarification remains for implementation planning.
