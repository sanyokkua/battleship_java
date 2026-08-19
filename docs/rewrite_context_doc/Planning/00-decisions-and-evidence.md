---
title: Rewrite decisions and evidence
status: approved launch input
reviewed_on: 2026-08-19
scope: anonymous production-ready local Battleship rewrite
---

# Rewrite decisions and evidence

This record turns the rewrite context into one set of implementation decisions.
It exists so later SpecKit features do not choose different rules, security
models, or ownership boundaries.

## Decision authority

When sources disagree, use this order:

1. Approved developer decisions in the rewrite request and launch-kit plan.
2. The recommended rewrite architecture and interactive mockup.
3. Focused research reports.
4. Current code and tests as behavioral evidence and a source of lessons.
5. Generic standards and tool recommendations.

The current `/api/v2` surface is not a compatibility target. Current code may
suggest useful scenarios, but no current DTO, identifier, package, route, or
storage shape survives without an explicit decision here or in a later accepted
contract.

## Product and delivery decisions

| Area | Adopted decision | Reason |
|---|---|---|
| Repository | One monorepo with `contracts/`, `backend/`, and `frontend/` | Keeps local development simple while preserving replaceable products |
| Delivery order | API contract, backend, frontend, then local integration | Prevents backend and frontend from inventing separate wire behavior |
| Runtime topology | One in-memory backend process and one independent static frontend | Matches the no-database constraint without pretending to support failover |
| Backend artifact | One executable JAR from `backend/app`; no frontend assets inside it | Keeps backend and frontend independently buildable |
| State authority | Backend alone decides legal transitions, outcomes, authorization, and hidden information | Prevents client drift and cheating |
| Realtime | REST commands plus cookie-authenticated SSE full snapshots | Battleship is realtime in observation, not high-frequency input |
| API version | New `/api/v1`; no `/api/v2` compatibility layer | This is a replacement, not a migration of unsafe shapes |
| Storage | Bounded process memory only | A database, durable history, and cross-restart recovery are excluded |
| Testing | Domain-heavy behavior tests and a few critical browser journeys | Proves the product without combinatorial or pixel-level bureaucracy |
| Operations | Production-like local run and one root gate | CI and deployment are deliberately later work |

## Rulesets fixed for the first release

Published ruleset identifiers are immutable. A future rule correction uses a
new identifier rather than changing an in-progress game.

| Stable ID | Product label | Fixed behavior |
|---|---|---|
| `sea-battle-10-ship.v1` | Sea Battle | 10 by 10 board; four one-cell, three two-cell, two three-cell, and one four-cell ship; horizontal or vertical placement; ships cannot touch, including diagonally; one shot per command; a hit or sink keeps the turn; server may reveal the surrounding water after a sink but distinguishes that reveal from the fired shot |
| `hasbro-classic-2002.v1` | Battleship Classic 2002 | 10 by 10 board; Carrier 5, Battleship 4, Destroyer 3, Submarine 3, and Patrol Boat 2; horizontal or vertical placement; contact is allowed but overlap is not; exactly one shot per turn; turn changes after a hit or miss |

The old `UKRAINIAN` and `MILTON_BRADLEY` labels are rejected. The former does
not identify one authoritative national ruleset, and the latter hid a
project-specific ten-ship variant that did not match the authentic classic
rules. Salvo and historical specialist variants are later features, not hidden
options in the first release.

Additional v1 policies are fixed:

- The server chooses the starting seat with a secure unbiased random choice at
  game creation, persists it in immutable state, and reveals it only when both
  valid fleets become ready. Readiness timing cannot bias the choice.
- Placement changes are authoritative commands. `READY` locks a valid fleet
  and cannot be undone in v1.
- Firing an already resolved coordinate is rejected as a domain conflict. It
  does not consume a turn, change state, or increment `gameVersion`.
- A final sink and the transition to finished happen atomically. Once finished,
  both complete fleets may be disclosed for the result view.
- `RESIGN` is available during play and gives the other seat the win with an
  explicit resignation outcome.
