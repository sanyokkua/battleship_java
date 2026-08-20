# Reusable Schema Catalogue

## Catalogue rules

- Each listed schema has one source file under root `contracts/schemas/` and one stable `$id` in the
  `urn:battleship:schema:1:<name>` namespace.
- OpenAPI references these schemas. It does not restate their properties.
- Request schemas use `unevaluatedProperties: false`; response/event schemas allow unknown optional
  fields while keeping named v1 requirements.
- Closed enums/discriminators are compatibility-sensitive. A new value is breaking unless its schema
  explicitly says the value set is extensible and defines fallback behavior.
- Examples are not embedded repeatedly in schemas. `examples/manifest.json` owns file-to-schema and
  operation/status linkage.

## `common.schema.json`

### Scalar schemas

| Schema | Exact shape |
|---|---|
| `ContractVersion` | string constant `1.0.0` |
| `ApiMajor` | integer constant `1` |
| `GameId` | canonical unpadded base64url of 16 bytes: pattern `^[A-Za-z0-9_-]{21}[AQgw]$` plus decode/re-encode equality |
| `ServerEpoch` | lowercase canonical UUID v4 string |
| `CommandId` | lowercase canonical UUID v4 string |
| `ShipId` | string pattern `^[A-Za-z0-9_-]{8,64}$` |
| `CorrelationId` | string pattern `^[A-Za-z0-9_-]{16,64}$` |
| `Instant` | `date-time` plus strict UTC-millisecond pattern |
| `SafeInteger` | integer, minimum 0, maximum `9007199254740991` |
| `DurationMs` | `SafeInteger` with millisecond semantics |
| `Revision` | `SafeInteger` |
| `DisplayName` | string length 1..32 code points; custom NFC/trim/control validation |
| `InvitationSecret` | canonical unpadded base64url of 32 bytes: pattern `^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$` plus decode/re-encode equality; the all-zero sentinel is denylisted and issuance randomness is behavioral proof |
| `GameStateTag` | string pattern for quoted strong `bs-g1` entity tag; weak tags forbidden |
| `SnapshotTag` | string pattern for quoted strong `bs-s1` entity tag; HTTP header use only |
| `AccuracyRatio` | number 0..1 with at most four fractional digits, or null |

### Value objects and enums

- `Coordinate { rowIndex: SafeInteger, columnIndex: SafeInteger }`; per-ruleset bounds are validated
  by scenario/ruleset checks.
- `Seat`: `OWNER | GUEST`.
- `Orientation`: `HORIZONTAL | VERTICAL`.
- `GamePhase`: `WAITING | PLACEMENT | PLAYING | FINISHED | ABANDONED`.
- `GameCommandType`: `PLACE_SHIP | MOVE_SHIP | ROTATE_SHIP | REMOVE_SHIP | READY | FIRE | RESIGN`.
- `AllowedOperation`: `ROTATE_INVITATION | SEND_PRESENCE | LEAVE_PRE_PLAY | LEAVE_TERMINAL |
  REFRESH_SNAPSHOT | OPEN_STREAM`.
- `FleetCondition`: `UNTOUCHED | DAMAGED | DESTROYED`.

## `meta.schema.json`

### `LifecyclePolicy`

Required constants:

- `idleTimeoutSeconds: 900`;
- `absoluteLifetimeSeconds: 7200`;
- `terminalRetentionSeconds: 300`;
- `invitationLifetimeSeconds: 900`;
- `presenceExtensionIntervalSeconds: 300`;
- `heartbeatIntervalSeconds: 15`;
- `maximumStreamLifetimeSeconds: 1200`.

### `ServiceAvailability`

- required `acceptingNewGames: boolean`;
- optional stable `reason: CAPACITY | DRAINING` only when false;
- no counts, utilization, instance, provider, configuration, or metrics fields.

### `MetaResponse`

Required:

- `contractVersion`, `apiMajor`, `serverEpoch`, `serverTime`;
- `lifecycle: LifecyclePolicy`;
- `availability: ServiceAvailability`.

Metadata creates no game membership. Its response establishes/refreshes the CSRF cookie through HTTP
headers, not JSON.

## `ruleset.schema.json`

### `BoardDefinition`

Required `rowCount`, `columnCount`, ordered unique `rowLabels`, ordered unique `columnLabels`, and
`coordinateBase: 0`. Label-array length must equal the matching dimension.

