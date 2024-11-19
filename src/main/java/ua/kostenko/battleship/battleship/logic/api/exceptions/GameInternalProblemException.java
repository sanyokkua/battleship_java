package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when an internal problem occurs in the Battleship game.
 * <p>
 * This exception is used to indicate that an unexpected internal problem has occurred during the game's execution.
 * </p>
 *
 * @see RuntimeException
 */
public class GameInternalProblemException extends RuntimeException {

    /**
     * Constructs a new GameInternalProblemException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameInternalProblemException(final String message) {
        super(message);
    }
}
