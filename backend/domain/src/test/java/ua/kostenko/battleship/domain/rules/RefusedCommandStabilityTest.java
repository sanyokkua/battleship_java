package ua.kostenko.battleship.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.transition.Rejection.ProblemCode;
import ua.kostenko.battleship.domain.transition.Transition;

class RefusedCommandStabilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void disallowedCommandsPreserveStateAndVersionInEachForbiddenPhase(String id) {
        List<GameCommand> seven = List.of(
                new GameCommand.PlaceShip("s01", new Coordinate(0, 0), Orientation.HORIZONTAL),
                new GameCommand.RemoveShip("s01"),
                new GameCommand.PlaceFleetRandomly(10000),
                new GameCommand.ClearFleet(),
                new GameCommand.Ready(),
                new GameCommand.Fire(new Coordinate(0, 0)),
                new GameCommand.Resign());
        GameState joined = joined(id);
        GameState arranged = arranged(id);
        for (Seat actor : Seat.values()) {
            for (Phase phase : List.of(Phase.WAITING, Phase.FINISHED, Phase.ABANDONED)) {
                GameState before = phase == Phase.WAITING
                        ? GameState.create(Rulesets.byId(id).orElseThrow(), "Host")
                        : inPhase(arranged, phase, null);
                for (GameCommand command : seven) assertRefused(before, actor, command);
            }
            for (GameCommand command : List.of(new GameCommand.Fire(new Coordinate(0, 0)), new GameCommand.Resign())) {
                assertRefused(joined, actor, command);
                assertRefused(arranged, actor, command);
            }
            assertRefused(joined, actor, new GameCommand.Ready());
            GameState partial = GameRules.apply(joined, actor, seven.getFirst(), NOW, new SeededRandomSource(42))
                    .next();
            assertRefused(partial, actor, new GameCommand.Ready());
            GameState ready = GameRules.apply(arranged, actor, new GameCommand.Ready(), NOW, new SeededRandomSource(42))
                    .next();
            for (GameCommand command : seven) assertRefused(ready, actor, command);
            for (Seat turn : Seat.values()) {
                GameState playing = inPhase(arranged, Phase.PLAYING, turn);
                for (GameCommand command : seven.subList(0, 5)) assertRefused(playing, actor, command);
                if (actor != turn) assertRefused(playing, actor, new GameCommand.Fire(new Coordinate(0, 0)));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void playingOffersResignationInsteadOfLeaveForBothSeats(String id) {
        for (Seat turn : Seat.values()) {
            GameState playing = inPhase(arranged(id), Phase.PLAYING, turn);
            for (Seat actor : Seat.values()) {
                assertThat(AllowedActions.of(playing, actor))
                        .contains(AllowedActions.Action.RESIGN)
                        .doesNotContain(AllowedActions.Action.LEAVE);
            }
        }
    }

    private static void assertRefused(GameState before, Seat actor, GameCommand command) {
        Transition result = GameRules.apply(before, actor, command, NOW, bound -> {
            throw new AssertionError("A refused command must not consume randomness");
        });
        assertThat(result.rejection())
                .as("%s / %s / %s", before.phase(), actor, command)
                .isNotNull();
        assertThat(result.rejection().code()).isEqualTo(ProblemCode.ACTION_NOT_ALLOWED);
        assertThat(result.next()).isSameAs(before);
        assertThat(result.next().version()).isEqualTo(before.version());
        assertThat(result.versionBumped()).isFalse();
        assertThat(result.viewChanged()).isEmpty();
    }

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private static GameState joined(String id) {
        return GameState.create(Rulesets.byId(id).orElseThrow(), "Host").withGuest("Guest", NOW);
    }

    private static GameState arranged(String id) {
        GameState state = joined(id);
        for (Seat seat : Seat.values()) {
            Transition result = GameRules.apply(
                    state,
                    seat,
                    new GameCommand.PlaceFleetRandomly(10000),
                    NOW,
                    new SeededRandomSource(seat == Seat.HOST ? 42 : 43));
            assertThat(result.rejection()).isNull();
            state = result.next();
        }
        return state;
    }

    private static GameState inPhase(GameState state, Phase phase, Seat turn) {
        return new GameState(
                state.rulesetId(),
                phase,
                state.version(),
                state.host(),
                state.guest(),
                turn,
                state.lastShot(),
                state.outcome(),
                state.timeline());
    }
}
