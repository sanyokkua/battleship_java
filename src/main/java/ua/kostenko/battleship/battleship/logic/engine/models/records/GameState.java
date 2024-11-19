package ua.kostenko.battleship.battleship.logic.engine.models.records;

import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Record representing the state of a game session in the Battleship game.
 * <p>
 * The GameState encapsulates the edition of the game, the session ID, the current stage of the game,
 * the set of players, and the last update timestamp.
 * </p>
 *
 * @param gameEdition the edition of the game being played
 * @param sessionId   the unique session identifier for the game
 * @param gameStage   the current stage of the game, defined by {@link GameStage}
 * @param players     the set of players currently in the game
 * @param lastUpdate  the timestamp of the last update to the game state
 * @see GameEdition
 * @see GameStage
 * @see Player
 */
public record GameState(GameEdition gameEdition, String sessionId, GameStage gameStage, Set<Player> players,
                        String lastUpdate) {

    /**
     * Creates a new GameState instance with the specified parameters and the current timestamp.
     *
     * @param gameEdition the edition of the game being played
     * @param sessionId   the unique session identifier for the game
     * @param gameStage   the current stage of the game
     * @param players     the set of players currently in the game
     * @return a new GameState instance
     */
    public static GameState create(GameEdition gameEdition, String sessionId, GameStage gameStage, Set<Player> players) {
        return new GameState(gameEdition, sessionId, gameStage, players, LocalDateTime.now().toString());
    }

    /**
     * Creates a new GameState instance with the specified parameters and an empty set of players.
     *
     * @param gameEdition the edition of the game being played
     * @param sessionId   the unique session identifier for the game
     * @param gameStage   the current stage of the game
     * @return a new GameState instance
     */
    public static GameState create(GameEdition gameEdition, String sessionId, GameStage gameStage) {
        return create(gameEdition, sessionId, gameStage, new HashSet<>());
    }
}
