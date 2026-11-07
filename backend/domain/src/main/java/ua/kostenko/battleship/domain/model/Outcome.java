package ua.kostenko.battleship.domain.model;

import java.util.Objects;

public record Outcome(Seat winner, Reason reason) {
    public Outcome {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(reason, "reason");
    }

    public enum Reason {
        FLEET_DESTROYED,
        RESIGNATION
    }
}
