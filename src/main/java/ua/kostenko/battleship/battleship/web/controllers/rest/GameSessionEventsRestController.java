package ua.kostenko.battleship.battleship.web.controllers.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.battleship.web.api.GameSessionEventsApi;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;
import ua.kostenko.battleship.battleship.web.api.dtos.session.ResponseSessionPushDto;
import ua.kostenko.battleship.battleship.web.sse.SessionEventBroadcaster;

/**
 * REST controller exposing Server-Sent Events push notifications for a game session.
 * <p>
 * The GameSessionEventsRestController class replaces client-side polling: a subscribing player
 * receives a full {@link ResponseSessionPushDto} snapshot immediately upon connecting, then a
 * fresh snapshot whenever the session's state changes (an opponent readying up, a shot being
 * resolved, a stage transition, etc.).
 * </p>
 *
 * @see GameSessionEventsApi
 * @see SessionEventBroadcaster
 * @see ResponseSessionPushDto
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/sessions/{sessionId}")
@Tag(name = "Session Events", description = "Server-Sent Events subscription replacing client-side polling for session state changes")
public class GameSessionEventsRestController implements GameSessionEventsApi {

    private final SessionEventBroadcaster broadcaster;

    /**
     * Subscribes a player to a session's state-change push notifications.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the subscribing player
     * @return an SseEmitter streaming state-changed events to the client
     */
    @Operation(
            summary = "Subscribe to session state-change push notifications",
            description = "Opens a Server-Sent Events stream for the given player: an immediate state snapshot is sent on connect, and a fresh snapshot is pushed whenever the session's state changes. Fails if the session id is unknown.")
    @ApiResponse(responseCode = "200",
            description = "SSE stream of ResponseSessionPushDto events named \"state-changed\"",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = ResponseSessionPushDto.class)))
    @ApiResponse(responseCode = "400",
            description = "Unknown session id",
            content = @Content(schema = @Schema(implementation = ExceptionDto.class)))
    @GetMapping(value = "players/{playerId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public SseEmitter subscribeToSessionEvents(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Subscribing player's identifier", required = true, example = "p1")
            @PathVariable final String playerId) {
        return broadcaster.subscribe(sessionId, playerId);
    }
}
