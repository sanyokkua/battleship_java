package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a ship direction in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided ship direction is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameShipDirectionIsNotCorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GameShipDirectionIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameShipDirectionIsNotCorrectException(final String message) {
        super(message);
    }
}
