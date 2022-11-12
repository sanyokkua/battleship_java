package ua.kostenko.battleship.battleship.logic.engine.utils;

import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

import java.util.Set;

public class GameUtils {

    public static void validatePlayerId(String playerId) {
        if (StringUtils.isBlank(playerId)) {
            throw new IllegalArgumentException("PlayerId can't be blank");
        }
    }

    public static void validatePlayerName(String playerName) {
        if (StringUtils.isBlank(playerName)) {
            throw new IllegalArgumentException("PlayerName can't be blank");
        }
    }

    public static void validateGameStage(GameStage currentGameStage, String errorMessage, GameStage... allowed) {
        if (!Set.of(allowed)
                .contains(currentGameStage)) {
            throw new IllegalStateException("%s doesn't allow operation. %s".formatted(currentGameStage, errorMessage));
        }
    }

    public static void validateNumberOfPlayers(Set<Player> players) {
        if (players.size() >= 2) {
            throw new IllegalStateException("Game can't have more than 2 players");
        }
    }
}
