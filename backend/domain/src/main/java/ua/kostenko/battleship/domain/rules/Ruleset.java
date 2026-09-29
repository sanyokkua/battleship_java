package ua.kostenko.battleship.domain.rules;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record Ruleset(
        String id,
        int rows,
        int columns,
        List<FleetEntry> fleet,
        boolean shipsMayTouch,
        boolean extraTurnOnHit,
        boolean revealWaterAroundSunk) {
    private static final Pattern RULESET_ID = Pattern.compile("^[a-z0-9-]+\\.v[0-9]+$");

    public Ruleset {
        Objects.requireNonNull(id, "id");
        if (!RULESET_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match [a-z0-9-]+\\.v[0-9]+");
        }
        if (rows < 1 || rows > 100 || columns < 1 || columns > 100) {
            throw new IllegalArgumentException("board dimensions must be between 1 and 100");
        }
        fleet = List.copyOf(Objects.requireNonNull(fleet, "fleet"));
    }
}
