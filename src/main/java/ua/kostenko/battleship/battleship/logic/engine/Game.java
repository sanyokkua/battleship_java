package ua.kostenko.battleship.battleship.logic.engine;

import lombok.NonNull;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Interface representing the core game logic for the Battleship game.
 * <p>
 * The Game interface defines methods to create players, manage ships and fields, make shots, and retrieve the game state.
 * </p>
 *
 * @see Player
 * @see Ship
 * @see Coordinate
 * @see Cell
 * @see GameState
 * @see ShotResult
 * @see FieldManagement
 */
public interface Game {

    /**
     * Creates a Game instance from a given GameState.
     *
     * @param gameState the state of the game to create
     * @return a new Game instance
     */
    static Game fromGameState(@NonNull GameState gameState) {
        return new GameImpl(gameState);
    }

    /**
     * Creates a new field management supplier.
     *
     * @return a supplier for FieldManagement instances
     */
    default Supplier<FieldManagement> newField() {
        return FieldManagementImpl::new;
    }

    /**
     * Creates a new player with the specified ID and name.
     *
     * @param playerId   the unique identifier for the player
     * @param playerName the name of the player
     * @return the created player
     */
    Player createPlayer(String playerId, String playerName);

    /**
     * Retrieves a player by their ID.
     *
     * @param playerId the unique identifier of the player to retrieve
     * @return the player with the specified ID
     */
    Player getPlayer(String playerId);

    /**
     * Retrieves the opponent of the specified player.
     *
     * @param currentPlayerId the unique identifier of the current player
     * @return the opponent player
     */
    Player getOpponent(String currentPlayerId);

    /**
     * Retrieves the ships not placed on the field for a specific player.
     *
     * @param playerId the unique identifier of the player
     * @return a set of ships not yet placed on the field
     */
    Set<Ship> getShipsNotOnTheField(String playerId);

    /**
     * Retrieves all ships for a specific player.
     *
     * @param playerId the unique identifier of the player
     * @return a set of all ships the player owns
     */
    Set<Ship> getAllShips(String playerId);

    /**
     * Adds a ship to the field at the specified coordinate for a specific player.
     *
     * @param playerId   the unique identifier of the player
     * @param coordinate the coordinate at which to add the ship
     * @param ship       the ship to add
     */
    void addShipToField(String playerId, Coordinate coordinate, Ship ship);

    /**
     * Removes a ship from the field at the specified coordinate for a specific player.
     *
     * @param playerId   the unique identifier of the player
     * @param coordinate the coordinate from which to remove the ship
     * @return an {@link Optional} containing the ship ID if the ship was removed, or an empty {@link Optional} if not
     */
    Optional<String> removeShipFromField(String playerId, Coordinate coordinate);

    /**
     * Changes the status of a player to ready.
     *
     * @param playerId the unique identifier of the player
     */
    void changePlayerStatusToReady(String playerId);

    /**
     * Makes a shot at the specified coordinate on the opponent's field and returns the result.
     *
     * @param currentPlayerId         the unique identifier of the current player
     * @param opponentFieldCoordinate the coordinate on the opponent's field to make the shot
     * @return the result of the shot as a {@link ShotResult}
     */
    ShotResult makeShot(String currentPlayerId, Coordinate opponentFieldCoordinate);

    /**
     * Retrieves the set of all players in the game.
     *
     * @return a set of all players
     */
    Set<Player> getPlayers();

    /**
     * Retrieves the field for a specific player.
     *
     * @param playerId the unique identifier of the player
     * @return a 2D array representing the player's field
     */
    Cell[][] getField(String playerId);

    /**
     * Retrieves the field for the opponent of a specific player.
     *
     * @param currentPlayerId the unique identifier of the current player
     * @return a 2D array representing the opponent's field
     */
    Cell[][] getOpponentField(String currentPlayerId);

    /**
     * Retrieves the winner of the game, if there is one.
     *
     * @return an {@link Optional} containing the winning player, or an empty {@link Optional} if there is no winner
     */
    Optional<Player> getWinner();

    /**
     * Retrieves the current state of the game.
     *
     * @return the current GameState
     */
    GameState getGameState();
}
