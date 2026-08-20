# Operation Matrix

## Shared response rules

- Every listed JSON, problem, 304, SSE, and health response includes `Cache-Control: no-store`.
- Every response to an allowed credentialed Origin—including success, problem, 204, 304, health,
  and SSE handshake responses—includes exact `Access-Control-Allow-Origin`,
  `Access-Control-Allow-Credentials: true`, `Vary: Origin`, and exposes `ETag`.
- A disallowed Origin returns `403 request-security-rejected` before resource lookup and does not
  emit `Access-Control-Allow-Origin` or `Access-Control-Allow-Credentials`.
- A success body that contains or is a `GameSnapshot` includes the caller-specific strong `ETag`.
- A pre-commit failure uses `application/problem+json` and one code from
  [problem-catalog.md](problem-catalog.md). Post-commit SSE failure closes the stream.
- `429` and every application-endpoint `503 service-unavailable` problem carry bounded consistent
  `Retry-After` and structured `recovery { action: RETRY_LATER, retryAfterSeconds }` values from 1
  through 300. The defined health 503 uses `HealthResponse` and is the only non-problem exception.
- `Set-Cookie` examples use `<redacted>` only. No cookie value is exposed to JavaScript or JSON.
- Create/join and every session-required operation return `401 invalid-session` for an invalid or
  unknown presented session capability and clear the session cookie before any invitation/domain
  decision. Metadata, ruleset, and health public safe reads ignore a presented session cookie for
  authority, validation, and session lifecycle; they never issue, refresh, or clear that cookie.

## `GET /api/v1/meta` — `getMetadata`

**Access**: public safe read. Exact configured frontend Origin is required for browser cross-origin
use. No membership is created. A presented session does not turn this into an authenticated game
operation and is not validated, issued, refreshed, or cleared. Metadata may independently issue or
refresh only the CSRF cookie.

**Request**: no body, no CSRF header.

**Success**: `200 MetaResponse` with `Set-Cookie: BATTLESHIP-XSRF-TOKEN=<redacted>; Secure;
SameSite=Strict; Path=/`. Contains contract/API identity, current epoch/time, lifecycle policy, and
bounded non-sensitive service availability.

**Failures**: `403 request-security-rejected`, `500 internal-error`, `503 service-unavailable`.

## `GET /api/v1/rulesets` — `listRulesets`

**Access**: public safe read with exact configured frontend Origin for browser cross-origin use. A
presented session is ignored and is not validated, issued, refreshed, or cleared.

**Request**: no body, no CSRF header.

**Success**: `200 RulesetsResponse` with exactly two complete immutable summaries.

**Failures**: `403 request-security-rejected`, `500 internal-error`, `503 service-unavailable`.

## `POST /api/v1/games` — `createGame`

**Access**: public deliberate unsafe request. Reuse a valid presented browser session or issue one on
success. Invalid presented session is rejected/cleared; it is not silently replaced in the same call.

**Request**: CSRF cookie/header, exact Origin, the Fetch Metadata truth table in
[openapi-design.md](openapi-design.md), `application/json`, <=16 KiB,
closed `CreateGameRequest { rulesetId, displayName }`. No command ID; automatic retry is forbidden.

**Success**: `201 CreateGameResponse { snapshot, invitation }`; owner snapshot is version/revision 0;
`ETag` matches that snapshot; issue `__Host-battleship_session=<redacted>` only when no valid identity
was presented. Returned invitation is the only shareable value.

**Failures**:

- `400 malformed-request`;
- `401 invalid-session` for malformed/unknown presented session;
- `403 request-security-rejected`;
- `413 payload-too-large`; `415 unsupported-media-type`; `422 validation-failed`;
- `429 rate-limit-exceeded` for the fixed five/minute bounded-source policy;
- `500 internal-error`; `503 service-unavailable` for capacity/draining.

Unknown success without a received `gameId` is not discoverable; client requires explicit confirmation
before another create.

## `POST /api/v1/games/{gameId}/join` — `joinGame`

**Access**: public explicit unsafe redemption. Valid existing identity may be reused but cannot hold
both seats. Invalid presented session is rejected/cleared.

**Request**: path `GameId`; CSRF cookie/header obtained only after the user chooses Join; exact Origin;
the Fetch Metadata truth table in [openapi-design.md](openapi-design.md); `application/json`; <=16 KiB; closed
`JoinGameRequest { invitationSecret, displayName }`. Fragment never reaches HTTP.

