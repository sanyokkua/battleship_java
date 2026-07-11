package ua.kostenko.battleship.battleship.web.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameCoordinateIsNotCorrectIncorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameSessionIdIsNotCorrectException;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameStageIsNotCorrectException;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Focused unit tests proving the errorCode dispatch mechanism in {@link ValidationExceptionHandler} is wired
 * correctly. Exhaustive per-exception-type coverage at the HTTP layer is out of scope here.
 */
class ValidationExceptionHandlerTest {

    private final ValidationExceptionHandler handler = new ValidationExceptionHandler();
    private final WebRequest webRequest = mock(WebRequest.class);

    @Test
    void handleConflict_setsCoordinateInvalidErrorCode_forCoordinateException() {
        var ex = new GameCoordinateIsNotCorrectIncorrectException("bad coordinate");

        var response = handler.handleConflict(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = (ExceptionDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.getErrorMessage()).isEqualTo("bad coordinate");
        assertThat(body.getErrorCode()).isEqualTo("COORDINATE_INVALID");
    }

    @Test
    void handleConflict_setsSessionNotFoundErrorCode_forSessionIdException() {
        var ex = new GameSessionIdIsNotCorrectException("unknown session");

        var response = handler.handleConflict(ex, webRequest);

        var body = (ExceptionDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
        assertThat(body.getErrorMessage()).isEqualTo("unknown session");
    }

    @Test
    void handleConflict_setsStageInvalidErrorCode_forStageException() {
        var ex = new GameStageIsNotCorrectException("wrong stage");

        var response = handler.handleConflict(ex, webRequest);

        var body = (ExceptionDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("STAGE_INVALID");
    }

    @Test
    void resolveErrorCode_throwsIllegalStateException_forUnmappedExceptionType() throws Exception {
        // resolveErrorCode's default branch is a defensive safety net: every exception type registered on the
        // @ExceptionHandler is also mapped in the switch, so this path cannot be reached through the real
        // dispatch pipeline. It is exercised directly via reflection to close the coverage gap without
        // fabricating an unreachable production code path.
        Method resolveErrorCode = ValidationExceptionHandler.class.getDeclaredMethod("resolveErrorCode",
                                                                                       RuntimeException.class);
        resolveErrorCode.setAccessible(true);
        var unmapped = new IllegalArgumentException("not a typed game exception");

        assertThatThrownBy(() -> {
            try {
                resolveErrorCode.invoke(handler, unmapped);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Unmapped exception type for error code resolution")
          .hasMessageContaining("IllegalArgumentException");
    }
}
