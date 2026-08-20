# OpenAPI and Protocol Design

## Canonical graph

- `contracts/openapi/openapi.yaml` is the only canonical HTTP root.
- It declares `openapi: 3.1.1`, contract `info.version: 1.0.0`, and
  `jsonSchemaDialect: https://json-schema.org/draft/2020-12/schema`.
- Use a relative server URL `/`; runtime hostname/port/public path are integration configuration and
  must not be baked into the contract.
- Path/component files and `contracts/schemas/*.schema.json` are reached only through `$ref` from the
  root graph. A wire field has one defining schema.
- `dist/openapi.json` and `baselines/<version>/openapi.json` are generated from the graph. They are not
  edited or used to redefine source.

## Exact operation identifiers

| operationId | Method and path |
|---|---|
| `getMetadata` | `GET /api/v1/meta` |
| `listRulesets` | `GET /api/v1/rulesets` |
| `createGame` | `POST /api/v1/games` |
| `joinGame` | `POST /api/v1/games/{gameId}/join` |
| `getGameSnapshot` | `GET /api/v1/games/{gameId}/snapshot` |
| `submitGameCommand` | `POST /api/v1/games/{gameId}/commands` |
| `streamGameEvents` | `GET /api/v1/games/{gameId}/events` |
| `rotateGameInvitation` | `POST /api/v1/games/{gameId}/invite/rotate` |
| `sendGamePresence` | `POST /api/v1/games/{gameId}/presence` |
| `leaveGame` | `POST /api/v1/games/{gameId}/leave` |
| `getHealth` | `GET /actuator/health` |

No alternate operation, alias, `HEAD`, generic action resource, or `/api/v2` compatibility surface is
published. CORS `OPTIONS` is described in the API guide/header matrix but is not an application
operation.

## Media types and body policy

- Success and health JSON: `application/json`.
- Pre-commit application failure: `application/problem+json`.
- Events: `text/event-stream` with UTF-8 JSON data.
- Unsafe requests with bodies require `application/json`; unsupported content type is 415.
- Each request body has a hard 16 KiB maximum. Oversize is 413 before invitation/game/domain lookup.
- `rotateGameInvitation` and `sendGamePresence` have no request body; they still require unsafe-request
  security gates.
- Request JSON objects are closed. Response and event JSON objects permit future optional fields.

## Wire primitives

- Time: strict UTC RFC 3339 with three millisecond digits and `Z`.
- Integer maximum: `9007199254740991`.
- Coordinate: zero-based `{rowIndex,columnIndex}`.
- Game ID: canonical unpadded base64url of exactly 16 bytes, matching
  `^[A-Za-z0-9_-]{21}[AQgw]$` plus decode/re-encode equality.
- Invitation secret: canonical unpadded base64url of exactly 32 bytes, matching
  `^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$` plus decode/re-encode equality.
- Server epoch and command ID: lowercase canonical UUID v4.
- Accuracy ratio: null for zero shots; otherwise number 0..1 rounded half-up to four fractional
  digits, with exact numerator/denominator.
- Snapshot/game tags: opaque strong entity tags with separate `bs-s1`/`bs-g1` prefixes.

Clients may validate these shapes but may not infer authorization or reconstruct opaque values.

## Components and reuse

OpenAPI components reference the schemas catalogued in [schema-catalog.md](schema-catalog.md) and add
HTTP-only components:

- parameters: `GameId`, `IfMatch`, `IfNoneMatch`, `LastEventId`, `Origin`, `CsrfHeader`;
- headers: `SnapshotETag`, `NoStore`, `VaryOrigin`, `RetryAfter`, `SessionCookie`, `CsrfCookie`,
  `ClearSessionCookie`, CORS allow/expose headers;
- security scheme: `browserSession` as an `apiKey` in cookie named `__Host-battleship_session`;
- named responses for each problem code/status combination and for 304/204;
- request bodies: `CreateGameRequest`, `JoinGameRequest`, `GameCommandEnvelope`, `LeaveEnvelope`;
- success schemas: metadata, rulesets, snapshots, invitation wrappers, presence, health.

The CSRF double-submit value, exact Origin, and Fetch Metadata policy require multiple coordinated
headers/cookies and are documented at operation level; they are not misrepresented as an OAuth/API-key
security scheme.

## Security classification

| Operation class | Session | CSRF | Exact Origin/CORS | Fetch Metadata |
|---|---|---|---|---|
| Metadata/rulesets/health safe reads | Presented session ignored for authority, validation, and lifecycle; no session issue/refresh/clear | No | Exact configured frontend Origin for credentialed browser use | Safe-read policy only |
| Create/join | Optional valid session reused; invalid presented session is 401+clear | Yes | Required exact Origin | Reject unsafe cross-site/incompatible browser mode/destination |
| Snapshot/events | Required game membership | No | Required exact Origin; events validate before allocation | Safe-read/SSE policy |
| Commands/rotate/presence/leave | Required game membership | Yes | Required exact Origin | Reject unsafe cross-site/incompatible browser mode/destination |

