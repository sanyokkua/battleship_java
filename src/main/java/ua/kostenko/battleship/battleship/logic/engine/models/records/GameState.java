package ua.kostenko.battleship.battleship.logic.engine.models.records;

import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public record GameState(GameEdition gameEdition, String sessionId, GameStage gameStage, Set<Player> players,
                        String lastUpdate) {

    public static GameState create(
            GameEdition gameEdition, String sessionId, GameStage gameStage, Set<Player> players) {
        return new GameState(gameEdition,
                             sessionId,
                             gameStage,
                             players,
                             LocalDateTime.now()
                                          .toString());
    }

    public static GameState create(GameEdition gameEdition, String sessionId, GameStage gameStage) {
        return create(gameEdition, sessionId, gameStage, new HashSet<>());
    }

}
