package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamShipDto {
    private int row;
    private int col;
    private String direction;

    public static Coordinate getCoordinateFrom(ParamShipDto paramShipDto) {
        return Coordinate.of(paramShipDto.row, paramShipDto.col);
    }
}
