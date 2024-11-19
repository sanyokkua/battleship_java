package ua.kostenko.battleship.battleship.logic.engine;

import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Optional;

/**
 * Interface representing field management operations in the Battleship game.
 * <p>
 * The FieldManagement interface defines methods to manage ships on the field, make shots, and retrieve field information.
 * </p>
 *
 * @see Coordinate
 * @see Ship
 * @see Cell
 * @see ShotResult
 */
public interface FieldManagement {

    /**
     * Adds a ship to the field at the specified coordinate.
     *
     * @param coordinate the coordinate at which to add the ship
     * @param ship       the ship to be added
     */
    void addShip(Coordinate coordinate, Ship ship);

    /**
     * Removes a ship from the field at the specified coordinate.
     *
     * @param coordinate the coordinate from which to remove the ship
     * @return an {@link Optional} containing the ship ID if the ship was removed, or an empty {@link Optional} if not
     */
    Optional<String> removeShip(Coordinate coordinate);

    /**
     * Makes a shot at the specified coordinate and returns the result.
     *
     * @param coordinate the coordinate at which to make the shot
     * @return the result of the shot as a {@link ShotResult}
     */
    ShotResult makeShot(Coordinate coordinate);

    /**
     * Retrieves the entire game field.
     *
     * @return a 2D array representing the game field
     */
    Cell[][] getField();

    /**
     * Retrieves the game field with ships hidden.
     *
     * @return a 2D array representing the game field with hidden ships
     */
    Cell[][] getFieldWithHiddenShips();

    /**
     * Gets the number of undamaged cells on the field.
     *
     * @return the number of undamaged cells
     */
    int getNumberOfUndamagedCells();

    /**
     * Gets the number of ships that have not been destroyed.
     *
     * @return the number of not destroyed ships
     */
    int getNumberOfNotDestroyedShips();
}
