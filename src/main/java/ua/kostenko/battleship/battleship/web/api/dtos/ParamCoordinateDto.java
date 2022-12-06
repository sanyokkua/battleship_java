package ua.kostenko.battleship.battleship.web.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamCoordinateDto {
    private int row;
    private int col;

    public static Coordinate getCoordinate(ParamCoordinateDto coordinateDto) {
        return Coordinate.of(coordinateDto.row, coordinateDto.col);
    }
}
