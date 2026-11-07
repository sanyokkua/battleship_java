package ua.kostenko.battleship.domain.model;

import java.util.Objects;

public record Shot(Seat by, Coordinate target, ShotResult result, String sunkShipId) {
    public Shot {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(result, "result");
        if ((result == ShotResult.SUNK) != (sunkShipId != null)) {
            throw new IllegalArgumentException("sunkShipId must be present exactly when result is SUNK");
        }
    }
}