Gate ordering for join is fixed: transport/media/body-size -> CSRF/Origin/Fetch Metadata -> presented
session -> source rate -> capacity -> syntactic validation -> invitation/game lookup and atomic domain
decision. Invitation validity is never inspected by an earlier gate.

### Fetch Metadata truth table

| Request class | Header values | Result after CSRF/exact-Origin checks |
|---|---|---|
| Unsafe browser fetch/XHR | `Sec-Fetch-Site: same-origin` or `same-site`; `Sec-Fetch-Mode: cors` or `same-origin`; `Sec-Fetch-Dest: empty` | Admit this gate. |
| Unsafe browser cross-site | `Sec-Fetch-Site: cross-site` or `none` | Reject `403 request-security-rejected`. |
| Unsafe browser incompatible mode | `navigate`, `no-cors`, `websocket`, or any mode other than `cors`/`same-origin` | Reject `403 request-security-rejected`. |
| Unsafe browser incompatible destination | any destination other than `empty` | Reject `403 request-security-rejected`. |
| Partial Fetch Metadata set | one or two of site/mode/destination absent | Reject `403 request-security-rejected`; do not downgrade it to a non-browser request. |
| Non-browser client | all Fetch Metadata headers absent | Admit only when the same CSRF cookie/header and exact-Origin checks pass. |
| Safe read or SSE | Fetch Metadata present or absent | No Fetch Metadata/CSRF requirement; exact-Origin/CORS and authentication/membership still apply. |
| CORS preflight | standard preflight headers | Evaluate preflight allowlist; do not require CSRF or Fetch Metadata. |

P10 fixtures cover every row. `Sec-Fetch-User` is neither required nor used for authorization.

## Cookies

`__Host-battleship_session`:

- `Secure; HttpOnly; SameSite=Strict; Path=/`; no `Domain`;
- no `Max-Age` or `Expires` on issue/refresh;
- invalid-cookie clearing expires the same name/path with deletion attributes;
- metadata, ruleset, and health public safe reads never inspect it for authority or session lifecycle
  and never issue, refresh, or clear it; metadata may separately issue/refresh only the CSRF cookie;
- never appears in schemas, URLs, examples except literal `<redacted>` header shape, browser storage,
  event data, logs, metrics, or problem values.

`BATTLESHIP-XSRF-TOKEN`:

- host-only `Secure; SameSite=Strict; Path=/`; no `Domain`; not `HttpOnly`;
- established/refreshed by metadata;
- echoed only in `X-Battleship-CSRF` for unsafe operations;
- not a game/membership credential.

## CORS and cache matrix

Every response to an allowed credentialed Origin—including successes, application problems, 204,
304, health, and SSE handshake responses—includes:

- `Access-Control-Allow-Origin: <exact configured frontend origin>`;
- `Access-Control-Allow-Credentials: true`;
- `Vary: Origin`;
- `Access-Control-Expose-Headers: ETag`.

A disallowed Origin is rejected as `403 request-security-rejected` before resource lookup and does
not echo `Access-Control-Allow-Origin` or `Access-Control-Allow-Credentials`. Automatic preflight
uses the same exact allowlist without creating an application operation.

Preflight:

- methods: `GET, POST, OPTIONS`;
- headers: `Content-Type, If-Match, If-None-Match, Last-Event-ID, X-Battleship-CSRF`;
- credentials allowed; exact origin only; no wildcard;
- no v1 `Access-Control-Max-Age` promise.

Every API JSON response, game-specific response/problem, secret-bearing response, 304, and SSE stream
uses `Cache-Control: no-store`. Health also uses no-store. This deliberately favors simple safe
behavior over public-cache optimization.

## Conditional request and mutation processing

Snapshot read:

1. Authenticate membership and linearize deadline decision.
2. Build/capture one caller-safe representation revision.
3. Compare caller-supplied `If-None-Match` against its strong snapshot tag.
4. Return 304 with `ETag`, `Cache-Control: no-store`, and CORS/Vary headers only when the complete
   authorized representation is unchanged; otherwise return 200 snapshot + matching ETag.

Command/leave:

1. Run transport/request-security/session/deadline/admission gates.
2. Look up retained receipt by game + membership + `commandId`.
3. If found, compare normalized semantic content; replay identical result or return
   `command-id-reused` before freshness/domain checks.
4. Require `If-Match` and numeric `expectedVersion`; missing is 428, invalid/disagreement is 422,
   new stale state is 412.
