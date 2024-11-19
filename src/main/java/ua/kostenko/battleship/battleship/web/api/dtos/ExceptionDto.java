package ua.kostenko.battleship.battleship.web.api.dtos;

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
    private int status;

    /**
     * The error message of the exception.
     */
    private String errorMessage;
}
