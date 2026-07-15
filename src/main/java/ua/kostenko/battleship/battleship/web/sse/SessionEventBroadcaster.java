package ua.kostenko.battleship.battleship.web.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.events.GameStateChangedEvent;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameOpponentNotFoundException;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.ResponseOpponentInformationDto;
import ua.kostenko.battleship.battleship.web.api.dtos.session.ResponseSessionPushDto;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registers per-(session, player) {@link SseEmitter} subscriptions and pushes a full state
 * snapshot to them whenever a {@link GameStateChangedEvent} is published, or immediately on
 * subscribe.
 * <p>
 * Broadcasting is decoupled from {@link GameControllerApi}'s mutating methods via Spring's event
 * mechanism, and is only ever triggered after the originating method has released its per-session
 * lock (see {@link ua.kostenko.battleship.battleship.logic.api.impl.GameControllerApiImpl}), so a
 * slow or blocked emitter can never stall an unrelated request against the same session.
 * </p>
 * <p>
 * Because a full-state push is asymmetric per player (an opponent's ships are hidden unless the
 * game is finished), payloads are built individually per subscribed player rather than broadcast
 * identically to every subscriber of a session.
 * </p>
 */
@Log4j2
@RequiredArgsConstructor
public class SessionEventBroadcaster {

    private final GameControllerApi controllerApi;
    private final Map<String, Map<String, CopyOnWriteArrayList<SseEmitter>>> subscribers = new ConcurrentHashMap<>();

    /**
     * Subscribes a player to a session's push notifications, sending an immediate snapshot of the
     * current state before returning the emitter. Any failure resolving the initial snapshot (e.g.
     * an unknown session ID) propagates to the caller synchronously, before any SSE state is
     * registered, so it's reported the same way every other endpoint reports such failures.
     *
     * @param sessionId the ID of the game session
     * @param playerId  the ID of the subscribing player
     * @return the emitter the caller's controller method should return to the client
     */
    public SseEmitter subscribe(final String sessionId, final String playerId) {
        val initialPayload = buildPayload(sessionId, playerId);

        val emitter = new SseEmitter(0L);
        val playerEmitters = subscribers.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new CopyOnWriteArrayList<>());
        playerEmitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(sessionId, playerId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, playerId, emitter));
        emitter.onError(ex -> removeEmitter(sessionId, playerId, emitter));

        send(sessionId, playerId, emitter, initialPayload);

        return emitter;
    }

    /**
     * Pushes the current state to every player subscribed to the changed session. Runs on the
     * publishing thread (the same thread that just released the session's lock); failures building
     * or sending any single player's payload are logged and skipped rather than propagated, so they
     * can never surface as an error response for the request that triggered the change.
     *
     * @param event the change notification
     */
    @EventListener
    public void onGameStateChanged(final GameStateChangedEvent event) {
        val sessionId = event.sessionId();
        val playerSubscribers = subscribers.get(sessionId);
        if (playerSubscribers == null) {
            return;
        }

        playerSubscribers.forEach((playerId, emitters) -> {
            try {
                val payload = buildPayload(sessionId, playerId);
                for (val emitter : List.copyOf(emitters)) {
                    send(sessionId, playerId, emitter, payload);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed building SSE payload for session {} player {}: {}", sessionId, playerId,
                        ex.getMessage());
            }
        });
    }

    private void send(
            final String sessionId, final String playerId, final SseEmitter emitter,
            final ResponseSessionPushDto payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name("state-changed")
                    .data(payload));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Removing dead SSE emitter for session {} player {}: {}", sessionId, playerId,
                    ex.getMessage());
            removeEmitter(sessionId, playerId, emitter);
            emitter.completeWithError(ex);
        }
    }

    private ResponseSessionPushDto buildPayload(final String sessionId, final String playerId) {
        val stage = controllerApi.getCurrentGameStage(sessionId);
        val lastUpdate = controllerApi.getLastSessionChangeTime(sessionId);

        ResponseOpponentInformationDto opponent = null;
        try {
            opponent = ResponseOpponentInformationDto.from(controllerApi.getOpponentInformation(sessionId, playerId));
        } catch (GameOpponentNotFoundException ex) {
            log.debug("No opponent yet for session {} player {}", sessionId, playerId);
        }

        ResponseGameplayStateDto gameplayState = null;
        if (opponent != null && (stage == GameStage.IN_GAME || stage == GameStage.FINISHED)) {
            gameplayState = ResponseGameplayStateDto.from(controllerApi.getGameState(sessionId, playerId));
        }

        return ResponseSessionPushDto.builder()
                .gameStage(stage.name())
                .lastUpdate(lastUpdate)
                .opponent(opponent)
                .gameplayState(gameplayState)
                .build();
    }

    private void removeEmitter(final String sessionId, final String playerId, final SseEmitter emitter) {
        val playerSubscribers = subscribers.get(sessionId);
        if (playerSubscribers == null) {
            return;
        }
        val emitters = playerSubscribers.get(playerId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
