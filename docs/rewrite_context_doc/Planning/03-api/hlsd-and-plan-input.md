# Copy-paste input: API contract plan

Read `specs/001-api-contract/spec.md` first. Plan the implementation of that feature only. Preserve its user-visible policy; do not add backend controllers, a web client, an executable application, or a database.

## Architecture and ownership

Create the complete contract artifact set only under `contracts/`. This feature is the source of truth for the v1 wire boundary. The backend will later consume it for conformance but must not redefine paths, headers, snapshots, errors, security semantics, or SSE rules. The contract must not import Java classes or frontend source.

Use OpenAPI 3.1.1 with JSON Schema 2020-12 reusable components. Make `contracts/` an independently buildable Node 24.19.0/npm product. Pin Redocly CLI 2.45.0 for OpenAPI lint/bundle checks, Ajv 8.20.0 for schemas and examples, and OpenAPI TypeScript 7.13.0 plus the repository's selected compatible TypeScript 6.0.3 only for a temporary consumer smoke compile. That smoke test leaves no generated source in another product and does not make feature 001 the owner of frontend code or future frontend compiler upgrades. Disable tool telemetry/update checks during deterministic validation. RFC 9457 is the one problem format. Do not add a second contract framework or a Java dependency to this product.

## Required layout

Plan tasks that create and validate this layout (equivalent file naming is acceptable only when the role remains clear):

```text
contracts/
  package.json
  package-lock.json
  README.md
  redocly.yaml
  openapi/openapi.yaml
  schemas/
    snapshot.schema.json
    command.schema.json
    event-envelope.schema.json
    problem.schema.json
  examples/
    create-owner.json
    join-guest.json
    meta.json
    rulesets.json
    waiting-snapshot.json
    placement-snapshot.json
    playing-snapshot.json
    finished-snapshot.json
    abandoned-snapshot.json
    invite-rotated.json
    presence-extended.json
    presence-not-extended.json
    health.json
    command-duplicate.json
    stale-precondition.problem.json
    expired.problem.json
    restart-unavailable.problem.json
    sse-snapshot.txt
  guides/api-guide.md
  guides/sse-protocol.md
  compatibility-policy.md
  changelog.md
  validation/validate.mjs
```

Do not place generated code, controllers, application configuration, frontend files, or a second implementation of game rules in this tree.

## Contract model and flows

Model the OpenAPI document around reusable resources and representations:

```text
GET  /api/v1/meta
GET  /api/v1/rulesets
POST /api/v1/games
POST /api/v1/games/{gameId}/join
GET  /api/v1/games/{gameId}/snapshot
POST /api/v1/games/{gameId}/commands
GET  /api/v1/games/{gameId}/events
POST /api/v1/games/{gameId}/invite/rotate
POST /api/v1/games/{gameId}/presence
POST /api/v1/games/{gameId}/leave
GET  /actuator/health
```

Do not add aliases or a generic catch-all endpoint. Meta, rulesets, creation, join, and health have their documented public/CSRF rules; every other game resource requires authenticated membership. Join requires the one-time invite rather than existing membership.

- Public metadata and ruleset discovery; metadata includes the process `serverEpoch`, contract/API identity, and non-sensitive service availability information. The metadata response also establishes or refreshes the separate CSRF cookie required before creation or invitation redemption, uses `Cache-Control: no-store`, and creates no membership.
- Game creation, which validates a published ruleset and owner display name, establishes the owner capability cookie, returns a player-safe owner snapshot, and exposes only the canonical one-time invitation URL for sharing.
- Join redemption, which accepts the one-time invitation secret only in a POST body, requires CSRF protections, atomically consumes the invite, establishes the guest capability cookie, and returns a guest snapshot. Document the public landing GET and browser-history fragment stripping in the guide, not as a secret-bearing request parameter.
- Authenticated player-safe snapshot reads with ETag/`If-None-Match`, commands with `commandId`, `expectedVersion`, and `If-Match`, invitation rotation, visible-tab presence, leave, events, and public `GET /actuator/health`.
- Versioning starts at `gameVersion: 0`. Join, invitation-status rotation, pre-play abandonment, and accepted game commands each advance the shared visible version once; presence, reads, failures, and terminal-member revocation do not. Define response and event examples that make these boundaries unambiguous.
- Explicit command union with command-specific payload schemas, normalized-content identity rules, result envelope, allowed-command projection, and all lifecycle/phase constraints stated by the spec.
- A separate leave request/result model that reuses `commandId`, `expectedVersion`, `If-Match`, duplicate-before-precondition ordering, and 428/412/409 problems. Model pre-play abandonment as a versioned full-snapshot result, active-play leave as `resign-required`, and terminal read-membership revocation as a 204 plus stream close. Document the two-entry per-seat leave-receipt tombstone that makes an identical terminal retry safe until game removal.
- A player-safe snapshot schema whose board-cell union distinguishes unknown, own ship, miss, hit, sunk, and automatically revealed moat rather than allowing a generic overloaded `shot` flag.
- A terminal statistics schema with explicit duration units and numerator/denominator fields for accuracy. Cover match, placement, per-seat placement, gameplay, turn intervals, shots, hits, accepted shot-decision total/average/fastest/slowest timing, and per-seat fleet-condition counts. Define the guest-join and accepted-fire timing boundaries in the guide, and omit fleet-derived values from pre-play abandonment.

