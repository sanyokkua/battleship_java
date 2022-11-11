package ua.kostenko.battleship.battleship.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoordinateUtil {
    public static boolean isCorrectColumn(Coordinate coordinate) {
        log.trace("In method: isCorrectColumn");
        val isCorrect =
                coordinate.column() >= 0 && coordinate.column() < GameConfig.NUMBER_OF_COLUMNS;
        log.debug("{} has correct columns: {}", coordinate, isCorrect);
        return isCorrect;
    }

    public static boolean isCorrectRow(Coordinate coordinate) {
        log.trace("In method: isCorrectRow");
        val isCorrect = coordinate.row() >= 0 && coordinate.row() < GameConfig.NUMBER_OF_ROWS;
        log.debug("{} has correct row: {}", coordinate, isCorrect);
        return isCorrect;
    }

    public static boolean isCorrectCoordinate(Coordinate coordinate) {
        log.trace("In method: isCorrectCoordinate");
        final boolean isCorrect = Objects.nonNull(coordinate)
                && isCorrectRow(coordinate)
                && isCorrectColumn(coordinate);
        log.debug("{} is correct: {}", coordinate, isCorrect);
        return isCorrect;
    }

    public static void validateCoordinateAndThrowException(Coordinate coordinate) {
        log.trace("In method: validateCoordinateAndThrowException");
        if (!isCorrectCoordinate(coordinate)) {
            throw new IllegalArgumentException("Coordinate is not valid. %s".formatted(coordinate));
        }
    }

    public static void validateCoordinateAndThrowException(Set<Coordinate> coordinates) {
        log.trace("In method: validateCoordinateAndThrowException");
        for (Coordinate coordinate : coordinates) {
            validateCoordinateAndThrowException(coordinate);
        }
    }

    public static Set<Coordinate> buildNeighbourCoordinates(final Coordinate currentCoordinate) {
        log.trace("In method: buildNeighbourCoordinates");
        val row = currentCoordinate.row();
        val col = currentCoordinate.column();
        val modifiers = Set.of(
                Coordinate.of(-1, -1),
                Coordinate.of(-1, 0),
                Coordinate.of(-1, 1),
                Coordinate.of(0, -1),
                Coordinate.of(0, 1),
                Coordinate.of(1, -1),
                Coordinate.of(1, 0),
                Coordinate.of(1, 1)
        );
        return modifiers.stream()
                        .map(modifier -> {
                            val neighbourRow = row + modifier.row();
                            val neighbourCol = col + modifier.column();
                            return Coordinate.of(neighbourRow, neighbourCol);
                        })
                        .filter(CoordinateUtil::isCorrectCoordinate)
                        .filter(coordinate -> !currentCoordinate.equals(coordinate))
                        .collect(Collectors.toSet());
    }

    public static Set<Coordinate> buildShipCoordinates(Coordinate coordinate, Ship ship) {
        log.trace("In method: buildShipCoordinates");
        Set<Coordinate> coordinates = new HashSet<>();
        val row = coordinate.row();
        val col = coordinate.column();
        for (int diff = 0; diff < ship.shipSize(); diff++) {
            if (Direction.HORIZONTAL == ship.direction()) {
                log.debug("For Horizontal ship");
                coordinates.add(Coordinate.of(row, col + diff));
            } else {
                log.debug("For Vertical ship");
                coordinates.add(Coordinate.of(row + diff, col));
            }
        }
        return coordinates;
    }

    public static Set<Coordinate> buildNeighbourCoordinates(
            Set<Coordinate> shipCoordinates) {
        log.trace("In method: buildNeighbourCoordinates");
        return shipCoordinates.stream()
                              .map(CoordinateUtil::buildNeighbourCoordinates)
                              .flatMap(Collection::stream)
                              .filter(c -> !shipCoordinates.contains(c))
                              .collect(Collectors.toSet());
    }
}
