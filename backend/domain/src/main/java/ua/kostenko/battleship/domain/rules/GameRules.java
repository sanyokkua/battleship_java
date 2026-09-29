package ua.kostenko.battleship.domain.rules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Board;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.model.Ship;
import ua.kostenko.battleship.domain.transition.Rejection;
import ua.kostenko.battleship.domain.transition.Rejection.ProblemCode;
import ua.kostenko.battleship.domain.transition.Transition;

public final class GameRules {
    private GameRules() {}

    public static Transition apply(GameState state, Seat actor, GameCommand command, Instant now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        return switch (command) {
            case GameCommand.PlaceShip placeShip -> placeShip(state, actor, placeShip);
            case GameCommand.RemoveShip removeShip -> removeShip(state, actor, removeShip);
        };
    }

    private static Transition placeShip(GameState state, Seat actor, GameCommand.PlaceShip command) {
        Ruleset ruleset = Rulesets.byId(state.rulesetId()).orElseThrow();
        if (command.anchor().rowIndex() >= ruleset.rows()) {
            return refused(state, Rejection.validation("/command/anchor/rowIndex", "OUT_OF_RANGE"));
        }
        if (command.anchor().columnIndex() >= ruleset.columns()) {
            return refused(state, Rejection.validation("/command/anchor/columnIndex", "OUT_OF_RANGE"));
        }

        PlayerState player = player(state, actor);
        List<Ship> fleet = player.board().fleet();
        int shipIndex = indexOf(fleet, command.shipId());
        if (shipIndex < 0) {
            return refused(state, Rejection.validation("/command/shipId", "UNKNOWN_VALUE"));
        }

        Ship moving = fleet.get(shipIndex);
        if (command.anchor().equals(moving.anchor()) && command.orientation() == moving.orientation()) {
            return acceptedNoOp(state);
        }
        Ship placed = new Ship(
                moving.shipId(),
                moving.shipTypeId(),
                moving.length(),
                command.anchor(),
                command.orientation(),
                moving.hits());
        if (placed.cells().stream()
                .anyMatch(cell -> cell.rowIndex() >= ruleset.rows() || cell.columnIndex() >= ruleset.columns())) {
            return refused(state, Rejection.problem(ProblemCode.PLACEMENT_OUT_OF_BOUNDS));
        }

        for (int index = 0; index < fleet.size(); index++) {
            if (index == shipIndex || fleet.get(index).anchor() == null) {
                continue;
            }
            Ship other = fleet.get(index);
            if (overlaps(placed, other)) {
                return refused(state, Rejection.problem(ProblemCode.PLACEMENT_OVERLAP));
            }
            if (!ruleset.shipsMayTouch() && touches(placed, other)) {
                return refused(state, Rejection.problem(ProblemCode.PLACEMENT_TOUCHING));
            }
        }

        List<Ship> updatedFleet = new ArrayList<>(fleet);
        updatedFleet.set(shipIndex, placed);
        return accepted(state, actor, playerWithFleet(player, updatedFleet));
    }

    private static Transition removeShip(GameState state, Seat actor, GameCommand.RemoveShip command) {
        PlayerState player = player(state, actor);
        List<Ship> fleet = player.board().fleet();
        int shipIndex = indexOf(fleet, command.shipId());
        if (shipIndex < 0) {
            return refused(state, Rejection.validation("/command/shipId", "UNKNOWN_VALUE"));
        }
        Ship existing = fleet.get(shipIndex);
        if (existing.anchor() == null) {
            return acceptedNoOp(state);
        }

        List<Ship> updatedFleet = new ArrayList<>(fleet);
        updatedFleet.set(
                shipIndex, new Ship(existing.shipId(), existing.shipTypeId(), existing.length(), null, null, Set.of()));
        return accepted(state, actor, playerWithFleet(player, updatedFleet));
    }

    private static boolean overlaps(Ship first, Ship second) {
        return first.cells().stream().anyMatch(second.cells()::contains);
    }

    private static boolean touches(Ship first, Ship second) {
        return first.cells().stream().anyMatch(a -> second.cells().stream()
                .anyMatch(b -> Math.abs(a.rowIndex() - b.rowIndex()) <= 1
                        && Math.abs(a.columnIndex() - b.columnIndex()) <= 1));
    }

    private static int indexOf(List<Ship> fleet, String shipId) {
        for (int index = 0; index < fleet.size(); index++) {
            if (fleet.get(index).shipId().equals(shipId)) {
                return index;
            }
        }
        return -1;
    }

    private static PlayerState player(GameState state, Seat seat) {
        return switch (seat) {
            case HOST -> state.host();
            case GUEST -> state.guest() == null ? null : state.guest();
        };
    }

    private static PlayerState playerWithFleet(PlayerState player, List<Ship> fleet) {
        return new PlayerState(
                player.displayName(),
                player.ready(),
                new Board(fleet, player.board().incomingShots(), player.board().revealedWater()),
                player.shotsFired());
    }

    private static Transition accepted(GameState state, Seat actor, PlayerState replacement) {
        PlayerState host = actor == Seat.HOST ? replacement : state.host();
        PlayerState guest = actor == Seat.GUEST ? replacement : state.guest();
        GameState next = new GameState(
                state.rulesetId(),
                state.phase(),
                state.version() + 1,
                host,
                guest,
                state.turn(),
                state.lastShot(),
                state.outcome(),
                state.timeline());
        return new Transition(next, true, Set.of(actor), null);
    }

    private static Transition acceptedNoOp(GameState state) {
        return new Transition(state, false, Set.of(), null);
    }

    private static Transition refused(GameState state, Rejection rejection) {
        return new Transition(state, false, Set.of(), rejection);
    }
}
