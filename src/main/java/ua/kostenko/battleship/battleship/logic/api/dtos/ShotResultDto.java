package ua.kostenko.battleship.battleship.logic.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShotResult;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShotResultDto {
    private ShotResult shotResult;
}
