package ua.kostenko.battleship.domain.transition;

import java.util.Objects;
import java.util.Set;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;

public record Transition(GameState next, boolean versionBumped, Set<Seat> viewChanged, Rejection rejection) {
    public Transition {
        Objects.requireNonNull(next, "next");
        viewChanged = Set.copyOf(Objects.requireNonNull(viewChanged, "viewChanged"));
        if (versionBumped != !viewChanged.isEmpty()) {
            throw new IllegalArgumentException("versionBumped must match whether a player view changed");
        }
    }
}
