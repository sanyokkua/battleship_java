package ua.kostenko.battleship.battleship.logic.api.exceptions;

public class GameStageIsNotCorrectException extends IllegalStateException {
    public GameStageIsNotCorrectException(final String message) {
        super(message);
    }
}
