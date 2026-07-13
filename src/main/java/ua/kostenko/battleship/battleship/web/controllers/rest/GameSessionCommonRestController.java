package ua.kostenko.battleship.battleship.web.controllers.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.web.api.GameSessionCommonApi;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;
import ua.kostenko.battleship.battleship.web.api.dtos.session.*;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * REST controller for managing game sessions in the Battleship game.
 * <p>
 * The GameSessionCommonRestController class handles API requests related to game session management, including creating sessions,
 * creating players, and retrieving game stages.
 * </p>
 *
 * @see GameSessionCommonApi
 * @see GameControllerApi
 * @see ResponseAvailableGameEditionsDto
 * @see ResponseCreatedSessionIdDto
 * @see ParamGameEditionDto
 * @see ResponseCreatedPlayerDto
 * @see ParamPlayerNameDto
 * @see ResponseCurrentGameStageDto
 * @see ResponseLastSessionChangeTimeDto
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game")
@Tag(name = "Game Session", description = "Game session lifecycle: creating sessions and players, and retrieving session stage and change time")
public class GameSessionCommonRestController implements GameSessionCommonApi {

    private final GameControllerApi controllerV2Api;

    /**
     * Retrieves the available game editions.
     *
     * @return a ResponseEntity containing the ResponseAvailableGameEditionsDto
     */
    @Operation(
            summary = "List available game editions",
            description = "Returns the fixed set of game editions the server supports (rule sets for ship counts/board size).")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Available editions",
                    content = @Content(schema = @Schema(implementation = ResponseAvailableGameEditionsDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "gameEditions": ["UKRAINIAN", "MILTON_BRADLEY"] }
                                    """)))
    })
    @GetMapping("/editions")
    @Override
    public ResponseEntity<ResponseAvailableGameEditionsDto> getAvailableGameEditions() {
        val editions = controllerV2Api.getAvailableGameEditions();

        val response = ResponseAvailableGameEditionsDto.builder()
                .gameEditions(editions.stream()
                        .map(GameEdition::name)
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList()))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new game session with the specified game edition.
     *
     * @param gameEdition the ParamGameEditionDto containing the chosen game edition
     * @return a ResponseEntity containing the ResponseCreatedSessionIdDto
     */
    @Operation(
            summary = "Create a game session",
            description = "Creates a new session for the given game edition. Fails if the edition name is blank or not a recognized GameEdition.")
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Session created",
                    content = @Content(schema = @Schema(implementation = ResponseCreatedSessionIdDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "sessionId": "abc123" }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid or unrecognized game edition",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "editionInvalid", value = """
                                    { "status": 400, "errorMessage": "...", "errorCode": "EDITION_INVALID" }
                                    """)))
    })
    @PostMapping("/sessions")
    @Override
    public ResponseEntity<ResponseCreatedSessionIdDto> createGameSession(
            @RequestBody final ParamGameEditionDto gameEdition) {
        val gameSessionId = controllerV2Api.createGameSession(gameEdition.getGameEdition());

        val response = ResponseCreatedSessionIdDto.builder()
                .sessionId(gameSessionId)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Creates a new player in the specified game session.
     *
     * @param sessionId     the unique identifier of the game session
     * @param playerNameDto the ParamPlayerNameDto containing the player's name
     * @return a ResponseEntity containing the ResponseCreatedPlayerDto
     */
    @Operation(
            summary = "Create a player in a session",
            description = "Registers a new player under the given session. Fails if the player name is blank, the session id is unknown, the session already has 2 players, or the session is not in a stage that allows joining.")
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Player created",
                    content = @Content(schema = @Schema(implementation = ResponseCreatedPlayerDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "playerId": "p1", "playerName": "Alice" }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid player name, unknown session, session full, or wrong stage",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = {
                                    @ExampleObject(name = "playerNameInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_NAME_INVALID" }
                                            """),
                                    @ExampleObject(name = "sessionNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "sessionFull", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_FULL" }
                                            """),
                                    @ExampleObject(name = "stageInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "STAGE_INVALID" }
                                            """)
                            })),
            @ApiResponse(responseCode = "500",
                    description = "Unexpected internal error",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "internal", value = """
                                    { "status": 500, "errorMessage": "...", "errorCode": "INTERNAL" }
                                    """)))
    })
    @PostMapping(value = "sessions/{sessionId}/players")
    @Override
    public ResponseEntity<ResponseCreatedPlayerDto> createPlayerInSession(
            @Parameter(name = "sessionId", description = "Session identifier returned by session creation", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @RequestBody final ParamPlayerNameDto playerNameDto) {
        val createdPlayer = controllerV2Api.createPlayerInSession(sessionId, playerNameDto.getPlayerName());

        var response = ResponseCreatedPlayerDto.builder()
                .playerName(createdPlayer.getPlayerName())
                .playerId(createdPlayer.getPlayerId())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Retrieves the current game stage for the specified session.
     *
     * @param sessionId the unique identifier of the game session
     * @return a ResponseEntity containing the ResponseCurrentGameStageDto
     */
    @Operation(
            summary = "Get current game stage",
            description = "Returns the session's current GameStage. Fails if the session id is unknown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Current stage",
                    content = @Content(schema = @Schema(implementation = ResponseCurrentGameStageDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "gameStage": "PREPARATION" }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Unknown session id",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "sessionNotFound", value = """
                                    { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                    """)))
    })
    @GetMapping(value = "sessions/{sessionId}/state")
    @Override
    public ResponseEntity<ResponseCurrentGameStageDto> getCurrentGameStage(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId) {
        val gameStage = controllerV2Api.getCurrentGameStage(sessionId);

        val response = ResponseCurrentGameStageDto.builder()
                .gameStage(gameStage.name())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the last session change time for the specified session.
     *
     * @param sessionId the unique identifier of the game session
     * @return a ResponseEntity containing the ResponseLastSessionChangeTimeDto
     */
    @Operation(
            summary = "Get last session change timestamp",
            description = "Returns the timestamp of the session's last state change, used by clients for change-detection polling. Fails if the session id is unknown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Last change timestamp",
                    content = @Content(schema = @Schema(implementation = ResponseLastSessionChangeTimeDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "lastId": "2026-07-13T10:15:30" }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Unknown session id",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "sessionNotFound", value = """
                                    { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                    """)))
    })
    @GetMapping(value = "sessions/{sessionId}/changesTime")
    @Override
    public ResponseEntity<ResponseLastSessionChangeTimeDto> getLastSessionChangeTime(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId) {
        val localDateTimeValueString = controllerV2Api.getLastSessionChangeTime(sessionId);

        val response = ResponseLastSessionChangeTimeDto.builder()
                .lastId(localDateTimeValueString)
                .build();

        return ResponseEntity.ok(response);
    }
}
