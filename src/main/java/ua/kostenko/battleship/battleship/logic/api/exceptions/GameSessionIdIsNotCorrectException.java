package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a game session ID in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided game session ID is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameSessionIdIsNotCorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GameSessionIdIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameSessionIdIsNotCorrectException(final String message) {
        super(message);
    }
}
