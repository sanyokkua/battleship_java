package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a player with the provided player ID cannot be found in the game session.
 * <p>
 * This exception is used to indicate that the given player ID does not correspond to any player
 * currently in the game (e.g. a stale or mismatched player ID from the client).
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GamePlayerNotFoundException extends IllegalArgumentException {

    /**
     * Constructs a new GamePlayerNotFoundException with the specified detail message.
     *
     * @param message the detail message
     */
    public GamePlayerNotFoundException(final String message) {
        super(message);
    }
}
