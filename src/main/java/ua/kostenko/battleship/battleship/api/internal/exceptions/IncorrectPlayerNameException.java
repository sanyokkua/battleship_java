package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectPlayerNameException extends IllegalArgumentException {
    public IncorrectPlayerNameException(final String message) {
        super(message);
    }
}
