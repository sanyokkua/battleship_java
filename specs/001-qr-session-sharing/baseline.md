# QR Session Sharing Baseline

Captured on 2026-08-16 from branch `feature/qr-session-sharing--baseline`,
before QR implementation changes.

## Worktree and artifact baseline

Commands:

```text
git status --short --branch
## feature/qr-session-sharing--baseline
 M .specify/bridge-events.jsonl

git diff --cached --stat
(no output; no staged changes)

git diff HEAD --stat
 .specify/bridge-events.jsonl | 1 +
 1 file changed, 1 insertion(+)

git diff -- src/main/java docs/openapi.json
(no output)
```

The one unstaged change is the bridge guard's audit event for this
implementation invocation. No source, test, frontend manifest, Java, REST,
SSE, or OpenAPI change was present at baseline. The generated
`frontend/build/` directory existed after the baseline build and is ignored by
the repository.

## Baseline verification

The repository-pinned frontend runtime was available as Node `v24.18.0` with
npm `11.16.0`.

| Command | Result | Evidence |
|---|---|---|
| `frontend/node/npm --prefix frontend run test` | FAIL (pre-existing) | 47 files: 38 passed, 9 failed; 329 tests passed, 81 failed, 410 total. The failures cascade from `localStorage` being undefined under the pinned jsdom/Node setup, including existing AppBar, routing, WaitScreen, PreparationScreen, GameplayScreen, and ResultsScreen tests. |
| `frontend/node/npm --prefix frontend run build` | PASS | TypeScript and Vite build completed; `frontend/build/index.html` and one CSS/JS asset pair emitted. |
| `frontend/node/npm --prefix frontend run lint` | PASS with existing warnings | 0 errors, 5 existing `react-refresh/only-export-components` warnings. |
| `scripts/verify.sh` | FAIL (capability/pre-existing) | Maven reached Surefire and reported 380 tests, 0 failures, 118 errors. Mockito could not initialize its inline Byte Buddy mock maker because Java 25 could not self-attach the agent. The gate stopped in Maven before QR-specific frontend/E2E verification. |

The baseline results are evidence of the starting state, not acceptance of
those failures as final feature status. The implementation must preserve this
separation and report any changed result explicitly.

## Reviewer-owned checklist status and task ownership

The checklist markers were not changed.

| Checklist | Total | Checked | Unchecked | Status |
|---|---:|---:|---:|---|
| `checklists/requirements.md` | 16 | 16 | 0 | PASS |
| `checklists/quality.md` | 40 | 40 | 0 | PASS |

Every requirements-quality item is assigned to the planned evidence owners:

| Requirement checklist item(s) | Evidence owner task(s) |
|---|---|
| `requirements.md` content/readiness items 1-16 | T001, T012, T013; checklist remains reviewer-owned |
| CHK001-CHK007 | T005, T006, T009, T011 |
| CHK008-CHK013 | T003, T005, T009, T010, T011 |
| CHK014-CHK018 | T001, T002, T004, T006, T012, T013 |
| CHK019-CHK023 | T005, T006, T007, T008, T012, T013 |
| CHK024-CHK028 | T005, T006, T008, T009, T011 |
| CHK029-CHK032 | T003, T005, T007, T009, T012 |
| CHK033-CHK036 | T005, T009, T010, T011 |
| CHK037-CHK040 | T001, T003, T004, T007, T012, T013 |

This mapping is a baseline record only. It does not mark implementation tasks
complete and does not alter any reviewer-owned marker.

## Baseline boundary

The approved feature remains frontend-only. `src/main/java` and
`docs/openapi.json` are expected to remain unchanged throughout
implementation. Any later change to either boundary requires explicit review
and must be recorded in `verification.md` before completion is claimed.