- Leaving before play abandons the game for both seats and revokes its
  invitation. Leaving during play is treated as resignation. Leaving a
  finished game only revokes that browser's membership during the remaining
  result-retention window. Pre-play abandonment never reveals either player's
  unfinished or hidden placement.
- The separate leave endpoint uses `commandId`, `expectedVersion`, and
  `If-Match`. Pre-play leave publishes one newer abandoned snapshot; during play
  it directs the client to `RESIGN`; terminal leave revokes only the caller,
  closes that stream, and changes no game version. At most two bounded leave
  receipts per seat preserve both a pre-play abandonment result and a later
  terminal-revocation retry until the game is removed.

## Anonymous security model

- A public `gameId` locates a game and never grants a seat.
- A browser receives a 256-bit random anonymous capability in a host-only
  `Secure; HttpOnly; SameSite=Strict; Path=/` cookie. The server stores only its
  SHA-256 digest and derives the seat from membership.
- One browser profile represents one anonymous identity. Playing both seats in
  normal tabs of one profile is outside v1; use a second profile, private
  window, or device.
- The guest invitation is a separate 128-to-256-bit, one-time secret. It is
  placed in the URL fragment, removed from history immediately, and redeemed
  only by an explicit join action and POST. Link previews and scanners cannot
  claim the seat.
- The server stores only the invitation digest, so an owner invitation URL is
  returned only when it is created or rotated. If the owner reloads after the
  in-memory URL is lost, the waiting room requires an explicit replacement;
  the old unused invitation is revoked and a new one is returned.
- The backend has one validated public frontend base URL, including any
  application subpath. It uses that configured value to create invitation
  URLs; the frontend uses the exact returned URL and never reconstructs it.
  Integration checks that this base and the frontend runtime public base path
  agree. In v1, the frontend and API origins must use the same hostname (ports
  may differ locally), so the readable CSRF cookie works without a parent-domain
  cookie and the Strict capability cookie remains same-site. Credentialed CORS
  still allows only the exact frontend origin.
- Rotating an unused invitation revokes it atomically. Of two concurrent
  redemption attempts, exactly one succeeds. A consumed invitation cannot
  authorize gameplay.
- Spring Security CSRF protection remains enabled for unsafe requests. The SPA
  receives a separate host-only, Secure, SameSite=Strict,
  `BATTLESHIP-XSRF-TOKEN` cookie at `Path=/` without HttpOnly and echoes it only
  in `X-Battleship-CSRF`. The same-hostname topology makes it readable even when
  local ports differ.
- Credentialed CORS uses only exact configured origins. State-changing requests
  also validate origin and Fetch Metadata. Authenticated responses and streams
  use `Cache-Control: no-store`.
- Capabilities, invitations, full share URLs, player names, boards, cookies,
  coordinates, and unbounded identifiers never enter logs or metric labels.

An unknown creation outcome is not described as exactly-once. The client may
offer an explicit new creation after checking current server state; any orphan
expires normally. A lost join response is recoverable only when the guest
cookie was received: retrying as that member returns the snapshot. Without the
cookie, the owner must rotate the now-consumed invitation. This honest policy
avoids a complex recovery service for an ephemeral game.

## Contract and synchronization policy

- The contract package owns OpenAPI 3.1-compatible HTTP shapes, schemas,
  examples, RFC 9457 problems, SSE rules, compatibility policy, and changelog.
- Contract release version, `/api/v1` major, and per-game `gameVersion` are
  different concepts.
- Every versioned game command uses a client-generated `commandId`, an
  `expectedVersion`, and matching `If-Match`. The server checks an identical
  command retry before the version precondition. Same identifier with different
  normalized content is a conflict; a missing precondition is `428`; a stale
  version is `412`. Creation and invitation rotation use the separate honest
  recovery policy below rather than pretending that a lost secret-bearing
  response can be replayed.
