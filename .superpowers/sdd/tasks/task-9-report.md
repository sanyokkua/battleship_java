# T009 implementation report

## Outcome

Implemented explicit invitation redemption for the frontend against the packaged backend. The fragment is captured and removed in `main.tsx` before React renders. Joining now requires a captain name and a deliberate click; the gateway performs one `request-security-rejected` retry, remembers the joined game, and stores the server snapshot. The screen distinguishes invitation-unavailable, browser-game-limit, and expired-game outcomes. The secret is cleared from module and component state when the join settles.

## RED evidence

`cd frontend && npm run e2e -- e2e/invitation.spec.ts` against the original UI (with browser permission enabled) failed as expected: the first-render journey reported the original `/join/<id>#invite=<secret>` URL instead of a fragment-free URL. The initial default-sandbox attempt could not start Chromium (`bootstrap_check_in ... Permission denied`); the same command with browser permission ran the real backend and produced the behavioral failure above.

## GREEN evidence

- `cd frontend && npm run build` — passed; TypeScript and Vite production build succeeded.
- `cd frontend && npm run e2e -- e2e/invitation.spec.ts` — passed, **6 tests** against the packaged backend. Covers pre-render stripping/history, no pre-click join, one successful join, secret scans, live host update, lost answer/no retry, one CSRF retry, two-guest race, browser-game-limit with remembered-game return, and 410 expiry after the guest session expires.
- `git diff --check` — passed.

## Mutation proofs

Each mutation was temporary, rebuilt into the production bundle, and reverted before final verification.

| Mutation | Proof command | Observed failure |
|---|---|---|
| Move fragment stripping from `main.tsx` into `JoinInvitation` effect | `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts -g 'stripped before render'` | Failed first-render hash assertion: expected `""`, received `"#invite=<secret>"`. |
| Auto-join on component mount | `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts -g 'stripped before render'` | Failed no-request-before-click assertion: expected 0 join requests, received 1. |
| Map every join 409 to invitation-unavailable | `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts -g 'browser game limit'` | Failed browser-game-limit outcome assertion; the distinct alert and Return to your game were absent. |
| Map join 410 to invitation-unavailable | `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts -g 'expired game invitation'` | Failed expired-session heading assertion; the Session unavailable “game expired” screen was absent. |

## Changed files

- `frontend/src/main.tsx`
- `frontend/src/session/inviteSecret.ts`
- `frontend/src/gateway/gameGateway.ts`
- `frontend/src/screens/JoinInvitation.tsx`
- `frontend/src/app/App.tsx`
- `frontend/src/i18n/en.ts`
- `frontend/src/i18n/uk.ts`
- `frontend/e2e/invitation.spec.ts`

T009 was reviewed by the controller and its checkbox was set there. This follow-up does not alter task checkboxes. T010 was not modified.

## Commit

Implementation commit: `87b3da6b6545dd361384e33a696aeb3592f8c5ef` (`Implement explicit invitation joining`).

## History-proof follow-up

Updated the real-backend invitation journey to navigate from `/join/<id>` to Home through SPA `pushState`/`popstate`, go back, and assert the restored pathname is `/join/<id>` with an empty hash and no join POST before the user clicks Join. This proves the invitation fragment was removed from the actual history entry.

- **RED mutation:** Temporarily removed `history.replaceState` from `stripInvitationSecret`. `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts -g 'stripped before render'` failed at the restored-history hash assertion: expected `""`, received `"#invite=<secret>"`.
- **GREEN:** Restored `history.replaceState`; `cd frontend && npm run build && npm run e2e -- e2e/invitation.spec.ts` passed the build and all 6 E2E cases.
- **Scope:** Test and report only; no production source changes and no task checkbox changes.

Follow-up commit: `24584f53723f1a608b200b30bc3321c31dbce78e` (`Prove invitation history fragment removal`).
