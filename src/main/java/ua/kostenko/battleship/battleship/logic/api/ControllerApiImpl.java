package ua.kostenko.battleship.battleship.logic.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.logic.api.dtos.*;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameInternalProblemException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameSessionIdIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameShipIdIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameStageIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.engine.Game;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.logic.persistence.Persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
public class ControllerApiImpl implements ControllerApi {
    private final Persistence persistence;

    private Game loadGame(final String sessionId) {
        log.debug("sessionId to load: {}", sessionId);
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
        final GameState gameState = game.getGameState();
        log.debug("sessionId for saving: {}", gameState.sessionId());

        persistence.save(gameState);

        log.debug("sessionId: {} is saved", gameState.sessionId());
    }

    @Override
    public ResponseEntity<GameEditionsDto> getGameEditions() {
        log.debug("Returning supporting GameEditions");
        return ResponseEntity.ok(new GameEditionsDto(List.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY)));
    }

    @Override
    public ResponseEntity<GameSessionIdDto> createGameSession(final String gameEdition) {
        ValidationUtils.validateGameEdition(gameEdition);
        val gameId = UUID.randomUUID()
                         .toString();

        persistence.save(GameState.builder()
                                  .sessionId(gameId)
                                  .gameEdition(GameEdition.valueOf(gameEdition))
                                  .gameStage(GameStage.INITIALIZED)
                                  .players(new HashSet<>())
                                  .build());

        return ResponseEntity.status(201)
                             .body(new GameSessionIdDto(gameId));
    }

    @Override
    public ResponseEntity<PlayerDto> createPlayerInSession(final String sessionId, final String playerName) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerName(playerName);

        final Game game = loadGame(sessionId);

        final String playerId = UUID.randomUUID()
                                    .toString();

        Player player;
        try {
            player = game.createPlayer(playerId, playerName);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        saveGame(game);
        return ResponseEntity.status(201)
                             .body(PlayerDto.of(player));
    }

    @Override
    public ResponseEntity<PlayerDto> getPlayer(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            player = game.getPlayer(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        return ResponseEntity.ok(PlayerDto.of(player));
    }

    @Override
    public ResponseEntity<PlayerDto> startGame(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            game.changePlayerStatusToReady(playerId);
            player = game.getPlayer(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(PlayerDto.of(player));
    }

    @Override
    public ResponseEntity<PlayerBaseInfoDto> getOpponent(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            player = game.getOpponent(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        return ResponseEntity.ok(PlayerBaseInfoDto.of(player));
    }

    @Override
    public ResponseEntity<CellDto[][]> getField(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Cell[][] field;
        try {
            field = game.getField(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        return ResponseEntity.ok(ControllerUtils.mapFieldToFieldDto(field));
    }

    @Override
    public ResponseEntity<CellDto[][]> getFieldOfOpponent(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Cell[][] field;
        try {
            field = game.getOpponentField(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        return ResponseEntity.ok(ControllerUtils.mapFieldToFieldDto(field));
    }

    @Override
    public ResponseEntity<Set<ShipDto>> getPrepareShipsList(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Set<Ship> ships;
        try {
            ships = game.getShipsNotOnTheField(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        return ResponseEntity.ok(ships.stream()
                                      .map(ShipDto::of)
                                      .collect(Collectors.toSet()));
    }

    @Override
    public ResponseEntity<ShipDto> addShipToField(
            final String sessionId, final String playerId, final String shipId, final Coordinate coordinate,
            final String shipDirection) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateShipId(shipId);
        ValidationUtils.validateShipDirection(shipDirection);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        Ship ship;
        try {
            ship = game.getAllShips(playerId)
                       .stream()
                       .filter(s -> shipId.equals(s.shipId()))
                       .findAny()
                       .orElseThrow(() -> new GameShipIdIsNotCorrectException("Ship (%s) is not found in player".formatted(
                               shipId)));
            val direction = ShipDirection.valueOf(shipDirection);
            game.addShipToField(playerId,
                                coordinate,
                                Ship.builder()
                                    .shipId(ship.shipId())
                                    .shipDirection(direction)
                                    .shipSize(ship.shipSize())
                                    .shipType(ship.shipType())
                                    .build());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(ShipDto.of(ship));
    }

    @Override
    public ResponseEntity<RemovedShipDto> removeShipFromField(
            final String sessionId, final String playerId, final Coordinate coordinate) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        String ship;
        try {
            val shipId = game.removeShipFromField(playerId, coordinate);
            ship = shipId.orElse("");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(new RemovedShipDto(ship));
    }

    @Override
    public ResponseEntity<PlayerBaseInfoDto> getActivePlayer(final String sessionId) {
        ValidationUtils.validateSessionId(sessionId);

        final Game game = loadGame(sessionId);

        Player player = game.getPlayers()
                            .stream()
                            .filter(Player::isActive)
                            .findAny()
                            .orElseThrow(() -> new GameStageIsNotCorrectException(game.getGameState()
                                                                                      .gameStage(),
                                                                                  "Active player is not found"));

        return ResponseEntity.ok(PlayerBaseInfoDto.of(player));
    }

    @Override
    public ResponseEntity<ShotResultDto> makeShot(
            final String sessionId, final String playerId, final Coordinate coordinate) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        ShotResult shotResult;
        try {
            shotResult = game.makeShot(playerId, coordinate);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(new ShotResultDto(shotResult));
    }

    @Override
    public ResponseEntity<UndamagedCellsDto> getNumberOfUndamagedCells(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        int amount = game.getPlayer(playerId)
                         .getField()
                         .getNumberOfUndamagedCells();

        return ResponseEntity.ok(new UndamagedCellsDto(amount));
    }

    @Override
    public ResponseEntity<NumberOfAliveShipsDto> getNumberOfNotDestroyedShips(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        int amount = game.getPlayer(playerId)
                         .getField()
                         .getNumberOfNotDestroyedShips();

        return ResponseEntity.ok(new NumberOfAliveShipsDto(amount));
    }

    @Override
    public ResponseEntity<UndamagedCellsDto> getNumberOfUndamagedCellsOpponent(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        int amount = game.getOpponent(playerId)
                         .getField()
                         .getNumberOfUndamagedCells();

        return ResponseEntity.ok(new UndamagedCellsDto(amount));
    }

    @Override
    public ResponseEntity<NumberOfAliveShipsDto> getNumberOfNotDestroyedShipsOpponent(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        int amount = game.getOpponent(playerId)
                         .getField()
                         .getNumberOfNotDestroyedShips();

        return ResponseEntity.ok(new NumberOfAliveShipsDto(amount));
    }

    @Override
    public ResponseEntity<PlayerBaseInfoDto> getWinner(final String sessionId) {
        ValidationUtils.validateSessionId(sessionId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            player = game.getWinner()
                         .orElseThrow(() -> new GameStageIsNotCorrectException(game.getGameState()
                                                                                   .gameStage(),
                                                                               "Winner can't be returned now"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new GameInternalProblemException(ex.getMessage());
        }

        return ResponseEntity.ok(PlayerBaseInfoDto.of(player));
    }

    @Override
    public ResponseEntity<GameStageDto> getStage(final String sessionId) {
        ValidationUtils.validateSessionId(sessionId);

        final Game game = loadGame(sessionId);

        return ResponseEntity.ok(GameStageDto.builder()
                                             .gameStage(game.getGameState()
                                                            .gameStage())
                                             .build());
    }

    @Override
    public ResponseEntity<LastGameUpdateDto> getLastUpdate(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        var responseData = LastGameUpdateDto.builder()
                                            .lastId(game.getGameState()
                                                        .lastUpdate())
                                            .gameStage(game.getGameState()
                                                           .gameStage())
                                            .build();
        return ResponseEntity.ok(responseData);
    }

    @Override
    public ResponseEntity<GameplayStateDto> getState(final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);
        var currentPlayer = game.getPlayer(playerId);
        var opponentPlayer = game.getOpponent(playerId);
        var playerField = getField(sessionId, playerId);
        var opponentField = game.getGameState()
                                .gameStage() == GameStage.FINISHED ?
                getField(sessionId, opponentPlayer.getPlayerId()) :
                getFieldOfOpponent(sessionId, playerId);

        var responseData = GameplayStateDto.builder()
                                           .playerName(currentPlayer.getPlayerName())
                                           .opponentName(opponentPlayer.getPlayerName())
                                           .isPlayerActive(currentPlayer.isActive())
                                           .isOpponentReady(opponentPlayer.isReady())
                                           .playerNumberOfAliveCells(currentPlayer.getField()
                                                                                  .getNumberOfUndamagedCells())
                                           .playerNumberOfAliveShips(currentPlayer.getField()
                                                                                  .getNumberOfNotDestroyedShips())
                                           .opponentNumberOfAliveCells(opponentPlayer.getField()
                                                                                     .getNumberOfUndamagedCells())
                                           .opponentNumberOfAliveShips(opponentPlayer.getField()
                                                                                     .getNumberOfNotDestroyedShips())
                                           .playerField(playerField.getBody())
                                           .opponentField(opponentField.getBody())
                                           .hasWinner(currentPlayer.isWinner() || opponentPlayer.isWinner())
                                           .build();
        return ResponseEntity.ok(responseData);
    }
}
