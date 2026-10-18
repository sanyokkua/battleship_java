package ua.kostenko.battleship.app.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.application.usecase.LeaveGameUseCase;

/** Leaves a game; the only operation that answers with no snapshot and no body (R16, R40). */
@RestController
class LeaveController {
    private final LeaveGameUseCase leaveGame;

    LeaveController(LeaveGameUseCase leaveGame) {
        this.leaveGame = leaveGame;
    }

    @PostMapping("/api/v1/games/{gameId}/leave")
    ResponseEntity<Void> leaveGame(
            @PathVariable String gameId,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        leaveGame.execute(gameId, presentedSession);
        return ResponseEntity.noContent().build();
    }
}
