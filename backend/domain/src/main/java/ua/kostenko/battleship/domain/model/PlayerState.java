package ua.kostenko.battleship.domain.model;

import java.util.List;
import java.util.Objects;

public record PlayerState(String displayName, boolean ready, Board board, List<Shot> shotsFired) {
    public PlayerState {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(board, "board");
        shotsFired = List.copyOf(Objects.requireNonNull(shotsFired, "shotsFired"));
    }

    public static PlayerState empty(String displayName) {
        return new PlayerState(displayName, false, Board.empty(), List.of());
    }
}
