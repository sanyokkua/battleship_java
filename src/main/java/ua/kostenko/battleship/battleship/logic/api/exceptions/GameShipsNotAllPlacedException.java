package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a player attempts to start/ready up while not all of their ships have been
 * placed on the field.
 * <p>
 * This exception is used to indicate that the player still has ships left to place before they can
 * be marked ready.
 * </p>
 *
 * @see IllegalStateException
 */
public class GameShipsNotAllPlacedException extends IllegalStateException {

    /**
     * Constructs a new GameShipsNotAllPlacedException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameShipsNotAllPlacedException(final String message) {
        super(message);
    }
}
