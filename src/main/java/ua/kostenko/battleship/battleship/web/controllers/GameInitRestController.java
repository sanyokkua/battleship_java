package ua.kostenko.battleship.battleship.web.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerApi;
import ua.kostenko.battleship.battleship.logic.api.dtos.GameEditionsDto;
import ua.kostenko.battleship.battleship.logic.api.dtos.GameSessionIdDto;
import ua.kostenko.battleship.battleship.web.controllers.dto.GameEditionDto;

@RequestMapping("/api/game")
@RestController
@RequiredArgsConstructor
public class GameInitRestController {
    private final ControllerApi controller;

    @GetMapping("/editions")
    public ResponseEntity<GameEditionsDto> getGameEditions() {
        return controller.getGameEditions();
    }

    @PostMapping("/sessions")
    public ResponseEntity<GameSessionIdDto> createGameSession(@RequestBody GameEditionDto gameEdition) {
        return controller.createGameSession(gameEdition.getGameEdition());
    }
}
