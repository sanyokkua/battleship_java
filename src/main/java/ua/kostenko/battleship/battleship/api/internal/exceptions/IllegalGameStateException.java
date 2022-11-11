package ua.kostenko.battleship.battleship.api.internal.exceptions;

import ua.kostenko.battleship.battleship.engine.models.enums.GameState;

public class IllegalGameStateException extends IllegalStateException {
    public IllegalGameStateException(final String message) {
        super(message);
    }

    public IllegalGameStateException(final GameState gameState, final String message) {
        super("Current game state: %s is not support operation. %s".formatted(gameState, message));
    }
}
