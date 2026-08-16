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
frontend/src/config/appConfig.ts     # one normalized browser runtime export
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
the environment-agnostic configuration/helper boundary and exports
`AppBasePathConfigError`,
`normalizeAppBasePath(raw: string | undefined): string`,
`toViteAssetBasePath(normalizedBasePath: string): string`, and
`buildCanonicalJoinUrl(origin: string, normalizedBasePath: string, sessionId: string): string | null`.
`frontend/src/config/appConfig.ts` exports the one browser runtime value
`APP_BASE_PATH: string`, normalized from Vite's `import.meta.env.BASE_URL`.
The logical normalized path is `/` for empty/root input or a single-leading,
slashless path for non-root input. `BrowserRouter` and join-URL construction
consume that logical form; Vite derives its asset form as `/` for root or the
same path with one trailing `/` for a subpath. All three forms come from the
same normalizer, with no duplicated normalization rules. Query/hash/current-
route suffixes throw `AppBasePathConfigError` with code
`INVALID_APP_BASE_PATH`; an invalid configured build exits non-zero. Empty or
whitespace session IDs return `null`, withholding both sharing consumers. The
router route table, adapters, hooks, backend, and OpenAPI remain unchanged.

## Contract and Data Flow Plan

### Backend/API

- Route/controller: none; the existing `/join` route remains the destination.
- DTO/OpenAPI: none; `docs/openapi.json` must remain semantically and byte-for-byte unchanged after the gate's normalization check.
- Application API, engine, persistence, and events/SSE: none.

### Frontend

- Adapter/service: none; do not add a `GameAdapter` method or network call.
- Configuration/helper: `appBasePath.ts` exports `AppBasePathConfigError` (`code: 'INVALID_APP_BASE_PATH'`), the pure normalizer, the pure Vite asset-base derivation, and the canonical URL builder. The normalizer returns the logical path (`/` or a non-root path without a trailing slash) and throws the typed error for query/hash/current-route suffixes. `vite.config.ts` loads the raw value with `loadEnv`, uses the shared functions, and fails the build non-zero for invalid input. `appConfig.ts` exports `APP_BASE_PATH`, normalized from `import.meta.env.BASE_URL`; `index.tsx` and `WaitScreen` consume that same runtime value without renormalizing it. The builder returns `null` for empty/whitespace session IDs, uses `new URL`, selects `/join` for root or `${basePath}/join` for subpaths, and sets `id` exclusively through `searchParams.set`. Tests use WHATWG `URLSearchParams` expected serialization rather than `encodeURIComponent`.
- Hook/state: `WaitScreen` owns the guarded valid waiting-session context, `qrOpen`, and canonical URL. A valid acceptance fixture has a non-empty session ID, player identity, reachable `WAITING_FOR_PLAYERS` status, and resolved initial waiting-room loading; missing, other-status, or unresolved-loading contexts withhold the action. `QrCodeSheet` uses a `not requested/loading/ready/error` state machine and an effect-local cancellation flag so late or rejected completions are never committed as ready after close/unmount. Renderer fulfillment is the readiness boundary; pixel-level QR decoding is not required.
- UI: add the waiting-room action, render `QrCodeSheet` beside the share card, add localized title/instruction/loading/error/close/canvas-description strings, and add a responsive QR presentation style without changing `Sheet`. `screens.qr.description` is the localized accessible label of the ready canvas (`role="img"`); the visible scan instruction is separate. Use a nominal 300 CSS-pixel square with margin 2 and error correction M, scaling proportionally within the Sheet content area down to 320 CSS-pixel viewport width.
- Browser behavior: mock Playwright proves the action is absent from the initial QR presentation, visible only for a valid waiting context, successfully rendered, closable, localized, responsive, and paired with the existing join route. The real create-and-share flow carries the opaque session ID issued by `MockGameAdapter` through the canonical URL and proves exact join-field prefill without submitting it; a separate direct-route fixture uses a valid UUID to prove existing join validation remains unchanged. Focused helper/consumer tests use defensive escaped-character fixtures and mock the QR module boundary to assert WHATWG serialization, byte-for-byte Copy-link/QR equality, the 300px/option contract, logical-to-Vite base-path forms, typed invalid-configuration failure, absent URL for empty session IDs, loading-before-ready, module/render failure, and stale-state behavior. Browser fault injection is not required, and neither opaque transport proof nor escaped fixtures broaden `JoinGameScreen` validation.

