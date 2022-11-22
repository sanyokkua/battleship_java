package ua.kostenko.battleship.battleship.logic.api.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CellDto {
    private int row;
    private int col;
    private Ship ship;
    private boolean hasShot;
    @JsonProperty("isAvailable")
    private boolean isAvailable;

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