Define response/header/cookie examples for 200/201/204/304, 400/401/403/404/409/410/412/422/428/429/503, and health readiness. Use one RFC 9457 schema with stable application `code`, `correlationId`, `serverEpoch` where it aids recovery, and bounded retry fields only where a retry is meaningful. Do not write internal exception text into examples.

## Security design to encode

Define cookie attributes and security schemes precisely: `__Host-battleship_session` is host-only, `Path=/`, Secure, HttpOnly, and SameSite=Strict; it is never a request parameter or schema field. Define `BATTLESHIP-XSRF-TOKEN` as a separate host-only, `Path=/`, Secure, SameSite=Strict cookie without HttpOnly, and require its value in `X-Battleship-CSRF` plus an exact configured frontend Origin for every unsafe operation. Require the frontend and API origins to have the same hostname; ports may differ in the local profile. Do not use a parent-domain cookie. Define the Fetch Metadata rule: reject an unsafe browser request identified as `cross-site` or with an incompatible mode/destination; allow same-origin/same-site browser traffic, and allow an absent metadata header only for non-browser clients that still satisfy CSRF and exact-Origin validation. Mark sensitive resources `Cache-Control: no-store`; document exact credentialed CORS/origin behavior without wildcard origins.

Document the invitation shape as `{configuredPublicFrontendBase}/join/{gameId}#invite={oneTimeSecret}`, including an example with a subpath such as `https://app.example/battleship/join/{gameId}#invite={redacted}`. The guide assigns canonical URL construction to the backend from one validated public frontend base URL and requires the client to share the exact response value. The OpenAPI join endpoint receives the secret in a POST body; fragments never traverse HTTP. The guide must require history stripping before explicit user redemption, prohibit automatic redemption, and state rotation/revocation/expiry behavior. Because only a digest is retained, the guide also states that a lost owner invitation URL requires explicit rotation and that an unknown rotation response is never blindly replayed. The SSE guide must prohibit credentials in its URL and rely on the authenticated cookie.

Ensure every guide/example/logging note follows redaction rules: do not include an actual reusable secret, cookie value, capability digest, raw full URL with fragment, or undisclosed board. Use clear redacted placeholders where an example must show shape.

## Versioning, synchronization, and lifecycle design

Specify the exact ETag format contractually enough to compare `If-Match` with `expectedVersion`; a documented opaque version-bound tag is sufficient. Document command processing order as duplicate lookup, normalized-payload equality check, precondition check, then domain validation. State that exact duplicate results remain player-safe and bounded retention is an implementation concern, not an unbounded client promise.

Apply that same ordering to leave. For a terminal retry, authenticate possession of the revoked capability against only the bounded leave receipt and return the prior safe result; do not restore membership or allow any other operation. A successful terminal leave closes that seat's SSE stream. A successful pre-play leave publishes the abandoned snapshot and leaves both former memberships read-only until either explicitly leaves or retention ends.

The SSE guide must make full snapshots the recovery primitive: event `snapshot`, `id` equal to decimal `gameVersion`, `serverEpoch` in the data, immediate snapshot after authenticated registration, 15-second comment heartbeat, one active stream per seat, 20-minute maximum, bounded queue/replay, slow-client close, and `Last-Event-ID` behavior. Define clients' epoch/version acceptance matrix for duplicate, stale, gap, and restart cases, plus conditional snapshot polling fallback.

Document the lifecycle policy in the API guide: accepted command or accepted, rate-limited visible-tab presence alone can extend idle lifetime; read traffic, polling, stream activity, reconnects, heartbeats, and rejected requests cannot. Include the exact retention, absolute-life, invite-life, final-disclosure, leave, resignation, capacity, and draining behavior from the named spec.

Define presence as a small authenticated result containing `extended` and the current idle expiry. A well-formed call inside the five-minute extension interval returns `extended: false` and changes neither state nor version; abuse limits still return 429. Define pre-play abandonment as a shared terminal transition with read-only former memberships and no board disclosure. Define full terminal fleet disclosure only after normal completion or resignation from play; a later finished-game leave revokes only the caller's read membership and changes no game version.

## Validation and task structure

Plan dependency-ordered, independently reviewable tasks. Begin with a contract vocabulary/Problem Details base and ruleset model, then resource paths/security/header conventions, then snapshot/command schemas and examples, then SSE/lifecycle guides, compatibility/changelog, and validation automation. Keep each task's modified paths disjoint when it can be done without coupling; identify the shared canonical document as a serial dependency instead of claiming unsafe parallelism.

Provide lightweight scenario-to-proving-test mapping in the plan:

| Scenario | Proving test |
| --- | --- |
| All referenced schemas and examples are valid and agree with OpenAPI. | Contract validator/linter plus example-schema test. |
| Create/join/rotation cannot expose a seat capability and honor one-time secret rules. | Header/cookie/example scan and negative secret-leak fixtures. |
| Duplicate/stale/missing-precondition command handling remains unambiguous. | Operation/response validation against representative command fixtures. |
| Player snapshots and SSE recovery never expose forbidden opponent data. | Schema/example privacy fixtures plus event protocol validation. |
| Additive versus breaking evolution has a mechanical review path. | Compatibility check against a checked-in released baseline fixture. |

The expected outputs are a versioned, validated contract bundle, concise human guides, representative redacted examples, and `npm ci && npm run check` from `contracts/`. The check lints and bundles OpenAPI, validates every schema/example, scans for forbidden secret shapes, generates TypeScript into a temporary directory, and compiles it without leaving generated source in another product. No coverage percentage, style quota, fake database, or backend integration test is a completion criterion for this feature.
