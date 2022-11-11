package ua.kostenko.battleship.battleship.engine.models.records;

import lombok.Builder;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.GameState;

import java.util.Set;

@Builder
public record GameStateRepresentation(
        GameEdition gameEdition,
        String sessionId,
        GameState gameState,
        Set<Player> players
) {
}
