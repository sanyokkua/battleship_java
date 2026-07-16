package ua.kostenko.battleship.battleship.web.api.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for exceptions in the Battleship game.
 * <p>
 * The ExceptionDto class is used to transfer data related to an exception, including its status and error message.
 * </p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExceptionDto {

    /**
     * The status code of the exception.
     */
    @Schema(description = "HTTP status code", example = "400")
    private int status;

    /**
     * The error message of the exception.
     */
    @Schema(description = "Human-readable error message")
    private String errorMessage;

    /**
     * The stable, machine-readable error code identifying the exception type.
     */
    @Schema(description = "Stable, machine-readable error code identifying the exception type", example = "SESSION_NOT_FOUND")
    private String errorCode;
}
