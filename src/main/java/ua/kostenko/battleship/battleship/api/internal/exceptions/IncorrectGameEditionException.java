package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectGameEditionException extends IllegalArgumentException {
    public IncorrectGameEditionException(final String message) {
        super(message);
    }
}
