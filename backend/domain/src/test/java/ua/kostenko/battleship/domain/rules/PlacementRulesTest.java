package ua.kostenko.battleship.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.transition.Rejection.ProblemCode;
import ua.kostenko.battleship.domain.transition.Transition;

class PlacementRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Test
    void placementChangesOnlyActorsViewAndIncrementsVersion() {
        GameState before = state("sea-battle-10-ship.v1");

        Transition result =
                apply(before, Seat.HOST, new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL));

        assertThat(result.rejection()).isNull();
        assertThat(result.next().host().board().fleet().getFirst().cells())
                .containsExactly(at(0, 0), at(0, 1), at(0, 2), at(0, 3));
        assertThat(result.next().guest()).isEqualTo(before.guest());
        assertThat(result.next().version()).isEqualTo(before.version() + 1);
        assertThat(result.versionBumped()).isTrue();
        assertThat(result.viewChanged()).containsExactly(Seat.HOST);
    }

    @Test
    void relocationIsAtomicAndFreesOldCells() {
        GameState before = withFleet(
                "sea-battle-10-ship.v1",
                List.of(
                        new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL),
                        new GameCommand.PlaceShip("s02", at(2, 0), Orientation.HORIZONTAL)));

        Transition result =
                apply(before, Seat.HOST, new GameCommand.PlaceShip("s01", at(4, 0), Orientation.HORIZONTAL));

        assertThat(result.rejection()).isNull();
        assertThat(ship(result.next(), "s01").cells()).containsExactly(at(4, 0), at(4, 1), at(4, 2), at(4, 3));
        assertThat(ship(result.next(), "s02").cells()).containsExactly(at(2, 0), at(2, 1), at(2, 2));
        assertThat(result.next().version()).isEqualTo(before.version() + 1);
    }

    @Test
    void placingAtCurrentLocationAndRemovingUnplacedShipAreAcceptedNoOps() {
        GameState placed = withFleet(
                "sea-battle-10-ship.v1", List.of(new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL)));

        Transition placeAgain =
                apply(placed, Seat.HOST, new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL));
        Transition removeUnplaced = apply(placed, Seat.HOST, new GameCommand.RemoveShip("s02"));

        assertNoOp(placeAgain, placed);
        assertNoOp(removeUnplaced, placed);
    }

    @Test
    void removingPlacedShipClearsItAndChangesOnlyActorsView() {
        GameState before = apply(
                        state("sea-battle-10-ship.v1"),
                        Seat.GUEST,
                        new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL))
                .next();

        Transition result = apply(before, Seat.GUEST, new GameCommand.RemoveShip("s01"));

        assertThat(result.rejection()).isNull();
        assertThat(ship(result.next(), Seat.GUEST, "s01").anchor()).isNull();
        assertThat(ship(result.next(), Seat.GUEST, "s01").orientation()).isNull();
        assertThat(ship(result.next(), Seat.GUEST, "s01").cells()).isEmpty();
        assertThat(result.next().version()).isEqualTo(before.version() + 1);
        assertThat(result.versionBumped()).isTrue();
        assertThat(result.viewChanged()).containsExactly(Seat.GUEST);
        assertThat(result.next().host()).isEqualTo(before.host());
    }

    @Test
    void coordinateOutsideBoardIsValidationFailureWithPointerAndRule() {
        GameState before = state("sea-battle-10-ship.v1");

        Transition result =
                apply(before, Seat.HOST, new GameCommand.PlaceShip("s01", at(10, 0), Orientation.HORIZONTAL));

        assertRefused(result, before, ProblemCode.VALIDATION_FAILED, "/command/anchor/rowIndex", "OUT_OF_RANGE");
    }

    @Test
    void unknownShipIsValidationFailureWithPointerAndRule() {
        GameState before = state("sea-battle-10-ship.v1");

        Transition result =
                apply(before, Seat.HOST, new GameCommand.PlaceShip("missing", at(0, 0), Orientation.HORIZONTAL));

        assertRefused(result, before, ProblemCode.VALIDATION_FAILED, "/command/shipId", "UNKNOWN_VALUE");
    }

    @Test
    void shipExtendingPastBoardIsPlacementOutOfBounds() {
        GameState before = state("sea-battle-10-ship.v1");

        Transition result =
                apply(before, Seat.HOST, new GameCommand.PlaceShip("s01", at(9, 8), Orientation.HORIZONTAL));

        assertRefused(result, before, ProblemCode.PLACEMENT_OUT_OF_BOUNDS, null, null);
    }

    @Test
    void overlapHasItsOwnRefusalCode() {
        GameState before = withFleet(
                "sea-battle-10-ship.v1", List.of(new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL)));

        Transition result =
                apply(before, Seat.HOST, new GameCommand.PlaceShip("s02", at(0, 2), Orientation.HORIZONTAL));

        assertRefused(result, before, ProblemCode.PLACEMENT_OVERLAP, null, null);
    }

    @Test
    void diagonalTouchIsRejectedOnlyWhenRulesetForbidsTouching() {
        GameState seaBattle = withFleet(
                "sea-battle-10-ship.v1", List.of(new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL)));
        GameState hasbro = withFleet(
                "hasbro-classic-2002.v1", List.of(new GameCommand.PlaceShip("s01", at(0, 0), Orientation.HORIZONTAL)));

        Transition refused =
                apply(seaBattle, Seat.HOST, new GameCommand.PlaceShip("s02", at(1, 4), Orientation.HORIZONTAL));
        Transition accepted =
                apply(hasbro, Seat.HOST, new GameCommand.PlaceShip("s02", at(1, 4), Orientation.HORIZONTAL));

        assertRefused(refused, seaBattle, ProblemCode.PLACEMENT_TOUCHING, null, null);
        assertThat(accepted.rejection()).isNull();
        assertThat(accepted.viewChanged()).containsExactly(Seat.HOST);
    }

    private static Transition apply(GameState state, Seat actor, GameCommand command) {
        return GameRules.apply(state, actor, command, NOW);
    }

    private static GameState state(String rulesetId) {
        return GameState.create(Rulesets.byId(rulesetId).orElseThrow(), "Host").withGuest("Guest", NOW);
    }

    private static GameState withFleet(String rulesetId, List<GameCommand> commands) {
        GameState state = state(rulesetId);
        for (GameCommand command : commands) {
            state = apply(state, Seat.HOST, command).next();
        }
        return state;
    }

    private static ua.kostenko.battleship.domain.model.Ship ship(GameState state, String shipId) {
        return state.host().board().fleet().stream()
                .filter(ship -> ship.shipId().equals(shipId))
                .findFirst()
                .orElseThrow();
    }

    private static ua.kostenko.battleship.domain.model.Ship ship(GameState state, Seat seat, String shipId) {
        var player = seat == Seat.HOST ? state.host() : state.guest();
        return player.board().fleet().stream()
                .filter(ship -> ship.shipId().equals(shipId))
                .findFirst()
                .orElseThrow();
    }

    private static Coordinate at(int row, int column) {
        return new Coordinate(row, column);
    }

    private static void assertNoOp(Transition transition, GameState before) {
        assertThat(transition.rejection()).isNull();
        assertThat(transition.next().version()).isEqualTo(before.version());
        assertThat(transition.versionBumped()).isFalse();
        assertThat(transition.viewChanged()).isEmpty();
    }

    private static void assertRefused(
            Transition transition, GameState before, ProblemCode code, String field, String rule) {
        assertThat(transition.next()).isSameAs(before);
        assertThat(transition.next().version()).isEqualTo(before.version());
        assertThat(transition.versionBumped()).isFalse();
        assertThat(transition.viewChanged()).isEmpty();
        assertThat(transition.rejection()).isNotNull();
        assertThat(transition.rejection().code()).isEqualTo(code);
        assertThat(transition.rejection().field()).isEqualTo(field);
        assertThat(transition.rejection().rule()).isEqualTo(rule);
    }
}
