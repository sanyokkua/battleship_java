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
            GameSessionIdIsNotCorrectException.class,
            GameShipDirectionIsNotCorrectException.class,
            GameShipIdIsNotCorrectException.class,
            GameStageIsNotCorrectException.class})
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        var message = ex.getMessage();
        var body = ExceptionDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorMessage(message)
                .build();
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
}
