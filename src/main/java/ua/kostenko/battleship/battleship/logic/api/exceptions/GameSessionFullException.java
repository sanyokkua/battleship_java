package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when an attempt is made to join a game session that already has the maximum
 * number of players.
 * <p>
 * This exception is used to indicate that the session already has two players and cannot accept
 * another one.
 * </p>
 *
 * @see IllegalStateException
 */
public class GameSessionFullException extends IllegalStateException {

    /**
     * Constructs a new GameSessionFullException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameSessionFullException(final String message) {
        super(message);
    }
}
