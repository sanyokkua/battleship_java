package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.IDLE;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.NOW;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.PRESENCE;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.usecase.CommandTestFixture.Game;

class PresenceThrottleTest {
    private static final Duration MS = Duration.ofMillis(1);

    @Test
    void acceptedSignalMovesTheIdleDeadlineAndLeavesTheVersionUnchanged() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        long version = f.state(game).version();
        f.time.set(NOW.plusSeconds(20));
        var answer = f.presence.execute(game.id(), game.host());
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plusSeconds(20).plus(IDLE));
        assertThat(answer.version()).isEqualTo(version);
        assertThat(f.state(game).version()).isEqualTo(version);
    }

    @Test
    void secondSignalFromTheSameSeatBeforeTheIntervalChangesNothing() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.time.set(NOW.plusSeconds(20));
        var first = f.presence.execute(game.id(), game.host());
        f.time.set(NOW.plusSeconds(25));
        var second = f.presence.execute(game.id(), game.host());
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plusSeconds(20).plus(IDLE));
        assertThat(second.version()).isEqualTo(first.version());
        assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(second.phase()).isEqualTo(first.phase());
    }

    @Test
    void seatsAreThrottledIndependently() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.time.set(NOW.plusSeconds(20));
        f.presence.execute(game.id(), game.host());
        f.time.set(NOW.plusSeconds(22));
        var guest = f.presence.execute(game.id(), game.guest());
        assertThat(guest.expiresAt()).isEqualTo(NOW.plusSeconds(22).plus(IDLE));
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plusSeconds(22).plus(IDLE));
    }

    @Test
    void throttleBoundaryIsInclusive() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.presence.execute(game.id(), game.host());
        f.time.set(NOW.plus(PRESENCE).minus(MS));
        f.presence.execute(game.id(), game.host());
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plus(IDLE));
        f.time.set(NOW.plus(PRESENCE));
        f.presence.execute(game.id(), game.host());
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plus(PRESENCE).plus(IDLE));
    }

    @Test
    void callerAnswerCarriesAFreshExpiresAtEvenWhenNothingElseChanged() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        var before =
                f.probe(game, ua.kostenko.battleship.domain.model.Seat.HOST).snapshot();
        f.time.set(NOW.plusSeconds(30));
        var after = f.presence.execute(game.id(), game.host());
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after.expiresAt()).isEqualTo(NOW.plusSeconds(30).plus(IDLE)).isNotEqualTo(before.expiresAt());
        assertThat(after.serverTime()).isEqualTo(NOW.plusSeconds(30));
    }
}
