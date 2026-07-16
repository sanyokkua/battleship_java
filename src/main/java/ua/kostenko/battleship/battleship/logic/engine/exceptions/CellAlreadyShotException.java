package ua.kostenko.battleship.battleship.logic.engine.exceptions;

/**
 * Exception thrown when a shot is attempted at a cell that has already been shot.
 * <p>
 * This exception indicates that the target cell has already been resolved (hit, miss, or
 * destroyed/moat) and cannot be shot again.
 * </p>
 *
 * @see IllegalStateException
 */
public class CellAlreadyShotException extends IllegalStateException {

    /**
     * Constructs a new CellAlreadyShotException with the specified detail message.
     *
     * @param message the detail message
     */
    public CellAlreadyShotException(final String message) {
        super(message);
    }
}
