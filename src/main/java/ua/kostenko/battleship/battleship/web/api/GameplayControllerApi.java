package ua.kostenko.battleship.battleship.web.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.api.dtos.ParamCoordinateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseGameplayStateDto;
import ua.kostenko.battleship.battleship.web.api.dtos.gameplay.ResponseShotResultDto;

/**
 * API interface for the gameplay operations in the Battleship game.
 * <p>
 * The GameplayControllerApi interface defines the operations for retrieving the game state and making shots during gameplay.
 * </p>
 *
 * @see ResponseGameplayStateDto
 * @see ResponseShotResultDto
 * @see ParamCoordinateDto
 */
public interface GameplayControllerApi {

    /**
     * Retrieves the game state for a specific player.
     *
     * @param sessionId the unique identifier of the game session
     * @param playerId  the unique identifier of the player
     * @return a ResponseEntity containing the ResponseGameplayStateDto
     */
    ResponseEntity<ResponseGameplayStateDto> getGameStateForPlayer(String sessionId, String playerId);

    /**
     * Makes a shot on the field based on the provided coordinates.
     *
     * @param sessionId  the unique identifier of the game session
     * @param playerId   the unique identifier of the player
     * @param coordinate the coordinates where the shot is made
     * @return a ResponseEntity containing the ResponseShotResultDto
     */
    ResponseEntity<ResponseShotResultDto> makeShotByField(
            String sessionId, String playerId, ParamCoordinateDto coordinate);
}
