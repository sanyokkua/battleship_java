package ua.kostenko.battleship.battleship.logic.engine.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

import java.util.Set;

/**
 * Utility class for handling game-related validations in the Battleship game.
 * <p>
 * The GameUtils class provides various methods for validating player IDs, player names, game stages,
 * and the number of players in a game session.
 * </p>
 *
 * @see Player
 * @see GameStage
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GameUtils {

    /**
     * Validates if the player ID is not blank.
     *
     * @param playerId the player ID to validate
     * @throws IllegalArgumentException if the player ID is blank
     */
    public static void validatePlayerId(String playerId) {
        if (StringUtils.isBlank(playerId)) {
            throw new IllegalArgumentException("PlayerId can't be blank");
        }
    }

    /**
     * Validates if the player name is not blank.
     *
     * @param playerName the player name to validate
     * @throws IllegalArgumentException if the player name is blank
     */
    public static void validatePlayerName(String playerName) {
        if (StringUtils.isBlank(playerName)) {
            throw new IllegalArgumentException("PlayerName can't be blank");
        }
    }

    /**
     * Validates if the current game stage is one of the allowed stages.
     *
     * @param currentGameStage the current game stage to validate
     * @param errorMessage     the error message to include if the validation fails
     * @param allowed          the allowed game stages
     * @throws IllegalStateException if the current game stage is not allowed
     */
    public static void validateGameStage(GameStage currentGameStage, String errorMessage, GameStage... allowed) {
        if (!Set.of(allowed).contains(currentGameStage)) {
            throw new IllegalStateException("%s doesn't allow operation. %s".formatted(currentGameStage, errorMessage));
        }
    }

    /**
     * Validates if the number of players in the game is not more than two.
     *
     * @param players the set of players to validate
     * @throws IllegalStateException if there are more than two players
     */
    public static void validateNumberOfPlayers(Set<Player> players) {
        if (players.size() >= 2) {
            throw new IllegalStateException("Game can't have more than 2 players");
        }
    }
}
