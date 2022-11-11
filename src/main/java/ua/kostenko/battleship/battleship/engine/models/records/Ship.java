package ua.kostenko.battleship.battleship.engine.models.records;

import lombok.Builder;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;

@Builder
public record Ship(
        String shipId,
        ShipType shipType,
        Direction direction,
        int shipSize
) {
}
