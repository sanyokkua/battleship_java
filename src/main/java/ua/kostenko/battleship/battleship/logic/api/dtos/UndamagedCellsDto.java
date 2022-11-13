package ua.kostenko.battleship.battleship.logic.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UndamagedCellsDto {
    private int numberOfUndamagedCells;
}
