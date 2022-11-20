package ua.kostenko.battleship.battleship.web.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.battleship.logic.api.ControllerApi;
import ua.kostenko.battleship.battleship.logic.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.logic.api.dtos.GameStageDto;
import ua.kostenko.battleship.battleship.logic.api.dtos.PlayerBaseInfoDto;
import ua.kostenko.battleship.battleship.logic.api.dtos.PlayerDto;

@RequestMapping("/api/game/sessions/{sessionId}")
@RestController
@RequiredArgsConstructor
public class GameSessionCommonRestController {
    private final ControllerApi controllerApi;

    @GetMapping(value = "players/{playerId}")
    public ResponseEntity<PlayerDto> getPlayer(@PathVariable String sessionId, @PathVariable String playerId) {
        return controllerApi.getPlayer(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}", params = "opponent")
    public ResponseEntity<PlayerBaseInfoDto> getOpponent(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controllerApi.getOpponent(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/field")
    public ResponseEntity<CellDto[][]> getField(@PathVariable String sessionId, @PathVariable String playerId) {
        return controllerApi.getField(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/field", params = "opponent")
    public ResponseEntity<CellDto[][]> getFieldOfOpponent(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controllerApi.getFieldOfOpponent(sessionId, playerId);
    }

    @GetMapping(value = "stage")
    public ResponseEntity<GameStageDto> getStage(@PathVariable final String sessionId) {
        return controllerApi.getStage(sessionId);
    }
}
