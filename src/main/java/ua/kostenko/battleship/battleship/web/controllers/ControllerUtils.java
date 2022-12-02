package ua.kostenko.battleship.battleship.web.controllers;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.web.controllers.api.dtos.CellDto;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControllerUtils {
    public static CellDto[][] mapFieldToFieldDto(final Cell[][] playerField) {
        val field = new CellDto[GameEditionConfiguration.NUMBER_OF_ROWS][GameEditionConfiguration.NUMBER_OF_COLUMNS];
        for (int i = 0; i < GameEditionConfiguration.NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < GameEditionConfiguration.NUMBER_OF_COLUMNS; j++) {
                field[i][j] = CellDto.of(playerField[i][j]);
            }
        }
        return field;
    }
}
