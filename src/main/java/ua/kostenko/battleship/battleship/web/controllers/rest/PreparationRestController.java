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
import ua.kostenko.battleship.battleship.web.api.ControllerUtils;
import ua.kostenko.battleship.battleship.web.api.PreparationControllerApi;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.entities.ShipDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.*;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * REST controller for managing the preparation phase in the Battleship game.
 * <p>
 * The PreparationRestController class handles API requests related to the preparation state, including adding and removing ships,
 * and retrieving opponent information.
 * </p>
 *
 * @see PreparationControllerApi
 * @see GameControllerApi
 * @see ControllerUtils
 * @see ResponsePreparationState
 * @see ResponseShipAddedDto
 * @see ResponseShipRemovedDto
 * @see ResponseOpponentInformationDto
 * @see ResponsePlayerReady
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/sessions/{sessionId}")
@Tag(name = "Preparation", description = "Ship placement/removal, opponent info, and readiness during the PREPARATION stage")
public class PreparationRestController implements PreparationControllerApi {

    private final GameControllerApi controllerV2Api;

    /**
     * Retrieves the preparation state for a specific player.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponsePreparationState
     */
    @Operation(
            summary = "Get preparation state",
            description = "Returns the ships still available to place and the player's current field. Fails if the player id is malformed, the session is unknown, or the player can't be resolved in this session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Current preparation state",
                    content = @Content(schema = @Schema(implementation = ResponsePreparationState.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid player id, unknown session, or player not found",
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
                                            """)
                            })),
            @ApiResponse(responseCode = "500",
                    description = "Unexpected internal error",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "internal", value = """
                                    { "status": 500, "errorMessage": "...", "errorCode": "INTERNAL" }
                                    """)))
    })
    @GetMapping(value = "players/{playerId}/preparationState")
    @Override
    public ResponseEntity<ResponsePreparationState> getPreparationState(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Player identifier", required = true, example = "p1")
            @PathVariable final String playerId) {
        val ships = controllerV2Api.getShipsNotOnTheBoard(sessionId, playerId);
        val field = controllerV2Api.getPreparationField(sessionId, playerId);

        val respShips = ships.stream()
                .map(ShipDto::of)
                .sorted(Comparator.comparing(ShipDto::getShipSize))
                .collect(Collectors.toList());
        val respField = ControllerUtils.mapFieldToFieldDto(field);

        val resp = ResponsePreparationState.builder()
                .ships(respShips)
                .field(respField)
                .build();

        return ResponseEntity.ok(resp);
    }

    /**
     * Adds a ship to the player's field.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @param shipId    the unique identifier of the ship
     * @param shipDto   the ParamShipDto containing the ship's placement details
     * @return a ResponseEntity containing the ResponseShipAddedDto
     */
    @Operation(
            summary = "Place a ship on the board",
            description = "Registers a ship's coordinates and orientation for the given player during PREPARATION. Fails if the stage isn't PREPARATION, the ship id/direction/coordinate is invalid, the ship is already placed, or the placement overlaps another ship or leaves the board.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Ship placed",
                    content = @Content(schema = @Schema(implementation = ResponseShipAddedDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "shipId": "patrol_boat_1" }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid ship id/direction/coordinate, wrong stage, or ship already placed / overlaps",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = {
                                    @ExampleObject(name = "playerIdInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_ID_INVALID" }
                                            """),
                                    @ExampleObject(name = "shipIdInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SHIP_ID_INVALID" }
                                            """),
                                    @ExampleObject(name = "shipDirectionInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SHIP_DIRECTION_INVALID" }
                                            """),
                                    @ExampleObject(name = "coordinateInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "COORDINATE_INVALID" }
                                            """),
                                    @ExampleObject(name = "sessionNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "stageInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "STAGE_INVALID" }
                                            """),
                                    @ExampleObject(name = "shipAlreadyPlaced", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SHIP_ALREADY_PLACED" }
                                            """)
                            })),
            @ApiResponse(responseCode = "500",
                    description = "Unexpected internal error (e.g. intersection resolution failure)",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "internal", value = """
                                    { "status": 500, "errorMessage": "...", "errorCode": "INTERNAL" }
                                    """)))
    })
    @PutMapping(value = "players/{playerId}/ships/{shipId}")
    @Override
    public ResponseEntity<ResponseShipAddedDto> addShipToField(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Player identifier", required = true, example = "p1")
            @PathVariable final String playerId,
            @Parameter(name = "shipId", description = "Ship type identifier from the edition's ship catalog", required = true, example = "patrol_boat_1")
            @PathVariable final String shipId,
            @RequestBody final ParamShipDto shipDto) {
        val coordinate = ParamShipDto.getCoordinateFrom(shipDto);
        val direction = shipDto.getDirection();

        val addedShip = controllerV2Api.addShipToField(sessionId, playerId, shipId, coordinate, direction);

        val response = ResponseShipAddedDto.fromId(addedShip.shipId());

        return ResponseEntity.ok(response);
    }

    /**
     * Removes a ship from the player's field based on the provided coordinates.
     *
     * @param sessionId     the unique identifier of the game session
     * @param playerId      the unique identifier of the player
     * @param coordinateDto the ParamCoordinateDto containing the coordinates for removal
     * @return a ResponseEntity containing the ResponseShipRemovedDto
     */
    @Operation(
            summary = "Remove a placed ship",
            description = "Removes whatever ship occupies the given coordinate for this player during PREPARATION. Fails if the player id/coordinate is invalid, the session is unknown, or the stage isn't PREPARATION.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Removal result",
                    content = @Content(schema = @Schema(implementation = ResponseShipRemovedDto.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "deleted": true }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid input, unknown session, or wrong stage",
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
    @DeleteMapping(value = "players/{playerId}/ships")
    @Override
    public ResponseEntity<ResponseShipRemovedDto> removeShipFromField(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Player identifier", required = true, example = "p1")
            @PathVariable final String playerId,
            @RequestBody final ParamCoordinateDto coordinateDto) {
        val idOfRemovedShip = controllerV2Api.removeShipFromField(sessionId,
                playerId,
                ParamCoordinateDto.getCoordinate(coordinateDto));

        val response = ResponseShipRemovedDto.fromString(idOfRemovedShip);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves information about the opponent.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponseOpponentInformationDto
     */
    @Operation(
            summary = "Get opponent information",
            description = "Returns the opponent's display name and whether they've readied up. Fails if the player id is invalid, the session is unknown, or no opponent has joined yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Opponent info",
                    content = @Content(schema = @Schema(implementation = ResponseOpponentInformationDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid player id, unknown session, or no opponent yet",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = {
                                    @ExampleObject(name = "playerIdInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_ID_INVALID" }
                                            """),
                                    @ExampleObject(name = "sessionNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "opponentNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "OPPONENT_NOT_FOUND" }
                                            """)
                            }))
    })
    @GetMapping(value = "players/{playerId}/opponent")
    @Override
    public ResponseEntity<ResponseOpponentInformationDto> getOpponentInformation(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Requesting player's identifier", required = true, example = "p1")
            @PathVariable final String playerId) {
        val opponentInfo = controllerV2Api.getOpponentInformation(sessionId, playerId);

        val response = ResponseOpponentInformationDto.from(opponentInfo);

        return ResponseEntity.ok(response);
    }

    /**
     * Indicates the player is ready to start the game.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponsePlayerReady
     */
    @Operation(
            summary = "Mark player ready to start",
            description = "Transitions the player to ready once all ships are placed. Fails if the player id is invalid, the session is unknown, the stage isn't PREPARATION, or the player still has unplaced ships.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Player marked ready",
                    content = @Content(schema = @Schema(implementation = ResponsePlayerReady.class),
                            examples = @ExampleObject(name = "success", value = """
                                    { "ready": true }
                                    """))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid player id, unknown session, wrong stage, or ships not all placed",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = {
                                    @ExampleObject(name = "playerIdInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "PLAYER_ID_INVALID" }
                                            """),
                                    @ExampleObject(name = "sessionNotFound", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SESSION_NOT_FOUND" }
                                            """),
                                    @ExampleObject(name = "stageInvalid", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "STAGE_INVALID" }
                                            """),
                                    @ExampleObject(name = "shipsNotAllPlaced", value = """
                                            { "status": 400, "errorMessage": "...", "errorCode": "SHIPS_NOT_ALL_PLACED" }
                                            """)
                            })),
            @ApiResponse(responseCode = "500",
                    description = "Unexpected internal error",
                    content = @Content(schema = @Schema(implementation = ExceptionDto.class),
                            examples = @ExampleObject(name = "internal", value = """
                                    { "status": 500, "errorMessage": "...", "errorCode": "INTERNAL" }
                                    """)))
    })
    @PostMapping(value = "players/{playerId}/start")
    @Override
    public ResponseEntity<ResponsePlayerReady> startGame(
            @Parameter(name = "sessionId", description = "Session identifier", required = true, example = "abc123")
            @PathVariable final String sessionId,
            @Parameter(name = "playerId", description = "Player identifier", required = true, example = "p1")
            @PathVariable final String playerId) {
        val player = controllerV2Api.startGame(sessionId, playerId);

        val response = new ResponsePlayerReady(player.isReady());

        return ResponseEntity.ok(response);
    }
}
