package ua.kostenko.battleship.battleship.logic.engine.utils;


import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration.NUMBER_OF_COLUMNS;
import static ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration.NUMBER_OF_ROWS;

class FieldManagementUtilsTest {

    @Test
    void testInitializeField() {
        final Cell[][] cells = FieldUtils.initializeField();

        assertEquals(NUMBER_OF_ROWS, cells.length);
        for (Cell[] row : cells) {
            assertEquals(NUMBER_OF_COLUMNS, row.length);
        }

        for (int row = 0; row < NUMBER_OF_ROWS; row++) {
            for (int column = 0; column < NUMBER_OF_COLUMNS; column++) {
                var cell = cells[row][column];
                assertEquals(row,
                             cell.coordinate()
                                 .row());
                assertEquals(column,
                             cell.coordinate()
                                 .column());
                assertFalse(cell.hasShip());
                assertFalse(cell.hasShot());
                assertTrue(cell.optionalShipId()
                               .isEmpty());
                assertTrue(cell.isAvailable());
            }
        }
    }

    @Test
    void testConvertToFlatSet() {
        var testField = FieldUtils.initializeField();
        var set = FieldUtils.convertToFlatSet(testField);
        assertEquals(100, set.size());
        for (Cell cell : set) {
            assertFalse(cell.hasShip());
            assertFalse(cell.hasShot());
            assertTrue(cell.optionalShipId()
                           .isEmpty());
            assertTrue(cell.isAvailable());
        }
    }

    @Test
    void testGetShipsFromField() {
        var testField = FieldUtils.initializeField();
        var ship1 = Ship.builder()
                        .shipId("test_id_1")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        var ship2 = Ship.builder()
                        .shipId("test_id_2")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        var ship3 = Ship.builder()
                        .shipId("test_id_3")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        testField[0][0] = Cell.builder()
                              .coordinate(Coordinate.of(0, 0))
                              .ship(ship1)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[0][1] = Cell.builder()
                              .coordinate(Coordinate.of(0, 1))
                              .ship(ship1)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();

        testField[5][2] = Cell.builder()
                              .coordinate(Coordinate.of(5, 2))
                              .ship(ship2)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][3] = Cell.builder()
                              .coordinate(Coordinate.of(5, 3))
                              .ship(ship2)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][4] = Cell.builder()
                              .coordinate(Coordinate.of(5, 4))
                              .ship(ship2)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();

        final Set<Ship> shipsFromField = FieldUtils.getShipsFromField(testField);
        assertEquals(2, shipsFromField.size());
        assertTrue(shipsFromField.stream()
                                 .anyMatch(ship1::equals));
        assertTrue(shipsFromField.stream()
                                 .anyMatch(ship2::equals));

        testField[4][9] = Cell.builder()
                              .coordinate(Coordinate.of(4, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][9] = Cell.builder()
                              .coordinate(Coordinate.of(5, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[6][9] = Cell.builder()
                              .coordinate(Coordinate.of(6, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[7][9] = Cell.builder()
                              .coordinate(Coordinate.of(7, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();

        final Set<Ship> shipsFromField2 = FieldUtils.getShipsFromField(testField);
        assertEquals(3, shipsFromField2.size());
        assertTrue(shipsFromField2.stream()
                                  .anyMatch(ship1::equals));
        assertTrue(shipsFromField2.stream()
                                  .anyMatch(ship2::equals));
        assertTrue(shipsFromField2.stream()
                                  .anyMatch(ship3::equals));
    }

    @Test
    void testFindShipCells() {
        var testField = FieldUtils.initializeField();
        var ship1 = Ship.builder()
                        .shipId("test_id_1")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        var ship2 = Ship.builder()
                        .shipId("test_id_2")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        var ship3 = Ship.builder()
                        .shipId("test_id_3")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        testField[0][0] = Cell.builder()
                              .coordinate(Coordinate.of(0, 0))
                              .ship(ship1)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[0][1] = Cell.builder()
                              .coordinate(Coordinate.of(0, 1))
                              .ship(ship1)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][2] = Cell.builder()
                              .coordinate(Coordinate.of(5, 2))
                              .ship(ship2)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][3] = Cell.builder()
                              .coordinate(Coordinate.of(5, 3))
                              .ship(ship2)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][4] = Cell.builder()
                              .coordinate(Coordinate.of(5, 4))
                              .ship(ship2)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[4][9] = Cell.builder()
                              .coordinate(Coordinate.of(4, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][9] = Cell.builder()
                              .coordinate(Coordinate.of(5, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[6][9] = Cell.builder()
                              .coordinate(Coordinate.of(6, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[7][9] = Cell.builder()
                              .coordinate(Coordinate.of(7, 9))
                              .ship(ship3)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        final Set<Cell> shipCells = FieldUtils.findShipCells(testField, ship2);
        assertEquals(3, shipCells.size());
        assertTrue(shipCells.stream()
                            .anyMatch(c -> testField[5][2].equals(c)));
        assertTrue(shipCells.stream()
                            .anyMatch(c -> testField[5][3].equals(c)));
        assertTrue(shipCells.stream()
                            .anyMatch(c -> testField[5][4].equals(c)));
    }

    @Test
    void testFindShipNeighbourCells() {
        var testField = FieldUtils.initializeField();
        var ship1 = Ship.builder()
                        .shipId("test_id_1")
                        .shipSize(2)
                        .shipType(ShipType.DESTROYER)
                        .shipDirection(ShipDirection.HORIZONTAL)
                        .build();
        testField[0][0] = Cell.builder()
                              .coordinate(Coordinate.of(0, 0))
                              .ship(ship1)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[0][1] = Cell.builder()
                              .coordinate(Coordinate.of(0, 1))
                              .ship(ship1)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        final Set<Cell> shipNeighbourCells = FieldUtils.findShipNeighbourCells(testField, ship1);
        assertEquals(4, shipNeighbourCells.size());
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][0].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][1].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][2].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[0][2].equals(c)));
    }
} 
