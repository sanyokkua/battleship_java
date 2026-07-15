package ua.kostenko.battleship.battleship.web.api;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API interface for subscribing to a session's push notifications in the Battleship game.
 * <p>
 * The GameSessionEventsApi interface defines the Server-Sent Events subscription endpoint that
 * replaces client-side polling: a subscribing player receives a full state snapshot immediately
 * on connect, then a fresh snapshot whenever the session's state changes.
 * </p>
 */
public interface GameSessionEventsApi {

    /**
     * Subscribes a player to a session's state-change push notifications.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the subscribing player
     * @return an SseEmitter streaming state-changed events to the client
     */
    SseEmitter subscribeToSessionEvents(String sessionId, String playerId);
}
