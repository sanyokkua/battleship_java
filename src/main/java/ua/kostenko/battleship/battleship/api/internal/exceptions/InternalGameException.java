package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class InternalGameException extends RuntimeException {
    public InternalGameException(final String message) {
        super(message);
    }
}
