# Research: Battleship Frontend and Local Integration

Each entry: **Decision** · **Rationale** · **Alternatives rejected**. No open `NEEDS CLARIFICATION` remains.

## D1 Stack pins

- **Decision:** the exact npm matrix of solitaire (`/Users/ok/Development/GitHub/solitaire/package.json`) plus
  `openapi-typescript` 7.13.0, `openapi-fetch` 0.17.0, `qrcode` 1.5.4 (plan § Stack). Runtimes by major only.
- **Rationale:** that matrix already installs, lints, type-checks, tests, builds a PWA and runs Playwright on this
  machine (Node 24, macOS) — compatibility evidence the seed pins lack. `openapi-typescript` matches `contracts/`.
- **Rejected:** seed pins (React 19.2.8, Vite 8.2.1, ESLint 9, Vitest 4) — older and unproven together here;
  npm "latest" (TypeScript 7, vite-plugin-pwa 2, Vitest 5.0.5) — new majors with unverified plugin peers.

## D2 No TanStack Query

- **Decision:** a small `useSyncExternalStore` store per open game.
- **Rationale:** there is one server object per screen (the snapshot), fed by HTTP and SSE, and R05 needs a custom
  version gate either way. Query's retries, refetch-on-focus and caching would each need disabling to keep R13/R19.
- **Rejected:** TanStack Query 5 (seed) — one dependency for behaviour the client must mostly switch off.

## D3 No hand-written schemas; decode sniff

- **Decision:** generated types only; at the gateway check content type, JSON parse and required top-level keys
  (typed against the generated `keyof`). Anything else is a transport failure (R23).
- **Rationale:** R56 forbids a second copy of the wire schema; wire conformance is proven by backend
  `WireConformanceIT` and by the journeys. The sniff catches proxy error pages, truncation and a wrong endpoint.
- **Rejected:** Zod schemas (seed) — duplicate the contract; Ajv compiled from `openapi.yaml` — a build step and a
  dependency for a check the journeys already make.

## D4 No router library

- **Decision:** History-API router for four routes (`/`, `/create`, `/join/:gameId`, `/games/:gameId`).
- **Rationale:** the only route the contract fixes is `/join/{gameId}` (invitation link); four static patterns do
  not need a library. Fragment stripping must run before any router anyway (R08).
- **Rejected:** react-router (master used v7; v8 is current) — a major-version choice for four routes.

## D5 Hand-written i18n

- **Decision:** adapt solitaire `src/i18n/translate.ts` (flat keys, `{name}` placeholders, `Intl.PluralRules`,
  English fallback); `en.ts`/`uk.ts`; parity unit test (R34).
- **Rejected:** i18next + react-i18next (master) — three dependencies for two locales.

## D6 Type generation

- **Decision:** `openapi-typescript ../contracts/openapi.yaml --enum-values -o src/contract/generated/api.ts`
  (a `.ts` file: the value arrays are runtime code) in a `generate` script run by `predev`/`prebuild`/
  `pretypecheck`/`prelint`/`pretest`; output git-ignored; calls via `openapi-fetch` typed by `paths`.
- **Rationale:** paths, bodies and enum value arrays come from the contract; a contract change breaks typecheck.
  `--enum-values` (verified in the CLI help of 7.13.0) exports value arrays used for the decode sniff and the
  problem map. SSE payloads are typed by `components['schemas']['GameSnapshot' | 'StreamClosed']` because the
  contract types the stream body as a string.
- **Rejected:** committing generated output (Constitution VI); plain `fetch` (loses path/body typing).

## D7 QR

- **Decision:** `qrcode` 1.5.4, lazy-imported, `toCanvas` with an `aria-label` — port of master
  `frontend/src/widgets/sharing/QrCodeSheet.tsx` without its design-system and i18next imports.
- **Rejected:** `qr` 0.6.0 (seed) — no working prior art in the owner's projects.

## D8 Sound and haptics

- **Decision:** port master `frontend/src/audio/audioRecipes.ts` and `AudioFeedback.ts` (lazy `AudioContext`,
  unlock on user gesture, all failures swallowed); outcome keys map from `lastShot.result` (`MISS`/`HIT`/`SUNK`).
  Haptics: `navigator.vibrate` behind a feature check (no prior art).
- **Rationale:** self-contained, already tested against fake contexts; R32 requires no-op on any failure.

