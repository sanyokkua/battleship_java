package ua.kostenko.battleship.battleship.logic.engine.config;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameEditionConfigurationTest {

    @Test
    void testGetClassicConfig() {
        var result = GameEditionConfiguration.getConfiguration(GameEdition.UKRAINIAN);

        final var correctShips =
                Set.of(ShipType.PATROL_BOAT, ShipType.SUBMARINE, ShipType.DESTROYER, ShipType.BATTLESHIP);

        assertTrue(result.stream()
                         .map(ShipConfiguration::shipType)
                         .allMatch(correctShips::contains));
        assertEquals(1,
                     result.stream()
                           .filter(s -> ShipType.PATROL_BOAT.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));
        assertEquals(2,
                     result.stream()
                           .filter(s -> ShipType.SUBMARINE.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));
        assertEquals(3,
                     result.stream()
                           .filter(s -> ShipType.DESTROYER.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));
        assertEquals(4,
                     result.stream()
                           .filter(s -> ShipType.BATTLESHIP.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));

        assertEquals(4,
                     result.stream()
                           .filter(s -> ShipType.PATROL_BOAT.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));
        assertEquals(3,
                     result.stream()
                           .filter(s -> ShipType.SUBMARINE.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));
        assertEquals(2,
                     result.stream()
                           .filter(s -> ShipType.DESTROYER.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));
        assertEquals(1,
                     result.stream()
                           .filter(s -> ShipType.BATTLESHIP.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));

    }

    @Test
    void testGetCustomConfig() {
        var result = GameEditionConfiguration.getConfiguration(GameEdition.MILTON_BRADLEY);

        final var correctShips = Set.of(ShipType.SUBMARINE, ShipType.DESTROYER, ShipType.BATTLESHIP, ShipType.CARRIER);

        assertTrue(result.stream()
                         .map(ShipConfiguration::shipType)
                         .allMatch(correctShips::contains));
        assertEquals(2,
                     result.stream()
                           .filter(s -> ShipType.SUBMARINE.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));
        assertEquals(3,
                     result.stream()
                           .filter(s -> ShipType.DESTROYER.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));
        assertEquals(4,
                     result.stream()
                           .filter(s -> ShipType.BATTLESHIP.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));
        assertEquals(5,
                     result.stream()
                           .filter(s -> ShipType.CARRIER.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipSize)
                           .orElse(-1));

        assertEquals(4,
                     result.stream()
                           .filter(s -> ShipType.SUBMARINE.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));
        assertEquals(3,
                     result.stream()
                           .filter(s -> ShipType.DESTROYER.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));
        assertEquals(2,
                     result.stream()
                           .filter(s -> ShipType.BATTLESHIP.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));
        assertEquals(1,
                     result.stream()
                           .filter(s -> ShipType.CARRIER.equals(s.shipType()))
                           .findAny()
                           .map(ShipConfiguration::shipAmount)
                           .orElse(-1));

    }

    @Test
    void testGameConfigMerging() {
        var config = new GameEditionConfiguration() {

            @Override
            protected Map<ShipType, Integer> getSizeMapping() {
                return Map.of(ShipType.PATROL_BOAT,
                              1,
                              ShipType.SUBMARINE,
                              2,
                              ShipType.DESTROYER,
                              3,
                              ShipType.BATTLESHIP,
                              4);
            }

            @Override
            protected Map<ShipType, Integer> getAmountMapping() {
                return Map.of(ShipType.SUBMARINE,
                              4,
                              ShipType.DESTROYER,
                              3,
                              ShipType.BATTLESHIP,
                              2,
                              ShipType.CARRIER,
                              1);
            }
        };
        assertThrows(IllegalArgumentException.class, config::getShipConfigs);
    }
}
