package ua.kostenko.battleship.battleship.web.controllers.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameEditionIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GamePlayerNameIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameSessionIdIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.web.api.dtos.session.ParamGameEditionDto;
import ua.kostenko.battleship.battleship.web.api.dtos.session.ParamPlayerNameDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest} coverage for
 * {@link GameSessionCommonRestController}. {@link GameControllerApi} is mocked so each test exercises only
 * controller wiring: request/path binding, response DTO shape, and the HTTP status + {@code errorCode}
 * produced by {@link ua.kostenko.battleship.battleship.web.exceptions.ValidationExceptionHandler} for the
 * validation/not-found cases each endpoint's contract documents.
 */
@WebMvcTest(GameSessionCommonRestController.class)
class GameSessionCommonRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private GameControllerApi controllerV2Api;

    // ---- GET /api/v2/game/editions ----

    @Test
    void getAvailableGameEditions_returnsEditionsSortedReverseAlphabetically() throws Exception {
        when(controllerV2Api.getAvailableGameEditions()).thenReturn(List.of(GameEdition.UKRAINIAN,
                                                                              GameEdition.MILTON_BRADLEY));

        mockMvc.perform(get("/api/v2/game/editions"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.gameEditions[0]").value("UKRAINIAN"))
               .andExpect(jsonPath("$.gameEditions[1]").value("MILTON_BRADLEY"))
               .andExpect(jsonPath("$.gameEditions.length()").value(2));
    }

    // ---- POST /api/v2/game/sessions ----

    @Test
    void createGameSession_returns201WithSessionId() throws Exception {
        when(controllerV2Api.createGameSession(GameEdition.UKRAINIAN.name())).thenReturn("session-123");

        var body = ParamGameEditionDto.builder()
                                       .gameEdition(GameEdition.UKRAINIAN.name())
                                       .build();

        mockMvc.perform(post("/api/v2/game/sessions").contentType(MediaType.APPLICATION_JSON)
                                                       .content(objectMapper.writeValueAsString(body)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.sessionId").value("session-123"));
    }

    @Test
    void createGameSession_invalidEdition_returns400WithEditionInvalidErrorCode() throws Exception {
        when(controllerV2Api.createGameSession(eq("NOT_AN_EDITION"))).thenThrow(new GameEditionIsNotCorrectException(
                "bad edition"));

        var body = ParamGameEditionDto.builder()
                                       .gameEdition("NOT_AN_EDITION")
                                       .build();

        mockMvc.perform(post("/api/v2/game/sessions").contentType(MediaType.APPLICATION_JSON)
                                                       .content(objectMapper.writeValueAsString(body)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value(400))
               .andExpect(jsonPath("$.errorMessage").value("bad edition"))
               .andExpect(jsonPath("$.errorCode").value("EDITION_INVALID"));
    }

    // ---- POST /api/v2/game/sessions/{sessionId}/players ----

    @Test
    void createPlayerInSession_returns201WithPlayerIdAndName() throws Exception {
        var createdPlayer = playerStub("player-1", "Alice");
        when(controllerV2Api.createPlayerInSession("session-123", "Alice")).thenReturn(createdPlayer);

        var body = ParamPlayerNameDto.builder()
                                      .playerName("Alice")
                                      .build();

        mockMvc.perform(post("/api/v2/game/sessions/session-123/players").contentType(MediaType.APPLICATION_JSON)
                                                                           .content(objectMapper.writeValueAsString(
                                                                                   body)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.playerId").value("player-1"))
               .andExpect(jsonPath("$.playerName").value("Alice"));
    }

    @Test
    void createPlayerInSession_invalidPlayerName_returns400WithPlayerNameInvalidErrorCode() throws Exception {
        when(controllerV2Api.createPlayerInSession(eq("session-123"),
                                                     eq(""))).thenThrow(new GamePlayerNameIsNotCorrectException(
                "bad name"));

        var body = ParamPlayerNameDto.builder()
                                      .playerName("")
                                      .build();

        mockMvc.perform(post("/api/v2/game/sessions/session-123/players").contentType(MediaType.APPLICATION_JSON)
                                                                           .content(objectMapper.writeValueAsString(
                                                                                   body)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("PLAYER_NAME_INVALID"));
    }

    @Test
    void createPlayerInSession_sessionNotFound_returns400WithSessionNotFoundErrorCode() throws Exception {
        when(controllerV2Api.createPlayerInSession(eq("unknown-session"),
                                                     eq("Alice"))).thenThrow(new GameSessionIdIsNotCorrectException(
                "unknown session"));

        var body = ParamPlayerNameDto.builder()
                                      .playerName("Alice")
                                      .build();

        mockMvc.perform(post("/api/v2/game/sessions/unknown-session/players").contentType(MediaType.APPLICATION_JSON)
                                                                               .content(objectMapper.writeValueAsString(
                                                                                       body)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    // ---- GET /api/v2/game/sessions/{sessionId}/state ----

    @Test
    void getCurrentGameStage_returnsStageName() throws Exception {
        when(controllerV2Api.getCurrentGameStage("session-123")).thenReturn(GameStage.PREPARATION);

        mockMvc.perform(get("/api/v2/game/sessions/session-123/state"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.gameStage").value("PREPARATION"));
    }

    @Test
    void getCurrentGameStage_sessionNotFound_returns400WithSessionNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getCurrentGameStage("unknown-session")).thenThrow(new GameSessionIdIsNotCorrectException(
                "unknown session"));

        mockMvc.perform(get("/api/v2/game/sessions/unknown-session/state"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"))
               .andExpect(jsonPath("$.errorMessage").value("unknown session"));
    }

    // ---- GET /api/v2/game/sessions/{sessionId}/changesTime ----

    @Test
    void getLastSessionChangeTime_returnsTimestampValue() throws Exception {
        when(controllerV2Api.getLastSessionChangeTime("session-123")).thenReturn("2026-07-11T12:00:00");

        mockMvc.perform(get("/api/v2/game/sessions/session-123/changesTime"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.lastId").value("2026-07-11T12:00:00"));
    }

    @Test
    void getLastSessionChangeTime_sessionNotFound_returns400WithSessionNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getLastSessionChangeTime("unknown-session")).thenThrow(new GameSessionIdIsNotCorrectException(
                "unknown session"));

        mockMvc.perform(get("/api/v2/game/sessions/unknown-session/changesTime"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    private Player playerStub(String playerId, String playerName) {
        return Player.builder()
                      .playerId(playerId)
                      .playerName(playerName)
                      .fieldManagement(mock(ua.kostenko.battleship.battleship.logic.engine.FieldManagement.class))
                      .shipsNotOnTheField(java.util.Set.of())
                      .allPlayerShips(java.util.Set.of())
                      .build();
    }
}