## Implementation Phases

### Phase 0: Research and Baseline

- Confirmed the active feature pointer, specification, requirements checklist, constitution, approved design, current worktree, waiting-room implementation, router, localization parity test, `Sheet`, package manifests, Playwright setup, and verification gate.
- Resolved the planning decisions recorded by clarification: the one build-time base-path setting is authoritative for Vite asset output, `BrowserRouter` basename, and the canonical URL helper; empty/root/leading/trailing/path-only normalization is explicit; the session ID is intentionally shareable under existing authorization; and the valid-session acceptance population excludes missing, non-waiting, and unresolved-loading contexts.
- Confirmed the approved local `dev.tools` reference uses dynamic `import('qrcode')` and `toCanvas` with nominal width 300, margin 2, and error-correction level `M`; supported narrow viewport behavior is bounded at 320 CSS pixels with proportional scaling and no horizontal overflow.
- T001 owns the live baseline capture, including staged and unstaged feature artifacts, bridge snapshots, focused frontend results, full-gate capability results, and the checklist-to-task map. The plan does not assert a baseline result; T001 records it from the implementation starting point.

### Phase 1: Backend Contract and Engine

Not applicable. Record the explicit no-backend decision in the implementation
task evidence and verify that Java sources, REST/SSE tests, and
`docs/openapi.json` remain unchanged.

### Phase 2: Foundational Frontend Configuration

T002-T004 establish the dependency, pure logical base-path/URL helper, and
derived Vite/router wiring before the user-story tasks begin.

### Phase 3: User Story 1 - Show a Session QR Code on Demand

T005 implements the QR widget and T006 integrates it into the valid waiting-room
share card with focused tests for lazy generation and existing behavior.

### Phase 4: User Story 2 - Scan the Same Public Destination as Copy Link

T007 proves URL/Copy-link/QR/JoinGameScreen conformance and T008 proves the root
and configured-subpath mock-browser journeys by separating opaque adapter-ID
transport/prefill from valid-UUID validation proof.

### Phase 5: User Story 3 - Use and Dismiss the Presentation Accessibly

T009 covers Sheet-backed accessibility and lifecycle behavior, T010 covers
responsive styling/build proof, and T011 covers visible browser dismissal,
recovery, localization, and narrow-viewport behavior. Deterministic library and
renderer failures remain focused-test evidence.

### Phase 6: Integration, Artifacts, and Verification

T012 records focused acceptance evidence and T013 runs the full gate, reviews
generated artifacts, and records closeout evidence.

Each task branch must contain one task's changes and one commit. Task branches
must be based on the feature branch's current parent state and must not merge
directly into `master`.

### Task Branch and Commit Map

The following map mirrors the current T001-T013 ledger exactly:

| Task | Branch | Responsibility |
|---|---|---|
| T001 | `feature/qr-session-sharing--baseline` | Capture the baseline and preserve checklist/worktree boundaries. |
| T002 | `feature/qr-session-sharing--qr-dependency` | Add and verify browser QR dependency metadata. |
| T003 | `feature/qr-session-sharing--canonical-url` | Add the pure base-path normalizer, canonical URL helper, and tests. |
| T004 | `feature/qr-session-sharing--base-path-wiring` | Wire logical and derived asset base paths into Vite and the router; prove root and subpath builds. |
| T005 | `feature/qr-session-sharing--qr-widget` | Implement the QR presentation, localization, renderer lifecycle, and widget tests. |
| T006 | `feature/qr-session-sharing--wait-screen-integration` | Integrate the action, canonical URL, localization, valid-session guard, and existing share behavior with screen tests. |
| T007 | `feature/qr-session-sharing--url-join-conformance` | Add focused URL/Copy-link/QR/JoinGameScreen conformance coverage. |
| T008 | `feature/qr-session-sharing--mock-browser-join` | Add root and configured-subpath mock-browser journey coverage. |
| T009 | `feature/qr-session-sharing--sheet-accessibility-tests` | Add Sheet-backed QR accessibility, lifecycle, and locale assertions. |
| T010 | `feature/qr-session-sharing--qr-responsive-style` | Complete responsive QR styling and build/lint proof. |
| T011 | `feature/qr-session-sharing--mock-browser-accessibility` | Add visible dismissal, recovery, localization, and narrow-viewport browser proof. |
| T012 | `feature/qr-session-sharing--focused-evidence` | Record focused acceptance evidence and checklist state without changing markers. |
| T013 | `feature/qr-session-sharing--full-gate-closeout` | Run the full gate, inspect artifacts, and record closeout evidence. |

