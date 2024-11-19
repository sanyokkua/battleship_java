package ua.kostenko.battleship.battleship.web.exceptions;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameInternalProblemException;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;

/**
 * Exception handler for internal server errors in the Battleship game.
 * <p>
 * The InternalExceptionHandler class handles exceptions related to internal problems during the game.
 * </p>
 */
@RestControllerAdvice
public class InternalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles GameInternalProblemException exceptions.
     *
     * @param ex      the runtime exception thrown during the game
     * @param request the web request context
     * @return a ResponseEntity containing the ExceptionDto
     */
    @ExceptionHandler(value = {GameInternalProblemException.class})
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        var message = ex.getMessage();
        var body = ExceptionDto.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorMessage(message)
                .build();
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
