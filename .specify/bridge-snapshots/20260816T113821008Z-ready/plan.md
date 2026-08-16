# Implementation Plan: QR Session Sharing

**Branch**: `feature/qr-session-sharing` | **Date**: 2026-08-16 | **Spec**: [`spec.md`](spec.md)

**Input**: Feature specification from `specs/001-qr-session-sharing/spec.md`

This plan is dependency-ordered. Implementation must use task branches under
the feature branch, one task per branch and commit, preserve unrelated
worktree changes, and leave final integration to the user.

## Summary

Add an on-demand QR action beside the waiting-room Copy and Copy link actions.
The smallest proving slice is: derive one absolute join URL from the browser
origin and configured application base path, open the existing accessible
`Sheet`, lazily render that URL into a canvas with `qrcode`, and prove the
loading/ready/error/dismissal behavior in focused and mock-browser tests.

The feature is frontend-only. No Java source, adapter method, REST/SSE
contract, persistence, route entry, or OpenAPI artifact changes are planned.

## Technical Context

**Backend Language/Version**: Java 25 (not changed)

**Frontend Language/Version**: TypeScript with React 19

**Build Tools**: Maven; `frontend-maven-plugin` installs Node v24.18.0 and runs npm; Vite builds to `frontend/build`

**Primary Dependencies**: React Router, i18next/react-i18next, `qrcode` 1.5.x with `@types/qrcode` 1.5.x, Vitest/Testing Library, Playwright

**Storage**: No new storage. The existing in-memory game session and browser session context remain authoritative.

**Testing**: Vitest/Testing Library for the URL helper, QR widget, waiting room, and localization; mock-browser Playwright for the user journey; the repository gate for packaged-JAR, OpenAPI, lint, and full regression evidence.

**Target Platform**: Browser client served by the Spring Boot JAR or the Vite mock-adapter server; desktop and narrow mobile viewports.

**Project Type**: Full-stack web service with a frontend-only sharing feature.

**Performance Goals**: Selecting `Show QR code` must open the Sheet before QR generation completes and show a localized loading state while package loading or rendering is pending. QR work must not run during the initial waiting-room render; no numeric generation-time SLA is specified. The nominal QR display is 300 CSS pixels with margin 2 modules and error-correction level M, scaling proportionally within the Sheet content area for supported viewport widths down to 320 CSS pixels without horizontal overflow.

**Constraints**: Preserve the single-JAR packaging, `GameAdapter` boundary, existing join route, in-memory session model, REST/SSE contracts, bilingual localization, accessible `Sheet` behavior, and `scripts/verify.sh`. No backend, external-service, persistence, authentication, QR-specific masking/encryption/expiry/authorization, or QR export scope may be introduced. The existing session validation and authorization remain authoritative because the session ID is intentionally shareable through Copy link and QR.

**Scale/Scope**: One new URL/config helper, one QR widget and stylesheet, waiting-room integration, shared base-path wiring in Vite/router, two locale resource updates, frontend dependency metadata, focused tests, and one mock-browser journey. No Java or generated API files.

## Constitution Check

*GATE: Must pass before implementation. Re-check after design and before final verification.*

- [x] Runtime authority is identified in `WaitScreen`, routing/configuration, locale resources, and existing tests; the approved design and docs provide context only.
- [x] Backend dependency direction is unchanged because no backend layer is affected.
- [x] Frontend network access remains behind `GameAdapter`; QR generation is local browser work and the widget receives a URL prop rather than accessing adapters or storage.
- [x] REST/SSE, DTO, mock-adapter, and OpenAPI effects are explicitly `none`; `docs/openapi.json` is expected to remain unchanged and will be checked by the gate.
- [x] In-memory persistence, authentication scope, and external-service scope are unchanged.
- [x] Focused Vitest/Testing Library and mock-browser tests are planned; Java tests are unchanged because no Java behavior changes.
- [x] Generated artifact and documentation effects are listed: package/build output changes only, no OpenAPI change, and feature-local validation artifacts are added.
- [x] Final evidence includes `scripts/verify.sh`, with environment/capability limitations reported separately from product failures.

## Existing Project Structure

```text
frontend/src/config/                 # new shared application base-path/URL helper
frontend/src/index.tsx               # BrowserRouter basename wiring
frontend/src/screens/WaitScreen.tsx  # waiting-room share action and open state
frontend/src/widgets/sharing/        # new QrCodeSheet widget and CSS
frontend/src/i18n/{en,uk}/screens.json
frontend/src/routing/                # existing route table remains unchanged
frontend/e2e/                         # new mock-browser QR journey
frontend/package.json                 # qrcode runtime/type dependency metadata
frontend/package-lock.json            # lockfile update
frontend/vite.config.ts               # shared VITE_APP_BASE_PATH build base
```

