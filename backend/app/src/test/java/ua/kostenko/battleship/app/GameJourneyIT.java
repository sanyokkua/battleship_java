package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;

/**
 * Success criteria S1 and S3 over a real server: two browsers that share nothing play whole games under both rulesets
 * and by resignation, a reload at five points needs one {@code getGame}, and the statistics they read obey the
 * contract's identities. Time is a {@link MutableTimeSource}, so nothing here sleeps (R60).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GameJourneyIT.Determinism.class)
class GameJourneyIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final Duration STEP = Duration.ofSeconds(1);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";
    private static final String HASBRO = "hasbro-classic-2002.v1";

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

    private record Game(Browser host, Browser guest, JsonNode ruleset) {
        Browser other(Browser player) {
            return player == host ? guest : host;
        }
    }

    /** What the journey saw of one accepted shot, from the shooter's own reply. */
    private record Shot(Browser shooter, JsonNode reply, JsonNode before) {}

    private JsonNode ruleset(String id) throws Exception {
        Reply reply = new SecurityHttp(port).call("GET", "/api/v1/rulesets", null);
        assertThat(reply.status()).isEqualTo(200);
        for (JsonNode ruleset : reply.json().get("rulesets")) {
            if (ruleset.get("id").asText().equals(id)) {
                return ruleset;
            }
        }
        throw new AssertionError("ruleset " + id + " is not served");
    }

    private void tick() {
        time.advance(STEP);
    }

    // ------------------------------------------------------------------ journeys

    @Test
    void seaBattleIsPlayedFromCreationToADestroyedFleetAndBothSidesLeave() throws Exception {
        long started = System.nanoTime();
        Game game = open(SEA_BATTLE);
        JsonNode rules = game.ruleset();
        assertThat(rules.get("shipsMayTouch").asBoolean()).isFalse();
        assertThat(rules.get("extraTurnOnHit").asBoolean()).isTrue();
        assertThat(rules.get("revealWaterAroundSunk").asBoolean()).isTrue();

        arrangeHalfAndReload(game);
        arrangeAndReady(game);
        List<Shot> shots = new ArrayList<>(playShots(game, 6));
        assertPlayingReload(game);
        shots.addAll(playToTheEnd(game));

        assertFinished(game, shots, "FLEET_DESTROYED");
        assertThat(shots)
                .anySatisfy(shot ->
                        assertThat(shot.reply().at("/lastShot/result").asText()).isEqualTo("SUNK"));
        assertThat(shots)
                .as("a hit grants another shot, so a hit leaves the shooter on turn")
                .anySatisfy(shot ->
                        assertThat(shot.reply().at("/lastShot/result").asText()).isEqualTo("HIT"));
        leaveFromBoth(game);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void hasbroClassicIsPlayedWithTouchingShipsNoExtraTurnAndNoRevealedWater() throws Exception {
        long started = System.nanoTime();
        Game game = open(HASBRO);
        JsonNode rules = game.ruleset();
        assertThat(rules.get("shipsMayTouch").asBoolean()).isTrue();
        assertThat(rules.get("extraTurnOnHit").asBoolean()).isFalse();
        assertThat(rules.get("revealWaterAroundSunk").asBoolean()).isFalse();

        arrangeHalfAndReload(game);
        arrangeAndReady(game);
        List<Shot> shots = new ArrayList<>(playShots(game, 6));
        assertPlayingReload(game);
        shots.addAll(playToTheEnd(game));

        assertFinished(game, shots, "FLEET_DESTROYED");
        assertThat(shots)
                .anySatisfy(shot ->
                        assertThat(shot.reply().at("/lastShot/result").asText()).isIn("HIT", "SUNK"));
        leaveFromBoth(game);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void aGameEndsByResignationWithTheOutcomeAndStatisticsReadFromBothSides() throws Exception {
        Game game = open(SEA_BATTLE);
        arrangeAndReady(game);
        playShots(game, 4);
        Browser resigner = game.host();
        Browser winner = game.guest();

        tick();
        JsonNode resigned = resigner.accept(simple("RESIGN"));
        assertThat(resigned.get("phase").asText()).isEqualTo("FINISHED");
        JsonNode hostView = resigner.snapshot();
        JsonNode guestView = winner.snapshot();

        assertThat(hostView.at("/outcome/winner").asText()).isEqualTo("OPPONENT");
        assertThat(guestView.at("/outcome/winner").asText()).isEqualTo("YOU");
        assertThat(hostView.at("/outcome/reason").asText()).isEqualTo("RESIGNATION");
        assertThat(guestView.at("/outcome/reason").asText()).isEqualTo("RESIGNATION");
        assertThat(allowed(hostView)).containsExactly("LEAVE");
        assertThat(allowed(guestView)).containsExactly("LEAVE");
        assertThat(hostView.has("turn")).isFalse();
        assertStatistics(hostView, guestView, game.ruleset());
        // The resigner is told the same outcome by a reload that holds only its cookie.
        assertThat(clockFree(resigner.reloaded().snapshot())).isEqualTo(clockFree(hostView));
        leaveFromBoth(game);
    }

    // ------------------------------------------------------------------ setup and placement

    /** S1 steps 1-3 and the first S3 point: the host waits, a guest joins, and a reload shows each in full. */
    private Game open(String rulesetId) throws Exception {
        JsonNode ruleset = ruleset(rulesetId);
        Browser host = new Browser(port);
        Browser guest = new Browser(port);
        JsonNode created = host.create(rulesetId, "Captain");

        assertThat(created.get("phase").asText()).isEqualTo("WAITING");
        assertReload(host, created, "WAITING", ruleset);
        assertThat(allowed(created)).containsExactlyInAnyOrder("NEW_INVITATION", "SEND_PRESENCE", "LEAVE");
        assertThat(created.has("opponent")).isFalse();
        assertThat(created.has("turn")).isFalse();
        assertThat(created.get("invitationUrl").asText()).contains("#invite=");

        tick();
        JsonNode joined = guest.join(host.gameId(), invitationSecret(created), "Rival");
        assertThat(joined.get("phase").asText()).isEqualTo("PLACEMENT");
        assertThat(joined.at("/opponent/displayName").asText()).isEqualTo("Captain");
        JsonNode hostView = host.snapshot();
        assertThat(hostView.has("invitationUrl")).isFalse();

        // PLACEMENT, empty: nothing placed yet, so READY is not offered.
        for (Browser player : new Browser[] {host, guest}) {
            JsonNode view = player == host ? hostView : joined;
            assertReload(player, view, "PLACEMENT", ruleset);
            assertThat(allowed(view))
                    .containsExactlyInAnyOrder(
                            "PLACE_SHIP",
                            "REMOVE_SHIP",
                            "PLACE_FLEET_RANDOMLY",
                            "CLEAR_FLEET",
                            "SEND_PRESENCE",
                            "LEAVE");
            assertThat(placedShips(view.at("/yourBoard"))).isZero();
        }
        return new Game(host, guest, ruleset);
    }

    /**
     * Arranges two ships by hand, so the ruleset's adjacency rule is observed in the answer, then reloads at the
     * half-arranged point.
     */
    private void arrangeHalfAndReload(Game game) throws Exception {
        Browser host = game.host();
        JsonNode fleet = host.snapshot().at("/yourBoard/ships");
        String first = fleet.get(0).get("shipId").asText();
        String second = fleet.get(1).get("shipId").asText();

        tick();
        host.accept(placeShip(first, 0, 0, "HORIZONTAL"));
        tick();
        Reply touching = host.send(placeShip(second, 1, 0, "HORIZONTAL"));
        JsonNode half;
        if (game.ruleset().get("shipsMayTouch").asBoolean()) {
            assertThat(touching.status()).isEqualTo(200);
            half = touching.json();
        } else {
            assertThat(touching.status()).isEqualTo(409);
            assertThat(touching.json().get("code").asText()).isEqualTo("placement-touching");
            assertThat(host.snapshot().at("/yourBoard/ships/1/cells")).isEmpty();
            tick();
            half = host.accept(placeShip(second, 3, 0, "HORIZONTAL"));
        }

        assertThat(placedShips(half.get("yourBoard"))).isEqualTo(2);
        assertThat(allowed(half)).doesNotContain("READY");
        assertThat(half.at("/you/ready").asBoolean()).isFalse();
        assertReload(host, half, "PLACEMENT", game.ruleset());
    }

    private void arrangeAndReady(Game game) throws Exception {
        for (Browser player : new Browser[] {game.host(), game.guest()}) {
            tick();
            JsonNode arranged = player.accept(simple("PLACE_FLEET_RANDOMLY"));
            assertThat(placedShips(arranged.get("yourBoard")))
                    .isEqualTo(arranged.at("/yourBoard/ships").size());
            assertThat(allowed(arranged)).contains("READY");
            tick();
            JsonNode ready = player.accept(simple("READY"));
            assertThat(ready.at("/you/ready").asBoolean()).isTrue();
        }
        JsonNode view = game.host().snapshot();
        assertThat(view.get("phase").asText()).isEqualTo("PLAYING");
        assertThat(view.has("turn")).isTrue();
    }

    // ------------------------------------------------------------------ play

    private List<Shot> playShots(Game game, int count) throws Exception {
        List<Shot> shots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            shots.add(fireOnce(game));
        }
        return shots;
    }

    private List<Shot> playToTheEnd(Game game) throws Exception {
        List<Shot> shots = new ArrayList<>();
        Shot last;
        do {
            last = fireOnce(game);
            shots.add(last);
        } while (!last.reply().get("phase").asText().equals("FINISHED"));
        return shots;
    }

    /** One accepted FIRE by whoever holds the turn, aimed at a cell that caller's own snapshot calls UNKNOWN. */
    private Shot fireOnce(Game game) throws Exception {
        JsonNode hostView = game.host().snapshot();
        Browser shooter = hostView.get("turn").asText().equals("YOU") ? game.host() : game.guest();
        JsonNode before = shooter == game.host() ? hostView : shooter.snapshot();
        assertThat(allowed(before)).contains("FIRE");
        assertThat(allowed(game.other(shooter).snapshot())).doesNotContain("FIRE");

        JsonNode grid = before.at("/opponentBoard/grid");
        int[] target = firstUnknown(grid);
        tick();
        JsonNode reply = shooter.accept(fire(target[0], target[1]));

        JsonNode shot = reply.get("lastShot");
        assertThat(shot.get("by").asText()).isEqualTo("YOU");
        assertThat(shot.at("/target/rowIndex").asInt()).isEqualTo(target[0]);
        assertThat(shot.at("/target/columnIndex").asInt()).isEqualTo(target[1]);
        String result = shot.get("result").asText();
        assertThat(reply.at("/opponentBoard/grid/" + target[0] + "/" + target[1])
                        .asText())
                .isEqualTo(result);
        assertTurnAfter(reply, result, game.ruleset());
        if (result.equals("SUNK")) {
            assertWaterAroundSunk(reply, shot.get("sunkShipId").asText(), game.ruleset());
        }
        return new Shot(shooter, reply, before);
    }

    private static void assertTurnAfter(JsonNode reply, String result, JsonNode ruleset) {
        if (reply.get("phase").asText().equals("FINISHED")) {
            assertThat(reply.has("turn")).isFalse();
            return;
        }
        boolean keepsTurn =
                !result.equals("MISS") && ruleset.get("extraTurnOnHit").asBoolean();
        assertThat(reply.get("turn").asText()).isEqualTo(keepsTurn ? "YOU" : "OPPONENT");
    }

    /** Revealed water is the ruleset's rule: every neighbour of a sunk ship is disclosed, or none ever is. */
    private static void assertWaterAroundSunk(JsonNode reply, String sunkShipId, JsonNode ruleset) {
        JsonNode grid = reply.at("/opponentBoard/grid");
        JsonNode sunk = null;
        for (JsonNode ship : reply.at("/opponentBoard/ships")) {
            if (ship.get("shipId").asText().equals(sunkShipId)) {
                sunk = ship;
            }
        }
        assertThat(sunk).as("sunk ship %s is shown to the shooter", sunkShipId).isNotNull();
        assertThat(sunk.get("status").asText()).isEqualTo("SUNK");
        boolean reveals = ruleset.get("revealWaterAroundSunk").asBoolean();
        for (JsonNode cell : sunk.get("cells")) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int row = cell.get("rowIndex").asInt() + dr;
                    int column = cell.get("columnIndex").asInt() + dc;
                    if (row < 0
                            || column < 0
                            || row >= grid.size()
                            || column >= grid.get(row).size()) {
                        continue;
                    }
                    String state = grid.get(row).get(column).asText();
                    if (reveals) {
                        assertThat(state).isNotEqualTo("UNKNOWN");
                    }
                }
            }
        }
        int revealed = count(grid, "REVEALED_WATER");
        if (reveals) {
            assertThat(revealed).isPositive();
        } else {
            assertThat(revealed).isZero();
        }
    }

    // ------------------------------------------------------------------ S3 reload

    /**
     * A fresh client holding only the session cookie issues one {@code getGame} and has everything needed to continue,
     * identical to what the long-lived client last saw once the always-current clock fields are set aside.
     */
    private void assertReload(Browser player, JsonNode lastSeen, String phase, JsonNode ruleset) throws Exception {
        Browser fresh = player.reloaded();
        JsonNode view = fresh.snapshot();
        assertThat(fresh.http().seen()).as("a reload is one getGame").hasSize(1);

        assertThat(clockFree(view)).isEqualTo(clockFree(lastSeen));
        assertThat(view.get("gameId").asText()).isEqualTo(player.gameId());
        assertThat(view.get("phase").asText()).isEqualTo(phase);
        assertThat(view.get("rulesetId").asText()).isEqualTo(ruleset.get("id").asText());
        assertThat(view.has("version")).isTrue();
        assertThat(view.get("allowedActions")).isNotEmpty();
        assertThat(view.at("/you/connected").isBoolean()).isTrue();
        assertThat(view.at("/you/shipsRemaining").asInt())
                .isEqualTo(shipCount(ruleset) - sunkShips(view.get("yourBoard")));
        int rows = ruleset.at("/board/rows").asInt();
        int columns = ruleset.at("/board/columns").asInt();
        for (String board : new String[] {"/yourBoard", "/opponentBoard"}) {
            assertThat(view.at(board + "/grid")).hasSize(rows);
            view.at(board + "/grid").forEach(row -> assertThat(row).hasSize(columns));
        }
        if (!phase.equals("WAITING")) {
            assertThat(view.at("/yourBoard/ships")).hasSize(shipCount(ruleset));
            assertThat(view.at("/opponent/connected").isBoolean()).isTrue();
            assertThat(view.at("/opponent/shipsRemaining").asInt())
                    .isEqualTo(shipCount(ruleset) - sunkShips(view.get("opponentBoard")));
        }
        assertThat(view.has("turn")).isEqualTo(phase.equals("PLAYING"));
        assertThat(view.has("outcome")).isEqualTo(phase.equals("FINISHED"));
        assertThat(view.has("statistics")).isEqualTo(phase.equals("FINISHED"));
    }

    private void assertPlayingReload(Game game) throws Exception {
        for (Browser player : new Browser[] {game.host(), game.guest()}) {
            JsonNode lastSeen = player.snapshot();
            assertReload(player, lastSeen, "PLAYING", game.ruleset());
            assertThat(lastSeen.has("lastShot")).isTrue();
            assertThat(lastSeen.get("turn").asText()).isIn("YOU", "OPPONENT");
            assertThat(allowed(lastSeen))
                    .isEqualTo(
                            lastSeen.get("turn").asText().equals("YOU")
                                    ? Set.of("FIRE", "RESIGN", "SEND_PRESENCE")
                                    : Set.of("RESIGN", "SEND_PRESENCE"));
            assertThat(count(lastSeen.at("/opponentBoard/grid"), "UNKNOWN"))
                    .isLessThan(game.ruleset().at("/board/rows").asInt()
                            * game.ruleset().at("/board/columns").asInt());
        }
    }

    // ------------------------------------------------------------------ the end

    private void assertFinished(Game game, List<Shot> shots, String reason) throws Exception {
        JsonNode hostView = game.host().snapshot();
        JsonNode guestView = game.guest().snapshot();
        assertReload(game.host(), hostView, "FINISHED", game.ruleset());
        assertReload(game.guest(), guestView, "FINISHED", game.ruleset());

        assertThat(hostView.at("/outcome/reason").asText()).isEqualTo(reason);
        assertThat(guestView.at("/outcome/reason").asText()).isEqualTo(reason);
        String hostSide = hostView.at("/outcome/winner").asText();
        assertThat(guestView.at("/outcome/winner").asText()).isEqualTo(hostSide.equals("YOU") ? "OPPONENT" : "YOU");
        Shot lastShot = shots.getLast();
        Browser winner = lastShot.shooter();
        assertThat((winner == game.host()) == hostSide.equals("YOU")).isTrue();
        assertThat(allowed(hostView)).containsExactly("LEAVE");
        assertThat(allowed(guestView)).containsExactly("LEAVE");

        JsonNode loserView = winner == game.host() ? guestView : hostView;
        assertThat(loserView.at("/you/shipsRemaining").asInt()).isZero();
        assertThat(loserView.at("/statistics/you/fleet/sunk").asInt()).isEqualTo(shipCount(game.ruleset()));

        assertStatistics(hostView, guestView, game.ruleset());
        for (Browser player : new Browser[] {game.host(), game.guest()}) {
            long fired = shots.stream().filter(s -> s.shooter() == player).count();
            long hits = shots.stream()
                    .filter(s -> s.shooter() == player)
                    .filter(s -> !s.reply().at("/lastShot/result").asText().equals("MISS"))
                    .count();
            JsonNode own = (player == game.host() ? hostView : guestView).at("/statistics/you");
            assertThat(own.get("shots").asLong()).isEqualTo(fired);
            assertThat(own.get("hits").asLong()).isEqualTo(hits);
        }
        // Every action advanced the one injected clock by a second; the server read no other.
        assertThat(Instant.parse(hostView.get("serverTime").asText())).isEqualTo(time.now());
        assertThat(hostView.at("/statistics/match/gameplayDurationMs").asLong())
                .isEqualTo(STEP.toMillis() * shots.size());
    }

    private void leaveFromBoth(Game game) throws Exception {
        for (Browser player : new Browser[] {game.host(), game.guest()}) {
            Reply left = player.leave();
            assertThat(left.status()).isEqualTo(204);
            assertThat(player.read().status()).isEqualTo(404);
        }
    }

    /** The four identities of contracts/openapi.yaml, on both views, plus agreement of one match seen twice. */
    private static void assertStatistics(JsonNode hostView, JsonNode guestView, JsonNode ruleset) {
        JsonNode stats = hostView.get("statistics");
        JsonNode match = stats.get("match");
        JsonNode you = stats.get("you");
        JsonNode opponent = stats.get("opponent");

        // totalDurationMs = placementDurationMs + gameplayDurationMs
        assertThat(match.get("totalDurationMs").asLong())
                .isEqualTo(match.get("placementDurationMs").asLong()
                        + match.get("gameplayDurationMs").asLong());
        // Both players' turns.totalMs add up to match.gameplayDurationMs
        assertThat(you.at("/turns/totalMs").asLong()
                        + opponent.at("/turns/totalMs").asLong())
                .isEqualTo(match.get("gameplayDurationMs").asLong());
        assertThat(match.get("placementDurationMs").asLong())
                .isEqualTo(Math.max(
                        you.get("placementDurationMs").asLong(),
                        opponent.get("placementDurationMs").asLong()));
        for (JsonNode player : new JsonNode[] {you, opponent}) {
            // shotDecisions.count equals shots; intact + damaged + sunk = total
            assertThat(player.at("/shotDecisions/count").asLong())
                    .isEqualTo(player.get("shots").asLong());
            JsonNode fleet = player.get("fleet");
            assertThat(fleet.get("intact").asInt()
                            + fleet.get("damaged").asInt()
                            + fleet.get("sunk").asInt())
                    .isEqualTo(fleet.get("total").asInt())
                    .isEqualTo(shipCount(ruleset));
            assertThat(player.get("hits").asLong())
                    .isLessThanOrEqualTo(player.get("shots").asLong());
            if (player.get("shots").asLong() == 0) {
                assertThat(player.has("accuracy")).isFalse();
            } else {
                assertThat(player.get("accuracy").asDouble())
                        .isEqualTo(Math.round(10000.0
                                        * player.get("hits").asLong()
                                        / player.get("shots").asLong())
                                / 10000.0);
            }
        }
        // One match, two views: each side's "you" is the other side's "opponent".
        JsonNode other = guestView.get("statistics");
        assertThat(other.get("match")).isEqualTo(match);
        assertThat(other.get("you")).isEqualTo(opponent);
        assertThat(other.get("opponent")).isEqualTo(you);
    }

    // ------------------------------------------------------------------ small readers

    private static Set<String> allowed(JsonNode snapshot) {
        Set<String> actions = new TreeSet<>();
        snapshot.get("allowedActions").forEach(action -> actions.add(action.asText()));
        return actions;
    }

    private static JsonNode clockFree(JsonNode snapshot) {
        ObjectNode copy = snapshot.deepCopy();
        copy.remove("serverTime");
        copy.remove("expiresAt");
        return JSON.valueToTree(copy);
    }

    private static int shipCount(JsonNode ruleset) {
        int ships = 0;
        for (JsonNode entry : ruleset.get("fleet")) {
            ships += entry.get("count").asInt();
        }
        return ships;
    }

    private static int sunkShips(JsonNode board) {
        int sunk = 0;
        for (JsonNode ship : board.get("ships")) {
            if (ship.get("status").asText().equals("SUNK")) {
                sunk++;
            }
        }
        return sunk;
    }

    private static int placedShips(JsonNode board) {
        int placed = 0;
        for (JsonNode ship : board.get("ships")) {
            if (!ship.get("cells").isEmpty()) {
                placed++;
            }
        }
        return placed;
    }

    private static int count(JsonNode grid, String state) {
        int found = 0;
        for (JsonNode row : grid) {
            for (JsonNode cell : row) {
                if (cell.asText().equals(state)) {
                    found++;
                }
            }
        }
        return found;
    }

    private static int[] firstUnknown(JsonNode grid) {
        for (int row = 0; row < grid.size(); row++) {
            for (int column = 0; column < grid.get(row).size(); column++) {
                if (grid.get(row).get(column).asText().equals("UNKNOWN")) {
                    return new int[] {row, column};
                }
            }
        }
        throw new AssertionError("no UNKNOWN cell is left to fire at");
    }
}
