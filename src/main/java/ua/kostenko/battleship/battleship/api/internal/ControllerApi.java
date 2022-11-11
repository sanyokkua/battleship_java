package ua.kostenko.battleship.battleship.api.internal;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerBaseInfoDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.api.dtos.ShipDto;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;

import java.util.Set;

public interface ControllerApi {
    ResponseEntity<Set<GameEdition>> getGameEditions();

    ResponseEntity<String> createGameSession(String gameEdition); // POST

    ResponseEntity<PlayerDto> createPlayerInSession(String sessionId, String playerName); // POST

    ResponseEntity<PlayerDto> getPlayer(String sessionId, String playerId);

    ResponseEntity<PlayerDto> startGame(String sessionId, String playerId); // POST

    ResponseEntity<PlayerBaseInfoDto> getOpponent(String sessionId, String playerId); // GET

    ResponseEntity<CellDto[][]> getField(String sessionId, String playerId); // GET

    ResponseEntity<CellDto[][]> getFieldOfOpponent(String sessionId, String playerId); // GET

    ResponseEntity<Set<ShipDto>> getPrepareShipsList(String sessionId, String playerId); // GET

    ResponseEntity<ShipDto> addShipToField(
            String sessionId, String playerId, String shipId,
            Coordinate coordinate, String shipDirection); // POST

    ResponseEntity<String> removeShipFromField(
            String sessionId, String playerId, Coordinate coordinate); // DELETE

    ResponseEntity<PlayerBaseInfoDto> getActivePlayer(String sessionId);

    ResponseEntity<ShotResult> makeShot(String sessionId, String playerId, Coordinate coordinate);

    ResponseEntity<Integer> getNumberOfUndamagedCells(String sessionId, String playerId);

    ResponseEntity<Integer> getNumberOfNotDestroyedShips(String sessionId, String playerId);

    ResponseEntity<PlayerBaseInfoDto> getWinner(String sessionId);
}
