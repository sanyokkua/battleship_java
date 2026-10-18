package ua.kostenko.battleship.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record Board(List<Ship> fleet, Set<Coordinate> incomingShots, Set<Coordinate> revealedWater) {
    public Board {
        fleet = List.copyOf(Objects.requireNonNull(fleet, "fleet"));
        incomingShots = Set.copyOf(Objects.requireNonNull(incomingShots, "incomingShots"));
        revealedWater = Set.copyOf(Objects.requireNonNull(revealedWater, "revealedWater"));
    }

    public static Board empty() {
        return new Board(List.of(), Set.of(), Set.of());
    }
}
