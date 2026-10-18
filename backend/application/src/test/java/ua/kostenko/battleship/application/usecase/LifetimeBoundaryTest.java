package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.IDLE;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.INVITATION;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.NOW;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.PRESENCE;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.RETENTION;
import static ua.kostenko.battleship.application.usecase.CommandTestFixture.failureCode;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.usecase.CommandTestFixture.Game;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

class LifetimeBoundaryTest {
    private static final Duration MS = Duration.ofMillis(1);
    private static final String ANY_SECRET = "Z".repeat(43);

    @Test
    void idleLifetimeIsLiveJustBeforeItsDeadlineAndExpiredAtIt() {
        var f = new CommandTestFixture();
        Game justBefore = f.newGame();
        Game atDeadline = f.newGame();
        f.time.set(NOW.plus(IDLE).minus(MS));
        assertThatCode(() -> f.probe(justBefore, Seat.HOST)).doesNotThrowAnyException();
        f.time.set(NOW.plus(IDLE));
        assertThat(failureCode(() -> f.probe(atDeadline, Seat.HOST))).isEqualTo("game-expired");
    }

    @Test
    void absoluteLifetimeEndsPlayAtItsDeadlineWhateverTheActivity() {
        var f = new CommandTestFixture(
                5, Duration.ofSeconds(100), Duration.ofSeconds(60), INVITATION, RETENTION, PRESENCE);
        Game active = f.newGame();
        Game other = f.newGame();
        f.time.set(NOW.plusSeconds(30));
        f.probe(active, Seat.HOST);
        f.time.set(NOW.plusSeconds(60).minus(MS));
        assertThatCode(() -> f.probe(active, Seat.HOST)).doesNotThrowAnyException();
        f.time.set(NOW.plusSeconds(60));
        assertThat(failureCode(() -> f.probe(active, Seat.GUEST))).isEqualTo("game-expired");
        assertThat(failureCode(() -> f.probe(other, Seat.HOST))).isEqualTo("game-expired");
    }

