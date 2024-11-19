package ua.kostenko.battleship.battleship.logic.api;

import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.List;

/**
 * Interface representing the Game Controller API for the Battleship game.
 * <p>
 * The GameControllerApi interface defines methods to manage game sessions, players, ships, and gameplay states.
 * </p>
 *
 * @see GameEdition
 * @see GameplayState
 * @see OpponentInfo
 * @see Player
 * @see GameStage
 * @see ShotResult
 * @see Cell
 * @see Coordinate
 * @see Ship
 */
public interface GameControllerApi {

    /**
     * Retrieves the list of available game editions.
     *
     * @return a list of available game editions
     */
    List<GameEdition> getAvailableGameEditions();

    /**
     * Creates a new game session with the specified game edition.
     *
     * @param gameEdition the edition of the game to create a session for
     * @return the session ID of the created game session
     */
    String createGameSession(String gameEdition);

    /**
     * Creates a new player in the specified game session.
     *
     * @param sessionId  the ID of the game session
     * @param playerName the name of the player
     * @return the created player
     */
    Player createPlayerInSession(String sessionId, String playerName);

    /**
     * Retrieves the current game stage of the specified session.
     *
     * @param sessionId the ID of the game session
     * @return the current game stage
     */
    GameStage getCurrentGameStage(String sessionId);

    /**
     * Retrieves the time of the last session change.
     *
     * @param sessionId the ID of the game session
     * @return the time of the last session change
     */
    String getLastSessionChangeTime(String sessionId);

    /**
     * Retrieves the list of ships not placed on the board for a specific player in the session.
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return a list of ships not on the board
     */
    List<Ship> getShipsNotOnTheBoard(String sessionId, String playerId);

    /**
     * Adds a ship to the field at the specified coordinate for a specific player in the session.
     *
     * @param sessionId  the ID of the game session
     * @param playerId   the ID of the player
     * @param shipId     the ID of the ship to add
     * @param coordinate the coordinate at which to add the ship
     * @param direction  the direction to place the ship (HORIZONTAL or VERTICAL)
     * @return the added ship
     */
    Ship addShipToField(String sessionId, String playerId, String shipId, Coordinate coordinate, String direction);

    /**
     * Removes a ship from the field at the specified coordinate for a specific player in the session.
     *
     * @param sessionId  the ID of the game session
     * @param playerId   the ID of the player
     * @param coordinate the coordinate from which to remove the ship
     * @return the ID of the removed ship
     */
    String removeShipFromField(String sessionId, String playerId, Coordinate coordinate);

    /**
     * Retrieves the opponent information for a specific player in the session.
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return the opponent information
     */
    OpponentInfo getOpponentInformation(String sessionId, String playerId);

    /**
     * Retrieves the preparation field for a specific player in the session.
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return a 2D array representing the preparation field
     */
    Cell[][] getPreparationField(String sessionId, String playerId);

    /**
     * Starts the game for a specific player in the session.
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return the player who started the game
     */
    Player startGame(String sessionId, String playerId);

    /**
     * Retrieves the gameplay state for a specific player in the session.
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return the gameplay state
     */
    GameplayState getGameState(String sessionId, String playerId);

    /**
     * Makes a shot at the specified coordinate for a specific player in the session and returns the result.
     *
     * @param sessionId  the ID of the game session
     * @param playerId   the ID of the player
     * @param coordinate the coordinate at which to make the shot
     * @return the result of the shot as a {@link ShotResult}
     */
    ShotResult makeShotByField(String sessionId, String playerId, Coordinate coordinate);
}
