# V1 Problem Catalogue

## Common representation

All pre-commit application failures use `application/problem+json` and this language-neutral shape:

```json
{
  "type": "urn:battleship:problem:stale-game-version",
  "status": 412,
  "code": "stale-game-version",
  "correlationId": "corr_AAAAAAAAAAAAAAAA",
  "serverEpoch": "123e4567-e89b-42d3-a456-426614174000",
  "recovery": {
    "action": "FETCH_SNAPSHOT",
    "currentGameVersion": 9,
    "currentSnapshotRevision": 11
  }
}
```

Rules:

- `type` is exactly `urn:battleship:problem:<code>`.
- `status` equals the HTTP status.
- `correlationId` is opaque, bounded, non-sensitive, and may differ between equivalent failures.
- `serverEpoch` appears only when current-process evidence helps safe reconciliation.
- `violations`, `conflictReason`, and `recovery` appear only where permitted below.
- `Retry-After` and `recovery.retryAfterSeconds` are both 1..300 and equal when both appear.
- `title`, `detail`, `instance`, localized/user-facing text, submitted values, raw command/body content,
  stack/exception data, secrets/cookies, invitations, boards, metrics, counts, and internal state are
  prohibited.

## Closed code/status matrix

| Status | Code | Permitted structured additions | Safe recovery meaning |
|---:|---|---|---|
| 400 | `malformed-request` | none | Body/header syntax could not be parsed; no submitted value is echoed. |
| 401 | `authentication-required` | optional `serverEpoch` | Required browser identity is absent; v1 membership recovery is unavailable. |
| 401 | `invalid-session` | optional `serverEpoch`; session-clearing `Set-Cookie` | On create/join or a session-required operation, the presented session is malformed/unknown and is cleared. Metadata, ruleset, and health public safe reads ignore it and never use this problem. A later public create/join may be explicit; game authority is not restored. |
| 403 | `request-security-rejected` | none | CSRF, Origin, Fetch Metadata, or credentialed-CORS policy failed before resource disclosure. |
| 403 | `operation-forbidden` | optional `serverEpoch` | Caller proved membership but the seat/retained role cannot use this endpoint; no hidden reason. |
| 404 | `game-unavailable` | optional current `serverEpoch` | Generic missing/unprovable/cleaned/restarted result. It never confirms prior existence or cause. |
| 409 | `invitation-unavailable` | none beyond required fields | Uniform admitted redemption failure. No retry/reason/existence hint. |
| 409 | `command-id-reused` | optional `serverEpoch`; `recovery.action: FETCH_SNAPSHOT` | Retained ID was reused with different normalized content; do not replay with that ID. |
| 409 | `game-conflict` | safe `conflictReason`; optional caller-safe recovery revisions/action | Authenticated intent conflicts with current public workflow/domain state. |
| 409 | `resign-required` | `recovery.action: RESIGN` | Active-play leave cannot stand in for the explicit `RESIGN` command. |
| 410 | `game-expired` | required current `serverEpoch`; `recovery.action: NONE` | Current process can prove the member game's deadline was reached; no resurrection. |
| 412 | `stale-game-version` | current epoch/game/snapshot revisions; `recovery.action: FETCH_SNAPSHOT` | A new command used stale state. No automatic replay under a new version. |
| 413 | `payload-too-large` | optional stable `violations` with maximum bytes | Rejected before game/invitation/domain lookup. |
| 415 | `unsupported-media-type` | optional stable `violations` naming accepted media type | Unsafe body must use the documented JSON media type. |
| 422 | `validation-failed` | non-empty bounded `violations` | Parsed request/header/path is well formed but violates public constraints. |
| 428 | `precondition-required` | `recovery.action: FETCH_SNAPSHOT` | Versioned command/leave omitted `If-Match` or required body precondition. |
| 429 | `rate-limit-exceeded` | `recovery.action: RETRY_LATER`, `retryAfterSeconds` | Fixed or adjustable admission limit; reveals no resource/invitation/membership state. |
| 500 | `internal-error` | optional `serverEpoch`; `recovery.action: NONE` | Safe unexpected failure; no implementation text. Unknown secret-bearing outcomes are not replayed. |
| 503 | `service-unavailable` | required `recovery.action: RETRY_LATER`, `retryAfterSeconds`, and matching `Retry-After` | Known capacity/draining rejection occurred before application work committed. Existing games are not evicted. A lost/unknown create or rotation outcome remains governed by its separate no-blind-replay rule. |