### `FleetEntry`

Required `shipTypeId`, stable language-neutral `labelKey`, `length`, `count`, and
`orientations: [HORIZONTAL,VERTICAL]`. IDs are unique per ruleset; lengths/counts are positive.

### `PlacementPolicy`

Required:

- `overlap: FORBIDDEN`;
- `orthogonalContact: ALLOWED | FORBIDDEN`;
- `diagonalContact: ALLOWED | FORBIDDEN`.

### `FirePolicy`

Required:

- `shotsPerDecision: 1`;
- `repeatTarget: REJECT`;
- `turnRetention: ON_HIT_OR_SINK | NEVER`;
- `automaticWater: AROUND_SUNK_SHIP | NONE`.

### `DisclosurePolicy`

Required stable values describing per-cell pre-terminal disclosure, sink disclosure, automatic-water
disclosure, and `terminalReveal: BOTH_COMPLETED_BOARDS_AFTER_VICTORY_OR_RESIGNATION`.

### `RulesetSummary`

Required `id`, `labelKey`, `board`, non-empty `fleet`, `placement`, `fire`,
`startingSeatPolicy: RANDOM_AT_CREATION_CONCEALED_UNTIL_PLAY`,
`victoryPolicy: ALL_SHIPS_DESTROYED`, and `disclosure`.

### `RulesetsResponse`

Required `contractVersion`, `apiMajor`, and exactly two unique summaries. Fixtures assert the complete
Sea Battle and Classic 2002 mechanics; the schema remains reusable for a future API-major/ruleset ID.

## `invitation.schema.json`

### Requests

- `CreateGameRequest`: required `rulesetId`, `displayName`; no other fields.
- `JoinGameRequest`: required `invitationSecret`, `displayName`; no game/seat/capability/role field.
- `LeaveEnvelope`: required `commandId`, `expectedVersion`; no client-selected leave result/phase.
- Rotation and presence have no request schema because they have no body.

### Invitation response values

- `Invitation`: required absolute `url` and `expiresAt`. URL description fixes backend construction,
  frontend subpath preservation, `/join/{gameId}#invite=`, and one-time handling. Entropy is not
  inferred from a committed example.
- `CreateGameResponse`: required `snapshot`, `invitation`.
- `RotateInvitationResponse`: required `snapshot`, `invitation`.
- `PresenceResponse`: required `extended`, `serverTime`, `deadlines`, `snapshotRevision`.

Cookie issuance/refresh/clearing remains an HTTP header component, never a JSON property.

## `snapshot.schema.json`

### `MemberProjection`

Required `seat`, `displayName`, and `ready`. The owner entry always exists; the
guest entry is null/absent only while vacant according to the chosen JSON representation. The chosen
source schema uses explicit `guest: null | MemberProjection` so vacancy is unambiguous. Membership
retention/revocation is caller authorization, not shared projection state; terminal leave cannot
change the other member's serialized projection.

### `MembersProjection`

Required `owner: MemberProjection` and `guest: MemberProjection | null`. Seat fields must match their
map key. Names are presentation only.

### `LifecycleDeadlines`

Conditional properties:

- active: `idleExpiresAt`, `absoluteExpiresAt`, `effectiveActiveExpiresAt`;
- owner with unused invitation: optional `invitationExpiresAt`;
- terminal retained member: `absoluteExpiresAt`, `readableUntil`;
- deadlines use strict `Instant`; effective values satisfy the minimum rules in scenario tests.

### `OwnShipProjection`

Required `shipId`, `shipTypeId`, `condition`; conditional `anchor`, `orientation`, and
`occupiedCoordinates` when placed. Occupied coordinates equal ruleset ship length, are unique/in
bounds, and remain caller-authorized.

### Cell unions

- `OwnBoardCell` variants by `state`: `WATER`, `OWN_SHIP`, `MISS`, `HIT`, `SUNK`,
  `AUTO_REVEALED_WATER`. Ship-bearing/hit/sunk variants may require caller-safe `shipId`.
- `OpponentBoardCell` variants: `UNKNOWN`, `MISS`, `HIT`, `SUNK`, `AUTO_REVEALED_WATER`; no pre-terminal
  ship ID, ship type, anchor, orientation, or undisclosed coordinate list.
