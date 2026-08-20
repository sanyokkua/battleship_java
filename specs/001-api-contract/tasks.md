---

description: "Dependency-ordered implementation tasks for the Anonymous Battleship API Contract"
---

# Tasks: Anonymous Battleship API Contract

**Input**: Clarified feature and generated design artifacts from `specs/001-api-contract/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, and `contracts/`

**Tests**: Required. The specification mandates contract-only scenarios and the plan defines deterministic lint, build, validation, test, and independent-consumer checks.

**Organization**: Tasks are grouped by user story. Each story starts with failing behavior checks, then implements the canonical artifacts that make those checks pass. Every task names owned paths and its requirement/proof scope.

**Scope guard**: Product implementation writes are limited to the root `contracts/` product. Repository integration writes are limited to `README.md`, `AGENTS.md`, `.specify/.gitignore`, generated agent mirrors, and current feature evidence explicitly named below. Do not add backend/frontend application code, runtime controllers, a rules engine, database/broker/cache authority, CI/deployment/hosting files, durable replay/idempotency, generated consumer packages, or recovery after process loss.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Safe to run in parallel after its stated prerequisites because it owns disjoint files.
- **[Story]**: Maps the task to a user story from `spec.md`.
- **Covers**: Governing requirement, success criterion, scenario, edge, or checklist concern.
- **Prove**: Proof identifier P01-P13 from `contracts/validation-matrix.md`, or an exact feature command.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the independently buildable contract product and its single canonical source.

- [ ] T001 Create the ESM npm product with Node `24.19.0`, exact plan-approved dependencies, and normative `lint`, `build`, `validate`, `validate:release`, `test`, `smoke:typescript`, `check:candidate`, and ordered final `check` scripts in `contracts/package.json`; T071 exclusively adds `baseline:create` after the candidate gate exists (Covers: FR-001, FR-005-FR-006, CHK001, CHK003; Prove: P01, P13)
- [ ] T002 Add and observe a failing dependency-free static product-control proof that inspects source/configuration without importing or invoking uninstalled packages for local-only tooling, telemetry/update-notice suppression, and precise ignore boundaries in `contracts/validation/tests/product-controls.test.mjs`; add or verify ignores for only reproducible dependencies, bundles, scratch data, temporary generated consumers, and per-checkout bridge state in `contracts/.gitignore` and `.specify/.gitignore`, prove ignored paths with `git check-ignore -v --no-index contracts/node_modules/.probe contracts/dist/openapi.json contracts/.tmp/generated/openapi.ts .specify/bridge-events.jsonl .specify/bridge-snapshots/.probe .specify/superpowers-handoff.json`, and prove canonical source plus `contracts/baselines/1.0.0/openapi.json` remain trackable (Covers: FR-002, SC-006, CHK001, CHK035; Prove: P01, P06, P13)
- [ ] T003 After T002's dependency-free static proof fails for the missing tool controls, add product-local `audit=false`, `fund=false`, and `update-notifier=false` controls in `contracts/.npmrc`, configure OpenAPI 3.1.1 lint/bundle rules and component conflict failures in `contracts/redocly.yaml`, implement the sole local Redocly entry point with telemetry and update notices disabled in `contracts/validation/run-redocly.mjs`, and make the static proof pass with `node --test validation/tests/product-controls.test.mjs` without an install (Covers: FR-004-FR-005, CHK002-CHK003; Prove: P01, P13)
- [ ] T004 After T003 establishes the install controls, generate the exact dependency lock without incidental upgrades in `contracts/package-lock.json` using the exact T001 dependencies and `npm install --package-lock-only --ignore-scripts`, run the first controlled `npm ci`, and invoke the installed CLI only through `node validation/run-redocly.mjs --version` to prove the wrapper is executable under those controls (Covers: FR-001, FR-005, CHK003; Prove: `cd contracts && npm ci && node validation/run-redocly.mjs --version`)
- [ ] T005 [P] Document contract-only ownership, canonical-versus-derived artifacts, exclusions, commands, and the no-HTTP-runtime limitation in `contracts/README.md` (Covers: FR-001-FR-002, FR-006, CHK001, CHK003, CHK035, CHK039; Prove: P13)
- [ ] T006 Add and observe a focused failing root-definition assertion in `contracts/validation/tests/canonical-graph.test.mjs`, then create the sole OpenAPI root with dialect, `1.0.0` contract identity, `/api/v1` identity, tags, and reference-only path/component ownership in `contracts/openapi/openapi.yaml` and make that focused assertion pass (Covers: FR-003-FR-006, SC-001, CHK002-CHK003; Prove: P01)

**Checkpoint**: The `contracts/` product installs reproducibly and has one documented canonical root, but its behavior checks are expected to fail until the following phases complete.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the shared wire vocabulary, problem model, HTTP components, oracle, and validation harness used by every story.

**Critical**: Complete this phase before any user-story implementation. T007-T009 are written first and must fail for missing contract behavior before T010-T015 make their foundational assertions pass.

- [ ] T007 [P] Extend T006 with a failing foundational graph/oracle test for one canonical root, the oracle registry's exact 11 operation IDs, uniqueness among currently referenced operation IDs, absence of aliases, reference resolution, dialect/version separation, public-safe invalid-cookie/no-session-side-effect classification, session-required invalid-cookie clearing, and the three health Origin cases in `contracts/validation/tests/canonical-graph.test.mjs`; reserve proof that the completed source and bundle expose exactly that surface for T060 (Covers: FR-003-FR-008, FR-017, FR-027, FR-064, SC-001, CHK002-CHK005, CHK008, CHK011, CHK023; Prove: P01, P10)
- [ ] T008 [P] Add failing primitive tests for base64url identifiers, UUID v4 command IDs, safe integers, zero-based coordinates, UTC millisecond timestamps, NFC names, and opaque strong entity tags in `contracts/validation/tests/wire-primitives.test.mjs` (Covers: FR-006, FR-008, FR-014-FR-018, FR-035-FR-040, CHK003, CHK007-CHK008, CHK015; Prove: P02, P10)
- [ ] T009 [P] Add a failing closed-catalog test for RFC 9457 shape, all 19 code/status pairs, language-neutral fields, bounded recovery, safe correlation, and forbidden data in `contracts/validation/tests/problem-contract.test.mjs` (Covers: FR-005, FR-024, FR-060-FR-064, CHK010, CHK022-CHK023; Prove: P08, P10, P12)
- [ ] T010 Implement shared identifiers, revisions, safe integers, instants, coordinates, display names, seats, phases, operation names, and tag primitives in `contracts/schemas/common.schema.json` (Covers: FR-006, FR-008, FR-014-FR-018, FR-029-FR-032, FR-035-FR-040; Prove: P02, P10)
- [ ] T011 Implement the closed language-neutral problem, validation-violation, conflict, retry, and recovery shapes in `contracts/schemas/problem.schema.json` (Covers: FR-005, FR-024, FR-055, FR-060-FR-063; Prove: P02, P08, P10)
- [ ] T012 [P] Define the canonical `gameId`, conditional-request, replay-cursor, Origin, CSRF, ETag, Retry-After, and redacted Set-Cookie parameters/headers in `contracts/openapi/components/parameters.yaml` and `contracts/openapi/components/headers.yaml` (Covers: FR-015-FR-018, FR-027-FR-028, FR-035-FR-040, FR-052-FR-060; Prove: P10-P12)
- [ ] T013 [P] Define only the foundational named problem responses, body-limit/media-type conventions, base session security, and references resolvable from the Phase 2 foundation in `contracts/openapi/components/responses.yaml` and `contracts/openapi/components/security-schemes.yaml`; defer snapshot success responses and operation request-body components to their later story owners (Covers: FR-004-FR-005, FR-017-FR-018, FR-027-FR-028, FR-062-FR-063; Prove: P01, P10)
- [ ] T014 Create the structured oracle with exactly 11 operation IDs, 19 problem codes, shared cache/CORS/security rules, public-safe-read session-insensitive outcome/security classification—same status, body schema, cache, and CORS class; never `invalid-session`; no session issue/refresh/clear; metadata's independent CSRF issue/refresh remains allowed—create/join/session-required invalid-session clearing, and the three health Origin cases in `contracts/validation/operation-expectations.json` (Covers: FR-003-FR-004, FR-017, FR-024, FR-027, FR-063-FR-064, CHK002, CHK008, CHK010-CHK011, CHK023; Prove: P10)
- [ ] T015 Implement bounded schema loading, reference resolution, primitive checks, problem-catalog checks, and the initial public validation entry point in `contracts/validation/lib/schema-validation.mjs`, `contracts/validation/lib/operation-validation.mjs`, and `contracts/validation/validate.mjs` (Covers: FR-004-FR-005, FR-008, SC-001-SC-002, CHK005, CHK025; Prove: P01-P02, P10)

**Checkpoint**: Shared schemas, HTTP components, the oracle, and the validator are ready. User-story work may now proceed in dependency order; only tasks explicitly marked `[P]` may overlap.

---

## Phase 3: User Story 1 - Create and Join a Private Game (Priority: P1) - MVP

**Goal**: Publish metadata and both rulesets, create one owner membership, carry one fragment-only invitation, join exactly one guest, and rotate safely without exposing authority.

**Independent Test**: Using only the contract fixtures, exercise metadata, both rulesets, create, explicit post-consent join, two-request redemption race, rotation, and unknown outcomes for two isolated browser identities; prove exactly two seats and zero capability/secret leaks.

### Tests for User Story 1

- [ ] T016 [P] [US1] Add failing metadata, exact lifecycle-policy constants, bounded service-availability shape, immutable-ruleset, display-name normalization, and exact-mechanics checks in `contracts/validation/tests/discovery-and-names.test.mjs` (Covers: FR-009-FR-014, FR-056, FR-058, FR-060-FR-061, SC-002-SC-003, CHK006-CHK007; Prove: P02-P03, P10, P12)
- [ ] T017 [P] [US1] Add failing locator, multi-game identity, public-safe session-insensitive outcome/security classification with no `invalid-session` or session `Set-Cookie` while allowing metadata's independent CSRF cookie, create/join/session-required invalid-session clearing, session/CSRF cookie, exact-Origin, Fetch Metadata, preflight, and redaction checks in `contracts/validation/tests/identity-and-request-security.test.mjs` (Covers: FR-015-FR-018, FR-027, SC-004, SC-008, CHK008, CHK011-CHK012; Prove: P04, P08, P10)
- [ ] T018 [P] [US1] Add failing create/join/rotate journey, concurrent redemption, same-identity second-seat, invitation race, gate-order, uniform-failure, and unknown-outcome checks in `contracts/validation/tests/create-join-invitation.test.mjs` (Covers: FR-019-FR-026, SC-003, SC-005, US1, CHK009-CHK010, CHK028, CHK033; Prove: P03, P05, P10)

### Implementation for User Story 1

- [ ] T019 [P] [US1] Implement metadata, lifecycle-policy, service-availability, and response schemas in `contracts/schemas/meta.schema.json` (Covers: FR-009, FR-056, FR-058-FR-061; Prove: P02, P12)
- [ ] T020 [P] [US1] Implement complete immutable ruleset summaries for board labels, fleets, placement, firing, turn, disclosure, and victory mechanics in `contracts/schemas/ruleset.schema.json` (Covers: FR-010-FR-013, SC-003, CHK006; Prove: P02-P03)
- [ ] T021 [US1] After T019-T020, implement closed create/join envelopes, invitation/create/join/rotate response wrappers, and the initial caller-safe `WAITING`/`PLACEMENT` snapshot definitions required by those responses in `contracts/schemas/invitation.schema.json` and `contracts/schemas/snapshot.schema.json` (Covers: FR-019, FR-021, FR-023, FR-026, FR-028-FR-032; Prove: P02, P04-P05)
- [ ] T022 [US1] Define `getMetadata` and `listRulesets` with exact cache, CSRF bootstrap, session-insensitive status/body-schema/cache/CORS classification, no `invalid-session`, no session issue/refresh/clear, metadata-only CSRF issue/refresh, Origin, response, and failure semantics in `contracts/openapi/paths/meta.yaml` and `contracts/openapi/paths/rulesets.yaml` (Covers: FR-003-FR-004, FR-009-FR-014, FR-017; Prove: P01-P02, P10)
- [ ] T023 [US1] Add exact metadata, Sea Battle, Classic 2002, and invalid-name examples in `contracts/examples/success/meta.response.json`, `contracts/examples/success/rulesets.response.json`, and `contracts/examples/problems/display-name-invalid.json` (Covers: FR-009-FR-014, SC-002-SC-003; Prove: P02-P03)
- [ ] T024 [US1] Complete session issue/reuse/clear, CSRF cookie/header, allowed/disallowed Origin, credentials, exposed ETag, preflight, and Fetch Metadata components in `contracts/openapi/components/headers.yaml` and `contracts/openapi/components/security-schemes.yaml` (Covers: FR-015-FR-018, FR-024, FR-027, CHK008, CHK011; Prove: P08, P10)
- [ ] T025 [US1] After T021 creates the relevant schemas, create and own the `createGame` and `joinGame` operation request-body components in `contracts/openapi/components/request-bodies.yaml`, and define `createGame`, `joinGame`, and `rotateGameInvitation` with exact request/response/status, gate-order, invitation-unavailable, revision, recovery, and non-blind-replay semantics in `contracts/openapi/paths/games.yaml`, `contracts/openapi/paths/game-join.yaml`, and `contracts/openapi/paths/game-invite-rotate.yaml` (Covers: FR-019-FR-027, CHK009-CHK012; Prove: P03, P05, P10)
- [ ] T026 [US1] Add owner-create, guest-join, rotation, uniform-invitation, invalid-session, security-rejection, and secret-redaction fixtures in `contracts/examples/success/create-owner.response.json`, `contracts/examples/success/join-guest.response.json`, `contracts/examples/success/rotate-owner.response.json`, `contracts/examples/problems/invitation-unavailable.json`, `contracts/examples/problems/invalid-session.json`, `contracts/examples/problems/request-security-rejected.json`, and `contracts/examples/privacy/invitation-redaction.json` (Covers: FR-016-FR-027, SC-003-SC-005, SC-008, CHK009-CHK012, CHK028, CHK033; Prove: P03-P05, P08, P10)
- [ ] T027 [US1] Document fragment removal before render, explicit-consent CSRF bootstrap, same-host cookie assumptions, storage prohibition, share-exactly-returned URL, and unknown create/join/rotation recovery in `contracts/guides/api-guide.md` (Covers: FR-020-FR-022, FR-025-FR-027, FR-065, CHK009-CHK012, CHK036; Prove: P05, P08, P13)
- [ ] T028 [US1] Integrate the five US1 operations and examples through the sole root/oracle/manifest owners, then make the US1 tests pass in `contracts/openapi/openapi.yaml`, `contracts/validation/operation-expectations.json`, and `contracts/examples/manifest.json` (Covers: FR-003-FR-027, SC-001-SC-005, US1, CHK005-CHK012, CHK025, CHK028; Prove: `cd contracts && npm run lint && npm run build && node --test validation/tests/discovery-and-names.test.mjs validation/tests/identity-and-request-security.test.mjs validation/tests/create-join-invitation.test.mjs`)

**Checkpoint**: User Story 1 is independently contract-testable as the MVP identity and invitation slice.

---

## Phase 4: User Story 2 - Place Fleets and Play Authoritatively (Priority: P1)

**Goal**: Publish caller-safe snapshots and the closed seven-command union so both rulesets can progress from waiting through authoritative play without client rule inference.

**Independent Test**: For each ruleset, validate waiting rejection, private fleet edits, readiness, concealed/revealed starter, fire outcomes, retained/transferred turns, repeated target, and concurrent same-version commands using only snapshots and commands.

### Tests for User Story 2

- [ ] T029 [P] [US2] Add failing two-ruleset waiting/placement/ready/fire/resign journey checks, including the structurally complete terminal outcome/statistics attachment produced by finishing `FIRE` or `RESIGN`, in `contracts/validation/tests/gameplay-journeys.test.mjs` (Covers: FR-031-FR-032, FR-039, FR-043-FR-048, SC-003, US2, CHK016-CHK018, CHK029; Prove: P03, P09)
- [ ] T030 [P] [US2] Add failing owner/guest, hidden-board-equivalence, abandoned-redaction, allowed-action, and phase-disclosure checks in `contracts/validation/tests/projection-privacy.test.mjs` (Covers: FR-028-FR-034, SC-004, SC-008, CHK013-CHK014, CHK019, CHK034, CHK037; Prove: P04, P08)
- [ ] T031 [P] [US2] Add failing dual-tag, conditional-read, exact revision advance, private-placement propagation, precondition, race, duplicate-before-freshness, and repeated-target checks in `contracts/validation/tests/game-concurrency.test.mjs` (Covers: FR-035-FR-043, SC-005, CHK015-CHK016, CHK019, CHK029, CHK033; Prove: P05, P10)

### Implementation for User Story 2

- [ ] T032 [P] [US2] Extend the T021 snapshot with `PLAYING`/`FINISHED`/`ABANDONED` members, deadlines, ships, cell unions, own/opponent/redacted/terminal boards, actions, tags, phase projections, and a structurally complete terminal outcome/statistics dependency in `contracts/schemas/snapshot.schema.json` and `contracts/schemas/terminal-statistics.schema.json` (Covers: FR-028-FR-038, FR-045, FR-048, CHK013-CHK015, CHK018-CHK019; Prove: P02, P04-P05, P09)
- [ ] T033 [P] [US2] Implement the closed `PLACE_SHIP`, `MOVE_SHIP`, `ROTATE_SHIP`, `REMOVE_SHIP`, `READY`, `FIRE`, and `RESIGN` union with version envelope and normalization rules in `contracts/schemas/command.schema.json` (Covers: FR-039-FR-043, CHK016-CHK017; Prove: P02-P03, P05)
- [ ] T034 [US2] After T032 establishes snapshot schemas, add the snapshot response components in `contracts/openapi/components/responses.yaml` and define `getGameSnapshot` with caller-specific ETag, `If-None-Match`, 200/304/no-store, membership, expiry, and adjustable admission outcomes in `contracts/openapi/paths/game-snapshot.yaml` (Covers: FR-028-FR-037, FR-059-FR-060; Prove: P05, P10, P12)
- [ ] T035 [US2] After T033 establishes command schemas, extend `contracts/openapi/components/request-bodies.yaml` for gameplay command bodies and define `submitGameCommand` with all seven variants, `If-Match`/`expectedVersion`, receipt-first processing, fixed rate limit, conflict, stale, missing-precondition, and safe snapshot outcomes in `contracts/openapi/paths/game-commands.yaml` (Covers: FR-037-FR-047, FR-055-FR-056, FR-060, CHK015-CHK017; Prove: P03, P05, P10)
- [ ] T036 [US2] Add waiting, placement owner/guest, playing owner/guest, place, ready, miss, hit, sink, repeat-target, and concurrent-transition examples in `contracts/examples/success/snapshot-waiting-owner.json`, `contracts/examples/success/snapshot-placement-owner.json`, `contracts/examples/success/snapshot-placement-guest.json`, `contracts/examples/success/snapshot-playing-owner.json`, `contracts/examples/success/snapshot-playing-guest.json`, `contracts/examples/success/command-place.response.json`, `contracts/examples/success/command-ready.response.json`, `contracts/examples/success/command-fire-miss.response.json`, `contracts/examples/success/command-fire-hit.response.json`, and `contracts/examples/success/command-fire-sink.response.json` (Covers: FR-028-FR-045, SC-002-SC-005, US2; Prove: P02-P05, P09)
- [ ] T037 [US2] Add paired undisclosed-board, abandoned-redaction, stale, missing-precondition, changed-command-ID, duplicate, repeated-target, and race fixtures in `contracts/examples/privacy/hidden-board-a.json`, `contracts/examples/privacy/hidden-board-b.json`, `contracts/examples/privacy/abandoned-redacted.json`, `contracts/examples/problems/stale-game-version.json`, `contracts/examples/problems/precondition-required.json`, `contracts/examples/problems/command-id-reused.json`, and `contracts/examples/problems/game-conflict.json` (Covers: FR-033-FR-043, SC-004-SC-005, SC-008, CHK014-CHK016, CHK029, CHK033-CHK034; Prove: P04-P05, P08, P10)
- [ ] T038 [US2] Integrate snapshot/command paths and examples through the sole root/oracle/manifest owners, then make the US2 tests pass in `contracts/openapi/openapi.yaml`, `contracts/validation/operation-expectations.json`, and `contracts/examples/manifest.json` (Covers: FR-028-FR-047, SC-001-SC-005, US2, CHK013-CHK019, CHK025, CHK029; Prove: `cd contracts && npm run lint && npm run build && node --test validation/tests/gameplay-journeys.test.mjs validation/tests/projection-privacy.test.mjs validation/tests/game-concurrency.test.mjs`)

**Checkpoint**: User Story 2 completes the P1 create-to-authoritative-play contract for both rulesets.

---

## Phase 5: User Story 3 - Resume and Recover Without Guessing (Priority: P2)

**Goal**: Define deterministic recovery for duplicate/stale/unknown outcomes, full-snapshot SSE, presence-only revisions, expiry, capacity, and process restart.

**Independent Test**: Lose responses, retry/reuse commands, reorder or omit events, replace a stream, conditionally read, extend presence, cross each deadline, and change epoch; every case converges or reports an honest unavailable outcome.

### Tests for User Story 3

- [ ] T039 [P] [US3] Add failing unknown-outcome, identical duplicate, changed-content reuse, stale, missing-precondition, conditional read, and epoch restart matrix checks in `contracts/validation/tests/retry-recovery.test.mjs` (Covers: FR-020, FR-025-FR-026, FR-035-FR-042, FR-055, FR-059, SC-005, US3, CHK015-CHK016, CHK030, CHK033; Prove: P05)
- [ ] T040 [P] [US3] Add failing raw SSE frame, immediate snapshot, decimal revision ID, replay-hint scope/fallback, replacement, overflow, lifetime, and post-commit close checks in `contracts/validation/tests/realtime.test.mjs` (Covers: FR-052-FR-055, SC-005, US3, CHK020, CHK030, CHK034; Prove: P11)
- [ ] T041 [P] [US3] Add failing presence-only revision, deadline linearization, fixed/adjustable rate, resource bound, 410/404, capacity/draining, and health 200/503 checks in `contracts/validation/tests/lifecycle-admission-health.test.mjs` (Covers: FR-056-FR-064, SC-005, US3, CHK021-CHK024, CHK030, CHK033-CHK034; Prove: P10, P12)

### Implementation for User Story 3

- [ ] T042 [US3] Add semantic duplicate, changed-content, stale, missing, unknown-create/join/rotation, conditional-read, and restart fixtures in `contracts/examples/compatibility/retry-recovery-matrix.json` (Covers: FR-020, FR-025-FR-026, FR-035-FR-042, FR-055, FR-059, SC-005, CHK030, CHK033; Prove: P05)
- [ ] T043 [P] [US3] Define `streamGameEvents` with membership-before-allocation, one `snapshot` event, direct JSON data, decimal `snapshotRevision` ID, replay hint, fallback, and pre/post-commit failure boundaries in `contracts/openapi/paths/game-events.yaml` (Covers: FR-027, FR-052-FR-055, CHK020; Prove: P10-P11)
- [ ] T044 [P] [US3] Document stream registration, 15-second heartbeat, 20-minute lifetime, single-stream replacement, bounded replay/output, slow-client close, client ordering, and snapshot fallback; add raw fixtures in `contracts/guides/sse-protocol.md`, `contracts/examples/sse/initial-snapshot.txt`, `contracts/examples/sse/replay-snapshot.txt`, and `contracts/examples/sse/current-fallback.txt` (Covers: FR-052-FR-055, SC-005, CHK020, CHK030, CHK034; Prove: P05, P08, P11, P13)
- [ ] T045 [US3] After T021, implement the closed `PresenceResponse` and define `sendGamePresence` with its no-body unsafe request, extension result, adjustable/abuse admission, and snapshot-only revision semantics in `contracts/schemas/invitation.schema.json` and `contracts/openapi/paths/game-presence.yaml` (Covers: FR-035, FR-038, FR-056-FR-060, CHK015, CHK021-CHK022; Prove: P05, P10-P12)
- [ ] T046 [US3] Implement the project-owned readiness/liveness body and `getHealth` 200/503 operation with presented sessions ignored and no session issue/refresh/clear, mandatory allowed-Origin CORS headers, disallowed-Origin 403, and same-origin/direct readiness behavior in `contracts/schemas/health.schema.json` and `contracts/openapi/paths/health.yaml` (Covers: FR-017, FR-027, FR-061-FR-064, CHK022-CHK024; Prove: P02, P10, P12)
- [ ] T047 [US3] Add presence, expiry, restart, rate-limit, capacity, ready, and draining examples in `contracts/examples/success/presence-extended.response.json`, `contracts/examples/success/presence-not-extended.response.json`, `contracts/examples/problems/game-expired.json`, `contracts/examples/problems/game-unavailable.json`, `contracts/examples/problems/rate-limit-exceeded.json`, `contracts/examples/problems/service-unavailable.json`, `contracts/examples/success/health-ready.response.json`, and `contracts/examples/success/health-not-ready.response.json` (Covers: FR-056-FR-064, SC-002, SC-005, SC-008, CHK021-CHK024, CHK034; Prove: P02, P05, P08, P10, P12)
- [ ] T048 [US3] Document unresolved-command pause, conditional recovery, foreground/epoch handling, lifecycle refresh/non-refresh, deadline ordering, fixed versus adjustable admission, bounded retry, restart loss, and durable-storage/offline-queue prohibitions in `contracts/guides/api-guide.md` (Covers: FR-030, FR-035-FR-042, FR-054-FR-065, CHK020-CHK024, CHK030, CHK035-CHK036; Prove: P05, P11-P13)
- [ ] T049 [US3] Integrate events/presence/health and recovery examples through the sole root/oracle/manifest owners in `contracts/openapi/openapi.yaml`, `contracts/validation/operation-expectations.json`, and `contracts/examples/manifest.json` (Covers: FR-052-FR-065, SC-001-SC-005, CHK020-CHK024, CHK030; Prove: P01-P02, P05, P10-P12)
- [ ] T050 [US3] Make the US3 recovery, realtime, lifecycle, admission, and health tests pass and record focused evidence in `specs/001-api-contract/tasks.md` (Covers: US3, SC-005, CHK020-CHK024, CHK030; Prove: `cd contracts && npm run validate && node --test validation/tests/retry-recovery.test.mjs validation/tests/realtime.test.mjs validation/tests/lifecycle-admission-health.test.mjs`)

**Checkpoint**: User Story 3 is independently testable as the full current-process recovery and continuity contract.

---

## Phase 6: User Story 4 - Finish, Leave, and View Safe Results (Priority: P2)

**Goal**: Define abandonment, resignation, normal finish, server-calculated terminal statistics, retained results, and membership-only terminal leave/replay.

**Independent Test**: Validate pre-play abandonment, active-play resign guidance, normal/resigned terminal results from both caller perspectives, zero/nonzero timing samples, and identical terminal leave replay while the other member's result and revisions remain unchanged.

### Tests for User Story 4

- [ ] T051 [P] [US4] Add failing terminal field, clock boundary, duration partition, null/zero aggregate, half-up accuracy, shot/hit, fleet-count, logical-turn, decision-timing, and caller-equality checks in `contracts/validation/tests/terminal-statistics.test.mjs` (Covers: FR-045, FR-047-FR-051, SC-009, US4, CHK017-CHK019, CHK027, CHK031, CHK034; Prove: P09)
- [ ] T052 [P] [US4] Add failing abandonment redaction, active resign guidance, terminal disclosure, terminal leave, post-revocation identical replay, and other-member byte invariance checks in `contracts/validation/tests/terminal-leave-privacy.test.mjs` (Covers: FR-034, FR-041, FR-046-FR-047, SC-004-SC-005, US4, CHK014, CHK016-CHK019, CHK031, CHK033; Prove: P04-P05)

### Implementation for User Story 4

- [ ] T053 [US4] Complete the T032 terminal outcomes, match/seat duration aggregates, accuracy, shot decisions, mutually exclusive fleet-condition counts, snapshot conditional links, and cross-field invariant validator in `contracts/schemas/terminal-statistics.schema.json`, `contracts/schemas/snapshot.schema.json`, and `contracts/validation/lib/terminal-statistics-validation.mjs` (Covers: FR-045, FR-047-FR-051, CHK018-CHK019; Prove: P02, P09)
- [ ] T054 [US4] After T045, first implement the closed `LeaveEnvelope` in `contracts/schemas/invitation.schema.json`; only after that schema exists, extend `contracts/openapi/components/request-bodies.yaml` for leave request bodies and define `leaveGame` across pre-play abandonment, active-play `resign-required`, terminal 204, retained receipt replay, and other-membership invariance in `contracts/openapi/paths/game-leave.yaml` (Covers: FR-040-FR-041, FR-046-FR-047, CHK016-CHK017, CHK031; Prove: P04-P05, P10)
- [ ] T055 [US4] Add normal owner/guest, resigned owner/guest, zero-sample, retained-turn, resign-before-shot, and abandoned result fixtures in `contracts/examples/success/terminal-normal-owner.json`, `contracts/examples/success/terminal-normal-guest.json`, `contracts/examples/success/terminal-resigned-owner.json`, `contracts/examples/success/terminal-resigned-guest.json`, `contracts/examples/success/terminal-zero-sample.json`, `contracts/examples/success/terminal-retained-turn.json`, `contracts/examples/success/terminal-resign-before-shot.json`, and `contracts/examples/privacy/terminal-abandoned-redacted.json` (Covers: FR-034, FR-045-FR-051, SC-002-SC-004, SC-009, CHK017-CHK019, CHK027, CHK031, CHK034; Prove: P02-P04, P09)
- [ ] T056 [US4] Add active-leave conflict, terminal-leave replay, and unchanged-other-member fixtures in `contracts/examples/problems/resign-required.json`, `contracts/examples/compatibility/terminal-leave-replay.json`, and `contracts/examples/privacy/terminal-leave-other-member.json` (Covers: FR-041, FR-046-FR-047, SC-004-SC-005, CHK016-CHK017, CHK031; Prove: P04-P05, P10)
- [ ] T057 [US4] Document server-owned terminal outcome/statistics, sample boundaries, client relabeling/formatting only, abandonment privacy, and leave/replay behavior in `contracts/guides/api-guide.md` (Covers: FR-045-FR-051, SC-009, CHK017-CHK019, CHK031, CHK036-CHK038; Prove: P04, P09, P13)
- [ ] T058 [US4] Integrate leave and terminal artifacts through the sole root/oracle/manifest owners in `contracts/openapi/openapi.yaml`, `contracts/validation/operation-expectations.json`, and `contracts/examples/manifest.json` (Covers: FR-034, FR-040-FR-051, SC-001-SC-005, SC-009, CHK014-CHK019, CHK031; Prove: P01-P05, P09-P10)
- [ ] T059 [US4] Make the US4 terminal and leave tests pass and record focused evidence in `specs/001-api-contract/tasks.md` (Covers: US4, SC-004-SC-005, SC-009, CHK017-CHK019, CHK027, CHK031; Prove: `cd contracts && npm run validate && node --test validation/tests/terminal-statistics.test.mjs validation/tests/terminal-leave-privacy.test.mjs`)

**Checkpoint**: User Story 4 is independently testable for every terminal and retained-result outcome.

---

## Phase 7: User Story 5 - Implement and Evolve One Contract (Priority: P3)

**Goal**: Complete one distributable contract, deterministic validation, independent consumer smoke checks, compatibility classification, redaction, release records, and documentation.

**Independent Test**: Run the isolated product gate, compile backend-neutral and web-consumer fixtures from temporary generated types, validate every example, and classify additive and breaking synthetic changes without importing application source or self-comparing release 1.0.0.

### Tests for User Story 5

- [ ] T060 [P] [US5] Add failing full graph, exact 11-operation, unique-definition, zero unresolved `$ref`s or remote `$ref` targets, local-tool-control, public-safe session-insensitive status/body-schema/cache/CORS classification with no `invalid-session` or session side effect, status/header/cookie/security-oracle, and complete example-manifest checks in `contracts/validation/tests/contract-bundle.test.mjs` (Covers: FR-001-FR-008, FR-017, SC-001-SC-002, US5, CHK001-CHK005, CHK025, CHK032; Prove: P01-P02, P10, P13)
- [ ] T061 [P] [US5] Add failing additive/breaking, unknown-response-field, closed-request/union, migration-window, candidate-mode initial-baseline absence, final-mode required-baseline, immutable-baseline, no-self-comparison, and isolated temporary-fixture tests of atomic creation/overwrite refusal that never invoke the top-level `baseline:create` command recursively in `contracts/validation/tests/compatibility.test.mjs` (Covers: FR-006-FR-008, SC-006-SC-007, US5, CHK003-CHK005, CHK027, CHK032; Prove: P06-P07)
- [ ] T062 [P] [US5] Add failing backend-neutral and web-consumer discriminator agreement and cleanup checks in `contracts/validation/tests/consumer-smoke.test.mjs` (Covers: FR-001-FR-002, FR-008, SC-006, US5, CHK001, CHK004-CHK005, CHK026, CHK032; Prove: P06)
- [ ] T063 [P] [US5] Add failing source/bundle/any-present-baseline secret scans; repository path, ignore, local-only-tool/no-`npx`/no-remote-executable-validation-input, root/contract documentation, guide link/fence/placeholder/newline, and no-duplicate-normative-source checks while allowing required dialect/schema identifier URIs and documentation links; and an explicit check that rendered accessibility is deferred because this product exposes no UI while semantic language-neutral data remains mandatory in `contracts/validation/tests/redaction-and-documentation.test.mjs` (Covers: FR-001-FR-002, FR-004, FR-017, FR-021-FR-022, FR-033-FR-034, FR-062, FR-065, SC-004, SC-008, CHK001-CHK002, CHK035-CHK039; Prove: P08, P13)

### Implementation for User Story 5

- [ ] T064 [US5] Complete manifest-bound schema/operation/status validation and focused example/scenario/privacy/SSE/lifecycle/document helpers, composing the terminal validator from T053, in `contracts/examples/manifest.json`, `contracts/validation/lib/example-validation.mjs`, `contracts/validation/lib/scenario-validation.mjs`, `contracts/validation/lib/privacy-validation.mjs`, `contracts/validation/lib/sse-validation.mjs`, and `contracts/validation/lib/lifecycle-validation.mjs` (Covers: FR-004, FR-008, FR-028-FR-065, SC-002-SC-005, SC-008-SC-009, CHK019, CHK024-CHK027, CHK033-CHK034, CHK038; Prove: P02-P05, P08-P12)
- [ ] T065 [P] [US5] Publish additive/breaking rules, migration obligations, release classification, candidate/final gate and atomic overwrite-refusing baseline instructions, changelog entry, and synthetic fixtures in `contracts/compatibility-policy.md`, `contracts/changelog.md`, `contracts/baselines/README.md`, `contracts/examples/compatibility/additive-optional-response.json`, `contracts/examples/compatibility/breaking-required-field.json`, `contracts/examples/compatibility/breaking-closed-union.json`, `contracts/examples/compatibility/breaking-security.json`, `contracts/examples/compatibility/breaking-status.json`, and `contracts/examples/compatibility/breaking-semantics.json` (Covers: FR-006-FR-008, SC-007, CHK003-CHK005, CHK027, CHK032; Prove: P07, P13)
- [ ] T066 [US5] After T065, implement bounded compatibility classification against its synthetic/current-prior inputs without treating 1.0.0 as its own evidence in `contracts/validation/lib/compatibility-validation.mjs` (Covers: FR-007-FR-008, SC-007, CHK004-CHK005, CHK027; Prove: P07)
- [ ] T067 [P] [US5] Implement local-bundle-only `openapi-typescript` generation with pinned local binaries, backend-neutral/web fixture compilation, forward-compatible response use, closed request/union failure checks, and cleanup on success/failure in `contracts/validation/smoke-types.mjs`, `contracts/validation/fixtures/backend-consumer.ts`, and `contracts/validation/fixtures/web-consumer.ts` (Covers: FR-001-FR-002, FR-008, SC-006, CHK001, CHK004-CHK005, CHK026, CHK032; Prove: P06, P13)
- [ ] T068 [US5] Complete the bounded orchestration that cross-checks OpenAPI, schemas, examples, operation oracle, problem catalog, redaction, scenarios, compatibility, documentation, and generated consumers, with candidate mode allowing only the not-yet-created initial baseline to be absent and `--require-release-baseline` making absence or drift fail, in `contracts/validation/validate.mjs` (Covers: FR-001-FR-008, FR-062-FR-065, SC-001-SC-009, CHK005, CHK012, CHK019, CHK024-CHK027, CHK040; Prove: P01-P13)
- [ ] T069 [US5] Finalize API/SSE consumption instructions, exact candidate/baseline/final commands, local-only tool controls, public-safe invalid-cookie/no-session-side-effect behavior, recovery/security/lifecycle boundaries, source links, validation interpretation, no-runtime limitation, and the contract-specific boundary that requires stable semantic language-neutral data here while deferring rendered keyboard/touch/assistive-technology and English/Ukrainian UI verification to the frontend feature in `contracts/README.md`, `contracts/guides/api-guide.md`, and `contracts/guides/sse-protocol.md`; update `README.md` with the current rewrite topology/status, independent `contracts/` product, canonical/derived ownership, exact feature gate, no-HTTP-runtime and no-root-gate-until-integration boundaries, and links to the constitution, feature docs, and contract README without duplicating wire definitions (Covers: FR-001-FR-008, FR-017, FR-020-FR-027, FR-052-FR-065, SC-006, US5, Constitution VI-VII, CHK001-CHK005, CHK020-CHK024, CHK032, CHK035-CHK039; Prove: P06, P13)
- [ ] T070 [US5] Perform the single-owner final merge and audit of all 11 operations, shared components, examples, statuses, and manifest entries, including `contracts/openapi/components/responses.yaml` and `contracts/openapi/components/request-bodies.yaml`, in `contracts/openapi/openapi.yaml`, `contracts/validation/operation-expectations.json`, and `contracts/examples/manifest.json`; make the merged bundle proof pass while leaving the deliberately failing baseline-creation proof for T071 (Covers: FR-003-FR-004, SC-001-SC-002, CHK002, CHK005, CHK012, CHK019, CHK024-CHK025, CHK040; Prove: `cd contracts && npm run lint && npm run build && npm run validate && node --test validation/tests/contract-bundle.test.mjs`)
- [ ] T071 [US5] Implement the atomic overwrite-refusing `baseline:create` generator that internally runs `check:candidate`, archives the passing bundle, and proves the immutable release artifact is required by the final gate but is not self-comparison evidence in `contracts/package.json`, `contracts/validation/create-baseline.mjs`, and `contracts/baselines/1.0.0/openapi.json` (Covers: FR-006-FR-008, SC-007-SC-008, CHK003-CHK005, CHK027, CHK032, CHK039; Prove: `cd contracts && npm ci && npm run baseline:create && npm run check`)

**Checkpoint**: All five stories are represented in one independently consumable, validated contract product.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Reconcile evidence, durable repository guidance, scope, and the final feature-local gate without broadening the feature.

- [ ] T072 Run every quickstart scenario, replace the planning-only status with fresh passed/failed/blocked evidence, and preserve the no-HTTP-runtime limitation in `specs/001-api-contract/quickstart.md` (Covers: SC-001-SC-009, CHK025-CHK027, CHK039; Prove: P01-P13)
- [ ] T073 Audit tracked `contracts/` dependencies and paths for forbidden backend/frontend/runtime/rules-engine/generated-client/CI/deployment/persistence artifacts and record the result in `contracts/README.md` (Covers: FR-002, CHK001, CHK035, CHK037-CHK039; Prove: P08, P13)
- [ ] T074 Update the durable repository-wide contract product boundaries, exact commands, generated-artifact ownership, local-only tool controls, and no-runtime verification guidance in `AGENTS.md`; then run `python3 scripts/sync-agent-files.py --apply`, `python3 scripts/sync-agent-files.py --check`, and `git diff --check` (Covers: Constitution VI-VIII, CHK035, CHK037-CHK039; Prove: `python3 scripts/sync-agent-files.py --check`)
- [ ] T075 Run the fresh completion gate and record exact results, task status, limitations, and handoff evidence in `specs/001-api-contract/tasks.md` (Covers: SC-001-SC-009, CHK025-CHK027, CHK039; Prove: `cd contracts && npm ci && npm run check`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: Starts immediately.
- **Phase 2 Foundational**: Depends on T001-T006 and blocks every story.
- **User Story 1**: Depends on Phase 2 and establishes metadata, rulesets, identity, and invitation primitives used downstream.
- **User Story 2**: Depends on User Story 1 because the gameplay contract references the selected immutable ruleset, memberships, and join-created snapshot.
- **User Story 3**: Depends on User Story 2 because recovery and SSE carry complete snapshots and command results.
- **User Story 4**: Depends on User Story 2 for its failing proofs; only T051-T052 may proceed beside late User Story 3 work. T053-T059 start after the User Story 3 checkpoint, and T054 additionally waits for T045 because both serially extend `invitation.schema.json`.
- **User Story 5**: Depends on User Stories 1-4 because it closes the complete graph, examples, compatibility, and consumer validation.
- **Polish**: Depends on User Story 5 and owns final evidence only.

### Canonical Merge Owners

The following shared files are serial integration points. Only their named merge tasks edit them after initial creation:

- `contracts/openapi/openapi.yaml`: T006, then T028, T038, T049, T058, and final owner T070.
- `contracts/examples/manifest.json`: T028, T038, T049, T058, T064, and final owner T070.
- `contracts/validation/operation-expectations.json`: T014, then T028, T038, T049, T058, and final owner T070.
- `contracts/openapi/components/responses.yaml`: T013 creates foundational named problem responses, T034 adds snapshot response components after T032 establishes snapshot schemas, and final owner T070 merges and audits the completed component file.
- `contracts/openapi/components/request-bodies.yaml`: T025 creates create/join bodies after T021 establishes their schemas, T035 extends gameplay command bodies after T033 establishes command schemas, T054 first establishes `LeaveEnvelope` and then extends leave bodies, and final owner T070 merges and audits the completed component file.
- `contracts/validation/validate.mjs`: T015, then final owner T068.
- `contracts/guides/api-guide.md`: T027, T048, T057, then final owner T069.
- `contracts/compatibility-policy.md`: sole implementation owner T065.
- `README.md`: sole implementation owner T069 so repository documentation passes before the candidate/baseline/final gates.
- `AGENTS.md` and generated agent mirrors: sole implementation owner T074 after the contract gate; its independent mirror proof is not part of the npm product gate.
- `contracts/schemas/invitation.schema.json`: T021 creates create/join/invitation wrappers, T045 adds presence after its failing proof, and T054 adds leave after its failing proof and T045.
- `contracts/schemas/snapshot.schema.json`: T021 creates the US1 base, T032 extends all phases, and T053 completes terminal conditions.
- `contracts/schemas/terminal-statistics.schema.json`: after T029-T030 fail on terminal structure/disclosure, T032 creates the structural dependency; after T051 fails on arithmetic and boundary behavior, T053 completes its invariants.

### Within Each User Story

1. Write the listed story tests and confirm they fail for the missing behavior.
2. Implement schemas and disjoint artifacts.
3. Implement operations and examples.
4. Use the phase's single merge task for the canonical root, manifest, and oracle.
5. Run the independent story check before advancing.

---

## Parallel Opportunities

- T002's dependency-free static proof must fail on the missing local tool controls before T003 implements them and makes that static proof pass without an install; T004 then creates the lock, runs the first controlled install, and performs the first installed-tool wrapper probe. T005 can run beside that sequence after T001 because it owns disjoint documentation.
- T008-T009 can run beside T007; T012-T013 can run together after T010-T011.
- T016-T018 can run together; after they fail, T019-T020 can run together and T021 follows both.
- T029-T031 can run together; after they fail, T032-T033 can run together.
- T039-T041 can run together. After they fail and T042 completes, T043-T044 can run together; T045-T050 remain serial.
- T051-T052 can run together.
- T060-T063 can run together. After their expected failures, complete T064; then T065 and T067 can run together. T066 starts only after T065, and T068 waits for T064-T067.
- Only T051-T052 may overlap late User Story 3; all User Story 4 implementation and integration tasks wait for the User Story 3 checkpoint.

## Parallel Examples by Story

### User Story 1

```text
T016 discovery/name tests | T017 identity/security tests | T018 create/join/invitation tests
then
T019 meta schema | T020 ruleset schema
then
T021 invitation wrappers + initial waiting/placement snapshot
```

### User Story 2

```text
T029 gameplay journeys | T030 projection privacy | T031 concurrency/revisions
then
T032 snapshot schema | T033 command schema
```

### User Story 3

```text
T039 retry/recovery | T040 realtime | T041 lifecycle/admission/health
then
T042 recovery fixtures
then
T043 event path | T044 SSE guide/raw fixtures
then
T045-T050 serial implementation and integration
```

### User Story 4

```text
T051 terminal arithmetic | T052 leave/replay/privacy
```

### User Story 5

```text
T060 full bundle | T061 compatibility | T062 consumer smoke | T063 redaction/documentation
then
T064 validation helpers
then
T065 compatibility policy/fixtures | T067 consumer smoke implementation
then
T066 compatibility classifier (after T065)
then
T068-T071 serial orchestration, merge, and baseline
```

---

## Requirements & Proof Traceability

| Governing requirements | Real tasks and named artifacts | Required proof |
|---|---|---|
| FR-001-FR-008 | T001-T015, T060-T071, T073 in `contracts/package.json`, `contracts/openapi/openapi.yaml`, `contracts/schemas/`, `contracts/compatibility-policy.md`, `contracts/validation/`, and `contracts/README.md` | P01-P02, P06-P08, P10, P13 |
| FR-009-FR-014 | T016, T019-T023, T028 in `contracts/schemas/meta.schema.json`, `contracts/schemas/ruleset.schema.json`, discovery paths, and exact fixtures | P01-P03, P10, P13 |
| FR-015-FR-018 | T008, T012-T014, T017, T022, T024, T026-T028, T046, T060, T069 in common schemas, HTTP components, public-safe/session-required security fixtures, operations, and API guide | P04-P05, P08, P10, P13 |
| FR-019-FR-027 | T018, T021, T024-T028 in create/join/rotate paths, invitation fixtures, guide, oracle, and manifest | P02-P05, P08, P10, P13 |
| FR-028-FR-034 | T030, T032, T034, T036-T038, T052, T055 in snapshot schema/path and projection/abandonment fixtures | P02-P04, P08, P10 |
| FR-035-FR-038 | T008, T012, T031-T038, T039, T045 in common/snapshot schemas, conditional headers, transition tests, and presence | P04-P05, P10-P12 |
| FR-039-FR-043 | T029, T031, T033, T035-T039 in command schema/path, gameplay/concurrency tests, and retry fixtures | P02-P05, P10 |
| FR-044-FR-047 | T029, T035-T038, T051-T059 in gameplay, terminal, leave, and privacy artifacts | P03-P05, P09-P10 |
| FR-048-FR-051 | T029, T051, T053, T055, T057-T059, T064 in terminal structural journey proof, schema, fixtures, guide, and invariant validator | P02-P04, P09, P13 |
| FR-052-FR-055 | T039-T044, T048-T050 in events path, raw frames, SSE guide, recovery tests, and API guide | P05, P08, P11, P13 |
| FR-056-FR-059 | T016, T019, T041-T042, T045, T047-T050 in failing metadata lifecycle proof, lifecycle policy, presence, boundary fixtures, tests, and guide | P02, P05, P11-P12, P13 |
| FR-060-FR-061 | T009, T011, T014, T016, T019, T041, T046-T050 in problem schema, metadata availability proof/schema, oracle, health/admission tests, fixtures, and guide | P02, P08, P10, P12 |
| FR-062-FR-064 | T009, T011, T013-T015, T041, T046-T050, T063-T064 in problem/health schemas, responses, oracle, redaction, and status tests | P02, P08, P10, P12 |
| FR-065 | T027, T048, T063, T069 in API guide, storage/redaction checks, and consumer documentation | P05, P08, P13 |

## Success Criteria Traceability

| Criterion | Tasks | Observable completion evidence |
|---|---|---|
| SC-001 | T006-T007, T014-T015, T022, T025, T028, T034-T035, T038, T043, T045-T046, T049, T054, T058, T060, T068, T070 | P01/P10 prove exactly 11 operations, one definition per wire element, and zero unresolved references or aliases. |
| SC-002 | T015-T016, T019-T023, T026, T028-T030, T032-T038, T041, T044-T049, T051-T058, T060, T064, T068, T070 | P02 proves every manifest-bound published example validates. |
| SC-003 | T016, T018, T020, T023, T025-T029, T033-T038 | P03 proves both rulesets and every P1 scenario without client policy invention. |
| SC-004 | T017, T026, T030, T032, T037-T038, T052, T055-T059, T063-T064 | P04/P08 prove projection equivalence and zero protected-data leaks. |
| SC-005 | T018, T025-T028, T031, T034-T050, T052, T054, T056, T058-T059, T064 | P05/P11/P12 prove every named safe recovery and continuity outcome. |
| SC-006 | T002, T060-T062, T067-T069 | P06 proves independent backend-neutral/web consumption and cleanup. |
| SC-007 | T061, T065-T066, T068, T071 | P07 proves additive/breaking classification, candidate-before-baseline validation, overwrite refusal, final required-baseline validation, and no 1.0.0 self-comparison. |
| SC-008 | T009, T017, T024, T026-T027, T030, T037, T044, T047-T048, T055-T057, T063-T064, T071, T073 | P08 proves zero reusable secrets, full fragment URLs, hidden boards, stacks, or raw errors. |
| SC-009 | T051, T053, T055, T057-T059, T064 | P09 proves authoritative, caller-identical terminal facts and zero client calculation. |

## Story and Edge-Case Traceability

| Story or edge class | Tasks and proving artifacts |
|---|---|
| US1 create/join/rotate/concurrent redemption | T016-T028; discovery, security, invitation, and journey tests/fixtures |
| US2 waiting/placement/ready/fire/repeat/concurrency | T029-T038; gameplay, privacy, and concurrency tests/fixtures |
| US3 unknown/stale/SSE/presence/restart | T039-T050; recovery, realtime, lifecycle, admission, and health tests/fixtures |
| US4 abandon/resign/disclose/leave/replay | T051-T059; terminal arithmetic and leave/privacy tests/fixtures |
| US5 independent consumption/evolution | T060-T071; bundle, compatibility, consumer smoke, docs, and baseline checks |
| Lost create before `gameId` and lost join after consumption | T018, T025-T028, T039, T042, T048 |
| Rotation/redemption race | T018, T025-T026, T039, T042 |
| Same identity claims both seats and one identity joins several games | T017-T018, T026 |
| Invalid/untrusted display names | T008, T016, T020, T023 |
| Concurrent READY and terminal FIRE/RESIGN | T029, T031, T036-T038, T051 |
| Retry after receipt guarantee ends | T031, T035, T039, T042, T048 |
| Presence-only revision | T031, T039, T041-T042, T045, T047-T050 |
| Exact deadline boundary and cleanup non-resurrection | T041-T042, T047-T050 |
| Zero shots/no timing samples | T051, T053, T055, T064 |
| Retained firing authority after hit | T029, T051, T055, T064 |
| Resignation before current shot | T051, T055, T064 |
| Terminal statistics from both perspectives | T030, T051-T055, T057-T059, T064 |
| Stale/wrong SSE cursor | T039-T044, T048-T050 |
| New stream replaces old and slow/overflow stream closes | T040, T043-T044, T049-T050 |
| Hidden internal board differs | T030, T032, T037-T038, T052, T063-T064 |
| Rate, capacity, and draining | T009, T011, T014, T041, T046-T050, T063-T064 |

## Scope, Assumptions, and Constitution Traceability

| Concern | Tasks and review criterion |
|---|---|
| Contract-only product scope; repository integration limited to current README/AGENTS/ignore/evidence files; no application/runtime/CI/deployment/persistence expansion | T002, T005, T060, T063, T069, T073-T074; P13 path/dependency audit |
| No `/api/v2`, alternate routes, or application-level OPTIONS | T006-T007, T014, T060, T070; P01/P10 exact surface |
| Same-host identity, exact-Origin local ports, and host-only cookies | T017, T024, T027, T046, T063; P08/P10/P13 |
| One browser profile may own memberships in several games but never both seats in one game | T017-T018, T026; P03/P04/P10 |
| Durable storage contains public hints/drafts/preferences only | T027, T048, T063, T069; P05/P08/P13 |
| Single bounded process, restart loss, no durable replay/idempotency | T039-T050, T069; P05/P11/P12/P13 |
| Full snapshots are recovery truth; dual revision is deliberate | T030-T050; P04/P05/P11/P12 |
| Mockup is UX direction, not fixture arithmetic or token authority | T051, T055, T057, T063-T064; P08/P09/P13 |
| Rate/heartbeat/lifetime values are initial documented defaults | T041, T044-T050; P10-P13 |
| Research-fixed time, ratio, coordinate, tag, ID, and problem encodings | T008-T015, T051-T053, T061, T065-T066; P01/P02/P07/P09/P10 |
| Constitution I-II: contract authority, server authority, caller privacy | T005-T015, T017-T038, T045-T064, T073-T074; P01-P05/P08/P10 |
| Constitution III, VI-VIII: simple ownership, independent artifacts, current evidence, local gate, proportionate process | T001-T015, T060-T075; P01/P06-P08/P13 and exact completion gate |
| Constitution IV-V: evidence-first tool/behavior/race/security/privacy/localization/diagnostic proof and explicit non-UI accessibility deferral | T002-T004, T007-T018, T029-T031, T039-T041, T051-T052, T060-T064, T069; P01-P13 behavior evidence, not coverage quotas |

## Checklist Closure Map

The requirements-quality checklist was already complete before task generation. The reviewer closes the task-traceability checklist only after confirming the following real-task mappings; checked markers prove ledger quality, not implementation completion.

| Checklist concerns | Concrete task-ledger evidence |
|---|---|
| CHK001-CHK005 | T001-T015 and T060-T075; contract product, exact surface, versions, compatibility, paths, and proofs are explicit. |
| CHK006-CHK012 | T016-T028; metadata/rulesets/names/identity/cookies/create/join/rotate/CORS each own schemas, paths, fixtures, tests, and proofs. |
| CHK013-CHK019 | T029-T038 and T051-T059; snapshots/privacy/revisions/commands/phases/terminal data each map to artifacts and behavior checks. |
| CHK020-CHK024 | T039-T050; SSE/lifecycle/presence/rates/capacity/problems/health/storage each map to paths, fixtures, guides, and tests. |
| CHK025-CHK027 | The SC-001-SC-009 table above maps every measurable outcome to tasks and P01-P13 evidence. |
| CHK028-CHK032 | The user-story rows above map all five scenario classes to independently testable phases and named fixtures. |
| CHK033-CHK034 | The edge-case rows above map every listed uncertainty, race, identity, deadline, statistics, SSE, privacy, and capacity edge to tasks. |
| CHK035-CHK039 | The scope/assumption/constitution table above maps every boundary and governing principle to task constraints and review evidence. |
| CHK040 | Requirements, success criteria, stories, edges, scope, assumptions, and constitution are all mapped above to real task IDs, exact paths, and P01-P13 or exact commands. |

---

## Implementation Strategy

### Pre-Implementation Gate

1. After task generation, run `$speckit-analyze` before handoff or implementation.
2. Resolve material findings and rerun analysis until it is clean.
3. After clean analysis, refresh the handoff, run the guard, and execute one bridge alias.

### MVP First

1. Complete Setup and Foundational tasks T001-T015.
2. Complete User Story 1 tasks T016-T028.
3. Stop and run the US1 independent check; this is the smallest usable identity/invitation contract slice.
4. Do not start backend or frontend feature implementation from the partial contract.

### Incremental Delivery

1. Add User Story 2 and prove both rulesets through authoritative play.
2. Add User Story 3 and prove current-process recovery/realtime/lifecycle behavior.
3. Add User Story 4 and prove terminal privacy/statistics/leave behavior.
4. Add User Story 5, publish the immutable 1.0.0 bundle, and run the full contract gate.
5. Run Polish tasks; use the post-implementation lifecycle below before close.

### Post-Implementation Lifecycle

1. After every implementation pass, run `$speckit-converge`.
2. If converge appends tasks, rerun the selected implementation path and repeat this lifecycle.
3. Once converge appends no tasks, run optional cleanup, the plan-approved verification, and then close the feature.

### Commit Discipline

- Commit after a focused task or cohesive red-green-refactor group.
- Never hand-edit `contracts/dist/openapi.json`, temporary generated types, or `contracts/baselines/1.0.0/openapi.json`.
- Preserve unrelated staged/unstaged work and the current untracked feature artifacts.
- Do not mark a task complete from a prior report; capture the command result required by that task.
