package ua.kostenko.battleship.app.web;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.app.realtime.SseHub;

/**
 * Opens the caller's live stream. The first event is always the current snapshot, so {@code Last-Event-ID} is
 * ignored and nothing is replayed (R27).
 */
@RestController
class EventsController {
    private final SseHub hub;

    EventsController(SseHub hub) {
        this.hub = hub;
    }

    @GetMapping("/api/v1/games/{gameId}/events")
    SseEmitter streamGameEvents(
            @PathVariable String gameId,
            @CookieValue(name = SessionCookie.NAME, required = false) String presentedSession) {
        return hub.open(gameId, presentedSession);
    }
}
