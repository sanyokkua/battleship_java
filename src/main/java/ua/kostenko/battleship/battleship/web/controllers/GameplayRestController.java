package ua.kostenko.battleship.battleship.web.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerApi;
import ua.kostenko.battleship.battleship.logic.api.dtos.*;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

@RequestMapping("/api/game/sessions/{sessionId}")
@RestController
@RequiredArgsConstructor
public class GameplayRestController {
    private final ControllerApi controller;

    @GetMapping(value = "players", params = "active")
    public ResponseEntity<PlayerBaseInfoDto> getActivePlayer(@PathVariable String sessionId) {
        return controller.getActivePlayer(sessionId);
    }

    @PostMapping(value = "players/{playerId}/field", params = "shot")
    public ResponseEntity<ShotResultDto> makeShot(
            @PathVariable String sessionId, @PathVariable String playerId, @RequestBody Coordinate coordinate) {
        return controller.makeShot(sessionId, playerId, coordinate);
    }

    @GetMapping(value = "players/{playerId}/cells")
    public ResponseEntity<UndamagedCellsDto> getNumberOfUndamagedCells(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controller.getNumberOfUndamagedCells(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/ships", params = "NotDestroyed")
    public ResponseEntity<NumberOfAliveShipsDto> getNumberOfNotDestroyedShips(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controller.getNumberOfNotDestroyedShips(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/opponent/cells")
    public ResponseEntity<UndamagedCellsDto> getNumberOfUndamagedCellsOpponent(
            @PathVariable String sessionId, @PathVariable String playerId) {

        return controller.getNumberOfUndamagedCellsOpponent(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/opponent/ships", params = "NotDestroyed")
    public ResponseEntity<NumberOfAliveShipsDto> getNumberOfNotDestroyedShipsOpponent(
            @PathVariable String sessionId, @PathVariable String playerId) {

        return controller.getNumberOfNotDestroyedShipsOpponent(sessionId, playerId);
    }

    @GetMapping("winner")
    public ResponseEntity<PlayerBaseInfoDto> getWinner(@PathVariable String sessionId) {
        return controller.getWinner(sessionId);
    }

    @GetMapping(value = "players/{playerId}/state")
    public ResponseEntity<GameplayStateDto> getGameStatus(
            @PathVariable String sessionId, @PathVariable String playerId) {

        return controller.getState(sessionId, playerId);
    }

    @GetMapping(value = "players/{playerId}/lastupdate")
    public ResponseEntity<LastGameUpdateDto> getLastUpdate(
            @PathVariable String sessionId, @PathVariable String playerId) {

        return controller.getLastUpdate(sessionId, playerId);
    }
}