- A newly created game starts at `gameVersion: 0`. Each accepted operation that
  changes the shared player-safe game view increments it exactly once and
  publishes new projections. This includes guest join, invitation-status
  rotation, pre-play abandonment, and game commands. Presence, reads, failed
  operations, and revoking one member after any terminal result do not increment it.
- Initial game creation is intentionally not promised as exactly-once. The
  client sends one request and, if its outcome is unknown, does not blindly
  retry; it offers an explicit new creation after reconciliation. Invitation
  rotation follows the same honest recovery rule because the server retains
  only the new secret's digest.
- Every accepted command returns an authoritative player-safe snapshot with an
  ETag. The projection is the only boundary allowed to expose domain state.
- SSE sends an immediate full player-safe snapshot. Event `id` is the decimal
  `gameVersion`; every snapshot also carries a random per-process
  `serverEpoch`.
- One stream is allowed per seat. A new stream replaces the old stream. A
  coalescing queue holds at most the newest unsent snapshot, slow clients are
  disconnected, replay is bounded, and the latest snapshot is always the
  correctness fallback.
- Heartbeats are comments around every 15 seconds and do not extend the game.
  Streams end after 20 minutes so reconnect and cleanup are exercised.
- The browser applies only a newer version from the current epoch. On gaps,
  foregrounding, or stream failure it conditionally fetches a snapshot and may
  fall back to adaptive conditional polling.
- While a command outcome is unknown, conflicting commands are paused. The
  client retries the same `commandId` or reconciles from a newer snapshot; it
  never guesses a hit, turn, or winner.

## Lifecycle and bounded-resource policy

| Control | Initial value | Meaning |
|---|---:|---|
| Active games | 100 | Hard admission cap; overload rejects new games without evicting live ones |
| Active SSE streams | 200 | Separate hard cap, normally one per seat |
| Idle lifetime | 15 minutes | Extended only by an accepted command or an explicit rate-limited presence signal from a visible active tab |
| Absolute lifetime | 2 hours | Cannot be extended |
| Finished result retention | 5 minutes | Allows result display and brief reconnect, then removes the game |
| Invitation lifetime | 15 minutes | Also ends when the game expires, the guest joins, or the owner rotates it |
| Recent command results | 64 per game | Bounded idempotency ring |
| Replay snapshots | 16 per game | Bounded optimization, never required for correctness |
| Streams per seat | 1 | New connection replaces the previous one |
| Request body | 16 KiB maximum | Individual endpoints may set a smaller fixed limit |
| Game creation rate | 5 per minute per bounded source key | Initial anonymous admission limit |
| Invitation attempts | 20 per minute per bounded source key | Covers invalid and competing redemptions without revealing invite state |
| Game commands | 60 per minute per capability | Far above normal play while bounding trivial automation |

Reads, snapshot polling, SSE heartbeats, automatic reconnects, failed commands,
and rejected presence calls do not extend idle life. Expiry is checked on every
access and by one sweeper. Elapsed-duration decisions use a monotonic clock;
wall-clock time is only for display. Cleanup and capacity release are
idempotent.

The numeric caps and rates are initial configuration, not claims about every
machine. They can change only after local retained-memory, slow-client, churn,
abuse, and constrained-resource evidence. Every attacker-controlled map, queue,
buffer, rate-limit key set, request, and stream has a hard bound.

There is exactly one backend replica. A restart, deployment, or process loss
destroys every active game. `/api/v1/meta` exposes `serverEpoch`; clients explain
the restart or expiry when evidence supports it and otherwise say only that the
game is unavailable. Graceful shutdown stops admission and closes streams but
does not promise game preservation.

## Ownership boundaries

