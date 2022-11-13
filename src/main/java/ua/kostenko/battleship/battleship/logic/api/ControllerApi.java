package ua.kostenko.battleship.battleship.logic.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.logic.api.dtos.*;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

import java.util.Set;

public interface ControllerApi {
    ResponseEntity<GameEditionsDto> getGameEditions();

    ResponseEntity<GameSessionDto> createGameSession(String gameEdition); // POST

    ResponseEntity<PlayerDto> createPlayerInSession(String sessionId, String playerName); // POST

    ResponseEntity<PlayerDto> getPlayer(String sessionId, String playerId);

    ResponseEntity<PlayerDto> startGame(String sessionId, String playerId); // POST

    ResponseEntity<PlayerBaseInfoDto> getOpponent(String sessionId, String playerId); // GET

    ResponseEntity<CellDto[][]> getField(String sessionId, String playerId); // GET

    ResponseEntity<CellDto[][]> getFieldOfOpponent(String sessionId, String playerId); // GET

    ResponseEntity<Set<ShipDto>> getPrepareShipsList(String sessionId, String playerId); // GET

    ResponseEntity<ShipDto> addShipToField(
            String sessionId, String playerId, String shipId, Coordinate coordinate, String shipDirection); // POST

    ResponseEntity<RemovedShipDto> removeShipFromField(
            String sessionId, String playerId, Coordinate coordinate); // DELETE

    ResponseEntity<PlayerBaseInfoDto> getActivePlayer(String sessionId);

    ResponseEntity<ShotResultDto> makeShot(String sessionId, String playerId, Coordinate coordinate);

    ResponseEntity<UndamagedCellsDto> getNumberOfUndamagedCells(String sessionId, String playerId);

    ResponseEntity<NumberOfAliveShipsDto> getNumberOfNotDestroyedShips(String sessionId, String playerId);

    ResponseEntity<PlayerBaseInfoDto> getWinner(String sessionId);
}
