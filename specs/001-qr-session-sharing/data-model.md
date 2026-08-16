# Data Model: QR Session Sharing

This feature adds no persisted or backend entity. The following frontend
values are the observable state and boundaries required by the specification.

## SessionShareContext

Owned by WaitScreen and derived from the existing guarded session.

| Field | Type | Rules |
|---|---|---|
| sessionId | string | Non-empty, non-whitespace existing waiting-room session identifier from a guarded context with a present player identity, reachable waiting status (`WAITING_FOR_PLAYERS`), and resolved initial waiting-room loading; empty/whitespace values are rejected by the URL builder and do not expose the QR action. No new storage authority is introduced. |
| canonicalJoinUrl | string or null | Absolute URL built from the current browser origin, normalized application base path, /join, and an `id` serialized by `URLSearchParams.set`. Blank IDs produce `null`; otherwise the same string is passed to Copy link and QrCodeSheet. |

Invariants:

- canonicalJoinUrl has a protocol and host and retains a non-default port.
- The application base path appears exactly once before /join.
- The session identifier is encoded through URL query parameters, never by
  unsafe string concatenation.
- Empty or whitespace session identifiers produce no canonical URL and never
  reach Copy link or QR generation.
- The context is unavailable when the existing session guard has no valid
  sessionId/player; no QR action is exposed in that defensive render.
- The session ID is intentionally shareable through Copy link and QR; existing
  session validation and authorization remain authoritative.

## QrPresentationState

Owned by QrCodeSheet while it is mounted.

| State | Meaning | Visible output |
|---|---|---|
| idle / not requested | Sheet is closed or has not begun a request | No QR presentation is rendered. |
| loading | Dynamic QR module import or canvas rendering is pending | Open Sheet, localized loading status, usable Close action; canvas remains hidden. |
| ready | toCanvas completed for the current URL | Open Sheet, one visible canvas with accessible description, localized scan instruction, usable Close action. |
| error | Module load or rendering failed | Open Sheet, localized error status, no visible/claimed QR result, usable Close action. |

Transitions:

```text
closed/idle --Show QR code--> loading
loading --successful current generation--> ready
loading --failed current generation--> error
loading --Close/Escape/backdrop/unmount--> closed/idle
ready --Close/Escape/backdrop--> closed/idle
error --Close/Escape/backdrop--> closed/idle
```

The canvas remains hidden for idle/loading/error and until the renderer reports
successful completion. Late completion from a cancelled loading transition has
no state effect. Reopening starts a new loading transition for the current URL.
Pixel-level decoding is not a separate state transition.

## ApplicationBasePath

Build-time configuration, not persisted application data.

| Field | Type | Default | Rules |
|---|---|---|---|
| VITE_APP_BASE_PATH | path string | / | The logical normalized value is `/` for empty/root input; otherwise it has exactly one leading `/`, no trailing `/`, and preserved internal segments. Query/hash values or an end-anchored reserved terminal suffix equal to `/join`, `/game/wait`, `/game/preparation`, `/game/gameplay`, or `/game/results` after trailing-slash removal throw `AppBasePathConfigError` with code `INVALID_APP_BASE_PATH` and fail the build non-zero rather than being stripped; near matches remain valid. |
| APP_BASE_PATH | normalized path string | derived | Browser runtime export from `appConfig.ts`, normalized from `import.meta.env.BASE_URL`; the router and join-URL consumer use this exact value without independent normalization. |

The logical normalized value is the sole source for all consumers. Vite derives
`/` for root or the logical value plus one trailing `/` for asset delivery;
BrowserRouter basename and join-URL construction use the logical value. The
application base path appears exactly once before `/join`.

## QR presentation constraints

The nominal QR output is a 300 CSS-pixel square with margin 2 modules and
error-correction level M. For supported viewport widths down to 320 CSS pixels,
the output scales proportionally within the Sheet content area and introduces
no horizontal overflow.

## LocalizedShareMessages

English and Ukrainian leaf keys with exact parity:

- waiting-room action: screens.wait.showQr;
- QR title: screens.qr.title;
- scan instruction: screens.qr.scan;
- loading status: screens.qr.loading;
- ready/canvas description: screens.qr.description;
- generation error: screens.qr.error;
- close action: screens.qr.close.

These values are presentation resources, not domain state. Existing Copy ID,
Copy link control/feedback, waiting, refresh, and navigation messages remain
unchanged. Root-deployment Copy link output remains byte-for-byte compatible;
a configured subpath intentionally corrects only its destination to the shared
canonical base-path-aware URL.
