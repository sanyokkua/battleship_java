# Planning Coverage and Validation Matrix

## How to use this matrix

This is the Phase 1 bridge from requirements to future implementation tasks. It proves that planning
has assigned every governing concern to a root artifact, dependency-ordered work package, and review
criterion. It is not `tasks.md`, and its mappings do not authorize checking the reviewer-owned
task-traceability checklist before `$speckit-tasks` creates actual tasks.

Work packages are defined in [../plan.md](../plan.md): WP01 through WP10.

## Proof identifiers

| Proof | Gate behavior |
|---|---|
| P01 Canonical graph | Local-only Redocly wrapper with telemetry/update notices disabled; lint/bundle has zero unresolved `$ref`s or remote `$ref` targets, exactly 11 operations/unique IDs, and one canonical definition per wire element. Required dialect/schema identifier URIs and documentation links are not fetchable references and remain allowed. |
| P02 Example manifest | Every committed JSON/request/response/snapshot/problem/health example maps to and validates against one schema and operation/status role. |
| P03 Journey fixtures | Contract-only create-to-terminal sequences for both rulesets plus every P1 acceptance scenario. |
| P04 Projection privacy | Owner/guest and hidden-board-equivalence fixtures; abandoned redacted-board structural checks; terminal-leave unchanged-other-member snapshot/ETag/revision fixture; forbidden-field and phase-disclosure checks. |
| P05 Retry/recovery | Duplicate/content-change/stale/missing/unknown-outcome/conditional/SSE/presence/expiry/restart matrix. |
| P06 Independent consumers | Temporary generated TypeScript compile plus backend-neutral schema/operation discriminator smoke reader. |
| P07 Compatibility | Candidate gate permits only the not-yet-created initial baseline to be absent; overwrite-refusing atomic baseline creation; final gate requires the immutable baseline; synthetic classifications and no 1.0.0 self-comparison. |
| P08 Secret/redaction | Scan canonical source, guides, examples, bundle, and baseline for capabilities, realistic invitation fragments, cookies, hidden boards, stacks, raw errors/bodies. |
| P09 Terminal invariants | Schema and arithmetic tests for phase presence, clocks, aggregates, accuracy, shots/hits, fleet counts, caller equality. |
| P10 HTTP/security | `operation-expectations.json` is cross-checked against OpenAPI and the human matrix for operation/status/media/header/cookie/CORS/cache/security/gate-order/retry behavior, including public-safe session-insensitive outcome/security classification: the same status, body schema, cache, and CORS class as without the cookie; never `invalid-session`; no session `Set-Cookie`; and metadata's independent CSRF issue/refresh still allowed. It also proves every Fetch Metadata truth-table row and exact equality with the 19-code problem catalog. |
| P11 Realtime | Raw SSE frame parser, immediate snapshot, snapshotRevision IDs, cursor scope/fallback, replacement/slow-close/post-commit behavior. |
| P12 Lifecycle/admission | Deadline transition table, presence-only revision, rate/control distinction, retry bounds, 410/404 epoch, 429/503, health 200/503. |
| P13 Documentation | Contract and repository README/current-feature link, fence, placeholder, final-newline, and source-reference checks; documented local-only tool controls; no duplicated normative wire source. AGENTS import/mirror synchronization has the independent T074 proof. |

## Functional requirement coverage

