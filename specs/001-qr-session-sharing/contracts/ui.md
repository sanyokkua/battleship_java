# UI Contract: QR Session Sharing

This feature has no REST, SSE, DTO, OpenAPI, or adapter contract. Its external
contract is the browser-visible waiting-room and QR Sheet behavior.

## Waiting-room share card

Location: /game/wait, inside the existing session share card.

| Control | Required behavior |
|---|---|
| Existing Copy action | Copies the unchanged raw sessionId. |
| Existing Copy link action | Copies canonicalJoinUrl. |
| Show QR code action | Opens the QR Sheet and starts QR generation only after selection. |

The QR action is rendered only when the existing waiting-room session context
is valid: the guard admits the waiting room, the session ID and player identity
are present, the reachable status is `WAITING_FOR_PLAYERS`, and initial
waiting-room loading has resolved. Missing-context, other-status, and
unresolved-loading cases withhold the action. The initial screen contains no
open presentation, visible QR canvas, or QR loading/error message.

## QR Sheet

The widget is conceptually QrCodeSheet({open, url, onClose}) and delegates
overlay behavior to the existing Sheet.

When open, the widget MUST expose:

- role="dialog" and aria-modal="true" through Sheet;
- a localized title such as "Join with QR code";
- role="status" for the localized loading message while pending;
- one canvas with `role="img"` and a localized `aria-label` only after
  successful rendering. The `screens.qr.description` value is that canvas
  label and is the QR description announced by assistive technology;
- a localized scan instruction in the ready state;
- role="alert" for the localized generation error when loading or rendering
  fails;
- a localized Close button in every open state.

The existing overlay's title/`aria-labelledby`, Escape, backdrop, focus-trap,
focus-restore, centered desktop, and mobile bottom-sheet behavior is the
contract; the QR widget must not reimplement or weaken it. The existing `Sheet`
API remains unchanged, so the QR-specific description is attached to the ready
canvas rather than introducing a new dialog `aria-describedby` prop. The QR
Sheet contains no download, export,
customization, duplicate-copy, or new navigation control.

The ready QR is a nominal 300 CSS-pixel square with margin 2 modules and
error-correction level M. At supported viewport widths down to 320 CSS pixels,
it scales proportionally within the Sheet content area without horizontal
overflow. The QR ID is intentionally shareable like Copy link; no QR-specific
masking, expiry, or authorization behavior is added.

## Canonical URL contract

For origin O, logical normalized base path B, and session ID S, the payload is
an absolute URL equivalent to:

```text
joinPath(B) = B == "/" ? "/join" : B + "/join"
url = new URL(joinPath(B), O)
url.searchParams.set("id", S)
payload = url.toString()
```

`URLSearchParams.set` and the WHATWG URL serializer are the sole session-ID
serialization rule. Tests MUST NOT derive expected values with
`encodeURIComponent`. For example, helper input `space & plus+slash/percent%
unicode Ž` serializes as
`space+%26+plus%2Bslash%2Fpercent%25+unicode+%C5%BD`, and parsing the result's
`id` parameter returns the original input. Copy link and `qrcode.toCanvas`
receive that same byte-for-byte `payload` string.

The implementation uses the browser URL API so protocol, hostname/IP,
optional port, path separators, and query serialization are canonicalized.
The logical normalized base path is `/` for empty or `/` input; otherwise it has
exactly one leading slash, no trailing slash, preserved internal segments, and
no query/hash or reserved terminal application-route suffix. Reserved matching
is end-anchored after trailing-slash removal for `/join`, `/game/wait`,
`/game/preparation`, `/game/gameplay`, and `/game/results`. For example,
`/battleship/game/wait` is invalid, while `/battleship/game/wait-assets` and
`/community/join-us` remain valid. An invalid input throws
`AppBasePathConfigError` with `code: "INVALID_APP_BASE_PATH"` rather than
producing a silently altered path.
The error message includes the rejected raw setting, and the build MUST
terminate non-zero when `VITE_APP_BASE_PATH` has that error.
Empty or whitespace session IDs are a defensive missing-context case:
`buildCanonicalJoinUrl` returns `null`, and neither sharing consumer is
rendered or invoked.

### Shared configuration API

`frontend/src/config/appBasePath.ts` is the environment-agnostic module and
exports these exact interfaces:

```ts
class AppBasePathConfigError extends Error {
  readonly code: 'INVALID_APP_BASE_PATH'
}

normalizeAppBasePath(raw: string | undefined): string
toViteAssetBasePath(normalizedBasePath: string): string
buildCanonicalJoinUrl(
  origin: string,
  normalizedBasePath: string,
  sessionId: string,
): string | null
```

`frontend/src/config/appConfig.ts` is the browser runtime boundary and exports
exactly one normalized value:

```ts
const APP_BASE_PATH: string
```

Vite configuration loads raw `VITE_APP_BASE_PATH`, calls the shared normalizer,
and passes `toViteAssetBasePath(B)` as its asset base. Vite exposes that
slash-terminated result as `import.meta.env.BASE_URL`; `appConfig.ts` normalizes
it to `APP_BASE_PATH`. `BrowserRouter basename`, Copy link, and QR URL
construction consume that same runtime export and apply no independent
normalization. The logical path appears exactly once before `/join`.
Examples:

```text
http://192.168.9.1:8080/join?id=S
https://battleship.example.com/battleship/join?id=S
```

Copy link and qrcode.toCanvas receive the same resulting string. The existing
join screen remains responsible for reading id; no new route is introduced.
Escaped-character fixtures exercise only the canonical helper/consumer
boundary. The mock-browser create/share journey derives its expected
destination from the observed origin, configured logical base path, and opaque
ID actually issued by the existing mock adapter, then proves route resolution
and exact join-field prefill without submission. This construction is not
consumer-equality evidence; focused clipboard and `qrcode.toCanvas` assertions
own that proof. A separate direct-route browser fixture and focused join-screen
test use a valid existing UUID-format session ID to prove this feature does not
change `JoinGameScreen` validation. No adapter alias or test-only production
hook is introduced.

Copy link compatibility preserves its control, feedback, and root-deployment
destination. At a configured subpath, its only intentional destination change
is the canonical base-path-aware URL above, which must equal the QR payload
byte-for-byte.

## Failure and lifecycle contract

- A pending QR operation that completes after close/unmount MUST be ignored.
- An error MUST show a localized failure state and Close, never a pending or
  rejected render presented as ready.
- The canvas remains hidden until the renderer reports successful completion for
  the current open URL. Pixel-level QR decoding is outside this contract.
- Reopening MUST load/render the current URL from a fresh state.
- No backend request is made for QR generation.
