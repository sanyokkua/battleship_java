package ua.kostenko.battleship.battleship.logic.engine.models.records;

import lombok.Builder;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

@Builder
public record Ship(String shipId, ShipType shipType, ShipDirection shipDirection, int shipSize) {
}