| Governing range | Root implementation artifacts | Work packages | Proof |
|---|---|---|---|
| Contract product, exact surface, one source, OpenAPI/RFC 9457, versions, compatibility, unknown fields (FR-001–008) | package/README, canonical OpenAPI graph, schemas, compatibility policy/changelog, baseline, validator | WP01, WP02, WP08–WP10 | P01, P02, P06, P07, P13 |
| Metadata, lifecycle policy, two rulesets, immutable mechanics, display names (FR-009–014) | meta/ruleset/common schemas; meta/ruleset paths; examples; API guide | WP02–WP03, WP09–WP10 | P01–P03, P10, P13 |
| Game locator, identity/session/CSRF, create and unknown create (FR-015–020) | common/invitation schemas; security/header components; create path; examples; unknown-outcome guide | WP02, WP04, WP08–WP10 | P02, P05, P08, P10 |
| Invitation URL/landing, join gate order/non-enumeration/recovery, rotation, CORS (FR-021–027) | invitation schemas; join/rotate paths; security/CORS components; API guide; uniform problem fixtures | WP04, WP08–WP10 | P03, P05, P08, P10 |
| Snapshot authority, no-store, fields/deadlines, ships/cells/actions, privacy/terminal disclosure (FR-028–034) | snapshot schemas; snapshot path/headers; phase/seat/privacy examples | WP05, WP08–WP10 | P02–P04, P08, P10 |
| Dual revisions, ETags/preconditions, exact advance rules (FR-035–038) | common tag schemas; snapshot/command OpenAPI components; transition fixtures; SSE guide | WP02, WP05, WP07, WP09 | P05, P10–P12 |
| Seven commands, version envelopes, receipt/order/race/repeated target (FR-039–043) | command schema; command/leave paths; duplicate/stale/conflict examples; API guide | WP06, WP08–WP10 | P02, P03, P05, P10 |
| Waiting/placement/ready/fire/outcome/leave/resign (FR-044–047) | command/snapshot/terminal schemas; journey and phase fixtures | WP05–WP06, WP08–WP10 | P03–P05, P09 |
| Terminal statistics, timing, arithmetic, consumer boundary (FR-048–051) | terminal-statistics schema; terminal owner/guest examples; invariant tests; API guide | WP06, WP08–WP10 | P02–P04, P09 |
| SSE frame, bounds, replay/fallback, unresolved command pause (FR-052–055) | events path; raw SSE examples; SSE guide; recovery fixtures | WP07–WP10 | P05, P08, P11, P13 |
| Activity/lifetimes/presence/deadline order/restart (FR-056–059) | presence path/schema; lifecycle guide; expiry/restart examples | WP07–WP10 | P05, P12, P13 |
| Fixed rates, adjustable admission, capacity/draining/resource bounds (FR-060–061) | API guide; problem/retry schemas; operation matrix fixtures | WP07–WP10 | P08, P10, P12 |
| Problem/status/localization/privacy and health (FR-062–064) | problem/health schemas; named responses; problem catalogue; health path/examples | WP02, WP07–WP10 | P02, P08, P10, P12 |
| Browser continuity/storage boundary (FR-065) | API guide recovery/storage section; no-secret/no-offline fixtures | WP07, WP10 | P05, P08, P13 |

## Success criteria coverage

| Criterion | Artifact outcome | Proof |
|---|---|---|
| SC-001 | Exact 11-operation graph, every wire component resolved once, no alias. | P01, P10 |
| SC-002 | 100% manifest-bound published examples validate. | P02 |
| SC-003 | Both rulesets and every P1 scenario complete contract-only journey fixtures. | P03 |
| SC-004 | Owner/guest leak scan and paired hidden-board equivalence. | P04, P08 |
| SC-005 | Every named retry/recovery/deadline/restart case has one safe outcome. | P05, P11, P12 |
| SC-006 | Two source-independent consumer smoke paths agree on operations/discriminators. | P06 |
| SC-007 | Representative additive/breaking pairs classify correctly; initial baseline is not self-evidence. | P07 |
| SC-008 | No reusable credential/secret/full realistic fragment/hidden board/stack/raw error in artifacts. | P08 |
| SC-009 | Both terminal caller fixtures map every result value to one authoritative field and pass invariants. | P09 |

## User-story coverage

