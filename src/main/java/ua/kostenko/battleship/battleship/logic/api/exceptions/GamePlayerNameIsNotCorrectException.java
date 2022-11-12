package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GamePlayerNameIsNotCorrectException extends IllegalArgumentException {
    public GamePlayerNameIsNotCorrectException(final String message) {
        super(message);
    }
}