| Product | Owns | Must not own |
|---|---|---|
| `contracts/` | Wire schemas, examples, compatibility, error and SSE protocol explanations | Backend framework code, domain transitions, UI components |
| `backend/domain` | Immutable rules, state, commands, transitions, outcomes | Spring, JSON, HTTP, storage, logging |
| `backend/application` | Use cases, genuine ports, safe projections, orchestration | Controllers, cookies, SSE emitters, UI state |
| `backend/app` | Registry, security, rate limits, REST/SSE adapters, configuration, logging, health, executable entry | Frontend assets or a second rules implementation |
| `frontend/` | User interaction, validated gateway, one server-state cache, responsive accessible presentation | Authoritative transitions, hidden fleet inference, Java DTO assumptions |
| Root integration | Local orchestration, shared verification, cross-product journeys and runbook | A fourth product or duplicated business logic |

## User experience scope

The HTML mockup is the complete UX direction, not an engine or API simulator.
The first frontend release covers its nine named surfaces: Home, Create game,
Join invitation, Waiting room, Fleet placement, Gameplay, Results, Session
unavailable, and UI states and overlays.

It also covers light and dark themes, English and Ukrainian, optional emoji,
synthesized sound, supported-device haptics, rules and settings dialogs, copy
and QR sharing, ship move/rotate/remove actions, confirmations, connection and
unknown-outcome states, an installable static PWA shell, and result statistics.
The server supplies rules and authoritative statistics.

For a normal finish or resignation, the result statistics are deliberately
small but match the mockup: match, placement, per-seat placement, and gameplay
durations; per-seat turn intervals, shots, hits, and accuracy; per-seat accepted
shot-decision total, average, fastest, and slowest time; and per-seat untouched,
damaged, and destroyed fleet counts. Match time begins when the guest joins and
placement starts. A shot-decision timer runs from the latest moment a player is
allowed to fire until that accepted fire, including a retained-turn reset. The
server calculates these values from accepted transitions. Pre-play abandonment
shows only its safe outcome and duration and never derives fleet statistics from
hidden placement.

Desktop, narrow portrait, and short landscape are supported. Keyboard, touch,
pointer, zoom, forced colors are product requirements. Drag is an enhancement; every action
has a visible click/touch and keyboard path. Visual review uses representative
states and semantic assertions, never pixel-by-pixel gating.

## Current implementation lessons

Keep the useful ideas: two-player journeys, personalized full snapshots,
foreground recovery, manual refresh, localization, focus-aware dialogs, QR/copy
sharing, and synthesized feedback. Rebuild them behind the new boundaries.

Do not carry forward these observed problems:

- public session and player identifiers acting as bearer credentials;
- mutable aggregate state and leaked internal collections;
- manually maintained unbounded session locks, storage, emitters, and queues;
- snapshots assembled from mixed revisions without ordering or idempotency;
- domain records serialized as public DTOs;
- a frontend mock that implements a second game engine; (We develop backend first for the testing frontend against final backend API);
- global mutation retry behavior and split local-storage authority;
- routing based on a persisted stage rather than a server snapshot;
- non-atomic invitation or session changes and rapid duplicate shots;
- mock-only backdoors and assertions that diverge from production behavior;
- incorrect alive-cell statistics and one flag representing both fired and
  automatically revealed water.

Representative historical evidence reviewed before cleanup:

| Evidence path | What it demonstrated |
|---|---|
| `src/main/java/ua/kostenko/battleship/battleship/logic/api/GameControllerApi.java` and `frontend/src/adapters/GameAdapter.ts` | Public session/player identifiers flow through nearly every operation as caller authority |
| `src/main/java/ua/kostenko/battleship/battleship/logic/persistence/InMemoryPersistence.java` and `src/main/java/ua/kostenko/battleship/battleship/logic/api/impl/GameControllerApiImpl.java` | Unbounded maps and manually coordinated multi-step state/lock ownership |
| `src/main/java/ua/kostenko/battleship/battleship/logic/engine/GameImpl.java` and its mutable `Player`/field collaborators | Domain operations mutate nested state and collections rather than returning one immutable transition |
| `frontend/src/App.tsx` and `frontend/src/adapters/MockGameAdapter.ts` | A selectable browser-side game implementation and test-only window control surface |
| `frontend/src/services/GameBrowserStorage.ts` and routing hooks | Browser storage participates in session/stage authority and recovery |
| `frontend/src/logic/ApplicationTypes.ts` | Handwritten DTO duplication and a single `hasShot` flag that cannot distinguish fired shots from automatic moat revelation |
| `frontend/src/config/appBasePath.ts` | The old share link is client-constructed with a query identifier instead of a server-issued one-time fragment invitation |
| `frontend/e2e-live/` and focused audio/QR tests | Useful real two-player, responsive, sharing, and synthesized-feedback scenarios worth rebuilding behind the new contract |

