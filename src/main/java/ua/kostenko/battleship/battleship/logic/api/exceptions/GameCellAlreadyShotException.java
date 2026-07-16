package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a shot is attempted at a cell that has already been shot.
 * <p>
 * This exception is used to indicate that the target cell has already been resolved and cannot
 * be shot again.
 * </p>
 *
 * @see IllegalStateException
 */
public class GameCellAlreadyShotException extends IllegalStateException {

    /**
     * Constructs a new GameCellAlreadyShotException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameCellAlreadyShotException(final String message) {
        super(message);
    }
}
