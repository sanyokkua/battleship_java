package ua.kostenko.battleship.battleship.web.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for the current game stage in the Battleship game.
 * <p>
 * The ResponseCurrentGameStageDto class is used to transfer data related to the current stage of the game.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseCurrentGameStageDto {

    /**
     * The current game stage, represented as a string.
     */
    private String gameStage;
}
