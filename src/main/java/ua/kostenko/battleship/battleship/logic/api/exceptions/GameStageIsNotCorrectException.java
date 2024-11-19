package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a game stage in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided game stage is invalid according to the game rules.
 * </p>
 *
 * @see IllegalStateException
 */
public class GameStageIsNotCorrectException extends IllegalStateException {

    /**
     * Constructs a new GameStageIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameStageIsNotCorrectException(final String message) {
        super(message);
    }
}
