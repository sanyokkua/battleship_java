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
    @ExceptionHandler(value = {GameCellAlreadyShotException.class,
            GameCoordinateIsNotCorrectIncorrectException.class,
            GameEditionIsNotCorrectException.class,
            GameOpponentNotFoundException.class,
            GamePlayerIdIsNotCorrectException.class,
            GamePlayerNameIsNotCorrectException.class,
            GamePlayerNotActiveException.class,
            GamePlayerNotFoundException.class,
            GameSessionFullException.class,
            GameSessionIdIsNotCorrectException.class,
            GameShipAlreadyPlacedException.class,
            GameShipDirectionIsNotCorrectException.class,
            GameShipIdIsNotCorrectException.class,
            GameShipsNotAllPlacedException.class,
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
            case GameCellAlreadyShotException _ -> "CELL_ALREADY_SHOT";
            case GameCoordinateIsNotCorrectIncorrectException _ -> "COORDINATE_INVALID";
            case GameEditionIsNotCorrectException _ -> "EDITION_INVALID";
            case GameOpponentNotFoundException _ -> "OPPONENT_NOT_FOUND";
            case GamePlayerIdIsNotCorrectException _ -> "PLAYER_ID_INVALID";
            case GamePlayerNameIsNotCorrectException _ -> "PLAYER_NAME_INVALID";
            case GamePlayerNotActiveException _ -> "PLAYER_NOT_ACTIVE";
            case GamePlayerNotFoundException _ -> "PLAYER_NOT_FOUND";
            case GameSessionFullException _ -> "SESSION_FULL";
            case GameSessionIdIsNotCorrectException _ -> "SESSION_NOT_FOUND";
            case GameShipAlreadyPlacedException _ -> "SHIP_ALREADY_PLACED";
            case GameShipDirectionIsNotCorrectException _ -> "SHIP_DIRECTION_INVALID";
            case GameShipIdIsNotCorrectException _ -> "SHIP_ID_INVALID";
            case GameShipsNotAllPlacedException _ -> "SHIPS_NOT_ALL_PLACED";
            case GameStageIsNotCorrectException _ -> "STAGE_INVALID";
            default -> throw new IllegalStateException(
                    "Unmapped exception type for error code resolution: %s".formatted(ex.getClass()
                            .getName()));
        };
    }
}
