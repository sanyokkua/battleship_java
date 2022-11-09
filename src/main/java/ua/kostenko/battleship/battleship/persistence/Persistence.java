package ua.kostenko.battleship.battleship.persistence;

import ua.kostenko.battleship.battleship.engine.Game;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;

import java.util.Optional;

public interface Persistence {

    Optional<Game> load(String id);

    Optional<Game> save(GameStateRepresentation gameStateRepresentation);

    void remove(String id);
}
