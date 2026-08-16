# QR Session Sharing Verification

**State:** T001-T011 implementation and focused/browser evidence are complete on
the task branches through `feature/qr-session-sharing--focused-evidence`.

## Command results

| Evidence | Command/result |
|---|---|
| Focused frontend tests | `NODE_OPTIONS=--localstorage-file=/private/tmp/battleship-qr-t012.localstorage frontend/node/npm --prefix frontend run test -- src/config/appBasePath.test.ts src/widgets/sharing/QrCodeSheet.test.tsx src/design/components/Sheet/Sheet.test.tsx src/screens/WaitScreen.test.tsx src/screens/JoinGameScreen.test.tsx src/i18n/keyParity.test.ts` — **PASS**, 6 files, 75 tests. The storage-file option is required by the pinned jsdom runtime; the baseline records the same pre-existing failure without it. |
| Lint | `frontend/node/npm --prefix frontend run lint` — **PASS**, 0 errors and 5 existing Fast Refresh warnings. |
| Root build | `frontend/node/npm --prefix frontend run build` — **PASS**; `frontend/build/index.html` references `/assets/...`. |
| Configured build | `VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run build` — **PASS**; `frontend/build/index.html` references `/battleship/assets/...`. |
| Invalid query | `VITE_APP_BASE_PATH='/battleship/?preview=1' frontend/node/npm --prefix frontend run build` — **EXPECTED FAIL** with exit 1, `INVALID_APP_BASE_PATH`, and the rejected raw value. |
| Invalid hash | `VITE_APP_BASE_PATH='/battleship/#preview' frontend/node/npm --prefix frontend run build` — **EXPECTED FAIL** with exit 1, `INVALID_APP_BASE_PATH`, and the rejected raw value. |
| Invalid reserved route | `VITE_APP_BASE_PATH='/battleship/game/wait' frontend/node/npm --prefix frontend run build` — **EXPECTED FAIL** with exit 1, `INVALID_APP_BASE_PATH`, and the rejected raw value. |
| Root browser journey | `frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts` — **PASS**, Chromium, Firefox, and WebKit, 3 tests. Scoped host access was required for the local Playwright server. |
| Configured browser journey | `VITE_APP_BASE_PATH=/battleship/ frontend/node/npm --prefix frontend run test:e2e -- e2e/qr-session-sharing.spec.ts` — **PASS**, Chromium, Firefox, and WebKit, 3 tests. |

The browser journey proves real creation through the existing mock adapter,
absence before selection, ready presentation, Close/Escape/backdrop dismissal,
focus restoration, fresh reopen, English/Ukrainian labels, 320px bottom-sheet
layout, no horizontal overflow, opaque-ID route resolution, exact join prefill,
and the unchanged valid-UUID submission boundary. The focused tests own QR
module/render failure, pending close/unmount, stale completion, recovery,
canvas accessibility, and Copy-link/QR byte equality.

## Acceptance criteria mapping

| Criterion | Result and evidence |
|---|---|
| FR-001 | **PASS** — `WaitScreen` integration tests and both browser deployments expose the action only for the valid waiting session. |
| FR-002 | **PASS** — browser checks no dialog/canvas before selection and on-demand opening. |
| FR-003 | **PASS** — focused `QrCodeSheet` tests prove one canvas, localized instruction, and no copy/download/export controls. |
| FR-004 | **PASS** — `qrcode` 1.5.4 and matching types are pinned in package metadata; focused tests prove the renderer boundary. |
| FR-005 | **PASS** — base-path matrix tests and root/configured builds prove normalized protocol, host, port, path, route, and encoded ID construction. |
| FR-006 | **PASS** — focused WaitScreen consumer tests prove byte-for-byte Copy-link/QR equality and one configured base path. |
| FR-007 | **PASS** — unchanged `Sheet` owns dialog semantics, with focused and browser evidence for Close, Escape, backdrop, focus, and responsive sizing. |
| FR-008 | **PASS** — focused tests prove loading, ready, renderer error, and library rejection semantics. |
| FR-009 | **PASS** — focused deferred tests prove pending close/unmount ignores late results and reopening starts a fresh generation. |
| FR-010 | **PASS** — key parity, focused locale cases, and the browser journey prove English/Ukrainian action, title, instruction, status/error, description, and Close text. |
| FR-011 | **PASS** — existing waiting-room, copy, refresh/poll, navigation, join, and route-resolution tests remain green; no Java/API/OpenAPI files changed. |

| Success criterion | Result and evidence |
|---|---|
| SC-001 | **PASS** — valid-session and withheld-action fixtures plus root/subpath journeys. |
| SC-002 | **PASS** — hostname/IP/protocol/port/root/subpath and encoding matrix in `appBasePath.test.ts` and consumer tests. |
| SC-003 | **PASS** — browser journey preserves the opaque adapter ID for route/prefill proof and separately uses the specified valid UUID for validation/submission. |
| SC-004 | **PASS** — all three dismissal paths, focus restoration, fresh reopen, ready canvas, 320px computed bounds, and document/body overflow checks pass in three browsers. |
| SC-005 | **PASS** — English/Ukrainian resource parity and visible browser labels pass. |
| SC-006 | **PASS** — existing copy/lifecycle/join behavior remains green in the focused suite and browser journey. |

## Checklist evidence

The reviewer-owned checklist markers were not changed.

- `checklists/requirements.md`: **16/16 checked** — Content Quality 4/4,
  Requirement Completeness 8/8, Feature Readiness 4/4.
- `checklists/quality.md`: **40/40 checked** — CHK001-CHK007,
  CHK008-CHK013, CHK014-CHK018, CHK019-CHK023, CHK024-CHK028,
  CHK029-CHK032, CHK033-CHK036, and CHK037-CHK040 all remain checked and
  are covered by the acceptance mapping and task-owned evidence above.

## Limitations carried from baseline

- The pinned frontend test command needs `NODE_OPTIONS=--localstorage-file=...`
  in this environment because jsdom otherwise exposes no usable localStorage;
  this is a pre-existing test-environment issue, not QR behavior.
- Playwright needs scoped host access here because the default sandbox rejects
  the local server bind; all three configured browsers pass after that retry.
- The full repository gate remains owned by T013. No Java, REST, persistence,
  or `docs/openapi.json` change is expected for this frontend-only feature.

## T013 closeout

`rtk scripts/verify.sh` was run from the packaged-JAR-capable state and exited
non-zero during Maven Surefire before frontend tests, mock-browser E2E, or live
JAR E2E could start: **380 tests, 0 failures, 118 errors**. The errors are the
pre-existing Java 25 Amazon Corretto 25.0.4 Mockito inline Byte Buddy failure:
the JVM cannot self-attach the Mockito agent. This matches the T001 baseline.

The non-project workaround
`JAVA_TOOL_OPTIONS='-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true'`
was also tried and produced the same 380/118 failure, now reporting that the
target JVM did not respond to the attach socket. No test or production code was
changed to hide this environment limitation.

Final artifact review:

- `git diff HEAD -- src/main/java docs/openapi.json frontend/package.json frontend/package-lock.json README.md docs` is empty.
- After the T013 commit, `git status --short --branch` reports only the current
  feature branch header; there are no tracked or untracked changes. Target and
  build outputs are ignored.
- The final packaged frontend build exists under `frontend/build` and the last
  Maven build copied the root deployment assets into the JAR staging tree.
- No agent/configuration files were touched, so the conditional
  `scripts/sync-agent-files.py --check` was not required.
- There is no registered `after_implement` hook in `.specify/extensions.yml`;
  the mandatory pre-implement hooks were already recorded in T001.
