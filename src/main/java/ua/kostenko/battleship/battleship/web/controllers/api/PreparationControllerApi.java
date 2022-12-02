package ua.kostenko.battleship.battleship.web.controllers.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation.*;

public interface PreparationControllerApi {
    ResponseEntity<ResponsePreparationState> getPreparationState(String sessionId, String playerId);

    ResponseEntity<ResponseShipAddedDto> addShipToField(
            String sessionId, String playerId, String shipId, ParamShipDto shipDto);

    ResponseEntity<ResponseShipRemovedDto> removeShipFromField(
            String sessionId, String playerId, ParamCoordinateDto coordinateDto);

    ResponseEntity<ResponseOpponentInformationDto> getOpponentInformation(String sessionId, String playerId);

    ResponseEntity<ResponsePlayerReady> startGame(String sessionId, String playerId);
}
