package ua.kostenko.battleship.battleship.logic.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.utils.CoordinateUtils;

/**
 * Utility class for handling validation operations in the Battleship game.
 * <p>
 * The ValidationUtils class provides methods for validating various game-related entities, such as coordinates, player IDs, session IDs, etc.
 * </p>
 *
 * @see Coordinate
 * @see ShipDirection
 * @see GameEdition
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationUtils {

    /**
     * Validates if a coordinate is correct.
     *
     * @param coordinate the coordinate to validate
     * @throws GameCoordinateIsNotCorrectIncorrectException if the coordinate is not valid
     */
    public static void validateCoordinate(Coordinate coordinate) {
        log.debug("Validating coordinate: {}", coordinate);
        if (!CoordinateUtils.isCorrectCoordinate(coordinate)) {
            throw new GameCoordinateIsNotCorrectIncorrectException("Coordinate: %s is not VALID!".formatted(coordinate));
        }
    }

    /**
     * Validates if the player ID is correct.
     *
     * @param playerId the player ID to validate
     * @throws GamePlayerIdIsNotCorrectException if the player ID is not valid
     */
    public static void validatePlayerId(String playerId) {
        log.debug("Validating player ID: {}", playerId);
        if (StringUtils.isBlank(playerId)) {
            throw new GamePlayerIdIsNotCorrectException("Player ID: %s is not VALID!".formatted(playerId));
        }
    }

    /**
     * Validates if the player name is correct.
     *
     * @param playerName the player name to validate
     * @throws GamePlayerNameIsNotCorrectException if the player name is not valid
     */
    public static void validatePlayerName(String playerName) {
        log.debug("Validating player name: {}", playerName);
        if (StringUtils.isBlank(playerName)) {
            throw new GamePlayerNameIsNotCorrectException("Player Name: %s is not VALID!".formatted(playerName));
        }
    }

    /**
     * Validates if the session ID is correct.
     *
     * @param sessionId the session ID to validate
     * @throws GameSessionIdIsNotCorrectException if the session ID is not valid
     */
    public static void validateSessionId(String sessionId) {
        log.debug("Validating session ID: {}", sessionId);
        if (StringUtils.isBlank(sessionId)) {
            throw new GameSessionIdIsNotCorrectException("Session ID: %s is not VALID!".formatted(sessionId));
        }
    }

    /**
     * Validates if the ship direction is correct.
     *
     * @param shipDirection the ship direction to validate
     * @throws GameShipDirectionIsNotCorrectException if the ship direction is not valid
     */
    public static void validateShipDirection(String shipDirection) {
        log.debug("Validating ship direction: {}", shipDirection);
        if (StringUtils.isBlank(shipDirection)) {
            throw new GameShipDirectionIsNotCorrectException("Ship Direction: %s is not VALID!".formatted(shipDirection));
        }
        try {
            ShipDirection.valueOf(shipDirection);
        } catch (Exception _) {
            throw new GameShipDirectionIsNotCorrectException("Ship Direction: %s is not VALID!".formatted(shipDirection));
        }
    }

    /**
     * Validates if the ship ID is correct.
     *
     * @param shipId the ship ID to validate
     * @throws GameShipIdIsNotCorrectException if the ship ID is not valid
     */
    public static void validateShipId(String shipId) {
        log.debug("Validating ship ID: {}", shipId);
        if (StringUtils.isBlank(shipId)) {
            throw new GameShipIdIsNotCorrectException("Ship ID: %s is not VALID!".formatted(shipId));
        }
    }

    /**
     * Validates if the game edition is correct.
     *
     * @param gameEdition the game edition to validate
     * @throws GameEditionIsNotCorrectException if the game edition is not valid
     */
    public static void validateGameEdition(String gameEdition) {
        log.debug("Validating game edition: {}", gameEdition);
        if (StringUtils.isBlank(gameEdition)) {
            throw new GameEditionIsNotCorrectException("Game Edition: %s is not VALID!".formatted(gameEdition));
        }
        try {
            GameEdition.valueOf(gameEdition);
        } catch (Exception _) {
            throw new GameEditionIsNotCorrectException("Game Edition: %s is not VALID!".formatted(gameEdition));
        }
    }
}
