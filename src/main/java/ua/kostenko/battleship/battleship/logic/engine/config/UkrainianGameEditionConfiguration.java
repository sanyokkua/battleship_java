package ua.kostenko.battleship.battleship.logic.engine.config;

import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

import java.util.Map;

class UkrainianGameEditionConfiguration extends GameEditionConfiguration {

    protected Map<ShipType, Integer> getSizeMapping() {
        return Map.of(ShipType.PATROL_BOAT, 1, ShipType.SUBMARINE, 2, ShipType.DESTROYER, 3, ShipType.BATTLESHIP, 4);
    }

    protected Map<ShipType, Integer> getAmountMapping() {
        return Map.of(ShipType.PATROL_BOAT, 4, ShipType.SUBMARINE, 3, ShipType.DESTROYER, 2, ShipType.BATTLESHIP, 1);
    }
}
