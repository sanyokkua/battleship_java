package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for indicating whether a player is ready in the Battleship game.
 * <p>
 * The ResponsePlayerReady class is used to transfer the readiness status of a player.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponsePlayerReady {

    /**
     * Indicates whether the player is ready.
     */
    @Schema(description = "Whether the player is now marked ready")
    private boolean ready;
}
