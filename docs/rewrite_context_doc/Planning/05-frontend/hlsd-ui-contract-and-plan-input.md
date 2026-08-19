# Frontend high-level solution design and plan input

## Architecture

Deliver `frontend/` as a standalone static React application. Pin Node 24.19.0 LTS with its npm, React and React DOM 19.2.8, Vite 8.2.1, TypeScript 6.0.3, Vite React Plugin 6.0.5, OpenAPI TypeScript 7.13.0, OpenAPI Fetch 0.17.0, Zod 4.4.3, TanStack Query 5.101.4, Vitest 4.1.10, React Testing Library 16.3.2, DOM Testing Library 10.4.1, User Event 14.6.3, jsdom 30.0.1, Playwright 1.62.1, `vite-plugin-pwa` 1.3.0, and the dependency-free `qr` 0.6.0 library. Use ESLint 9.39.5 with TypeScript ESLint 8.67.0, React Hooks ESLint 7.1.1, and JSX A11y ESLint 6.10.2 for correctness; use Prettier 3.9.6 as the only formatter. TypeScript 7 and ESLint 10 are deliberately deferred because the selected stable lint plugins do not yet support those peer ranges. Write exact versions without ranges and commit the npm lockfile. Prove the matrix through install, typecheck, lint, format check, build, service-worker, component, and browser checks; do not silently substitute or auto-upgrade a selection.

The browser client consumes only the published contract and runtime API configuration. OpenAPI TypeScript generates transport types and OpenAPI Fetch is used only behind one handwritten `GameGateway`; screens, widgets, and state hooks never import generated transport classes. Focused handwritten Zod schemas validate every response/event at the gateway and are checked against canonical contract fixtures and generated TypeScript types. Runtime validation occurs before data enters the cache. Unknown additive fields are tolerated; missing, invalid, or incompatible required values are rejected as recoverable transport failures.

TanStack Query is the sole authoritative server-state cache. Query keys include `serverEpoch`, public game locator, and authenticated view scope but never a capability value. Query data contains only validated player-safe projections and their epoch/version pair. Local component state is limited to transient drafts and presentation state: form text, open overlay, selected board tab, pending gesture, focus return target, the in-memory invitation URL, and non-secret preferences. There is no `MockGameAdapter`, reducer, store, or local rules engine that can advance a game.

## Exact folder responsibilities

```text
frontend/
  package.json                  npm scripts and exact toolchain constraints
  package-lock.json             exact reproducible dependency graph
  vite.config.ts                static build, dev proxy, PWA shell inputs
  public/                       manifest icons and static shell assets only
  src/
    app/                        bootstrap, router, providers, error boundary, shell
    config/                     validated runtime API configuration and safe public base path
    contract/                   generated OpenAPI client/types and runtime schemas
    gateway/                    one GameGateway, event decoder, command-id handling, error mapping
    queries/                    TanStack Query keys, snapshot queries, mutation and SSE reconciliation
    features/
      home/ create-game/ join-invitation/ waiting-room/ fleet-placement/
      gameplay/ results/ session-unavailable/ overlays/
    components/                 semantic primitives, board, status, dialogs, QR, statistics
    preferences/                non-secret locale, theme, emoji, sound, haptic preferences
    feedback/                   synthesized audio and capability-guarded haptics
    i18n/                       English and Ukrainian resources plus key-parity checks
    styles/                     semantic tokens, themes, motion, forced-colour and layout rules
    test/                       contract fixtures, render helpers, accessibility and visual helpers
  e2e/                          isolated-context browser journeys and viewport checks
  scripts/                      generation, contract drift, and local check helpers
```

Generated contract output is replaced only by its generation script and is not hand-edited. `contract/` exports generated types plus Zod schemas for snapshots, event envelopes, commands, server metadata, rulesets, problems, runtime configuration, expiry, restart loss, and rich result statistics. `gateway/` uses `credentials: "include"` for HTTP, credentialed EventSource construction for SSE, and no browser-readable seat credential. It initializes CSRF through `/api/v1/meta`, reads only the separate `BATTLESHIP-XSRF-TOKEN`, and attaches it as `X-Battleship-CSRF` to unsafe requests. It never serializes fragment secrets outside join/copy/QR, and maps only safe contract problems to user messages.