These are historical observations from the pre-cleanup source. The paths are
plain evidence labels rather than durable links because cleanup removes that
source.

## Standards adopted and rejected

Adopt a small, enforceable baseline: Maven Wrapper, Java 25, Spring Boot 4.1.0,
Node 24 LTS with npm, React, Vite, TypeScript, generated contract code behind a
gateway, runtime response validation, TanStack Query, JUnit, Vitest, Testing
Library, Playwright, strict type checking, one formatter owner, structured
redacted stdout logs, health endpoints, and one root verification script.

Reject or defer recommendations that conflict with this product:

- Gradle, Bun, pnpm, Next.js, Prisma, JPA, Flyway, database Testcontainers, and
  H2 mandates;
- fixed coverage percentages, one-assertion/no-loop test rules, method-size
  quotas, blanket bans on synchronization, mandatory interfaces, and an ADR for
  every dependency;
- exhaustive Javadoc or TSDoc, a large analyzer stack, mandatory hook
  frameworks, SBOM/scanner pipelines, CI, container hardening, and deployment
  automation before those concerns enter scope.

KISS and DRY guide the design. SOLID and GRASP are useful review tools, not a
reason to create interfaces or layers that have no current job.

## Version evidence refreshed on 2026-08-19

| Tool or runtime | Selected stable baseline | Evidence and policy |
|---|---|---|
| SpecKit | 0.16.4 | Official latest release and installed CLI; refresh again immediately before repository initialization |
| Java | 25 LTS | Oracle lists Java 25 as LTS; use the current supported patch from the chosen JDK distribution |
| Maven | 3.9.16 via Maven Wrapper Plugin 3.3.4 | Apache lists 3.9.16 as the current recommended Maven release; Maven 4 remains preview |
| Spring Boot | 4.1.0 | Official stable documentation; compatible with Java 17 through 26 and Maven 3.6.3 or later |
| Node.js | 24.19.0 LTS | Official release table identifies the 24 line as LTS and 24.19.0 as the latest LTS on the review date |
| React and React DOM | 19.2.8 | Stable npm `latest` packages on the review date |
| Vite | 8.2.1 | Stable npm `latest`; Vite documents 8.2 as the regular-patch line |
| TypeScript | 6.0.3 selected; 7.0.2 latest overall | The policy requires a mutually compatible set: 6.0.3 is the newest release in TypeScript ESLint 8.67.0's supported peer range; 7.0.2 is recorded but deliberately deferred |
| TanStack Query | 5.101.4 | Stable npm `latest`; selected as the only authoritative frontend server-state cache |
| Redocly CLI | 2.45.0 | Stable contract linter/bundler with OpenAPI 3.1 support |
| Ajv | 8.20.0 | Stable JSON Schema 2020-12 validator for contract examples |
| OpenAPI TypeScript | 7.13.0 | Stable OpenAPI 3.1 TypeScript type generator |
| OpenAPI Fetch | 0.17.0 | Small typed native-fetch adapter hidden behind the frontend gateway |
| Zod | 4.4.3 | Stable runtime validator selected for data entering the frontend cache |
| Playwright | 1.62.1 | Stable browser test runner for focused isolated-context journeys |
| Vitest | 4.1.10 | Stable component/unit runner; its Node and Vite minimums are below the selected baseline |
| React Testing Library | 16.3.2 | Stable React 19-compatible semantic component testing library |
| DOM Testing Library | 10.4.1 | Direct peer required by React Testing Library |
| User Event | 14.6.3 | Stable semantic interaction helper for component tests |
| jsdom | 30.0.1 | Stable test DOM with explicit Node 24.15 or newer support |
| Vite PWA | 1.3.0 | Selected cached-shell service-worker integration; the Vite 8 build check is mandatory |
| qr | 0.6.0 | Dependency-free local QR encoder; no remote script or third-party request |
| Vite React plugin | 6.0.5 | Stable Vite 8 and React 19 integration |
| ESLint | 9.39.5 | Latest maintained line compatible with JSX A11y 6.10.2; ESLint 10 is deliberately deferred |
| TypeScript ESLint | 8.67.0 | Stable parser/rules release supporting TypeScript through 6.0 |
| React Hooks ESLint | 7.1.1 | Stable React Hooks rules with flat configuration support |
| JSX A11y ESLint | 6.10.2 | Stable semantic JSX rules; its peer range requires the selected ESLint 9 line |
| Prettier | 3.9.6 | Sole frontend formatter; ESLint owns correctness rules rather than formatting |
| Spotless Maven Plugin | 3.9.0 | Sole Java formatter/checker; supports Java 25 through its Java 17+ runtime baseline |
| Maven Enforcer | 3.6.3 | Spring Boot parent-managed runtime/dependency guard |
| Maven Surefire and Failsafe | 3.5.6 | Spring Boot parent-managed stable unit/integration test pair; upstream 3.6 is still a milestone |

