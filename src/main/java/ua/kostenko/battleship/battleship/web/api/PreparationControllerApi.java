package ua.kostenko.battleship.battleship.web.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.preparation.*;

/**
 * API interface for the preparation phase operations in the Battleship game.
 * <p>
 * The PreparationControllerApi interface defines the operations for managing the preparation phase, including adding and removing ships, and retrieving opponent information.
 * </p>
 *
 * @see ResponsePreparationState
 * @see ResponseShipAddedDto
 * @see ResponseShipRemovedDto
 * @see ParamCoordinateDto
 * @see ResponseOpponentInformationDto
 * @see ResponsePlayerReady
 */
public interface PreparationControllerApi {

    /**
     * Retrieves the preparation state for a specific player.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponsePreparationState
     */
    ResponseEntity<ResponsePreparationState> getPreparationState(String sessionId, String playerId);

    /**
     * Adds a ship to the player's field.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @param shipId    the unique identifier of the ship
     * @param shipDto   the ParamShipDto containing the ship's placement details
     * @return a ResponseEntity containing the ResponseShipAddedDto
     */
    ResponseEntity<ResponseShipAddedDto> addShipToField(
            String sessionId, String playerId, String shipId, ParamShipDto shipDto);

    /**
     * Removes a ship from the player's field based on the provided coordinates.
     *
     * @param sessionId     the unique identifier of the game session
     * @param playerId      the unique identifier of the player
     * @param coordinateDto the ParamCoordinateDto containing the coordinates for removal
     * @return a ResponseEntity containing the ResponseShipRemovedDto
     */
    ResponseEntity<ResponseShipRemovedDto> removeShipFromField(
            String sessionId, String playerId, ParamCoordinateDto coordinateDto);

    /**
     * Retrieves information about the opponent.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponseOpponentInformationDto
     */
    ResponseEntity<ResponseOpponentInformationDto> getOpponentInformation(String sessionId, String playerId);

    /**
     * Indicates the player is ready to start the game.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponsePlayerReady
     */
    ResponseEntity<ResponsePlayerReady> startGame(String sessionId, String playerId);
}
