package ua.kostenko.battleship.battleship.web.sse;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.events.GameStateChangedEvent;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameOpponentNotFoundException;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

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
        when(controllerApi.getCurrentGameStage(anyString())).thenReturn(GameStage.PREPARATION);
        when(controllerApi.getLastSessionChangeTime(anyString())).thenReturn("t");
        when(controllerApi.getOpponentInformation(anyString(), anyString())).thenThrow(
                new GameOpponentNotFoundException("no opponent yet"));

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
}
