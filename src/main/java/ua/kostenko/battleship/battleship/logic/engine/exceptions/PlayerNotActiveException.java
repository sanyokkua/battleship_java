package ua.kostenko.battleship.battleship.logic.engine.exceptions;

/**
 * Exception thrown when a player attempts to make a shot while not being the active player.
 * <p>
 * This exception indicates that it is not the given player's turn.
 * </p>
 *
 * @see IllegalStateException
 */
public class PlayerNotActiveException extends IllegalStateException {

    /**
     * Constructs a new PlayerNotActiveException with the specified detail message.
     *
     * @param message the detail message
     */
    public PlayerNotActiveException(final String message) {
        super(message);
    }
}
