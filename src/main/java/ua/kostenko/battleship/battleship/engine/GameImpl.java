package ua.kostenko.battleship.battleship.engine;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.GameState;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.engine.utils.CoordinateUtil;
import ua.kostenko.battleship.battleship.engine.utils.ShipUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class GameImpl implements Game {
    private GameStateRepresentation gameState;

    public GameImpl(GameStateRepresentation gameState) {
        this.gameState = gameState;
    }

    @Override
    public Player createPlayer(final String playerId, final String playerName) {
        if (StringUtils.isBlank(playerId)) {
            throw new IllegalArgumentException("PlayerId can't be blank");
        }
        if (StringUtils.isBlank(playerName)) {
            throw new IllegalArgumentException("PlayerName can't be blank");
        }
        val states = List.of(GameState.PREPARATION,
                             GameState.WAITING_FOR_PLAYERS,
                             GameState.FINISHED);
        if (states.contains(gameState.gameState())) {
            throw new IllegalStateException(
                    "%s doesn't allow to add players.".formatted(gameState.gameState()));
        }
        if (gameState.players().size() >= 2) {
            throw new IllegalStateException("Game can't have more than 2 players");
        }

        val ships = ShipUtil.generateShips(gameState.gameType());
        val player = Player.builder()
                           .playerId(playerId)
                           .playerName(playerName)
                           .allPlayerShips(new HashSet<>(ships))
                           .shipsNotOnTheField(new HashSet<>(ships))
                           .build();
        gameState.players().add(player);

        GameState currentState = gameState.players().size() == 2 ?
                GameState.PREPARATION :
                GameState.WAITING_FOR_PLAYERS;

        gameState = GameStateRepresentation.builder()
                                           .gameType(gameState.gameType())
                                           .sessionId(gameState.sessionId())
                                           .gameState(currentState)
                                           .players(gameState.players())
                                           .build();

        return player;
    }

    @Override
    public Player getPlayer(final String playerId) {
        return getPlayer(playerId, player -> playerId.equals(player.getPlayerId()));
    }

    @Override
    public Player getOpponent(final String playerId) {
        return getPlayer(playerId, player -> !playerId.equals(player.getPlayerId()));
    }

    @Override
    public Set<Ship> getAvailableShipsForPlayer(final String playerId) {
        val current = getPlayer(playerId);
        return current.getShipsNotOnTheField();
    }

    @Override
    public Set<Ship> getAllShipsForPlayer(final String playerId) {
        val current = getPlayer(playerId);
        return current.getAllPlayerShips();
    }

    @Override
    public void addShipToField(
            final String playerId, final Coordinate coordinate, final Ship ship) {
        val current = getPlayer(playerId);
        val field = current.getField();
        val shipsNotOnTheField = current.getShipsNotOnTheField();

        if (!shipsNotOnTheField.contains(ship)) {
            throw new IllegalArgumentException("Ship %s is not available for add operation"
                                                       .formatted(ship));
        }
        field.addShip(coordinate, ship);
        shipsNotOnTheField.remove(ship);
    }

    @Override
    public Optional<String> removeShipFromField(
            final String playerId, final Coordinate coordinate) {
        val current = getPlayer(playerId);
        val field = current.getField();

        val removed = field.removeShip(coordinate);
        if (removed.isEmpty()) {
            return Optional.empty();
        }

        val shipId = removed.get();
        val allShips = current.getShipsNotOnTheField();
        val foundShip = allShips.stream()
                                .filter(ship -> shipId.equals(ship.shipId()))
                                .findAny();

        if (foundShip.isEmpty()) {
            throw new IllegalArgumentException("Ship %s not found in player ship list"
                                                       .formatted(shipId));
        }

        val shipsNotOnTheField = current.getShipsNotOnTheField();
        shipsNotOnTheField.add(foundShip.get());
        return foundShip.map(Ship::shipId);
    }

    @Override
    public void makePlayerReady(final String playerId) {
        val current = getPlayer(playerId);
        if (!current.getShipsNotOnTheField().isEmpty()) {
            throw new IllegalStateException(
                    "Player can't be made ready. Player has ships not added to the field");
        }
        if (GameState.PREPARATION != gameState.gameState()) {
            throw new IllegalArgumentException(
                    "Player can be made ready only in PREPARATION state");
        }
        current.setReady(true);
    }

    @Override
    public Set<Player> getPlayers() {
        return gameState.players();
    }

    @Override
    public ShotResult makeShot(final String playerId, final Coordinate coordinate) {
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);
        val current = getPlayer(playerId);
        val opponent = getOpponent(playerId);
        if (GameState.IN_GAME != gameState.gameState()) {
            throw new IllegalStateException("Shot can be made only in IN_GAME state");
        }
        val shotResult = opponent.getField().makeShot(coordinate);
        updateGameState(current, opponent, shotResult);
        return shotResult;
    }

    @Override
    public Cell[][] getField(final String playerId) {
        val current = getPlayer(playerId);
        return current.getField().getField();
    }

    @Override
    public Cell[][] getOpponentField(final String currentPlayerId) {
        val opponent = getOpponent(currentPlayerId);
        return opponent.getField().getFieldWithHiddenShips();
    }

    @Override
    public Optional<Player> getWinner() {
        if (GameState.FINISHED != gameState.gameState()) {
            return Optional.empty();
        }
        val winner = getPlayers().stream().filter(Player::isWinner).findAny();
        if (winner.isEmpty()) {
            throw new IllegalStateException("Winner is not found for Finished game.");
        }
        return winner;
    }

    @Override
    public GameStateRepresentation getGameStateRepresentation() {
        return gameState;
    }

    private Player getPlayer(final String playerId, final Predicate<Player> filterPlayerPredicate) {
        if (StringUtils.isBlank(playerId)) {
            throw new IllegalArgumentException("PlayerId can't be blank");
        }
        if (gameState.players().isEmpty()) {
            throw new IllegalStateException("No players in the game.");
        }

        val optionalPlayer = gameState.players()
                                      .stream()
                                      .filter(filterPlayerPredicate)
                                      .findAny();

        if (optionalPlayer.isEmpty()) {
            throw new IllegalArgumentException("Player with provided id is not found");
        }
        return optionalPlayer.get();
    }

    private void updateGameState(
            final Player current, final Player opponent, final ShotResult shotResult) {
        if (ShotResult.HIT == shotResult || ShotResult.DESTROYED == shotResult) {
            current.setActive(true);
            opponent.setActive(false);
        } else {
            current.setActive(false);
            opponent.setActive(true);
        }
        val playerShipsAmount = current.getField().getAmountOfAliveShips();
        val opponentShipsAmount = opponent.getField().getAmountOfAliveShips();
        GameState newGameState = gameState.gameState();
        if (opponentShipsAmount == 0 || playerShipsAmount == 0) {
            if (playerShipsAmount == 0) {
                opponent.setWinner(true);
            } else {
                current.setWinner(true);
            }
            newGameState = GameState.FINISHED;
        }
        this.gameState = GameStateRepresentation.builder()
                                                .gameType(gameState.gameType())
                                                .players(gameState.players())
                                                .sessionId(gameState.sessionId())
                                                .gameState(newGameState)
                                                .build();
    }
}
