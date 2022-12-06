package ua.kostenko.battleship.battleship.web.controllers.rest;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.web.api.GameplayControllerApi;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseShotResultDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/sessions/{sessionId}")
public class GameplayRestController implements GameplayControllerApi {
    private final GameControllerApi controllerV2Api;

    @GetMapping(value = "players/{playerId}/state")
    @Override
    public ResponseEntity<ResponseGameplayStateDto> getGameStateForPlayer(
            @PathVariable final String sessionId, @PathVariable final String playerId) {
        val gameplayState = controllerV2Api.getGameState(sessionId, playerId);

        val response = ResponseGameplayStateDto.from(gameplayState);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "players/{playerId}/field/shot")
    @Override
    public ResponseEntity<ResponseShotResultDto> makeShotByField(
            @PathVariable final String sessionId, @PathVariable final String playerId,
            @RequestBody final ParamCoordinateDto coordinate) {
        val shotResult = controllerV2Api.makeShotByField(sessionId,
                                                         playerId,
                                                         Coordinate.of(coordinate.getRow(), coordinate.getCol()));

        val response = new ResponseShotResultDto(shotResult.name());

        return ResponseEntity.ok(response);
    }
}
