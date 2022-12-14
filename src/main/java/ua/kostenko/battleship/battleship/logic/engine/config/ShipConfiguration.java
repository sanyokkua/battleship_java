package ua.kostenko.battleship.battleship.logic.engine.config;

import lombok.Builder;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

@Builder
public record ShipConfiguration(ShipType shipType, int shipSize, int shipAmount) {
}
