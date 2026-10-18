package ua.kostenko.battleship.domain.rules;

import java.util.Objects;
import java.util.regex.Pattern;

public record FleetEntry(String shipTypeId, int length, int count) {
    private static final Pattern SHIP_TYPE_ID = Pattern.compile("^[a-z0-9-]{2,32}$");

    public FleetEntry {
        Objects.requireNonNull(shipTypeId, "shipTypeId");
        if (!SHIP_TYPE_ID.matcher(shipTypeId).matches()) {
            throw new IllegalArgumentException("shipTypeId must match [a-z0-9-]{2,32}");
        }
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
