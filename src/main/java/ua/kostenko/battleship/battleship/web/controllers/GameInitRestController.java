package ua.kostenko.battleship.battleship.web.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerApi;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;

import java.util.Set;

@RequestMapping("/api/game")
@RestController
@RequiredArgsConstructor
public class GameInitRestController {
    private final ControllerApi controller;

    @GetMapping("/editions")
    public ResponseEntity<Set<GameEdition>> getGameEditions() {
        return controller.getGameEditions();
    }

    @PostMapping("/sessions")
    public ResponseEntity<String> createGameSession(@RequestBody String gameEdition) {
        return controller.createGameSession(gameEdition);
    }
}
