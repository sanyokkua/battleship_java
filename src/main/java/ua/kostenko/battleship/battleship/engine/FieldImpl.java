package ua.kostenko.battleship.battleship.engine;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.engine.utils.CoordinateUtil;
import ua.kostenko.battleship.battleship.engine.utils.FieldUtil;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ua.kostenko.battleship.battleship.engine.config.GameConfig.NUMBER_OF_COLUMNS;
import static ua.kostenko.battleship.battleship.engine.config.GameConfig.NUMBER_OF_ROWS;

@Slf4j
public class FieldImpl implements Field {
    private final Cell[][] field;

    public FieldImpl() {
        log.trace("Initialized field in constructor");
        this.field = FieldUtil.initializeField();
    }

    private Cell getCell(@NonNull final Coordinate coordinate) {
        log.trace("In method: getCell");
        log.debug("get cell for coordinate: {}", coordinate);
        return this.field[coordinate.row()][coordinate.column()];
    }

    private Cell updateCell(@NonNull final Cell cell) {
        log.trace("In method: updateCell");
        log.debug("Updating cell {}", cell);
        this.field[cell.coordinate().row()][cell.coordinate().column()] = cell;
        return this.field[cell.coordinate().row()][cell.coordinate().column()];
    }

    private void validateShipIntersections(Set<Coordinate> shipCoordinates) {
        log.trace("In method: validateShipIntersections");
        val neighbourCoordinates = CoordinateUtil.buildNeighbourCoordinates(shipCoordinates);

        val shipCoordinatesRegion = new HashSet<>(shipCoordinates);
        shipCoordinatesRegion.addAll(neighbourCoordinates);
        val shipCoordinatesAlreadyOccupied = shipCoordinatesRegion.stream()
                                                                  .map(this::getCell)
                                                                  .anyMatch(Cell::hasShip);
        if (shipCoordinatesAlreadyOccupied) {
            throw new IllegalArgumentException("Ship can't be added. Intersection with other.");
        }
    }

    @Override
    public void addShip(@NonNull final Coordinate coordinate, @NonNull final Ship ship) {
        log.trace("In method: addShip");
        log.debug("coordinate: {}, ship: {}", coordinate, ship);
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);

        val shipCoordinates = CoordinateUtil.buildShipCoordinates(coordinate, ship);
        CoordinateUtil.validateCoordinateAndThrowException(shipCoordinates);

        validateShipIntersections(shipCoordinates);

