package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GameShipIdIsNotCorrectException extends IllegalArgumentException {
    public GameShipIdIsNotCorrectException(final String message) {
        super(message);
    }
}
