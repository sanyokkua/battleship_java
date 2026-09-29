package ua.kostenko.battleship.domain.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record Ship(
        String shipId,
        String shipTypeId,
        int length,
        Coordinate anchor,
        Orientation orientation,
        Set<Coordinate> hits) {
    public Ship {
        Objects.requireNonNull(shipId, "shipId");
        Objects.requireNonNull(shipTypeId, "shipTypeId");
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        if ((anchor == null) != (orientation == null)) {
            throw new IllegalArgumentException("anchor and orientation must both be present or absent");
        }
        hits = Set.copyOf(Objects.requireNonNull(hits, "hits"));
        if (!cells(anchor, orientation, length).containsAll(hits)) {
            throw new IllegalArgumentException("hits must be cells occupied by the ship");
        }
    }

    public Set<Coordinate> cells() {
        return cells(anchor, orientation, length);
    }

    private static Set<Coordinate> cells(Coordinate anchor, Orientation orientation, int length) {
        if (anchor == null) {
            return Set.of();
        }
        Set<Coordinate> cells = new LinkedHashSet<>();
        for (int offset = 0; offset < length; offset++) {
            int row = anchor.rowIndex() + (orientation == Orientation.VERTICAL ? offset : 0);
            int column = anchor.columnIndex() + (orientation == Orientation.HORIZONTAL ? offset : 0);
            cells.add(new Coordinate(row, column));
        }
        return java.util.Collections.unmodifiableSet(cells);
    }

    public ShipStatus status() {
        Set<Coordinate> cells = cells();
        if (hits.isEmpty()) {
            return ShipStatus.INTACT;
        }
        return !cells.isEmpty() && hits.containsAll(cells) ? ShipStatus.SUNK : ShipStatus.DAMAGED;
    }

    public enum ShipStatus {
        INTACT,
        DAMAGED,
        SUNK
    }
}
