package ua.kostenko.battleship.battleship.logic.api;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilsTest {
    @Test
    void validateCoordinate() {
        assertThrows(GameCoordinateIsNotCorrectIncorrectException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(-1, 0)));
        assertThrows(GameCoordinateIsNotCorrectIncorrectException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(0, -1)));
        assertThrows(GameCoordinateIsNotCorrectIncorrectException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(10, 0)));
        assertThrows(GameCoordinateIsNotCorrectIncorrectException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(0, 10)));
        assertDoesNotThrow(() -> ValidationUtils.validateCoordinate(Coordinate.of(0, 0)));
        assertDoesNotThrow(() -> ValidationUtils.validateCoordinate(Coordinate.of(5, 5)));
    }

    @Test
    void validatePlayerId() {
        assertThrows(GamePlayerIdIsNoctCorrectException.class, () -> ValidationUtils.validatePlayerId(""));
        assertThrows(GamePlayerIdIsNoctCorrectException.class, () -> ValidationUtils.validatePlayerId(null));
        assertThrows(GamePlayerIdIsNoctCorrectException.class, () -> ValidationUtils.validatePlayerId("    "));
        assertDoesNotThrow(() -> ValidationUtils.validatePlayerId("player-id"));
    }

    @Test
    void validatePlayerName() {
        assertThrows(GamePlayerNameIsNotCorrectException.class, () -> ValidationUtils.validatePlayerName(""));
        assertThrows(GamePlayerNameIsNotCorrectException.class, () -> ValidationUtils.validatePlayerName(null));
        assertThrows(GamePlayerNameIsNotCorrectException.class, () -> ValidationUtils.validatePlayerName("    "));
        assertDoesNotThrow(() -> ValidationUtils.validatePlayerName("player-name"));
    }

    @Test
    void validateSessionId() {
        assertThrows(GameSessionIdIsNotCorrectException.class, () -> ValidationUtils.validateSessionId(""));
        assertThrows(GameSessionIdIsNotCorrectException.class, () -> ValidationUtils.validateSessionId(null));
        assertThrows(GameSessionIdIsNotCorrectException.class, () -> ValidationUtils.validateSessionId("    "));
        assertDoesNotThrow(() -> ValidationUtils.validateSessionId("session-id"));
    }

    @Test
    void validateShipDirection() {
        assertThrows(GameShipDirectionIsNotCorrectException.class, () -> ValidationUtils.validateShipDirection(""));
        assertThrows(GameShipDirectionIsNotCorrectException.class, () -> ValidationUtils.validateShipDirection(null));
        assertThrows(GameShipDirectionIsNotCorrectException.class, () -> ValidationUtils.validateShipDirection("    "));
        assertThrows(GameShipDirectionIsNotCorrectException.class,
                     () -> ValidationUtils.validateShipDirection("horizontal"));
        assertThrows(GameShipDirectionIsNotCorrectException.class,
                     () -> ValidationUtils.validateShipDirection("vertical"));
        assertThrows(GameShipDirectionIsNotCorrectException.class,
                     () -> ValidationUtils.validateShipDirection("other"));
        assertThrows(GameShipDirectionIsNotCorrectException.class,
                     () -> ValidationUtils.validateShipDirection("INCORRECT"));
        assertDoesNotThrow(() -> ValidationUtils.validateShipDirection("HORIZONTAL"));
        assertDoesNotThrow(() -> ValidationUtils.validateShipDirection("VERTICAL"));
    }

    @Test
    void validateShipId() {
        assertThrows(GameShipIdIsNotCorrectException.class, () -> ValidationUtils.validateShipId(""));
        assertThrows(GameShipIdIsNotCorrectException.class, () -> ValidationUtils.validateShipId(null));
        assertThrows(GameShipIdIsNotCorrectException.class, () -> ValidationUtils.validateShipId("    "));
        assertDoesNotThrow(() -> ValidationUtils.validateShipId("ship-id"));
    }

    @Test
    void validateGameEdition() {
        assertThrows(GameEditionIsNotCorrectException.class, () -> ValidationUtils.validateGameEdition(""));
        assertThrows(GameEditionIsNotCorrectException.class, () -> ValidationUtils.validateGameEdition(null));
        assertThrows(GameEditionIsNotCorrectException.class, () -> ValidationUtils.validateGameEdition("    "));
        assertThrows(GameEditionIsNotCorrectException.class, () -> ValidationUtils.validateGameEdition("Edition"));
        assertThrows(GameEditionIsNotCorrectException.class, () -> ValidationUtils.validateGameEdition("ukrainian"));
        assertDoesNotThrow(() -> ValidationUtils.validateGameEdition("UKRAINIAN"));
        assertDoesNotThrow(() -> ValidationUtils.validateGameEdition("MILTON_BRADLEY"));
    }
}