### Phase Dependencies

- T001 precedes all implementation. T002 and T003 run in parallel; T004
  depends on T003. T005 depends on T002/T004, and T006 depends on T005/T004.
- T007, T008, and T009 run in parallel after T006. T010 may start as soon as
  T005 completes and can run alongside T006 or T007-T009 because it owns only
  `QrCodeSheet.css`; T006 exclusively owns `WaitScreen.css`. T011 depends on
  T007-T010. T012 depends on all story checkpoints, and T013 depends on T012.
- The task ledger marks T002, T003, T007, T008, T009, and T010 as parallel
  tasks. Parallel tasks have distinct file ownership after their declared
  dependencies; if that changes, stop and rebase rather than conflicting.

### Documentation and Close

- Keep `README.md`, `docs/index.md`, and `docs/architecture.md` unchanged unless implementation evidence shows their current context is inaccurate.
- Record acceptance-criterion-to-test mappings in T012/T013 and use `quickstart.md` as the runnable validation guide, including the `JoinGameScreen.test.tsx` prefill check.
- Run `scripts/verify.sh` from the final implementation state; separately report any localhost binding or runtime capability failure.

## Verification Plan

| Concern | Evidence | Acceptance coverage |
|---|---|---|
| Canonical URL and shared base path | `frontend/src/config/appBasePath.test.ts` plus root/subpath build assertions with origin/base-path/session matrix, exact logical-to-Vite forms, invalid configuration, empty/whitespace session rejection, and shared Vite/router/helper configuration | FR-005, FR-006, SC-002 |
| Lazy QR lifecycle and presentation constraints | `frontend/src/widgets/sharing/QrCodeSheet.test.tsx` with mocked QR module loading and `toCanvas`, 300px/options, canvas `role="img"` and localized `aria-label`, loading-before-ready, rejection/error, and stale completion cases | FR-002-FR-004, FR-008-FR-009, SC-001, SC-004 |
| Waiting-room and join behavior | `frontend/src/screens/WaitScreen.test.tsx` proves sharing compatibility/equality; `frontend/src/screens/JoinGameScreen.test.tsx` uses a valid UUID to preserve validation | FR-001, FR-005-FR-006, FR-011, SC-001-SC-003, SC-006 |
| Locale/accessibility parity | existing `frontend/src/i18n/keyParity.test.ts`, widget assertions for role/status/close/title | FR-003, FR-007, FR-010, SC-004, SC-005 |
| Visible user journey | `frontend/e2e/qr-session-sharing.spec.ts` under the mock adapter, including exact opaque adapter-ID transport/prefill without submission, a separate valid-UUID direct-route validation fixture, a 320px viewport, dismissal/reopen, localization, and join navigation; deterministic module/render failure remains focused-test evidence | FR-001-FR-003, FR-005, FR-007, FR-010-FR-011, SC-001, SC-003-SC-006 |
| Build and lint | `frontend/node/npm --prefix frontend run lint`, frontend build through Maven, plus explicit root/subpath build checks | packaging and reproducibility |
| Full gate | `scripts/verify.sh` | SC-007; OpenAPI remains unchanged |
| Agent configuration | `python3 scripts/sync-agent-files.py --check` | repository workflow integrity |

The browser journey cannot decode a canvas as a physical phone would. The
exact QR payload is proven by the focused `toCanvas` call assertion; the
browser journey proves the rendered presentation and exact transport/prefill
of the adapter-issued ID. The separate valid-UUID fixture proves the existing
join validation boundary without changing mock-adapter behavior.

## Complexity and Risk Factors

- A deployment base path affects asset URLs and client routing in addition to
  the QR payload; all three consumers must use the same normalized setting.
- The session ID is intentionally shareable, so the feature must preserve the
  existing authorization/validation boundary and must not invent QR-specific
  secrecy, expiry, or access-control behavior.
- `qrcode` generation is asynchronous and cannot be cancelled at the package
  boundary; the component must ignore late completions and must not show the
  canvas as ready before the renderer reports successful completion.
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
