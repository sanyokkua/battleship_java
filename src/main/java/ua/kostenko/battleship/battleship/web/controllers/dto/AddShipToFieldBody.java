package ua.kostenko.battleship.battleship.web.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddShipToFieldBody {
    private Coordinate coordinate;
    private String shipDirection;
}