        shipCoordinates.forEach(c -> updateCell(Cell.builder()
                                                    .coordinate(c)
                                                    .ship(ship)
                                                    .isAvailable(false)
                                                    .build()));
        val shipNeighbourCoordinates = CoordinateUtil.buildNeighbourCoordinates(shipCoordinates);
        shipNeighbourCoordinates.forEach(
                neighbourCoordinate -> updateCell(Cell.builder()
                                                      .coordinate(neighbourCoordinate)
                                                      .isAvailable(false)
                                                      .build()));
    }

    @Override
    public Optional<String> removeShip(@NonNull final Coordinate coordinate) {
        log.trace("In method: removeShip");
        log.debug("remove ship, coordinate: {}", coordinate);
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);

        val cell = getCell(coordinate);
        if (!cell.hasShip()) {
            log.debug("cell doesn't have ship, Optional.empty() will be returned");
            return Optional.empty();
        }

        val ship = cell.ship();
        val shipCells = FieldUtil.findShipCells(field, ship);
        val neighbourCells = FieldUtil.findShipNeighbourCells(field, ship);

        shipCells.forEach(shipCell -> updateCell(Cell.builder()
                                                     .coordinate(shipCell.coordinate())
                                                     .isAvailable(true)
                                                     .build()));
        neighbourCells.forEach(
                neighbourCell -> updateCell(Cell.builder()
                                                .coordinate(neighbourCell.coordinate())
                                                .isAvailable(true)
                                                .build()));
        return Optional.of(ship.shipId());
    }

    @Override
    public ShotResult makeShot(@NonNull final Coordinate coordinate) {
        log.trace("In method: makeShot");
        log.debug("Shot to coordinate: {}", coordinate);
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);
        val cell = updateCell(Cell.builder()
                                  .coordinate(coordinate)
                                  .ship(getCell(coordinate).ship())
                                  .hasShot(true)
                                  .build());
        if (!cell.hasShip()) {
            log.debug("Cell doesn't have a ship, shot result is MISS");
            return ShotResult.MISS;
        }

        val shipCells = FieldUtil.findShipCells(this.field, cell.ship());
        val isDestroyed = shipCells.stream().allMatch(Cell::hasShot);
        log.debug("ship is destroyed: {}", isDestroyed);
        if (isDestroyed) {
            log.debug("Additional processing of neighbour cells will be started");
            processDestroyedShip(shipCells);
        }
        val result = isDestroyed ? ShotResult.DESTROYED : ShotResult.HIT;
        log.debug("ShotResult: {}", result);
        return result;
    }

    @Override
    public Cell[][] getField() {
        log.trace("In method: getField");
        Cell[][] newField = new Cell[NUMBER_OF_ROWS][NUMBER_OF_COLUMNS];
        for (int i = 0; i < NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < NUMBER_OF_COLUMNS; j++) {
                final Coordinate coordinate = Coordinate.of(i, j);
                val cell = getCell(coordinate);
                newField[i][j] = Cell.builder()
                                     .coordinate(coordinate)
                                     .ship(cell.ship())
                                     .hasShot(cell.hasShot())
                                     .isAvailable(cell.isAvailable())
                                     .build();
            }
        }
        log.debug("Copy of the field will be returned");
        return newField;
    }

    @Override
    public Cell[][] getFieldWithHiddenShips() {
        log.trace("In method: getFieldWithHiddenShips");
        Cell[][] newField = new Cell[NUMBER_OF_ROWS][NUMBER_OF_COLUMNS];
        for (int i = 0; i < NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < NUMBER_OF_COLUMNS; j++) {
                final Coordinate coordinate = Coordinate.of(i, j);
                val cell = getCell(coordinate);
                val ship = cell.hasShot() && cell.hasShip() ? cell.ship() : null;
                newField[i][j] = Cell.builder()
                                     .coordinate(coordinate)
                                     .ship(ship)
                                     .hasShot(cell.hasShot())
                                     .isAvailable(false)
                                     .build();
            }
        }
        log.debug("Copy of the field with hidden ships will be returned");
        return newField;
    }

    @Override
    public int getAmountOfAliveCells() {
        log.trace("In method: getAmountOfAliveCells");
        final Predicate<Cell> hasShotPredicate = Cell::hasShot;
        final Predicate<Cell> doesntHaveShotPredicate = hasShotPredicate.negate();
        return (int) FieldUtil.convertToFlatSet(field)
                              .stream()
                              .filter(doesntHaveShotPredicate)
                              .count();
    }

    @Override
    public int getAmountOfAliveShips() {
        log.trace("In method: getAmountOfAliveShips");
        return (int) FieldUtil.getShipsFromField(field).stream()
                              .map(ship -> FieldUtil.findShipCells(field, ship))
                              .filter(s -> s.stream().anyMatch(c -> !c.hasShot()))
                              .count();
    }

    private void processDestroyedShip(final Set<Cell> shipCells) {
        log.trace("In method: processDestroyedShip");
        val shipCoordinates = shipCells.stream()
                                       .map(Cell::coordinate)
                                       .collect(Collectors.toSet());
        val neighbourCoordinates = CoordinateUtil.buildNeighbourCoordinates(shipCoordinates)
                                                 .stream()
                                                 .map(this::getCell)
                                                 .collect(Collectors.toSet());
        neighbourCoordinates.forEach(c -> updateCell(Cell.builder()
                                                         .coordinate(c.coordinate())
                                                         .ship(c.ship())
                                                         .hasShot(true)
                                                         .build()));
    }
}