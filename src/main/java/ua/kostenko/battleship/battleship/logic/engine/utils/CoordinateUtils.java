package ua.kostenko.battleship.battleship.logic.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoordinateUtils {

    public static boolean isCorrectCoordinate(Coordinate coordinate) {
        log.trace("In method: isCorrectCoordinate");
        if (Objects.isNull(coordinate)) {
            return false;
        }
        val isCorrectColumn =
                coordinate.column() >= 0 && coordinate.column() < GameEditionConfiguration.NUMBER_OF_COLUMNS;
        val isCorrectRow = coordinate.row() >= 0 && coordinate.row() < GameEditionConfiguration.NUMBER_OF_ROWS;
        val isCorrect = isCorrectRow && isCorrectColumn;
        log.debug("{} is correct: {}", coordinate, isCorrect);
        return isCorrect;
    }

    public static void validateCoordinate(Coordinate coordinate) {
        log.trace("In method: validateCoordinateAndThrowException");
        if (!isCorrectCoordinate(coordinate)) {
            throw new IllegalArgumentException("Coordinate is not valid. %s".formatted(coordinate));
        }
    }

    public static void validateCoordinates(Set<Coordinate> coordinates) {
        log.trace("In method: validateCoordinateAndThrowException");
        for (Coordinate coordinate : coordinates) {
            validateCoordinate(coordinate);
        }
    }

    public static Set<Coordinate> buildNeighbourCoordinates(final Coordinate currentCoordinate) {
        log.trace("In method: buildNeighbourCoordinates");
        val row = currentCoordinate.row();
        val col = currentCoordinate.column();
        val modifiers = Set.of(Coordinate.of(-1, -1),
                               Coordinate.of(-1, 0),
                               Coordinate.of(-1, 1),
                               Coordinate.of(0, -1),
                               Coordinate.of(0, 1),
                               Coordinate.of(1, -1),
                               Coordinate.of(1, 0),
                               Coordinate.of(1, 1));
        return modifiers.stream()
                        .map(modifier -> {
                            val neighbourRow = row + modifier.row();
                            val neighbourCol = col + modifier.column();
                            return Coordinate.of(neighbourRow, neighbourCol);
                        })
                        .filter(CoordinateUtils::isCorrectCoordinate)
                        .filter(coordinate -> !currentCoordinate.equals(coordinate))
                        .collect(Collectors.toSet());
    }

    public static Set<Coordinate> buildShipCoordinates(Coordinate coordinate, Ship ship) {
        log.trace("In method: buildShipCoordinates");
        Set<Coordinate> coordinates = new HashSet<>();
        val row = coordinate.row();
        val col = coordinate.column();
        for (int diff = 0; diff < ship.shipSize(); diff++) {
            if (ShipDirection.HORIZONTAL == ship.shipDirection()) {
                log.debug("For Horizontal ship");
                coordinates.add(Coordinate.of(row, col + diff));
            } else {
                log.debug("For Vertical ship");
                coordinates.add(Coordinate.of(row + diff, col));
            }
        }
        return coordinates;
    }

    public static Set<Coordinate> buildNeighbourCoordinates(Set<Coordinate> shipCoordinates) {
        log.trace("In method: buildNeighbourCoordinates");
        return shipCoordinates.stream()
                              .map(CoordinateUtils::buildNeighbourCoordinates)
                              .flatMap(Collection::stream)
                              .filter(c -> !shipCoordinates.contains(c))
                              .collect(Collectors.toSet());
    }
}
