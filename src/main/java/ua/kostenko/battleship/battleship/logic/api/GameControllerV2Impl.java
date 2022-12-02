package ua.kostenko.battleship.battleship.logic.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameInternalProblemException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameSessionIdIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameShipIdIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
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

@Log4j2
@RequiredArgsConstructor
public class GameControllerV2Impl implements GameControllerV2Api {
    private final Persistence persistence;
    private final IdGenerator idGenerator;

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

    private void saveGame(final Game game) {
        val gameState = game.getGameState();
        log.debug("sessionId for saving: {}", gameState.sessionId());

        persistence.save(gameState);

        log.debug("sessionId: {} is saved", gameState.sessionId());
    }

    @Override
    public List<GameEdition> getAvailableGameEditions() {
        log.debug("Returning supporting GameEditions");
        return List.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY);
    }

    @Override
    public String createGameSession(final String gameEdition) {
        ValidationUtils.validateGameEdition(gameEdition);
        val gameId = idGenerator.generateId();

        persistence.save(GameState.create(GameEdition.valueOf(gameEdition), gameId, GameStage.INITIALIZED));

        return gameId;
    }

    @Override
    public Player createPlayerInSession(final String sessionId, final String playerName) {
        ValidationUtils.validatePlayerName(playerName);

        val game = loadGame(sessionId);
        val playerId = idGenerator.generateId();

        try {
            val player = game.createPlayer(playerId, playerName);
            saveGame(game);
            return player;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    @Override
    public GameStage getCurrentGameStage(final String sessionId) {
        val game = loadGame(sessionId);
        return game.getGameState()
                   .gameStage();
    }

    @Override
    public String getLastSessionChangeTime(final String sessionId) {
        val game = loadGame(sessionId);
        return game.getGameState()
                   .lastUpdate();
    }

    @Override
    public List<Ship> getShipsNotOnTheBoard(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        try {
            return game.getShipsNotOnTheField(playerId)
                       .stream()
                       .toList();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    @Override
    public OpponentInfo getOpponentInformation(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);

        val opponent = game.getOpponent(playerId);
        return new OpponentInfo(opponent.getPlayerName(), opponent.isReady());
    }

    @Override
    public Cell[][] getPreparationField(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);
        try {
            return game.getField(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    @Override
    public Player startGame(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);

        try {
            game.changePlayerStatusToReady(playerId);
            val player = game.getPlayer(playerId);

            saveGame(game);

            return player;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }

    @Override
    public GameplayState getGameState(final String sessionId, final String playerId) {
        ValidationUtils.validatePlayerId(playerId);

        val game = loadGame(sessionId);
        val currentGameStage = game.getGameState()
                                   .gameStage();
        var currentPlayer = game.getPlayer(playerId);
        var opponentPlayer = game.getOpponent(playerId);

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

    @Override
    public ShotResult makeShotByField(final String sessionId, final String playerId, final Coordinate coordinate) {
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        try {
            val shotResult = game.makeShot(playerId, coordinate);
            saveGame(game);
            return shotResult;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }
    }
}
