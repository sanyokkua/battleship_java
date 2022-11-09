package ua.kostenko.battleship.battleship.persistence;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.engine.Game;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class InMemoryPersistence implements Persistence {
    private final Map<String, GameStateRepresentation> db;

    public InMemoryPersistence() {
        db = new HashMap<>();
    }

    @Override
    public Optional<Game> load(final String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }

        val gameState = Optional.ofNullable(db.get(id));

        if (gameState.isEmpty()) {
            return Optional.empty();
        }

        val game = Game.wrap(gameState.get());
        return Optional.of(game);
    }

    @Override
    public Optional<Game> save(final GameStateRepresentation gameStateRepresentation) {
        if (Objects.isNull(gameStateRepresentation)) {
            return Optional.empty();
        }
        db.put(gameStateRepresentation.sessionId(), gameStateRepresentation);
        val game = Game.wrap(gameStateRepresentation);
        return Optional.of(game);
    }

    @Override
    public void remove(final String id) {
        if (StringUtils.isNotBlank(id)) {
            db.remove(id);
        }
    }
}
