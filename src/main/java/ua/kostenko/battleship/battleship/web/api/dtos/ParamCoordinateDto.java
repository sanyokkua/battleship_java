package ua.kostenko.battleship.battleship.web.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

/**
 * Data Transfer Object (DTO) for specifying coordinates in the Battleship game.
 * <p>
 * The ParamCoordinateDto class is used to transfer data related to the coordinates of a cell.
 * </p>
 *
 * @see Coordinate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamCoordinateDto {

    /**
     * The row coordinate of the cell.
     */
    private int row;

    /**
     * The column coordinate of the cell.
     */
    private int col;

    /**
     * Converts the ParamCoordinateDto object to a Coordinate object.
     *
     * @param coordinateDto the ParamCoordinateDto object to convert
     * @return the created Coordinate object
     */
    public static Coordinate getCoordinate(ParamCoordinateDto coordinateDto) {
        return Coordinate.of(coordinateDto.row, coordinateDto.col);
    }
}
