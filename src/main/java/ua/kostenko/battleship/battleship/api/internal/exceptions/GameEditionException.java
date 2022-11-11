package ua.kostenko.battleship.battleship.api.internal.exceptions;

public class GameEditionException extends IllegalArgumentException {
    public GameEditionException(final String message) {
        super(message);
    }
}
