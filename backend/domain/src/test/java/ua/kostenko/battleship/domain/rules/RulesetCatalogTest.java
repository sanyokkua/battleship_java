package ua.kostenko.battleship.domain.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

class RulesetCatalogTest {

    private static final Pattern RULESET_ID = Pattern.compile("^[a-z0-9-]+\\.v[0-9]+$");
    private static final Pattern SHIP_TYPE_ID = Pattern.compile("^[a-z0-9-]{2,32}$");

    @Test
    void publishesExactlyTheTwoR05RulesetsWithImmutableCompleteData() {
        List<Ruleset> rulesets = Rulesets.all();

        assertEquals(2, rulesets.size());

        Ruleset seaBattle = rulesets.get(0);
        assertEquals("sea-battle-10-ship.v1", seaBattle.id());
        assertEquals(10, seaBattle.rows());
        assertEquals(10, seaBattle.columns());
        assertEquals(
                List.of(
                        new FleetEntry("ship-4", 4, 1),
                        new FleetEntry("ship-3", 3, 2),
                        new FleetEntry("ship-2", 2, 3),
                        new FleetEntry("ship-1", 1, 4)),
                seaBattle.fleet());
        assertFalse(seaBattle.shipsMayTouch());
        assertTrue(seaBattle.extraTurnOnHit());
        assertTrue(seaBattle.revealWaterAroundSunk());

        Ruleset hasbro = rulesets.get(1);
        assertEquals("hasbro-classic-2002.v1", hasbro.id());
        assertEquals(10, hasbro.rows());
        assertEquals(10, hasbro.columns());
        assertEquals(
                List.of(
                        new FleetEntry("carrier", 5, 1),
                        new FleetEntry("battleship", 4, 1),
                        new FleetEntry("destroyer", 3, 1),
                        new FleetEntry("submarine", 3, 1),
                        new FleetEntry("patrol-boat", 2, 1)),
                hasbro.fleet());
        assertFalse(seaBattle.shipsMayTouch() == hasbro.shipsMayTouch());
        assertFalse(seaBattle.extraTurnOnHit() == hasbro.extraTurnOnHit());
        assertFalse(seaBattle.revealWaterAroundSunk() == hasbro.revealWaterAroundSunk());
        assertTrue(hasbro.shipsMayTouch());
        assertFalse(hasbro.extraTurnOnHit());
        assertFalse(hasbro.revealWaterAroundSunk());

        for (Ruleset ruleset : rulesets) {
            assertTrue(RULESET_ID.matcher(ruleset.id()).matches());
            for (FleetEntry entry : ruleset.fleet()) {
                assertTrue(SHIP_TYPE_ID.matcher(entry.shipTypeId()).matches());
                assertTrue(entry.length() > 0);
                assertTrue(entry.count() > 0);
            }
            assertEquals(ruleset, Rulesets.byId(ruleset.id()).orElseThrow());
        }

        assertThrows(
                UnsupportedOperationException.class, () -> seaBattle.fleet().add(new FleetEntry("extra", 1, 1)));
        assertThrows(UnsupportedOperationException.class, () -> Rulesets.all().clear());
    }

    @Test
    void definesBoardPrimitiveValuesAndRejectsNegativeCoordinates() {
        Coordinate coordinate = new Coordinate(2, 7);

        assertEquals(2, coordinate.rowIndex());
        assertEquals(7, coordinate.columnIndex());
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, -1));
        assertEquals(List.of(Orientation.HORIZONTAL, Orientation.VERTICAL), List.of(Orientation.values()));
        assertEquals(List.of(Seat.HOST, Seat.GUEST), List.of(Seat.values()));
        assertEquals(
                List.of(Phase.WAITING, Phase.PLACEMENT, Phase.PLAYING, Phase.FINISHED, Phase.ABANDONED),
                List.of(Phase.values()));
    }

    @Test
    void validatesFleetEntriesAndRulesetsAndDefensivelyCopiesFleet() {
        assertThrows(IllegalArgumentException.class, () -> new FleetEntry("x", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FleetEntry("invalid_type", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FleetEntry("ship-1", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FleetEntry("ship-1", 1, 0));

        List<FleetEntry> sourceFleet = new ArrayList<>(List.of(new FleetEntry("ship-1", 1, 1)));
        Ruleset ruleset = new Ruleset("test-rules.v1", 1, 100, sourceFleet, false, false, false);
        sourceFleet.add(new FleetEntry("ship-2", 2, 1));
        assertEquals(List.of(new FleetEntry("ship-1", 1, 1)), ruleset.fleet());
        assertThrows(UnsupportedOperationException.class, () -> ruleset.fleet().add(new FleetEntry("ship-2", 2, 1)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Ruleset("invalid_id", 10, 10, List.of(), false, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Ruleset("test-rules.v1", 0, 10, List.of(), false, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Ruleset("test-rules.v1", 10, 101, List.of(), false, false, false));
    }
}
