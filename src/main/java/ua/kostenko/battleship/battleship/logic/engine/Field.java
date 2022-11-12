package ua.kostenko.battleship.battleship.logic.engine;

import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Optional;

public interface Field {

    void addShip(Coordinate coordinate, Ship ship);

    Optional<String> removeShip(Coordinate coordinate);

    ShotResult makeShot(Coordinate coordinate);

    Cell[][] getField();

    Cell[][] getFieldWithHiddenShips();

    int getNumberOfUndamagedCells();

    int getNumberOfNotDestroyedShips();
}
