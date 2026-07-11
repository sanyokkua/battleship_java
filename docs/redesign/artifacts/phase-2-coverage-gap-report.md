# Phase 2 — Backend Test Coverage Gap Report

**Generated:** 2026-07-11
**Source:** `mvn clean test jacoco:report` (JaCoCo 0.8.13), 72 tests, all green.
**Report artifacts read:** `target/site/jacoco/index.html`, `target/site/jacoco/jacoco.csv`, per-package
`index.html` pages under `target/site/jacoco/<package>/`.

This report is the deliverable for ticket 2.1 (Task 1 of the Phase 2 backend test-gap
closure plan). It documents the state of backend test coverage **before** Tasks 2-6 add any
new tests, so those tasks have a concrete, numbers-backed punch list instead of guessing.

## 1. Overall coverage (before this phase's work)

| Metric | Missed | Covered | Total | Coverage % |
|---|---|---|---|---|
| Instructions | 192 | 3,160 | 3,352 | **94.27%** |
| Branches | 19 | 148 | 167 | **88.02%** |
| Lines | 25 | 263 | 288 | **91.32%** |
| Methods | 6 | 179 | 185 | 96.76% |
| Classes | 3 | 52 | — | — |

52 classes analyzed. The bundle-wide instruction coverage (94.27%) is already well above
the 80% bar this phase's `check` rule enforces — that rule is not where the risk is. The
risk is entirely concentrated in two packages that currently have ~0 dedicated tests, called
out below.

Note on why overall numbers look deceptively healthy: the engine (`logic.engine`,
`logic.engine.utils`, `logic.engine.config`, `logic.persistence`, `logic.api.impl`) already
has substantial unit test coverage from the pre-redesign test suite (61 of the 72 tests).
The remaining 11 tests are `BattleshipApplicationTests` (context-load smoke test) and
`ApiBaselineOracleCaptureTest` (a Phase-0 regression-oracle test that happens to drive one
full game through the real REST API end-to-end). That oracle test is *not* a REST-layer
test suite — it has no assertions targeting individual controller behaviors, status codes,
or error paths — but it incidentally exercises the happy-path lines of all three REST
controllers, which is why those show high coverage below despite zero purpose-built
controller tests existing yet.

## 2. Per-package breakdown

| Package | Instruction % | Line % | Lines missed | Notes |
|---|---|---|---|---|
| `battleship` (root) | 37.5% (3/8) | 33.3% (1/3) | 2 | `BattleshipApplication.main()` — not meaningfully testable/not a priority |
| `logic.api` (`ValidationUtils`) | 100% | 100% | 0 | fully covered |
| `logic.api.exceptions` | 87.5% (28/32) | 87.5% (14/16) | 2 | `GameInternalProblemException` itself: 0/2 lines (see §4) |
| `logic.api.impl` | 83.6% (368/440) | 91.1% (116/130) | 16 | `GameControllerApiImpl` catch-block/error paths under-tested |
| `logic.engine` | 96.1% (1232/1282) | 99.4% (331/333) | 2 | `GameImpl`/`FieldManagementImpl`/`Game` — near-complete |
| `logic.engine.config` | 92.4% (180/195) | 96.7% (29/30) | 1 | see dead-code note, §5 |
| `logic.engine.models`, `.enums`, `.records` | 100% | 100% | 0 | fully covered |
| `logic.engine.utils` | 98.2% (448/456) | 97.6% (82/84) | 2 | near-complete |
| `logic.persistence` | 100% | 100% | 0 | fully covered |
| `web.api` (`ControllerUtils`) | 100% | 100% | 0 | fully covered |
| `web.api.dtos`, `.entities`, `.gameplay` | 100% | 100% | 0 | fully covered (simple DTOs) |
| `web.api.dtos.preparation` | 61.3% (26/37) | 63.6% (7/11) | 4 | `ResponseShipsNotOnTheBoard` (0/3 lines) is the gap |
| `web.config` (`BeansConfiguration`) | 100% | 100% | 0 | fully covered |
| `web.controllers` (`IndexController`) | 100% | 100% | 0 | fully covered |
| **`web.controllers.rest`** | **100% (215/215)** | **100% (65/65)** | **0** | See §3 — covered only incidentally |
| **`web.exceptions`** | **55.9% (27/48)** | **70.0% (14/20)** | **6** | See §3 — the real gap |

