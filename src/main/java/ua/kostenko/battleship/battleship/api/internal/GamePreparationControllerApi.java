package ua.kostenko.battleship.battleship.api.internal;

import ua.kostenko.battleship.battleship.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.api.dtos.ShipDto;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;

import java.util.Optional;
import java.util.Set;

public interface GamePreparationControllerApi {
    Set<ShipDto> getPrepareShipsList(String sessionId, String playerId);

    CellDto[][] getPreparePlayerField(String sessionId, String playerId);

    Optional<ShipDto> addShipToField(
            String sessionId, String playerId, String shipId,
            Coordinate coordinate, String shipDirection);

    Optional<ShipDto> removeShipFromField(String sessionId, String playerId, Coordinate coordinate);

    Optional<PlayerDto> getOpponentPrepareStatus(String sessionId, String playerId);
}
