package ua.kostenko.battleship.battleship.web.controllers.rest;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.web.api.ControllerUtils;
import ua.kostenko.battleship.battleship.web.api.PreparationControllerApi;
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
public class PreparationRestController implements PreparationControllerApi {

    private final GameControllerApi controllerV2Api;

    /**
     * Retrieves the preparation state for a specific player.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponsePreparationState
     */
    @GetMapping(value = "players/{playerId}/preparationState")
    @Override
    public ResponseEntity<ResponsePreparationState> getPreparationState(
            @PathVariable final String sessionId, @PathVariable final String playerId) {
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
    @PutMapping(value = "players/{playerId}/ships/{shipId}")
    @Override
    public ResponseEntity<ResponseShipAddedDto> addShipToField(
            @PathVariable final String sessionId, @PathVariable final String playerId,
            @PathVariable final String shipId, @RequestBody final ParamShipDto shipDto) {
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
    @DeleteMapping(value = "players/{playerId}/ships")
    @Override
    public ResponseEntity<ResponseShipRemovedDto> removeShipFromField(
            @PathVariable final String sessionId, @PathVariable final String playerId,
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
    @GetMapping(value = "players/{playerId}/opponent")
    @Override
    public ResponseEntity<ResponseOpponentInformationDto> getOpponentInformation(
            @PathVariable final String sessionId, @PathVariable final String playerId) {
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
    @PostMapping(value = "players/{playerId}/start")
    @Override
    public ResponseEntity<ResponsePlayerReady> startGame(
            @PathVariable final String sessionId, @PathVariable final String playerId) {
        val player = controllerV2Api.startGame(sessionId, playerId);

        val response = new ResponsePlayerReady(player.isReady());

        return ResponseEntity.ok(response);
    }
}
