package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class IncorrectShipIdException extends IllegalArgumentException {
    public IncorrectShipIdException(final String message) {
        super(message);
    }
}
