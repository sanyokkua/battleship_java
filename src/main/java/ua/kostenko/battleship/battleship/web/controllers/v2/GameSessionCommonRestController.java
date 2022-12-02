package ua.kostenko.battleship.battleship.web.controllers.v2;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerV2Api;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.web.controllers.api.GameSessionCommonApi;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.session.*;

import java.util.Comparator;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game")
public class GameSessionCommonRestController implements GameSessionCommonApi {
    private GameControllerV2Api controllerV2Api;

    @GetMapping("/editions")
    @Override
    public ResponseEntity<ResponseAvailableGameEditionsDto> getAvailableGameEditions() {
        val editions = controllerV2Api.getAvailableGameEditions();
        val response = ResponseAvailableGameEditionsDto.builder()
                                                       .gameEditions(editions.stream()
                                                                             .map(GameEdition::name)
                                                                             .sorted(Comparator.reverseOrder())
                                                                             .collect(Collectors.toList()))
                                                       .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions")
    @Override
    public ResponseEntity<ResponseCreatedSessionIdDto> createGameSession(
            @RequestBody final ParamGameEditionDto gameEdition) {
        val gameSessionId = controllerV2Api.createGameSession(gameEdition.getGameEdition());

        val response = ResponseCreatedSessionIdDto.builder()
                                                  .sessionId(gameSessionId)
                                                  .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(response);
    }

    @PostMapping(value = "sessions/{sessionId}/players")
    @Override
    public ResponseEntity<ResponseCreatedPlayerDto> createPlayerInSession(
            @PathVariable final String sessionId, @RequestBody final ParamPlayerNameDto playerNameDto) {
        val createdPlayer = controllerV2Api.createPlayerInSession(sessionId, playerNameDto.getPlayerName());

        var response = ResponseCreatedPlayerDto.builder()
                                               .playerName(createdPlayer.getPlayerName())
                                               .playerId(createdPlayer.getPlayerId())
                                               .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(response);
    }

    @GetMapping(value = "sessions/{sessionId}/state")
    @Override
    public ResponseEntity<ResponseCurrentGameStageDto> getCurrentGameStage(@PathVariable final String sessionId) {
        val gameStage = controllerV2Api.getCurrentGameStage(sessionId);

        val response = ResponseCurrentGameStageDto.builder()
                                                  .gameStage(gameStage.name())
                                                  .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "sessions/{sessionId}/changesTime")
    @Override
    public ResponseEntity<ResponseLastSessionChangeTimeDto> getLastSessionChangeTime(
            @PathVariable final String sessionId) {
        val localDateTimeValueString = controllerV2Api.getLastSessionChangeTime(sessionId);

        val response = ResponseLastSessionChangeTimeDto.builder()
                                                       .lastId(localDateTimeValueString)
                                                       .build();

        return ResponseEntity.ok(response);
    }
}
