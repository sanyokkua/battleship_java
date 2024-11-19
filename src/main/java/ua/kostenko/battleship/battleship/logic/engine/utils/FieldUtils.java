package ua.kostenko.battleship.battleship.logic.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for handling game field operations in the Battleship game.
 * <p>
 * This class provides methods to initialize the game field, convert the field to a set, and perform
 * operations related to ships on the field.
 * </p>
 *
 * @see Cell
 * @see Coordinate
 * @see Ship
 * @see GameEditionConfiguration
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FieldUtils {

    /**
     * Initializes the game field with default cells.
     *
     * @return a 2D array representing the initialized game field
     */
    public static Cell[][] initializeField() {
        log.trace("In method: initializeField");
        Cell[][] field = new Cell[GameEditionConfiguration.NUMBER_OF_ROWS][GameEditionConfiguration.NUMBER_OF_COLUMNS];
        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS; j++) {
                field[i][j] = Cell.builder().coordinate(Coordinate.of(i, j)).ship(null).hasShot(false).isAvailable(true).build();
            }
        }
        return field;
    }

    /**
     * Converts the 2D game field array to a flat set of cells.
     *
     * @param field the 2D array representing the game field
     * @return a set of all cells in the game field
     */
    public static Set<Cell> convertToFlatSet(Cell[][] field) {
        log.trace("In method: convertToFlatSet");
        return Arrays.stream(field).flatMap(Arrays::stream).collect(Collectors.toSet());
    }

    /**
     * Retrieves all ships from the game field.
     *
     * @param field the 2D array representing the game field
     * @return a set of ships present on the field
     */
    public static Set<Ship> getShipsFromField(Cell[][] field) {
        log.trace("In method: getShipsFromField");
        return convertToFlatSet(field).stream().filter(Cell::hasShip).map(Cell::ship).collect(Collectors.toSet());
    }

    /**
     * Finds all cells occupied by a specific ship on the field.
     *
     * @param field the 2D array representing the game field
     * @param ship  the ship to locate
     * @return a set of cells occupied by the ship
     */
    public static Set<Cell> findShipCells(Cell[][] field, Ship ship) {
        log.trace("In method: findShipCells");
        return convertToFlatSet(field).stream().filter(Cell::hasShip).filter(c -> ship.equals(c.ship())).collect(Collectors.toSet());

    }

    /**
     * Finds all neighboring cells around a specific ship on the field.
     *
     * @param field the 2D array representing the game field
     * @param ship  the ship to locate neighboring cells for
     * @return a set of neighboring cells around the ship
     */
    public static Set<Cell> findShipNeighbourCells(Cell[][] field, Ship ship) {
        log.trace("In method: findShipNeighbourCells");
        val shipCells = findShipCells(field, ship);
        val shipCoordinates = shipCells.stream().map(Cell::coordinate).collect(Collectors.toSet());
        val neighbourCoordinates = CoordinateUtils.buildNeighbourCoordinates(shipCoordinates);

        return neighbourCoordinates.stream().map(c -> field[c.row()][c.column()]).collect(Collectors.toSet());
    }
}
