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

@RestControllerAdvice
public class ValidationExceptionHandler extends ResponseEntityExceptionHandler {

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
