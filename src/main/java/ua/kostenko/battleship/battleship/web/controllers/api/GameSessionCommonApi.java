package ua.kostenko.battleship.battleship.web.controllers.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.session.*;

public interface GameSessionCommonApi {
    //"/api/game/editions");
    ResponseEntity<ResponseAvailableGameEditionsDto> getAvailableGameEditions();

    //"/api/game/sessions");
    ResponseEntity<ResponseCreatedSessionIdDto> createGameSession(ParamGameEditionDto gameEdition);

    //"/api/game/sessions/${sessionId}/players");
    ResponseEntity<ResponseCreatedPlayerDto> createPlayerInSession(String sessionId, ParamPlayerNameDto playerNameDto);

    //"/api/game/sessions/${sessionId}/stage");
    ResponseEntity<ResponseCurrentGameStageDto> getCurrentGameStage(String sessionId);

    //"/api/game/sessions/${sessionId}/lastupdate");
    ResponseEntity<ResponseLastSessionChangeTimeDto> getLastSessionChangeTime(String sessionId);
}