**Success**: `200 GameSnapshot` for guest; consume invitation and allocate membership atomically;
issue/reuse session cookie; refresh eligible idle deadline; both revisions +1; return matching `ETag`.

**Gate/failure order**:

1. `400 malformed-request`, `413 payload-too-large`, and `415 unsupported-media-type` transport gates
   reject without invitation lookup.
2. `403 request-security-rejected` resolves CSRF/Origin/Fetch Metadata before body-value validation.
3. `401 invalid-session` plus clear resolves any invalid presented capability.
4. `429 rate-limit-exceeded` applies the fixed 20/minute bounded-source invitation-attempt gate.
5. `503 service-unavailable` applies capacity/draining admission.
6. `422 validation-failed` rejects a parsed but invalid redemption shape after the non-enumerating
   security/admission gates and before invitation lookup.
7. After admission, every missing/wrong/expired/revoked/consumed/raced/occupied/same-identity failure is
   exactly `409 invitation-unavailable` with equivalent public body and headers.
8. `500 internal-error` remains safe and non-enumerating.

Unknown result recovers only by authenticated snapshot for the known game when membership exists.

## `GET /api/v1/games/{gameId}/snapshot` — `getGameSnapshot`

**Access**: valid session and current/retained membership; exact Origin; no CSRF.

**Request**: path `GameId`; optional exact prior caller-specific `If-None-Match`.

**Success**:

- `200 GameSnapshot` + matching `ETag` when representation differs or validator is absent;
- `304` with no body, same `ETag`, no-store, and CORS/Vary headers only when that caller's complete
  representation is unchanged.

**Failures**: `401 authentication-required`/`invalid-session`, `403 operation-forbidden` or
`request-security-rejected`, generic `404 game-unavailable`, provable `410 game-expired`,
`422 validation-failed` for malformed conditional/path values, adjustable `429 rate-limit-exceeded`,
`500 internal-error`, `503 service-unavailable`.

Reads never extend idle life or advance a revision.

## `POST /api/v1/games/{gameId}/commands` — `submitGameCommand`

**Access**: valid active membership; CSRF, exact Origin, and the Fetch Metadata truth table in
[openapi-design.md](openapi-design.md).

**Request**: path `GameId`; `If-Match`; `application/json`; <=16 KiB; closed
`GameCommandEnvelope { commandId, expectedVersion, command }` with one of seven command variants.

**Success**: `200 GameSnapshot` + `ETag` for accepted transition or identical retained retry. Each new
accepted transition advances both revisions exactly once. Retry does not advance again.

**Failures**:

- `400 malformed-request`; `413 payload-too-large`; `415 unsupported-media-type`;
- `401 authentication-required`/`invalid-session`;
- `403 request-security-rejected`/`operation-forbidden`;
- `404 game-unavailable`; `410 game-expired`;
- `409 command-id-reused`, `game-conflict`, or stable repeat-target `game-conflict`;
- `412 stale-game-version`; `428 precondition-required`;
- `422 validation-failed`, including header/body mismatch or invalid discriminator/coordinate;
- fixed 60/minute/capability `429 rate-limit-exceeded`;
- `500 internal-error`; `503 service-unavailable`.

Duplicate lookup/content equality precedes precondition and domain validation. A client with an
unknown result may only retry the identical request or reconcile a newer snapshot.

## `GET /api/v1/games/{gameId}/events` — `streamGameEvents`

**Access**: valid current/retained membership and exact frontend Origin checked before stream
allocation/replacement. No CSRF. Session cookie only; no URL credential.

**Request**: path `GameId`; optional decimal `Last-Event-ID` replay hint.

**Success**: `200 text/event-stream`; immediate full caller-safe snapshot frame after atomic
registration, one active stream/membership, `id = snapshotRevision`, 15-second comments, 20-minute
maximum, bounded replay/queue. `Cache-Control: no-store`, `Connection: keep-alive` where HTTP version
permits, and `X-Accel-Buffering: no` may be documented as a deployment hint but is not required wire
compatibility.

**Pre-commit failures**: `401 authentication-required`/`invalid-session`,
`403 request-security-rejected`/`operation-forbidden`, `404 game-unavailable`,
`410 game-expired`, `422 validation-failed` for an invalid cursor/path, adjustable
`429 rate-limit-exceeded`, `500 internal-error`, `503 service-unavailable`.

After 200 commit, replacement, overflow, lifetime, or transport failure closes without a problem
body. Heartbeats/reconnects never extend game idle life.

