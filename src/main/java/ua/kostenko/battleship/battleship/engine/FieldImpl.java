package ua.kostenko.battleship.battleship.engine;

import lombok.NonNull;
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

public class FieldImpl implements Field {
    private final Cell[][] field;

    public FieldImpl() {
        this.field = FieldUtil.initializeField();
    }

    private Cell getCell(@NonNull final Coordinate coordinate) {
        return this.field[coordinate.row()][coordinate.column()];
    }

    private Cell updateCell(@NonNull final Cell cell) {
        this.field[cell.coordinate().row()][cell.coordinate().column()] = cell;
        return this.field[cell.coordinate().row()][cell.coordinate().column()];
    }

    private void validateShipIntersections(Set<Coordinate> shipCoordinates) {
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
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);

        val shipCoordinates = CoordinateUtil.buildShipCoordinates(coordinate, ship);
        CoordinateUtil.validateCoordinateAndThrowException(shipCoordinates);

        validateShipIntersections(shipCoordinates);

        for (var shipCoordinate : shipCoordinates) {
            val row = shipCoordinate.row();
            val col = shipCoordinate.column();
            this.field[row][col] = Cell.builder()
                                       .coordinate(shipCoordinate)
                                       .ship(ship)
                                       .isAvailable(false)
                                       .build();
        }

        val shipNeighbourCoordinates = CoordinateUtil.buildNeighbourCoordinates(shipCoordinates);

        for (var neighbourCoordinate : shipNeighbourCoordinates) {
            val row = neighbourCoordinate.row();
            val col = neighbourCoordinate.column();
            this.field[row][col] = Cell.builder()
                                       .coordinate(neighbourCoordinate)
                                       .isAvailable(false)
                                       .build();
        }
    }

    @Override
    public Optional<String> removeShip(@NonNull final Coordinate coordinate) {
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);

        val cell = getCell(coordinate);
        if (!cell.hasShip()) {
            return Optional.empty();
        }

        val ship = cell.ship();
        val shipCells = FieldUtil.findShipCells(field, ship);
        val neighbourCells = FieldUtil.findShipNeighbourCells(field, ship);

        for (var shipCell : shipCells) {
            val row = shipCell.coordinate().row();
            val col = shipCell.coordinate().column();
            this.field[row][col] = Cell.builder()
                                       .coordinate(shipCell.coordinate())
                                       .isAvailable(true)
                                       .build();
        }
        for (var neighbourCell : neighbourCells) {
            val row = neighbourCell.coordinate().row();
            val col = neighbourCell.coordinate().column();
            this.field[row][col] = Cell.builder()
                                       .coordinate(neighbourCell.coordinate())
                                       .isAvailable(true)
                                       .build();
        }
        return Optional.of(ship.shipId());
    }

    @Override
    public ShotResult makeShot(@NonNull final Coordinate coordinate) {
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);
        val cell = updateCell(Cell.builder()
                                  .coordinate(coordinate)
                                  .ship(getCell(coordinate).ship())
                                  .hasShot(true)
                                  .build());
        if (!cell.hasShip()) {
            return ShotResult.MISS;
        }

        val shipCells = FieldUtil.findShipCells(this.field, cell.ship());
        val isDestroyed = shipCells.stream().allMatch(Cell::hasShot);
        if (isDestroyed) {
            processDestroyedShip(shipCells);
        }
        return isDestroyed ? ShotResult.DESTROYED : ShotResult.HIT;
    }

    @Override
    public Cell[][] getField() {
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
        return newField;
    }

    @Override
    public Cell[][] getFieldWithHiddenShips() {
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
        return newField;
    }

    @Override
    public int getAmountOfAliveCells() {
        final Predicate<Cell> hasShotPredicate = cell -> cell.hasShot();
        final Predicate<Cell> doesntHaveShotPredicate = hasShotPredicate.negate();
        return (int) FieldUtil.convertToFlatSet(field)
                              .stream()
                              .filter(doesntHaveShotPredicate)
                              .count();
    }

    @Override
    public int getAmountOfAliveShips() {
        return (int) FieldUtil.getShipsFromField(field).stream()
                              .map(ship -> FieldUtil.findShipCells(field, ship))
                              .filter(coordinates -> coordinates.stream().noneMatch(Cell::hasShot))
                              .count();
    }

    private void processDestroyedShip(final Set<Cell> shipCells) {
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
