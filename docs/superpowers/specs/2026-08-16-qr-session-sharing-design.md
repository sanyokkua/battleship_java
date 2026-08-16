# QR Session Sharing Design

**Status:** Approved design

**Date:** 2026-08-16

**Feature:** `001-qr-session-sharing`

## Goal

Give the player who created a Battleship session a QR-sharing option in the existing waiting room. The opponent can scan the code with a phone and open the game's join screen without manually copying a game ID or link.

The QR code is an alternative representation of the existing join link. It must not create a second invite format or require a backend session-sharing API.

## Scope

The feature includes:

- a `Show QR code` action in the waiting-room share card, next to the existing Copy and Copy link actions;
- a QR-only popup that is rendered only after the action is selected;
- client-side QR generation using the `qrcode` package and an HTML canvas;
- a short `Scan to join` instruction and a Close action;
- localized English and Ukrainian labels, loading text, and generation-error text;
- tests for URL correctness, lazy generation, popup behavior, and failure handling.

The feature does not include backend endpoints, session-model changes, persistence changes, authentication changes, QR downloads, or additional sharing actions inside preparation, gameplay, or results screens.

## Existing context

The waiting room already displays the current `sessionId` and supports:

- copying the raw game ID;
- copying a join link built from the current browser origin and `/join?id=<sessionId>`;
- opening the join screen, where `JoinGameScreen` reads the `id` query parameter and pre-fills the game ID.

The frontend already has an accessible `Sheet` overlay with focus trapping, Escape handling, backdrop dismissal, focus restoration, and responsive desktop/mobile presentation. The QR popup will use that component instead of introducing another modal implementation.

The project at `/Users/ok/Development/GitHub/dev.tools` is the implementation reference for the `qrcode` dependency, dynamic loading, and `toCanvas` rendering. Its broader QR payload and export features are outside this feature's scope.
Also, project is available by the link: https://github.com/sanyokkua/dev.tools

## User experience

The waiting-room share card contains three actions:

1. Copy the raw game ID.
2. Copy the join link.
3. Show the QR code.

No QR canvas is present in the waiting-room page before the third action is selected. Selecting it opens the popup and starts generation. The popup contains:

- a localized title such as `Join with QR code`;
- a QR canvas after generation succeeds;
- a localized `Scan to join` instruction;
- a Close button.

The popup closes through the Close button, Escape, or backdrop click. On mobile it uses the existing bottom-sheet presentation; on larger screens it uses the existing centered sheet.

While the QR is being generated, the popup shows a localized loading state. If package loading or canvas generation fails, the popup shows a localized error state and the Close action rather than displaying an empty or misleading QR area.

## Absolute join URL contract

The QR payload must be the complete absolute public join URL, not a route fragment. It must include:

- the current public protocol (`http` or `https`);
- the current public hostname or IP address;
- the current port when one is present, such as `:8080`;
- the application's configured public deployment path;
- the `/join` route;
- the URL-encoded `id` query parameter containing the session ID.

Examples:

```text
http://192.168.9.1:8080/join?id=<sessionId>
https://battleship.example.com/join?id=<sessionId>
```

For the current root-served application, the deployment path is `/`. If the application is later served below a public path, the join URL must remain below that same configured path and the router must resolve it there.

The implementation will centralize URL construction in a small helper using the browser `URL` API. Both the existing Copy link action and the new QR widget will consume the exact same helper result. No hardcoded hostname, port, localhost value, or relative-only payload is permitted.

## Architecture and data flow

`WaitScreen` remains responsible for the session context, canonical join URL, and popup open/closed state. A focused `QrCodeSheet` widget is responsible for QR-specific rendering and generation.

```text
Show QR code click
  -> WaitScreen sets qrOpen(true)
  -> QrCodeSheet opens through the existing Sheet
  -> qrcode is dynamically imported
  -> qrcode.toCanvas(canvas, absoluteJoinUrl, options)
  -> QR canvas is displayed
```

The widget boundary should be equivalent to:

```text
QrCodeSheet({ open, url, onClose })
```

The widget must not know how sessions are stored, joined, or fetched. It receives a fully formed URL and only turns that URL into a QR presentation.

## QR generation behavior

The frontend will add `qrcode` as a runtime dependency and `@types/qrcode` as the matching TypeScript development dependency, following the versions and API shape used by `dev.tools`.

The package will be dynamically imported when the popup opens so the QR code is not generated and the QR library is not loaded on the initial waiting-room render. Generation will use `toCanvas` with the reference implementation's readable defaults:

- width: approximately 300 pixels;
- margin: 2 modules;
- error-correction level: `M`.

The effect that performs generation must cancel or ignore stale work if the popup closes or the component unmounts while the dynamic import or canvas operation is pending.

## Component and file responsibilities

The implementation should follow these boundaries:

- `WaitScreen`: render the new action, build the canonical absolute join URL once, pass the URL to Copy link and `QrCodeSheet`, and own `qrOpen` state.
- URL helper: construct and validate the absolute public join URL using the current origin and configured application path.
- `QrCodeSheet`: render the existing `Sheet`, manage idle/loading/ready/error QR state, dynamically load `qrcode`, draw the canvas, and expose Close behavior.
- `Sheet`: remain unchanged and continue to provide shared overlay accessibility and interaction behavior.
- i18n resources: provide equivalent English and Ukrainian keys for the action, title, instruction, loading state, error state, and Close label.

No backend, Java, REST, OpenAPI, adapter, route-table, or browser-storage changes are required.

## Accessibility and responsive behavior

The popup remains a `role="dialog"` with `aria-modal="true"` and a localized title through `Sheet`. The QR presentation will have an accessible text description identifying it as the join code for the current game, and the instruction will remain available to assistive technologies.

The Close button is keyboard reachable and is the explicit primary dismissal action. Existing Escape, backdrop, focus-trap, and focus-restore behavior must remain intact. The QR canvas must fit within the popup on narrow screens without horizontal overflow.

## Verification

Frontend tests will prove:

- the QR action is available in the waiting room;
- no QR canvas or QR generation exists before the action is selected;
- selecting the action opens the Sheet and invokes `qrcode.toCanvas` lazily;
- `toCanvas` receives the same complete absolute URL used by Copy link, including protocol, host/IP, port, deployment path, route, and session ID;
- a successful generation displays the QR canvas and scan instruction;
- generation failure displays the localized error state and does not report a successful QR;
- Close, Escape, and backdrop dismissal work through the existing Sheet contract;
- existing Copy ID, Copy link, waiting-state polling, and navigation behavior remain unchanged;
- English and Ukrainian translation keys remain in parity.

The implementation will also run the repository's standard frontend build, lint, frontend tests, and `scripts/verify.sh` gate. Because there are no backend or API changes, no OpenAPI regeneration is expected; any unexpected generated-artifact diff must still be inspected and reported.

## Acceptance criteria

- A player in `/game/wait` can select `Show QR code`.
- The QR popup is not rendered by default.
- The popup shows a QR code that encodes the complete absolute public join URL for the active session.
- Scanning the QR code opens the join route at the same host/port/deployment path and pre-fills the session ID.
- The popup provides `Scan to join` and Close, with the existing accessible dismissal behavior.
- Loading and failure states are visible and localized.
- Existing copy actions continue to work and use the same canonical URL as the QR code.
- The feature works in both English and Ukrainian.
- The repository verification gate is green before implementation is declared complete.
