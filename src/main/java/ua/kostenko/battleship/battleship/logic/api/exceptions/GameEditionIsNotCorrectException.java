package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GameEditionIsNotCorrectException extends IllegalArgumentException {
    public GameEditionIsNotCorrectException(final String message) {
        super(message);
    }
}
