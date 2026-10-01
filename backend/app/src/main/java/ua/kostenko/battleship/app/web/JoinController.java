package ua.kostenko.battleship.app.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.GameSnapshot;
import ua.kostenko.battleship.app.web.dto.JoinGameRequest;
import ua.kostenko.battleship.application.usecase.JoinGameUseCase;

/** Redeems an invitation, or re-reads the caller's own game when it already is the guest (R34, R45, R63). */
@RestController
class JoinController {
    private final JoinGameUseCase joinGame;

    JoinController(JoinGameUseCase joinGame) {
        this.joinGame = joinGame;
    }

    @PostMapping("/api/v1/games/{gameId}/join")
    ResponseEntity<GameSnapshot> joinGame(
            @PathVariable String gameId,
            @Valid @RequestBody JoinGameRequest request,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        var joined =
                joinGame.execute(gameId, request.getInvitationSecret(), request.getDisplayName(), presentedSession);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (!joined.sessionValue().equals(presentedSession)) {
            response.header(HttpHeaders.SET_COOKIE, SessionCookie.issued(joined.sessionValue()));
        }
        return response.body(SnapshotDtoAssembler.assemble(joined.snapshot()));
    }
}
