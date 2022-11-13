package ua.kostenko.battleship.battleship.logic.engine;

import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.logic.engine.utils.CoordinateUtils;
import ua.kostenko.battleship.battleship.logic.engine.utils.GameUtils;
import ua.kostenko.battleship.battleship.logic.engine.utils.ShipUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Log4j2
public class GameImpl implements Game {
    private GameState gameState;

    public GameImpl(GameState gameState) {
        log.debug("Initialized with: {}", gameState);
        this.gameState = gameState;
    }

    @Override
    public Player createPlayer(final String playerId, final String playerName) {
        log.trace("In method: createPlayer");
        GameUtils.validatePlayerId(playerId);
        GameUtils.validatePlayerName(playerName);
        GameUtils.validateGameStage(this.gameState.gameStage(),
                                    "There is no possibility to create player now",
                                    GameStage.INITIALIZED,
                                    GameStage.WAITING_FOR_PLAYERS);
        GameUtils.validateNumberOfPlayers(this.gameState.players());

        val allAvailableShipsForGame = Set.copyOf(ShipUtils.generateShips(this.gameState.gameEdition()));
        val listOfShipsToPlaceOnField = new HashSet<>(allAvailableShipsForGame);

        log.debug("All player ships: {}", allAvailableShipsForGame);
        log.debug("Prepare ships: {}", listOfShipsToPlaceOnField);

        val player = Player.builder()
                           .playerId(playerId)
                           .playerName(playerName)
                           .field(newField().get())
                           .allPlayerShips(allAvailableShipsForGame)
                           .shipsNotOnTheField(listOfShipsToPlaceOnField)
                           .build();
        this.gameState.players()
                      .add(player);

        log.debug("Current gameState: {}", this.gameState.gameStage());
        GameStage currentGameStage = this.gameState.players()
                                                   .size() == 2 ? GameStage.PREPARATION : GameStage.WAITING_FOR_PLAYERS;
        log.debug("New gameState: {}", currentGameStage);

        this.gameState = GameState.builder()
                                  .gameEdition(this.gameState.gameEdition())
                                  .sessionId(this.gameState.sessionId())
                                  .gameStage(currentGameStage)
                                  .players(this.gameState.players())
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
    public Player getOpponent(final String currentPlayerId) {
        log.trace("In method: getOpponent");
        return getPlayer(currentPlayerId, player -> !currentPlayerId.equals(player.getPlayerId()));
    }

    @Override
    public Set<Ship> getShipsNotOnTheField(final String playerId) {
        log.trace("In method: getAvailableShipsForPlayer");
        val player = getPlayer(playerId);
        return player.getShipsNotOnTheField();
    }

    @Override
    public Set<Ship> getAllShips(final String playerId) {
        log.trace("In method: getAllShipsForPlayer");
        val player = getPlayer(playerId);
        return player.getAllPlayerShips();
    }

    @Override
    public void addShipToField(final String playerId, final Coordinate coordinate, final Ship ship) {
        log.trace("In method: addShipToField");
        GameUtils.validateGameStage(this.gameState.gameStage(),
                                    "Ship can be added only in Preparation stage",
                                    GameStage.PREPARATION);

        val player = getPlayer(playerId);
        val playerField = player.getField();
        val shipsNotOnTheField = player.getShipsNotOnTheField();

        if (shipsNotOnTheField.stream()
                              .noneMatch(s -> ship.shipId()
                                                  .equals(s.shipId()))) {
            throw new IllegalArgumentException("Ship %s is not available for add operation".formatted(ship));
        }

        playerField.addShip(coordinate, ship);
        log.debug("Ship {} added to field with {} for player {}", ship, coordinate, playerId);

        shipsNotOnTheField.remove(ship);
        log.debug("Ship added to field, removed from the available ships");
    }

    @Override
    public Optional<String> removeShipFromField(final String playerId, final Coordinate coordinate) {
        log.trace("In method: removeShipFromField");
        GameUtils.validateGameStage(this.gameState.gameStage(),
                                    "Ship can be removed only in Preparation stage",
                                    GameStage.PREPARATION);

        val player = getPlayer(playerId);
        val playerField = player.getField();

        val removedShipIdOptional = playerField.removeShip(coordinate);
        if (removedShipIdOptional.isEmpty()) {
            log.debug("Ship not found (removed) by coordinate {}", coordinate);
            return Optional.empty();
        }

        val removedShipId = removedShipIdOptional.get();
        val allPlayerShips = player.getAllPlayerShips();
        val foundShipOptional = allPlayerShips.stream()
                                              .filter(ship -> removedShipId.equals(ship.shipId()))
                                              .findAny();

        if (foundShipOptional.isEmpty()) {
            throw new IllegalArgumentException("Ship %s not found in player ship list".formatted(removedShipId));
        }

        val shipsNotOnTheField = player.getShipsNotOnTheField();
        shipsNotOnTheField.add(foundShipOptional.get());
        player.setReady(false);
        log.debug("Ship removed from field. Returned to the available ships list");
        log.debug("Player readiness reset");
        return foundShipOptional.map(Ship::shipId);
    }

    @Override
    public void changePlayerStatusToReady(final String playerId) {
        log.trace("In method: makePlayerReady");
        GameUtils.validateGameStage(this.gameState.gameStage(),
                                    "State can be changed to ready only in Preparation stage",
                                    GameStage.PREPARATION);

        val player = getPlayer(playerId);
        val shipsNotOnTheField = player.getShipsNotOnTheField();

        if (!shipsNotOnTheField.isEmpty()) {
            throw new IllegalStateException("Player can't be made ready. Player has ships not added to the field");
        }

        player.setReady(true);
        val opponent = getOpponent(playerId);

        if (player.isReady() && !opponent.isActive()) {
            log.debug("Player is ready and opponent is not Active player");
            log.debug("Player set Active. {}", playerId);
            player.setActive(true);
        }

        if (player.isReady() && opponent.isReady()) {
            log.debug("Player and Opponent are ready. Changing game status.");
            this.gameState = GameState.builder()
                                      .gameEdition(this.gameState.gameEdition())
                                      .sessionId(this.gameState.sessionId())
                                      .gameStage(GameStage.IN_GAME)
                                      .players(this.gameState.players())
                                      .build();
            log.info("Game state updated. Current state: {}", this.gameState.gameStage());
        }
    }

    @Override
    public ShotResult makeShot(final String currentPlayerId, final Coordinate opponentFieldCoordinate) {
        log.trace("In method: makeShot");
        GameUtils.validateGameStage(this.gameState.gameStage(),
                                    "Shot can be made only in IN_GAME stage",
                                    GameStage.IN_GAME);
        CoordinateUtils.validateCoordinate(opponentFieldCoordinate);

        val player = getPlayer(currentPlayerId);

        if (!player.isActive()) {
            throw new IllegalStateException("Player is not active to make a shot");
        }

        val opponent = getOpponent(currentPlayerId);
        val opponentField = opponent.getField();
        val shotResult = opponentField.makeShot(opponentFieldCoordinate);

        updateGameState(player, opponent, shotResult);

        return shotResult;
    }

    @Override
    public Set<Player> getPlayers() {
        log.trace("In method: getPlayers");
        return this.gameState.players();
    }

    @Override
    public Cell[][] getField(final String playerId) {
        log.trace("In method: getField");
        val player = getPlayer(playerId);
        val playerField = player.getField();
        return playerField.getField();
    }

    @Override
    public Cell[][] getOpponentField(final String currentPlayerId) {
        log.trace("In method: getOpponentField");
        val opponent = getOpponent(currentPlayerId);
        val opponentField = opponent.getField();
        return opponentField.getFieldWithHiddenShips();
    }

    @Override
    public Optional<Player> getWinner() {
        log.trace("In method: getWinner");
        if (GameStage.FINISHED != this.gameState.gameStage()) {
            log.debug("GameState is not finished, Optional.empty() will be returned");
            return Optional.empty();
        }

        val winner = getPlayers().stream()
                                 .filter(Player::isWinner)
                                 .findAny();
        if (winner.isEmpty()) {
            throw new IllegalStateException("Winner is not found for Finished game.");
        }

        val player = winner.get();
        log.debug("Winner is: {} -> {}", player.getPlayerId(), player.getPlayerName());
        return winner;
    }

    @Override
    public GameState getGameState() {
        log.trace("In method: getGameStateRepresentation");
        return this.gameState;
    }

    private Player getPlayer(final String playerId, final Predicate<Player> filterPlayerPredicate) {
        log.trace("In method: getPlayer");
        GameUtils.validatePlayerId(playerId);

        val players = this.gameState.players();

        if (players.isEmpty()) {
            throw new IllegalStateException("No players in the game.");
        }
        if (players.stream()
                   .noneMatch(p -> playerId.equals(p.getPlayerId()))) {
            throw new IllegalArgumentException("Player with id %s not found".formatted(playerId));
        }

        val optionalPlayer = players.stream()
                                    .filter(filterPlayerPredicate)
                                    .findAny();

        if (optionalPlayer.isEmpty()) {
            throw new IllegalArgumentException("Player with provided filter is not found");
        }
        return optionalPlayer.get();
    }

    private void updateGameState(final Player player, final Player opponent, final ShotResult shotResult) {
        log.trace("In method: updateGameState");
        log.debug("updateGameState(player {}, opponent {}, shotRes {})", player, opponent, shotResult);

        if (ShotResult.HIT == shotResult || ShotResult.DESTROYED == shotResult) {
            player.setActive(true);
            opponent.setActive(false);
            log.debug("Res: HIT or DESTROYED, player now is active, opponent is not");
        } else {
            player.setActive(false);
            opponent.setActive(true);
            log.debug("Res: MISS, opponent now is active, player is not");
        }

        val playerField = player.getField();
        val playerShipsAmount = playerField.getNumberOfNotDestroyedShips();
        log.debug("{} shipAmount: {}", player, playerShipsAmount);

        val opponentField = opponent.getField();
        val opponentShipsAmount = opponentField.getNumberOfNotDestroyedShips();
        log.debug("{} shipAmount: {}", opponent, opponentShipsAmount);

        val opponentIsDestroyed = opponentShipsAmount == 0;
        val playerIsDestroyed = playerShipsAmount == 0;
        val isGameFinished = opponentIsDestroyed || playerIsDestroyed;
        log.debug("is game finished: {}", isGameFinished);

        val newGameState = isGameFinished ? GameStage.FINISHED : this.gameState.gameStage();
        player.setWinner(opponentIsDestroyed);
        opponent.setWinner(playerIsDestroyed);

        this.gameState = GameState.builder()
                                  .gameEdition(this.gameState.gameEdition())
                                  .players(this.gameState.players())
                                  .sessionId(this.gameState.sessionId())
                                  .gameStage(newGameState)
                                  .build();
        log.debug("New Game State: {}", gameState);
    }
}