## D9 PWA

- **Decision:** copy solitaire `src/pwa/` (`registerPwa.ts`, `pwaGateway.ts`, `deferredGateway.ts`,
  `installGateway.ts`) and its VitePWA block: `registerType: 'prompt'`, static manifest, precache static assets,
  `navigateFallback: 'index.html'`, plus `navigateFallbackDenylist: [/^\/api\//]` and no runtime caching.
- **Rationale:** working prompt-before-update (R44) and a shell-only cache (R42).

## D10 Production-style server = `vite preview`

- **Decision:** `vite preview` with `preview.proxy` for `/api` and `strictPort`.
- **Rationale:** serves `dist/` unchanged, applies the proxy before static files, falls back to `index.html` for
  SPA deep links, adds no compression (SSE streams). Configuration lives in `vite.config.ts`, not in `dist/`, so no
  development address is baked in (R47). Proven by the smoke and waiting journeys.
- **Rejected:** serving the UI from the backend JAR (Constitution: separate artifacts); nginx/Caddy (global tool);
  a hand-written Node server (kept as the fallback only if the journeys disprove streaming or deep links).

## D11 One orchestrator: `scripts/stack.mjs`

- **Decision:** Node standard-library module + CLI: start JAR and frontend server, wait for health, redact and
  prefix logs, stop own children. `dev.sh`/`local-prod.sh` call it; the Playwright fixture imports it.
- **Rationale:** the journeys must start, stop and restart backends with different settings (R50); one
  implementation serves people and tests. Allowed by the integration seed ("Node standard-library helper").
- **Rejected:** Playwright `webServer` alone (cannot restart or run several stacks); duplicated bash + TS control.

## D12 Short lifetimes and limits by configuration

- **Decision:** dedicated stacks with env overrides of existing keys (`BATTLESHIP_IDLETIMEOUTSECONDS`,
  `…PRESENCEINTERVALSECONDS`, `…SWEEPINTERVALSECONDS`, `…RESULTRETENTIONSECONDS`,
  `…INVITATIONLIFETIMESECONDS`, `BATTLESHIP_RATELIMIT_*`), each on its own fixed ports with a matching
  `BATTLESHIP_PUBLICBASEURL`. The default stack raises the create/join limits (keyed by client address; every
  proxied request shares 127.0.0.1).
- **Rejected:** test-only endpoints or clock control (R50); one shared short-idle backend (couples unrelated journeys).

## D13 Lost answer without a mock

- **Decision:** Playwright `page.route` on the command URL: `route.fetch()` sends the request to the real backend,
  then `route.abort()` hides the answer from the page. The retry must carry the same `commandId`, and the board must
  show the action applied once.
- **Rationale:** the server is real and decides everything; only the network loss is simulated (R53 allows it — no
  game data is invented).

## D14 `browser-game-limit`

- **Decision:** new problem code, HTTP 409, no `Retry-After`, on `createGame` and `joinGame`.
- **Rationale:** R07 requires the per-browser cap to be distinguishable from "server busy" and not retried. Today
  `SessionRegistry.java:58-59` reuses `CapacityExceededException` → `503 service-unavailable`, `Retry-After: 1`.
  409 fits "conflicts with this browser's state"; the caller learns only about its own session (no privacy cost).
- **Rejected:** 429 (implies waiting fixes it soon); keeping 503 with a different `detail` (clients must not parse
  text, R22).

## D15 Runtime majors only

- **Decision:** `install.sh` checks `java` major 25 and `node` major 24. Constitution amended to v1.3.2.
- **Rationale:** owner decision 2026-10-18; patch levels move during development (machine: Node 24.21.0).

## D16 Invitation fields leave `version`

- **Decision:** amend `GameSnapshot.version` in `contracts/openapi.yaml` so it excludes `invitationUrl` and
  `invitationExpiresAt` as it already excludes `expiresAt`/`serverTime`. The client merges them at equal version and
  reads `getGame` once at `invitationExpiresAt`.
- **Rationale:** the backend drops the link at its deadline without a version bump or push
  (`GameSlot.java:145-163`), so the current contract text is false and the R05 rule would hide the expiry. The
  amendment makes the contract describe the delivered behaviour; no backend change.
- **Rejected:** a backend sweeper that bumps the version and publishes on lapse — new scheduled state change, an IT
  and a task, for a display that one read already gives.
