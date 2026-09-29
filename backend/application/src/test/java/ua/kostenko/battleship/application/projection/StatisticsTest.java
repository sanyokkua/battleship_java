package ua.kostenko.battleship.application.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.*;
import ua.kostenko.battleship.domain.rules.GameRules;
import ua.kostenko.battleship.domain.rules.Rulesets;
import ua.kostenko.battleship.domain.transition.Transition;

class StatisticsTest {
    private static final Instant START = Instant.parse("2026-09-29T12:00:00Z");
    private static final String RULESET = "sea-battle-10-ship.v1";
    private final MutableTimeSource time = new MutableTimeSource(START);
    private final SeededRandomSource random = new SeededRandomSource(42);
    private final SnapshotProjector projector = new SnapshotProjector();

    @Test
    void resignationUsesAcceptedSamplesAndClosesTheOtherPlayersTurn() {
        GameState game = playing();
        game = accepted(game, Seat.HOST, new GameCommand.Fire(c(0, 0)), 11);
        Transition refused = GameRules.apply(game, Seat.HOST, new GameCommand.Fire(c(0, 0)), time.now(), random);
        assertThat(refused.rejection()).isNotNull();
        assertThat(refused.next()).isSameAs(game);
        time.advance(Duration.ofMillis(7));
        game = accepted(game, Seat.HOST, new GameCommand.Fire(c(9, 9)), 13);
        game = accepted(game, Seat.GUEST, new GameCommand.Resign(), 19);

        StatisticsView host = snapshot(game, Seat.HOST).statistics();
        StatisticsView guest = snapshot(game, Seat.GUEST).statistics();
        assertThat(host).isNotNull();
        assertThat(host.match()).isEqualTo(new StatisticsView.MatchStatistics(3050, 3000, 50));
        assertThat(host.you().placementDurationMs()).isEqualTo(1000);
        assertThat(host.opponent().placementDurationMs()).isEqualTo(3000);
        assertThat(host.you().shots()).isEqualTo(2);
        assertThat(host.you().hits()).isEqualTo(1);
        assertThat(host.you().accuracy()).isEqualTo(0.5);
        assertThat(host.you().turns()).isEqualTo(new StatisticsView.DurationAggregate(1, 31, 31L, 31L, 31L));
        assertThat(host.you().shotDecisions()).isEqualTo(new StatisticsView.DurationAggregate(2, 31, 16L, 11L, 20L));
        assertThat(host.opponent().turns()).isEqualTo(new StatisticsView.DurationAggregate(1, 19, 19L, 19L, 19L));
        assertThat(host.opponent().shotDecisions())
                .isEqualTo(new StatisticsView.DurationAggregate(0, 0, null, null, null));
        assertThat(host.opponent().accuracy()).isNull();
        assertThat(host.you().fleet()).isEqualTo(new StatisticsView.FleetSummary(10, 10, 0, 0));
        assertThat(host.opponent().fleet()).isEqualTo(new StatisticsView.FleetSummary(10, 9, 1, 0));
        assertThat(guest.you()).isEqualTo(host.opponent());
        assertThat(guest.opponent()).isEqualTo(host.you());
        assertIdentities(host);
        assertIdentities(guest);
    }

    @Test
    void fleetDestructionClosesTheWinningTurn() {
        GameState game = playing();
        List<Coordinate> targets = game.guest().board().fleet().stream()
                .flatMap(ship -> ship.cells().stream())
                .toList();
        for (Coordinate target : targets) game = accepted(game, Seat.HOST, new GameCommand.Fire(target), 7);
        assertThat(game.phase()).isEqualTo(Phase.FINISHED);
        assertThat(game.outcome().reason()).isEqualTo(Outcome.Reason.FLEET_DESTROYED);
        StatisticsView statistics = snapshot(game, Seat.HOST).statistics();
        assertThat(statistics.match()).isEqualTo(new StatisticsView.MatchStatistics(3140, 3000, 140));
        assertThat(statistics.you().turns()).isEqualTo(new StatisticsView.DurationAggregate(1, 140, 140L, 140L, 140L));
        assertThat(statistics.opponent().turns())
                .isEqualTo(new StatisticsView.DurationAggregate(0, 0, null, null, null));
        assertThat(statistics.you().shots()).isEqualTo(20);
        assertThat(statistics.you().hits()).isEqualTo(20);
        assertThat(statistics.you().accuracy()).isEqualTo(1.0);
        assertThat(statistics.opponent().fleet()).isEqualTo(new StatisticsView.FleetSummary(10, 0, 0, 10));
        assertIdentities(statistics);
    }

