package ua.kostenko.battleship.battleship.web.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for specifying a game edition in the Battleship game.
 * <p>
 * The ParamGameEditionDto class is used to transfer data related to the chosen game edition.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamGameEditionDto {

    /**
     * The name of the game edition.
     */
    private String gameEdition;
}
