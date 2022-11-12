package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GameInternalProblemException extends RuntimeException {
    public GameInternalProblemException(final String message) {
        super(message);
    }
}
