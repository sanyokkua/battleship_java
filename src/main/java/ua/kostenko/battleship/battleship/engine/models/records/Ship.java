package ua.kostenko.battleship.battleship.engine.models.records;

import lombok.Builder;
import lombok.NonNull;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;

@Builder
public record Ship(
        @NonNull String shipId,
        @NonNull ShipType shipType,
        @NonNull Direction direction,
        int shipSize
) {
}
