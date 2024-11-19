package ua.kostenko.battleship.battleship.logic.persistence;

import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;

import java.util.Optional;

/**
 * Interface representing persistence operations for the Battleship game.
 * <p>
 * The Persistence interface defines methods to load, save, and remove game states, ensuring that the game's progress can be stored and retrieved.
 * </p>
 *
 * @see Game
 * @see GameState
 */
public interface Persistence {

    /**
     * Loads a game by its unique identifier.
     *
     * @param id the unique identifier of the game to load
     * @return an {@link Optional} containing the loaded game, or an empty {@link Optional} if no game is found
     */
    Optional<Game> load(String id);

    /**
     * Saves the current state of the game.
     *
     * @param gameState the state of the game to save
     * @return an {@link Optional} containing the saved game, or an empty {@link Optional} if the save operation fails
     */
    Optional<Game> save(GameState gameState);

    /**
     * Removes a game by its unique identifier.
     *
     * @param id the unique identifier of the game to remove
     */
    void remove(String id);
}
