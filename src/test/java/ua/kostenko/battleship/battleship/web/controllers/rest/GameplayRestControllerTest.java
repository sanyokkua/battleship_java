package ua.kostenko.battleship.battleship.web.controllers.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest} coverage for
 * {@link GameplayRestController}. {@link GameControllerApi} is mocked so each test exercises only controller
 * wiring: request/path binding, response DTO shape (including correct JSON serialization of {@link
 * ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto#from} output, such as
 * omitting a null {@code ship} field for a controller-supplied {@link Cell} that already arrives unmasked
 * from the engine layer), and the HTTP status + {@code errorCode} produced by {@link
 * ua.kostenko.battleship.battleship.web.exceptions.ValidationExceptionHandler} for the validation/not-found
 * cases each endpoint's contract documents. Note: ship-masking logic itself lives in {@code GameImpl}/{@code
 * FieldManagementImpl} at the engine layer, which this {@code @WebMvcTest} slice mocks out entirely — it is
 * not exercised or verified by this test class.
 * <p>
 * Also carries the out-of-turn-shot regression test for the {@link GamePlayerNotActiveException} fix: a shot
 * made by a non-active player must surface as HTTP 400 with {@code errorCode == "PLAYER_NOT_ACTIVE"}.
 */
@WebMvcTest(GameplayRestController.class)
class GameplayRestControllerTest {

    private static final String SESSION_ID = "session-123";
    private static final String PLAYER_ID = "player-1";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GameControllerApi controllerV2Api;

    // ---- GET /api/v2/game/sessions/{sessionId}/players/{playerId}/state ----

