package ua.kostenko.battleship.battleship.api;

import ua.kostenko.battleship.battleship.engine.config.GameType;

import java.util.Set;

public interface ControllerApi extends GameSessionControllerApi, GamePreparationControllerApi,
                                       GameplayControllerApi {
    default Set<GameType> getGameTypes() {
        return Set.of(GameType.CLASSIC, GameType.CUSTOM);
    }
}