## `POST /api/v1/games/{gameId}/invite/rotate` — `rotateGameInvitation`

**Access**: valid owner membership while guest seat is vacant; CSRF, exact Origin, and the Fetch
Metadata truth table in [openapi-design.md](openapi-design.md).

**Request**: path `GameId`; no body and no idempotency key/precondition. It is a deliberate
secret-bearing non-replay operation.

**Success**: `200 RotateInvitationResponse { snapshot, invitation }` + `ETag`; old invitation revoked;
new URL returned once; both revisions +1 exactly once.

**Failures**: `401 authentication-required`/`invalid-session`, `403 request-security-rejected` or
`operation-forbidden`, `404 game-unavailable`, `409 game-conflict` with non-sensitive
`INVITATION_NOT_ROTATABLE`, `410 game-expired`, `422 validation-failed` for path shape,
`429 rate-limit-exceeded`, `500 internal-error`, `503 service-unavailable`.

Unknown success requires snapshot reconciliation and a separate explicit replacement action, never
blind replay.

## `POST /api/v1/games/{gameId}/presence` — `sendGamePresence`

**Access**: valid non-terminal membership; CSRF, exact Origin, and the Fetch Metadata truth table in
[openapi-design.md](openapi-design.md). Caller invokes only
from a visible tab; visibility is not a client authority field.

**Request**: path `GameId`; no body.

**Success**: `200 PresenceResponse`. Eligible extension returns `extended: true`, changes only
`snapshotRevision` once, and publishes the new snapshot to both streams. A well-formed call inside the
five-minute interval returns `extended: false` and advances neither revision.

**Failures**: `401 authentication-required`/`invalid-session`, `403 request-security-rejected` or
`operation-forbidden`, `404 game-unavailable`, `409 game-conflict` for terminal/incompatible state,
`410 game-expired`, `422 validation-failed` for path shape, adjustable/abuse
`429 rate-limit-exceeded`, `500 internal-error`, `503 service-unavailable`.

## `POST /api/v1/games/{gameId}/leave` — `leaveGame`

**Access**: valid current membership, or possession proven only against an identical retained terminal
leave receipt; CSRF, exact Origin, and the Fetch Metadata truth table in
[openapi-design.md](openapi-design.md).

**Request**: path `GameId`; `If-Match`; `application/json`; <=16 KiB; closed
`LeaveEnvelope { commandId, expectedVersion }`.

**Success**:

- waiting/placement: `200 GameSnapshot` + `ETag` for safe abandoned view; both revisions +1;
- terminal: `204` no body; revoke only caller membership/stream; revisions unchanged;
- identical retained replay: same 200 snapshot or 204 without restoring membership.

**Failures**: `400 malformed-request`, `401 authentication-required`/`invalid-session`,
`403 request-security-rejected`/`operation-forbidden`, `404 game-unavailable`,
`409 resign-required`, `command-id-reused`, or other safe `game-conflict`, `410 game-expired`,
`412 stale-game-version`, `413 payload-too-large`, `415 unsupported-media-type`,
`422 validation-failed`, `428 precondition-required`, `429 rate-limit-exceeded`,
`500 internal-error`, `503 service-unavailable`.

## `GET /actuator/health` — `getHealth`

**Access**: public safe read. A presented session is ignored and is not validated, issued, refreshed,
or cleared; no CSRF applies. A direct or same-origin request with no `Origin` receives the health
result without CORS headers. An allowed configured frontend Origin receives exact
`Access-Control-Allow-Origin`, credentials, `Vary: Origin`, and exposed `ETag`. A disallowed Origin
returns `403 request-security-rejected` before the health body and without allow-origin or
allow-credentials headers.

**Success/readiness result**:

- `200 HealthResponse { status: READY, live: true, ready: true, checkedAt }`;
- `503 HealthResponse { status: NOT_READY, live: true, ready: false, reason: STARTING|DRAINING,
  checkedAt }`, optionally with bounded `Retry-After`.

No RFC 9457 body is used for the defined not-ready health result. It reveals no components,
configuration, capacity/game/stream counts, metrics, secrets, players, or framework internals. Full
game capacity alone remains ready.

## Automatic CORS preflight

Automatic `OPTIONS` support is not one of the 11 application operations. An allowed preflight returns
`204` without a body and only the exact allow-origin/credentials/method/header/Vary contract. A
disallowed origin/request is rejected without resource lookup using the safe request-security policy.
The 403 does not echo allow-origin/credentials headers. No alternate application route or business
response is created.
