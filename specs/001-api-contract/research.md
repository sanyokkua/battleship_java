# Phase 0 Research: Anonymous Battleship API Contract

## Purpose and authority

This research resolves the technical decisions left to planning by
`specs/001-api-contract/spec.md`. The authority order is the project constitution, the active
clarified feature specification, approved planning decisions, design/research evidence, and then
generic standards. Historical `master` code and mockup simulation behavior are not compatibility
sources.

All research questions are resolved. Phase 1 contains no deferred wire decision.

## Decision 1: Contract dialect and toolchain

**Decision**: Use OpenAPI 3.1.1 with `jsonSchemaDialect` set to JSON Schema draft 2020-12. Build an
independent Node.js 24.19.0/npm product. Pin `@redocly/cli` 2.45.0, Ajv 8.20.0,
`openapi-typescript` 7.13.0, and TypeScript 6.0.3 exactly in `package-lock.json`. Set
`telemetry: off` in `redocly.yaml`. Invoke the pinned local Redocly CLI only through
`validation/run-redocly.mjs`, which sets `REDOCLY_TELEMETRY=off` and
`REDOCLY_SUPPRESS_UPDATE_NOTICE=true` before execution. Keep npm install output deterministic with a
product-local `.npmrc` containing `audit=false`, `fund=false`, and `update-notifier=false`. Ajv is
loaded locally; `openapi-typescript` and `tsc` use only their local installed binaries and the local
bundle, never `npx`, a global tool, a remote executable schema input, or a remote `$ref` target.
Required dialect/schema identifier URIs and documentation links remain allowed. The canonical root is
`contracts/openapi/openapi.yaml`.

**Rationale**: These versions are the accepted API-plan baseline and are compatible with the
contract dialect. OpenAPI 3.1 shares JSON Schema vocabulary and lets the reusable schemas remain
language-neutral. Exact lockfile versions prevent an incidental upgrade from changing the accepted
wire surface or validation result. The wrapper applies Redocly's documented telemetry and update
notice controls consistently across supported shells. In the completed product workflow, `npm ci` is
the only network-bearing gate step; one-time lockfile creation during implementation occurs after the
product-local controls exist and is not a published validation gate. Every `npm run` validation
command is network-independent afterward.

**Alternatives considered**:

- OpenAPI 3.2: newer is not automatically accepted; it would reopen the approved 3.1-compatible
  baseline and consumer compatibility.
- OpenAPI 3.1.0: valid, but 3.1.1 incorporates specification corrections without changing the chosen
  major/minor dialect.
- AsyncAPI or a Java contract framework: rejected because SSE is one HTTP representation and a
  second framework/source would violate contract ownership.
- Generated Java or frontend clients in this feature: rejected; only a temporary type smoke compile
  is allowed.

