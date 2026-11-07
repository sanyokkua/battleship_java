package ua.kostenko.battleship.domain.rules;

import java.util.Objects;
import java.util.Set;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;

public final class AllowedActions {
    private AllowedActions() {}

    public enum Action {
        PLACE_SHIP,
        REMOVE_SHIP,
        PLACE_FLEET_RANDOMLY,
        CLEAR_FLEET,
        READY,
        FIRE,
        RESIGN,
        NEW_INVITATION,
        SEND_PRESENCE,
        LEAVE
    }

    public static Set<Action> of(GameState state, Seat seat) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(seat, "seat");
        PlayerState player = seat == Seat.HOST ? state.host() : state.guest();
        if (player == null) return Set.of();
        return switch (state.phase()) {
            case WAITING ->
                seat == Seat.HOST ? Set.of(Action.NEW_INVITATION, Action.SEND_PRESENCE, Action.LEAVE) : Set.of();
            case PLACEMENT -> {
                if (player.ready()) yield Set.of(Action.SEND_PRESENCE, Action.LEAVE);
                boolean complete = !player.board().fleet().isEmpty()
                        && player.board().fleet().stream().allMatch(ship -> ship.anchor() != null);
                yield complete
                        ? Set.of(
                                Action.PLACE_SHIP,
                                Action.REMOVE_SHIP,
                                Action.PLACE_FLEET_RANDOMLY,
                                Action.CLEAR_FLEET,
                                Action.READY,
                                Action.SEND_PRESENCE,
                                Action.LEAVE)
                        : Set.of(
                                Action.PLACE_SHIP,
                                Action.REMOVE_SHIP,
                                Action.PLACE_FLEET_RANDOMLY,
                                Action.CLEAR_FLEET,
                                Action.SEND_PRESENCE,
                                Action.LEAVE);
            }
            case PLAYING ->
                state.turn() == seat
                        ? Set.of(Action.FIRE, Action.RESIGN, Action.SEND_PRESENCE)
                        : Set.of(Action.RESIGN, Action.SEND_PRESENCE);
            case FINISHED, ABANDONED -> Set.of(Action.LEAVE);
        };
    }
}
