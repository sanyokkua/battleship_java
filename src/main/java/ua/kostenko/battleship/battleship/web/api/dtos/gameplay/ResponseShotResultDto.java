package ua.kostenko.battleship.battleship.web.api.dtos.gameplay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for the shot result in the Battleship game.
 * <p>
 * The ResponseShotResultDto class is used to transfer data related to the result of a shot, such as a hit or miss.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseShotResultDto {

    /**
     * The result of the shot, represented as a string.
     */
    private String shotResult;
}
