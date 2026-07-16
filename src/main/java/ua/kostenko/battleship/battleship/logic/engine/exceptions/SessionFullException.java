package ua.kostenko.battleship.battleship.logic.engine.exceptions;

/**
 * Exception thrown when an attempt is made to add a player to a game session that already has the
 * maximum number of players.
 * <p>
 * This exception indicates that the session already has two players and cannot accept another one.
 * </p>
 *
 * @see IllegalStateException
 */
public class SessionFullException extends IllegalStateException {

    /**
     * Constructs a new SessionFullException with the specified detail message.
     *
     * @param message the detail message
     */
    public SessionFullException(final String message) {
        super(message);
    }
}