    @Test
    void accuracyRoundsRepeatingFractionsToFourDecimals() {
        GameState game = playing();
        game = accepted(game, Seat.HOST, new GameCommand.Fire(c(0, 0)), 1);
        game = accepted(game, Seat.HOST, new GameCommand.Fire(c(0, 1)), 1);
        game = accepted(game, Seat.HOST, new GameCommand.Fire(c(9, 9)), 1);
        game = accepted(game, Seat.GUEST, new GameCommand.Resign(), 1);
        assertThat(snapshot(game, Seat.HOST).statistics().you().accuracy()).isEqualTo(0.6667);
    }

    @Test
    void subMillisecondBoundariesKeepTotalEqualToRoundedParts() {
        GameState game = playing(Duration.ofNanos(500_000));
        time.advance(Duration.ofNanos(600_000));
        Transition resignation = GameRules.apply(game, Seat.GUEST, new GameCommand.Resign(), time.now(), random);
        assertThat(resignation.rejection()).isNull();
        StatisticsView statistics = snapshot(resignation.next(), Seat.HOST).statistics();
        assertThat(statistics.match()).isEqualTo(new StatisticsView.MatchStatistics(3000, 3000, 0));
        assertIdentities(statistics);
    }

    @Test
    void unfinishedAndAbandonedGamesHaveNoStatistics() {
        GameState waiting = GameState.create(Rulesets.byId(RULESET).orElseThrow(), "Host");
        assertThat(snapshot(waiting, Seat.HOST).statistics()).isNull();
        GameState placement = waiting.withGuest("Guest", time.now());
        assertThat(snapshot(placement, Seat.HOST).statistics()).isNull();
        GameState playing = playing();
        assertThat(snapshot(playing, Seat.GUEST).statistics()).isNull();
        GameState abandoned = new GameState(
                playing.rulesetId(),
                Phase.ABANDONED,
                playing.version(),
                playing.host(),
                playing.guest(),
                null,
                playing.lastShot(),
                null,
                playing.timeline());
        assertThat(snapshot(abandoned, Seat.HOST).statistics()).isNull();
    }

    private GameState playing() {
        return playing(Duration.ZERO);
    }

    private GameState playing(Duration beforeSecondReady) {
        GameState game = GameState.create(Rulesets.byId(RULESET).orElseThrow(), "Host");
        time.advance(Duration.ofMillis(100));
        game = game.withGuest("Guest", time.now());
        int[][] anchors = {{0, 0}, {2, 0}, {2, 4}, {4, 0}, {4, 3}, {4, 6}, {6, 0}, {6, 2}, {6, 4}, {6, 6}};
        for (Seat seat : Seat.values()) {
            for (int index = 0; index < anchors.length; index++) {
                game = accepted(
                        game,
                        seat,
                        new GameCommand.PlaceShip(
                                "s%02d".formatted(index + 1),
                                c(anchors[index][0], anchors[index][1]),
                                Orientation.HORIZONTAL),
                        0);
            }
        }
        game = accepted(game, Seat.HOST, new GameCommand.Ready(), 1000);
        time.advance(beforeSecondReady);
        game = accepted(game, Seat.GUEST, new GameCommand.Ready(), 2000);
        assertThat(game.phase()).isEqualTo(Phase.PLAYING);
        assertThat(game.turn()).isEqualTo(Seat.HOST);
        return game;
    }

    private GameState accepted(GameState state, Seat actor, GameCommand command, long advanceMs) {
        time.advance(Duration.ofMillis(advanceMs));
        Transition transition = GameRules.apply(state, actor, command, time.now(), random);
        assertThat(transition.rejection()).isNull();
        return transition.next();
    }

    private SnapshotView snapshot(GameState state, Seat seat) {
        return projector.project(
                state,
                seat,
                new SnapshotContext("game-1", time.now(), time.now().plusSeconds(60), null, null, true, true));
    }

    private static Coordinate c(int row, int column) {
        return new Coordinate(row, column);
    }

    private static void assertIdentities(StatisticsView statistics) {
        assertThat(statistics.match().totalDurationMs())
                .isEqualTo(statistics.match().placementDurationMs()
                        + statistics.match().gameplayDurationMs());
        assertThat(statistics.you().turns().totalMs()
                        + statistics.opponent().turns().totalMs())
                .isEqualTo(statistics.match().gameplayDurationMs());
        for (StatisticsView.PlayerStatistics player : List.of(statistics.you(), statistics.opponent())) {
            assertThat(player.shotDecisions().count()).isEqualTo(player.shots());
            assertThat(player.fleet().intact()
                            + player.fleet().damaged()
                            + player.fleet().sunk())
                    .isEqualTo(player.fleet().total());
        }
    }
}
