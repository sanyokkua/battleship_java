package ua.kostenko.battleship.battleship.logic.persistence;

import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Class implementing in-memory persistence for the Battleship game.
 * <p>
 * The InMemoryPersistence class provides methods to load, save, and remove game states,
 * storing them in an in-memory database represented by a {@link Map}.
 * </p>
 *
 * @see Game
 * @see GameState
 * @see Persistence
 */
@Log4j2
public class InMemoryPersistence implements Persistence {

    /**
     * In-memory database to store game states.
     */
    private final Map<String, GameState> db;

    /**
     * Constructs a new InMemoryPersistence instance.
     */
    public InMemoryPersistence() {
        db = new HashMap<>();
    }

    /**
     * Loads a game by its unique identifier.
     *
     * @param id the unique identifier of the game to load
     * @return an {@link Optional} containing the loaded game, or an empty {@link Optional} if no game is found
     */
    @Override
    public Optional<Game> load(final String id) {
        log.trace("In method: load");
        log.debug("GameId: {}", id);
        if (StringUtils.isBlank(id)) {
            log.debug("GameId is blank, Optional.empty() will be returned");
            return Optional.empty();
        }

        val gameState = Optional.ofNullable(db.get(id));

        if (gameState.isEmpty()) {
            log.debug("gameState not found, Optional.empty() will be returned");
            return Optional.empty();
        }

        val game = Game.fromGameState(gameState.get());
        log.debug("Game {}", game);
        return Optional.of(game);
    }

    /**
     * Saves the current state of the game.
     *
     * @param gameState the state of the game to save
     * @return an {@link Optional} containing the saved game, or an empty {@link Optional} if the save operation fails
     */
    @Override
    public Optional<Game> save(final GameState gameState) {
        log.trace("In method: save");
        if (Objects.isNull(gameState)) {
            log.debug("null is passed, Optional.empty() will be returned");
            return Optional.empty();
        }
        db.put(gameState.sessionId(), GameState.create(gameState.gameEdition(), gameState.sessionId(), gameState.gameStage(), gameState.players()));
        val game = Game.fromGameState(gameState);
        log.debug("Game: {}", game);
        return Optional.of(game);
    }

    /**
     * Removes a game by its unique identifier.
     *
     * @param id the unique identifier of the game to remove
     */
    @Override
    public void remove(final String id) {
        log.trace("In method: remove");
        log.debug("gameId: {}", id);
        if (StringUtils.isNotBlank(id)) {
            db.remove(id);
            log.info("removed");
        }
    }
}
