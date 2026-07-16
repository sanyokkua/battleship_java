package ua.kostenko.battleship.battleship.web.exceptions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.web.controllers.rest.GameSessionCommonRestController;

import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Table-driven, end-to-end coverage proving every typed exception maps to the correct HTTP status and
 * {@link ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto} shape through the real Spring
 * exception-handling pipeline ({@link ValidationExceptionHandler} and {@link InternalExceptionHandler}, both
 * {@code @RestControllerAdvice} beans picked up by {@link WebMvcTest}) rather than by invoking handler methods
 * directly.
 * <p>
 * {@link GameSessionCommonRestController#getAvailableGameEditions()} — the simplest endpoint, no path variables
 * or request body — is used purely as a vehicle: {@link GameControllerApi} is mocked to throw each exception
 * type in turn, and the assertions target only the resulting HTTP response.
 */
@WebMvcTest(GameSessionCommonRestController.class)
class ExceptionHandlersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameControllerApi controllerV2Api;

    static Stream<Arguments> typedExceptions() {
        return Stream.of(Arguments.of(new GameCoordinateIsNotCorrectIncorrectException("bad coordinate"),
                        "COORDINATE_INVALID"),
                Arguments.of(new GameEditionIsNotCorrectException("bad edition"), "EDITION_INVALID"),
                Arguments.of(new GamePlayerIdIsNotCorrectException("bad player id"),
                        "PLAYER_ID_INVALID"),
                Arguments.of(new GamePlayerNameIsNotCorrectException("bad player name"),
                        "PLAYER_NAME_INVALID"),
                Arguments.of(new GamePlayerNotActiveException("player not active"),
                        "PLAYER_NOT_ACTIVE"),
                Arguments.of(new GameSessionIdIsNotCorrectException("unknown session"),
                        "SESSION_NOT_FOUND"),
                Arguments.of(new GameShipDirectionIsNotCorrectException("bad direction"),
                        "SHIP_DIRECTION_INVALID"),
                Arguments.of(new GameShipIdIsNotCorrectException("bad ship id"), "SHIP_ID_INVALID"),
                Arguments.of(new GameStageIsNotCorrectException("bad stage"), "STAGE_INVALID"));
    }

    static Stream<Arguments> internalException() {
        return Stream.of(Arguments.of(new GameInternalProblemException("unexpected failure")));
    }

    @ParameterizedTest(name = "{0} -> 400 / errorCode={1}")
    @MethodSource("typedExceptions")
    void typedException_mapsTo400WithErrorCodeAndMessagePassthrough(RuntimeException ex, String expectedErrorCode)
            throws Exception {
        when(controllerV2Api.getAvailableGameEditions()).thenThrow(ex);

        mockMvc.perform(get("/api/v2/game/editions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorMessage").value(ex.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode));
    }

    @ParameterizedTest(name = "GameInternalProblemException -> 500 / errorCode=INTERNAL")
    @MethodSource("internalException")
    void internalException_mapsTo500WithInternalErrorCode(RuntimeException ex) throws Exception {
        when(controllerV2Api.getAvailableGameEditions()).thenThrow(ex);

        mockMvc.perform(get("/api/v2/game/editions"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorMessage").value(ex.getMessage()))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL"));
    }
}
