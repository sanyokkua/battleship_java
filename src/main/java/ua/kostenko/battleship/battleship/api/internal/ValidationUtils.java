package ua.kostenko.battleship.battleship.api.internal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import ua.kostenko.battleship.battleship.api.internal.exceptions.*;
import ua.kostenko.battleship.battleship.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.engine.utils.CoordinateUtil;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationUtils {

    public static void validateCoordinate(Coordinate coordinate) {
        log.debug("Validations of: {}", coordinate);
        if (!CoordinateUtil.isCorrectCoordinate(coordinate)) {
            throw new IncorrectCoordinateException(
                    "Coordinate: %s is not VALID!".formatted(coordinate));
        }
    }

    public static void validatePlayerId(String playerId) {
        log.debug("Validations of: {}", playerId);
        if (StringUtils.isBlank(playerId)) {
            throw new IncorrectPlayerIdException(
                    "Player ID: %s is not VALID!".formatted(playerId));
        }

    }

    public static void validatePlayerName(String playerName) {
        log.debug("Validations of: {}", playerName);
        if (StringUtils.isBlank(playerName)) {
            throw new IncorrectPlayerNameException(
                    "Player Name: %s is not VALID!".formatted(playerName));
        }
    }

    public static void validateSessionId(String sessionId) {
        log.debug("Validations of: {}", sessionId);
        if (StringUtils.isBlank(sessionId)) {
            throw new IncorrectSessionIdException(
                    "Session ID: %s is not VALID!".formatted(sessionId));
        }
    }

    public static void validateShipDirection(String shipDirection) {
        log.debug("Validations of: {}", shipDirection);
        if (StringUtils.isBlank(shipDirection)) {
            throw new IncorrectShipDirectionException(
                    "Ship Direction: %s is not VALID!".formatted(shipDirection));
        }
        try {
            Direction.valueOf(shipDirection);
        } catch (Exception ex) {
            throw new IncorrectShipDirectionException(
                    "Ship Direction: %s is not VALID!".formatted(shipDirection));
        }
    }

    public static void validateShipId(String shipId) {
        log.debug("Validations of: {}", shipId);
        if (StringUtils.isBlank(shipId)) {
            throw new IncorrectShipIdException(
                    "Ship ID: %s is not VALID!".formatted(shipId));
        }
    }

    public static void validateGameEdition(String gameEdition) {
        log.debug("Validations of: {}", gameEdition);
        if (StringUtils.isBlank(gameEdition)) {
            throw new IncorrectGameEditionException(
                    "Game Edition: %s is not VALID!".formatted(gameEdition));
        }
        try {
            GameEdition.valueOf(gameEdition);
        } catch (Exception ex) {
            throw new IncorrectGameEditionException(
                    "Game Edition: %s is not VALID!".formatted(gameEdition));
        }
    }
}
