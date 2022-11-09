package ua.kostenko.battleship.battleship.engine;

import lombok.NonNull;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.models.records.GameStateRepresentation;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Optional;
import java.util.Set;

public interface Game {

    static Game wrap(@NonNull GameStateRepresentation gameStateRepresentation) {
        return new GameImpl(gameStateRepresentation);
    }

    Player createPlayer(String playerId, String playerName);

    Player getPlayer(String playerId);

    Player getOpponent(String playerId);

    Set<Ship> getAvailableShipsForPlayer(String playerId);

    Set<Ship> getAllShipsForPlayer(String playerId);

    void addShipToField(String playerId, Coordinate coordinate, Ship ship);

    Optional<String> removeShipFromField(String playerId, Coordinate coordinate);

    void makePlayerReady(String playerId);

    Set<Player> getPlayers();

    ShotResult makeShot(String playerId, Coordinate coordinate);

    Cell[][] getField(String playerId);

    Cell[][] getOpponentField(String currentPlayerId);

    Optional<Player> getWinner();

    GameStateRepresentation getGameStateRepresentation();
}