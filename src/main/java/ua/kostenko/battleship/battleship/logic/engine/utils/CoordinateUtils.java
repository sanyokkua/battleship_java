package ua.kostenko.battleship.battleship.logic.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
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

/**
 * Utility class for handling coordinates in the Battleship game.
 * <p>
 * This class provides various methods for validating and manipulating coordinates on the game board.
 * </p>
 *
 * @see Coordinate
 * @see Ship
 * @see ShipDirection
 * @see GameEditionConfiguration
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoordinateUtils {

    private static final Set<Coordinate> NEIGHBOUR_OFFSETS = Set.of(Coordinate.of(-1, -1), Coordinate.of(-1, 0), Coordinate.of(-1, 1), Coordinate.of(0, -1), Coordinate.of(0, 1), Coordinate.of(1, -1), Coordinate.of(1, 0), Coordinate.of(1, 1));

    /**
     * Checks if a given coordinate is valid within the game board dimensions.
     *
     * @param coordinate the coordinate to validate
     * @return {@code true} if the coordinate is valid, {@code false} otherwise
     */
    public static boolean isCorrectCoordinate(Coordinate coordinate) {
        log.trace("In method: isCorrectCoordinate");
        if (Objects.isNull(coordinate)) {
            return false;
        }
        val isCorrectColumn = coordinate.column() >= 0 && coordinate.column() < GameEditionConfiguration.NUMBER_OF_COLUMNS;
        val isCorrectRow = coordinate.row() >= 0 && coordinate.row() < GameEditionConfiguration.NUMBER_OF_ROWS;
        val isCorrect = isCorrectRow && isCorrectColumn;
        log.debug("{} is correct: {}", coordinate, isCorrect);
        return isCorrect;
    }

    /**
     * Validates if a coordinate is correct and throws an exception if it's not.
     *
     * @param coordinate the coordinate to validate
     * @throws IllegalArgumentException if the coordinate is not valid
     */
    public static void validateCoordinate(Coordinate coordinate) {
        log.trace("In method: validateCoordinate");
        if (!isCorrectCoordinate(coordinate)) {
            throw new IllegalArgumentException("Coordinate is not valid. %s".formatted(coordinate));
        }
    }

    /**
     * Validates a set of coordinates.
     *
     * @param coordinates the set of coordinates to validate
     * @throws IllegalArgumentException if any coordinate in the set is not valid
     */
    public static void validateCoordinates(Set<Coordinate> coordinates) {
        log.trace("In method: validateCoordinates");
        for (Coordinate coordinate : coordinates) {
            validateCoordinate(coordinate);
        }
    }

    /**
     * Builds the neighboring coordinates for a given coordinate.
     *
     * @param currentCoordinate the current coordinate
     * @return a set of neighboring coordinates
     */
    public static Set<Coordinate> buildNeighbourCoordinates(final Coordinate currentCoordinate) {
        log.trace("In method: buildNeighbourCoordinates(Coordinate)");
        val row = currentCoordinate.row();
        val col = currentCoordinate.column();
        return NEIGHBOUR_OFFSETS.stream().map(modifier -> {
            val neighbourRow = row + modifier.row();
            val neighbourCol = col + modifier.column();
            return Coordinate.of(neighbourRow, neighbourCol);
        }).filter(CoordinateUtils::isCorrectCoordinate).collect(Collectors.toSet());
    }

    /**
     * Builds the coordinates for a ship based on its starting coordinate and direction.
     *
     * @param coordinate the starting coordinate
     * @param ship       the ship to place
     * @return a set of coordinates occupied by the ship
     */
    public static Set<Coordinate> buildShipCoordinates(Coordinate coordinate, Ship ship) {
        log.trace("In method: buildShipCoordinates");
        Set<Coordinate> coordinates = new HashSet<>();
        val row = coordinate.row();
        val col = coordinate.column();
        val isHorizontal = ShipDirection.HORIZONTAL == ship.shipDirection();
        log.debug(isHorizontal ? "For Horizontal ship" : "For Vertical ship");
        for (int diff = 0; diff < ship.shipSize(); diff++) {
            if (isHorizontal) {
                coordinates.add(Coordinate.of(row, col + diff));
            } else {
                coordinates.add(Coordinate.of(row + diff, col));
            }
        }
        return coordinates;
    }

    /**
     * Builds the neighboring coordinates for a set of ship coordinates.
     *
     * @param shipCoordinates the set of ship coordinates
     * @return a set of neighboring coordinates
     */
    public static Set<Coordinate> buildNeighbourCoordinates(Set<Coordinate> shipCoordinates) {
        log.trace("In method: buildNeighbourCoordinates(Set<Coordinate>)");
        return shipCoordinates.stream().map(CoordinateUtils::buildNeighbourCoordinates).flatMap(Collection::stream).filter(c -> !shipCoordinates.contains(c)).collect(Collectors.toSet());
    }
}
