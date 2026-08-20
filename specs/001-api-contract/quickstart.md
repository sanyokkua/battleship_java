# Quickstart: Validate the API Contract Product

## Scope

This guide is the runnable acceptance path for feature 001 after the root `contracts/` product is
implemented. It validates the contract without a backend, frontend, database, browser session, or
network service. The implementation task ledger exists, but these product commands cannot yet pass
because the root product has not been implemented.

## Prerequisites

- Checkout `feature/001-api-contract`.
- Node.js exactly 24.19.0 LTS and the npm version shipped/approved with that runtime.
- No globally installed Redocly, Ajv, TypeScript, or generator is used; `npm ci` installs the exact
  lockfile tools locally.
- In the completed product workflow, network access is needed only for a cold `npm ci`. One-time
  lockfile creation during implementation occurs after the product-local controls are installed and
  is not a published validation gate. Validation after installation is local and deterministic.
  `.npmrc` disables audit, funding, and update-notifier output; the local Redocly wrapper disables
  telemetry and update notices; validation forbids `npx`, global tools, remote executable schema
  inputs, and remote `$ref` targets while allowing required identifier URIs and documentation links.

Confirm the runtime:

```sh
node --version
npm --version
```

Expected Node output: `v24.19.0`.

## Fresh complete gate

From the repository root:

```sh
cd contracts
npm ci
npm run check
```

Expected result:

- exact lockfile installation succeeds without modifying `package-lock.json`;
- canonical OpenAPI lint passes with zero unresolved references or configured warnings;
- one derived `dist/openapi.json` bundle is produced and component-renaming conflicts are fatal;
- every manifest-bound example validates against its referenced schema/operation/status;
- operation, security, revision, SSE, lifecycle, privacy, compatibility, and statistics tests pass;
- the committed immutable release baseline exists, matches the generated release artifact, and is
  scanned without being used as self-comparison evidence for 1.0.0;
- temporary OpenAPI TypeScript output compiles with `tsc --noEmit` and is removed;
- no backend/frontend source or reusable secret is created.

This is the feature's build/test/lint/local-run completion command. There is no contract HTTP runtime
and no root repository gate until feature 004 creates one.

## Focused commands

Run one focused stage while authoring:

```sh
cd contracts
npm run lint
npm run build
npm run validate
npm run validate:release
npm test
npm run smoke:typescript
npm run check:candidate
```

Expected ownership:

- `lint`: Redocly specification and reference rules only;
- `build`: canonical graph to ignored derived bundle only;
- `validate`: manifest/schema/example/matrix/SSE/redaction/compatibility validation through
  `validation/validate.mjs`; only the not-yet-created initial baseline may be absent;
- `validate:release`: the same validation with the immutable release baseline required;
- `test`: Node behavior tests for cross-schema invariants and negative fixtures;
- `smoke:typescript`: generate/compile/remove a temporary independent consumer.
- `check:candidate`: run every local candidate check before initial baseline creation.

## Validation scenarios

### 1. Prove the single canonical surface

Run:

```sh
cd contracts
npm run lint
npm run build
```

Expected:

- exactly the 11 accepted method/path pairs and unique operation IDs;
- no `/api/v2`, alias, generic action route, unresolved `$ref`, or duplicate schema definition;
- OpenAPI 3.1.1 and contract 1.0.0 remain distinct from API/game/snapshot version values.

### 2. Prove every example and public status

Run:

```sh
cd contracts
npm run validate
```

Expected:

- manifest coverage includes all success bodies, 304/204 metadata, raw SSE frames, both health
  bodies, and exactly the 19 problem codes/status roles;
- no example file is unlisted and no manifest entry is missing;
- create/join/rotation/snapshot/command/presence/leave examples use the exact header/cookie/cache
  rules from `specs/001-api-contract/contracts/operation-matrix.md`.

### 3. Prove security and projection privacy

Run:

```sh
cd contracts
npm test -- --test-name-pattern='security|redaction|projection|invitation'
```

Expected:

- session capability appears only as a redacted `Set-Cookie` shape;
- metadata, ruleset, and health keep the same status, body schema, cache, and CORS classification
  when an invalid session cookie is presented; they never return `invalid-session` and neither issue,
  refresh, nor clear the session cookie, while metadata may still independently issue or refresh only
  the CSRF cookie and dynamic body/header values need not be byte-for-byte identical;
