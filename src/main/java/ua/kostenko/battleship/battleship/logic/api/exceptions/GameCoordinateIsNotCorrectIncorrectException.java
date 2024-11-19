package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a coordinate in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided coordinate is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameCoordinateIsNotCorrectIncorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GameCoordinateIsNotCorrectIncorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameCoordinateIsNotCorrectIncorrectException(final String message) {
        super(message);
    }
}
