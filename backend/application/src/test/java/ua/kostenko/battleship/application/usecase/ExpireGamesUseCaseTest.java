package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.ABSOLUTE;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.IDLE;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.INVITATION;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.NOW;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.PRESENCE;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.RETENTION;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.failureCode;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.usecase.CommandTestFixture.Game;
import ua.kostenko.battleship.domain.model.Seat;

class ExpireGamesUseCaseTest {
    private static final Duration MS = Duration.ofMillis(1);
    private static final Instant FORGOTTEN_AT = NOW.plus(IDLE).plus(RETENTION);

    private static CommandTestFixture singleGameFixture() {
        return new CommandTestFixture(1, IDLE, ABSOLUTE, INVITATION, RETENTION, PRESENCE);
    }

    @Test
    void sweepReclaimsAnExpiredSlotAndItsPermitSoAdmissionSucceedsAgain() {
        var f = singleGameFixture();
        f.newGame();
        assertThat(failureCode(() -> f.create.execute(CommandTestFixture.RULESET, "Other", null)))
                .isEqualTo("service-unavailable");
        f.time.set(FORGOTTEN_AT);
        f.expire.sweep(f.time.now());
        assertThat(f.games.gameIds()).isEmpty();
        assertThatCode(() -> f.create.execute(CommandTestFixture.RULESET, "Other", null))
                .doesNotThrowAnyException();
    }

    @Test
    void sweepLeavesAnUnexpiredSlotUntouched() {
        var f = singleGameFixture();
        Game game = f.newGame();
        long version = f.state(game).version();
        Instant idle = f.idleDeadline(game.id());
        f.time.set(NOW.plus(IDLE).minus(MS));
        f.expire.sweep(f.time.now());
        assertThat(f.games.gameIds()).containsExactly(game.id());
        assertThat(f.state(game).version()).isEqualTo(version);
        assertThat(f.idleDeadline(game.id())).isEqualTo(idle);
        assertThatCode(() -> f.probe(game, Seat.HOST)).doesNotThrowAnyException();
    }

    @Test
    void permitIsReleasedExactlyOnceWhenTheSameSlotIsSweptTwice() {
        var f = singleGameFixture();
        f.newGame();
        f.time.set(FORGOTTEN_AT);
        f.expire.sweep(f.time.now());
        f.expire.sweep(f.time.now());
        f.create.execute(CommandTestFixture.RULESET, "First", null);
        assertThat(failureCode(() -> f.create.execute(CommandTestFixture.RULESET, "Second", null)))
                .isEqualTo("service-unavailable");
    }

    @Test
    void sweepChangesNoAnswerARequestWouldHaveReceived() {
        var f = singleGameFixture();
        Game game = f.newGame();
        f.time.set(NOW.plus(IDLE).plusSeconds(5));
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-expired");
        assertThat(failureCode(() -> f.probe(game, Seat.GUEST))).isEqualTo("game-expired");
        f.expire.sweep(f.time.now());
        assertThat(f.games.gameIds()).containsExactly(game.id());
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-expired");
        assertThat(failureCode(() -> f.probe(game, Seat.GUEST))).isEqualTo("game-expired");
        assertThat(f.sessions.find(game.host())).get().satisfies(r -> assertThat(r.liveGames())
                .isEmpty());
        assertThat(f.sessions.find(game.guest())).get().satisfies(r -> assertThat(r.liveGames())
                .isEmpty());

        f.time.set(FORGOTTEN_AT);
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-unavailable");
        f.expire.sweep(f.time.now());
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-unavailable");
    }
}
