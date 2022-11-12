package ua.kostenko.battleship.battleship.logic.api;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.logic.engine.utils.FieldUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ControllerUtilsTest {

    @Test
    void testMapFieldToFieldDto() {
        var field = FieldUtils.initializeField();
        var fieldDto = ControllerUtils.mapFieldToFieldDto(field);

        assertNotNull(fieldDto);
        for (int i = 0; i < field.length; i++) {
            var line = field[i];
            for (int j = 0; j < line.length; j++) {
                assertEquals(field[i][j].ship(), fieldDto[i][j].getShip());
                assertEquals(field[i][j].hasShot(), fieldDto[i][j].isHasShot());
                assertEquals(field[i][j].isAvailable(), fieldDto[i][j].isAvailable());
                assertEquals(field[i][j].coordinate()
                                        .row(), fieldDto[i][j].getRow());
                assertEquals(field[i][j].coordinate()
                                        .column(), fieldDto[i][j].getCol());
            }
        }

    }
}
