# Quickstart: Validate QR Session Sharing

This guide validates the feature after implementation. It assumes the current
checkout is the feature/qr-session-sharing feature branch or an implementation
state derived from it.

## Prerequisites

- Java 25 and Maven available.
- The repository's pinned frontend runtime installed by Maven at frontend/node
  (the full gate installs Node v24.18.0).
- A browser supported by the configured Playwright projects.

## Focused validation

From the repository root:

```bash
frontend/node/npm --prefix frontend run test -- src/config/appBasePath.test.ts src/widgets/sharing/QrCodeSheet.test.tsx src/design/components/Sheet/Sheet.test.tsx src/screens/WaitScreen.test.tsx src/screens/JoinGameScreen.test.tsx src/i18n/keyParity.test.ts
frontend/node/npm --prefix frontend run lint
```

Expected results:

- URL tests cover empty/root and configured subpath deployments, exactly one
  leading slash, removed trailing slash, preserved internal segments,
  path-only input, hostname/IP, protocol, non-default port, the shared
  `APP_BASE_PATH` runtime export, end-anchored reserved-route rejection with
  near-match acceptance, typed invalid-path errors, absent URLs for blank IDs,
  WHATWG `URLSearchParams.set` serialization, and byte-for-byte
  Copy-link/QR equality. The escaped-character fixture is helper/consumer-only;
  focused join-screen validation fixtures use existing valid UUIDs.
- QR widget tests cover lazy import/render, loading-before-ready,
  ready/error states, renderer-rejection handling, stale completion
  protection, nominal 300px options, exact canvas accessibility semantics,
  accessible Close, and no export/copy controls. The renderer's successful
  completion is the readiness boundary; pixel-level QR decoding is not needed.
- Waiting-room tests cover the valid-session population and withheld-action
  cases, the action, preserved Copy ID and Copy link interaction/feedback,
  byte-for-byte root Copy link compatibility, intentional configured-subpath
  canonical equality, and existing refresh/poll/navigation behavior.
- Join-screen tests cover the existing `?id=` prefill contract without changing
  join validation or navigation.
- Locale parity reports the same leaf-key set for English and Ukrainian.

These commands invoke the repository's pinned runtime directly. If
`frontend/node/npm` is not present, run the Maven build step in the full
validation section first; do not use host Node/npm as final evidence.

## Base-path build validation

Run both deployment shapes and inspect the generated entrypoint and asset
references after each build:

```bash
frontend/node/npm --prefix frontend run build
VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run build
```

The root build must emit root-relative application assets. The configured build
must emit `/battleship/`-prefixed assets; BrowserRouter and the canonical join
URL use the logical `/battleship` form, with no duplicated prefix.

Invalid configuration must fail before a bundle or share URL is accepted. Run
this exact fixture matrix:

```bash
VITE_APP_BASE_PATH='/battleship/?preview=1' frontend/node/npm --prefix frontend run build
VITE_APP_BASE_PATH='/battleship/#preview' frontend/node/npm --prefix frontend run build
VITE_APP_BASE_PATH='/battleship/game/wait' frontend/node/npm --prefix frontend run build
```

Each command must exit non-zero and its error output must contain
`INVALID_APP_BASE_PATH` plus the rejected raw setting. A zero exit, a silently
stripped query/hash/route suffix, or generated assets is a failure.

## Mock-browser validation

```bash
frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts
VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts
```

The journey must:

1. create a mock session and reach /game/wait;
2. confirm the QR Sheet is absent before Show QR code;
3. select Show QR code and observe the localized ready presentation;
4. observe one accessible QR canvas and the scan instruction;
5. exercise Close, Escape, and backdrop dismissal, including a 320px narrow
   viewport where the existing Sheet becomes a bottom sheet and the QR remains
   within the content width;
6. capture the opaque ID issued by the existing mock adapter, derive the
   expected destination from the observed origin, configured logical base path,
   and that exact ID, navigate it, and prove route resolution plus exact
   join-field prefill without treating the derivation as Copy-link/QR equality
   evidence, submitting it, or claiming UUID validity;
7. navigate directly to the join route with valid UUID
   `123e4567-e89b-12d3-a456-426614174000` and prove the existing validation and
   submission boundary remains enabled without adapter remapping; and
8. switch to Ukrainian and prove the new action/title/instruction/Close labels
   are present.

Deterministic pending-close, module-loading, rendering-failure, stale-result,
and recovery behavior in both locales is proven by the focused QR widget tests,
because browser timing and fault injection would depend on scheduler or bundler
chunk details rather than stable user-visible behavior.

The exact canvas payload and escaped-character fixtures are asserted in the
focused helper/generator-boundary tests, because Playwright cannot reliably
decode a rendered canvas across all configured browsers. Both browser commands
must pass: the first proves the root deployment and the second proves the
configured `/battleship/` deployment.

## Full repository gate

```bash
scripts/verify.sh
```

Expected results:

- Maven verification, frontend build, frontend tests/lint, mock-browser E2E,
  and live packaged-JAR E2E pass.
- docs/openapi.json remains unchanged; any generated diff must be inspected
  before claiming completion.
- No Java, REST, SSE, adapter, or persistence changes are needed for this
  feature.

Also run the agent configuration check when agent files are touched:

```bash
python3 scripts/sync-agent-files.py --check
```

## Manual smoke check

```bash
frontend/node/npm --prefix frontend run dev:mock
```

Open the printed local URL, create a game, reach the waiting room, and confirm
that Show QR code opens the accessible Sheet only when selected. Confirm the
Copy link feedback remains usable, the QR ready presentation is visible, and
the Sheet can be closed with its button, Escape, and backdrop. Exact
Copy-link/QR destination equality remains owned by the focused tests because
the manual smoke check does not decode the canvas. Stop the dev server after
the check.