5. Validate authoritative phase/ruleset/turn/domain rules and commit at most one transition.
6. Return the recorded caller-safe snapshot or 204 result.

Create and rotation do not claim idempotency. Join uncertainty reconciles only through authenticated
snapshot when the membership cookie was received.

## Snapshot and revision semantics

- Create: `(gameVersion,snapshotRevision) = (0,0)`.
- Join, rotation, pre-play abandonment, and each accepted game command: both +1 exactly once.
- Eligible presence that moves the deadline: game version unchanged; snapshot revision +1 exactly
  once; new snapshot published to both memberships.
- Terminal membership revocation, read, 304, failure, duplicate replay, repeated target,
  non-extending presence, stream activity/reconnect/heartbeat: neither changes.
- Snapshot `observedAt` is fixed for a snapshot revision; reread does not manufacture a new value.

## SSE representation

OpenAPI describes the event endpoint as `text/event-stream` and references raw text examples. The JSON
after `data:` is directly a `GameSnapshot`; there is no JSON event wrapper schema.

```text
event: snapshot
id: 17
data: {"contractVersion":"1.0.0","snapshotRevision":17,...}

```

- ID is decimal `snapshotRevision`, never `gameVersion`.
- `Last-Event-ID` is a hint only inside the authenticated game/current epoch.
- Registration + initial current snapshot capture is atomic.
- One active stream per membership; new replaces old.
- 15-second comment heartbeat; 20-minute maximum lifetime.
- Bounded authorized replay; newest-unsent-snapshot coalescing; close slow/superseded streams.
- After commit, transport/replacement/overflow/lifetime errors close the stream without an HTTP
  problem body.
- Full conditional snapshot is the correctness fallback for gaps, foreground, failure, or degraded
  polling.

## Invitation representation and examples

`CreateGameResponse` and `RotateInvitationResponse` contain:

```json
{
  "snapshot": {},
  "invitation": {
    "url": "https://app.example/battleship/join/AAAAAAAAAAAAAAAAAAAAAA#invite=redacted",
    "expiresAt": "2026-08-20T12:49:56.789Z"
  }
}
```

Committed examples use only the visibly inert `redacted` fragment sentinel; it is never treated as
an issuable `InvitationSecret`. Positive secret-shape validation creates ephemeral values only in
ignored temporary memory/files. No committed fixture contains a reusable secret or full realistic
fragment token.

## Compatibility policy mechanics

The compatibility policy separates:

- additive: optional response/event property with documented old-consumer tolerance;
- breaking: path/method/status/header removal/change, request or response required-field change,
  type/meaning/security/event change, or closed enum/discriminator addition/removal;
- review-required: changes the scoped mechanical comparator cannot classify without semantics.

For 1.0.0, synthetic pairs prove classification. `check:candidate` validates the candidate without
requiring the not-yet-created baseline. `baseline:create` first runs that candidate gate, refuses to
overwrite an existing release baseline, and atomically archives the passing bundle as
`baselines/1.0.0/openapi.json`. The final `check` requires and validates that immutable baseline.
Later candidate checks require an explicit immutable prior baseline. The changelog records `initial`,
`additive`, or `breaking` plus API-major/migration action. Generated baseline drift or a missing
post-creation baseline fails final validation, and 1.0.0 is never compared with itself as release
evidence.

## Redocly configuration

`redocly.yaml` will:

- register the canonical API root and output;
- set `telemetry: off`;
- extend the OpenAPI specification ruleset and a focused recommended ruleset;
- fail unresolved/unused/conflicting references and duplicate operation IDs;
- require operation descriptions, tags, unique IDs, defined security, and documented success/problem
  responses;
- avoid generic rules that contradict mandated verb-like paths or project-owned health shape;
- treat warnings selected by this feature as errors so `npm run lint` is deterministic.

All Redocly calls go through `validation/run-redocly.mjs`, which sets
`REDOCLY_TELEMETRY=off` and `REDOCLY_SUPPRESS_UPDATE_NOTICE=true` before invoking the pinned local
CLI. `.npmrc` disables npm audit, funding, and update-notifier output. Validation forbids `npx`,
global tools, remote executable schema inputs, and remote `$ref` targets; required dialect/schema
identifier URIs and documentation links remain allowed, and after `npm ci` all checks are local-only.

The validator separately enforces product-specific route count, problem, header/cookie, privacy,
revision, and example rules that generic OpenAPI lint cannot express.

`npm run build` invokes the local Redocly wrapper with exactly `bundle openapi/openapi.yaml --config
redocly.yaml --output dist/openapi.json --ext json
--component-renaming-conflicts-severity=error`. The validator consumes
`validation/operation-expectations.json` as the structured source for operation/status/media/header/
security expectations and cross-checks both the bundle and human-readable matrix against it.