| Story | Named fixture groups | Work packages/proofs |
|---|---|---|
| Create and join private game | meta CSRF, both rulesets, create owner, safe fragment, explicit join, two-redemption race, rotation | WP03–WP04, WP08–WP10; P03, P05, P08, P10 |
| Place fleets and play authoritatively | waiting rejection, legal/illegal edits, readiness, concealed starter, both ruleset fire/turn behavior, repeated target, concurrent commands, terminal fire | WP03, WP05–WP06, WP08–WP10; P03–P05, P09 |
| Resume and recover | lost response duplicate, changed-content reuse, stale 412, 304, SSE duplicate/gap, presence revision, epoch restart | WP05–WP07, WP08–WP10; P05, P11–P12 |
| Finish, leave, and view safe results | pre-play abandonment, active resign guidance, normal/resigned result, owner/guest stats, terminal leave/replay | WP05–WP06, WP08–WP10; P03–P05, P09 |
| Implement/evolve one contract | canonical bundle, two consumer smoke tests, additive/breaking fixtures, release baseline/changelog | WP01, WP08–WP10; P01–P02, P06–P07, P13 |

## Edge-case coverage

| Edge case from spec | Planned fixture/assertion | Proof |
|---|---|---|
| Lost create before `gameId` | No automatic retry/recovery route; explicit new-create confirmation guidance. | P05, P13 |
| Lost join after consume | Known-game authenticated snapshot recovery or honest unrecoverable seat. | P05 |
| Rotation vs redemption race | Exactly one serialized outcome; no second membership; old/new secret rules. | P03, P05 |
| Same browser attempts both seats | Uniform admitted `invitation-unavailable`; existing membership preserved. | P04, P10 |
| One identity in several games | Leaving/expiry of one game does not alter other memberships. | P03, P04 |
| Terminal member leaves | Caller receives/replays 204; the other member's serialized result, ETag, members projection, `gameVersion`, and `snapshotRevision` remain byte-for-byte unchanged. | P04, P05 |
| Pre-play abandonment | Both `ownBoard` and `opponentBoard` validate only as redacted forms; no placement/fleet/cell/statistics field can be represented. | P04, P08 |
| Invalid/untrusted display name | NFC/trim/code-point/control/markup-as-plain-text fixtures; no log/metric use. | P02, P08, P10 |
| Concurrent READY or terminal FIRE/RESIGN | At most one version advance and exactly one terminal result. | P03, P05, P09 |
| Retry after receipt guarantee ends | Stale precondition fails safely; no reapplication promise. | P05 |
| Presence moves deadline only | `snapshotRevision` and snapshot ETag/SSE advance; `gameVersion`/game tag do not. | P05, P11–P12 |
| Exact deadline boundary | Before-deadline winner may proceed; at/after observes expiry; cleanup cannot race it away/resurrect. | P12 |
| Zero shots/no timing samples | Counts/totals zero; aggregates/ratio null exactly as specified. | P09 |
| Retained firing authority after hit | One logical turn continues; new shot-decision sample starts immediately. | P09 |
| Resign before current shot | Logical turn closes; unfinished shot-decision sample discarded. | P09 |
| Terminal statistics from both perspectives | Stable owner/guest keyed facts byte-equivalent; labels remain presentation-only. | P04, P09 |
| Stale/wrong SSE cursor | Authorized current-game/current-epoch hint only; current snapshot fallback; no provenance leak. | P05, P11 |
| New stream replaces old/slow stream | Older/overflow stream closes; commands are not blocked; output remains bounded. | P11–P12 |
| Hidden internal board differs | Caller fixtures equal except allowed opaque correlation outside snapshot. | P04, P08 |
| Rate/capacity/draining | 429/503 bounded guidance; no live-game eviction and no resource/secret oracle. | P10, P12 |

## Scope and assumption coverage

