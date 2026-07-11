package ua.kostenko.battleship.battleship.web.exceptions;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ua.kostenko.battleship.battleship.logic.api.exceptions.*;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;

/**
 * Exception handler for validation errors in the Battleship game.
 * <p>
 * The ValidationExceptionHandler class handles exceptions related to validation issues during the game.
 * </p>
 */
@RestControllerAdvice
public class ValidationExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles various validation-related exceptions.
     *
     * @param ex      the runtime exception thrown during the game
     * @param request the web request context
     * @return a ResponseEntity containing the ExceptionDto
     */
    @ExceptionHandler(value = {GameCoordinateIsNotCorrectIncorrectException.class,
            GameEditionIsNotCorrectException.class,
            GamePlayerIdIsNotCorrectException.class,
            GamePlayerNameIsNotCorrectException.class,
            GamePlayerNotActiveException.class,
            GameSessionIdIsNotCorrectException.class,
            GameShipDirectionIsNotCorrectException.class,
            GameShipIdIsNotCorrectException.class,
            GameStageIsNotCorrectException.class})
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        var message = ex.getMessage();
        var body = ExceptionDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorMessage(message)
                .errorCode(resolveErrorCode(ex))
                .build();
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Resolves the stable, machine-readable error code for a given validation-related exception.
     *
     * @param ex the runtime exception thrown during the game
     * @return the stable error code associated with the exception's runtime type
     */
    private String resolveErrorCode(RuntimeException ex) {
        return switch (ex) {
            case GameCoordinateIsNotCorrectIncorrectException ignored -> "COORDINATE_INVALID";
            case GameEditionIsNotCorrectException ignored -> "EDITION_INVALID";
            case GamePlayerIdIsNotCorrectException ignored -> "PLAYER_ID_INVALID";
            case GamePlayerNameIsNotCorrectException ignored -> "PLAYER_NAME_INVALID";
            case GamePlayerNotActiveException ignored -> "PLAYER_NOT_ACTIVE";
            case GameSessionIdIsNotCorrectException ignored -> "SESSION_NOT_FOUND";
            case GameShipDirectionIsNotCorrectException ignored -> "SHIP_DIRECTION_INVALID";
            case GameShipIdIsNotCorrectException ignored -> "SHIP_ID_INVALID";
            case GameStageIsNotCorrectException ignored -> "STAGE_INVALID";
            default -> throw new IllegalStateException(
                    "Unmapped exception type for error code resolution: %s".formatted(ex.getClass()
                            .getName()));
        };
    }
}
