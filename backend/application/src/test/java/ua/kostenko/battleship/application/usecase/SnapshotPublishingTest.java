package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * Every use case that changes a player's view hands the new view to the publisher, and every one that makes a game
 * stop existing for a seat says so — always after the game lock is released (plan.md § Realtime).
 */
class SnapshotPublishingTest {
    private final CommandTestFixture f = new CommandTestFixture();

    @Test
    void anAcceptedCommandPublishesExactlyTheSeatsWhoseViewChangedOutsideTheLock() {
        var game = f.newGame();
        f.published.clear();

        f.send(game, Seat.HOST, new GameCommand.PlaceFleetRandomly(100));
        assertThat(f.published.calls()).singleElement().satisfies(call -> {
            assertThat(call.seat()).isEqualTo(Seat.HOST);
            assertThat(call.gameId()).isEqualTo(game.id());
            assertThat(call.lockFree()).isTrue();
        });

        f.published.clear();
        var ready = f.send(game, Seat.HOST, new GameCommand.Ready());
        assertThat(f.published.calls())
                .extracting(RecordingPublisher.Call::seat)
                .containsExactlyInAnyOrder(Seat.HOST, Seat.GUEST);
        assertThat(f.published.calls()).allSatisfy(call -> {
            assertThat(call.lockFree()).isTrue();
            assertThat(call.view()).isEqualTo(ready.deliveries().get(call.seat()));
        });
    }

    @Test
    void aJoinPublishesTheHostsNewViewAndARepeatedJoinPublishesNothing() {
        var waiting = f.newWaitingGame();
        f.published.clear();
        var joined = f.join.execute(waiting.id(), waiting.secret(), "Guest", null);
        assertThat(f.published.calls()).singleElement().satisfies(call -> {
            assertThat(call.seat()).isEqualTo(Seat.HOST);
            assertThat(call.view().phase()).isEqualTo(Phase.PLACEMENT);
            assertThat(call.view().opponent().displayName()).isEqualTo("Guest");
            assertThat(call.lockFree()).isTrue();
        });

        f.published.clear();
        f.join.execute(waiting.id(), waiting.secret(), "Guest", joined.sessionValue());
        assertThat(f.published.calls()).isEmpty();
    }

    @Test
    void replacingTheInvitationPublishesTheHostsNewView() {
        var waiting = f.newWaitingGame();
        f.published.clear();
        SnapshotView answered = f.replace.execute(waiting.id(), waiting.host());
        assertThat(f.published.calls()).singleElement().satisfies(call -> {
            assertThat(call.seat()).isEqualTo(Seat.HOST);
            assertThat(call.view()).isEqualTo(answered);
            assertThat(call.lockFree()).isTrue();
        });
    }

    @Test
    void leavingDuringPlacementTellsTheLeaverItIsGoneAndTheStayerItIsAbandoned() {
        var game = f.newGame();
        f.published.clear();
        f.leave.execute(game.id(), game.guest());
        assertThat(f.published.calls()).hasSize(2).allSatisfy(call -> assertThat(call.lockFree())
                .isTrue());
        assertThat(f.published.calls()).anySatisfy(call -> {
            assertThat(call.seat()).isEqualTo(Seat.GUEST);
            assertThat(call.unavailable()).isTrue();
        });
        assertThat(f.published.calls()).anySatisfy(call -> {
            assertThat(call.seat()).isEqualTo(Seat.HOST);
            assertThat(call.view().phase()).isEqualTo(Phase.ABANDONED);
        });
    }

    @Test
    void leavingAWaitingGameTellsTheHostItIsGone() {
        var waiting = f.newWaitingGame();
        f.published.clear();
        f.leave.execute(waiting.id(), waiting.host());
        assertThat(f.published.calls()).singleElement().satisfies(call -> {
            assertThat(call.seat()).isEqualTo(Seat.HOST);
            assertThat(call.unavailable()).isTrue();
            assertThat(call.lockFree()).isTrue();
        });
    }

    @Test
    void theSweepTellsBothSeatsOfAnExpiredGameAndNothingForALiveOne() {
        var game = f.newGame();
        f.published.clear();
        f.expire.sweep(f.time.now());
        assertThat(f.published.calls()).isEmpty();

        f.time.advance(CommandTestFixture.IDLE.plus(Duration.ofMillis(1)));
        f.expire.sweep(f.time.now());
        assertThat(f.published.calls())
                .extracting(RecordingPublisher.Call::seat)
                .containsExactlyInAnyOrder(Seat.HOST, Seat.GUEST);
        assertThat(f.published.calls()).allSatisfy(call -> {
            assertThat(call.gameId()).isEqualTo(game.id());
            assertThat(call.unavailable()).isTrue();
            assertThat(call.lockFree()).isTrue();
        });
    }
}
