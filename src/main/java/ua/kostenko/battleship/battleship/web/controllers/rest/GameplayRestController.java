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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.web.api.GameplayControllerApi;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseShotResultDto;

/**
 * REST controller for managing gameplay operations in the Battleship game.
 * <p>
 * The GameplayRestController class handles API requests related to gameplay, including retrieving game state and making shots.
 * </p>
 *
 * @see GameplayControllerApi
 * @see GameControllerApi
 * @see ResponseGameplayStateDto
 * @see ResponseShotResultDto
 * @see ParamCoordinateDto
 * @see Coordinate
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/sessions/{sessionId}")
@Tag(name = "Gameplay", description = "In-game state retrieval and shot resolution during the IN_GAME stage")
public class GameplayRestController implements GameplayControllerApi {

    private final GameControllerApi controllerV2Api;

    /**
     * Retrieves the game state for a specific player.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponseGameplayStateDto
     */
    @Operation(
            summary = "Get gameplay state for a player",
            description = "Returns the requesting player's and opponent's boards, alive-cell/ship counts, and win state. Fails if the player id is malformed, the session is unknown, or the player/opponent can't be resolved in this session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Gameplay state for the requesting player",
                    content = @Content(schema = @Schema(implementation = ResponseGameplayStateDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid player id, unknown session, or player/opponent not found",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = {
                                    @ExampleObject(name = "playerIdInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_ID_INVALID" }
                                            """),
                                    @ExampleObject(name = "sessionNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "playerNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "opponentNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "OPPONENT_NOT_FOUND" }
                                            """)
                            }))
    })
    @GetMapping(value = "players/{playerId}/state")
    @Override
    public ResponseEntity<ResponseGameplayStateDto> getGameStateForPlayer(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Requesting player's identifier", required = true, example = "p1")
            @PathVariable final String playerId) {
        val gameplayState = controllerV2Api.getGameState(sessionId, playerId);

        val response = ResponseGameplayStateDto.from(gameplayState);

        return ResponseEntity.ok(response);
    }

    /**
     * Makes a shot on the field based on the provided coordinates.
     *
     * @param sessionId  the unique identifier of the game session
     * @param playerId   the unique identifier of the player
     * @param coordinate the coordinates where the shot is made
     * @return a ResponseEntity containing the ResponseShotResultDto
     */
    @Operation(
            summary = "Make a shot",
            description = "Resolves a shot at the given coordinate on the acting player's opponent field. Fails if the player id or coordinate is malformed, the session is unknown, it isn't this player's turn, the cell was already shot, or the session isn't in the IN_GAME stage.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Shot resolved",
                    content = @Content(schema = @Schema(implementation = ResponseShotResultDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "shotResult": "HIT" }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid input, unknown session, not this player's turn, cell already shot, or wrong stage",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = {
                                    @ExampleObject(name = "playerIdInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_ID_INVALID" }
                                            """),
                                    @ExampleObject(name = "coordinateInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "COORDINATE_INVALID" }
                                            """),
                                    @ExampleObject(name = "sessionNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "playerNotActive", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_NOT_ACTIVE" }
                                            """),
                                    @ExampleObject(name = "cellAlreadyShot", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "CELL_ALREADY_SHOT" }
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
    @PostMapping(value = "players/{playerId}/field/shot")
    @Override
    public ResponseEntity<ResponseShotResultDto> makeShotByField(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Shooting player's identifier", required = true, example = "p1")
            @PathVariable final String playerId,
            @RequestBody final ParamCoordinateDto coordinate) {
        val shotResult = controllerV2Api.makeShotByField(sessionId,
                playerId,
                Coordinate.of(coordinate.getRow(), coordinate.getCol()));

        val response = new ResponseShotResultDto(shotResult.name());

        return ResponseEntity.ok(response);
    }
}
