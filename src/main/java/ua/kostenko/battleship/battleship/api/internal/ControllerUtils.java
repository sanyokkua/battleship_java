package ua.kostenko.battleship.battleship.api.internal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.kostenko.battleship.battleship.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.engine.config.GameConfig;
import ua.kostenko.battleship.battleship.engine.models.records.Cell;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControllerUtils {
    public static CellDto[][] mapFieldToFieldDto(final Cell[][] playerField) {
        CellDto[][] field = new CellDto[GameConfig.NUMBER_OF_ROWS][GameConfig.NUMBER_OF_COLUMNS];
        for (int i = 0; i < GameConfig.NUMBER_OF_ROWS; i++) {
            for (int j = 0; j < GameConfig.NUMBER_OF_COLUMNS; j++) {
                field[i][j] = CellDto.of(playerField[i][j]);
            }
        }
        return field;
    }
}
