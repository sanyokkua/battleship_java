package ua.kostenko.battleship.battleship.web.controllers.rest;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kostenko.battleship.battleship.logic.api.GameControllerApi;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.web.api.GameSessionCommonApi;
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
public class GameSessionCommonRestController implements GameSessionCommonApi {

    private final GameControllerApi controllerV2Api;

    /**
     * Retrieves the available game editions.
     *
     * @return a ResponseEntity containing the ResponseAvailableGameEditionsDto
     */
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
    @PostMapping(value = "sessions/{sessionId}/players")
    @Override
    public ResponseEntity<ResponseCreatedPlayerDto> createPlayerInSession(
            @PathVariable final String sessionId, @RequestBody final ParamPlayerNameDto playerNameDto) {
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
    @GetMapping(value = "sessions/{sessionId}/state")
    @Override
    public ResponseEntity<ResponseCurrentGameStageDto> getCurrentGameStage(@PathVariable final String sessionId) {
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
    @GetMapping(value = "sessions/{sessionId}/changesTime")
    @Override
    public ResponseEntity<ResponseLastSessionChangeTimeDto> getLastSessionChangeTime(
            @PathVariable final String sessionId) {
        val localDateTimeValueString = controllerV2Api.getLastSessionChangeTime(sessionId);

        val response = ResponseLastSessionChangeTimeDto.builder()
                .lastId(localDateTimeValueString)
                .build();

        return ResponseEntity.ok(response);
    }
}