- `TerminalBoardCell` variants provide the complete permitted post-finish/resignation board with
  seat-keyed ship identity/type and damage.

Every cell variant is a closed discriminated object. `MISS` means an accepted fired coordinate;
`AUTO_REVEALED_WATER` is never counted as a shot.

### Board schemas

- `OwnBoardProjection`: dimensions, complete row-major cell array, and stable fleet entries.
- `OpponentBoardProjection`: dimensions and complete row-major caller-safe cell array.
- `TerminalBoards`: exactly `owner` and `guest` complete boards; normal/resignation only.
- `RedactedBoardProjection`: dimensions and constant `disclosure: REDACTED`; closed and incapable of
  containing cells, ships, placement, coordinates, orientation, or damage.

Array length must equal `rowCount * columnCount`; each coordinate appears exactly once. These
cross-field rules are enforced by validation fixtures in addition to JSON Schema.

### `TurnProjection`

Required `currentSeat` while playing. No client-calculated outcome, countdown authority, or hidden
starting-seat value is permitted.

### `GameSnapshot`

Always required base fields:

- `contractVersion`, `apiMajor`, `serverEpoch`, `observedAt`;
- `gameId`, `gameVersion`, `snapshotRevision`, `gameStateTag`;
- `ruleset`, `phase`, `callerSeat`, `members`;
- unique `allowedGameCommands`, unique `allowedOperations`;
- `deadlines`.

Phase-discriminated board variants:

- `WAITING`, `PLACEMENT`, `PLAYING`, and `FINISHED` require `ownBoard: OwnBoardProjection` and
  `opponentBoard: OpponentBoardProjection`;
- `ABANDONED` requires both fields as `RedactedBoardProjection` and rejects ordinary or terminal
  board projections, making either placement unrepresentable.

Conditional:

- `startingSeat` only from the first `PLAYING` revision onward;
- `turn` only in `PLAYING`;
- `terminalOutcome` for `FINISHED`/`ABANDONED`;
- `terminalBoards` and `terminalStatistics` only for normal victory/resignation;
- no placement/fleet-derived terminal disclosure for `ABANDONED`.

The snapshot response remains open to future optional properties. Privacy/invariant validators reject
forbidden conditional combinations even if JSON Schema composition alone cannot compare fixtures.

## `command.schema.json`

### `GameCommandEnvelope`

Required `commandId`, `expectedVersion`, and `command`; no other properties. `If-Match` is defined by
OpenAPI and must match the body's version/tag fixture matrix.

### Command variants

Each variant is a closed object with required `type: const`:

- `PlaceShipCommand`: `PLACE_SHIP`, `shipId`, `anchor`, `orientation`;
- `MoveShipCommand`: `MOVE_SHIP`, `shipId`, `anchor`;
- `RotateShipCommand`: `ROTATE_SHIP`, `shipId`, explicit target `orientation`;
- `RemoveShipCommand`: `REMOVE_SHIP`, `shipId`;
- `ReadyCommand`: `READY` only;
- `FireCommand`: `FIRE`, `target`;
- `ResignCommand`: `RESIGN` only.

The union uses `oneOf` and discriminator property `type`. `FIRE_SALVO`, actor, seat, result, hit,
winner, turn, score, and authorization fields cannot validate.

### Normalized semantic content

Validation documentation fixes normalized equality as expectedVersion + discriminator + all and only
variant fields after JSON parsing. Object-key order and insignificant JSON whitespace do not differ;
absent versus null differs because command fields are non-nullable. Header/body precondition mismatch
is validation failure, not a second command identity.

## `terminal-statistics.schema.json`

### `DurationAggregate`

Required `sampleCount`, `totalDurationMs`, `averageDurationMs`, `fastestDurationMs`,
`slowestDurationMs`. Last three are `DurationMs | null` with empty/non-empty invariants.

### `Accuracy`

Required `numerator`, `denominator`, `ratio`. Numerator equals hits; denominator equals shots; ratio
null iff denominator zero, otherwise the four-decimal half-up quotient.

### `FleetConditionCounts`

Required `totalShips`, `untouchedShips`, `damagedShips`, `destroyedShips`; non-negative integers;
categories sum to total and ruleset fleet count.

### `SeatTerminalStatistics`

Required `placementDurationMs`, `logicalTurns`, `shots`, `hits`, `accuracy`, `shotDecisions`, and
`fleetCondition`.

