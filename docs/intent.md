# Intent

Why this application exists and what it deliberately is not. The binding requirements are in
[`specs/`](../specs/); the visual reference is [`design/naval-command-center-mockup.html`](design/naval-command-center-mockup.html).

## What it is

Anonymous, registration-free, two-player Battleship in the browser. One player creates a game and shares a
private invitation link or QR code. The other joins, and both place fleets and play live. The server alone
decides rules, turns, outcomes and what each player may see. The backend and frontend are separate products
that meet only at a published API contract ([`contracts/openapi.yaml`](../contracts/openapi.yaml)).

## Who it is for

Two friends who want to play right now, on phones or desktops, in English or Ukrainian, with no account and
no install. It runs as a public free-hosted demo or as a small always-on host.

## Vision

- Play within seconds: no sign-up, share a link or QR code, and the game updates near-instantly for both sides.
- Privacy by construction: a leaked game ID gives no seat and no view of hidden boards.
- Honest about being ephemeral: games live in memory, so restarts, expiry, capacity limits and lost
  connections are explained plainly, never papered over. A free host cannot promise continuity; the demo is a demo.
- A replaceable architecture: the contract is the only coupling, so either side can be rewritten without
  touching the other.

## Key principles

1. **Contract before implementation.** Anything that crosses the network is specified, with examples, before
   either side relies on it.
2. **Server authority and player privacy.** Opponent data is projected away on the server, never merely hidden
   in CSS. The client is not a second game engine.
3. **Anonymous capability security.** A high-entropy HttpOnly cookie identifies the browser. A one-time
   invitation secret in the URL fragment is redeemed only by an explicit Join request, so link scanners cannot
   claim a seat.
4. **Explicit rulesets.** Sea Battle (10 ships) and Hasbro Classic (2002) are immutable and honestly named;
   other variants would arrive as new IDs.
5. **Everything attacker-controlled is bounded:** games, streams, bodies, rates, lifetimes.
6. **Near-realtime** through REST commands plus per-seat SSE snapshots, ordered by version, with snapshot
   recovery. The UI never shows a hit, miss, sink, turn or winner until the server says so.
7. **Accessible and responsive:** keyboard, touch and screen-reader parity, no colour-only cues, one board per
   screen on phones and two on wide screens, English/Ukrainian, dark and light.
8. **Simplicity and proportion:** the smallest design that works (KISS, YAGNI, DRY, SOLID), behaviour-focused
   tests, one repository gate.

## Non-goals (v1)

Accounts, profiles or passwords. Database persistence, durable history, crash or restart recovery. Multiple
backend replicas or failover. Spectators, chat, matchmaking, tournaments, moderation. Offline play or queued
commands. Any game engine in the frontend. Analytics, ads and third-party scripts on game pages. Pixel-perfect
matching of the mockup. Playing both seats from one browser profile (use a second profile, a private window or
another device).

## Lessons carried forward

The earlier application used game IDs in URLs as credentials and had unbounded sessions, locks and SSE
emitters. It leaked mutable domain state into DTOs and had no monotonic revision. A client-side mock engine
drifted from production. Rotate was a two-call remove-then-add, and mutation retries ran with no
idempotency. The rewrite exists to remove each of these.
