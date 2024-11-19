package ua.kostenko.battleship.battleship.logic.api.exceptions;

/**
 * Exception thrown when a ship ID in the Battleship game is not correct.
 * <p>
 * This exception is used to indicate that a provided ship ID is invalid according to the game rules.
 * </p>
 *
 * @see IllegalArgumentException
 */
public class GameShipIdIsNotCorrectException extends IllegalArgumentException {

    /**
     * Constructs a new GameShipIdIsNotCorrectException with the specified detail message.
     *
     * @param message the detail message
     */
    public GameShipIdIsNotCorrectException(final String message) {
        super(message);
    }
}
