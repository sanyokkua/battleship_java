package ua.kostenko.battleship.battleship.web.sse;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.events.GameStateChangedEvent;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameOpponentNotFoundException;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers {@link SessionEventBroadcaster}'s subscribe/snapshot-on-subscribe/broadcast/dead-emitter
 * behavior in isolation from any real HTTP request, using a mocked {@link GameControllerApi} to
 * supply payload data.
 */
class SessionEventBroadcasterTest {

    @Mock
    GameControllerApi controllerApi;

    SessionEventBroadcaster broadcaster;

    private static Cell[][] field() {
        val field = new Cell[GameEditionConfiguration.NUMBER_OF_ROWS][GameEditionConfiguration.NUMBER_OF_COLUMNS];
        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS; j++) {
                field[i][j] = Cell.builder()
                        .coordinate(Coordinate.of(i, j))
                        .hasShot(false)
                        .isAvailable(true)
                        .build();
            }
        }
        return field;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        broadcaster = new SessionEventBroadcaster(controllerApi);
    }

    @Test
    void subscribe_sendsImmediateSnapshotAndReturnsANonNullEmitter() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.WAITING_FOR_PLAYERS);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "playerId")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        val emitter = broadcaster.subscribe("sessionId", "playerId");

        assertThat(emitter).isNotNull();
        verify(controllerApi, times(1)).getCurrentGameStage("sessionId");
        verify(controllerApi, never()).getGameState(anyString(), anyString());
    }

    @Test
    void subscribe_propagatesFailureBuildingTheInitialSnapshotWithoutRegisteringAnEmitter() {
        when(controllerApi.getCurrentGameStage("unknown")).thenThrow(new RuntimeException("session not found"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> broadcaster.subscribe("unknown", "playerId"));

        // A failed initial snapshot must not leave a dangling subscription: a later broadcast for
        // the same (bad) session id has nothing to iterate and must not touch the controller API.
        broadcaster.onGameStateChanged(new GameStateChangedEvent("unknown"));
        verify(controllerApi, times(1)).getCurrentGameStage("unknown");
    }

    @Test
    void broadcast_pushesAFreshPayloadToEverySubscribedPlayerOfTheChangedSession() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "alice")).thenReturn(new OpponentInfo("Bob", false));
        when(controllerApi.getOpponentInformation("sessionId", "bob")).thenReturn(new OpponentInfo("Alice", false));

        broadcaster.subscribe("sessionId", "alice");
        broadcaster.subscribe("sessionId", "bob");
        verify(controllerApi, times(1)).getOpponentInformation("sessionId", "alice");
        verify(controllerApi, times(1)).getOpponentInformation("sessionId", "bob");

        broadcaster.onGameStateChanged(new GameStateChangedEvent("sessionId"));

        verify(controllerApi, times(2)).getOpponentInformation("sessionId", "alice");
        verify(controllerApi, times(2)).getOpponentInformation("sessionId", "bob");
    }

    @Test
    void broadcast_ignoresChangesForSessionsWithNoSubscribers() {
        broadcaster.onGameStateChanged(new GameStateChangedEvent("no-subscribers"));
        verifyNoInteractions(controllerApi);
    }

    @Test
    void broadcast_removesADeadEmitterWithoutThrowing() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "alice")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        val emitter = broadcaster.subscribe("sessionId", "alice");
        emitter.complete(); // simulates the client already having disconnected

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> broadcaster.onGameStateChanged(new GameStateChangedEvent("sessionId")));
    }

    @Test
    void buildPayload_includesGameplayStateOnlyOnceAnOpponentExistsAndStageIsInGameOrFinished() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.IN_GAME);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "alice")).thenReturn(new OpponentInfo("Bob", true));
        when(controllerApi.getGameState("sessionId", "alice")).thenReturn(GameplayState.builder()
                .playerName("Alice")
                .opponentName("Bob")
                .playerField(field())
                .opponentField(field())
                .build());

        broadcaster.subscribe("sessionId", "alice");

        verify(controllerApi, times(1)).getGameState("sessionId", "alice");
    }

    @Test
    void buildPayload_skipsGameplayStateWhileWaitingForAnOpponentEvenIfSomehowInGame() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.IN_GAME);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "alice")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        broadcaster.subscribe("sessionId", "alice");

        verify(controllerApi, never()).getGameState(anyString(), anyString());
    }

    @Test
    void sendHeartbeats_sendsHeartbeatCommentToEveryRegisteredEmitterAcrossMultipleSessionsAndPlayers() {
        when(controllerApi.getCurrentGameStage("session1")).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime("session1")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("session1", "alice")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));
        when(controllerApi.getCurrentGameStage("session2")).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime("session2")).thenReturn("t2");
        when(controllerApi.getOpponentInformation("session2", "bob")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        broadcaster.subscribe("session1", "alice");
        broadcaster.subscribe("session2", "bob");

        // Reset mock interactions before calling sendHeartbeats
        reset(controllerApi);

        broadcaster.sendHeartbeats();

        // Verify that no controllerApi methods were called during sendHeartbeats (heartbeats are comments)
        verifyNoInteractions(controllerApi);
    }

    @Test
    void sendHeartbeats_doesNothingWhenNoSubscribersAreRegistered() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> broadcaster.sendHeartbeats());
        verifyNoInteractions(controllerApi);
    }

    @Test
    void sendHeartbeats_silentlyRemovesDeadEmitterWithoutThrowing() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "alice")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        val emitter = broadcaster.subscribe("sessionId", "alice");
        emitter.complete(); // simulates the client having disconnected

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> broadcaster.sendHeartbeats());
    }

    @Test
    void removeEmitter_cleansUpEmptyPlayerAndSessionEntriesAfterTheSoleSubscriberDisconnects() {
        when(controllerApi.getCurrentGameStage("sessionId")).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime("sessionId")).thenReturn("t1");
        when(controllerApi.getOpponentInformation("sessionId", "alice")).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        val emitter = broadcaster.subscribe("sessionId", "alice");
        verify(controllerApi, times(1)).getOpponentInformation("sessionId", "alice");

        emitter.complete(); // simulates the client already having disconnected. Note: with no real
        // Handler attached (only happens via Spring MVC's async request lifecycle, absent here),
        // ResponseBodyEmitter#complete() does not itself invoke the onCompletion callback — the
        // dead emitter is only discovered, and removeEmitter only triggered, on the next attempted
        // send(), matching this file's existing broadcast_removesADeadEmitterWithoutThrowing test.

        // This first broadcast still builds a payload for "alice" (the entry hasn't been cleaned
        // up yet) but then discovers mid-send that the emitter is dead, which is exactly what
        // triggers removeEmitter's cleanup of the now-empty "alice" entry and, since it was the
        // only player subscribed, the now-empty "sessionId" entry too.
        broadcaster.onGameStateChanged(new GameStateChangedEvent("sessionId"));
        verify(controllerApi, times(2)).getOpponentInformation("sessionId", "alice");

        // A second broadcast must find nothing left to iterate: the "sessionId" entry should
        // already be gone, so the call count stays exactly where it was.
        broadcaster.onGameStateChanged(new GameStateChangedEvent("sessionId"));
        verify(controllerApi, times(2)).getOpponentInformation("sessionId", "alice");

        // A fresh subscribe for the same session/player must behave as if starting clean (a
        // brand-new map entry, not a stale/orphaned one still hanging off some removed key) — it
        // triggers a new initial-snapshot lookup just like the very first subscribe did.
        broadcaster.subscribe("sessionId", "alice");
        verify(controllerApi, times(3)).getOpponentInformation("sessionId", "alice");
    }

    @Test
    void removeEmitter_isAtomicAgainstAConcurrentSubscribeForTheSameSessionAndPlayer() throws Exception {
        // Regression test for a TOCTOU race: removeEmitter's cleanup of the last emitter for a
        // (sessionId, playerId) must not be able to interleave with a concurrent subscribe() for
        // the same key in a way that orphans the newly subscribed emitter (registers it in a list
        // object that then gets unlinked from the `subscribers` map, making it unreachable for all
        // future broadcasts/heartbeats). This drives many real concurrent trials, each pairing a
        // "disconnect" (a direct call to the private removeEmitter, via reflection — see note
        // below) against a "reconnect" (subscribe()) for the same key, started as close to
        // simultaneously as a CountDownLatch allows, then asserts the post-race state is always
        // reachable. This can't deterministically force the exact instant described in the
        // finding (the fix's computeIfPresent makes that window of interleaving impossible by
        // construction), but repeated concurrent trials give strong confidence the atomicity
        // holds, and would be expected to flake under the old check-then-act implementation.
        //
        // removeEmitter is invoked directly via reflection rather than through the emitter's
        // lifecycle (e.g. emitter.complete()) because ResponseBodyEmitter only invokes its
        // onCompletion callback through a real Handler, which is attached by Spring MVC's async
        // request machinery — absent in this plain unit test (see
        // removeEmitter_cleansUpEmptyPlayerAndSessionEntriesAfterTheSoleSubscriberDisconnects).
        // Calling the method directly is the most faithful way to reproduce two threads racing
        // inside removeEmitter/subscribe for the same key, which is exactly what the finding
        // describes.
        when(controllerApi.getCurrentGameStage(anyString())).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime(anyString())).thenReturn("t1");
        when(controllerApi.getOpponentInformation(anyString(), anyString())).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

        val removeEmitterMethod = SessionEventBroadcaster.class.getDeclaredMethod("removeEmitter", String.class,
                String.class, SseEmitter.class);
        removeEmitterMethod.setAccessible(true);

        val iterations = 200;
        for (int i = 0; i < iterations; i++) {
            val sessionId = "race-session-" + i;
            val playerId = "alice";

            val oldEmitter = broadcaster.subscribe(sessionId, playerId); // call #1 for this session

            val start = new CountDownLatch(1);
            val threadAFailure = new AtomicReference<Throwable>();
            val threadBFailure = new AtomicReference<Throwable>();

            val threadA = new Thread(() -> {
                try {
                    start.await();
                    removeEmitterMethod.invoke(broadcaster, sessionId, playerId, oldEmitter);
                } catch (Throwable t) {
                    threadAFailure.set(t);
                }
            });
            val threadB = new Thread(() -> {
                try {
                    start.await();
                    broadcaster.subscribe(sessionId, playerId); // call #2 for this session
                } catch (Throwable t) {
                    threadBFailure.set(t);
                }
            });

            threadA.start();
            threadB.start();
            start.countDown();
            threadA.join();
            threadB.join();

            assertThat(threadAFailure.get()).isNull();
            assertThat(threadBFailure.get()).isNull();

            // If thread B's emitter got orphaned by the race, this session's entry would be
            // missing (or empty) from the map and the broadcast below would find nothing to
            // iterate, so the call count would stay at 2 instead of advancing to 3.
            broadcaster.onGameStateChanged(new GameStateChangedEvent(sessionId));
            verify(controllerApi, times(3)).getOpponentInformation(sessionId, playerId);
        }
    }
}