    @Test
    void getGameStateForPlayer_returnsFullGameplayStateJsonShapeWithNullShipOmitted() throws Exception {
        var visibleShip = Ship.builder()
                .shipId("ship-1")
                .shipType(ShipType.PATROL_BOAT)
                .shipSize(1)
                .build();
        // player's own field: fully visible ship at (0,0)
        var playerField = fieldWithSingleCell(Cell.builder()
                .coordinate(Coordinate.of(0, 0))
                .ship(visibleShip)
                .hasShot(false)
                .isAvailable(false)
                .build());
        // opponent field: cell constructed with ship=null, as it would already arrive from the engine layer
        // after upstream masking (not performed by this test); verifies the DTO/JSON layer omits a null ship
        var opponentField = fieldWithSingleCell(Cell.builder()
                .coordinate(Coordinate.of(0, 0))
                .ship(null)
                .hasShot(false)
                .isAvailable(false)
                .build());

        var gameplayState = GameplayState.builder()
                .playerName("Alice")
                .isPlayerActive(true)
                .isPlayerWinner(false)
                .playerNumberOfAliveCells(20)
                .playerNumberOfAliveShips(10)
                .playerField(playerField)
                .opponentName("Bob")
                .isOpponentReady(true)
                .opponentNumberOfAliveCells(18)
                .opponentNumberOfAliveShips(9)
                .opponentField(opponentField)
                .hasWinner(false)
                .winnerPlayerName("")
                .build();

        when(controllerV2Api.getGameState(SESSION_ID, PLAYER_ID)).thenReturn(gameplayState);

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/state", SESSION_ID, PLAYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Alice"))
                .andExpect(jsonPath("$.isPlayerActive").value(true))
                .andExpect(jsonPath("$.isPlayerWinner").value(false))
                .andExpect(jsonPath("$.playerNumberOfAliveCells").value(20))
                .andExpect(jsonPath("$.playerNumberOfAliveShips").value(10))
                .andExpect(jsonPath("$.playerField[0][0].ship.shipId").value("ship-1"))
                .andExpect(jsonPath("$.playerField[0][0].isAvailable").value(false))
                .andExpect(jsonPath("$.opponentName").value("Bob"))
                .andExpect(jsonPath("$.isOpponentReady").value(true))
                .andExpect(jsonPath("$.opponentNumberOfAliveCells").value(18))
                .andExpect(jsonPath("$.opponentNumberOfAliveShips").value(9))
                // JSON shape: a Cell with ship=null (as this test hand-constructs it) must serialize with the
                // ship field omitted, not null-valued; this checks DTO/Jackson serialization only, not the
                // engine-layer masking logic that would normally produce such a cell for a real opponent field
                .andExpect(jsonPath("$.opponentField[0][0].ship").doesNotExist())
                .andExpect(jsonPath("$.opponentField[0][0].isAvailable").value(false))
                .andExpect(jsonPath("$.hasWinner").value(false))
                .andExpect(jsonPath("$.winnerPlayerName").value(""));
    }

    @Test
    void getGameStateForPlayer_invalidPlayerId_returns400WithPlayerIdInvalidErrorCode() throws Exception {
        when(controllerV2Api.getGameState(eq(SESSION_ID),
                eq("not-a-real-player"))).thenThrow(new GamePlayerIdIsNotCorrectException(
                "bad player id"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/state",
                        SESSION_ID,
                        "not-a-real-player"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLAYER_ID_INVALID"));
    }

    @Test
    void getGameStateForPlayer_sessionNotFound_returns400WithSessionNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getGameState("unknown-session", PLAYER_ID)).thenThrow(new GameSessionIdIsNotCorrectException(
                "unknown session"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/state",
                        "unknown-session",
                        PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    /**
     * Regression test for the confirmed production bug: a solo player on the Wait screen polling this
     * endpoint every 5s before a second player has joined must now surface as a clean, structured HTTP 400
     * with {@code errorCode == "OPPONENT_NOT_FOUND"} — not an unhandled {@code IllegalArgumentException}
     * reaching the servlet container's default (unstructured) error response.
     */
    @Test
    void getGameStateForPlayer_soloPlayerPolling_returns400WithOpponentNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getGameState(SESSION_ID, PLAYER_ID)).thenThrow(new GameOpponentNotFoundException(
                "Player with provided filter is not found"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/state", SESSION_ID, PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("OPPONENT_NOT_FOUND"));
    }

    // ---- POST /api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot ----

    @Test
    void makeShotByField_returnsShotResult() throws Exception {
        when(controllerV2Api.makeShotByField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq(Coordinate.of(3, 4)))).thenReturn(ShotResult.HIT);

        var body = ParamCoordinateDto.builder()
                .row(3)
                .col(4)
                .build();

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shotResult").value("HIT"));
    }

    @Test
    void makeShotByField_invalidCoordinate_returns400WithCoordinateInvalidErrorCode() throws Exception {
        when(controllerV2Api.makeShotByField(eq(SESSION_ID),
                eq(PLAYER_ID),
                any())).thenThrow(new GameCoordinateIsNotCorrectIncorrectException(
                "bad coordinate"));

        var body = ParamCoordinateDto.builder()
                .row(-1)
                .col(-1)
                .build();

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COORDINATE_INVALID"));
    }

    @Test
    void makeShotByField_sessionNotFound_returns400WithSessionNotFoundErrorCode() throws Exception {
        when(controllerV2Api.makeShotByField(eq("unknown-session"),
                eq(PLAYER_ID),
                any())).thenThrow(new GameSessionIdIsNotCorrectException(
                "unknown session"));

        var body = ParamCoordinateDto.builder()
                .row(0)
                .col(0)
                .build();

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot",
                        "unknown-session",
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    /**
     * Shooting out of turn must surface as HTTP 400 with {@code errorCode == "PLAYER_NOT_ACTIVE"},
     * not as a 500 or a silently-accepted shot.
     */
    @Test
    void makeShotByField_outOfTurnShot_returns400WithPlayerNotActiveErrorCode() throws Exception {
        when(controllerV2Api.makeShotByField(eq(SESSION_ID),
                eq(PLAYER_ID),
                any())).thenThrow(new GamePlayerNotActiveException(
                "Player is not active"));

        var body = ParamCoordinateDto.builder()
                .row(2)
                .col(2)
                .build();

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("PLAYER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.errorMessage").value("Player is not active"));
    }

    /**
     * Regression test for the cell-re-shoot fix: shooting a cell that was already shot must surface as
     * HTTP 400 with {@code errorCode == "CELL_ALREADY_SHOT"}, not as a 500 or a silently-accepted shot.
     */
    @Test
    void makeShotByField_alreadyShotCell_returns400WithCellAlreadyShotErrorCode() throws Exception {
        when(controllerV2Api.makeShotByField(eq(SESSION_ID),
                eq(PLAYER_ID),
                any())).thenThrow(new GameCellAlreadyShotException(
                "Cell has already been shot"));

        var body = ParamCoordinateDto.builder()
                .row(2)
                .col(2)
                .build();

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("CELL_ALREADY_SHOT"))
                .andExpect(jsonPath("$.errorMessage").value("Cell has already been shot"));
    }

    @Test
    void makeShotByField_wrongStage_returns400WithStageInvalidErrorCode() throws Exception {
        when(controllerV2Api.makeShotByField(eq(SESSION_ID),
                eq(PLAYER_ID),
                any())).thenThrow(new GameStageIsNotCorrectException("wrong stage"));

        var body = ParamCoordinateDto.builder()
                .row(2)
                .col(2)
                .build();

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/field/shot",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAGE_INVALID"));
    }

    private Cell[][] fieldWithSingleCell(Cell cellAtOrigin) {
        var field = new Cell[10][10];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                field[i][j] = Cell.builder()
                        .coordinate(Coordinate.of(i, j))
                        .ship(null)
                        .hasShot(false)
                        .isAvailable(true)
                        .build();
            }
        }
        field[0][0] = cellAtOrigin;
        return field;
    }
}