## 3. `web.controllers.rest.*` and `web.exceptions.*` in detail

These are the two packages this phase's `check` rule enforces to 100% line coverage
(via a `CLASS`-scoped rule with `<includes>` on these two packages — see `pom.xml`).

### `web.controllers.rest` — currently 100% line/instruction coverage

| Class | Instructions | Lines |
|---|---|---|
| `GameplayRestController` | 0 missed / 32 covered | 0 missed / 7 covered |
| `GameSessionCommonRestController` | 0 missed / 84 covered | 0 missed / 31 covered |
| `PreparationRestController` | 0 missed / 99 covered | 0 missed / 27 covered |

All three controllers already show 100% line coverage, but **not because purpose-built
controller tests exist** — there are none (`find src/test -iname '*RestController*'` returns
nothing). This is a side effect of `ApiBaselineOracleCaptureTest` driving one full two-player
game through the live REST API. That test has no per-endpoint assertions on status codes,
error responses, or edge cases (bad IDs, wrong game stage, malformed bodies, etc.) — it only
exercises the happy path once. The `check` rule technically already passes for this package
today, but the *coverage that matters* (edge cases, error responses, request validation
failures at the controller boundary) is untested. This is exactly what Tasks 2-3 are meant
to close with real MockMvc/`@WebMvcTest` or slice tests.

### `web.exceptions` — currently 55.9% instruction / 70.0% line coverage (fails the 100% gate)

| Class | Instructions | Lines |
|---|---|---|
| `ValidationExceptionHandler` | 0 missed / 24 covered | 0 missed / 7 covered |
| `InternalExceptionHandler` | 21 missed / 3 covered | 6 missed / 1 covered |

`ValidationExceptionHandler` is incidentally fully covered — its `@ExceptionHandler` maps
eight validation exception types to HTTP 400, and `GameControllerApiImplTest` /
`ValidationUtilsTest` exercise validation-failure paths that route through it during context
wiring in some tests. `InternalExceptionHandler` (mapping `GameInternalProblemException` to
HTTP 500) has essentially no coverage — see §4.

**This confirms the brief's expectation**: no dedicated REST-controller-level or
exception-handler-level tests exist yet. The `check` rule (bound to `mvn verify`, currently
skipped by default via the `jacoco.check.skip` property — see `pom.xml` comment) fails today
specifically on `InternalExceptionHandler`'s 14% (1/7) line coverage when run with
`-Djacoco.check.skip=false`. That is expected and by design; Tasks 2-6 are what will close
this gap and let the gate go green.

## 4. Why `InternalExceptionHandler` / `GameInternalProblemException` are uncovered

`GameInternalProblemException` is thrown from 7 call sites in `GameControllerApiImpl`
(lines 87, 116, 151, 170, 192, 210, 267), each inside a `catch` block wrapping an unexpected
runtime failure from the engine layer. `grep -rn "GameInternalProblemException" src/test`
returns zero hits — no existing test ever triggers one of these catch paths, so
`InternalExceptionHandler.handleConflict(...)` (the `@RestControllerAdvice` method that maps
this exception to an HTTP 500 `ExceptionDto`) is exercised by nothing. This is the single
largest, most concrete gap in the two frozen-API packages and should be a priority target for
whichever of Tasks 2-6 covers exception-handler tests.

## 5. Untestable / dead code found (documented, not fixed)

**`GameEditionConfiguration.getConfiguration(GameEdition)` — `src/main/java/.../logic/engine/config/GameEditionConfiguration.java:53`**

