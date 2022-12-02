package ua.kostenko.battleship.battleship.web.controllers.v2;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerV2Api;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.web.controllers.api.GameplayControllerApi;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.gameplay.ResponseShotResultDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/sessions/{sessionId}")
public class GameplayRestController implements GameplayControllerApi {
    private GameControllerV2Api controllerV2Api;


    @GetMapping(value = "players/{playerId}/state")
    @Override
    public ResponseEntity<ResponseGameplayStateDto> getGameStateForPlayer(
            @PathVariable final String sessionId, @PathVariable final String playerId) {
        val gameplayState = controllerV2Api.getGameState(sessionId, playerId);

        val response = ResponseGameplayStateDto.from(gameplayState);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "players/{playerId}/field", params = "shot")
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
