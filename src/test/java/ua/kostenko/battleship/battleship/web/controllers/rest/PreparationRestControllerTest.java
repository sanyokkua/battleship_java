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
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.ParamShipDto;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest} coverage for
 * {@link PreparationRestController}. {@link GameControllerApi} is mocked so each test exercises only controller
 * wiring: request/path binding, response DTO shape (including the {@link
 * ua.kostenko.battleship.battleship.web.api.dtos.preparation.ResponseShipRemovedDto#fromString} blank/non-blank
 * mapping), and the HTTP status + {@code errorCode} produced by {@link
 * ua.kostenko.battleship.battleship.web.exceptions.ValidationExceptionHandler} for the validation/not-found
 * cases each endpoint's contract documents.
 * <p>
 * Note: {@code ResponseShipsNotOnTheBoard} is not reachable from this controller — {@code getPreparationState}
 * builds {@code ResponsePreparationState} directly from {@code ShipDto}s, so that DTO is intentionally not
 * exercised here.
 */
@WebMvcTest(PreparationRestController.class)
class PreparationRestControllerTest {

    private static final String SESSION_ID = "session-123";
    private static final String PLAYER_ID = "player-1";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GameControllerApi controllerV2Api;

    // ---- GET /api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState ----

    @Test
    void getPreparationState_returnsShipsSortedBySizeAndFieldShape() throws Exception {
        var bigShip = Ship.builder()
                .shipId("ship-big")
                .shipType(ShipType.BATTLESHIP)
                .shipSize(4)
                .build();
        var smallShip = Ship.builder()
                .shipId("ship-small")
                .shipType(ShipType.PATROL_BOAT)
                .shipSize(1)
                .build();

        when(controllerV2Api.getShipsNotOnTheBoard(SESSION_ID, PLAYER_ID)).thenReturn(List.of(bigShip, smallShip));
        when(controllerV2Api.getPreparationField(SESSION_ID, PLAYER_ID)).thenReturn(emptyField());

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState",
                        SESSION_ID,
                        PLAYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ships.length()").value(2))
                .andExpect(jsonPath("$.ships[0].shipId").value("ship-small"))
                .andExpect(jsonPath("$.ships[0].shipSize").value(1))
                .andExpect(jsonPath("$.ships[1].shipId").value("ship-big"))
                .andExpect(jsonPath("$.ships[1].shipSize").value(4))
                .andExpect(jsonPath("$.field[0][0].isAvailable").value(true))
                .andExpect(jsonPath("$.field[0][0].hasShot").value(false));
    }

    @Test
    void getPreparationState_wrongStage_returns400WithStageInvalidErrorCode() throws Exception {
        when(controllerV2Api.getShipsNotOnTheBoard(SESSION_ID, PLAYER_ID)).thenThrow(new GameStageIsNotCorrectException(
                "wrong stage"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState",
                        SESSION_ID,
                        PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAGE_INVALID"));
    }

    @Test
    void getPreparationState_playerNotFound_returns400WithPlayerNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getShipsNotOnTheBoard(SESSION_ID, PLAYER_ID)).thenThrow(new GamePlayerNotFoundException(
                "stale player id"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/preparationState",
                        SESSION_ID,
                        PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLAYER_NOT_FOUND"));
    }

    // ---- PUT /api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId} ----

    @Test
    void addShipToField_returnsAddedShipId() throws Exception {
        var ship = Ship.builder()
                .shipId("ship-1")
                .shipType(ShipType.SUBMARINE)
                .shipDirection(ShipDirection.HORIZONTAL)
                .shipSize(2)
                .build();
        when(controllerV2Api.addShipToField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq("ship-1"),
                eq(Coordinate.of(2, 3)),
                eq("HORIZONTAL"))).thenReturn(ship);

        var body = ParamShipDto.builder()
                .row(2)
                .col(3)
                .direction("HORIZONTAL")
                .build();

        mockMvc.perform(put("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}",
                        SESSION_ID,
                        PLAYER_ID,
                        "ship-1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipId").value("ship-1"));
    }

    @Test
    void addShipToField_invalidShipId_returns400WithShipIdInvalidErrorCode() throws Exception {
        when(controllerV2Api.addShipToField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq("unknown-ship"),
                any(),
                any())).thenThrow(new GameShipIdIsNotCorrectException(
                "ship not found"));

        var body = ParamShipDto.builder()
                .row(0)
                .col(0)
                .direction("HORIZONTAL")
                .build();

        mockMvc.perform(put("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}",
                        SESSION_ID,
                        PLAYER_ID,
                        "unknown-ship").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHIP_ID_INVALID"));
    }

    @Test
    void addShipToField_shipAlreadyPlaced_returns400WithShipAlreadyPlacedErrorCode() throws Exception {
        when(controllerV2Api.addShipToField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq("ship-1"),
                any(),
                any())).thenThrow(new GameShipAlreadyPlacedException(
                "ship already placed"));

        var body = ParamShipDto.builder()
                .row(0)
                .col(0)
                .direction("HORIZONTAL")
                .build();

        mockMvc.perform(put("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}",
                        SESSION_ID,
                        PLAYER_ID,
                        "ship-1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHIP_ALREADY_PLACED"));
    }

    @Test
    void addShipToField_wrongStage_returns400WithStageInvalidErrorCode() throws Exception {
        when(controllerV2Api.addShipToField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq("ship-1"),
                any(),
                any())).thenThrow(new GameStageIsNotCorrectException("wrong stage"));

        var body = ParamShipDto.builder()
                .row(0)
                .col(0)
                .direction("HORIZONTAL")
                .build();

        mockMvc.perform(put("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}",
                        SESSION_ID,
                        PLAYER_ID,
                        "ship-1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAGE_INVALID"));
    }

    @Test
    void addShipToField_invalidDirection_returns400WithShipDirectionInvalidErrorCode() throws Exception {
        when(controllerV2Api.addShipToField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq("ship-1"),
                any(),
                eq("SIDEWAYS"))).thenThrow(new GameShipDirectionIsNotCorrectException(
                "bad direction"));

        var body = ParamShipDto.builder()
                .row(0)
                .col(0)
                .direction("SIDEWAYS")
                .build();

        mockMvc.perform(put("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}",
                        SESSION_ID,
                        PLAYER_ID,
                        "ship-1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHIP_DIRECTION_INVALID"));
    }

    // ---- DELETE /api/v2/game/sessions/{sessionId}/players/{playerId}/ships ----

    @Test
    void removeShipFromField_shipRemoved_returnsDeletedTrue() throws Exception {
        when(controllerV2Api.removeShipFromField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq(Coordinate.of(1, 1)))).thenReturn("ship-1");

        var body = ParamCoordinateDto.builder()
                .row(1)
                .col(1)
                .build();

        mockMvc.perform(delete("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
    }

    /**
     * {@code ResponseShipRemovedDto.fromString} edge case: a blank ship id (the controller API's
     * {@code Optional.orElse("")} fallback when no ship occupied the given coordinate) must map to
     * {@code deleted=false}, not throw or map to {@code true}.
     */
    @Test
    void removeShipFromField_noShipAtCoordinate_returnsDeletedFalse() throws Exception {
        when(controllerV2Api.removeShipFromField(eq(SESSION_ID),
                eq(PLAYER_ID),
                eq(Coordinate.of(5, 5)))).thenReturn("");

        var body = ParamCoordinateDto.builder()
                .row(5)
                .col(5)
                .build();

        mockMvc.perform(delete("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void removeShipFromField_invalidCoordinate_returns400WithCoordinateInvalidErrorCode() throws Exception {
        when(controllerV2Api.removeShipFromField(eq(SESSION_ID),
                eq(PLAYER_ID),
                any())).thenThrow(new ua.kostenko.battleship.battleship.logic.api.exceptions.GameCoordinateIsNotCorrectIncorrectException(
                "bad coordinate"));

        var body = ParamCoordinateDto.builder()
                .row(-1)
                .col(-1)
                .build();

        mockMvc.perform(delete("/api/v2/game/sessions/{sessionId}/players/{playerId}/ships",
                        SESSION_ID,
                        PLAYER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COORDINATE_INVALID"));
    }

    // ---- GET /api/v2/game/sessions/{sessionId}/players/{playerId}/opponent ----

    @Test
    void getOpponentInformation_returnsOpponentNameAndReadyFlag() throws Exception {
        when(controllerV2Api.getOpponentInformation(SESSION_ID, PLAYER_ID)).thenReturn(new OpponentInfo("Bob", true));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/opponent",
                        SESSION_ID,
                        PLAYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Bob"))
                .andExpect(jsonPath("$.ready").value(true));
    }

    @Test
    void getOpponentInformation_sessionNotFound_returns400WithSessionNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getOpponentInformation("unknown-session",
                PLAYER_ID)).thenThrow(new ua.kostenko.battleship.battleship.logic.api.exceptions.GameSessionIdIsNotCorrectException(
                "unknown session"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/opponent",
                        "unknown-session",
                        PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    /**
     * Regression test for the confirmed production bug: a solo player on the Wait screen polling this
     * endpoint every 3s before a second player has joined must now surface as a clean, structured HTTP 400
     * with {@code errorCode == "OPPONENT_NOT_FOUND"} — not an unhandled {@code IllegalArgumentException}
     * reaching the servlet container's default (unstructured) error response.
     */
    @Test
    void getOpponentInformation_soloPlayerPolling_returns400WithOpponentNotFoundErrorCode() throws Exception {
        when(controllerV2Api.getOpponentInformation(SESSION_ID, PLAYER_ID)).thenThrow(new GameOpponentNotFoundException(
                "Player with provided filter is not found"));

        mockMvc.perform(get("/api/v2/game/sessions/{sessionId}/players/{playerId}/opponent", SESSION_ID, PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("OPPONENT_NOT_FOUND"));
    }

    // ---- POST /api/v2/game/sessions/{sessionId}/players/{playerId}/start ----

    @Test
    void startGame_playerReady_returnsReadyTrue() throws Exception {
        var readyPlayer = Player.builder()
                .playerId(PLAYER_ID)
                .playerName("Alice")
                .fieldManagement(mock(ua.kostenko.battleship.battleship.logic.engine.FieldManagement.class))
                .shipsNotOnTheField(Set.of())
                .allPlayerShips(Set.of())
                .isReady(true)
                .build();
        when(controllerV2Api.startGame(SESSION_ID, PLAYER_ID)).thenReturn(readyPlayer);

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/start", SESSION_ID, PLAYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true));
    }

    @Test
    void startGame_wrongStage_returns400WithStageInvalidErrorCode() throws Exception {
        when(controllerV2Api.startGame(SESSION_ID, PLAYER_ID)).thenThrow(new GameStageIsNotCorrectException(
                "wrong stage"));

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/start", SESSION_ID, PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAGE_INVALID"));
    }

    @Test
    void startGame_shipsNotAllPlaced_returns400WithShipsNotAllPlacedErrorCode() throws Exception {
        when(controllerV2Api.startGame(SESSION_ID, PLAYER_ID)).thenThrow(new GameShipsNotAllPlacedException(
                "ships not all placed"));

        mockMvc.perform(post("/api/v2/game/sessions/{sessionId}/players/{playerId}/start", SESSION_ID, PLAYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHIPS_NOT_ALL_PLACED"));
    }

    private Cell[][] emptyField() {
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
        return field;
    }
}
