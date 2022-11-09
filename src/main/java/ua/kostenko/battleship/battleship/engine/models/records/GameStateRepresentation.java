package ua.kostenko.battleship.battleship.engine.models.records;

import lombok.Builder;
import ua.kostenko.battleship.battleship.engine.config.GameType;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.GameState;

import java.util.Set;

@Builder
public record GameStateRepresentation(
        GameType gameType,
        String sessionId,
        GameState gameState,
        Set<Player> players
) {
}
