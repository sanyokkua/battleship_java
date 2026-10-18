package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

class CommandIdempotencyTest {
    private static GameCommand.PlaceShip place(int row, int col) {
        return new GameCommand.PlaceShip("s01", new Coordinate(row, col), Orientation.HORIZONTAL);
    }

    @Test
    void duplicateReturnsCurrentSnapshotEvenAfterFortyAcceptedIdsAndDifferentBody() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        var firstId = f.id();
        var placed = f.send(game, Seat.HOST, firstId, place(0, 0));
        assertThat(placed.snapshot().version()).isEqualTo(2);
        assertThat(placed.deliveries()).containsOnlyKeys(Seat.HOST);
        assertThatThrownBy(() -> placed.deliveries().clear()).isInstanceOf(UnsupportedOperationException.class);
        f.time.advance(Duration.ofSeconds(1));
        var immediate = f.send(game, Seat.HOST, firstId, place(2, 0));
        assertThat(immediate.snapshot().version()).isEqualTo(2);
        assertThat(immediate.snapshot().serverTime()).isEqualTo(f.time.now());
        assertThat(immediate.deliveries()).isEmpty();
        for (int i = 0; i < 39; i++) f.send(game, Seat.HOST, place(0, 0));
        f.send(game, Seat.HOST, new GameCommand.RemoveShip("s01"));
        var late = f.send(game, Seat.HOST, firstId, place(4, 0));
        assertThat(late.snapshot().version()).isEqualTo(3);
        assertThat(f.state(game).host().board().fleet().getFirst().anchor()).isNull();
        assertThat(late.deliveries()).isEmpty();
    }

    @Test
    void newIdsReachRulesAndRefusedIdCanBeRetriedLater() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.send(game, Seat.HOST, place(0, 0));
        var moved = f.send(game, Seat.HOST, place(8, 0));
        assertThat(moved.snapshot().version()).isEqualTo(3);
        assertThat(f.state(game).host().board().fleet().getFirst().anchor()).isEqualTo(new Coordinate(8, 0));

        var refusedId = f.id();
        long before = f.state(game).version();
        var deadline = f.games.withSlot(game.id(), slot -> slot.idleDeadline());
        assertThatThrownBy(() -> f.send(game, Seat.HOST, refusedId, new GameCommand.Ready()))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("action-not-allowed");
                    assertThat(failure.field()).isNull();
                    assertThat(failure.rule()).isNull();
                });
        assertThat(f.state(game).version()).isEqualTo(before);
        java.time.Instant afterRefusal = f.games.withSlot(game.id(), slot -> slot.idleDeadline());
        assertThat(afterRefusal).isEqualTo(deadline);
        for (int i = 1; i < CommandTestFixture.ANCHORS.length; i++) {
            int[] anchor = CommandTestFixture.ANCHORS[i];
            f.send(
                    game,
                    Seat.HOST,
                    new GameCommand.PlaceShip(
                            "s%02d".formatted(i + 1), new Coordinate(anchor[0], anchor[1]), Orientation.HORIZONTAL));
        }
        var ready = f.send(game, Seat.HOST, refusedId, new GameCommand.Ready());
        assertThat(ready.snapshot().you().ready()).isTrue();
        long version = ready.snapshot().version();
        assertThatThrownBy(() -> f.send(game, Seat.HOST, f.id(), new GameCommand.Ready()))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("action-not-allowed"));
        assertThat(f.state(game).version()).isEqualTo(version);
    }

    @Test
    void domainValidationRefusalPreservesFieldRuleStateAndCommandId() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        var before = f.state(game);
        var deadline = f.games.withSlot(game.id(), slot -> slot.idleDeadline());
        var refusedId = f.id();
        assertThatThrownBy(() -> f.send(
                        game,
                        Seat.HOST,
                        refusedId,
                        new GameCommand.PlaceShip("missing", new Coordinate(0, 0), Orientation.HORIZONTAL)))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("validation-failed");
                    assertThat(failure.field()).isEqualTo("/command/shipId");
                    assertThat(failure.rule()).isEqualTo("UNKNOWN_VALUE");
                });
        assertThat(f.state(game)).isSameAs(before);
        assertThat(f.state(game).version()).isEqualTo(before.version());
        java.time.Instant after = f.games.withSlot(game.id(), slot -> slot.idleDeadline());
        assertThat(after).isEqualTo(deadline);
        Set<java.util.UUID> accepted = f.games.withSlot(game.id(), slot -> Set.copyOf(slot.acceptedCommandIds()));
        assertThat(accepted).doesNotContain(refusedId);
        var retried = f.send(game, Seat.HOST, refusedId, place(0, 0));
        assertThat(retried.snapshot().version()).isEqualTo(before.version() + 1);
    }

    @Test
    void acceptedNoOpRemembersIdAndMovesIdleWithoutVersionOrDelivery() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.send(game, Seat.HOST, place(0, 0));
        f.time.advance(Duration.ofSeconds(5));
        var noOpId = f.id();
        var noOp = f.send(game, Seat.HOST, noOpId, place(0, 0));
        assertThat(noOp.snapshot().version()).isEqualTo(2);
        assertThat(noOp.snapshot().expiresAt()).isEqualTo(f.time.now().plus(CommandTestFixture.IDLE));
        assertThat(noOp.deliveries()).isEmpty();
        f.send(game, Seat.HOST, place(2, 0));
        var duplicate = f.send(game, Seat.HOST, noOpId, place(0, 0));
        assertThat(duplicate.snapshot().version()).isEqualTo(3);
        assertThat(f.state(game).host().board().fleet().getFirst().anchor()).isEqualTo(new Coordinate(2, 0));
    }

    @Test
    void terminalResignReleasesBothBrowsersAndDuplicateStillReadsResult() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.startPlaying(game);
        f.time.set(CommandTestFixture.NOW.plusSeconds(59));
        Coordinate target = f.state(game)
                .guest()
                .board()
                .fleet()
                .getFirst()
                .cells()
                .iterator()
                .next();
        f.send(game, Seat.HOST, new GameCommand.Fire(target));
        f.time.set(CommandTestFixture.NOW.plusSeconds(119).minusMillis(1));
        var resignId = f.id();
        var result = f.send(game, Seat.HOST, resignId, new GameCommand.Resign());
        assertThat(result.snapshot().phase()).isEqualTo(Phase.FINISHED);
        assertThat(result.snapshot().expiresAt()).isEqualTo(f.time.now().plus(CommandTestFixture.RETENTION));
        assertThat(result.deliveries()).containsOnlyKeys(Seat.HOST, Seat.GUEST);
        assertThat(f.sessions.find(game.host())).get().satisfies(r -> assertThat(r.liveGames())
                .isEmpty());
        assertThat(f.sessions.find(game.guest())).get().satisfies(r -> assertThat(r.liveGames())
                .isEmpty());
        f.time.set(CommandTestFixture.NOW.plusSeconds(121));
        var repeated = f.send(game, Seat.HOST, resignId, new GameCommand.ClearFleet());
        assertThat(repeated.snapshot().phase()).isEqualTo(Phase.FINISHED);
        assertThat(repeated.snapshot().serverTime()).isEqualTo(f.time.now());
        assertThat(repeated.deliveries()).isEmpty();
        assertThatThrownBy(() -> f.send(game, Seat.HOST, new GameCommand.Resign()))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("action-not-allowed"));
        f.newGame(game.host(), game.guest());
    }

    @Test
    void terminalFireReleasesBothBrowsersAndRetainsResultBeyondAbsoluteCeiling() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.startPlaying(game);
        var cells = f.state(game).guest().board().fleet().stream()
                .flatMap(s -> s.cells().stream())
                .toList();
        for (int i = 0; i < cells.size() - 2; i++) f.send(game, Seat.HOST, new GameCommand.Fire(cells.get(i)));
        f.time.set(CommandTestFixture.NOW.plusSeconds(59));
        f.send(game, Seat.HOST, new GameCommand.Fire(cells.get(cells.size() - 2)));
        f.time.set(CommandTestFixture.NOW.plusSeconds(119).minusMillis(1));
        var last = f.send(game, Seat.HOST, new GameCommand.Fire(cells.getLast()));
        assertThat(last.snapshot().phase()).isEqualTo(Phase.FINISHED);
        assertThat(last.snapshot().expiresAt()).isEqualTo(f.time.now().plus(CommandTestFixture.RETENTION));
        assertThat(f.sessions.find(game.host())).get().satisfies(r -> assertThat(r.liveGames())
                .isEmpty());
        assertThat(f.sessions.find(game.guest())).get().satisfies(r -> assertThat(r.liveGames())
                .isEmpty());
        f.newGame(game.host(), game.guest());
    }

    @Test
    void liveDeadlineIsInclusiveAndPrivacySafeBeforeDuplicateDetection() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        var accepted = f.id();
        f.send(game, Seat.HOST, accepted, place(0, 0));
        long version = f.state(game).version();
        f.time.set(f.games.withSlot(game.id(), slot -> slot.idleDeadline()));
        assertThatThrownBy(() -> f.send(game, Seat.HOST, accepted, place(0, 0)))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("game-expired"));
        assertThatThrownBy(() -> f.commands.execute(game.id(), "stranger", f.id(), place(0, 0)))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("game-unavailable"));
        assertThat(f.state(game).version()).isEqualTo(version);
        Set<java.util.UUID> ids = f.games.withSlot(game.id(), slot -> Set.copyOf(slot.acceptedCommandIds()));
        assertThat(ids).containsExactly(accepted);
    }

    @Test
    void absoluteDeadlineIsInclusiveEvenWhenAcceptedNoOpsExtendIdle() {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.send(game, Seat.HOST, place(0, 0));
        f.time.set(CommandTestFixture.NOW.plusSeconds(59));
        f.send(game, Seat.HOST, place(0, 0));
        f.time.set(CommandTestFixture.NOW.plusSeconds(118));
        var beforeCeiling = f.send(game, Seat.HOST, place(0, 0));
        assertThat(beforeCeiling.snapshot().version()).isEqualTo(2);
        assertThat(beforeCeiling.snapshot().expiresAt()).isEqualTo(CommandTestFixture.NOW.plusSeconds(120));
        f.time.set(CommandTestFixture.NOW.plusSeconds(120));
        assertThatThrownBy(() -> f.send(game, Seat.HOST, new GameCommand.RemoveShip("s01")))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("game-expired"));
        assertThat(f.state(game).version()).isEqualTo(2);
    }
}
