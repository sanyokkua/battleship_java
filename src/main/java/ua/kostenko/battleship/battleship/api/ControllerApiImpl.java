package ua.kostenko.battleship.battleship.api;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.api.dtos.ShipDto;
import ua.kostenko.battleship.battleship.engine.Game;
import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.config.GameType;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.GameState;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.persistence.Persistence;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ControllerApiImpl implements ControllerApi {
    private final Persistence persistence;

    private static CellDto[][] mapFieldToFieldDto(final Cell[][] playerField) {
        CellDto[][] field = new CellDto[GameConfig.NUMBER_OF_ROWS][GameConfig.NUMBER_OF_COLUMNS];
        for (int i = 0; i < GameConfig.NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < GameConfig.NUMBER_OF_COLUMNS; j++) {
                field[i][j] = CellDto.of(playerField[i][j]);
            }
        }
        return field;
    }

    private Game loadGame(final String sessionId) {
        val loaded = persistence.load(sessionId);
        if (loaded.isEmpty()) {
            throw new IllegalArgumentException("Game with sessionId doesn't exist");
        }
        return loaded.get();
    }

    @Override
    public Set<ShipDto> getPrepareShipsList(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            return Collections.emptySet();
        }
        val game = loadGame(sessionId);
        val player = game.getPlayer(playerId);
        return player.getShipsNotOnTheField().stream().map(ShipDto::of).collect(Collectors.toSet());
    }

    @Override
    public CellDto[][] getPreparePlayerField(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }
        val game = loadGame(sessionId);
        val playerField = game.getField(playerId);
        return mapFieldToFieldDto(playerField);
    }

    @Override
    public Optional<ShipDto> addShipToField(
            final String sessionId, final String playerId, final String shipId,
            final Coordinate coordinate,
            final String shipDirection) {
        if (StringUtils.isAnyBlank(sessionId, playerId, shipId)) {
            throw new IllegalArgumentException("sessionId, or playerId, or shipId is blank");
        }
        val direction = Direction.valueOf(shipDirection.toUpperCase());
        val game = loadGame(sessionId);
        val optionalShip = game.getAvailableShipsForPlayer(playerId).stream()
                               .filter(s -> shipId.equals(s.shipId()))
                               .findAny();
        if (optionalShip.isEmpty()) {
            return Optional.empty();
        }
        val playerShip = optionalShip.get();
        val shipToBeAdded = Ship.builder()
                                .shipId(playerShip.shipId())
                                .shipSize(playerShip.shipSize())
                                .shipType(playerShip.shipType())
                                .direction(direction)
                                .build();

        game.addShipToField(playerId, coordinate, shipToBeAdded);

        persistence.save(game.getGameStateRepresentation());

        return Optional.ofNullable(ShipDto.of(shipToBeAdded));
    }


    @Override
    public Optional<ShipDto> removeShipFromField(
            final String sessionId, final String playerId, final Coordinate coordinate) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);
        val res = game.removeShipFromField(playerId, coordinate);
        if (res.isEmpty()) {
            return Optional.empty();
        }
        persistence.save(game.getGameStateRepresentation());

        return game.getAvailableShipsForPlayer(playerId).stream()
                   .filter(s -> res.get().equals(s.shipId()))
                   .map(ShipDto::of)
                   .findAny();
    }

    @Override
    public Optional<PlayerDto> getOpponentPrepareStatus(
            final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }
        val game = loadGame(sessionId);
        try {
            return Optional.ofNullable(PlayerDto.of(game.getOpponent(playerId)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> createGameSession(@NonNull final GameType gameType) {
        val newGameId = UUID.randomUUID().toString();
        val gameRepresentation = GameStateRepresentation.builder()
                                                        .sessionId(newGameId)
                                                        .gameType(gameType)
                                                        .gameState(GameState.INITIALIZED)
                                                        .players(new HashSet<>())
                                                        .build();
        persistence.save(gameRepresentation);
        return Optional.of(newGameId);
    }

    @Override
    public Optional<PlayerDto> createPlayerInSession(
            final String sessionId, final String playerName) {
        if (StringUtils.isAnyBlank(sessionId, playerName)) {
            throw new IllegalArgumentException("sessionId or playerName is blank");
        }

        val game = loadGame(sessionId);
        val playerId = UUID.randomUUID().toString();
        val player = game.createPlayer(playerId, playerName);
        persistence.save(game.getGameStateRepresentation());

        return Optional.of(PlayerDto.of(player));
    }

    @Override
    public Optional<PlayerDto> startGame(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);
        game.makePlayerReady(playerId);
        persistence.save(game.getGameStateRepresentation());

        val player = game.getPlayer(playerId);
        return Optional.of(PlayerDto.of(player));
    }

    @Override
    public Optional<PlayerDto> getOpponent(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);

        val player = game.getOpponent(playerId);
        return Optional.of(PlayerDto.of(player));
    }

    @Override
    public Optional<PlayerDto> getPlayerById(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);

        val player = game.getPlayer(playerId);
        return Optional.of(PlayerDto.of(player));
    }

    @Override
    public Optional<PlayerDto> getActivePlayer(final String sessionId) {
        if (StringUtils.isAnyBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is blank");
        }

        val game = loadGame(sessionId);

        val player = game.getPlayers().stream().filter(Player::isActive).findAny();
        return player.map(PlayerDto::of);
    }

    @Override
    public CellDto[][] getField(
            final String sessionId, final String playerId, final boolean isForOpponent) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);
        val field = isForOpponent ? game.getOpponentField(playerId) : game.getField(playerId);
        return mapFieldToFieldDto(field);
    }

    @Override
    public ShotResult makeShot(
            final String sessionId, final String playerId, final Coordinate coordinate) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);
        val res = game.makeShot(playerId, coordinate);
        persistence.save(game.getGameStateRepresentation());

        return res;
    }

    @Override
    public int getNumberOfCellsLeft(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);
        return game.getPlayer(playerId).getField().getAmountOfAliveCells();
    }

    @Override
    public int getNumberOfShipsLeft(final String sessionId, final String playerId) {
        if (StringUtils.isAnyBlank(sessionId, playerId)) {
            throw new IllegalArgumentException("sessionId or playerId is blank");
        }

        val game = loadGame(sessionId);
        return game.getPlayer(playerId).getField().getAmountOfAliveShips();
    }

    @Override
    public Optional<PlayerDto> getWinner(final String sessionId) {
        if (StringUtils.isAnyBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is blank");
        }

        val game = loadGame(sessionId);
        val player = game.getWinner();
        return player.map(PlayerDto::of);
    }
}
