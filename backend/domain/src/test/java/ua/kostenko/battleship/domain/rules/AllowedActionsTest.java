package ua.kostenko.battleship.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.CLEAR_FLEET;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.FIRE;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.LEAVE;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.NEW_INVITATION;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.PLACE_FLEET_RANDOMLY;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.PLACE_SHIP;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.READY;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.REMOVE_SHIP;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.RESIGN;
import static ua.kostenko.battleship.domain.rules.AllowedActions.Action.SEND_PRESENCE;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.transition.Transition;

class AllowedActionsTest {
    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void offersExactlyTheSevenPermissionRowsForBothSeats(String id) {
        GameState waiting = GameState.create(Rulesets.byId(id).orElseThrow(), "Host");
        assertThat(AllowedActions.of(waiting, Seat.HOST)).isEqualTo(Set.of(NEW_INVITATION, SEND_PRESENCE, LEAVE));
        assertThat(AllowedActions.of(waiting, Seat.GUEST)).isEmpty();
        GameState joined = joined(id);
        GameState arranged = arranged(id);
        for (Seat actor : Seat.values()) {
            assertThat(AllowedActions.of(joined, actor))
                    .isEqualTo(
                            Set.of(PLACE_SHIP, REMOVE_SHIP, PLACE_FLEET_RANDOMLY, CLEAR_FLEET, SEND_PRESENCE, LEAVE));
            GameState partial = GameRules.apply(
                            joined,
                            actor,
                            new GameCommand.PlaceShip("s01", new Coordinate(0, 0), Orientation.HORIZONTAL),
                            NOW,
                            new SeededRandomSource(42))
                    .next();
            assertThat(AllowedActions.of(partial, actor))
                    .isEqualTo(
                            Set.of(PLACE_SHIP, REMOVE_SHIP, PLACE_FLEET_RANDOMLY, CLEAR_FLEET, SEND_PRESENCE, LEAVE));
            assertThat(AllowedActions.of(arranged, actor))
                    .isEqualTo(Set.of(
                            PLACE_SHIP, REMOVE_SHIP, PLACE_FLEET_RANDOMLY, CLEAR_FLEET, READY, SEND_PRESENCE, LEAVE));
            GameState ready = GameRules.apply(arranged, actor, new GameCommand.Ready(), NOW, new SeededRandomSource(42))
                    .next();
            assertThat(AllowedActions.of(ready, actor)).isEqualTo(Set.of(SEND_PRESENCE, LEAVE));
            Seat other = actor == Seat.HOST ? Seat.GUEST : Seat.HOST;
            assertThat(AllowedActions.of(ready, other))
                    .isEqualTo(Set.of(
                            PLACE_SHIP, REMOVE_SHIP, PLACE_FLEET_RANDOMLY, CLEAR_FLEET, READY, SEND_PRESENCE, LEAVE));
            for (Seat turn : Seat.values()) {
                GameState playing = inPhase(arranged, Phase.PLAYING, turn);
                assertThat(AllowedActions.of(playing, actor))
                        .isEqualTo(actor == turn ? Set.of(FIRE, RESIGN, SEND_PRESENCE) : Set.of(RESIGN, SEND_PRESENCE));
            }
            for (Phase phase : List.of(Phase.FINISHED, Phase.ABANDONED)) {
                assertThat(AllowedActions.of(inPhase(arranged, phase, null), actor))
                        .isEqualTo(Set.of(LEAVE));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void emptyFleetDoesNotOfferReadyByVacuousCompleteness(String id) {
        GameState joined = joined(id);
        for (Seat actor : Seat.values()) {
            PlayerState empty = PlayerState.empty("Empty");
            GameState state = new GameState(
                    id,
                    Phase.PLACEMENT,
                    joined.version(),
                    actor == Seat.HOST ? empty : joined.host(),
                    actor == Seat.GUEST ? empty : joined.guest(),
                    null,
                    null,
                    null,
                    joined.timeline());
            assertThat(AllowedActions.of(state, actor))
                    .isEqualTo(
                            Set.of(PLACE_SHIP, REMOVE_SHIP, PLACE_FLEET_RANDOMLY, CLEAR_FLEET, SEND_PRESENCE, LEAVE));
        }
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
