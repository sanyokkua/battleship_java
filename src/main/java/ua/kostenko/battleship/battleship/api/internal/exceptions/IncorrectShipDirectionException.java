package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectShipDirectionException extends IllegalArgumentException {
    public IncorrectShipDirectionException(final String message) {
        super(message);
    }
}
