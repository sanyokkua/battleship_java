package ua.kostenko.battleship.battleship.logic.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStageDto {
    private GameStage gameStage;
}
