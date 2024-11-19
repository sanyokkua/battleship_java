package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a player ID in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided player ID is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GamePlayerIdIsNotCorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GamePlayerIdIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GamePlayerIdIsNotCorrectException(final String message) {
        super(message);
    }
}
