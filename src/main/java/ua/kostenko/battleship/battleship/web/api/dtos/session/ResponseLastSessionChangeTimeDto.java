package ua.kostenko.battleship.battleship.web.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for the last session change time in the Battleship game.
 * <p>
 * The ResponseLastSessionChangeTimeDto class is used to transfer data related to the last change time of a session.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseLastSessionChangeTimeDto {

    /**
     * The ID of the last session change.
     */
    private String lastId;
}
