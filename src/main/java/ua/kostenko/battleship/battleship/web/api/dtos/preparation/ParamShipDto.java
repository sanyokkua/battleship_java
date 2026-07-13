package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;

/**
 * Data Transfer Object (DTO) for the parameters required to place a ship in the Battleship game.
 * <p>
 * The ParamShipDto class is used to transfer data related to the placement of a ship, including its coordinates and direction.
 * </p>
 *
 * @see Coordinate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamShipDto {

    /**
     * The row coordinate for placing the ship.
     */
    @Schema(description = "Zero-based top-left board row")
    private int row;

    /**
     * The column coordinate for placing the ship.
     */
    @Schema(description = "Zero-based top-left board column")
    private int col;

    /**
     * The direction in which to place the ship (e.g., HORIZONTAL, VERTICAL).
     */
    @Schema(description = "Ship orientation", allowableValues = {"HORIZONTAL", "VERTICAL"})
    private String direction;

    /**
     * Converts the ParamShipDto object to a Coordinate object.
     *
     * @param paramShipDto the ParamShipDto object to convert
     * @return the created Coordinate object
     */
    public static Coordinate getCoordinateFrom(ParamShipDto paramShipDto) {
        return Coordinate.of(paramShipDto.row, paramShipDto.col);
    }
}
