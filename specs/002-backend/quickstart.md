# Quickstart: Battleship Backend

**Plan**: [plan.md](plan.md) · **Spec**: [spec.md](spec.md)

Run and validation guide. Nothing here exists until this feature is implemented; this file is what
"done" is checked against by hand. The automated evidence is plan.md § *Validation*.

## Prerequisites

Java 25 and Node 24 on `PATH`. The constitution's approved baselines (v1.2.1) are Java 25 LTS at patch
25.0.4, Maven 3.9.16 Wrapper, Spring Boot 4.1.1 and Node 24.19.0 LTS; this machine was verified on
Corretto 25.0.4.1, Maven 3.9.16 (used once to generate the wrapper) and Node 24.21.0. After the wrapper
exists, use only `backend/mvnw`.

`~/.m2` does not exist on this machine, so the **first** `./mvnw verify` is a cold download: the Maven
distribution, the Spring Boot 4.1.1 dependency tree and `openapi-generator-maven-plugin` 7.25.0 all
arrive on that run. Expect minutes and network traffic, and do not read its duration as a build problem.
Subsequent runs are warm.

## Commands

| From | Command | Expect |
|---|---|---|
| `contracts/` | `npm ci && npm run check` | `Your API description is valid` and `openapi.yaml → .tmp/api.d.ts` |
| `backend/` | `./mvnw -q verify` | `BUILD SUCCESS`; Surefire and Failsafe both report `Failures: 0, Errors: 0, Skipped: 0` |
| `backend/` | `./mvnw spotless:check` | no violations (also runs inside `verify`); `spotless:apply` fixes |
| `backend/` | `./mvnw -pl app -am spring-boot:run` | `Started BattleshipApplication` on port 8080 |
| `backend/` | `./mvnw -q -DskipTests package && java -jar app/target/battleship-app-1.0.0-SNAPSHOT.jar` | same, from the packaged artifact (R57, S10) |

`-am` ("also make") is not optional on any `-pl` invocation: `domain` and `application` are
`1.0.0-SNAPSHOT`, the gate is `verify` and never `install`, so nothing is ever placed in `~/.m2` for a
single-module build to resolve. Without `-am`, `./mvnw -pl app …` fails resolving its own siblings.

A skipped, timed-out or capability-blocked stage is never green (Constitution VII).

## Smoke: the service is up and publishes its limits

```bash
curl -s localhost:8080/api/v1/health                  # {"live":true,"ready":true}
curl -s localhost:8080/api/v1/meta | jq .limits       # the six published limits
curl -s localhost:8080/api/v1/rulesets | jq '.rulesets[].id'
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/   # 404 — no UI assets are served
```

R55/S12 check: restart with `BATTLESHIP_IDLETIMEOUTSECONDS=60` and confirm `meta.limits.idleTimeoutSeconds`
is `60` **and** that a game left alone now expires after 60 s. R54 check: start with
`BATTLESHIP_IDLETIMEOUTSECONDS=0` and confirm start-up fails naming that setting.

## Smoke: a whole game over the wire

```bash
# Both contract cookies are Secure; that works over plain http://localhost, which browsers
# and curl 8.7 both treat as a secure context. No local-development exception is needed.
J=$(mktemp)                                            # one cookie jar per "browser"
curl -s -c $J localhost:8080/api/v1/meta > /dev/null    # obtains XSRF-TOKEN
X=$(awk '/XSRF-TOKEN/{print $7}' $J)
curl -s -b $J -c $J -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"rulesetId":"sea-battle-10-ship.v1","displayName":"Captain"}' \
  localhost:8080/api/v1/games | jq '{gameId, phase, invitationUrl, allowedActions}'
```

Take `gameId` and the `#invite=` fragment of `invitationUrl`, repeat the `meta` + cookie-jar step in a
second jar, `POST …/join`, then drive both sides through `POST …/commands` (`PLACE_FLEET_RANDOMLY`,
`READY`, `FIRE`) while `curl -N -b $J localhost:8080/api/v1/games/$GAME/events` streams snapshots.
This is the manual form of the end-to-end path; the automated form lives in `GameJourneyIT`.
