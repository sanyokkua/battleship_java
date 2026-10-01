package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;
import static ua.kostenko.battleship.app.web.Browser.fire;
import static ua.kostenko.battleship.app.web.Browser.invitationSecret;
import static ua.kostenko.battleship.app.web.Browser.placeShip;
import static ua.kostenko.battleship.app.web.Browser.simple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.realtime.StreamProbe;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.app.web.ProblemProbeController;
import ua.kostenko.battleship.app.web.SnapshotDtoAssembler;
import ua.kostenko.battleship.app.web.SseStream;
import ua.kostenko.battleship.app.web.SseStream.Event;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.projection.BoardView;
import ua.kostenko.battleship.application.projection.PlayerView;
import ua.kostenko.battleship.application.projection.ShotView;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.projection.StatisticsView;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Outcome;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Ship;
import ua.kostenko.battleship.domain.model.ShotResult;
import ua.kostenko.battleship.domain.rules.AllowedActions.Action;

/**
 * Proof area 10: every operation's success and failure body, asserted field by field against {@code
 * contracts/openapi.yaml} read as data ({@link Contract}), so the published wire boundary is proven and not assumed.
 *
 * <p>Two oracles run on every body. The schema walk checks required, optional-per-stated-condition, no-extra-field,
 * type, format and pattern against the contract's own schema. The example comparison checks the body against the
 * embedded example it answers: same keys and types at every level, same array sizes, and exact values for the
 * deterministic fields (phase, ruleset, turn, allowed actions, names, ready flags, outcome, last shot, and the board
 * and ship lists wherever the example's play is reproduced). Every enum value met is recorded, and the last test checks
 * each against the contract's list.
 *
 * <p>Honest limits. The examples are hand-written: their ids, clocks, version numbers (a finished game at version 58
 * is not reachable by the short plays here) and statistics are not reproducible by live play, and their boards are
 * only reproduced where a hand-placed play can reach them. Those fields are judged by schema, never by value. The
 * games are staged through the public operations only, with the injected clock and a seeded random source, so nothing
 * here reads the service's internals. {@code random-arrangement-failed} and {@code internal-error} cannot be reached
 * by the real operations under the default configuration; they are produced by the probe controller's throw sites,
 * which is the real advice and mapper but not a real game path.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "battleship.rate-limit.create-game-per-minute=100000",
            "battleship.rate-limit.join-per-minute=100000",
            "battleship.rate-limit.commands-per-minute=100000",
            "battleship.rate-limit.read-game-per-minute=100000",
            "battleship.rate-limit.presence-per-minute=100000",
            "battleship.rate-limit.stream-open-per-minute=100000",
            "battleship.rate-limit.leave-per-minute=100000",
            "battleship.rate-limit.replace-invitation-per-minute=2"
        })
@Import({
    WireConformanceIT.Determinism.class,
    ProblemProbeController.class,
    ProblemProbeController.PermitAllSecurity.class
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WireConformanceIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.000Z");
    private static final Duration STEP = Duration.ofSeconds(1);
    private static final Contract CONTRACT = Contract.get();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEA = "sea-battle-10-ship.v1";
    private static final String HASBRO = "hasbro-classic-2002.v1";
    private static final String GAMES = "/api/v1/games/";
    private static final String WRONG_SECRET = "A".repeat(43);

    /** Anchors of the contract's example fleets: all ships horizontal, as the examples draw them. */
    private static final int[][] SEA_FLEET = {
        {0, 0}, {2, 0}, {2, 4}, {4, 0}, {4, 3}, {4, 6}, {6, 0}, {6, 2}, {6, 4}, {6, 6}
    };

    private static final int[][] HASBRO_FLEET = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}};

    /** The fields every snapshot example pins: the ones a staged play reproduces exactly. */
    private static final Set<String> PINNED = Set.of(
            "/phase",
            "/rulesetId",
            "/allowedActions",
            "/turn",
            "/you/displayName",
            "/you/ready",
            "/opponent/displayName",
            "/opponent/ready",
            "/outcome");

    @TestConfiguration(proxyBeanMethods = false)
    static class Determinism {
        @Bean
        @Primary
        MutableTimeSource mutableTime() {
            return new MutableTimeSource(START);
        }

        @Bean
        @Primary
        RandomSource seededRandom() {
            return new SeededRandomSource(2026L);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableTimeSource time;

    @Autowired
    private ApplicationContext context;

    /** The one mapper every wire byte goes through, so the assembled document is judged as it is sent. */
    @Autowired
    private ObjectMapper wireMapper;

    private void tick() {
        time.advance(STEP);
    }

    // ------------------------------------------------------------------ the two oracles

    /** Asserts {@code body} is a conforming document of {@code schema}; the schema walk records its enum values. */
    private static void assertConforms(JsonNode body, String schema) {
        assertThat(CONTRACT.problems(body, schema))
                .as("%s body %s", schema, body)
                .isEmpty();
    }

    /** Asserts a snapshot conforms to its schema and matches the named embedded example, plus the extra pins. */
    private static JsonNode assertSnapshot(Reply reply, int status, String example, String... extraPins) {
        assertThat(reply.status()).as(reply.response().body()).isEqualTo(status);
        return assertSnapshot(reply.json(), example, extraPins);
    }

    private static JsonNode assertSnapshot(JsonNode body, String example, String... extraPins) {
        assertConforms(body, "GameSnapshot");
        Set<String> pinned = new TreeSet<>(PINNED);
        pinned.addAll(List.of(extraPins));
        assertThat(Contract.differences(body, CONTRACT.example(example), pinned))
                .as("%s against the %s example", body, example)
                .isEmpty();
        return body;
    }

    /** A failure that has its own embedded example: its status and code are pinned to it (the title is developer text). */
    private static void assertProblem(Reply reply, int status, String code, JsonNode example, String... extraPins) {
        Set<String> pinned = new TreeSet<>(List.of("/status", "/code"));
        pinned.addAll(List.of(extraPins));
        assertProblemBody(reply, status, code, example, pinned);
    }

    /** A failure the contract covers only through the default {@code Problem}: same document, other values. */
    private static void assertDefaultProblem(Reply reply, int status, String code) {
        assertProblemBody(reply, status, code, problemExample("Problem", "internalError"), Set.of());
    }

    private static void assertProblemBody(Reply reply, int status, String code, JsonNode example, Set<String> pinned) {
        assertThat(reply.status()).as(reply.response().body()).isEqualTo(status);
        assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
        JsonNode body = reply.json();
        assertConforms(body, "Problem");
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("title").asText()).isNotBlank();
        assertThat(body.get("correlationId").asText()).isNotBlank();
        assertThat(Contract.differences(body, example, pinned))
                .as("%s against its example", body)
                .isEmpty();
    }

    private static JsonNode problemExample(String response, String name) {
        return CONTRACT.problemExample(response, name);
    }

    // ------------------------------------------------------------------ staging through the public operations

    private record Table(Browser host, Browser guest, JsonNode created, JsonNode joined) {}

    /** A host named Captain and a guest named Rival, as the examples name them, in PLACEMENT with nothing placed. */
    private Table seated(String rulesetId) throws Exception {
        Browser host = new Browser(port);
        Browser guest = new Browser(port);
        JsonNode created = host.create(rulesetId, "Captain");
        tick();
        JsonNode joined = guest.join(host.gameId(), invitationSecret(created), "Rival");
        return new Table(host, guest, created, joined);
    }

    /** Places the first {@code count} ships of the caller's fleet at the example anchors; the last reply is returned. */
    private Reply place(Browser player, int[][] anchors, int count) throws Exception {
        JsonNode ships = player.snapshot().at("/yourBoard/ships");
        Reply last = null;
        for (int i = 0; i < count; i++) {
            tick();
            last = player.send(
                    placeShip(ships.get(i).get("shipId").asText(), anchors[i][0], anchors[i][1], "HORIZONTAL"));
            assertThat(last.status()).as(last.response().body()).isEqualTo(200);
        }
        return last;
    }

    /** Both fleets laid out as the examples draw them, the host ready first (so the host fires first). */
    private Table playing(String rulesetId) throws Exception {
        Table table = seated(rulesetId);
        int[][] anchors = rulesetId.equals(SEA) ? SEA_FLEET : HASBRO_FLEET;
        for (Browser player : List.of(table.host(), table.guest())) {
            place(player, anchors, anchors.length);
            tick();
            player.accept(simple("READY"));
        }
        return table;
    }

    private Reply fireAt(Browser player, int row, int column) throws Exception {
        tick();
        Reply reply = player.send(fire(row, column));
        assertThat(reply.status()).as(reply.response().body()).isEqualTo(200);
        return reply;
    }

    private Reply post(Browser player, String operation) throws Exception {
        tick();
        return player.http()
                .postWithToken(
                        GAMES + player.gameId() + "/" + operation,
                        player.http().freshToken(),
                        SESSION_COOKIE + "=" + player.sessionValue());
    }

    private static String joinBody(String secret) {
        return "{\"invitationSecret\":\"" + secret + "\",\"displayName\":\"Rival\"}";
    }

    // ------------------------------------------------------------------ (1) success bodies

    @Test
    void getMetaMatchesTheMetaExample() throws Exception {
        Reply reply = new SecurityHttp(port).call("GET", "/api/v1/meta", null);

        assertThat(reply.status()).isEqualTo(200);
        assertConforms(reply.json(), "Meta");
        assertThat(Contract.differences(reply.json(), CONTRACT.example("Meta"), Set.of("/apiVersion", "/limits")))
                .isEmpty();
        assertThat(Instant.parse(reply.json().get("serverTime").asText())).isEqualTo(time.now());
        assertThat(reply.setCookies(SecurityHttp.XSRF_COOKIE).getFirst())
                .contains("Path=/", "Secure", "SameSite=Strict")
                .doesNotContain("HttpOnly");
    }

    @Test
    void listRulesetsMatchesTheRulesetsExampleExactly() throws Exception {
        Reply reply = new SecurityHttp(port).call("GET", "/api/v1/rulesets", null);

        assertThat(reply.status()).isEqualTo(200);
        assertConforms(reply.json(), "RulesetList");
        assertThat(reply.json()).isEqualTo(CONTRACT.example("Rulesets"));
    }

    @Test
    void getHealthIsLiveAndReadyAndTheDrainingFormIsAHealthBodyNotAProblem() throws Exception {
        SecurityHttp http = new SecurityHttp(port);
        JsonNode ready = CONTRACT.pathExample(
                "~1api~1v1~1health/get/responses/200/content/application~1json/examples/ready/value");
        JsonNode starting = CONTRACT.pathExample(
                "~1api~1v1~1health/get/responses/503/content/application~1json/examples/starting/value");

        Reply ok = http.call("GET", "/api/v1/health", null);
        assertThat(ok.status()).isEqualTo(200);
        assertConforms(ok.json(), "Health");
        assertThat(ok.json()).isEqualTo(ready);

        AvailabilityChangeEvent.publish(context, this, ReadinessState.REFUSING_TRAFFIC);
        try {
            Reply draining = http.call("GET", "/api/v1/health", null);
            assertThat(draining.status()).isEqualTo(503);
            assertThat(draining.header("Content-Type")).doesNotContain("problem");
            assertConforms(draining.json(), "Health");
            assertThat(Contract.differences(draining.json(), starting, Set.of("/live", "/ready")))
                    .isEmpty();
            assertThat(draining.json().get("reason").asText()).isEqualTo("DRAINING");
        } finally {
            AvailabilityChangeEvent.publish(context, this, ReadinessState.ACCEPTING_TRAFFIC);
        }
        assertThat(http.call("GET", "/api/v1/health", null).status()).isEqualTo(200);
    }

    @Test
    void createGameAnswersTheWaitingExampleWithLocationAndTheSessionCookie() throws Exception {
        Reply reply = new SecurityHttp(port)
                .postJson("/api/v1/games", null, "{\"rulesetId\":\"" + SEA + "\",\"displayName\":\"Captain\"}");

        assertThat(reply.status()).isEqualTo(201);
        JsonNode body =
                assertSnapshot(reply.json(), "SnapshotWaiting", "/version", "/yourBoard", "/opponentBoard", "/you");
        assertThat(reply.header("Location"))
                .isEqualTo(GAMES + body.get("gameId").asText());
        String cookie = reply.setCookies(SESSION_COOKIE).getFirst();
        assertThat(cookie)
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain");
        assertThat(body.get("invitationUrl").asText())
                .matches(".+/join/" + body.get("gameId").asText() + "#invite=[A-Za-z0-9_-]{43}");
        Instant deadline = Instant.parse(body.get("serverTime").asText()).plusSeconds(900);
        assertThat(Instant.parse(body.get("expiresAt").asText())).isEqualTo(deadline);
        assertThat(Instant.parse(body.get("invitationExpiresAt").asText())).isEqualTo(deadline);
    }

    @Test
    void getGameAnswersTheWaitingExample() throws Exception {
        Browser host = new Browser(port);
        host.create(SEA, "Captain");

        assertSnapshot(host.read(), 200, "SnapshotWaiting", "/version", "/yourBoard", "/opponentBoard", "/you");
    }

    @Test
    void joinGameAndTheGuestsGetGameAnswerThePlacementStartExample() throws Exception {
        Table table = seated(SEA);

        assertSnapshot(
                table.joined(),
                "SnapshotPlacementStart",
                "/yourBoard",
                "/opponentBoard",
                "/you/shipsRemaining",
                "/opponent/shipsRemaining");
        assertSnapshot(
                table.guest().read(),
                200,
                "SnapshotPlacementStart",
                "/yourBoard",
                "/opponentBoard",
                "/you/shipsRemaining",
                "/opponent/shipsRemaining");

        tick();
        Reply again = table.guest()
                .http()
                .postJson(
                        GAMES + table.host().gameId() + "/join",
                        SESSION_COOKIE + "=" + table.guest().sessionValue(),
                        joinBody(invitationSecret(table.created())));
        assertSnapshot(again, 200, "SnapshotPlacementStart");
    }

    @Test
    void sendCommandAndGetGameAnswerThePlacementInProgressExample() throws Exception {
        Table table = seated(SEA);
        table.guest().accept(simple("PLACE_FLEET_RANDOMLY"));
        tick();
        table.guest().accept(simple("READY"));

        Reply third = place(table.host(), SEA_FLEET, 3);

        String[] pins = {"/yourBoard", "/opponentBoard", "/you/shipsRemaining", "/opponent/shipsRemaining"};
        assertSnapshot(third, 200, "SnapshotPlacementInProgress", pins);
        assertSnapshot(table.host().read(), 200, "SnapshotPlacementInProgress", pins);
    }

    @Test
    void sendCommandAndGetGameAnswerThePlayingAfterHitExample() throws Exception {
        Table table = playing(SEA);

        Reply hit = fireAt(table.host(), 0, 0);

        String[] pins = {"/lastShot/by", "/lastShot/result", "/you/shipsRemaining", "/opponent/shipsRemaining"};
        assertSnapshot(hit, 200, "SnapshotPlayingAfterHit", pins);
        assertSnapshot(table.host().read(), 200, "SnapshotPlayingAfterHit", pins);
    }

    @Test
    void sendCommandAndGetGameAnswerThePlayingAfterSunkExample() throws Exception {
        Table table = playing(SEA);
        fireAt(table.host(), 0, 0);
        fireAt(table.host(), 0, 1);
        fireAt(table.host(), 0, 2);

        Reply sunk = fireAt(table.host(), 0, 3);

        String[] pins = {"/lastShot/by", "/lastShot/result", "/you/shipsRemaining", "/opponent/shipsRemaining"};
        assertSnapshot(sunk, 200, "SnapshotPlayingAfterSunk", pins);
        assertThat(sunk.json().at("/lastShot/sunkShipId").asText()).isEqualTo("s01");
        assertSnapshot(table.host().read(), 200, "SnapshotPlayingAfterSunk", pins);
    }

    @Test
    void getGameAnswersThePlayingClassicExample() throws Exception {
        Table table = playing(HASBRO);
        fireAt(table.host(), 4, 0);
        fireAt(table.guest(), 4, 0);
        fireAt(table.host(), 4, 1);
        fireAt(table.guest(), 4, 1);

        assertSnapshot(
                table.host().read(),
                200,
                "SnapshotPlayingClassic",
                "/lastShot/by",
                "/lastShot/result",
                "/you/shipsRemaining",
                "/opponent/shipsRemaining");
    }

    @Test
    void sendCommandAndGetGameAnswerTheFinishedByVictoryExample() throws Exception {
        Table table = playing(SEA);
        fireAt(table.host(), 9, 9);
        fireAt(table.guest(), 0, 0);
        fireAt(table.guest(), 9, 9);
        Reply last = null;
        for (int[] cell : shipCells(table.host())) {
            last = fireAt(table.host(), cell[0], cell[1]);
        }

        String[] pins = {"/lastShot/by", "/lastShot/result", "/you/shipsRemaining", "/opponent/shipsRemaining"};
        assertSnapshot(last, 200, "SnapshotFinishedByVictory", pins);
        assertSnapshot(table.host().read(), 200, "SnapshotFinishedByVictory", pins);
    }

    /** Every cell of the caller's own fleet; both players lay out the same fleet, so these are the cells to hit. */
    private static List<int[]> shipCells(Browser player) throws Exception {
        List<int[]> cells = new ArrayList<>();
        for (JsonNode ship : player.snapshot().at("/yourBoard/ships")) {
            ship.get("cells")
                    .forEach(c -> cells.add(new int[] {
                        c.get("rowIndex").asInt(), c.get("columnIndex").asInt()
                    }));
        }
        return cells;
    }

    @Test
    void getGameAnswersTheFinishedByResignationExample() throws Exception {
        Table table = playing(SEA);
        fireAt(table.host(), 9, 9);
        fireAt(table.guest(), 0, 0);
        fireAt(table.guest(), 9, 9);
        for (int column = 0; column < 4; column++) {
            fireAt(table.host(), 0, column);
        }

        tick();
        Reply resigned = table.host().send(simple("RESIGN"));

        assertThat(resigned.status()).isEqualTo(200);
        assertSnapshot(table.host().read(), 200, "SnapshotFinishedByResignation", "/lastShot/by", "/lastShot/result");
    }

    @Test
    void getGameAnswersTheAbandonedExample() throws Exception {
        Table table = seated(SEA);
        table.guest().accept(simple("PLACE_FLEET_RANDOMLY"));
        tick();
        table.guest().accept(simple("READY"));
        place(table.host(), SEA_FLEET, 3);

        assertThat(post(table.guest(), "leave").status()).isEqualTo(204);

        assertSnapshot(table.host().read(), 200, "SnapshotAbandoned", "/yourBoard", "/opponentBoard");
    }

    @Test
    void replaceInvitationAndSendPresenceAnswerTheWaitingExample() throws Exception {
        Browser host = new Browser(port);
        host.create(SEA, "Captain");

        assertSnapshot(post(host, "invitation"), 200, "SnapshotWaiting", "/yourBoard", "/opponentBoard", "/you");
        assertSnapshot(post(host, "presence"), 200, "SnapshotWaiting", "/yourBoard", "/opponentBoard", "/you");
    }

    @Test
    void leaveGameAnswers204WithAnEmptyBody() throws Exception {
        Table table = seated(SEA);

        Reply left = post(table.guest(), "leave");

        assertThat(left.status()).isEqualTo(204);
        assertThat(left.response().body()).isEmpty();
        assertThat(left.header("Content-Type")).isEmpty();
        assertThat(left.header("Cache-Control")).isEqualTo("no-store");
    }

    // ------------------------------------------------------------------ (1) the event stream

    @Test
    void streamGameEventsFollowsTheFramingOfTheEventStreamExample() throws Exception {
        List<List<String>> example = CONTRACT.eventStreamFrames();
        assertThat(example).hasSize(3);
        Table table = seated(SEA);

        try (SseStream stream = table.host().events()) {
            assertThat(stream.status()).isEqualTo(200);
            assertThat(stream.header("Content-Type")).startsWith("text/event-stream");

            Event first = stream.nextFrame();
            assertThat(names(first.lines())).isEqualTo(names(example.get(0)));
            assertThat(first.name()).isEqualTo("snapshot");
            JsonNode snapshot = JSON.readTree(first.data());
            assertConforms(snapshot, "GameSnapshot");
            assertThat(first.id()).isEqualTo(snapshot.get("version").asText());
            assertThat(first.lines().get(2)).startsWith("data: {").doesNotContain("\n");

            time.advance(Duration.ofSeconds(15));
            StreamProbe.heartbeat(context);
            assertThat(stream.nextFrame().lines()).isEqualTo(example.get(1));

            try (SseStream newer = table.host().events()) {
                assertThat(newer.next().name()).isEqualTo("snapshot");
                Event closed = stream.nextFrame();
                assertThat(closed.lines()).isEqualTo(example.get(2));
                assertConforms(JSON.readTree(closed.data()), "StreamClosed");
            }
        }
    }

    @Test
    void aStreamForAGameThatStopsExistingClosesWithGameUnavailable() throws Exception {
        Table table = seated(SEA);

        try (SseStream stream = table.guest().events()) {
            assertThat(stream.next().name()).isEqualTo("snapshot");
            assertThat(post(table.guest(), "leave").status()).isEqualTo(204);

            Event closed = stream.nextFrame();
            assertThat(closed.lines()).containsExactly("event: closed", "data: {\"reason\":\"GAME_UNAVAILABLE\"}");
            assertConforms(JSON.readTree(closed.data()), "StreamClosed");
        }
    }

    private static List<String> names(List<String> lines) {
        return lines.stream()
                .map(l -> l.substring(0, Math.max(l.indexOf(':'), 0)))
                .toList();
    }

    // ------------------------------------------------------------------ (2) failure bodies

    @Test
    void badRequestIsMalformedRequest() throws Exception {
        Reply reply = new SecurityHttp(port).postJson("/api/v1/games", null, "{not json");

        assertProblem(reply, 400, "malformed-request", problemExample("BadRequest", "malformedRequest"));
    }

    @Test
    void unauthorizedIsSessionRequired() throws Exception {
        Reply reply = new SecurityHttp(port).call("GET", GAMES + "Zk3vQ1hYc0a7Tn2LwR9sXg", null);

        assertProblem(reply, 401, "session-required", problemExample("Unauthorized", "sessionRequired"));
    }

    @Test
    void forbiddenIsRequestSecurityRejected() throws Exception {
        Reply reply = new SecurityHttp(port).call("POST", "/api/v1/games", null, "Content-Type", "application/json");

        assertProblem(reply, 403, "request-security-rejected", problemExample("Forbidden", "requestSecurityRejected"));
    }

    @Test
    void notFoundIsGameUnavailable() throws Exception {
        Browser stranger = new Browser(port);
        stranger.create(SEA, "Captain");

        Reply reply = stranger.http()
                .call("GET", GAMES + "AAAAAAAAAAAAAAAAAAAAAA", SESSION_COOKIE + "=" + stranger.sessionValue());

        assertProblem(reply, 404, "game-unavailable", problemExample("NotFound", "gameUnavailable"));
    }

    static Stream<String> conflictCodes() {
        return Stream.of(
                "invitation-unavailable",
                "action-not-allowed",
                "placement-out-of-bounds",
                "placement-overlap",
                "placement-touching",
                "random-arrangement-failed",
                "target-already-fired");
    }

    @ParameterizedTest
    @MethodSource("conflictCodes")
    void conflictCarriesEachOfItsCodesWithItsEmbeddedExample(String code) throws Exception {
        String exampleName = CONTRACT.problemExampleNames("Conflict").stream()
                .filter(name ->
                        problemExample("Conflict", name).get("code").asText().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Conflict component embeds no example for " + code));

        assertProblem(conflict(code), 409, code, problemExample("Conflict", exampleName));
    }

    /** Each of the Conflict component's codes, driven end to end except {@code random-arrangement-failed}. */
    private Reply conflict(String code) throws Exception {
        return switch (code) {
            case "invitation-unavailable" -> {
                Browser host = new Browser(port);
                host.create(SEA, "Captain");
                yield new SecurityHttp(port).postJson(GAMES + host.gameId() + "/join", null, joinBody(WRONG_SECRET));
            }
            case "action-not-allowed" -> seated(SEA).host().send(fire(0, 0));
            case "placement-out-of-bounds" -> {
                Table table = seated(SEA);
                yield table.host().send(placeShip(shipId(table.host(), 0), 0, 8, "HORIZONTAL"));
            }
            case "placement-overlap" -> {
                Table table = seated(SEA);
                place(table.host(), SEA_FLEET, 1);
                yield table.host().send(placeShip(shipId(table.host(), 1), 0, 2, "HORIZONTAL"));
            }
            case "placement-touching" -> {
                Table table = seated(SEA);
                place(table.host(), SEA_FLEET, 1);
                yield table.host().send(placeShip(shipId(table.host(), 1), 1, 0, "HORIZONTAL"));
            }
            case "random-arrangement-failed" ->
                new SecurityHttp(port).call("GET", "/probe/fail/random-arrangement-failed", null);
            case "target-already-fired" -> {
                Table table = playing(SEA);
                fireAt(table.host(), 0, 0);
                yield table.host().send(fire(0, 0));
            }
            default -> throw new IllegalArgumentException(code);
        };
    }

    private static String shipId(Browser player, int index) throws Exception {
        return player.snapshot().at("/yourBoard/ships/" + index + "/shipId").asText();
    }

    @Test
    void goneIsGameExpiredForTheRetainedGuest() throws Exception {
        Table table = seated(SEA);
        time.advance(Duration.ofSeconds(900));

        Reply reply = table.guest()
                .http()
                .postJson(
                        GAMES + table.host().gameId() + "/join",
                        SESSION_COOKIE + "=" + table.guest().sessionValue(),
                        joinBody(invitationSecret(table.created())));

        assertProblem(reply, 410, "game-expired", problemExample("Gone", "gameExpired"));
    }

    @Test
    void unprocessableIsValidationFailedWithItsViolations() throws Exception {
        Reply reply = new SecurityHttp(port)
                .postJson(
                        "/api/v1/games",
                        null,
                        "{\"rulesetId\":\"" + SEA + "\",\"displayName\":\"" + "x".repeat(33) + "\"}");

        assertProblem(
                reply, 422, "validation-failed", problemExample("Unprocessable", "validationFailed"), "/violations");
    }

    @Test
    void tooManyRequestsCarriesRetryAfterEqualToItsBody() throws Exception {
        Browser host = new Browser(port);
        host.create(SEA, "Captain");
        assertThat(post(host, "invitation").status()).isEqualTo(200);
        assertThat(post(host, "invitation").status()).isEqualTo(200);

        Reply limited = post(host, "invitation");

        assertProblem(limited, 429, "rate-limit-exceeded", problemExample("TooManyRequests", "rateLimitExceeded"));
        assertThat(limited.json().get("retryAfterSeconds").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(limited.header("Retry-After"))
                .isEqualTo(limited.json().get("retryAfterSeconds").asText());
    }

    @Test
    void serviceUnavailableCarriesRetryAfterEqualToItsBody() throws Exception {
        Browser host = new Browser(port);
        host.create(SEA, "Captain");

        Reply refused = host.http()
                .postJson(
                        "/api/v1/games",
                        SESSION_COOKIE + "=" + host.sessionValue(),
                        "{\"rulesetId\":\"" + SEA + "\",\"displayName\":\"Captain\"}");

        assertProblem(refused, 503, "service-unavailable", problemExample("ServiceUnavailable", "serviceUnavailable"));
        assertThat(refused.json().get("retryAfterSeconds").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(refused.header("Retry-After"))
                .isEqualTo(refused.json().get("retryAfterSeconds").asText());
    }

    @Test
    void theDefaultProblemCoversPayloadTooLargeUnsupportedMediaTypeAndInternalError() throws Exception {
        SecurityHttp http = new SecurityHttp(port);

        assertDefaultProblem(
                http.postJson("/api/v1/games", null, "{\"x\":\"" + "a".repeat(17_000) + "\"}"),
                413,
                "payload-too-large");
        assertDefaultProblem(
                http.postBody(
                        "/api/v1/games",
                        null,
                        "text/plain",
                        "{\"rulesetId\":\"" + SEA + "\",\"displayName\":\"Captain\"}"),
                415,
                "unsupported-media-type");
        assertProblem(
                http.call("GET", "/probe/boom?leak=secret", null),
                500,
                "internal-error",
                problemExample("Problem", "internalError"));
    }

    // ------------------------------------------------------------------ (3) the oracle can fail

    @Test
    void everyEmbeddedExampleConformsToItsSchema() {
        for (String name : CONTRACT.exampleNames()) {
            if (name.startsWith("Snapshot")) {
                assertThat(CONTRACT.problemsOfExample(CONTRACT.example(name), "GameSnapshot"))
                        .as(name)
                        .isEmpty();
            }
        }
        assertThat(CONTRACT.problemsOfExample(CONTRACT.example("Meta"), "Meta")).isEmpty();
        assertThat(CONTRACT.problemsOfExample(CONTRACT.example("Rulesets"), "RulesetList"))
                .isEmpty();
        for (String response : CONTRACT.problemResponses()) {
            for (String name : CONTRACT.problemExampleNames(response)) {
                assertThat(CONTRACT.problemsOfExample(problemExample(response, name), "Problem"))
                        .as(response + "." + name)
                        .isEmpty();
            }
        }
    }

    @Test
    void theOracleRefusesBodiesThatDriftFromTheContract() {
        JsonNode sunk = CONTRACT.example("SnapshotPlayingAfterSunk");

        ObjectNode missingRequired = sunk.deepCopy();
        ((ObjectNode) missingRequired.get("you")).remove("shipsRemaining");
        assertThat(CONTRACT.problemsOfExample(missingRequired, "GameSnapshot"))
                .anyMatch(p -> p.contains("/you/shipsRemaining") && p.contains("required"));

        ObjectNode extraField = sunk.deepCopy();
        extraField.put("score", 1);
        assertThat(CONTRACT.problemsOfExample(extraField, "GameSnapshot"))
                .anyMatch(p -> p.contains("/score") && p.contains("not defined"));

        ObjectNode optionalOutOfPlace =
                CONTRACT.example("SnapshotFinishedByVictory").deepCopy();
        optionalOutOfPlace.put("turn", "YOU");
        assertThat(CONTRACT.problemsOfExample(optionalOutOfPlace, "GameSnapshot"))
                .anyMatch(p -> p.contains("turn must be absent"));

        ObjectNode optionalMissing = sunk.deepCopy();
        optionalMissing.remove("lastShot");
        ((ObjectNode) optionalMissing.get("opponentBoard").get("ships").get(0)).remove("anchor");
        assertThat(CONTRACT.problemsOfExample(optionalMissing, "GameSnapshot"))
                .anyMatch(p -> p.contains("anchor must be present"));

        ObjectNode unrounded = sunk.deepCopy();
        unrounded.put("serverTime", "2026-09-20T12:03:52Z");
        assertThat(CONTRACT.problemsOfExample(unrounded, "GameSnapshot")).anyMatch(p -> p.contains("/serverTime"));

        ObjectNode renamed = sunk.deepCopy();
        renamed.set("phaze", renamed.remove("phase"));
        assertThat(Contract.differences(renamed, sunk, Set.of("/phase"))).isNotEmpty();
    }

    // ------------------------------------------------------------------ (4) assembler completeness

    private static Coordinate at(int row, int column) {
        return new Coordinate(row, column);
    }

    private static SnapshotView view(boolean everyOptional) {
        Instant now = Instant.parse("2031-05-06T07:08:09.000Z");
        BoardView.ShipView placed = new BoardView.ShipView(
                "s01",
                "ship-2",
                2,
                List.of(at(0, 0), at(0, 1)),
                at(0, 0),
                Orientation.HORIZONTAL,
                Ship.ShipStatus.SUNK);
        BoardView.ShipView unplaced =
                new BoardView.ShipView("s02", "ship-1", 1, List.of(), null, null, Ship.ShipStatus.INTACT);
        BoardView board = new BoardView(
                List.of(List.of(BoardView.CellState.SUNK, BoardView.CellState.SUNK)),
                List.of(everyOptional ? placed : unplaced));
        PlayerView player = new PlayerView("Captain", true, true, 3);
        Set<Action> actions = Set.of(Action.LEAVE);
        if (!everyOptional) {
            return new SnapshotView(
                    "Zk3vQ1hYc0a7Tn2LwR9sXg",
                    1,
                    now,
                    "sea-battle-10-ship.v1",
                    Phase.WAITING,
                    player,
                    null,
                    null,
                    actions,
                    now,
                    null,
                    null,
                    board,
                    board,
                    null,
                    null,
                    null);
        }
        StatisticsView.PlayerStatistics statistics = new StatisticsView.PlayerStatistics(
                10,
                2,
                1,
                0.5,
                new StatisticsView.DurationAggregate(2, 10L, 5L, 4L, 6L),
                new StatisticsView.DurationAggregate(2, 10L, 5L, 4L, 6L),
                new StatisticsView.FleetSummary(2, 1, 0, 1));
        return new SnapshotView(
                "Zk3vQ1hYc0a7Tn2LwR9sXg",
                1,
                now,
                "sea-battle-10-ship.v1",
                Phase.FINISHED,
                player,
                player,
                SnapshotView.Side.YOU,
                actions,
                now,
                "https://battleship.example/join/Zk3vQ1hYc0a7Tn2LwR9sXg#invite=" + "A".repeat(43),
                now,
                board,
                board,
                new ShotView(SnapshotView.Side.YOU, at(0, 1), ShotResult.SUNK, "s01"),
                new SnapshotView.OutcomeView(SnapshotView.Side.YOU, Outcome.Reason.FLEET_DESTROYED),
                new StatisticsView(new StatisticsView.MatchStatistics(30, 10, 20), statistics, statistics));
    }

    @Test
    void aViewWithEveryOptionalFieldAssemblesADocumentWithAllOfThem() throws Exception {
        JsonNode wire = JSON.readTree(wireMapper.writeValueAsString(SnapshotDtoAssembler.assemble(view(true))));

        assertThat(CONTRACT.completeness(wire, "GameSnapshot", true))
                .as("every optional field the view holds must reach the wire")
                .isEmpty();
    }

    @Test
    void aViewWithEveryOptionalFieldAbsentAssemblesADocumentWithNoneOfThem() throws Exception {
        JsonNode wire = JSON.readTree(wireMapper.writeValueAsString(SnapshotDtoAssembler.assemble(view(false))));

        assertThat(CONTRACT.completeness(wire, "GameSnapshot", false))
                .as("an absent optional field must not appear, not even as null")
                .isEmpty();
    }

    // ------------------------------------------------------------------ (6) one representation, R17

    @Test
    void theStreamPayloadAndTheReadAreByteIdenticalAndOnlyTheClockMovesWithoutAVersionChange() throws Exception {
        Table table = seated(SEA);

        try (SseStream stream = table.host().events()) {
            Event connected = stream.next();
            assertThat(table.host().read().response().body()).isEqualTo(connected.data());

            tick();
            Reply placed = table.host().send(placeShip(shipId(table.host(), 0), 0, 0, "HORIZONTAL"));
            Event pushed = stream.next();
            assertThat(pushed.data()).isEqualTo(placed.response().body());
            assertThat(table.host().read().response().body())
                    .isEqualTo(placed.response().body());
            assertThat(pushed.id()).isEqualTo(placed.json().get("version").asText());

            time.advance(Duration.ofSeconds(7));
            Reply later = table.host().read();
            assertThat(later.response().body()).isNotEqualTo(placed.response().body());
            assertThat(later.json().get("version")).isEqualTo(placed.json().get("version"));
            assertThat(later.json().get("expiresAt")).isEqualTo(placed.json().get("expiresAt"));
            assertThat(Contract.without(later.json(), "serverTime"))
                    .isEqualTo(Contract.without(placed.json(), "serverTime"));
        }
    }

    @Test
    void everyTimestampIsUtcWithThreeDigitMillisWhateverTheClockReads() throws Exception {
        Instant base =
                time.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1);
        java.time.format.DateTimeFormatter millis = java.time.format.DateTimeFormatter.ofPattern(
                        "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC);
        for (Instant instant : List.of(base.plusMillis(100), base.plusMillis(123), base.plusSeconds(1))) {
            time.set(instant);
            Reply meta = new SecurityHttp(port).call("GET", "/api/v1/meta", null);
            assertThat(meta.json().get("serverTime").asText()).isEqualTo(millis.format(instant));
        }
    }

    // ------------------------------------------------------------------ (5) enum spellings

    @Test
    @Order(Integer.MAX_VALUE)
    void everyEnumValueTheServiceEmittedIsListedByTheContractAndSpelledExactly() {
        Map<String, List<String>> listed = CONTRACT.enums();
        Map<String, Set<String>> emitted = Contract.emitted();

        assertThat(emitted.keySet())
                .as("the proofs above reach every manifest enum")
                .containsAll(List.of(
                        "Phase",
                        "Side",
                        "Action",
                        "CellState",
                        "ShipStatus",
                        "Shot.result",
                        "Outcome.reason",
                        "Health.reason",
                        "StreamClosed.reason",
                        "Violation.rule",
                        "ProblemCode"));
        emitted.forEach((label, values) ->
                assertThat(listed).as("the contract lists %s", label).containsKey(label));
        emitted.forEach((label, values) -> assertThat(listed.get(label))
                .as("every %s value emitted: %s", label, values)
                .containsAll(values));
        assertThat(emitted.get("ProblemCode"))
                .as("every problem code is emitted by some failure above")
                .containsExactlyInAnyOrderElementsOf(listed.get("ProblemCode"));
    }
}
