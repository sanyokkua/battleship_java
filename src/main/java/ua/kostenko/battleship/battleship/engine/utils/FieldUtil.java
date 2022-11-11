package ua.kostenko.battleship.battleship.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static ua.kostenko.battleship.battleship.engine.config.GameConfig.NUMBER_OF_COLUMNS;
import static ua.kostenko.battleship.battleship.engine.config.GameConfig.NUMBER_OF_ROWS;


@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FieldUtil {
    public static Cell[][] initializeField() {
        log.trace("In method: initializeField");
        Cell[][] field = new Cell[NUMBER_OF_ROWS][NUMBER_OF_COLUMNS];
        for (int i = 0; i < NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < NUMBER_OF_COLUMNS; j++) {
                field[i][j] = Cell.builder()
                                  .coordinate(Coordinate.of(i, j))
                                  .ship(null)
                                  .hasShot(false)
                                  .isAvailable(true)
                                  .build();
            }
        }
        return field;
    }

    public static Set<Cell> convertToFlatSet(Cell[][] field) {
        log.trace("In method: convertToFlatSet");
        return Arrays.stream(field)
                     .flatMap(Arrays::stream)
                     .collect(Collectors.toSet());
    }

    public static Set<Ship> getShipsFromField(Cell[][] field) {
        log.trace("In method: getShipsFromField");
        return convertToFlatSet(field)
                .stream()
                .filter(Cell::hasShip)
                .map(Cell::ship)
                .collect(Collectors.toSet());
    }

    public static Set<Cell> findShipCells(Cell[][] field, Ship ship) {
        log.trace("In method: findShipCells");
        return convertToFlatSet(field)
                .stream()
                .filter(Cell::hasShip)
                .filter(c -> ship.equals(c.ship()))
                .collect(Collectors.toSet());

    }

    public static Set<Cell> findShipNeighbourCells(Cell[][] field, Ship ship) {
        log.trace("In method: findShipNeighbourCells");
        val shipCells = findShipCells(field, ship);
        val shipCoordinates = shipCells.stream()
                                       .map(Cell::coordinate)
                                       .collect(Collectors.toSet());
        val neighbourCoordinates =
                CoordinateUtil.buildNeighbourCoordinates(shipCoordinates);

        return neighbourCoordinates.stream()
                                   .map(c -> field[c.row()][c.column()])
                                   .collect(Collectors.toSet());
    }
}