These 19 rows are the complete initial application problem code list. Validation requires exact
set equality between this catalogue, `ProblemDetail.code`, named OpenAPI responses, examples, and
`operation-expectations.json`; duplicates, omissions, or extra codes fail the gate. New codes are additions to a closed enum
and are breaking in v1 unless a later contract explicitly supplies a safe unknown-code fallback.

## Validation violation rules

`violations[].field` is a stable JSON Pointer-like location such as `/displayName`, `/command/type`,
or header pseudo-path `/headers/If-Match`. It never includes an input value.

Closed `rule` values:

- `required`;
- `unknown-field`;
- `invalid-type`;
- `invalid-format`;
- `out-of-range`;
- `not-normalized`;
- `contains-control-character`;
- `unsupported-ruleset`;
- `invalid-coordinate`;
- `invalid-ship`;
- `invalid-precondition`.

`parameters` may contain safe bounds or stable accepted identifiers only, for example
`{"minimum":1,"maximum":32}` or `{"acceptedMediaType":"application/json"}`. It cannot contain
display names, invitation material, cookie/header values, coordinates from a raw command, or hidden
domain state.

## Safe conflict reasons

`game-conflict` may use only reasons that are already safe for the authenticated caller:

- `WAITING_FOR_GUEST`;
- `PLACEMENT_NOT_ALLOWED`;
- `FLEET_INCOMPLETE`;
- `ILLEGAL_PLACEMENT`;
- `READINESS_LOCKED`;
- `COMMAND_NOT_ALLOWED`;
- `NOT_YOUR_TURN`;
- `TARGET_ALREADY_RESOLVED`;
- `INVITATION_NOT_ROTATABLE`;
- `GAME_ALREADY_TERMINAL`.

No reason identifies a missing/valid invitation, opponent coordinate/ship, concealed starting seat,
internal receipt, rate key, subscriber, lock, capacity count, or whether an unproved game existed.

## Recovery actions

Closed actions:

- `NONE`: stop; no safe automated recovery is promised.
- `RETRY_SAME_REQUEST`: only when operation-specific documentation says the same identity/body is
  safe; never creation or unknown invitation rotation.
- `FETCH_SNAPSHOT`: retrieve authoritative current snapshot before deciding any new intent.
- `RETRY_LATER`: wait for the bounded retry interval; operation-specific replay rules still apply.
- `CREATE_NEW_GAME`: only after explicit user confirmation under the unknown-create policy.
- `ROTATE_INVITATION`: owner makes a new explicit rotation after reconciliation; never an automatic
  replay of an unknown rotation.
- `RESIGN`: issue the explicit versioned `RESIGN` command if still allowed.

Problems never instruct a browser to change `expectedVersion`, mint a new command ID, infer success,
or recover hidden authority automatically.

## Uniform invitation-unavailable proof

After transport/security/session/rate/capacity gates admit a syntactically valid join request, all of
these cases serialize the same status, code, type, field set, content type, cache policy, and CORS
headers (apart from opaque correlation value):

- missing game;
- wrong, expired, revoked, or consumed secret;
- invitation/game deadline reached;
- guest seat occupied;
- valid identity already controls owner seat;
- redemption loses a race.

The response contains no `serverEpoch`, violation, conflict reason, retry action, game ID, seat,
deadline, or invitation state. This override takes precedence over ordinary 404/410/403
classification after the request reaches the admitted redemption decision.

## SSE boundary

Only failures before the `200 text/event-stream` response is committed use this catalogue. After
commit, stream replacement, overflow, lifetime expiry, and transport failure close the stream; no
problem JSON is written into SSE data.

## Health boundary

The defined `503` from `GET /actuator/health` uses `HealthResponse`, not `service-unavailable`, because
it is the normal live-but-not-ready representation. Application endpoints rejected during draining
use the `503 service-unavailable` problem.
