package ua.kostenko.battleship.battleship.logic.api.exceptions;

import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

public class GameStageIsNotCorrectException extends IllegalStateException {
    public GameStageIsNotCorrectException(final String message) {
        super(message);
    }

    public GameStageIsNotCorrectException(final GameStage gameStage, final String message) {
        super("Current game state: %s is not support operation. %s".formatted(gameStage, message));
    }
}
