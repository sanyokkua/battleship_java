package ua.kostenko.battleship.battleship.web.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerApi;
import ua.kostenko.battleship.battleship.logic.api.dtos.PlayerBaseInfoDto;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

@RequestMapping("/api/game/sessions/{sessionId}")
@RestController
@RequiredArgsConstructor
public class GamePlayRestController {
    private final ControllerApi controller;

    @GetMapping(value = "players", params = "active")
    public ResponseEntity<PlayerBaseInfoDto> getActivePlayer(@PathVariable String sessionId) {
        return controller.getActivePlayer(sessionId);
    }

    @PostMapping(value = "players/{playerId}/field", params = "shot")
    public ResponseEntity<ShotResult> makeShot(
            @PathVariable String sessionId, @PathVariable String playerId, @RequestBody Coordinate coordinate) {
        return controller.makeShot(sessionId, playerId, coordinate);
    }

    @GetMapping(value = "players/{playerId}/cells")
    public ResponseEntity<Integer> getNumberOfUndamagedCells(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controller.getNumberOfUndamagedCells(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/ships", params = "NotDestroyed")
    public ResponseEntity<Integer> getNumberOfNotDestroyedShips(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controller.getNumberOfNotDestroyedShips(sessionId, playerId);
    }

    @GetMapping("winner")
    public ResponseEntity<PlayerBaseInfoDto> getWinner(@PathVariable String sessionId) {
        return controller.getWinner(sessionId);
    }
}
