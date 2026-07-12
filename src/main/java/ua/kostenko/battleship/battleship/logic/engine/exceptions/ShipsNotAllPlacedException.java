package ua.kostenko.battleship.battleship.logic.engine.exceptions;

/**
 * Exception thrown when a player attempts to become ready while not all of their ships have been
 * placed on the field.
 * <p>
 * This exception indicates that the player still has ships left in {@code shipsNotOnTheField} and
 * therefore cannot transition to the ready state.
 * </p>
 *
 * @see IllegalStateException
 */
public class ShipsNotAllPlacedException extends IllegalStateException {

    /**
     * Constructs a new ShipsNotAllPlacedException with the specified detail message.
     *
     * @param message the detail message
     */
    public ShipsNotAllPlacedException(final String message) {
        super(message);
    }
}
