package ua.kostenko.battleship.battleship.web.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;
import ua.kostenko.battleship.battleship.logic.api.exceptions.GameInternalProblemException;
import ua.kostenko.battleship.battleship.web.api.dtos.ExceptionDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Focused unit test proving {@link InternalExceptionHandler} always sets the "INTERNAL" errorCode.
 */
class InternalExceptionHandlerTest {

    private final InternalExceptionHandler handler = new InternalExceptionHandler();
    private final WebRequest webRequest = mock(WebRequest.class);

    @Test
    void handleConflict_setsInternalErrorCode_forInternalProblemException() {
        var ex = new GameInternalProblemException("unexpected failure");

        var response = handler.handleConflict(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        var body = (ExceptionDto) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(body.getErrorMessage()).isEqualTo("unexpected failure");
        assertThat(body.getErrorCode()).isEqualTo("INTERNAL");
    }
}