### `MatchTerminalStatistics`

Required `totalDurationMs`, `placementPhaseDurationMs`, and `gameplayDurationMs`.

### `TerminalStatistics`

Required `match` and `bySeat` with exactly stable `owner` and `guest` entries. Both caller projections
for one terminal revision have identical facts.

### Terminal outcomes

- `FleetDestroyedOutcome`: `reason: ALL_SHIPS_DESTROYED`, `winnerSeat`, `loserSeat`.
- `ResignationOutcome`: `reason: RESIGNATION`, `winnerSeat`, `loserSeat`.
- `AbandonedOutcome`: `reason: ABANDONED`; winner/loser prohibited.
- `TerminalOutcome`: closed `oneOf` union.

JSON Schema proves shape; Node invariant tests prove sums, ordering, half-up rounding, phase presence,
and two-caller identity.

## `problem.schema.json`

### `ValidationViolation`

Required:

- `field`: bounded JSON Pointer-like stable path that never includes a submitted value;
- `rule`: one of `required`, `unknown-field`, `invalid-type`, `invalid-format`, `out-of-range`,
  `not-normalized`, `contains-control-character`, `unsupported-ruleset`, `invalid-coordinate`,
  `invalid-ship`, `invalid-precondition`;
- optional bounded `parameters` containing numbers/stable identifiers only.

### `RecoveryAdvice`

Required `action`, one of:

- `NONE`, `RETRY_SAME_REQUEST`, `FETCH_SNAPSHOT`, `RETRY_LATER`, `CREATE_NEW_GAME`,
  `ROTATE_INVITATION`, `RESIGN`.

Optional `retryAfterSeconds` is 1..300. Optional `currentGameVersion`/`currentSnapshotRevision` appears
only for authenticated caller-safe recovery cases.

### `ProblemDetail`

Required `type`, `status`, `code`, `correlationId`. Optional `serverEpoch`, `violations`,
`conflictReason`, `recovery` are restricted per problem code. `title`, `detail`, `instance`, messages,
stacks, raw bodies, secrets, boards, and internal fields are not defined.

`type` is the matching `urn:battleship:problem:<code>`. The v1 code catalogue is closed and listed in
[problem-catalog.md](problem-catalog.md).

## `health.schema.json`

### `HealthResponse`

Required `status`, `live`, `ready`, `checkedAt`:

- ready body: `status: READY`, `live: true`, `ready: true`, no reason;
- not-ready body: `status: NOT_READY`, `live: true`, `ready: false`, reason exactly `STARTING` or
  `DRAINING`.

No component details, counts, configuration, provider/runtime names, metrics, secrets, player data,
or framework extension object is permitted. Like other response objects, the health schema tolerates
future optional fields so an additive release remains consumable; fixtures and semantic validation
still reject known sensitive/internal fields, and every added field receives compatibility/privacy
review.

## OpenAPI-only header/parameter schemas

- `GameIdPath`: required `GameId`.
- `IfMatch`: required strong `GameStateTag` for command/leave only.
- `IfNoneMatch`: optional strong `SnapshotTag` for snapshot read.
- `LastEventId`: optional decimal `Revision`, used only as authorized replay hint.
- `CsrfHeader`: required non-empty value for unsafe operations; examples use `<redacted>`.
- `Origin`: exact configured frontend origin behavior described, not hard-coded as one deployment URL.
- `ETag`: required `SnapshotTag` on every success containing a snapshot and on 200/304 snapshot read.
- `RetryAfter`: integer seconds 1..300 and consistent with problem recovery.
- `Set-Cookie`: named examples for session issue, CSRF issue/refresh, and invalid-session clearing.

## Cross-schema proof obligations

JSON Schema alone does not prove all behavior. The manifest/test layer must additionally prove:

- ruleset dimensions/fleet totals and coordinate bounds;
- row-major complete board cardinality and coordinate uniqueness;
- conditional phase/projection fields and hidden-board equivalence;
- revision-transition table and fixed observation time;
- header/body tag/version agreement and duplicate-before-freshness outcomes;
- deadlines and min/max retention formulas;
- terminal arithmetic, seat equality, and half-up ratio;
- problem code/status/type/field restrictions;
- exact operation/status/header/cookie/security matrix;
- raw SSE framing and `snapshotRevision` identity;
- no committed reusable secret or forbidden internal/private shape.
