package ua.kostenko.battleship.battleship.logic.api.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.IdGenerator;
import ua.kostenko.battleship.battleship.logic.api.ValidationUtils;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

import java.util.List;

/**
 * Implementation of the {@link GameControllerApi} interface.
 * <p>
 * The GameControllerApiImpl class is the boundary between the web layer and the framework-agnostic
 * {@link Game} engine: it validates incoming identifiers via {@link ValidationUtils}, loads and saves
 * {@link GameState} through {@link Persistence}, generates session and player IDs via {@link IdGenerator},
 * and translates the engine's unchecked failures (both the plain {@link IllegalArgumentException}/
 * {@link IllegalStateException} thrown by validation helpers and the engine's typed exceptions such as
 * {@link ua.kostenko.battleship.battleship.logic.engine.exceptions.SessionFullException},
 * {@link ua.kostenko.battleship.battleship.logic.engine.exceptions.ShipNotAvailableForAddException},
 * {@link ua.kostenko.battleship.battleship.logic.engine.exceptions.ShipsNotAllPlacedException},
 * {@link ua.kostenko.battleship.battleship.logic.engine.exceptions.PlayerNotActiveException}, and
 * {@link ua.kostenko.battleship.battleship.logic.engine.exceptions.CellAlreadyShotException}) into this
 * package's own typed exceptions, so no engine or Spring MVC types leak across the boundary.
 * </p>
 *
 * @see GameControllerApi
 * @see Game
 * @see Persistence
 * @see IdGenerator
 * @see ValidationUtils
 */
@Log4j2
@RequiredArgsConstructor
public class GameControllerApiImpl implements GameControllerApi {
    private final Persistence persistence;
    private final IdGenerator idGenerator;

    /**
     * Loads the {@link Game} for the specified session ID after validating it.
     *
     * @param sessionId the ID of the game session to load
     * @return the loaded {@link Game} instance
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     */
    private Game loadGame(final String sessionId) {
        log.debug("sessionId to load: {}", sessionId);
        ValidationUtils.validateSessionId(sessionId);

        val loaded = persistence.load(sessionId);

        if (loaded.isEmpty()) {
            throw new GameSessionIdIsNotCorrectException("Session is not found. ID: %s".formatted(sessionId));
        }

        val game = loaded.get();
        val gameState = game.getGameState();
        log.debug("sessionId is loaded: {}", gameState.sessionId());
        return game;
    }

    /**
     * Persists the current state of the specified {@link Game}.
     *
     * @param game the game whose state should be saved
     */
    private void saveGame(final Game game) {
        val gameState = game.getGameState();
        log.debug("sessionId for saving: {}", gameState.sessionId());

        persistence.save(gameState);

        log.debug("sessionId: {} is saved", gameState.sessionId());
    }

