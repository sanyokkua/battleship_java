package ua.kostenko.battleship.battleship.web.controllers.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.session.*;

public interface GameSessionCommonApi {
    ResponseEntity<ResponseAvailableGameEditionsDto> getAvailableGameEditions();

    ResponseEntity<ResponseCreatedSessionIdDto> createGameSession(ParamGameEditionDto gameEdition);

    ResponseEntity<ResponseCreatedPlayerDto> createPlayerInSession(String sessionId, ParamPlayerNameDto playerNameDto);

    ResponseEntity<ResponseCurrentGameStageDto> getCurrentGameStage(String sessionId);

    ResponseEntity<ResponseLastSessionChangeTimeDto> getLastSessionChangeTime(String sessionId);
}
