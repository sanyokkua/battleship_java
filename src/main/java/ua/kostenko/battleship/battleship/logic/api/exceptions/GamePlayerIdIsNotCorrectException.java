package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GamePlayerIdIsNotCorrectException extends IllegalArgumentException {
    public GamePlayerIdIsNotCorrectException(final String message) {
        super(message);
    }
}
