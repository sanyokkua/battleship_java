package ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.entities.ShipDto;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponsePreparationState {
    private List<ShipDto> ships;
    private CellDto[][] field;
}
