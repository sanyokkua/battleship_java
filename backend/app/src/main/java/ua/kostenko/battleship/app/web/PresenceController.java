package ua.kostenko.battleship.app.web;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.GameSnapshot;
import ua.kostenko.battleship.application.usecase.PresenceUseCase;

/** A body-less "I am here" signal; it reads no content type and ignores any body sent (R40). */
@RestController
class PresenceController {
    private final PresenceUseCase presence;

    PresenceController(PresenceUseCase presence) {
        this.presence = presence;
    }

    @PostMapping("/api/v1/games/{gameId}/presence")
    GameSnapshot sendPresence(
            @PathVariable String gameId,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        return SnapshotDtoAssembler.assemble(presence.execute(gameId, presentedSession));
    }
}