## Data and interaction flows

### Boot and configuration

The static entry document loads a minimal cached shell, reads a build-independent runtime configuration document with no-store/network-only handling, validates API origin and public base path, then mounts the React app. An invalid configuration stops before authenticated requests and presents Session unavailable with a safe configuration explanation. The service worker caches only the shell, manifest, icons, fonts packaged with the app, and immutable static assets; it does not cache runtime configuration, API responses, SSE payloads, invitation secrets, command bodies, or authenticated snapshots, and it never queues an offline command.

### Invitation flow

`join-invitation` reads `#invite=` once, copies the secret into memory, immediately calls `history.replaceState` with the fragmentless URL, and records neither the secret nor the complete source URL. The explicit Join game mutation sends the secret in the contract-defined body and invalidates it from memory immediately after settlement. For the host, creation or rotation may place the exact server-returned canonical invitation URL in memory. QR generation and clipboard copy receive that value only for their explicit user action; the URL is never reconstructed. If reload or an unknown rotation outcome loses it, Waiting room offers Replace invitation and performs a new explicit rotation after reconciliation. The secret never appears as visible text, query data, logging input, error detail, or persistent storage.

### Server state and realtime flow

Bootstrap metadata establishes the current `serverEpoch` before game state is trusted. `queries/` stores each validated snapshot by its epoch/version pair. The SSE decoder ignores an older or duplicate version only within the same epoch; an epoch change invalidates game queries and enters restart/unavailable recovery instead of comparing the numbers. On transport loss it marks connection state, reconnects according to contract retry hints, and refetches the snapshot before declaring recovered. Visibility restoration performs the same ordered snapshot refresh.

Versioned game commands and `LEAVE` create one `commandId` and derive both `expectedVersion` and a matching `If-Match` from the same last validated snapshot/ETag. The gateway preserves that complete request identity for an identical retry, disables the semantic control while unresolved, and reconciles from the validated result. A 412 causes a snapshot refresh before a new command is offered. Creation and invitation rotation are deliberately different: an ambiguous secret-bearing result is never replayed automatically and requires the explicit recovery action described above. While a non-terminal member screen is active and the document is visible, a small presence coordinator sends at most once every five minutes. It stops for hidden/background tabs and treats `extended: false` as a normal expiry refresh with no cache transition.

### Screen composition

- `home` supplies entry choices and no game authority.
- `create-game` reads `rulesetMetadata` from a validated server response and renders both rulesets rather than duplicated fleet constants.
- `join-invitation` owns fragment stripping and explicit redemption.
- `waiting-room` owns in-memory invitation share state, QR/copy overlay, the reload/unknown-outcome Replace invitation state, expiry visibility, and realtime guest arrival.
- `fleet-placement` renders the server projection, offers candidate actions from advertised fleet metadata and the current allowed-command vocabulary, and invokes atomic server commands for place, rotate, remove, and ready. It may reject malformed input such as an out-of-board coordinate, but it does not decide fit, overlap, contact, completion, or transition legality. Optional pointer/touch drag is only an enhancement over the same cell/action-dialog and keyboard command path.
- `gameplay` renders target and own board projections, turn state, connection state, rich server statistics, and one pending shot per cell.
- `results` renders finished server statistics and permitted board projections, and owns the terminal Leave results action that revokes only the caller's retained read membership.
- `session-unavailable` owns expired, revoked, restart-lost, capacity, and invalid-config recovery messages.
- `overlays` supplies rules, settings, confirmations, QR, placement action, error, and status dialogs.

## Security and privacy

- HttpOnly server cookies remain outside JavaScript. The web app never accepts player ID, role, turn, winner, or capability as client authority.
- The invitation fragment is stripped before routing, copied nowhere persistent, and redeemed only by user activation. Referrer-safe routes, no-store API responses, and a restrictive CSP are contract/deployment requirements that the frontend tests assert from local headers where available.
- Only the readable CSRF token is read by JavaScript. All gateway traffic includes credentials; unsafe calls use the exact CSRF header and never place either token in application logs, query keys, errors, analytics, or persistent state.
- Runtime configuration allows only an explicitly configured API origin. No root-relative production API assumption, wildcard origin, remote QR script, telemetry SDK, or third-party widget is permitted.
- Error views expose stable safe problem titles and actions, never stack traces, raw server messages, cookie data, board internals, or a secret-bearing URL.

