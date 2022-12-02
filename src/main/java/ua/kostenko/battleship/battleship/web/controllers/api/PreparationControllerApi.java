package ua.kostenko.battleship.battleship.web.controllers.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation.*;

public interface PreparationControllerApi {
    //"/api/game/sessions/${sessionId}/players/${playerId}/ships?available");
    //    ResponseEntity<ResponseShipsNotOnTheBoard> getShipsNotOnTheBoard(String sessionId, String playerId);

    //"/api/game/sessions/${sessionId}/players/${playerId}/field");
    //    ResponseEntity<ResponsePreparationFieldDto> getPreparationField(String sessionId, String playerId);
    ResponseEntity<ResponsePreparationState> getPreparationState(String sessionId, String playerId);

    //"/api/game/sessions/${sessionId}/players/${playerId}/ships/${shipId}");
    ResponseEntity<ResponseShipAddedDto> addShipToField(
            String sessionId, String playerId, String shipId, ParamShipDto shipDto);

    //"/api/game/sessions/${sessionId}/players/${playerId}/ships?delete");
    ResponseEntity<ResponseShipRemovedDto> removeShipFromField(
            String sessionId, String playerId, ParamCoordinateDto coordinateDto);

    //"/api/game/sessions/${sessionId}/players/${playerId}?opponent");
    ResponseEntity<ResponseOpponentInformationDto> getOpponentInformation(String sessionId, String playerId);

    //"/api/game/sessions/${sessionId}/players/${playerId}?start");
    ResponseEntity<ResponsePlayerReady> startGame(String sessionId, String playerId);
}
