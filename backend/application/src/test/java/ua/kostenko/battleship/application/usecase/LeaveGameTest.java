package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.RULESET;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.failureCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.projection.BoardView.CellState;
import ua.kostenko.battleship.application.usecase.CommandTestFixture.Game;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

class LeaveGameTest {
    private static final Duration SECOND = Duration.ofSeconds(1);

    private static CommandTestFixture single() {
        return new CommandTestFixture(
                1,
                CommandTestFixture.IDLE,
                CommandTestFixture.ABSOLUTE,
                CommandTestFixture.INVITATION,
                CommandTestFixture.RETENTION,
                CommandTestFixture.PRESENCE);
    }

    private boolean liveGamesEmpty(CommandTestFixture f, String session) {
        return f.sessions.find(session).orElseThrow().liveGames().isEmpty();
    }

    @Test
    void waitingHostLeaveRemovesTheGameAndFreesPermitAndAllocation() {
        var f = single();
        var created = f.create.execute(RULESET, "Host", null);
        String id = created.snapshot().gameId();
        String host = created.sessionValue();

        f.leave.execute(id, host);

        assertThat(f.games.gameIds()).doesNotContain(id);
        assertThat(failureCode(() -> f.presence.execute(id, host))).isEqualTo("game-unavailable");
        assertThat(liveGamesEmpty(f, host)).isTrue();
        // permit released and per-browser cap (1) freed: the same browser can create again at once
        assertThat(f.create.execute(RULESET, "Host", host).snapshot().gameId()).isNotEqualTo(id);
    }

    @Test
    void placementLeaveByEitherPlayerAbandonsWithoutRevealingAnything() {
        for (Seat leaver : Seat.values()) {
            var f = new CommandTestFixture();
            Game game = f.newGame();
            f.placeFleet(game, Seat.HOST);
            f.placeFleet(game, Seat.GUEST);
            long version = f.state(game).version();

            f.leave.execute(game.id(), leaver == Seat.HOST ? game.host() : game.guest());

            assertThat(f.state(game).phase()).isEqualTo(Phase.ABANDONED);
            assertThat(f.state(game).version()).isEqualTo(version + 1);
            for (String session : new String[] {game.host(), game.guest()}) {
                assertThat(liveGamesEmpty(f, session)).isTrue();
            }
            String leaverSession = leaver == Seat.HOST ? game.host() : game.guest();
            assertThat(failureCode(() -> f.presence.execute(game.id(), leaverSession)))
                    .isEqualTo("game-unavailable");
            Seat other = leaver == Seat.HOST ? Seat.GUEST : Seat.HOST;
            String otherSession = other == Seat.HOST ? game.host() : game.guest();
            var view = f.presence.execute(game.id(), otherSession);
            assertThat(view.phase()).isEqualTo(Phase.ABANDONED);
            assertThat(view.statistics()).isNull();
            assertThat(view.outcome()).isNull();
            assertThat(view.opponentBoard().ships()).isEmpty();
            assertThat(view.opponentBoard().grid().stream().flatMap(java.util.List::stream))
                    .doesNotContain(CellState.SHIP);
        }
    }

    @Test
    void playingLeaveIsRefusedForBothSeatsWhileResignStaysAccepted() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.startPlaying(game);
        var before = f.state(game);

        assertThat(failureCode(() -> f.leave.execute(game.id(), game.host()))).isEqualTo("action-not-allowed");
        assertThat(failureCode(() -> f.leave.execute(game.id(), game.guest()))).isEqualTo("action-not-allowed");
        assertThat(f.state(game)).isSameAs(before);
        assertThat(f.state(game).version()).isEqualTo(before.version());

        assertThatCode(() -> f.send(game, Seat.HOST, new GameCommand.Resign())).doesNotThrowAnyException();

        var other = new CommandTestFixture();
        Game second = other.newGame();
        other.startPlaying(second);
        assertThatCode(() -> other.send(second, Seat.GUEST, new GameCommand.Resign()))
                .doesNotThrowAnyException();
    }

    @Test
    void finishedLeaveClearsOnlyTheLeaversSeatAndIsRepeatSafe() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.startPlaying(game);
        f.send(game, Seat.HOST, new GameCommand.Resign());

        assertThatCode(() -> f.leave.execute(game.id(), game.host())).doesNotThrowAnyException();

        assertThat(failureCode(() -> f.presence.execute(game.id(), game.host())))
                .isEqualTo("game-unavailable");
        assertThat(failureCode(() -> f.leave.execute(game.id(), game.host()))).isEqualTo("game-unavailable");
        var view = f.presence.execute(game.id(), game.guest());
        assertThat(view.phase()).isEqualTo(Phase.FINISHED);
        assertThat(view.outcome()).isNotNull();
        assertThat(view.statistics()).isNotNull();

        // the other player's result stays readable for the full retention period
        f.time.advance(CommandTestFixture.RETENTION.minus(SECOND));
        assertThat(f.presence.execute(game.id(), game.guest()).phase()).isEqualTo(Phase.FINISHED);
    }

    @Test
    void abandonedLeaveClearsOnlyTheLeaversSeat() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.leave.execute(game.id(), game.host());

        assertThat(f.state(game).phase()).isEqualTo(Phase.ABANDONED);
        assertThat(failureCode(() -> f.leave.execute(game.id(), game.host()))).isEqualTo("game-unavailable");
        assertThat(f.presence.execute(game.id(), game.guest()).phase()).isEqualTo(Phase.ABANDONED);
        f.leave.execute(game.id(), game.guest());
        assertThat(failureCode(() -> f.leave.execute(game.id(), game.guest()))).isEqualTo("game-unavailable");
    }

    @Test
    void leavingFreesTheBrowserAllocationAtOnce() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.leave.execute(game.id(), game.host());
        assertThat(liveGamesEmpty(f, game.host())).isTrue();
        assertThat(liveGamesEmpty(f, game.guest())).isTrue();
        assertThat(f.create.execute(RULESET, "Again", game.host()).snapshot().gameId())
                .isNotEqualTo(game.id());

        var done = new CommandTestFixture();
        Game finished = done.newGame();
        done.startPlaying(finished);
        done.send(finished, Seat.GUEST, new GameCommand.Resign());
        done.leave.execute(finished.id(), finished.guest());
        assertThat(liveGamesEmpty(done, finished.guest())).isTrue();
        assertThat(done.create
                        .execute(RULESET, "Again", finished.guest())
                        .snapshot()
                        .gameId())
                .isNotEqualTo(finished.id());
    }
}