**Structure Decision**: Keep the screen/widget/design boundary. `WaitScreen`
owns the guarded waiting-room session context, canonical URL result, and
`qrOpen`; the new `QrCodeSheet` owns QR status and lazy canvas generation; the
existing `Sheet` remains unchanged. `frontend/src/config/appBasePath.ts` is
the single client configuration/helper boundary. The one build-time
application base-path setting is normalized once as: empty or `/` -> `/`,
otherwise exactly one leading `/`, no trailing `/`, internal segments
preserved, and no query/hash/current-route suffix. `frontend/vite.config.ts`,
`index.tsx`, and the URL helper consume that same normalized value for asset
paths, router basename, and join URLs. The router route table, adapters, hooks,
backend, and OpenAPI remain unchanged.

## Contract and Data Flow Plan

### Backend/API

- Route/controller: none; the existing `/join` route remains the destination.
- DTO/OpenAPI: none; `docs/openapi.json` must remain semantically and byte-for-byte unchanged after the gate's normalization check.
- Application API, engine, persistence, and events/SSE: none.

### Frontend

- Adapter/service: none; do not add a `GameAdapter` method or network call.
- Configuration/helper: `appBasePath.ts` normalizes the one build-time application base-path setting (default `/`) using the exact root/leading/trailing/internal-segment/path-only rules, exposes the value for Vite and the router, and builds an absolute join URL with `new URL` and `searchParams.set('id', sessionId)`.
- Hook/state: `WaitScreen` owns the guarded valid waiting-session context, `qrOpen`, and canonical URL. A valid acceptance fixture has a non-empty session ID, player identity, waiting status, and resolved initial waiting-room loading; missing, non-waiting, or unresolved-loading contexts withhold the action. `QrCodeSheet` uses a `not requested/loading/ready/error` state machine and an effect-local cancellation flag so late, partial, blank, or failed completions are never committed as ready after close/unmount.
- UI: add the waiting-room action, render `QrCodeSheet` beside the share card, add localized title/instruction/loading/error/close/canvas-description strings, and add a responsive QR presentation style without changing `Sheet`. Use a nominal 300 CSS-pixel square with margin 2 and error correction M, scaling proportionally within the Sheet content area down to 320 CSS-pixel viewport width.
- Browser behavior: mock Playwright proves the action is absent from the initial QR presentation, visible only for a valid waiting context, successfully rendered, closable, localized, and paired with the existing join route. Focused tests stub `qrcode.toCanvas` to assert the exact URL, 300px/option contract, path-normalization matrix, loading-before-ready, and failure/stale-state behavior.

## Implementation Phases

### Phase 0: Research and Baseline

- Confirmed the active feature pointer, specification, requirements checklist, constitution, approved design, current worktree, waiting-room implementation, router, localization parity test, `Sheet`, package manifests, Playwright setup, and verification gate.
- Resolved the planning decisions recorded by clarification: the one build-time base-path setting is authoritative for Vite asset output, `BrowserRouter` basename, and the canonical URL helper; empty/root/leading/trailing/path-only normalization is explicit; the session ID is intentionally shareable under existing authorization; and the valid-session acceptance population excludes missing, non-waiting, and unresolved-loading contexts.
- Confirmed the approved local `dev.tools` reference uses dynamic `import('qrcode')` and `toCanvas` with nominal width 300, margin 2, and error-correction level `M`; supported narrow viewport behavior is bounded at 320 CSS pixels with proportional scaling and no horizontal overflow.
- Baseline state to preserve: `feature/qr-session-sharing`; existing modified `spec.md`, existing checklist, and unrelated untracked `.specify/bridge-events.jsonl`. No baseline gate was run because this planning unit does not alter application code.

### Phase 1: Backend Contract and Engine

Not applicable. Record the explicit no-backend decision in the implementation
task evidence and verify that Java sources, REST/SSE tests, and
`docs/openapi.json` remain unchanged.

### Phase 2: Frontend Integration and UI

Implementation task units, in order:

1. **Canonical URL and base-path configuration** — task branch
   `feature/qr-session-sharing--canonical-url`; add the shared setting and
   helper, wire Vite asset base and `BrowserRouter` basename, and add helper
   tests for root/subpath origins, protocol/host/IP/port retention, encoded
   IDs, empty/root/leading/trailing/path-only normalization, internal path
   preservation, and no duplicated base path; also confirm the same normalized
   value is consumed by Vite asset base and router basename.
2. **QR widget and dependency** — task branch
   `feature/qr-session-sharing--qr-widget`; add `qrcode`/`@types/qrcode`,
   create `QrCodeSheet` and its CSS, and add tests for lazy generation,
   loading/ready/error states, hidden partial/blank results, 300px canvas
   options, 320px proportional sizing, close/unmount stale-result protection,
   accessible status/description, and no export/copy controls.
