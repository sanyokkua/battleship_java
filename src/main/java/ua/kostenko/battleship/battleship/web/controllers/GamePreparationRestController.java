package ua.kostenko.battleship.battleship.web.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerApi;
import ua.kostenko.battleship.battleship.logic.api.dtos.PlayerDto;
import ua.kostenko.battleship.battleship.logic.api.dtos.ShipDto;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.web.controllers.dto.AddShipToFieldBody;

import java.util.Set;

@RequestMapping("/api/game/sessions/{sessionId}")
@RestController
@RequiredArgsConstructor
public class GamePreparationRestController {
    private final ControllerApi controller;

    @PostMapping(value = "players")
    public ResponseEntity<PlayerDto> createPlayerInSession(
            @PathVariable String sessionId, @RequestBody String playerName) {
        return controller.createPlayerInSession(sessionId, playerName);
    }

    @PutMapping(value = "players/{playerId}/ships/{shipId}")
    public ResponseEntity<ShipDto> addShipToField(
            @PathVariable String sessionId, @PathVariable String playerId, @PathVariable String shipId,
            @RequestBody AddShipToFieldBody addShipToFieldBodyDto) {
        return controller.addShipToField(sessionId,
                                         playerId,
                                         shipId,
                                         addShipToFieldBodyDto.getCoordinate(),
                                         addShipToFieldBodyDto.getShipDirection());
    }

    @DeleteMapping(value = "players/{playerId}/ships", params = "delete")
    public ResponseEntity<String> removeShipFromField(
            @PathVariable String sessionId, @PathVariable String playerId, @RequestBody Coordinate coordinate) {
        return controller.removeShipFromField(sessionId, playerId, coordinate);
    }

    @GetMapping(value = "players/{playerId}/ships", params = "available")
    public ResponseEntity<Set<ShipDto>> getPrepareShipsList(
            @PathVariable String sessionId, @PathVariable String playerId) {
        return controller.getPrepareShipsList(sessionId, playerId);
    }

    @PostMapping(value = "players/{playerId}", params = "start")
    public ResponseEntity<PlayerDto> startGame(@PathVariable String sessionId, @PathVariable String playerId) {
        return controller.startGame(sessionId, playerId);
    }
}