- invitation examples use documented inert sentinels and no reusable/full realistic fragment token;
- all admitted failed joins have equivalent `409 invitation-unavailable` output;
- paired internal hidden-board variants yield identical caller-facing fixtures;
- pre-terminal opponent boards expose no raw placement; abandoned results accept only structurally
  redacted own/opponent boards and expose no fleet/cell/statistics fields;
- terminal leave/replay returns 204 while the other member's snapshot, ETag, projection, and both
  revisions remain unchanged;
- exact Origin/credentials/CSRF/Fetch Metadata/CORS/no-store matrices pass.

### 4. Prove revisions, unknown outcomes, and SSE recovery

Run:

```sh
cd contracts
npm test -- --test-name-pattern='revision|receipt|recovery|sse|deadline'
```

Expected:

- create starts 0/0; join/rotation/abandonment/commands advance both once;
- eligible presence advances only `snapshotRevision` and publishes a snapshot;
- duplicate lookup precedes freshness, changed content conflicts, stale is 412, missing precondition
  is 428, repeated target changes nothing;
- raw SSE uses `event: snapshot`, decimal `snapshotRevision`, and direct full-snapshot JSON;
- stale/wrong cursor falls back safely; post-commit failure is a close, not a problem event;
- 15-minute idle, two-hour absolute, 15-minute invitation, five-minute terminal, and exact-deadline
  cases pass, including 410 current expiry versus generic 404 restart/loss.

### 5. Prove terminal statistics

Run:

```sh
cd contracts
npm test -- --test-name-pattern='terminal|statistics|accuracy|timing'
```

Expected:

- normal and resigned owner/guest fixtures contain identical seat-keyed facts;
- abandoned/pre-terminal fixtures omit terminal statistics;
- all duration sums, partitions, null/zero aggregates, half-up ratios, hit/shot rules, and fleet totals
  pass;
- each mockup result value maps to one authoritative field without copying inconsistent mock numbers.

### 6. Prove independent consumption

Run:

```sh
cd contracts
npm run smoke:typescript
```

Expected:

- the generated consumer recognizes every operation and closed discriminator;
- a forward-compatible response fixture with an unknown optional field compiles/is tolerated;
- an unknown request field and unknown command discriminator fail their negative contract test;
- generated sources exist only under ignored temporary storage and are removed.

### 7. Prove compatibility without self-comparison

For the first release, before `baselines/1.0.0/openapi.json` exists, run these commands in order:

```sh
cd contracts
npm run check:candidate
npm run baseline:create
npm run check
```

Expected for 1.0.0:

- optional response-field fixture is additive;
- required-field, closed-enum/discriminator, security, status, and meaning-change fixtures are
  breaking;
- `check:candidate` permits only the not-yet-created initial baseline to be absent;
- `baseline:create` reruns the candidate gate, writes atomically, and refuses overwrite;
- final `check` fails if the created baseline is missing or changed;
- no candidate-to-itself comparison is reported as evidence;
- the created 1.0.0 baseline is future comparison input, not evidence against itself.

For a later release, the task/guide must run the explicit baseline comparison against the immutable
prior `baselines/<version>/openapi.json` before replacing or adding a new baseline.

## Manual review after the gate

1. Open `contracts/dist/openapi.json` as text and confirm `info.version`, exact paths, tags, and
   component names are readable and contain no unresolved external reference.
2. Read `README.md`, `contracts/README.md`, `contracts/guides/api-guide.md`, `contracts/guides/sse-protocol.md`,
   `contracts/compatibility-policy.md`, and `contracts/changelog.md`; confirm each links back to the
   canonical schema/operation rather than restating a divergent shape.
3. Run `git status --short` and verify `dist/`, `.tmp/`, `node_modules/`, and generated consumer files
   are not tracked.
4. Run `git diff --check`, then `python3 scripts/sync-agent-files.py --apply` and
   `python3 scripts/sync-agent-files.py --check` because this feature updates durable AGENTS guidance.

## Current verification status

The clarified specification, design artifacts, 75-task implementation ledger, and clean analysis are
complete; planning is ready for implementation. Product commands above remain pending until the root
`contracts/` product exists. No product verification is claimed.