3. **Waiting-room integration and localization** — task branch
   `feature/qr-session-sharing--wait-screen`; update `WaitScreen` to reuse
   the canonical URL for Copy link and QR, add the action/open state, add
   English/Ukrainian keys, enforce the valid-session/withheld-action boundary,
   and extend `WaitScreen` tests without regressing Copy ID, Copy link, refresh,
   polling, or navigation.

### Phase 3: Cross-Layer and Browser Verification

4. **Mock-browser acceptance and verification** — task branch
   `feature/qr-session-sharing--browser-verification`; add the dedicated
   `frontend/e2e/qr-session-sharing.spec.ts` journey. Exercise the real
   waiting-room controls, valid-session/withheld-action boundary, QR Sheet,
   320px narrow viewport behavior, dismissal, generation failure, and the
   existing join screen. Run frontend focused tests/lint, the mock suite, Maven
   packaging, and inspect OpenAPI drift.

Each task branch must contain one task's changes and one commit. Task branches
must be based on the feature branch's current parent state and must not merge
directly into `master`.

### Phase 4: Documentation and Close

- Keep `README.md`, `docs/index.md`, and `docs/architecture.md` unchanged unless implementation evidence shows their current context is inaccurate.
- Record acceptance-criterion-to-test mappings in the task/close evidence and use `quickstart.md` as the runnable validation guide.
- Run `scripts/verify.sh` from the final implementation state; separately report any localhost binding or runtime capability failure.

## Verification Plan

| Concern | Evidence | Acceptance coverage |
|---|---|---|
| Canonical URL and shared base path | `frontend/src/config/appBasePath.test.ts` (or the selected helper test) with origin/base-path/session matrix, exact normalization boundaries, and shared Vite/router/helper configuration | FR-005, FR-006, SC-002 |
| Lazy QR lifecycle and presentation constraints | `frontend/src/widgets/sharing/QrCodeSheet.test.tsx` with mocked dynamic `qrcode` import and `toCanvas`, 300px/options, 320px scaling, loading-before-ready, hidden partial/blank results, and stale completion cases | FR-002, FR-004, FR-008, FR-009, SC-001, SC-004 |
| Waiting-room behavior and valid-session boundary | `frontend/src/screens/WaitScreen.test.tsx` | FR-001, FR-006, FR-011, SC-001, SC-006 |
| Locale/accessibility parity | existing `frontend/src/i18n/keyParity.test.ts`, widget assertions for role/status/close/title | FR-003, FR-007, FR-010, SC-004, SC-005 |
| User journey | `frontend/e2e/qr-session-sharing.spec.ts` under the mock adapter, including a 320px viewport and failure/reopen path | FR-001–FR-011, SC-001, SC-003–SC-006 |
| Build and lint | `npm --prefix frontend run lint`, frontend build through Maven | packaging and reproducibility |
| Full gate | `scripts/verify.sh` | SC-007; OpenAPI remains unchanged |
| Agent configuration | `python3 scripts/sync-agent-files.py --check` | repository workflow integrity |

The browser journey cannot decode a canvas as a physical phone would. The
exact QR payload is proven by the focused `toCanvas` call assertion; the
browser journey proves the rendered presentation and the existing join
destination accepts the same encoded session ID.

## Complexity and Risk Factors

- A deployment base path affects asset URLs and client routing in addition to
  the QR payload; all three consumers must use the same normalized setting.
- The session ID is intentionally shareable, so the feature must preserve the
  existing authorization/validation boundary and must not invent QR-specific
  secrecy, expiry, or access-control behavior.
- `qrcode` generation is asynchronous and cannot be cancelled at the package
  boundary; the component must ignore late completions and must not show a
  partially rendered canvas as ready.
- `Sheet` focus and dismissal behavior is shared infrastructure; tests should
  exercise it through `QrCodeSheet` without modifying the existing component.
- Cross-browser clipboard permissions are intentionally not required; the
  existing copy-to-clipboard fallback remains the source of Copy-link evidence.
- Maven owns the pinned frontend runtime. Host Node/npm results are not the
  authoritative full-gate result.

## Complexity Tracking

No constitution violation is requested. The base-path wiring is required by
FR-005 and the approved clarification, not an architectural expansion.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| None | N/A | N/A |

## Post-Design Constitution Check

- [x] The design preserves runtime authority and keeps the approved design as context rather than implementation truth.
- [x] No backend, adapter, persistence, authentication, external-service, REST/SSE, or OpenAPI boundary is crossed.
- [x] The URL helper and QR widget are covered by focused tests, while the user-visible flow is covered by mock-browser evidence.
- [x] The shared base-path setting is explicit, normalized, and used by both router and URL construction.
- [x] The final implementation remains subject to `scripts/verify.sh` and generated-artifact inspection.
