package ua.kostenko.battleship.battleship.web.api.dtos.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for specifying a player's name in the Battleship game.
 * <p>
 * The ParamPlayerNameDto class is used to transfer data related to the player's name.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamPlayerNameDto {

    /**
     * The name of the player.
     */
    @Schema(description = "Display name for the new player")
    private String playerName;
}
