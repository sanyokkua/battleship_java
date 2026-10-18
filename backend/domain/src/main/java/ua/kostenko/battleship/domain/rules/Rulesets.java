package ua.kostenko.battleship.domain.rules;

import java.util.List;
import java.util.Optional;

public final class Rulesets {
    private static final List<Ruleset> ALL = List.of(
            new Ruleset(
                    "sea-battle-10-ship.v1",
                    10,
                    10,
                    List.of(
                            new FleetEntry("ship-4", 4, 1),
                            new FleetEntry("ship-3", 3, 2),
                            new FleetEntry("ship-2", 2, 3),
                            new FleetEntry("ship-1", 1, 4)),
                    false,
                    true,
                    true),
            new Ruleset(
                    "hasbro-classic-2002.v1",
                    10,
                    10,
                    List.of(
                            new FleetEntry("carrier", 5, 1),
                            new FleetEntry("battleship", 4, 1),
                            new FleetEntry("destroyer", 3, 1),
                            new FleetEntry("submarine", 3, 1),
                            new FleetEntry("patrol-boat", 2, 1)),
                    true,
                    false,
                    false));

    private Rulesets() {}

    public static List<Ruleset> all() {
        return ALL;
    }

    public static Optional<Ruleset> byId(String id) {
        return ALL.stream().filter(ruleset -> ruleset.id().equals(id)).findFirst();
    }
}
