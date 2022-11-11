package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectPlayerIdException extends IllegalArgumentException {
    public IncorrectPlayerIdException(final String message) {
        super(message);
    }
}
