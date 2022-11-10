package ua.kostenko.battleship.battleship.engine.utils;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateUtilTest {

    @Test
    void testIsCorrectColumn() {
        assertFalse(CoordinateUtil.isCorrectColumn(Coordinate.of(-1, -1)));
        assertFalse(CoordinateUtil.isCorrectColumn(Coordinate.of(0, 10)));
        assertTrue(CoordinateUtil.isCorrectColumn(Coordinate.of(0, 0)));
        assertTrue(CoordinateUtil.isCorrectColumn(Coordinate.of(1, 1)));
        assertTrue(CoordinateUtil.isCorrectColumn(Coordinate.of(9, 9)));
    }


    @Test
    void testIsCorrectRow() {
        assertFalse(CoordinateUtil.isCorrectRow(Coordinate.of(-1, -1)));
        assertFalse(CoordinateUtil.isCorrectRow(Coordinate.of(10, 0)));
        assertTrue(CoordinateUtil.isCorrectRow(Coordinate.of(0, -1)));
        assertTrue(CoordinateUtil.isCorrectRow(Coordinate.of(1, -1)));
        assertTrue(CoordinateUtil.isCorrectRow(Coordinate.of(9, -9)));
    }


    @Test
    void testIsCorrectCoordinate() {
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(-1, -1)));
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(-1, 0)));
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(-1, 1)));
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(0, -1)));
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(1, -1)));
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(0, 10)));
        assertFalse(CoordinateUtil.isCorrectCoordinate(Coordinate.of(10, 0)));
        assertTrue(CoordinateUtil.isCorrectCoordinate(Coordinate.of(0, 0)));
        assertTrue(CoordinateUtil.isCorrectCoordinate(Coordinate.of(1, 1)));
        assertTrue(CoordinateUtil.isCorrectCoordinate(Coordinate.of(9, 9)));
    }


    @Test
    void testValidateCoordinateAndThrowException() {
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(-1, -1)));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(-1, 0)));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(-1, 1)));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(0, -1)));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(1, -1)));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(0, 10)));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             Coordinate.of(10, 0)));
    }

    @Test
    void testValidateCoordinateAndThrowExceptionForSetOfCoordinates() {
        var testValues = Set.of(Coordinate.of(0, 0), Coordinate.of(-1, 9));
        assertThrows(IllegalArgumentException.class,
                     () -> CoordinateUtil.validateCoordinateAndThrowException(
                             testValues));
    }


    @Test
    void testBuildNeighbourCoordinatesCurrentCoordinate() {
        var coordinates = CoordinateUtil.buildNeighbourCoordinates(Coordinate.of(0, 0));
        assertEquals(3, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 0 && c.column() == 1));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 1 && c.column() == 0));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 1 && c.column() == 1));

        coordinates = CoordinateUtil.buildNeighbourCoordinates(Coordinate.of(9, 9));
        assertEquals(3, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 8 && c.column() == 9));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 8 && c.column() == 8));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 9 && c.column() == 8));

        coordinates = CoordinateUtil.buildNeighbourCoordinates(Coordinate.of(5, 5));
        assertEquals(8, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 4 && c.column() == 4));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 4 && c.column() == 5));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 4 && c.column() == 6));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 5 && c.column() == 4));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 5 && c.column() == 6));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 6 && c.column() == 4));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 6 && c.column() == 5));
        assertTrue(coordinates.stream().anyMatch(c -> c.row() == 6 && c.column() == 6));
    }


    @Test
    void testBuildShipCoordinates() {
        var coordinates = CoordinateUtil.buildShipCoordinates(
                Coordinate.of(0, 0),
                Ship.builder()
                    .shipId("test")
                    .shipType(ShipType.SUBMARINE)
                    .shipSize(3)
                    .direction(Direction.HORIZONTAL)
                    .build());
        assertEquals(3, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 0).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 1).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 2).equals(c)));

        coordinates = CoordinateUtil.buildShipCoordinates(
                Coordinate.of(9, 0),
                Ship.builder()
                    .shipId("test")
                    .shipType(ShipType.SUBMARINE)
                    .shipSize(5)
                    .direction(Direction.VERTICAL)
                    .build());
        assertEquals(5, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(9, 0).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(10, 0).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(11, 0).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(12, 0).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(13, 0).equals(c)));

        coordinates = CoordinateUtil.buildShipCoordinates(
                Coordinate.of(5, 5),
                Ship.builder()
                    .shipId("test")
                    .shipType(ShipType.SUBMARINE)
                    .shipSize(1)
                    .direction(Direction.VERTICAL)
                    .build());
        assertEquals(1, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(5, 5).equals(c)));

        coordinates = CoordinateUtil.buildShipCoordinates(
                Coordinate.of(0, 8),
                Ship.builder()
                    .shipId("test")
                    .shipType(ShipType.SUBMARINE)
                    .shipSize(4)
                    .direction(Direction.HORIZONTAL)
                    .build());
        assertEquals(4, coordinates.size());
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 8).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 9).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 10).equals(c)));
        assertTrue(coordinates.stream().anyMatch(c -> Coordinate.of(0, 11).equals(c)));
    }

    @Test
    void testBuildNeighbourCoordinatesShipCoordinates() {
        var coordinates = Set.of(Coordinate.of(0, 0), Coordinate.of(0, 1));
        var neighbours = CoordinateUtil.buildNeighbourCoordinates(coordinates);
        assertEquals(4, neighbours.size());
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(1, 0).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(1, 1).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(1, 2).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(0, 2).equals(c)));

        coordinates = Set.of(Coordinate.of(5, 5), Coordinate.of(4, 5));
        neighbours = CoordinateUtil.buildNeighbourCoordinates(coordinates);
        assertEquals(10, neighbours.size());
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(3, 4).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(3, 5).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(3, 6).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(4, 4).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(4, 6).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(5, 4).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(5, 6).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(6, 4).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(6, 5).equals(c)));
        assertTrue(neighbours.stream().anyMatch(c -> Coordinate.of(6, 6).equals(c)));
    }
} 
