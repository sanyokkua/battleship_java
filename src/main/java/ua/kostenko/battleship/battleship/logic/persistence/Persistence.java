package ua.kostenko.battleship.battleship.logic.persistence;

import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;

import java.util.Optional;

public interface Persistence {

    Optional<Game> load(String id);

    Optional<Game> save(GameState gameState);

    void remove(String id);
}
