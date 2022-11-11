package ua.kostenko.battleship.battleship.engine.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipUtilTest {

    @Test
    void testGenerateShips() {
        final Set<Ship> shipsClassic = ShipUtil.generateShips(GameEdition.UKRAINIAN);
        assertEquals(10, shipsClassic.size());
        assertTrue(shipsClassic.stream().anyMatch(s -> ShipType.PATROL_BOAT.equals(s.shipType())));
        assertTrue(shipsClassic.stream().anyMatch(s -> ShipType.SUBMARINE.equals(s.shipType())));
        assertTrue(shipsClassic.stream().anyMatch(s -> ShipType.DESTROYER.equals(s.shipType())));
        assertTrue(shipsClassic.stream().anyMatch(s -> ShipType.BATTLESHIP.equals(s.shipType())));

        assertTrue(shipsClassic.stream().noneMatch(s -> ShipType.CARRIER.equals(s.shipType())));

        assertEquals(4, shipsClassic.stream()
                                    .filter(ship -> ShipType.PATROL_BOAT.equals(ship.shipType()))
                                    .count());
        assertEquals(3, shipsClassic.stream()
                                    .filter(ship -> ShipType.SUBMARINE.equals(ship.shipType()))
                                    .count());
        assertEquals(2, shipsClassic.stream()
                                    .filter(ship -> ShipType.DESTROYER.equals(ship.shipType()))
                                    .count());
        assertEquals(1, shipsClassic.stream()
                                    .filter(ship -> ShipType.BATTLESHIP.equals(ship.shipType()))
                                    .count());

        assertEquals(4, shipsClassic.stream()
                                    .filter(ship -> ShipType.PATROL_BOAT.equals(ship.shipType()))
                                    .map(Ship::shipSize)
                                    .filter(size -> size == 1)
                                    .count());
        assertEquals(3, shipsClassic.stream()
                                    .filter(ship -> ShipType.SUBMARINE.equals(ship.shipType()))
                                    .map(Ship::shipSize)
                                    .filter(size -> size == 2)
                                    .count());
        assertEquals(2, shipsClassic.stream()
                                    .filter(ship -> ShipType.DESTROYER.equals(ship.shipType()))
                                    .map(Ship::shipSize)
                                    .filter(size -> size == 3)
                                    .count());
        assertEquals(1, shipsClassic.stream()
                                    .filter(ship -> ShipType.BATTLESHIP.equals(ship.shipType()))
                                    .map(Ship::shipSize)
                                    .filter(size -> size == 4)
                                    .count());

        assertTrue(shipsClassic.stream().allMatch(s -> StringUtils.isNotBlank(s.shipId())));

        final Set<Ship> shipsCustom = ShipUtil.generateShips(GameEdition.MILTON_BRADLEY);
        assertEquals(10, shipsCustom.size());
        assertTrue(shipsCustom.stream().anyMatch(s -> ShipType.SUBMARINE.equals(s.shipType())));
        assertTrue(shipsCustom.stream().anyMatch(s -> ShipType.DESTROYER.equals(s.shipType())));
        assertTrue(shipsCustom.stream().anyMatch(s -> ShipType.BATTLESHIP.equals(s.shipType())));
        assertTrue(shipsCustom.stream().anyMatch(s -> ShipType.CARRIER.equals(s.shipType())));

        assertTrue(shipsCustom.stream().noneMatch(s -> ShipType.PATROL_BOAT.equals(s.shipType())));

        assertEquals(4, shipsCustom.stream()
                                   .filter(ship -> ShipType.SUBMARINE.equals(ship.shipType()))
                                   .count());
        assertEquals(3, shipsCustom.stream()
                                   .filter(ship -> ShipType.DESTROYER.equals(ship.shipType()))
                                   .count());
        assertEquals(2, shipsCustom.stream()
                                   .filter(ship -> ShipType.BATTLESHIP.equals(ship.shipType()))
                                   .count());
        assertEquals(1, shipsCustom.stream()
                                   .filter(ship -> ShipType.CARRIER.equals(ship.shipType()))
                                   .count());

        assertEquals(4, shipsCustom.stream()
                                   .filter(ship -> ShipType.SUBMARINE.equals(ship.shipType()))
                                   .map(Ship::shipSize)
                                   .filter(size -> size == 2)
                                   .count());
        assertEquals(3, shipsCustom.stream()
                                   .filter(ship -> ShipType.DESTROYER.equals(ship.shipType()))
                                   .map(Ship::shipSize)
                                   .filter(size -> size == 3)
                                   .count());
        assertEquals(2, shipsCustom.stream()
                                   .filter(ship -> ShipType.BATTLESHIP.equals(ship.shipType()))
                                   .map(Ship::shipSize)
                                   .filter(size -> size == 4)
                                   .count());
        assertEquals(1, shipsCustom.stream()
                                   .filter(ship -> ShipType.CARRIER.equals(ship.shipType()))
                                   .map(Ship::shipSize)
                                   .filter(size -> size == 5)
                                   .count());

        assertTrue(shipsCustom.stream().allMatch(s -> StringUtils.isNotBlank(s.shipId())));

    }
} 
