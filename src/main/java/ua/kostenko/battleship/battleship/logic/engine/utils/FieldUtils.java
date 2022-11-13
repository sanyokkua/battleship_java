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


@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FieldUtils {
    public static Cell[][] initializeField() {
        log.trace("In method: initializeField");
        Cell[][] field = new Cell[GameEditionConfiguration.NUMBER_OF_ROWS][GameEditionConfiguration.NUMBER_OF_COLUMNS];
        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS; j++) {
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
        return convertToFlatSet(field).stream()
                                      .filter(Cell::hasShip)
                                      .map(Cell::ship)
                                      .collect(Collectors.toSet());
    }

    public static Set<Cell> findShipCells(Cell[][] field, Ship ship) {
        log.trace("In method: findShipCells");
        return convertToFlatSet(field).stream()
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
        val neighbourCoordinates = CoordinateUtils.buildNeighbourCoordinates(shipCoordinates);

        return neighbourCoordinates.stream()
                                   .map(c -> field[c.row()][c.column()])
                                   .collect(Collectors.toSet());
    }
}
