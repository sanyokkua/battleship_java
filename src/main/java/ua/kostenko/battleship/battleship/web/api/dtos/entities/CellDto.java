package ua.kostenko.battleship.battleship.web.api.dtos.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

/**
 * Data Transfer Object (DTO) for a cell in the Battleship game.
 * <p>
 * The CellDto class is used to transfer data related to a cell, including its coordinates, associated ship, shot status, and availability status.
 * </p>
 *
 * @see Cell
 * @see Ship
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CellDto {

    /**
     * The row coordinate of the cell.
     */
    private int row;

    /**
     * The column coordinate of the cell.
     */
    private int col;

    /**
     * The ship associated with the cell, if any.
     */
    private Ship ship;

    /**
     * Indicates whether the cell has been shot at.
     */
    private boolean hasShot;

    /**
     * Indicates whether the cell is available for placing a ship.
     */
    @JsonProperty("isAvailable")
    private boolean isAvailable;

    /**
     * Creates a CellDto object from a Cell object.
     *
     * @param cell the Cell object to convert to CellDto
     * @return the created CellDto object
     */
    public static CellDto of(Cell cell) {
        val coordinate = cell.coordinate();
        return CellDto.builder()
                .row(coordinate.row())
                .col(coordinate.column())
                .ship(cell.ship())
                .hasShot(cell.hasShot())
                .isAvailable(cell.isAvailable())
                .build();
    }
}
