package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectSessionIdException extends IllegalArgumentException {
    public IncorrectSessionIdException(final String message) {
        super(message);
    }
}