    /**
     * {@inheritDoc}
     *
     * @return a list of the available game editions ({@link GameEdition#UKRAINIAN} and
     *         {@link GameEdition#MILTON_BRADLEY})
     */
    @Override
    public List<GameEdition> getAvailableGameEditions() {
        log.debug("Returning supporting GameEditions");
        return List.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY);
    }

    /**
     * {@inheritDoc}
     *
     * @param gameEdition the edition of the game to create a session for
     * @return the generated session ID of the created game session
     * @throws GameEditionIsNotCorrectException if the game edition is blank or not a known {@link GameEdition}
     */
    @Override
    public String createGameSession(final String gameEdition) {
        ValidationUtils.validateGameEdition(gameEdition);
        val gameId = idGenerator.generateId();

        persistence.save(GameState.create(GameEdition.valueOf(gameEdition), gameId, GameStage.INITIALIZED));

        return gameId;
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId  the ID of the game session
     * @param playerName the name of the player
     * @return the created player
     * @throws GamePlayerNameIsNotCorrectException if the player name is blank
     * @throws GameSessionIdIsNotCorrectException  if the session ID is blank or no session exists for it
     * @throws GameSessionFullException            if the session already has two players
     * @throws GameStageIsNotCorrectException      if the session is not in a stage that allows adding a player
     * @throws GameInternalProblemException        if the player cannot otherwise be created
     */
    @Override
    public Player createPlayerInSession(final String sessionId, final String playerName) {
        ValidationUtils.validatePlayerName(playerName);

        val game = loadGame(sessionId);
        val playerId = idGenerator.generateId();

        try {
            val player = game.createPlayer(playerId, playerName);
            saveGame(game);
            return player;
        } catch (SessionFullException ex) {
            throw new GameSessionFullException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new GameStageIsNotCorrectException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @return the current game stage
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     */
    @Override
    public GameStage getCurrentGameStage(final String sessionId) {
        val game = loadGame(sessionId);
        return game.getGameState()
                .gameStage();
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @return the time of the last session change
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     */
    @Override
    public String getLastSessionChangeTime(final String sessionId) {
        val game = loadGame(sessionId);
        return game.getGameState()
                .lastUpdate();
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return a list of ships not on the board
     * @throws GamePlayerIdIsNotCorrectException  if the player ID is blank
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     * @throws GamePlayerNotFoundException        if no player with the given ID exists in the session
     * @throws GameInternalProblemException       if the ships cannot otherwise be retrieved
     */
    @Override
    public List<Ship> getShipsNotOnTheBoard(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        try {
            return game.getShipsNotOnTheField(playerId)
                    .stream()
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw new GamePlayerNotFoundException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId  the ID of the game session
     * @param playerId   the ID of the player
     * @param shipId     the ID of the ship to add
     * @param coordinate the coordinate at which to add the ship
     * @param direction  the direction to place the ship (HORIZONTAL or VERTICAL)
     * @return the added ship
     * @throws GamePlayerIdIsNotCorrectException            if the player ID is blank
     * @throws GameShipIdIsNotCorrectException              if the ship ID is blank, or does not match any of the
     *                                                       player's ships
     * @throws GameShipDirectionIsNotCorrectException       if the direction is blank or not a known
     *                                                       {@link ShipDirection}
     * @throws GameCoordinateIsNotCorrectIncorrectException if the coordinate is invalid
     * @throws GameSessionIdIsNotCorrectException           if the session ID is blank or no session exists for it
     * @throws GameShipAlreadyPlacedException               if the ship has already been placed on the field
     * @throws GameStageIsNotCorrectException               if the session is not in the Preparation stage
     * @throws GameInternalProblemException                 if the ship cannot otherwise be placed at the given
     *                                                       coordinate (e.g. overlapping another ship)
     */
    @Override
    public Ship addShipToField(
            final String sessionId, final String playerId, final String shipId, final Coordinate coordinate,
            final String direction) {
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateShipId(shipId);
        ValidationUtils.validateShipDirection(direction);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        try {
            val ship = game.getAllShips(playerId)
                    .stream()
                    .filter(s -> shipId.equals(s.shipId()))
                    .findAny()
                    .orElseThrow(() -> new GameShipIdIsNotCorrectException(
                            "Ship (%s) is not found in player ships".formatted(shipId)));
            val shipDirection = ShipDirection.valueOf(direction);
            game.addShipToField(playerId,
                    coordinate,
                    Ship.builder()
                            .shipId(ship.shipId())
                            .shipDirection(shipDirection)
                            .shipSize(ship.shipSize())
                            .shipType(ship.shipType())
                            .build());
            saveGame(game);

            return ship;
        } catch (GameShipIdIsNotCorrectException ex) {
            throw ex;
        } catch (ShipNotAvailableForAddException ex) {
            throw new GameShipAlreadyPlacedException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new GameStageIsNotCorrectException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId  the ID of the game session
     * @param playerId   the ID of the player
     * @param coordinate the coordinate from which to remove the ship
     * @return the ID of the removed ship, or an empty string if no ship was found at the coordinate
     * @throws GamePlayerIdIsNotCorrectException            if the player ID is blank
     * @throws GameCoordinateIsNotCorrectIncorrectException if the coordinate is invalid
     * @throws GameSessionIdIsNotCorrectException           if the session ID is blank or no session exists for it
     * @throws GameStageIsNotCorrectException               if the session is not in the Preparation stage
     * @throws GameInternalProblemException                 if the ship cannot otherwise be removed
     */
    @Override
    public String removeShipFromField(final String sessionId, final String playerId, final Coordinate coordinate) {
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        try {
            val shipId = game.removeShipFromField(playerId, coordinate);
            val ship = shipId.orElse("");

            saveGame(game);

            return ship;
        } catch (IllegalStateException ex) {
            throw new GameStageIsNotCorrectException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return the opponent information
     * @throws GamePlayerIdIsNotCorrectException  if the player ID is blank
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     * @throws GameOpponentNotFoundException      if no opponent has joined the session yet
     */
    @Override
    public OpponentInfo getOpponentInformation(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);

        try {
            val opponent = game.getOpponent(playerId);
            return new OpponentInfo(opponent.getPlayerName(), opponent.isReady());
        } catch (IllegalArgumentException ex) {
            throw new GameOpponentNotFoundException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return a 2D array representing the preparation field
     * @throws GamePlayerIdIsNotCorrectException  if the player ID is blank
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     * @throws GamePlayerNotFoundException        if no player with the given ID exists in the session
     * @throws GameInternalProblemException       if the field cannot otherwise be retrieved
     */
    @Override
    public Cell[][] getPreparationField(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);
        try {
            return game.getField(playerId);
        } catch (IllegalArgumentException ex) {
            throw new GamePlayerNotFoundException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return the player who started the game
     * @throws GamePlayerIdIsNotCorrectException  if the player ID is blank
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     * @throws GameShipsNotAllPlacedException     if the player still has ships not placed on the field
     * @throws GameStageIsNotCorrectException     if the session is not in the Preparation stage
     * @throws GameInternalProblemException       if the player cannot otherwise be marked ready
     */
    @Override
    public Player startGame(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);

        try {
            game.changePlayerStatusToReady(playerId);
            val player = game.getPlayer(playerId);

            saveGame(game);

            return player;
        } catch (ShipsNotAllPlacedException ex) {
            throw new GameShipsNotAllPlacedException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new GameStageIsNotCorrectException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the player
     * @return the gameplay state, including both players' fields (the opponent's ships are hidden unless the
     *         game is {@link GameStage#FINISHED}), alive cell/ship counts, and winner information
     * @throws GamePlayerIdIsNotCorrectException  if the player ID is blank
     * @throws GameSessionIdIsNotCorrectException if the session ID is blank or no session exists for it
     * @throws GamePlayerNotFoundException        if no player with the given ID exists in the session
     * @throws GameOpponentNotFoundException      if no opponent has joined the session yet
     */
    @Override
    public GameplayState getGameState(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);
        val currentGameStage = game.getGameState()
                .gameStage();

        Player currentPlayer;
        try {
            currentPlayer = game.getPlayer(playerId);
        } catch (IllegalArgumentException ex) {
            throw new GamePlayerNotFoundException(ex.getMessage());
        }

        Player opponentPlayer;
        try {
            opponentPlayer = game.getOpponent(playerId);
        } catch (IllegalArgumentException ex) {
            throw new GameOpponentNotFoundException(ex.getMessage());
        }

        val playerFieldManagement = currentPlayer.getFieldManagement();
        val opponentFieldManagement = opponentPlayer.getFieldManagement();

        var playerField = playerFieldManagement.getField();
        var opponentField = currentGameStage == GameStage.FINISHED ?
                opponentFieldManagement.getField() :
                opponentFieldManagement.getFieldWithHiddenShips();

        val hasWinner = currentPlayer.isWinner() || opponentPlayer.isWinner();
        var winnerName = "";
        if (hasWinner) {
            winnerName = currentPlayer.isWinner() ? currentPlayer.getPlayerName() : opponentPlayer.getPlayerName();
        }

        return GameplayState.builder()
                .playerName(currentPlayer.getPlayerName())
                .isPlayerActive(currentPlayer.isActive())
                .isPlayerWinner(currentPlayer.isWinner())
                .playerNumberOfAliveCells(playerFieldManagement.getNumberOfUndamagedCells())
                .playerNumberOfAliveShips(playerFieldManagement.getNumberOfNotDestroyedShips())
                .playerField(playerField)
                .opponentName(opponentPlayer.getPlayerName())
                .isOpponentReady(opponentPlayer.isReady())
                .opponentNumberOfAliveCells(opponentFieldManagement.getNumberOfUndamagedCells())
                .opponentNumberOfAliveShips(opponentFieldManagement.getNumberOfNotDestroyedShips())
                .opponentField(opponentField)
                .hasWinner(hasWinner)
                .winnerPlayerName(winnerName)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @param sessionId  the ID of the game session
     * @param playerId   the ID of the player
     * @param coordinate the coordinate at which to make the shot
     * @return the result of the shot as a {@link ShotResult}
     * @throws GamePlayerIdIsNotCorrectException            if the player ID is blank
     * @throws GameCoordinateIsNotCorrectIncorrectException if the coordinate is invalid
     * @throws GameSessionIdIsNotCorrectException           if the session ID is blank or no session exists for it
     * @throws GamePlayerNotActiveException                 if it is not the given player's turn
     * @throws GameCellAlreadyShotException                 if the target cell has already been shot
     * @throws GameStageIsNotCorrectException               if the session is not in the {@link GameStage#IN_GAME}
     *                                                       stage
     * @throws GameInternalProblemException                 if the shot cannot otherwise be made
     */
    @Override
    public ShotResult makeShotByField(final String sessionId, final String playerId, final Coordinate coordinate) {
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        try {
            val shotResult = game.makeShot(playerId, coordinate);
            saveGame(game);
            return shotResult;
        } catch (PlayerNotActiveException ex) {
            throw new GamePlayerNotActiveException(ex.getMessage());
        } catch (CellAlreadyShotException ex) {
            throw new GameCellAlreadyShotException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new GameStageIsNotCorrectException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }
}