```java
switch (gameEdition) {
    case MILTON_BRADLEY -> { ... }
    case UKRAINIAN -> { ... }
    default -> throw new IllegalArgumentException("GameType %s is not supported yet".formatted(gameEdition));
}
```

`GameEdition` (`logic/engine/config/GameEdition.java`) has exactly two enum constants,
`UKRAINIAN` and `MILTON_BRADLEY`, both handled by explicit `case` arms. The `default` branch
is therefore unreachable through normal Java enum dispatch (it would only trigger via
reflection-based enum manipulation or a future third constant added without updating this
switch). This accounts for `GameEditionConfiguration`'s 12 missed instructions / 1 missed
line in the coverage numbers above. Per the redesign's frozen game-engine-logic rule, this is
**not** being fixed as part of Phase 2 — flagging it here for awareness only. If a future task
wants 100% on this class, the pragmatic option is a JaCoCo class-level exclusion or an
explicit `@Generated`-style annotation, not deleting the defensive branch.

No other unreachable `switch`/`default` branches were found across the other enum-driven
switches in scope for this phase (checked `logic.engine.config`, `web.controllers.rest`,
`web.exceptions`, `logic.api.impl`).

## 6. What Tasks 2-6 will close

(Summarized from the Phase 2 plan — no new scope invented here.)

- **Task 2** — REST-controller-level tests (likely `@WebMvcTest`/MockMvc) for
  `GameSessionCommonRestController`, `GameplayRestController`, `PreparationRestController`:
  status codes, request/response DTO shapes, and — critically, per §3 above — error and edge
  cases the current oracle test doesn't touch (bad session/player/ship IDs, wrong game stage,
  malformed request bodies, etc.).
- **Task 3** — Exception-handler tests for `ValidationExceptionHandler` and, especially,
  `InternalExceptionHandler` (§4's gap): driving each of the 8 validation exception types and
  the `GameInternalProblemException` path to assert correct HTTP status + `ExceptionDto` body.
- **Task 4** — Engine/edge-case top-ups where this report shows gaps: `logic.persistence`
  eviction paths, `logic.engine.config` editions, `logic.engine.utils` coordinate/ship utils
  (currently 97.6-98.2%, a handful of missed lines each per §2).
- **Task 5 / Task 6** — (per the plan's remaining Phase 2 tickets) further top-up and the
  final `mvn clean verify` gate validation — flipping `jacoco.check.skip` to `false` (or
  removing the property) once `web.controllers.rest.*` and `web.exceptions.*` both hit 100%
  line coverage and the bundle stays ≥80% instruction coverage.

## 7. JaCoCo wiring notes for context (see `pom.xml` for the actual config)

- `jacoco-maven-plugin` 0.8.13 (current release at time of writing; verified available on
  Maven Central; supports Java 24+ bytecode, covering this project's Java 25 toolchain).
- `prepare-agent` execution bound to the default lifecycle (runs before `test`).
- `report` execution bound to the `test` phase — `mvn test` and `mvn clean test jacoco:report`
  always produce `target/site/jacoco/index.html`.
- `check` execution bound to `verify`, with two rules:
  - Bundle-wide **80% minimum instruction coverage** (currently passes at 94.27%).
  - **100% minimum line coverage**, `CLASS`-scoped via `<includes>` to
    `ua.kostenko.battleship.battleship.web.controllers.rest.*` and
    `ua.kostenko.battleship.battleship.web.exceptions.*` (currently fails on
    `InternalExceptionHandler`, 14% line coverage).
- Because Maven's default lifecycle always runs `verify` before `install`, and `check`
  currently fails, the `check` execution's `skip` is wired to a new `jacoco.check.skip`
  property, defaulted to `true` in `pom.xml`. This keeps `mvn test` and `mvn clean install`
  green today (frontend bundling included) while still letting `mvn verify
  -Djacoco.check.skip=false` (or a future flip of the default) enforce the real gate once
  Tasks 2-6 close the gaps above. `prepare-agent` and `report` are **not** skipped — every
  build still instruments and generates the HTML/CSV report.