| Boundary/assumption | Plan constraint/artifact | Proof/review |
|---|---|---|
| Contract feature only | Product writes restricted to `contracts/`; repository integration writes are limited to `README.md`, `AGENTS.md`, `.specify/.gitignore`, generated agent mirrors, and current feature evidence under `specs/001-api-contract/`. | Worktree path audit; P13 |
| No `/api/v2`, app code, database, broker, replicas, CI/deployment | Exact route/source tree and package dependency scan. | P01, P13 |
| No durable SSE/idempotency/restart recovery | Guides and problem/recovery catalogue state honest loss. | P05, P11–P13 |
| Same hostname; local ports may differ | Cookie/CORS guide and operation matrix; no parent-domain cookie. | P10, P13 |
| One browser profile is one identity | Invitation/membership fixtures; same-identity second-seat failure. | P03–P04 |
| Durable browser storage is public hints/drafts/preferences only | API guide and forbidden-secret/storage scan. | P08, P13 |
| Single bounded in-memory process and restart loss | Meta epoch, 404/410 and lifecycle guide; no persistence schema. | P05, P12 |
| Full snapshots are recovery truth | Snapshot/SSE guides and no event-history dependency. | P05, P11 |
| Dual revision is deliberate | Transition matrix and tags; historical `gameVersion` SSE text rejected. | P05, P11–P12 |
| Mockup is UX direction, not fixture math/token authority | Terminal invariant fixtures and fragment-only guide. | P08–P09, P13 |
| Rate/heartbeat/stream values are initial defaults | Guide separates fixed public rates from adjustable snapshot/presence/stream admission. | P10, P12 |
| Deferred encoding choices | Research and schema catalogue fix time, ratio, coordinate, tag, ID, and problem catalogue. | P01–P02, P07, P10 |

## Constitution coverage

| Principle | Planned enforcement |
|---|---|
| Contract before implementation | Canonical root and root-path audit; consumers never edit source contract. |
| Server authority/player privacy | Intent-only requests, caller-derived seat, projection schemas/privacy fixtures, secret exclusion. |
| Simple replaceable boundaries | One npm product, one public validator entry, focused schemas, no app/runtime framework. |
| Practical proof | P01–P13 target behavior/races/privacy rather than coverage quotas/private methods. |
| Accessible/secure/observable operation | Stable localization keys/codes, exact browser security, safe health/retry/correlation semantics. |
| Independent artifacts/current evidence | Source/derived/baseline/example/guide roles; fresh exact commands; no checked-box-as-proof claim. |
| Local production-like readiness | Exact feature gate; bounded resources/protocols; no missing runtime called green. |
| Proportionate process | Work packages support task generation without adding CI/deployment/ADR/second contract bureaucracy. |

## CHK001–CHK040 planning map

