package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a player name in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided player name is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GamePlayerNameIsNotCorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GamePlayerNameIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GamePlayerNameIsNotCorrectException(final String message) {
        super(message);
    }
}
