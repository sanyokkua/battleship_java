package ua.kostenko.battleship.battleship.web.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for the created player in the Battleship game.
 * <p>
 * The ResponseCreatedPlayerDto class is used to transfer data related to a newly created player, including their ID and name.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseCreatedPlayerDto {

    /**
     * The unique identifier of the player.
     */
    private String playerId;

    /**
     * The name of the player.
     */
    private String playerName;
}
