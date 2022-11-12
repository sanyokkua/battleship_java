package ua.kostenko.battleship.battleship.logic.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.utils.CoordinateUtils;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationUtils {

    private static final String DEBUG_TEMPLATE_MESSAGE = "Validations of: {}";

    public static void validateCoordinate(Coordinate coordinate) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, coordinate);
        if (!CoordinateUtils.isCorrectCoordinate(coordinate)) {
            throw new GameCoordinateIsNotCorrectIncorrectException("Coordinate: %s is not VALID!".formatted(coordinate));
        }
    }

    public static void validatePlayerId(String playerId) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, playerId);
        if (StringUtils.isBlank(playerId)) {
            throw new GamePlayerIdIsNoctCorrectException("Player ID: %s is not VALID!".formatted(playerId));
        }

    }

    public static void validatePlayerName(String playerName) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, playerName);
        if (StringUtils.isBlank(playerName)) {
            throw new GamePlayerNameIsNotCorrectException("Player Name: %s is not VALID!".formatted(playerName));
        }
    }

    public static void validateSessionId(String sessionId) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, sessionId);
        if (StringUtils.isBlank(sessionId)) {
            throw new GameSessionIdIsNotCorrectException("Session ID: %s is not VALID!".formatted(sessionId));
        }
    }

    public static void validateShipDirection(String shipDirection) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, shipDirection);
        if (StringUtils.isBlank(shipDirection)) {
            throw new GameShipDirectionIsNotCorrectException("Ship Direction: %s is not VALID!".formatted(shipDirection));
        }
        try {
            ShipDirection.valueOf(shipDirection);
        } catch (Exception ex) {
            throw new GameShipDirectionIsNotCorrectException("Ship Direction: %s is not VALID!".formatted(shipDirection));
        }
    }

    public static void validateShipId(String shipId) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, shipId);
        if (StringUtils.isBlank(shipId)) {
            throw new GameShipIdIsNotCorrectException("Ship ID: %s is not VALID!".formatted(shipId));
        }
    }

    public static void validateGameEdition(String gameEdition) {
        log.debug(DEBUG_TEMPLATE_MESSAGE, gameEdition);
        if (StringUtils.isBlank(gameEdition)) {
            throw new GameEditionIsNotCorrectException("Game Edition: %s is not VALID!".formatted(gameEdition));
        }
        try {
            GameEdition.valueOf(gameEdition);
        } catch (Exception ex) {
            throw new GameEditionIsNotCorrectException("Game Edition: %s is not VALID!".formatted(gameEdition));
        }
    }
}
