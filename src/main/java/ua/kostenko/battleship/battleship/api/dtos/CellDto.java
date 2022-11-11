package ua.kostenko.battleship.battleship.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CellDto {
    private int row;
    private int col;
    private Ship ship;
    private boolean hasShot;
    private boolean isAvailable;

    public static CellDto of(Cell cell) {
        return CellDto.builder()
                      .row(cell.coordinate().row())
                      .col(cell.coordinate().column())
                      .ship(cell.ship())
                      .hasShot(cell.hasShot())
                      .isAvailable(cell.isAvailable())
                      .build();
    }
}
