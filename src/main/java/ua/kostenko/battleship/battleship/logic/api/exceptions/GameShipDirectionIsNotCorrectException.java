package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GameShipDirectionIsNotCorrectException extends IllegalArgumentException {
    public GameShipDirectionIsNotCorrectException(final String message) {
        super(message);
    }
}
