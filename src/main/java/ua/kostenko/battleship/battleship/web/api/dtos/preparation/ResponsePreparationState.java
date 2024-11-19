package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.web.api.dtos.entities.CellDto;
import ua.kostenko.battleship.battleship.web.api.dtos.entities.ShipDto;

import java.util.List;

/**
 * Data Transfer Object (DTO) for the preparation state in the Battleship game.
 * <p>
 * The ResponsePreparationState class is used to transfer data related to the preparation state,
 * including the list of ships and the field status.
 * </p>
 *
 * @see ShipDto
 * @see CellDto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponsePreparationState {

    /**
     * The list of ships in the preparation state.
     */
    private List<ShipDto> ships;

    /**
     * The field status in the preparation state.
     */
    private CellDto[][] field;
}
