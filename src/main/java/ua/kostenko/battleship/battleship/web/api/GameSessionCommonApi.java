package ua.kostenko.battleship.battleship.web.api;

import org.springframework.http.ResponseEntity;
import ua.kostenko.battleship.battleship.web.api.dtos.session.*;

/**
 * API interface for common game session operations in the Battleship game.
 * <p>
 * The GameSessionCommonApi interface defines the operations for managing game sessions, including creating sessions, players, and retrieving game stages.
 * </p>
 *
 * @see ResponseAvailableGameEditionsDto
 * @see ResponseCreatedSessionIdDto
 * @see ParamGameEditionDto
 * @see ResponseCreatedPlayerDto
 * @see ParamPlayerNameDto
 * @see ResponseCurrentGameStageDto
 * @see ResponseLastSessionChangeTimeDto
 */
public interface GameSessionCommonApi {

    /**
     * Retrieves the available game editions.
     *
     * @return a ResponseEntity containing the ResponseAvailableGameEditionsDto
     */
    ResponseEntity<ResponseAvailableGameEditionsDto> getAvailableGameEditions();

    /**
     * Creates a new game session with the specified game edition.
     *
     * @param gameEdition the ParamGameEditionDto containing the chosen game edition
     * @return a ResponseEntity containing the ResponseCreatedSessionIdDto
     */
    ResponseEntity<ResponseCreatedSessionIdDto> createGameSession(ParamGameEditionDto gameEdition);

    /**
     * Creates a new player in the specified game session.
     *
     * @param sessionId     the unique identifier of the game session
     * @param playerNameDto the ParamPlayerNameDto containing the player's name
     * @return a ResponseEntity containing the ResponseCreatedPlayerDto
     */
    ResponseEntity<ResponseCreatedPlayerDto> createPlayerInSession(String sessionId, ParamPlayerNameDto playerNameDto);

    /**
     * Retrieves the current game stage for the specified session.
     *
     * @param sessionId the unique identifier of the game session
     * @return a ResponseEntity containing the ResponseCurrentGameStageDto
     */
    ResponseEntity<ResponseCurrentGameStageDto> getCurrentGameStage(String sessionId);

    /**
     * Retrieves the last session change time for the specified session.
     *
     * @param sessionId the unique identifier of the game session
     * @return a ResponseEntity containing the ResponseLastSessionChangeTimeDto
     */
    ResponseEntity<ResponseLastSessionChangeTimeDto> getLastSessionChangeTime(String sessionId);
}
