package ua.kostenko.battleship.battleship.engine;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class GameImpl implements Game {
    private GameStateRepresentation gameState;

    public GameImpl(GameStateRepresentation gameState) {
        log.debug("Initialized with: {}", gameState);
        this.gameState = gameState;
    }

    @Override
    public Player createPlayer(final String playerId, final String playerName) {
        log.trace("In method: createPlayer");
        if (StringUtils.isBlank(playerId)) {
            throw new IllegalArgumentException("PlayerId can't be blank");
        }
        if (StringUtils.isBlank(playerName)) {
            throw new IllegalArgumentException("PlayerName can't be blank");
        }
        val states = List.of(GameState.IN_GAME,
                             GameState.PREPARATION,
                             GameState.FINISHED);
        if (states.contains(gameState.gameState())) {
            throw new IllegalStateException(
                    "%s doesn't allow to add players.".formatted(gameState.gameState()));
        }
        if (gameState.players().size() >= 2) {
            throw new IllegalStateException("Game can't have more than 2 players");
        }

        val allShips = Set.copyOf(ShipUtil.generateShips(gameState.gameEdition()));
        val fieldShips = new HashSet<>(allShips);
        log.debug("All player ships: {}", allShips);
        log.debug("Prepare ships: {}", fieldShips);
        val player = Player.builder()
                           .playerId(playerId)
                           .playerName(playerName)
                           .field(newField().get())
                           .allPlayerShips(allShips)
                           .shipsNotOnTheField(fieldShips)
                           .build();
        gameState.players().add(player);

        log.debug("Current gameState: {}", gameState.gameState());
        GameState currentState = gameState.players().size() == 2 ?
                GameState.PREPARATION :
                GameState.WAITING_FOR_PLAYERS;
        log.debug("New gameState: {}", currentState);

        gameState = GameStateRepresentation.builder()
                                           .gameEdition(gameState.gameEdition())
                                           .sessionId(gameState.sessionId())
                                           .gameState(currentState)
                                           .players(gameState.players())
                                           .build();
        log.info("Player Created. Game state updated.");
        return player;
    }

    @Override
    public Player getPlayer(final String playerId) {
        log.trace("In method: getPlayer");
        return getPlayer(playerId, player -> playerId.equals(player.getPlayerId()));
    }

    @Override
    public Player getOpponent(final String playerId) {
        log.trace("In method: getOpponent");
        return getPlayer(playerId, player -> !playerId.equals(player.getPlayerId()));
    }

    @Override
    public Set<Ship> getAvailableShipsForPlayer(final String playerId) {
        log.trace("In method: getAvailableShipsForPlayer");
        val current = getPlayer(playerId);
        return current.getShipsNotOnTheField();
    }

    @Override
    public Set<Ship> getAllShipsForPlayer(final String playerId) {
        log.trace("In method: getAllShipsForPlayer");
        val current = getPlayer(playerId);
        return current.getAllPlayerShips();
    }

    @Override
    public void addShipToField(
            final String playerId, final Coordinate coordinate, final Ship ship) {
        log.trace("In method: addShipToField");
        if (GameState.PREPARATION != gameState.gameState()) {
            throw new IllegalStateException(
                    "%s doesn't allow to add ship.".formatted(gameState.gameState()));
        }
        val current = getPlayer(playerId);
        val field = current.getField();
        val shipsNotOnTheField = current.getShipsNotOnTheField();

        if (!shipsNotOnTheField.contains(ship)) {
            throw new IllegalArgumentException("Ship %s is not available for add operation"
                                                       .formatted(ship));
        }
        field.addShip(coordinate, ship);
        log.debug("Ship {} added to field with main coordinate {} for player {}", ship, coordinate,
                  playerId);
        shipsNotOnTheField.remove(ship);
        log.debug("Ship added to field, removed from the available ships");
    }

    @Override
    public Optional<String> removeShipFromField(
            final String playerId, final Coordinate coordinate) {
        log.trace("In method: removeShipFromField");
        if (GameState.PREPARATION != gameState.gameState()) {
            throw new IllegalStateException(
                    "%s doesn't allow to remove ship.".formatted(gameState.gameState()));
        }
        val current = getPlayer(playerId);
        val field = current.getField();

        val removed = field.removeShip(coordinate);
        if (removed.isEmpty()) {
            log.debug("Ship not found (removed) by coordinate {}", coordinate);
            return Optional.empty();
        }

        val shipId = removed.get();
        val allShips = current.getAllPlayerShips();
        val foundShip = allShips.stream()
                                .filter(ship -> shipId.equals(ship.shipId()))
                                .findAny();

        if (foundShip.isEmpty()) {
            throw new IllegalArgumentException("Ship %s not found in player ship list"
                                                       .formatted(shipId));
        }

        val shipsNotOnTheField = current.getShipsNotOnTheField();
        shipsNotOnTheField.add(foundShip.get());
        current.setReady(false);
        log.debug("Ship removed from field. Returned to the available ships list");
        log.debug("Player readiness reset");
        return foundShip.map(Ship::shipId);
    }

    @Override
    public void makePlayerReady(final String playerId) {
        log.trace("In method: makePlayerReady");
        if (GameState.PREPARATION != gameState.gameState()) {
            throw new IllegalStateException(
                    "%s doesn't allow to change player state to ready.".formatted(
                            gameState.gameState()));
        }
        val current = getPlayer(playerId);
        if (!current.getShipsNotOnTheField().isEmpty()) {
            throw new IllegalStateException(
                    "Player can't be made ready. Player has ships not added to the field");
        }

        current.setReady(true);
        val opponent = getOpponent(playerId);

        if (current.isReady() && !opponent.isActive()) {
            log.debug("Player is ready and opponent is not Active player");
            log.debug("Player set Active. {}", playerId);
            current.setActive(true);
        }

        if (current.isReady() && opponent.isReady()) {
            log.debug("Player and Opponent are ready. Changing game status.");
            gameState = GameStateRepresentation.builder()
                                               .gameEdition(gameState.gameEdition())
                                               .sessionId(gameState.sessionId())
                                               .gameState(GameState.IN_GAME)
                                               .players(gameState.players())
                                               .build();
            log.info("Game state updated. Current state: {}", gameState.gameState());
        }
    }

    @Override
    public Set<Player> getPlayers() {
        log.trace("In method: getPlayers");
        return gameState.players();
    }

    @Override
    public ShotResult makeShot(final String playerId, final Coordinate coordinate) {
        log.trace("In method: makeShot");
        if (GameState.IN_GAME != gameState.gameState()) {
            throw new IllegalStateException(
                    "Shot can be made only in IN_GAME state, current state is %s".formatted(
                            gameState.gameState()));
        }
        CoordinateUtil.validateCoordinateAndThrowException(coordinate);
        val current = getPlayer(playerId);
        val opponent = getOpponent(playerId);
        val shotResult = opponent.getField().makeShot(coordinate);
        updateGameState(current, opponent, shotResult);
        return shotResult;
    }

    @Override
    public Cell[][] getField(final String playerId) {
        log.trace("In method: getField");
        val current = getPlayer(playerId);
        return current.getField().getField();
    }

    @Override
    public Cell[][] getOpponentField(final String currentPlayerId) {
        log.trace("In method: getOpponentField");
        val opponent = getOpponent(currentPlayerId);
        return opponent.getField().getFieldWithHiddenShips();
    }

    @Override
    public Optional<Player> getWinner() {
        log.trace("In method: getWinner");
        if (GameState.FINISHED != gameState.gameState()) {
            log.debug("GameState is not finished, Optional.empty() will be returned");
            return Optional.empty();
        }
        val winner = getPlayers().stream().filter(Player::isWinner).findAny();
        if (winner.isEmpty()) {
            throw new IllegalStateException("Winner is not found for Finished game.");
        }
        log.debug("Winner is: {} -> {}", winner.get().getPlayerId(), winner.get().getPlayerName());
        return winner;
    }

    @Override
    public GameStateRepresentation getGameStateRepresentation() {
        log.trace("In method: getGameStateRepresentation");
        return gameState;
    }

    private Player getPlayer(final String playerId, final Predicate<Player> filterPlayerPredicate) {
        log.trace("In method: getPlayer");
        if (StringUtils.isBlank(playerId)) {
            throw new IllegalArgumentException("PlayerId can't be blank");
        }
        if (gameState.players().isEmpty()) {
            throw new IllegalStateException("No players in the game.");
        }
        if (gameState.players().stream().noneMatch(p -> playerId.equals(p.getPlayerId()))) {
            throw new IllegalArgumentException("Player with id %s not found".formatted(playerId));
        }

        val optionalPlayer = gameState.players()
                                      .stream()
                                      .filter(filterPlayerPredicate)
                                      .findAny();

        if (optionalPlayer.isEmpty()) {
            throw new IllegalArgumentException("Player with provided filter is not found");
        }
        return optionalPlayer.get();
    }

    private void updateGameState(
            final Player current, final Player opponent, final ShotResult shotResult) {
        log.trace("In method: updateGameState");
        log.debug("updateGameState(player {}, opponent {}, shotRes {})",
                  current, opponent, shotResult);
        if (ShotResult.HIT == shotResult || ShotResult.DESTROYED == shotResult) {
            current.setActive(true);
            opponent.setActive(false);
            log.debug("Res: HIT or DESTROYED, current now is active, opponent is not");
        } else {
            current.setActive(false);
            opponent.setActive(true);
            log.debug("Res: MISS, opponent now is active, current is not");
        }
        val playerShipsAmount = current.getField().getAmountOfAliveShips();
        log.debug("{} shipAmount: {}", current, playerShipsAmount);
        val opponentShipsAmount = opponent.getField().getAmountOfAliveShips();
        log.debug("{} shipAmount: {}", opponent, opponentShipsAmount);
        val opponentIsDestroyed = opponentShipsAmount == 0;
        val playerIsDestroyed = playerShipsAmount == 0;
        val isGameFinished = opponentIsDestroyed || playerIsDestroyed;
        log.debug("is game finished: {}", isGameFinished);
        val newGameState = isGameFinished ? GameState.FINISHED : gameState.gameState();
        current.setWinner(opponentIsDestroyed);
        opponent.setWinner(playerIsDestroyed);

        this.gameState = GameStateRepresentation.builder()
                                                .gameEdition(gameState.gameEdition())
                                                .players(gameState.players())
                                                .sessionId(gameState.sessionId())
                                                .gameState(newGameState)
                                                .build();
        log.debug("New Game State: {}", gameState);
    }
}
