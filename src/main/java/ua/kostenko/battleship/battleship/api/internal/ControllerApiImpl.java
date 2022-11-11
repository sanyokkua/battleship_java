package ua.kostenko.battleship.battleship.api.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerBaseInfoDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.api.dtos.ShipDto;
import ua.kostenko.battleship.battleship.api.internal.exceptions.IllegalGameStateException;
import ua.kostenko.battleship.battleship.api.internal.exceptions.IncorrectSessionIdException;
import ua.kostenko.battleship.battleship.api.internal.exceptions.IncorrectShipIdException;
import ua.kostenko.battleship.battleship.api.internal.exceptions.InternalGameException;
import ua.kostenko.battleship.battleship.engine.Game;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.GameState;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.persistence.Persistence;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ControllerApiImpl implements ControllerApi {
    private final Persistence persistence;

    private Game loadGame(final String sessionId) {
        log.debug("sessionId to load: {}", sessionId);
        final Optional<Game> loaded = persistence.load(sessionId);
        if (loaded.isEmpty()) {
            throw new IncorrectSessionIdException("Session is not found. ID: %s".formatted(
                    sessionId));
        }
        final Game game = loaded.get();
        log.debug("sessionId is loaded: {}", game.getGameStateRepresentation().sessionId());
        return game;
    }

    private void saveGame(final Game game) {
        log.debug("sessionId for saving: {}", game.getGameStateRepresentation().sessionId());

        persistence.save(game.getGameStateRepresentation());

        log.debug("sessionId: {} is saved", game.getGameStateRepresentation().sessionId());
    }

    @Override
    public ResponseEntity<Set<GameEdition>> getGameEditions() {
        log.debug("Returning supporting GameEditions");
        return ResponseEntity.ok(Set.of(GameEdition.UKRAINIAN, GameEdition.MILTON_BRADLEY));
    }

    @Override
    public ResponseEntity<String> createGameSession(final String gameEdition) {
        ValidationUtils.validateGameEdition(gameEdition);
        val gameId = UUID.randomUUID().toString();

        persistence.save(GameStateRepresentation.builder()
                                                .sessionId(gameId)
                                                .gameEdition(GameEdition.valueOf(gameEdition))
                                                .gameState(GameState.INITIALIZED)
                                                .players(new HashSet<>())
                                                .build());

        return ResponseEntity.status(201).body(gameId);
    }

    @Override
    public ResponseEntity<PlayerDto> createPlayerInSession(
            final String sessionId, final String playerName) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerName(playerName);

        final Game game = loadGame(sessionId);

        final String playerId = UUID.randomUUID().toString();
        Player player;
        try {
            player = game.createPlayer(playerId, playerName);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        saveGame(game);
        return ResponseEntity.status(201).body(PlayerDto.of(player));
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
            throw new InternalGameException(ex.getMessage());
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
            game.makePlayerReady(playerId);
            player = game.getPlayer(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(PlayerDto.of(player));
    }

    @Override
    public ResponseEntity<PlayerBaseInfoDto> getOpponent(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            player = game.getOpponent(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
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
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(ControllerUtils.mapFieldToFieldDto(field));
    }

    @Override
    public ResponseEntity<CellDto[][]> getFieldOfOpponent(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Cell[][] field;
        try {
            field = game.getOpponentField(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(ControllerUtils.mapFieldToFieldDto(field));
    }

    @Override
    public ResponseEntity<Set<ShipDto>> getPrepareShipsList(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        Set<Ship> field;
        try {
            field = game.getAvailableShipsForPlayer(playerId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(field.stream().map(ShipDto::of).collect(Collectors.toSet()));
    }

    @Override
    public ResponseEntity<ShipDto> addShipToField(
            final String sessionId, final String playerId, final String shipId,
            final Coordinate coordinate,
            final String shipDirection) {

        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateShipId(shipId);
        ValidationUtils.validateShipDirection(shipDirection);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        Ship ship;
        try {
            ship = game.getAllShipsForPlayer(playerId)
                       .stream()
                       .filter(s -> shipId.equals(s.shipId()))
                       .findAny()
                       .orElseThrow(() -> new IncorrectShipIdException(
                               "Ship (%s) is not found in player".formatted(shipId)));
            val direction = Direction.valueOf(shipDirection);
            game.addShipToField(playerId, coordinate, Ship.builder()
                                                          .shipId(ship.shipId())
                                                          .direction(direction)
                                                          .shipSize(ship.shipSize())
                                                          .shipType(ship.shipType())
                                                          .build());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(ShipDto.of(ship));
    }

    @Override
    public ResponseEntity<String> removeShipFromField(
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
            throw new InternalGameException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(ship);
    }

    @Override
    public ResponseEntity<PlayerBaseInfoDto> getActivePlayer(final String sessionId) {
        ValidationUtils.validateSessionId(sessionId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            player = game.getPlayers()
                         .stream()
                         .filter(Player::isActive)
                         .findAny()
                         .orElseThrow(
                                 () -> new IllegalGameStateException("Active player is not found"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(PlayerBaseInfoDto.of(player));
    }

    @Override
    public ResponseEntity<ShotResult> makeShot(
            final String sessionId, final String playerId, final Coordinate coordinate) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);
        ValidationUtils.validateCoordinate(coordinate);

        val game = loadGame(sessionId);

        ShotResult shotResult;
        try {
            shotResult = game.makeShot(playerId, coordinate);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        saveGame(game);

        return ResponseEntity.ok(shotResult);
    }

    @Override
    public ResponseEntity<Integer> getNumberOfUndamagedCells(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        int amount;
        try {
            amount = game.getPlayer(playerId).getField().getAmountOfAliveCells();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(amount);
    }

    @Override
    public ResponseEntity<Integer> getNumberOfNotDestroyedShips(
            final String sessionId, final String playerId) {
        ValidationUtils.validateSessionId(sessionId);
        ValidationUtils.validatePlayerId(playerId);

        final Game game = loadGame(sessionId);

        int amount;
        try {
            amount = game.getPlayer(playerId).getField().getAmountOfAliveShips();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(amount);
    }

    @Override
    public ResponseEntity<PlayerBaseInfoDto> getWinner(final String sessionId) {
        ValidationUtils.validateSessionId(sessionId);

        final Game game = loadGame(sessionId);

        Player player;
        try {
            player = game.getWinner()
                         .orElseThrow(() -> new IllegalGameStateException(
                                 "Winner can't be returned now"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InternalGameException(ex.getMessage());
        }

        return ResponseEntity.ok(PlayerBaseInfoDto.of(player));
    }
}
