package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GameSessionIdIsNotCorrectException extends IllegalArgumentException {
    public GameSessionIdIsNotCorrectException(final String message) {
        super(message);
    }
}
