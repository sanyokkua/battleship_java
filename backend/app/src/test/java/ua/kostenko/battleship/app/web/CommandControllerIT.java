package ua.kostenko.battleship.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.issuedSession;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;

/** Commands, presence and leaving over HTTP between two real browsers (R01, R16, R23, R24, R33, R40, R46). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CommandControllerIT.Determinism.class)
class CommandControllerIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String GAMES = "/api/v1/games/";

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
            return new SeededRandomSource(42L);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableTimeSource time;

    private record Player(SecurityHttp http, String cookie, String gameId) {
        String path(String operation) {
            return GAMES + gameId + "/" + operation;
        }
    }

    private record Match(Player host, Player guest) {}

    private Match startMatch() throws Exception {
        SecurityHttp hostHttp = new SecurityHttp(port);
        Reply created = hostHttp.postJson(
                "/api/v1/games", null, "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Captain\"}");
        assertThat(created.status()).isEqualTo(201);
        String gameId = created.json().get("gameId").asText();
        String url = created.json().get("invitationUrl").asText();
        String secret = url.substring(url.indexOf("#invite=") + "#invite=".length());
        SecurityHttp guestHttp = new SecurityHttp(port);
        Reply joined = guestHttp.postJson(
                GAMES + gameId + "/join", null, "{\"invitationSecret\":\"" + secret + "\",\"displayName\":\"Rival\"}");
        assertThat(joined.status()).isEqualTo(200);
        return new Match(
                new Player(hostHttp, issuedSession(created), gameId),
                new Player(guestHttp, issuedSession(joined), gameId));
    }

    private static String simple(String type) {
        return "{\"commandId\":\"" + UUID.randomUUID() + "\",\"command\":{\"type\":\"" + type + "\"}}";
    }

    private static String commandBody(UUID id, String command) {
        return "{\"commandId\":\"" + id + "\",\"command\":" + command + "}";
    }

    private static String placeShip(String shipId, int row, int column, String orientation) {
        return "{\"type\":\"PLACE_SHIP\",\"shipId\":\"" + shipId + "\",\"anchor\":{\"rowIndex\":" + row
                + ",\"columnIndex\":" + column + "},\"orientation\":\"" + orientation + "\"}";
    }

    private static String fire(int row, int column) {
        return "{\"type\":\"FIRE\",\"target\":{\"rowIndex\":" + row + ",\"columnIndex\":" + column + "}}";
    }

    private Reply send(Player player, String body) throws Exception {
        return player.http().postJson(player.path("commands"), player.cookie(), body);
    }

    private Reply command(Player player, String simpleType) throws Exception {
        return send(player, simple(simpleType));
    }

    private JsonNode read(Player player) throws Exception {
        Reply reply = player.http().call("GET", GAMES + player.gameId(), player.cookie());
        assertThat(reply.status()).isEqualTo(200);
        return reply.json();
    }

    private static JsonNode ship(JsonNode snapshot, int index) {
        return snapshot.get("yourBoard").get("ships").get(index);
    }

    private static void assertProblem(Reply reply, int status, String code) {
        assertThat(reply.status()).isEqualTo(status);
        assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
        assertThat(reply.json().get("code").asText()).isEqualTo(code);
    }

    private void bothReady(Match match) throws Exception {
        for (Player player : new Player[] {match.host(), match.guest()}) {
            assertThat(command(player, "PLACE_FLEET_RANDOMLY").status()).isEqualTo(200);
            Reply ready = command(player, "READY");
            assertThat(ready.status()).isEqualTo(200);
        }
    }

    private Player shooter(Match match) throws Exception {
        return read(match.host()).get("turn").asText().equals("YOU") ? match.host() : match.guest();
    }

    @Test
    void everyCommandTypeIsAcceptedWhereOfferedAndAnswersTheCallersCurrentSnapshot() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        String shipId = ship(read(host), 0).get("shipId").asText();

        Reply placed = send(host, commandBody(UUID.randomUUID(), placeShip(shipId, 0, 0, "HORIZONTAL")));
        assertThat(placed.status()).isEqualTo(200);
        assertThat(ship(placed.json(), 0).get("anchor").get("rowIndex").asInt()).isZero();
        assertThat(ship(placed.json(), 0).get("orientation").asText()).isEqualTo("HORIZONTAL");
        assertThat(placed.json()).isEqualTo(read(host));

        Reply removed =
                send(host, commandBody(UUID.randomUUID(), "{\"type\":\"REMOVE_SHIP\",\"shipId\":\"" + shipId + "\"}"));
        assertThat(removed.status()).isEqualTo(200);
        assertThat(ship(removed.json(), 0).hasNonNull("anchor")).isFalse();
        assertThat(removed.json()).isEqualTo(read(host));

        Reply random = command(host, "PLACE_FLEET_RANDOMLY");
        assertThat(random.status()).isEqualTo(200);
        assertThat(random.json().get("yourBoard").get("ships"))
                .allSatisfy(s -> assertThat(s.hasNonNull("anchor")).isTrue());

        Reply cleared = command(host, "CLEAR_FLEET");
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.json().get("yourBoard").get("ships"))
                .allSatisfy(s -> assertThat(s.hasNonNull("anchor")).isFalse());

        assertThat(command(host, "PLACE_FLEET_RANDOMLY").status()).isEqualTo(200);
        Reply ready = command(host, "READY");
        assertThat(ready.status()).isEqualTo(200);
        assertThat(ready.json().get("you").get("ready").asBoolean()).isTrue();
        assertThat(command(match.guest(), "PLACE_FLEET_RANDOMLY").status()).isEqualTo(200);
        assertThat(command(match.guest(), "READY").json().get("phase").asText()).isEqualTo("PLAYING");

        Player firing = shooter(match);
        Reply shot = send(firing, commandBody(UUID.randomUUID(), fire(0, 0)));
        assertThat(shot.status()).isEqualTo(200);
        assertThat(shot.json().get("lastShot").get("by").asText()).isEqualTo("YOU");
        assertThat(shot.json()).isEqualTo(read(firing));

        Player resigning = firing == host ? match.guest() : host;
        Reply resigned = command(resigning, "RESIGN");
        assertThat(resigned.status()).isEqualTo(200);
        assertThat(resigned.json().get("phase").asText()).isEqualTo("FINISHED");
        assertThat(resigned.json().get("outcome").get("reason").asText()).isEqualTo("RESIGNATION");
        assertThat(resigned.json().get("outcome").get("winner").asText()).isEqualTo("OPPONENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLACE_FLEET_RANDOMLY", "CLEAR_FLEET", "READY", "RESIGN"})
    void eachSimpleCommandTypeBindsThroughTheDiscriminatorToItsOwnBehaviour(String type) throws Exception {
        Match match = startMatch();
        Player host = match.host();
        if (type.equals("CLEAR_FLEET") || type.equals("READY") || type.equals("RESIGN")) {
            assertThat(command(host, "PLACE_FLEET_RANDOMLY").status()).isEqualTo(200);
        }
        if (type.equals("RESIGN")) {
            assertThat(command(host, "READY").status()).isEqualTo(200);
            assertThat(command(match.guest(), "PLACE_FLEET_RANDOMLY").status()).isEqualTo(200);
            assertThat(command(match.guest(), "READY").status()).isEqualTo(200);
        }

        JsonNode snapshot = command(host, type).json();

        switch (type) {
            case "PLACE_FLEET_RANDOMLY" ->
                assertThat(ship(snapshot, 0).hasNonNull("anchor")).isTrue();
            case "CLEAR_FLEET" ->
                assertThat(ship(snapshot, 0).hasNonNull("anchor")).isFalse();
            case "READY" ->
                assertThat(snapshot.get("you").get("ready").asBoolean()).isTrue();
            default -> assertThat(snapshot.get("phase").asText()).isEqualTo("FINISHED");
        }
    }

    @Test
    void aCoordinateOffTheBoardAndAnUnknownShipAre422WhileAShipOverTheEdgeIs409() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        String shipId = ship(read(host), 0).get("shipId").asText();
        int versionBefore = read(host).get("version").asInt();

        Reply offBoardRow = send(host, commandBody(UUID.randomUUID(), placeShip(shipId, 10, 0, "HORIZONTAL")));
        assertProblem(offBoardRow, 422, "validation-failed");
        assertThat(offBoardRow.json().at("/violations/0/field").asText()).isEqualTo("/command/anchor/rowIndex");
        assertThat(offBoardRow.json().at("/violations/0/rule").asText()).isEqualTo("OUT_OF_RANGE");

        Reply offBoardColumn = send(host, commandBody(UUID.randomUUID(), placeShip(shipId, 0, 10, "VERTICAL")));
        assertProblem(offBoardColumn, 422, "validation-failed");
        assertThat(offBoardColumn.json().at("/violations/0/field").asText()).isEqualTo("/command/anchor/columnIndex");
        assertThat(offBoardColumn.json().at("/violations/0/rule").asText()).isEqualTo("OUT_OF_RANGE");

        Reply unknownShip = send(host, commandBody(UUID.randomUUID(), placeShip("no-such-ship", 0, 0, "HORIZONTAL")));
        assertProblem(unknownShip, 422, "validation-failed");
        assertThat(unknownShip.json().at("/violations/0/field").asText()).isEqualTo("/command/shipId");
        assertThat(unknownShip.json().at("/violations/0/rule").asText()).isEqualTo("UNKNOWN_VALUE");

        Reply unknownRemove =
                send(host, commandBody(UUID.randomUUID(), "{\"type\":\"REMOVE_SHIP\",\"shipId\":\"nope\"}"));
        assertProblem(unknownRemove, 422, "validation-failed");
        assertThat(unknownRemove.json().at("/violations/0/rule").asText()).isEqualTo("UNKNOWN_VALUE");

        int length = ship(read(host), 0).get("length").asInt();
        assertThat(length).isGreaterThan(1);
        Reply overTheEdge =
                send(host, commandBody(UUID.randomUUID(), placeShip(shipId, 0, 10 - length + 1, "HORIZONTAL")));
        assertProblem(overTheEdge, 409, "placement-out-of-bounds");

        assertThat(read(host).get("version").asInt()).isEqualTo(versionBefore);
    }

    @Test
    void aRepeatedCommandIdReturnsTheCurrentStateAndAppliesNothing() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        UUID id = UUID.randomUUID();
        String body = commandBody(id, "{\"type\":\"PLACE_FLEET_RANDOMLY\"}");
        Reply first = send(host, body);
        assertThat(first.status()).isEqualTo(200);
        JsonNode arranged = read(host);

        Reply repeat = send(host, body);
        Reply repeatWithAnotherCommand = send(host, commandBody(id, "{\"type\":\"CLEAR_FLEET\"}"));

        assertThat(repeat.status()).isEqualTo(200);
        assertThat(repeat.json()).isEqualTo(arranged);
        assertThat(repeatWithAnotherCommand.status()).isEqualTo(200);
        assertThat(repeatWithAnotherCommand.json()).isEqualTo(arranged);
        assertThat(read(host)).isEqualTo(arranged);
    }

    @Test
    void anActionNotInAllowedActionsIs409AndTheVersionDoesNotMove() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        JsonNode before = read(host);
        assertThat(before.get("allowedActions").toString()).doesNotContain("FIRE");

        Reply refused = send(host, commandBody(UUID.randomUUID(), fire(0, 0)));

        assertProblem(refused, 409, "action-not-allowed");
        assertThat(read(host).get("version")).isEqualTo(before.get("version"));
    }

    @Test
    void presenceExtendsTheDeadlineOncePerIntervalWithoutMovingTheVersion() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        JsonNode before = read(host);
        time.advance(Duration.ofSeconds(10));

        Reply accepted =
                host.http().postWithToken(host.path("presence"), host.http().freshToken(), host.cookie());

        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.json().get("version")).isEqualTo(before.get("version"));
        OffsetDateTime extended =
                OffsetDateTime.parse(accepted.json().get("expiresAt").asText());
        assertThat(extended)
                .isEqualTo(
                        OffsetDateTime.parse(before.get("expiresAt").asText()).plusSeconds(10));
        assertThat(accepted.json().get("serverTime").asText())
                .isNotEqualTo(before.get("serverTime").asText());

        time.advance(Duration.ofSeconds(10));
        Reply throttled =
                host.http().postWithToken(host.path("presence"), host.http().freshToken(), host.cookie());

        assertThat(throttled.status()).isEqualTo(200);
        assertThat(throttled.json().get("version")).isEqualTo(before.get("version"));
        assertThat(OffsetDateTime.parse(throttled.json().get("expiresAt").asText()))
                .isEqualTo(extended);
        assertThat(read(host).get("expiresAt")).isEqualTo(accepted.json().get("expiresAt"));
    }

    @Test
    void leaveAnswers204WithNoBodyARepeatIs404AndLeavingDuringPlayIs409() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        Reply left = host.http().postWithToken(host.path("leave"), host.http().freshToken(), host.cookie());
        assertThat(left.status()).isEqualTo(204);
        assertThat(left.response().body()).isEmpty();
        assertThat(left.header("Content-Type")).isEmpty();

        Reply repeated =
                host.http().postWithToken(host.path("leave"), host.http().freshToken(), host.cookie());
        assertProblem(repeated, 404, "game-unavailable");

        Match playing = startMatch();
        bothReady(playing);
        Reply duringPlay = playing.guest()
                .http()
                .postWithToken(
                        playing.guest().path("leave"),
                        playing.guest().http().freshToken(),
                        playing.guest().cookie());
        assertProblem(duringPlay, 409, "action-not-allowed");
    }

    @Test
    void theBodylessOperationsSucceedWithNoBodyAndNeverAnswer415() throws Exception {
        Match match = startMatch();
        Player host = match.host();
        for (String contentType : new String[] {"text/plain", "application/xml", "application/octet-stream"}) {
            Reply presence = host.http().postBody(host.path("presence"), host.cookie(), contentType, "<x/>");
            assertThat(presence.status()).isEqualTo(200);
        }
        assertThat(host.http()
                        .postBody(host.path("presence"), host.cookie(), "application/json", "not json")
                        .status())
                .isEqualTo(200);
        assertThat(host.http()
                        .postWithToken(host.path("presence"), host.http().freshToken(), host.cookie())
                        .status())
                .isEqualTo(200);

        Reply leaveWithBody = host.http().postBody(host.path("leave"), host.cookie(), "text/plain", "ignored");
        assertThat(leaveWithBody.status()).isEqualTo(204);
        assertThat(leaveWithBody.response().body()).isEmpty();

        Player guest = match.guest();
        Reply leaveBodyless =
                guest.http().postWithToken(guest.path("leave"), guest.http().freshToken(), guest.cookie());
        assertThat(leaveBodyless.status()).isEqualTo(204);
        assertThat(leaveBodyless.status()).isNotEqualTo(415);
    }
}
