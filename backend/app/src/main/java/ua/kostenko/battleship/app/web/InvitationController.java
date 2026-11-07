package ua.kostenko.battleship.app.web;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.GameSnapshot;
import ua.kostenko.battleship.application.usecase.ReplaceInvitationUseCase;

/** Replaces the host's invitation. The operation takes no request body, so no content type is ever checked (R40). */
@RestController
class InvitationController {
    private final ReplaceInvitationUseCase replaceInvitation;

    InvitationController(ReplaceInvitationUseCase replaceInvitation) {
        this.replaceInvitation = replaceInvitation;
    }

    @PostMapping("/api/v1/games/{gameId}/invitation")
    GameSnapshot replaceInvitation(
            @PathVariable String gameId,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        return SnapshotDtoAssembler.assemble(replaceInvitation.execute(gameId, presentedSession));
    }
}