References: `docs/rewrite_context_doc/Planning/03-api/hlsd-and-plan-input.md`,
`docs/rewrite_context_doc/Planning/00-decisions-and-evidence.md`,
[Redocly usage data](https://redocly.com/docs/cli/usage-data), and
[Redocly update notifications](https://github.com/Redocly/redocly-cli#update-notifications).

## Decision 2: Canonical source, references, and derived artifacts

**Decision**: Keep one split OpenAPI root with `$ref` links to focused path/component files and the
standalone JSON schemas. Never copy a schema definition into OpenAPI and `schemas/`. The local
Redocly wrapper bundles the graph with `bundle openapi/openapi.yaml --config redocly.yaml --output
dist/openapi.json --ext json --component-renaming-conflicts-severity=error` into ignored
`dist/openapi.json`. The committed `baselines/1.0.0/openapi.json` is generated
from a passing candidate as the immutable future comparison input. Temporary TypeScript output is
created under `.tmp/` and removed in success and failure paths.

`validation/operation-expectations.json` is the single structured test oracle for each operation's
method, path, operationId, success/failure statuses, media types, required headers, security class,
and retry behavior. Human-readable operation documentation is checked against that oracle and the
bundled OpenAPI so validation cannot silently reproduce prose drift.

**Rationale**: A split source remains one canonical reference graph, keeps files reviewable, and
allows schema/examples to be validated directly. The bundled file supports independent consumers.
A release baseline is distinct from source: it records what was released and is never edited by hand.

**Alternatives considered**:

- One very large YAML file: simpler file count but harder review and direct JSON Schema/example use.
- Checked-in generated clients: creates another ownership surface and consumer coupling.
- Two independent OpenAPI/JSON Schema definitions: rejected because drift would be unavoidable.
- Comparing 1.0.0 to itself: explicitly rejected by the success criteria; synthetic fixtures prove
  the classifier for the initial release.

Reference: [Redocly bundle documentation](https://redocly.com/docs/cli/commands/bundle).

## Decision 3: Stable wire encodings

**Decision**:

- Time instants use UTC RFC 3339 with exactly three fractional digits and `Z`, for example
  `2026-08-20T12:34:56.789Z`. JSON Schema uses `format: date-time` plus a UTC-millisecond pattern.
- Durations use non-negative integer milliseconds.
- Versions, revisions, durations, counts, and retry seconds are JSON integers no greater than
  `9007199254740991`, keeping JavaScript consumers exact.
- Coordinates are objects `{ "rowIndex": n, "columnIndex": n }`, zero-based. The selected ruleset
  supplies row/column labels and dimensions; consumers do not infer labels from indexes.
- `gameId` is the unpadded canonical base64url encoding of exactly 16 random bytes: exactly 22
  characters matching `^[A-Za-z0-9_-]{21}[AQgw]$`. Validators decode and re-encode for canonical
  equality. It is a public locator and grants no authority.
- `serverEpoch` and client-generated `commandId` are lowercase canonical UUID v4 strings.
- Opaque `shipId` values use 8–64 base64url characters and are only echoed where a schema permits.
- `correlationId` uses 16–64 base64url characters.
- Invitation secrets use the unpadded canonical base64url encoding of exactly 32 random bytes:
  exactly 43 characters matching `^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$`. Validators decode and
  re-encode for canonical equality; implementation/conformance proves cryptographic random issuance.
- `accuracy.ratio` is `null` only for zero shots; otherwise it is a JSON number from 0 through 1,
  rounded half-up to at most four fractional digits. Exact numerator and denominator remain present.

**Rationale**: These choices remove coordinate, time-zone, precision, and JavaScript-number ambiguity
while staying easy for Java and TypeScript consumers. Explicit numerator/denominator values prevent
loss of truth when a displayed percentage uses a different localized precision.

**Alternatives considered**:

- Local times or arbitrary offsets: complicate comparison and fixture determinism.
- One-based coordinates or `A1` strings: make bounds and machine transforms less explicit.
- A decimal string for accuracy: exact but unnecessarily awkward for UI formatting when the exact
  fraction is already carried.
- Unbounded JSON integers: unsafe for ordinary TypeScript number consumers.

## Decision 4: Entity-tag syntax and revision ownership

**Decision**:

- `gameVersion` is the state-concurrency integer. `snapshotRevision` is the caller-visible
  representation-order integer.
- `gameStateTag` is an opaque strong entity tag with grammar
  `"bs-g1.<base64url-value>"`. It binds the game and `gameVersion`, remains unchanged across
  presence-only snapshot changes, and is supplied in the snapshot body for `If-Match`.
- Snapshot response `ETag` is an opaque caller-specific strong entity tag with grammar
  `"bs-s1.<base64url-value>"`. It binds process epoch, game, caller projection, and
  `snapshotRevision`.
- Weak tags are forbidden. Clients compare tags as opaque octet strings and never construct, parse,
  or infer state from their payload.
- Commands and leave echo `gameStateTag` in `If-Match` and send the identical numeric
  `expectedVersion`. Snapshot reads may send the last snapshot `ETag` in `If-None-Match`.

**Rationale**: Separate opaque tags prevent a lifecycle-only representation update from causing a
false command-staleness result while still supporting correct conditional reads and caller privacy.
The prefixes distinguish the two validator classes without exposing their bound inputs.

**Alternatives considered**:

- Use `gameVersion` as snapshot ETag/SSE cursor: rejected because presence can change deadlines
  without changing game state.
- Embed parseable game IDs, seat IDs, and versions: unnecessary information disclosure and invites
  consumers to reconstruct validators.
- Weak ETags: do not meet the full-authorized-representation requirement.

## Decision 5: JSON Schema composition and compatibility openness

**Decision**: Request bodies and command variants reject unknown fields. Use `oneOf` plus a required
`type` discriminator and `const` per command; composed request schemas use
`unevaluatedProperties: false`. Response/event objects define all required v1 fields but do not set a
global unknown-property prohibition, so optional response fields may be added. Closed v1 enums and
discriminated unions are documented as compatibility-sensitive.

**Rationale**: The feature intentionally has asymmetric compatibility: clients cannot submit invented
authority, while old consumers must tolerate new optional response information.

**Alternatives considered**:

- `additionalProperties: false` on all responses: would make approved additive evolution breaking.
- Open request objects: allows accidental or malicious seat/turn/result claims.
- One catch-all command payload: loses field-level validation and discriminator exhaustiveness.

## Decision 6: HTTP success shapes and cache behavior

**Decision**:

- `GET /api/v1/meta`: `200 MetaResponse`.
- `GET /api/v1/rulesets`: `200 RulesetsResponse`.
- `POST /api/v1/games`: `201 CreateGameResponse` containing `snapshot` and `invitation`.
- `POST /api/v1/games/{gameId}/join`: `200 GameSnapshot`.
- `GET /api/v1/games/{gameId}/snapshot`: `200 GameSnapshot` or `304` with no body.
- `POST /api/v1/games/{gameId}/commands`: `200 GameSnapshot`, including identical duplicate replay.
- `GET /api/v1/games/{gameId}/events`: `200 text/event-stream` until close.
- `POST /api/v1/games/{gameId}/invite/rotate`: `200 RotateInvitationResponse`.
- `POST /api/v1/games/{gameId}/presence`: `200 PresenceResponse`.
- `POST /api/v1/games/{gameId}/leave`: `200 GameSnapshot` for pre-play abandonment,
  `204` for terminal membership revocation or its identical replay, and `409 resign-required` in play.
- `GET /actuator/health`: `200 HealthResponse` when ready or `503 HealthResponse` when alive but not
  ready. Its 503 is not an RFC 9457 problem.

All JSON application responses use `application/json`; failures use `application/problem+json`; SSE
uses `text/event-stream`. All API JSON, game-specific, secret-bearing, conditional 304, and SSE
responses use `Cache-Control: no-store`. A response containing a snapshot supplies the matching
strong `ETag`, including create/join/command/rotation/pre-play-leave responses.

**Rationale**: Direct snapshot responses keep recovery authoritative. Only create needs creation
status plus an invitation wrapper; only rotation needs the same extra secret-bearing wrapper.

**Alternatives considered**:

- Generic command-result event history: rejected; consumers recover from full snapshots.
- `204` for accepted commands: would force an extra read and make unknown outcomes harder.
- Framework-owned actuator schema: leaks an implementation type and violates the project-owned
  health boundary.

## Decision 7: CORS, request security, and cookies

**Decision**:

- Credentialed API and SSE responses admit only the exact configured frontend Origin and return
  `Access-Control-Allow-Origin: <that-origin>`, `Access-Control-Allow-Credentials: true`, and
  `Vary: Origin`. No wildcard or origin reflection outside the allowlist is permitted.
- `Access-Control-Expose-Headers` contains `ETag`.
- Preflight permits only `GET`, `POST`, and `OPTIONS`; allowed request headers are `Content-Type`,
  `If-Match`, `If-None-Match`, `Last-Event-ID`, and `X-Battleship-CSRF`. The contract omits
  `Access-Control-Max-Age` in v1 rather than promising a preflight-cache duration.
- Unsafe operations require `application/json` when they have bodies, a maximum 16 KiB body, the
  readable CSRF cookie echoed in `X-Battleship-CSRF`, exact Origin, and compatible Fetch Metadata.
  Rotation and presence have no request body but remain unsafe and require the same CSRF/Origin rules.
- For an unsafe browser request, accept only `Sec-Fetch-Site: same-origin|same-site`,
  `Sec-Fetch-Mode: cors|same-origin`, and `Sec-Fetch-Dest: empty`. Reject `cross-site`, `none`,
  `navigate`, `no-cors`, `websocket`, non-empty destinations, and a partial Fetch Metadata set.
  If all Fetch Metadata headers are absent, treat the caller as non-browser only for this gate and
  continue solely when the same CSRF and exact-Origin checks pass. Safe reads/SSE do not require Fetch
  Metadata or CSRF, but exact-Origin/CORS and membership rules still apply.
- The session cookie is exactly `__Host-battleship_session; Secure; HttpOnly; SameSite=Strict; Path=/`
  with no Domain, Max-Age, or Expires. Only invalid-cookie clearing adds expiry deletion attributes.
- Metadata, ruleset, and health public safe reads ignore a presented session cookie completely for
  authority, validation, and session lifecycle. They never return `invalid-session` and never issue,
  refresh, or clear the session cookie; metadata may independently issue or refresh only the CSRF
  cookie. Create/join and session-required operations validate a presented session and reject and
  clear a malformed or unknown value before domain lookup.
- The CSRF cookie is exactly `BATTLESHIP-XSRF-TOKEN; Secure; SameSite=Strict; Path=/`, with no Domain
  or HttpOnly.

**Rationale**: SameSite is defense in depth, not a replacement for explicit anti-forgery and origin
validation. Omitting a preflight cache promise keeps the contract simple and avoids stale security
configuration.

**Alternatives considered**:

- Wildcard CORS with credentials: invalid and insecure.
- Parent-domain cookies: contradict the same-hostname topology and weakens host scoping.
- CSRF in SSE query parameters: exposes credentials in URLs and logs.
- Cookie-only CSRF defense: insufficient for unsafe cross-origin requests.

## Decision 8: SSE and recovery semantics

**Decision**: Publish only raw SSE frames with `event: snapshot`, `id: <decimal snapshotRevision>`,
and `data: <one-line full GameSnapshot JSON>`. Do not create a nested JSON event envelope. Register
membership and capture/send the initial snapshot atomically. Use one active stream per membership,
15-second comment heartbeats, a 20-minute maximum stream lifetime, bounded authorized replay, a
newest-unsent-snapshot queue, and disconnect for replacement/overflow/transport failure. Treat
`Last-Event-ID` only as a decimal hint within the authenticated game and current epoch; otherwise send
the current snapshot. Order client evidence by `(serverEpoch, snapshotRevision)`.

An eligible presence extension changes deadlines, advances only `snapshotRevision`, and publishes a
new snapshot to both member streams. Reads, failed/non-extending presence, heartbeat, reconnect, and
stream replacement advance neither revision.

**Rationale**: `snapshotRevision` covers visible lifecycle-only changes that `gameVersion` cannot.
Full snapshots make replay optional and eliminate event-history reconstruction.

**Alternatives considered**:

- Historical `gameVersion` event IDs: superseded by the clarified active spec.
- A nested `event-envelope.schema.json`: duplicates SSE framing and is explicitly forbidden.
- Durable replay: outside the ephemeral single-process scope.

## Decision 9: Problem catalogue and localization boundary

**Decision**: Use the 19 closed v1 codes defined in
`contracts/problem-catalog.md`. `type` is the stable
`urn:battleship:problem:<code>`. Required fields are `type`, `status`, `code`, and bounded opaque
`correlationId`; `serverEpoch`, `violations`, `conflictReason`, and `recovery` appear only where
meaningful. `recovery` uses structured actions and optional bounded `retryAfterSeconds`. No problem
contains localized `title`, `detail`, message text, stack/exception data, secrets, board state, raw
command content, internal collection state, or existence evidence.

**Rationale**: Stable structured values allow both frontend languages without freezing English text
in the server contract. A small top-level catalogue plus bounded reason/violation identifiers avoids
one code per private domain fact.

**Alternatives considered**:

- Generic standards' English `title`/`detail`: superseded by the clarified localization boundary.
- One generic 400/409 code: insufficient for safe client recovery and localization.
- Detailed invitation failure reasons: creates an existence oracle.

## Decision 10: Compatibility mechanics

**Decision**: Contract semver begins at 1.0.0 and is independent of API major, game version, snapshot
revision, and process epoch. The compatibility validator compares a normalized canonical bundle to an
explicit immutable baseline and checks the published policy for removed operations/statuses/headers,
new required request or response fields, changed types/meanings/security, and changed closed enums or
discriminators. It also proves representative additive and breaking pairs under
`examples/compatibility/`. The initial release records `initial` in the changelog, runs the synthetic
classifier fixtures through `check:candidate`, then `baseline:create` reruns that gate, refuses to
overwrite an existing release baseline, and atomically archives its passing bundle. Final `check`
requires and validates the immutable baseline; the initial release does not compare the candidate to
itself.

**Rationale**: The custom check is scoped to this fixed contract and exposes exactly which rules are
mechanical versus reviewer-owned. An explicit baseline avoids comparing against a moving branch.

**Alternatives considered**:

- No mechanical compatibility path: fails the success criteria.
- Adopt another large diff framework: adds a second tool/semantic authority without an approved need.
- Treat new closed-enum values as additive: unsafe for exhaustive Java/TypeScript consumers.

## Decision 11: Validation architecture and commands

**Decision**: `validation/validate.mjs` is the single public validation entry point and delegates to
small local modules. `examples/manifest.json` maps every committed example to its canonical schema,
operation/status role, projection seat, expected validity, and scenario tags. The validator checks:

1. reference closure and exact route/method count;
2. JSON Schema validity and every manifest-bound example;
3. request-closed/response-open policy and discriminator consistency;
4. operation/status/header/cookie/security/cache matrices;
5. dual-revision and SSE raw-frame rules;
6. owner/guest privacy-equivalence fixtures;
7. retry, concurrency, deadline, rate/capacity, and restart scenario matrices;
8. terminal-statistics arithmetic/nullability;
9. forbidden secret/full-fragment/stack/raw-body patterns, allowing only documented inert redaction
   sentinels;
10. synthetic compatibility pairs and any explicitly supplied prior baseline; candidate mode permits
    only the not-yet-created initial baseline to be absent, while release mode requires it.

`npm run smoke:typescript` generates into `.tmp/consumer`, compiles with the pinned local
`tsc --noEmit`, and removes the directory. Candidate and final gates both run lint, build, their
respective validation mode, Node tests, and the smoke compile.

**Rationale**: A manifest prevents orphan examples and gives each scenario a reviewer-visible owner.
Focused modules keep the orchestrator simple without creating another product or contract source.
`npm run check:candidate` uses candidate validation before the initial baseline exists;
`npm run baseline:create` runs that gate and archives without overwrite; final `npm run check` uses
release validation and requires the immutable baseline.

**Alternatives considered**:

- Validate only that YAML parses: does not prove examples, privacy, or semantic matrices.
- Snapshot or coverage quotas: do not prove the contract boundary.
- Network-based tests or a fake backend: application implementation is outside feature scope.

## Decision 12: Local run and documentation preview

**Decision**: The contracts product has no HTTP process. `npm run check` is its exact local
run-equivalent, and `npm run build` produces the reviewable bundle. Do not add a documentation server
or Redoc rendering package in feature 001.

**Rationale**: Redocly CLI v2 removed the old standalone `preview-docs` command and its current
`preview` command targets separate documentation products. Adding one solely to satisfy a generic
"run" shape would expand dependencies and ownership without proving contract behavior.

**Alternatives considered**:

- Add a Redoc/Realm/Reef docs project: rejected as a fourth deliverable with no requirement.
- Reuse the removed v1 `preview-docs` command: incompatible with the pinned v2 CLI.

References: [Redocly CLI v2 migration](https://redocly.com/docs/cli/guides/migrate-to-v2) and
[Redocly CLI commands](https://redocly.com/docs/cli/commands).

## Cross-context supersession record

| Older context | Governing Phase 0 resolution |
|---|---|
| API/backend/frontend/integration inputs and decision record use SSE `id = gameVersion`. | Active spec FR-052/054 controls: use decimal `snapshotRevision` and `(serverEpoch, snapshotRevision)`. |
| Backend input says presence does not change the event stream. | Eligible presence publishes a newer snapshot to both streams but does not change `gameVersion`. |
| Decision record says active-play leave is treated as resignation. | Active-play leave returns `409 resign-required`; only explicit `RESIGN` performs the terminal transition. |
| Mockup displays/copies an invitation token and simulates automatic state. | V1 supports only the exact server-issued fragment URL, immediate stripping, transient memory, explicit POST redemption, and authoritative server snapshots. |
| Architecture says starting seat is selected when both players are ready. | It is selected securely at creation, stored concealed, and first disclosed when play begins. |
| Older architecture route list omits presence. | The exact v1 surface contains all 11 operations, including `POST .../presence`. |
| Generic API standard requires `/health/live` and `/health/ready` and discourages verb-like paths. | Feature spec controls: only `GET /actuator/health`; mandated `/invite/rotate` is retained. |
| Generic Problem Details example contains English title/detail. | Stable language-neutral identifiers only; frontend owns English/Ukrainian messages. |
| Generic standards require CI, containers, database testing, and broad observability. | Adopt only applicable redaction/documentation principles; CI, deployment, database, container, and runtime observability implementation are out of feature scope. |

## Phase 0 exit gate

- Contract dialect/tooling: resolved.
- Timestamp, integer, ID, coordinate, ratio, and entity-tag encodings: resolved.
- HTTP success/status/header/cookie/cache/CORS conventions: resolved.
- Problem code catalogue: fixed by Phase 1 design.
- SSE/revision and historical conflicts: resolved in favor of the active spec.
- Compatibility baseline and initial-release evidence: resolved.
- Exact feature-local build/test/lint/local-run commands: resolved.

Phase 1 may proceed.