    @Test
    void resultRetentionIsReadableJustBeforeItsDeadlineAndGoneAtIt() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.startPlaying(game);
        UUID resign = f.id();
        f.send(game, Seat.HOST, resign, new GameCommand.Resign());
        Instant finishedAt = f.time.now();
        f.time.set(finishedAt.plus(RETENTION).minus(MS));
        var result = f.send(game, Seat.GUEST, resign, new GameCommand.Resign());
        assertThat(result.snapshot().phase()).isEqualTo(Phase.FINISHED);
        f.time.set(finishedAt.plus(RETENTION));
        assertThat(failureCode(() -> f.send(game, Seat.GUEST, resign, new GameCommand.Resign())))
                .isEqualTo("game-unavailable");
        assertThat(failureCode(() -> f.send(game, Seat.HOST, resign, new GameCommand.Resign())))
                .isEqualTo("game-unavailable");
    }

    @Test
    void invitationLifetimeAcceptsJoinJustBeforeItsDeadlineAndRefusesAtIt() {
        var f = new CommandTestFixture();
        var early = f.newWaitingGame();
        var late = f.newWaitingGame();
        f.time.set(NOW.plus(INVITATION).minus(MS));
        assertThat(f.join.execute(early.id(), early.secret(), "Guest", null)
                        .snapshot()
                        .phase())
                .isEqualTo(Phase.PLACEMENT);
        f.time.set(NOW.plus(INVITATION));
        assertThat(failureCode(() -> f.join.execute(late.id(), late.secret(), "Guest", null)))
                .isEqualTo("invitation-unavailable");
    }

    @Test
    void presenceThrottleThrottlesJustBeforeItsBoundaryAndAcceptsAtIt() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        var first = f.presence.execute(game.id(), game.host());
        assertThat(first.expiresAt()).isEqualTo(NOW.plus(IDLE));
        f.time.set(NOW.plus(PRESENCE).minus(MS));
        assertThat(f.presence.execute(game.id(), game.host()).expiresAt()).isEqualTo(NOW.plus(IDLE));
        f.time.set(NOW.plus(PRESENCE));
        assertThat(f.presence.execute(game.id(), game.host()).expiresAt())
                .isEqualTo(NOW.plus(PRESENCE).plus(IDLE));
    }

    @Test
    void creationJoinAcceptedActionAndAcceptedPresenceEachMoveTheIdleDeadline() {
        var f = new CommandTestFixture();
        var waiting = f.newWaitingGame();
        assertThat(f.idleDeadline(waiting.id())).isEqualTo(NOW.plus(IDLE));
        f.time.set(NOW.plusSeconds(5));
        var joined = f.join.execute(waiting.id(), waiting.secret(), "Guest", null);
        assertThat(f.idleDeadline(waiting.id())).isEqualTo(NOW.plusSeconds(5).plus(IDLE));
        var game = new Game(waiting.id(), waiting.host(), joined.sessionValue());
        f.time.set(NOW.plusSeconds(7));
        f.probe(game, Seat.HOST);
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plusSeconds(7).plus(IDLE));
        f.time.set(NOW.plusSeconds(9));
        f.presence.execute(game.id(), game.guest());
        assertThat(f.idleDeadline(game.id())).isEqualTo(NOW.plusSeconds(9).plus(IDLE));
    }

    @Test
    void refusedActionReplacedInvitationThrottledPresenceAndRepeatedCommandDoNotMoveTheIdleDeadline() {
        var f = new CommandTestFixture();
        var waiting = f.newWaitingGame();
        f.time.set(NOW.plusSeconds(1));
        f.replace.execute(waiting.id(), waiting.host());
        assertThat(f.idleDeadline(waiting.id())).isEqualTo(NOW.plus(IDLE));

        Game game = f.newGame();
        Instant deadline = f.idleDeadline(game.id());
        f.time.set(NOW.plusSeconds(2));
        assertThat(failureCode(() -> f.send(game, Seat.HOST, new GameCommand.Ready())))
                .isEqualTo("action-not-allowed");
        assertThat(f.idleDeadline(game.id())).isEqualTo(deadline);

        UUID id = f.id();
        f.send(
                game,
                Seat.HOST,
                id,
                new GameCommand.PlaceShip(
                        "s01",
                        new ua.kostenko.battleship.domain.model.Coordinate(0, 0),
                        ua.kostenko.battleship.domain.model.Orientation.HORIZONTAL));
        Instant afterAction = f.idleDeadline(game.id());
        f.time.set(NOW.plusSeconds(3));
        f.send(
                game,
                Seat.HOST,
                id,
                new GameCommand.PlaceShip(
                        "s01",
                        new ua.kostenko.battleship.domain.model.Coordinate(0, 0),
                        ua.kostenko.battleship.domain.model.Orientation.HORIZONTAL));
        assertThat(f.idleDeadline(game.id())).isEqualTo(afterAction);

        f.presence.execute(game.id(), game.host());
        Instant afterPresence = f.idleDeadline(game.id());
        f.time.set(NOW.plusSeconds(4));
        f.presence.execute(game.id(), game.host());
        assertThat(f.idleDeadline(game.id())).isEqualTo(afterPresence);
    }

    @Test
    void gameFinishedJustBeforeTheAbsoluteCeilingKeepsItsResultForTheFullRetention() {
        var f = new CommandTestFixture(
                5, Duration.ofSeconds(100), Duration.ofSeconds(60), INVITATION, RETENTION, PRESENCE);
        Game game = f.newGame();
        f.startPlaying(game);
        f.time.set(NOW.plusSeconds(60).minus(MS));
        UUID resign = f.id();
        f.send(game, Seat.HOST, resign, new GameCommand.Resign());
        Instant finishedAt = f.time.now();
        f.time.set(finishedAt.plus(RETENTION).minus(MS));
        assertThat(f.send(game, Seat.HOST, resign, new GameCommand.Resign())
                        .snapshot()
                        .phase())
                .isEqualTo(Phase.FINISHED);
        f.time.set(finishedAt.plus(RETENTION));
        assertThat(failureCode(() -> f.send(game, Seat.HOST, resign, new GameCommand.Resign())))
                .isEqualTo("game-unavailable");
    }

    @Test
    void expiredGameAnswersGoneToItsOwnDigestsAndUnavailableToOthersThenUnavailableToAllAfterRetention() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        f.sessions.register("stranger", NOW);
        f.time.set(NOW.plus(IDLE));
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-expired");
        assertThat(failureCode(() -> f.probe(game, Seat.GUEST))).isEqualTo("game-expired");
        assertThat(failureCode(() -> f.commands.execute(game.id(), "stranger", f.id(), new GameCommand.Ready())))
                .isEqualTo("game-unavailable");
        f.time.set(NOW.plus(IDLE).plus(RETENTION).minus(MS));
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-expired");
        f.time.set(NOW.plus(IDLE).plus(RETENTION));
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-unavailable");
        assertThat(failureCode(() -> f.probe(game, Seat.GUEST))).isEqualTo("game-unavailable");
    }

    @Test
    void joiningAnExpiredRetainedGameTellsOnlyItsExistingGuest() {
        var f = new CommandTestFixture();
        var waiting = f.newWaitingGame();
        String guest =
                f.join.execute(waiting.id(), waiting.secret(), "Guest", null).sessionValue();
        f.sessions.register("stranger-1", NOW);
        f.sessions.register("stranger-2", NOW);
        f.time.set(NOW.plus(IDLE));
        assertThat(failureCode(() -> f.join.execute(waiting.id(), ANY_SECRET, "Guest", guest)))
                .isEqualTo("game-expired");
        assertThat(failureCode(() -> f.join.execute(waiting.id(), waiting.secret(), "Guest", guest)))
                .isEqualTo("game-expired");
        assertThat(failureCode(() -> f.join.execute(waiting.id(), ANY_SECRET, "Guest", waiting.host())))
                .isEqualTo("invitation-unavailable");
        assertThat(failureCode(() -> f.join.execute(waiting.id(), waiting.secret(), "Guest", "stranger-1")))
                .isEqualTo("invitation-unavailable");
        assertThat(failureCode(() -> f.join.execute(waiting.id(), ANY_SECRET, "Guest", "stranger-2")))
                .isEqualTo("invitation-unavailable");
        f.time.set(NOW.plus(IDLE).plus(RETENTION));
        assertThat(failureCode(() -> f.join.execute(waiting.id(), ANY_SECRET, "Guest", guest)))
                .isEqualTo("invitation-unavailable");
    }

    @Test
    void requestAfterExpiryButBeforeTheSweepGetsTheExpiredAnswerAndNothingIsResurrected() {
        var f = new CommandTestFixture();
        Game game = f.newGame();
        long version = f.state(game).version();
        f.time.set(NOW.plus(IDLE).plus(MS));
        assertThat(f.games.gameIds()).contains(game.id());
        assertThat(failureCode(() -> f.probe(game, Seat.HOST))).isEqualTo("game-expired");
        assertThat(failureCode(() -> f.presence.execute(game.id(), game.host())))
                .isEqualTo("game-expired");
        assertThat(f.state(game).version()).isEqualTo(version);
    }

    @Test
    void replacingTheInvitationOfAnIdleExpiredGameAnswersGoneToItsHostAndChangesNothing() {
        var f = new CommandTestFixture();
        var waiting = f.newWaitingGame();
        f.sessions.register("stranger", NOW);
        long version = f.games.withSlot(waiting.id(), slot -> slot.state().version());
        f.time.set(NOW.plus(IDLE));
        assertThat(failureCode(() -> f.replace.execute(waiting.id(), waiting.host())))
                .isEqualTo("game-expired");
        assertThat(failureCode(() -> f.replace.execute(waiting.id(), "stranger")))
                .isEqualTo("game-unavailable");
        long after = f.games.withSlot(waiting.id(), slot -> slot.state().version());
        assertThat(after).isEqualTo(version);
    }

    @Test
    void replacingTheInvitationAfterRetentionIsUnavailableAndASweepChangesNoAnswer() {
        var f = new CommandTestFixture();
        var waiting = f.newWaitingGame();
        f.time.set(NOW.plus(IDLE).plus(RETENTION));
        assertThat(failureCode(() -> f.replace.execute(waiting.id(), waiting.host())))
                .isEqualTo("game-unavailable");
        f.expire.sweep(f.time.now());
        assertThat(failureCode(() -> f.replace.execute(waiting.id(), waiting.host())))
                .isEqualTo("game-unavailable");
    }

    @Test
    void perBrowserCapIsFreedByExpiryEvenBeforeAnySweep() {
        var f = new CommandTestFixture();
        var first = f.create.execute(CommandTestFixture.RULESET, "Host", null);
        assertThat(failureCode(() -> f.create.execute(CommandTestFixture.RULESET, "Host", first.sessionValue())))
                .isEqualTo("service-unavailable");
        f.time.set(NOW.plus(IDLE).plusSeconds(5));
        assertThatCode(() -> f.create.execute(CommandTestFixture.RULESET, "Host", first.sessionValue()))
                .doesNotThrowAnyException();
    }

    @Test
    void perBrowserCapIsFreedByExpiryForAGuestJoiningAnotherGameBeforeAnySweep() {
        var f = new CommandTestFixture();
        var guestsGame = f.newWaitingGame();
        String guest = f.join.execute(guestsGame.id(), guestsGame.secret(), "Guest", null)
                .sessionValue();
        var other = f.newWaitingGame();
        f.time.set(NOW.plus(INVITATION).minusSeconds(1));
        assertThat(failureCode(() -> f.join.execute(other.id(), other.secret(), "Guest", guest)))
                .isEqualTo("service-unavailable");
        f.time.set(NOW.plus(IDLE));
        var late = f.create.execute(CommandTestFixture.RULESET, "Host", null);
        String url = late.snapshot().invitationUrl();
        assertThatCode(() -> f.join.execute(
                        late.snapshot().gameId(), url.substring(url.indexOf("#invite=") + 8), "Guest", guest))
                .doesNotThrowAnyException();
    }
}
