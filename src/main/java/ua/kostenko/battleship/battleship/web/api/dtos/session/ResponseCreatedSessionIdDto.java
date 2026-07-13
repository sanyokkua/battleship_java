package ua.kostenko.battleship.battleship.web.api.dtos.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for the created session ID in the Battleship game.
 * <p>
 * The ResponseCreatedSessionIdDto class is used to transfer data related to the newly created session ID.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseCreatedSessionIdDto {

    /**
     * The unique identifier of the created session.
     */
    @Schema(description = "Newly created session's identifier")
    private String sessionId;
}