## Accessibility and responsive design

Use semantic buttons, forms, labelled inputs, radiogroups, tablists, status regions, and dialogs. The board is keyboard-addressable with coordinate labels and state descriptions. Dialogs trap focus, restore it, support Escape where safe, and remain reachable at browser zoom. Touch and pointer targets meet the shared size token. Colour is supplemented with text/pattern/icon state; forced-colours retains visible boundaries and selection.

CSS tokens implement dark and light themes; emoji mode is decorative and opt-in. `prefers-reduced-motion` removes nonessential motion while retaining state changes. Layout has three deliberate modes: desktop dual-board, narrow portrait single-board tabs, and short landscape compact dual-board. Representative visual assertions test semantic hierarchy, visible state, focus, contrast expectations, overflow, and layout mode; no pixel-diff or exact-coordinate gate is used.

Audio synthesis remains a best-effort side effect of validated authoritative transitions. It initializes only after a user gesture, respects the persisted non-secret preference, does not delay rendering, and suppresses itself when disabled, unsupported, suspended, backgrounded, or failing. Haptics use only a supported browser/device API behind the same no-op-safe preference boundary.

## Test design and expected artifacts

| Scenario | Proving test | Expected artifact |
|---|---|---|
| Contract data enters the UI | Validator rejects malformed required fields and accepts additive fields | Gateway unit tests and versioned JSON fixtures |
| Ruleset facts are visible | Both server metadata records render without frontend fleet constants | Create-game component test |
| Fragment invitation is safe | Fragment is removed before explicit redemption and never reaches persisted state | Join integration test with request and history assertions |
| Commands are race-safe | One cached snapshot supplies matching body/header preconditions; pending controls are single-flight; 412 reconciles; duplicate result/version events do not regress the cache | Query/mutation tests with ordered fixtures |
| Realtime recovers | SSE reconnect plus newer snapshot updates once after background or stale stream | Gateway and Playwright recovery test |
| Inclusive layouts work | Keyboard, screen reader labels, zoom, touch, forced-colours, reduced motion, portrait, and landscape remain usable | Testing Library accessibility tests and Playwright representative visual checks |
| Feedback is optional | Audio/haptic denial cannot block a game command or results | Capability-stub tests |
| Shell remains honest offline | Static shell opens; every game command explains network dependence and is not queued | Service-worker and Playwright offline test |

Expected artifacts are the standalone web workspace, generated contract output, runtime schemas, gateway, query policy, locale files, semantic token system, PWA manifest and cached-shell service worker, contract fixtures, unit/component tests, and frontend-owned Playwright checks. The frontend Playwright checks use validated fixtures to prove surfaces, accessibility, and representative layouts; feature `004-integration` owns live packaged-backend, two-identity, race, restart, and full-journey browser evidence.

## Local operation

The web product remains independently operable during feature 003: `npm --prefix frontend ci`, `npm --prefix frontend run contract:generate`, `npm --prefix frontend run check`, `npm --prefix frontend run dev`, `npm --prefix frontend run build`, and `npm --prefix frontend run preview`. Its test-only validated fixtures may drive components and representative browser surfaces, but no mock game engine may decide transitions. Root orchestration and live cross-product commands belong only to feature `004-integration`; the frontend does not start an embedded backend or bake a development-only API address into its production build.

## Plan order

1. Establish the standalone npm workspace, exact validated runtime configuration, contract generation, schemas, gateway boundary, and cache/error boundary.
2. Implement shell, locale/theme/preferences, semantic primitives, accessibility infrastructure, and cached-shell PWA behaviour.
3. Build Home, Create game, Join invitation, Waiting room, and safe invitation sharing.
4. Build Fleet placement, Gameplay, Results, Session unavailable, rules/settings, and all overlay states from authoritative metadata.
5. Add SSE reconciliation, command pending/idempotency presentation, capability-safe feedback, responsive layouts, and rich statistics.
6. Complete contract, accessibility, responsive, privacy, recovery, visual, and isolated-browser integration evidence.
