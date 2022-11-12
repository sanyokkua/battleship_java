package ua.kostenko.battleship.battleship.api.internal;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.api.internal.exceptions.*;
import ua.kostenko.battleship.battleship.engine.models.records.Coordinate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilsTest {
    @Test
    void validateCoordinate() {
        assertThrows(IncorrectCoordinateException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(-1, 0)));
        assertThrows(IncorrectCoordinateException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(0, -1)));
        assertThrows(IncorrectCoordinateException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(10, 0)));
        assertThrows(IncorrectCoordinateException.class,
                     () -> ValidationUtils.validateCoordinate(Coordinate.of(0, 10)));
        assertDoesNotThrow(() -> ValidationUtils.validateCoordinate(Coordinate.of(0, 0)));
        assertDoesNotThrow(() -> ValidationUtils.validateCoordinate(Coordinate.of(5, 5)));
    }

    @Test
    void validatePlayerId() {
        assertThrows(IncorrectPlayerIdException.class, () -> ValidationUtils.validatePlayerId(""));
        assertThrows(IncorrectPlayerIdException.class,
                     () -> ValidationUtils.validatePlayerId(null));
        assertThrows(IncorrectPlayerIdException.class,
                     () -> ValidationUtils.validatePlayerId("    "));
        assertDoesNotThrow(() -> ValidationUtils.validatePlayerId("player-id"));
    }

    @Test
    void validatePlayerName() {
        assertThrows(IncorrectPlayerNameException.class,
                     () -> ValidationUtils.validatePlayerName(""));
        assertThrows(IncorrectPlayerNameException.class,
                     () -> ValidationUtils.validatePlayerName(null));
        assertThrows(IncorrectPlayerNameException.class,
                     () -> ValidationUtils.validatePlayerName("    "));
        assertDoesNotThrow(() -> ValidationUtils.validatePlayerName("player-name"));
    }

    @Test
    void validateSessionId() {
        assertThrows(IncorrectSessionIdException.class,
                     () -> ValidationUtils.validateSessionId(""));
        assertThrows(IncorrectSessionIdException.class,
                     () -> ValidationUtils.validateSessionId(null));
        assertThrows(IncorrectSessionIdException.class,
                     () -> ValidationUtils.validateSessionId("    "));
        assertDoesNotThrow(() -> ValidationUtils.validateSessionId("session-id"));
    }

    @Test
    void validateShipDirection() {
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection(""));
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection(null));
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection("    "));
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection("horizontal"));
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection("vertical"));
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection("other"));
        assertThrows(IncorrectShipDirectionException.class,
                     () -> ValidationUtils.validateShipDirection("INCORRECT"));
        assertDoesNotThrow(() -> ValidationUtils.validateShipDirection("HORIZONTAL"));
        assertDoesNotThrow(() -> ValidationUtils.validateShipDirection("VERTICAL"));
    }

    @Test
    void validateShipId() {
        assertThrows(IncorrectShipIdException.class, () -> ValidationUtils.validateShipId(""));
        assertThrows(IncorrectShipIdException.class, () -> ValidationUtils.validateShipId(null));
        assertThrows(IncorrectShipIdException.class, () -> ValidationUtils.validateShipId("    "));
        assertDoesNotThrow(() -> ValidationUtils.validateShipId("ship-id"));
    }

    @Test
    void validateGameEdition() {
        assertThrows(IncorrectGameEditionException.class,
                     () -> ValidationUtils.validateGameEdition(""));
        assertThrows(IncorrectGameEditionException.class,
                     () -> ValidationUtils.validateGameEdition(null));
        assertThrows(IncorrectGameEditionException.class,
                     () -> ValidationUtils.validateGameEdition("    "));
        assertThrows(IncorrectGameEditionException.class,
                     () -> ValidationUtils.validateGameEdition("Edition"));
        assertThrows(IncorrectGameEditionException.class,
                     () -> ValidationUtils.validateGameEdition("ukrainian"));
        assertDoesNotThrow(() -> ValidationUtils.validateGameEdition("UKRAINIAN"));
        assertDoesNotThrow(() -> ValidationUtils.validateGameEdition("MILTON_BRADLEY"));
    }
}
