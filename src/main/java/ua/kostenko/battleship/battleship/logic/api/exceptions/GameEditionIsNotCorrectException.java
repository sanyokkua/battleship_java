package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a game edition in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided game edition is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameEditionIsNotCorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GameEditionIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameEditionIsNotCorrectException(final String message) {
        super(message);
    }
}
