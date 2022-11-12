package ua.kostenko.battleship.battleship.logic.persistence;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class InMemoryPersistence implements Persistence {
    private final Map<String, GameState> db;

    public InMemoryPersistence() {
        db = new HashMap<>();
    }

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

    @Override
    public Optional<Game> save(final GameState gameState) {
        log.trace("In method: save");
        if (Objects.isNull(gameState)) {
            log.debug("null is passed, Optional.empty() will be returned");
            return Optional.empty();
        }
        db.put(gameState.sessionId(), gameState);
        val game = Game.fromGameState(gameState);
        log.debug("Game: {}", game);
        return Optional.of(game);
    }

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
