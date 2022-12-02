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

public interface Game {

    static Game fromGameState(@NonNull GameState gameState) {
        return new GameImpl(gameState);
    }

    default Supplier<FieldManagement> newField() {
        return FieldManagementImpl::new;
    }

    Player createPlayer(String playerId, String playerName);

    Player getPlayer(String playerId);

    Player getOpponent(String currentPlayerId);

    Set<Ship> getShipsNotOnTheField(String playerId);

    Set<Ship> getAllShips(String playerId);

    void addShipToField(String playerId, Coordinate coordinate, Ship ship);

    Optional<String> removeShipFromField(String playerId, Coordinate coordinate);

    void changePlayerStatusToReady(String playerId);

    ShotResult makeShot(String currentPlayerId, Coordinate opponentFieldCoordinate);

    Set<Player> getPlayers();

    Cell[][] getField(String playerId);

    Cell[][] getOpponentField(String currentPlayerId);

    Optional<Player> getWinner();

    GameState getGameState();
}