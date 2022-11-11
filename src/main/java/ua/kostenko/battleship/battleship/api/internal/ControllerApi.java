package ua.kostenko.battleship.battleship.api.internal;

import ua.kostenko.battleship.battleship.engine.config.GameEdition;

import java.util.Set;

public interface ControllerApi extends GameSessionControllerApi, GamePreparationControllerApi,
                                       GameplayControllerApi {
    default Set<GameEdition> getGameTypes() {
        return Set.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY);
    }
}
