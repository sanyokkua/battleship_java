package ua.kostenko.battleship.app.web;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.CreateGameRequest;
import ua.kostenko.battleship.app.web.dto.GameSnapshot;
import ua.kostenko.battleship.application.usecase.CreateGameUseCase;
import ua.kostenko.battleship.application.usecase.GetGameUseCase;

/** Creates a game and reads it back; every answer is the caller's snapshot (R17). */
@RestController
class GameController {
    private final CreateGameUseCase createGame;
    private final GetGameUseCase getGame;

    GameController(CreateGameUseCase createGame, GetGameUseCase getGame) {
        this.createGame = createGame;
        this.getGame = getGame;
    }

    @PostMapping("/api/v1/games")
    ResponseEntity<GameSnapshot> createGame(
            @Valid @RequestBody CreateGameRequest request,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        var created = createGame.execute(request.getRulesetId(), request.getDisplayName(), presentedSession);
        GameSnapshot snapshot = SnapshotDtoAssembler.assemble(created.snapshot());
        ResponseEntity.BodyBuilder response =
                ResponseEntity.created(URI.create("/api/v1/games/" + snapshot.getGameId()));
        if (!created.sessionValue().equals(presentedSession)) {
            response.header(HttpHeaders.SET_COOKIE, SessionCookie.issued(created.sessionValue()));
        }
        return response.body(snapshot);
    }

    @GetMapping("/api/v1/games/{gameId}")
    GameSnapshot getGame(
            @PathVariable String gameId,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        return SnapshotDtoAssembler.assemble(getGame.execute(gameId, presentedSession));
    }
}
