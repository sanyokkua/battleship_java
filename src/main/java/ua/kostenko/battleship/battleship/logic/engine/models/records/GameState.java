package ua.kostenko.battleship.battleship.logic.engine.models.records;

import lombok.Builder;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

import java.util.Set;

@Builder
public record GameState(GameEdition gameEdition, String sessionId, GameStage gameStage, Set<Player> players,
                        String lastUpdate) {
}
