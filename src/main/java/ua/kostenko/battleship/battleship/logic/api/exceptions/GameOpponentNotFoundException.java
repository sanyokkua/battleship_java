package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when the opponent of the given player cannot be found in the game session.
 * <p>
 * This exception is used to indicate that a second player has not joined the game session yet
 * (e.g. a solo player polling for opponent information before anyone else has joined).
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameOpponentNotFoundException extends IllegalArgumentException {

    /**
     * Constructs a new GameOpponentNotFoundException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameOpponentNotFoundException(final String message) {
        super(message);
    }
}
