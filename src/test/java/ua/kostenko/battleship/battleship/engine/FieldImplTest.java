package ua.kostenko.battleship.battleship.engine;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.engine.utils.FieldUtil;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FieldImplTest {

    private static final Ship TEST_SHIP_HORIZONTAL_S1 = Ship.builder()
                                                            .shipId("TEST_SHIP_HORIZONTAL_S1")
                                                            .shipType(ShipType.PATROL_BOAT)
                                                            .shipSize(1)
                                                            .direction(Direction.HORIZONTAL)
                                                            .build();
    private static final Ship TEST_SHIP_HORIZONTAL_S3 = Ship.builder()
                                                            .shipId("TEST_SHIP_HORIZONTAL_S3")
                                                            .shipType(ShipType.BATTLESHIP)
                                                            .shipSize(3)
                                                            .direction(Direction.HORIZONTAL)
                                                            .build();
    private static final Ship TEST_SHIP_VERTICAL_S4 = Ship.builder()
                                                          .shipId("TEST_SHIP_VERTICAL_S4")
                                                          .shipType(ShipType.BATTLESHIP)
                                                          .shipSize(4)
                                                          .direction(Direction.VERTICAL)
                                                          .build();

    @Test
    void testAddShip() {
        FieldImpl field = new FieldImpl();

        var coordinate = Coordinate.of(0, 0);
        field.addShip(coordinate, TEST_SHIP_HORIZONTAL_S3);

        assertFalse(FieldUtil.getShipsFromField(field.getField()).isEmpty());
        assertEquals(1, FieldUtil.getShipsFromField(field.getField()).size());

        coordinate = Coordinate.of(5, 5);
        field.addShip(coordinate, TEST_SHIP_VERTICAL_S4);

        assertFalse(FieldUtil.getShipsFromField(field.getField()).isEmpty());
        assertEquals(2, FieldUtil.getShipsFromField(field.getField()).size());

        final Cell[][] gameField = field.getField();
        Set<Cell> ship1Cells = Set.of(
                gameField[0][0],
                gameField[0][1],
                gameField[0][2]
        );
        Set<Cell> ship2Cells = Set.of(
                gameField[5][5],
                gameField[6][5],
                gameField[7][5],
                gameField[8][5]
        );
        assertTrue(ship1Cells.stream().allMatch(Cell::hasShip));
        assertTrue(ship1Cells.stream().allMatch(s -> TEST_SHIP_HORIZONTAL_S3.equals(s.ship())));
        assertFalse(ship1Cells.stream().allMatch(Cell::isAvailable));

        assertTrue(ship2Cells.stream().allMatch(Cell::hasShip));
        assertTrue(ship2Cells.stream().allMatch(s -> TEST_SHIP_VERTICAL_S4.equals(s.ship())));
        assertFalse(ship2Cells.stream().allMatch(Cell::isAvailable));
    }

    @Test
    void testAddShipFailures() {
        FieldImpl field = new FieldImpl();

        assertThrows(IllegalArgumentException.class,
                     () -> field.addShip(Coordinate.of(0, 9), TEST_SHIP_HORIZONTAL_S3));
        assertTrue(FieldUtil.getShipsFromField(field.getField()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                     () -> field.addShip(Coordinate.of(7, 3), TEST_SHIP_VERTICAL_S4));
        assertTrue(FieldUtil.getShipsFromField(field.getField()).isEmpty());

        field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S3);

        assertFalse(FieldUtil.getShipsFromField(field.getField()).isEmpty());
        assertEquals(1, FieldUtil.getShipsFromField(field.getField()).size());
        assertThrows(IllegalArgumentException.class,
                     () -> field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S1));
        assertThrows(IllegalArgumentException.class,
                     () -> field.addShip(Coordinate.of(0, 3), TEST_SHIP_HORIZONTAL_S1));
        assertThrows(IllegalArgumentException.class,
                     () -> field.addShip(Coordinate.of(1, 2), TEST_SHIP_HORIZONTAL_S1));
    }

    @Test
    void testRemoveShip() {
        FieldImpl field = new FieldImpl();

        assertTrue(FieldUtil.convertToFlatSet(field.getField()).stream()
                            .allMatch(Cell::isAvailable));

        field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S1);
        field.addShip(Coordinate.of(4, 5), TEST_SHIP_HORIZONTAL_S3);
        field.addShip(Coordinate.of(5, 2), TEST_SHIP_VERTICAL_S4);

        assertEquals(37, FieldUtil.convertToFlatSet(field.getField())
                                  .stream()
                                  .filter(c -> !c.isAvailable())
                                  .count());

        assertFalse(FieldUtil.getShipsFromField(field.getField()).isEmpty());
        assertEquals(3, FieldUtil.getShipsFromField(field.getField()).size());

        assertTrue(field.removeShip(Coordinate.of(0, 1)).isEmpty());
        assertEquals(3, FieldUtil.getShipsFromField(field.getField()).size());

        var removed1 = field.removeShip(Coordinate.of(0, 0));
        assertTrue(removed1.isPresent());
        assertEquals(TEST_SHIP_HORIZONTAL_S1.shipId(), removed1.get());
        assertEquals(2, FieldUtil.getShipsFromField(field.getField()).size());
        assertTrue(field.getField()[0][0].isAvailable());
        assertEquals(33, FieldUtil.convertToFlatSet(field.getField())
                                  .stream()
                                  .filter(c -> !c.isAvailable())
                                  .count());

        var removed2 = field.removeShip(Coordinate.of(4, 7));
        assertTrue(removed2.isPresent());
        assertEquals(TEST_SHIP_HORIZONTAL_S3.shipId(), removed2.get());
        assertEquals(1, FieldUtil.getShipsFromField(field.getField()).size());
        assertEquals(18, FieldUtil.convertToFlatSet(field.getField())
                                  .stream()
                                  .filter(c -> !c.isAvailable())
                                  .count());

        var removed3 = field.removeShip(Coordinate.of(6, 2));
        assertTrue(removed3.isPresent());
        assertEquals(TEST_SHIP_VERTICAL_S4.shipId(), removed3.get());
        assertEquals(0, FieldUtil.getShipsFromField(field.getField()).size());
        assertEquals(0, FieldUtil.convertToFlatSet(field.getField())
                                 .stream()
                                 .filter(c -> !c.isAvailable())
                                 .count());
    }

    @Test
    void testMakeShot() {
        FieldImpl field = new FieldImpl();
        field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S1);
        field.addShip(Coordinate.of(4, 5), TEST_SHIP_HORIZONTAL_S3);
        field.addShip(Coordinate.of(5, 2), TEST_SHIP_VERTICAL_S4);

        assertFalse(field.getField()[0][0].hasShot());
        final ShotResult shotResult1 = field.makeShot(Coordinate.of(0, 0));
        assertEquals(ShotResult.DESTROYED, shotResult1);
        assertTrue(field.getField()[0][0].hasShot());
        assertTrue(field.getField()[0][1].hasShot());
        assertTrue(field.getField()[1][0].hasShot());
        assertTrue(field.getField()[1][1].hasShot());

        assertFalse(field.getField()[3][3].hasShot());
        final ShotResult shotResult2 = field.makeShot(Coordinate.of(3, 3));
        assertEquals(ShotResult.MISS, shotResult2);
        assertTrue(field.getField()[3][3].hasShot());

        assertFalse(field.getField()[6][2].hasShot());
        final ShotResult shotResult3 = field.makeShot(Coordinate.of(6, 2));
        assertEquals(ShotResult.HIT, shotResult3);
        assertTrue(field.getField()[3][3].hasShot());
    }

    @Test
    void testGetField() {
        FieldImpl field = new FieldImpl();

        var getField1 = field.getField();
        assertEquals(GameConfig.NUMBER_OF_ROWS, getField1.length);
        for (var row : getField1) {
            assertEquals(GameConfig.NUMBER_OF_COLUMNS, row.length);
        }
    }

    @Test
    void testGetFieldWithHiddenShips() {
        FieldImpl field = new FieldImpl();
        field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S1);
        field.addShip(Coordinate.of(4, 5), TEST_SHIP_HORIZONTAL_S3);
        field.addShip(Coordinate.of(5, 2), TEST_SHIP_VERTICAL_S4);

        var getField1 = field.getFieldWithHiddenShips();
        assertFalse(FieldUtil.convertToFlatSet(getField1).stream().anyMatch(Cell::hasShip));

        field.makeShot(Coordinate.of(0, 0));

        getField1 = field.getFieldWithHiddenShips();
        assertTrue(FieldUtil.convertToFlatSet(getField1).stream().anyMatch(Cell::hasShip));
        assertEquals(1, FieldUtil.convertToFlatSet(getField1).stream()
                                 .filter(Cell::hasShip).count());

        field.makeShot(Coordinate.of(4, 5));
        field.makeShot(Coordinate.of(4, 6));

        getField1 = field.getFieldWithHiddenShips();
        assertTrue(FieldUtil.convertToFlatSet(getField1).stream().anyMatch(Cell::hasShip));
        assertEquals(3, FieldUtil.convertToFlatSet(getField1).stream()
                                 .filter(Cell::hasShip).count());
        assertEquals(2, FieldUtil.convertToFlatSet(getField1).stream()
                                 .filter(Cell::hasShip)
                                 .map(Cell::ship)
                                 .distinct()
                                 .count());
    }

    @Test
    void testGetAmountOfAliveCells() {
        FieldImpl field = new FieldImpl();
        field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S1);
        field.addShip(Coordinate.of(4, 5), TEST_SHIP_HORIZONTAL_S3);
        field.addShip(Coordinate.of(5, 2), TEST_SHIP_VERTICAL_S4);

        assertEquals(100, field.getAmountOfAliveCells());

        field.makeShot(Coordinate.of(0, 5));

        assertEquals(99, field.getAmountOfAliveCells());

        field.makeShot(Coordinate.of(0, 0));

        assertEquals(95, field.getAmountOfAliveCells());
    }

    @Test
    void testGetAmountOfAliveShips() {
        FieldImpl field = new FieldImpl();
        field.addShip(Coordinate.of(0, 0), TEST_SHIP_HORIZONTAL_S1);
        field.addShip(Coordinate.of(4, 5), TEST_SHIP_HORIZONTAL_S3);
        field.addShip(Coordinate.of(5, 2), TEST_SHIP_VERTICAL_S4);

        assertEquals(3, field.getAmountOfAliveShips());

        field.makeShot(Coordinate.of(0, 5));

        assertEquals(3, field.getAmountOfAliveShips());

        field.makeShot(Coordinate.of(0, 0));

        assertEquals(2, field.getAmountOfAliveShips());

        field.makeShot(Coordinate.of(4, 5));
        field.makeShot(Coordinate.of(4, 6));
        field.makeShot(Coordinate.of(4, 7));

        assertEquals(1, field.getAmountOfAliveShips());

        field.makeShot(Coordinate.of(5, 2));
        field.makeShot(Coordinate.of(6, 2));
        field.makeShot(Coordinate.of(7, 2));
        field.makeShot(Coordinate.of(8, 2));

        assertEquals(0, field.getAmountOfAliveShips());
    }
}
