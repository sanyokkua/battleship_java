# PHASE-0.1 Baseline Notes

Captured on branch `feature/redesign-v2`, against the current (pre-redesign) backend as it exists
on this branch. This is a capture-only phase — no production/game-logic code was modified.

> **Update (Phase 1.1):** this document is a Phase 0 capture-in-time snapshot and is intentionally
> left as-is below — it reflects what was true *before* the backend toolchain upgrade. The Swagger
> UI 404 finding described in "Swagger UI is currently broken" was fixed in Phase 1.1: springdoc
> was swapped to `springdoc-openapi-starter-webmvc-ui` 3.0.3, and `/swagger-ui/index.html` and
> `/v3/api-docs` now both return 200. Node was also bumped to v24.18.0 (LTS) in the same phase. See
> the "Phase 1.1" entry in `docs/redesign/IMPLEMENTATION_PLAN.md`'s Changelog / decisions section
> for full details.

## 1. `mvn clean install`

**Result: BUILD SUCCESS.** Full build including `frontend-maven-plugin` (Node 16.17.0 install +
`npm run build` for the CRA frontend), backend compile, and the pre-existing test suite (71 tests
at that point, before this phase's new test was added), packaged into the Spring Boot fat JAR and
installed to the local `.m2` repo.

Notable build-time warnings (pre-existing, not introduced by this phase):

- `Browserslist: caniuse-lite is outdated. Please run: npx update-browserslist-db@latest` — CRA
  frontend build warning, cosmetic.
- No backend compiler warnings or Maven warnings of note.

Total time: ~13s for `mvn clean install` (frontend build dominates).

## 2. Manual smoke-check

Started the app with `mvn spring-boot:run` in the background, polled `http://localhost:8080/`
until ready (ready after ~3 polling attempts / a few seconds), then:

- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/` → **200**
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/swagger-ui/index.html` → **404**
  (see Swagger finding below)

Server was stopped cleanly after the checks.

### Swagger UI is currently broken (pre-existing, not touched)

`application.properties` sets `springdoc.swagger-ui.path=/swagger-ui.html`, but hitting that path
also returns 404. The app logs:

```
NoResourceFoundException: No static resource swagger-ui.html.
NoResourceFoundException: No static resource swagger-ui/index.html.
```

Root cause (not fixed, per this phase's "capture-only" scope — flagging for whoever owns the
redesign's dependency upgrade): the POM pins
`org.springdoc:springdoc-openapi-ui:1.8.0`, which is the Spring Boot 2.x-era WebMVC artifact. This
app runs **Spring Boot 3.3.5**, which needs `springdoc-openapi-starter-webmvc-ui` (the
`2.x`/Boot-3-compatible artifact family) instead. With the old artifact on the classpath, no
Swagger/OpenAPI auto-configuration registers, so both `/swagger-ui.html`,
`/swagger-ui/index.html`, and `/v3/api-docs` all 404. `CLAUDE.md` documents
`/swagger-ui/index.html` as the expected path, but neither that nor the
`application.properties`-configured path work in the current codebase state. This is a
pre-existing dependency-version mismatch, unrelated to the redesign work in this phase — logging
it here rather than "fixing" it, per the hard invariant against touching `src/main` in this
ticket.

> **Fixed in Phase 1.1.** The springdoc dependency was swapped to
> `springdoc-openapi-starter-webmvc-ui` 3.0.3 as part of the Java 25 / Spring Boot 4.1.0 upgrade;
> `/swagger-ui/index.html` and `/v3/api-docs` now both return 200. See the "Phase 1.1" entry in
> `docs/redesign/IMPLEMENTATION_PLAN.md`'s Changelog / decisions section for details.

## 3. `mvn test` (full suite)

**Result: BUILD SUCCESS. Tests run: 72, Failures: 0, Errors: 0, Skipped: 0.**

That's the 13 pre-existing test classes (71 tests) plus the new
`ApiBaselineOracleCaptureTest` (1 test method, but it drives 78 distinct HTTP calls internally
with real assertions on each). All green.

Pre-existing test classes (unaffected by this phase):

- `ControllerUtilsTest`, `IndexControllerTest`, `InMemoryPersistenceTest`,
  `GameControllerApiImplTest`, `IdGeneratorImplTest`, `ValidationUtilsTest`,
  `GameEditionConfigurationTest`, `CoordinateUtilsTest`, `FieldManagementUtilsTest`,
  `ShipUtilsTest`, `FieldManagementImplTest`, `GameImplTest`, `BattleshipApplicationTests`.

## 4. Quirks / oddities discovered while driving one full game

- **Turn-based shot rule causes a bare 500, not a 400, on a misuse.** `GameImpl#makeShot` throws a
  plain `IllegalStateException("Player is not active to make a shot")` when a player shoots out of
  turn. `ValidationExceptionHandler` only catches the specific typed exceptions under
  `logic.api.exceptions` (all of which extend `IllegalArgumentException`); a bare
  `IllegalStateException` is not one of them, so it falls through to Spring Boot's default error
  handling and surfaces as an unstructured `500 Internal Server Error` rather than the
  `ExceptionDto{status, errorMessage}` shape every other validation failure returns. Discovered
  this the hard way: the capture test's first draft shot MISS, then immediately shot again as the
  same player — which is illegal once the engine hands the turn to the opponent — and got a 500
  instead of the expected 200. Worth flagging for Phase 1 (test-gap phase): this looks like a
  legitimate small inconsistency in the exception taxonomy (an out-of-turn shot is arguably a
  validation/client error, not a server error), but per the redesign ground rules any such fix is
  out of scope unless it's a documented, regression-tested bug fix explicitly done during Phase 2.
  This phase does not touch it — the final test works *with* the rule (queues a harmless
  make-up shot for the other player after every MISS) rather than around it.
- **Turn semantics, for the record:** a `MISS` passes the active turn to the opponent; a `HIT` or
  `DESTROYED` result lets the same player shoot again. This isn't documented anywhere obvious in
  the DTOs/controllers — it's buried in `GameImpl#updateGameState`. Confirmed via the
  `isPlayerActive` / `isOpponentReady` fields on `ResponseGameplayStateDto`.
  `PreparationRestController#getPreparationState` sorts the not-yet-placed ships list ascending by
  `shipSize` — useful to know for anyone else scripting ship placement, since the ship IDs are
  server-generated and otherwise opaque.
- **`ResponseShipRemovedDto.deleted` doubles as "was there anything to delete."** The DELETE ships
  endpoint returns `{"deleted": true}` when a ship existed at that coordinate and `{"deleted":
  false}` if not (confirmed by the pre-existing `delete_ship_should_remove_ship_from_field` test
  and reconfirmed in this capture) — i.e. it's idempotent-safe to call on an empty cell, it just
  reports `false` rather than erroring.
- **Validation error shape is consistent and simple.** Every 400 seen (out-of-bounds shot
  coordinate, in this capture) returns `{"status": 400, "errorMessage": "<message>"}` via
  `ExceptionDto`. The `errorMessage` text directly echoes the underlying Java exception message
  (e.g. `"Coordinate: Coordinate[row=99, column=99] is not VALID!"`), which is a bit
  implementation-leaky (exposes a Java record's `toString()`) but is exactly what's captured for
  future diffing.
- **Non-deterministic fields to normalize in future diffs** — documented in detail in this
  directory's `README.md`: `sessionId`, `playerId`, `shipId` (UUIDs), and the `changesTime` →
  `lastId` value.

## Summary

No blockers. Build, smoke-check, and full test suite all green. One pre-existing, unrelated
issue found and documented (Swagger UI 404 due to a Spring Boot 2-era springdoc dependency) and
one pre-existing engine behavior worth flagging for later phases (out-of-turn shot surfaces as a
bare 500 instead of a structured 400). Neither was touched, per this phase's capture-only scope.

**Update (Phase 1.1):** the Swagger UI 404 finding above has since been fixed as part of the
backend toolchain upgrade (springdoc swapped to `springdoc-openapi-starter-webmvc-ui` 3.0.3;
Node bumped to v24.18.0 LTS). The out-of-turn-shot bare-500 quirk was re-confirmed unchanged and
remains untouched, per the frozen-logic invariant. See the "Phase 1.1" entry in
`docs/redesign/IMPLEMENTATION_PLAN.md`'s Changelog / decisions section for the full details.
