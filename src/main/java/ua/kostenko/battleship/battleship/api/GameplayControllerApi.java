package ua.kostenko.battleship.battleship.api;

import ua.kostenko.battleship.battleship.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;

import java.util.Optional;

public interface GameplayControllerApi {
    Optional<PlayerDto> getOpponent(String sessionId, String playerId);

    Optional<PlayerDto> getPlayerById(String sessionId, String playerId);

    Optional<PlayerDto> getActivePlayer(String sessionId);

    CellDto[][] getField(String sessionId, String playerId, boolean isForOpponent);

    ShotResult makeShot(String sessionId, String playerId, Coordinate coordinate);

    int getNumberOfCellsLeft(String sessionId, String playerId);

    int getNumberOfShipsLeft(String sessionId, String playerId);

    Optional<PlayerDto> getWinner(String sessionId);
}
