package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a ship that has already been placed on the field is submitted again for
 * placement.
 * <p>
 * This exception is used to indicate that the requested ship is not available for the add
 * operation because it is already placed on the field.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameShipAlreadyPlacedException extends IllegalArgumentException {

    /**
     * Constructs a new GameShipAlreadyPlacedException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameShipAlreadyPlacedException(final String message) {
        super(message);
    }
}