| Checklist item | Planning evidence now present | Required future task evidence before `[x]` |
|---|---|---|
| CHK001 | Plan source tree/WP01 and canonical boundary. | Tasks name every bundle artifact and prohibit application code. |
| CHK002 | Exact operation IDs and one-definition graph in OpenAPI design. | Tasks assign each operation/shared wire element to canonical paths. |
| CHK003 | Research decisions 1–4 and plan technical context. | Tasks distinguish dialect/tooling, semver/API/revisions, and source/derived outputs. |
| CHK004 | Research decision 10 and compatibility policy design. | Tasks own additive/breaking fixtures, immutable baseline, changelog, migration wording. |
| CHK005 | Functional coverage row FR-001–008. | Each requirement maps to at least one real task/path/proof. |
| CHK006 | Ruleset schema and WP03. | Task owns meta/rulesets and both exact immutable fixtures. |
| CHK007 | DisplayName primitive and violation fixtures. | Task owns normalization/trim/code-point/control/plain-text evidence. |
| CHK008 | Security classification/cookies and WP04. | Tasks own locator/session/CSRF schema/header boundaries. |
| CHK009 | Create/invitation/post-consent meta operation designs. | Separate task outcomes for create uncertainty, URL/landing guide, CSRF bootstrap. |
| CHK010 | Join gate order/uniform problem/recovery design. | Separate tasks prove allocation, gate precedence, uniform failure, unknown join. |
| CHK011 | Rotation and CORS design. | Tasks own rotation/recovery and exact-origin/credentials/preflight. |
| CHK012 | Functional rows FR-009–027. | Every range maps to task + artifact + proof with no consumer policy invention. |
| CHK013 | Snapshot/deadline/observation-time schema design. | Tasks own snapshot/cache/ETag and lifecycle fixtures. |
| CHK014 | Board unions/privacy matrix. | Tasks include owner/guest preterminal and terminal projection fixtures. |
| CHK015 | Research decision 4 and revision transition table. | Tasks own tags/preconditions/exact advances in schemas/examples/tests. |
| CHK016 | Command union/normalization/receipt design. | Tasks own seven variants, retry ordering, race/repeated-target fixtures. |
| CHK017 | Game phase transition table. | Tasks map waiting/placement/ready/fire/resign/abandon/terminal membership. |
| CHK018 | Terminal schema and invariant list. | Tasks own every field, clock, null/ratio/fleet/client-consumption proof. |
| CHK019 | Functional rows FR-028–051. | Every range maps to task + artifact + proof. |
| CHK020 | SSE design/WP07/P11. | Tasks own framing, replacement, bounds, replay/fallback, unknown-command pause. |
| CHK021 | Lifecycle table/WP07/P12. | Tasks own idle/absolute/terminal/presence/deadline/restart fixtures. |
| CHK022 | Operation/problem matrices distinguish fixed/adjustable/capacity. | Tasks own public rates, adjustable controls, retry bounds, non-enumeration. |
| CHK023 | Problem and health catalogues/localization boundary. | Tasks own shape/status/code/privacy and health 200/503 fixtures. |
| CHK024 | Functional rows FR-052–065. | Every range maps to task + artifact + proof. |
| CHK025 | SC-001–003 table. | Tasks explicitly link canonical/example/journey evidence. |
| CHK026 | SC-004–006 table. | Tasks explicitly link privacy/recovery/two-consumer evidence. |
| CHK027 | SC-007–009 table. | Tasks explicitly link compatibility/redaction/stats evidence. |
| CHK028 | Story 1 row and edge fixtures. | Tasks link create/invite/rotate/redemption scenarios. |
| CHK029 | Story 2 row and phase/race fixtures. | Tasks link waiting/placement/ready/fire/repeat/concurrency. |
| CHK030 | Story 3 row and P05/P11/P12. | Tasks link unknown/stale/SSE/presence/epoch scenarios. |
| CHK031 | Story 4 row and terminal fixtures. | Tasks link abandon/resign/disclose/leave/replay. |
| CHK032 | Story 5 row and P01/P06/P07. | Tasks link independent consumers/evolution without invented policy. |
| CHK033 | First ten edge rows. | Tasks name each lost/race/identity/name/concurrent/deadline class. |
| CHK034 | Remaining stats/SSE/privacy/capacity edge rows. | Tasks name each fixture/assertion. |
| CHK035 | Scope table and plan constraints. | Task ledger limits writes and lists explicit exclusions/non-promises. |
| CHK036 | Assumption table and research encodings. | Each assumption is a task constraint, decision, or explicit deferral. |
| CHK037 | Constitution rows 1–2. | Tasks preserve canonical ownership, caller projections, and secret exclusion. |
| CHK038 | Constitution rows 4–5 and P03–P13. | Tasks name race/security/privacy/accessibility/localization/diagnostic review evidence. |
| CHK039 | Constitution rows 3, 6–7. | Tasks preserve independent artifacts, bounds, current evidence, honest no-runtime limit. |
| CHK040 | This complete matrix. | Reviewer confirms every governing row has real task ID + path + proof after `$speckit-tasks`. |

## Checklist closure rule

- `checklists/requirements.md` may close after its wording and notes accurately describe this
  contract-focused specification and this plan resolves its technical planning decisions.
- `checklists/task-traceability.md` remains unchecked through `$speckit-plan`. Its own instructions
  require generated `tasks.md`, reviewer evaluation, and links from every row above to actual task IDs.
- After `$speckit-tasks`, update this matrix or the task ledger with concrete task IDs, review all
  CHK001–CHK040, then run `$speckit-analyze` before implementation.