Exact dependency versions are written without ranges to lockfiles during their
own feature and must pass that feature's compatibility checks. A newer version
is not adopted merely because it appears during implementation; upgrading after
this review is a separate evidence-backed decision. Runtime patches may receive
security updates without changing the architecture.

Official sources:

- [SpecKit releases](https://github.com/github/spec-kit/releases)
- [Oracle Java support roadmap](https://www.oracle.com/europe/java/technologies/java-se-support-roadmap.html)
- [Apache Maven downloads](https://maven.apache.org/download.cgi)
- [Apache Maven Wrapper Plugin](https://maven.apache.org/tools/wrapper/maven-wrapper-plugin/)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Node.js releases](https://nodejs.org/en/about/previous-releases)
- [React versions](https://react.dev/versions)
- [React npm package](https://www.npmjs.com/package/react)
- [Vite releases](https://vite.dev/releases)
- [Vite npm package](https://www.npmjs.com/package/vite)
- [TypeScript npm package](https://www.npmjs.com/package/typescript)
- [TypeScript ESLint package manifest](https://github.com/typescript-eslint/typescript-eslint/blob/main/packages/typescript-eslint/package.json)
- [ESLint npm package](https://www.npmjs.com/package/eslint)
- [Vite React plugin npm package](https://www.npmjs.com/package/%40vitejs/plugin-react)
- [React Hooks ESLint npm package](https://www.npmjs.com/package/eslint-plugin-react-hooks)
- [JSX A11y ESLint npm package](https://www.npmjs.com/package/eslint-plugin-jsx-a11y)
- [Prettier npm package](https://www.npmjs.com/package/prettier)
- [Spotless releases](https://github.com/diffplug/spotless/releases)
- [Maven Enforcer downloads](https://maven.apache.org/enforcer/download.cgi)
- [Maven Surefire downloads](https://maven.apache.org/surefire/download.cgi)
- [TanStack Query npm package](https://www.npmjs.com/package/%40tanstack/react-query)
- [Redocly CLI npm package](https://www.npmjs.com/package/%40redocly/cli)
- [Ajv npm package](https://www.npmjs.com/package/ajv)
- [OpenAPI TypeScript npm package](https://www.npmjs.com/package/openapi-typescript)
- [OpenAPI Fetch npm package](https://www.npmjs.com/package/openapi-fetch)
- [Zod npm package](https://www.npmjs.com/package/zod)
- [Vitest npm package](https://www.npmjs.com/package/vitest)
- [React Testing Library release](https://github.com/testing-library/react-testing-library/releases/tag/v16.3.2)
- [DOM Testing Library release](https://github.com/testing-library/dom-testing-library/releases/tag/v10.4.1)
- [User Event npm package](https://www.npmjs.com/package/%40testing-library/user-event)
- [jsdom package](https://www.npmjs.com/package/jsdom)
- [Playwright documentation](https://playwright.dev/docs/intro)
- [Vite PWA guide](https://vite-pwa-org.netlify.app/guide/)
- [qr project](https://github.com/paulmillr/qr)

## Evidence map

| Source | How it is used | Status |
|---|---|---|
| [Recommended rewrite architecture](<../Design/Recommended Rewrite Architecture — Production-Ready Anonymous Battleship.md>) | Primary synthesis for boundaries, security, versioning, SSE, lifecycle, and verification | Adopted with the concrete decisions in this record |
| [Interactive HTML mockup](<../Design/Naval Command Center — Interactive HTML Mockup.html>) | Complete UI surface and interaction direction | Adopted for UX; rejected as domain/API authority |
| [Architecture smells report](../Previous_Mistakes/ARCHITECTURE-SMELLS-REPORT.md) | Negative design constraints and regression targets | Adopted as lessons, not as a reason to preserve old structure |
| [Research 01](<../Researches/01. Battleship Rules and Editions — Rules, History, and Domain-Modeling Research.md>) | Ruleset history and policy model | Adopted for the two exact initial modes |
| [Research 02](<../Researches/02. Near-Realtime Web Game Updates. Technology-Neutral Research and Architecture Recommendation.md>) | REST/SSE ordering, idempotency, and recovery | Adopted as snapshot-first v1 |
| [Research 03](<../Researches/03. Independent Contract-First React Frontend for a Server-Authoritative Battleship Game.md>) | Replaceable contract-first React client | Adopted with TanStack Query as the sole server-state cache |
| [Research 04](<../Researches/04. Production Java:Spring Architecture for an Anonymous Ephemeral Battleship Game.md>) | Bounded Java/Spring in-memory architecture | Adopted through the simpler three-module backend |
| [Research 05](<../Researches/05. Responsive Battleship Frontend UX Research.md>) | Responsive and accessible board interaction | Adopted for the UI contract |
| [Research 06](<../Researches/06. Battleship Reference Implementations. Architecture, Rules, Realtime, Testing, Security, and Adaptation.md>) | Reference implementation lessons | Patterns only; no copied architecture authority |
| [Research 07](<../Researches/07. Security Architecture for a Registration-Free Two-Player Browser Game.md>) | Anonymous capability and invitation security | Adopted with same-site cookie SSE topology |
| [Research 08](<../Researches/08. Deterministic Battleship Engine Testing Without a Database.md>) | Deterministic no-database testing | Adopted without mechanical test quotas |
| [Research 09](<../Researches/09. Operating an Ephemeral Realtime Game on Free Hosting.md>) | Ephemeral operation and honest host limits | Adopted for local lifecycle/restart behavior; provider deployment facts deferred |
| [Research 10](<../Researches/10. Production Java:Spring Architecture for an Anonymous Ephemeral Battleship Game.md>) | Byte-identical copy of research 04 | Rejected as duplicate evidence |
| [Generic standards](../Standards_and_recommendations/) | Candidate practices and tool ideas | Only the lean subset listed above is adopted |
| Current source/tests | Behavior examples and concrete failures | Historical reference only; deliberately removed by cleanup |

Research 04 and 10 have the same SHA-256 digest:
`6c7b6b624bce8a17fa68897061572741d7cdce964d566bbff8759cb9c30e9019`.
Research 10 is therefore excluded from source counts and citations.

## Explicit non-goals

The first release has no database, durable games, accounts, profiles, chat,
spectators, matchmaking, bots, offline commands, multi-instance backend,
cross-restart recovery, CI workflow, production deployment manifest, hosting
automation, provider-specific scaling, or migration of existing games. These
are excluded rather than left as implicit future hooks.
