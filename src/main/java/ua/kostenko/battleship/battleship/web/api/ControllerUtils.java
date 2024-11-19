package ua.kostenko.battleship.battleship.web.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;
import ua.kostenko.battleship.battleship.web.api.dtos.entities.CellDto;

/**
 * Utility class for controller operations in the Battleship game.
 * <p>
 * The ControllerUtils class provides static methods to assist with common controller tasks, such as mapping game fields to DTOs.
 * </p>
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControllerUtils {

    /**
     * Maps a field of Cell objects to a field of CellDto objects.
     *
     * @param playerField the 2D array of Cell objects representing the player's field
     * @return a 2D array of CellDto objects representing the player's field
     */
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
