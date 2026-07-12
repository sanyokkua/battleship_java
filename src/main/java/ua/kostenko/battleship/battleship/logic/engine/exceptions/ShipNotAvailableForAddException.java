package ua.kostenko.battleship.battleship.logic.engine.exceptions;

/**
 * Exception thrown when a ship that is not available for placement (e.g. it has already been
 * placed on the field) is passed to the add-ship operation.
 * <p>
 * This exception indicates that the requested ship is not present in the player's
 * {@code shipsNotOnTheField} collection and therefore cannot be added again.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class ShipNotAvailableForAddException extends IllegalArgumentException {

    /**
     * Constructs a new ShipNotAvailableForAddException with the specified detail message.
     *
     * @param message the detail message
     */
    public ShipNotAvailableForAddException(final String message) {
        super(message);
    }
}
