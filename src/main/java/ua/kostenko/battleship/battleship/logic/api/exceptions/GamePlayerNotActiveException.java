package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a player attempts to make a shot while not being the active player.
 * <p>
 * This exception is used to indicate that it is not the given player's turn.
 * </p>
 *
 * @see IllegalStateException
 */
public class GamePlayerNotActiveException extends IllegalStateException {

    /**
     * Constructs a new GamePlayerNotActiveException with the specified detail message.
     *
     * @param message the detail message
     */
    public GamePlayerNotActiveException(final String message) {
        super(message);
    }
}
