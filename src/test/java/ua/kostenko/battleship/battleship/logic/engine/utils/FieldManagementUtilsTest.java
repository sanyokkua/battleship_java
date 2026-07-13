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

    @Test
    void testGetShipsFromField_noShipsOnField_returnsEmptySet() {
        final Cell[][] testField = FieldUtils.initializeField();
        final Set<Ship> shipsFromField = FieldUtils.getShipsFromField(testField);
        assertEquals(0, shipsFromField.size());
        assertTrue(shipsFromField.isEmpty());
    }

    @Test
    void testFindShipCells_shipNotPlacedOnField_returnsEmptySet() {
        final Cell[][] testField = FieldUtils.initializeField();
        var unplacedShip = Ship.builder()
                               .shipId("test_id_unplaced")
                               .shipSize(2)
                               .shipType(ShipType.DESTROYER)
                               .shipDirection(ShipDirection.HORIZONTAL)
                               .build();
        final Set<Cell> shipCells = FieldUtils.findShipCells(testField, unplacedShip);
        assertEquals(0, shipCells.size());
        assertTrue(shipCells.isEmpty());
    }

    @Test
    void testFindShipNeighbourCells_shipAtBottomRightCorner() {
        var testField = FieldUtils.initializeField();
        var shipCorner = Ship.builder()
                             .shipId("test_id_corner")
                             .shipSize(2)
                             .shipType(ShipType.DESTROYER)
                             .shipDirection(ShipDirection.HORIZONTAL)
                             .build();
        testField[NUMBER_OF_ROWS - 1][NUMBER_OF_COLUMNS - 2] = Cell.builder()
                                                                   .coordinate(Coordinate.of(NUMBER_OF_ROWS - 1, NUMBER_OF_COLUMNS - 2))
                                                                   .ship(shipCorner)
                                                                   .hasShot(false)
                                                                   .isAvailable(false)
                                                                   .build();
        testField[NUMBER_OF_ROWS - 1][NUMBER_OF_COLUMNS - 1] = Cell.builder()
                                                                   .coordinate(Coordinate.of(NUMBER_OF_ROWS - 1, NUMBER_OF_COLUMNS - 1))
                                                                   .ship(shipCorner)
                                                                   .hasShot(false)
                                                                   .isAvailable(false)
                                                                   .build();
        final Set<Cell> shipNeighbourCells = FieldUtils.findShipNeighbourCells(testField, shipCorner);
        assertEquals(4, shipNeighbourCells.size());
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[NUMBER_OF_ROWS - 2][NUMBER_OF_COLUMNS - 2].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[NUMBER_OF_ROWS - 2][NUMBER_OF_COLUMNS - 1].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[NUMBER_OF_ROWS - 1][NUMBER_OF_COLUMNS - 3].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[NUMBER_OF_ROWS - 2][NUMBER_OF_COLUMNS - 3].equals(c)));
    }

    @Test
    void testFindShipNeighbourCells_shipAlongTopEdge() {
        var testField = FieldUtils.initializeField();
        var shipTopEdge = Ship.builder()
                              .shipId("test_id_top_edge")
                              .shipSize(2)
                              .shipType(ShipType.DESTROYER)
                              .shipDirection(ShipDirection.HORIZONTAL)
                              .build();
        testField[0][4] = Cell.builder()
                              .coordinate(Coordinate.of(0, 4))
                              .ship(shipTopEdge)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[0][5] = Cell.builder()
                              .coordinate(Coordinate.of(0, 5))
                              .ship(shipTopEdge)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        final Set<Cell> shipNeighbourCells = FieldUtils.findShipNeighbourCells(testField, shipTopEdge);
        assertEquals(6, shipNeighbourCells.size());
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[0][3].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[0][6].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][3].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][4].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][5].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[1][6].equals(c)));
    }

    @Test
    void testFindShipNeighbourCells_shipAlongLeftEdge() {
        var testField = FieldUtils.initializeField();
        var shipLeftEdge = Ship.builder()
                               .shipId("test_id_left_edge")
                               .shipSize(2)
                               .shipType(ShipType.DESTROYER)
                               .shipDirection(ShipDirection.VERTICAL)
                               .build();
        testField[4][0] = Cell.builder()
                              .coordinate(Coordinate.of(4, 0))
                              .ship(shipLeftEdge)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        testField[5][0] = Cell.builder()
                              .coordinate(Coordinate.of(5, 0))
                              .ship(shipLeftEdge)
                              .hasShot(false)
                              .isAvailable(false)
                              .build();
        final Set<Cell> shipNeighbourCells = FieldUtils.findShipNeighbourCells(testField, shipLeftEdge);
        assertEquals(6, shipNeighbourCells.size());
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[3][0].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[3][1].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[4][1].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[5][1].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[6][0].equals(c)));
        assertTrue(shipNeighbourCells.stream()
                                     .anyMatch(c -> testField[6][1].equals(c)));
    }
} 
