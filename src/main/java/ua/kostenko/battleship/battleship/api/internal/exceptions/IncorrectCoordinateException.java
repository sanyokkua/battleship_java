package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectCoordinateException extends IllegalArgumentException {
    public IncorrectCoordinateException(final String message) {
        super(message);
    }
}
