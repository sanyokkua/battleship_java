package ua.kostenko.battleship.battleship.web.controllers.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.gameplay.ResponseShotResultDto;

public interface GameplayControllerApi {
    ResponseEntity<ResponseGameplayStateDto> getGameStateForPlayer(String sessionId, String playerId);

    ResponseEntity<ResponseShotResultDto> makeShotByField(
            String sessionId, String playerId, ParamCoordinateDto coordinate);
}
