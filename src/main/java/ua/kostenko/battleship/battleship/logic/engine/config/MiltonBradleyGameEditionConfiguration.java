package ua.kostenko.battleship.battleship.logic.engine.config;

import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

import java.util.Map;

class MiltonBradleyGameEditionConfiguration extends GameEditionConfiguration {

    protected Map<ShipType, Integer> getSizeMapping() {
        return Map.of(ShipType.SUBMARINE, 2, ShipType.DESTROYER, 3, ShipType.BATTLESHIP, 4, ShipType.CARRIER, 5);
    }

    protected Map<ShipType, Integer> getAmountMapping() {
        return Map.of(ShipType.SUBMARINE, 4, ShipType.DESTROYER, 3, ShipType.BATTLESHIP, 2, ShipType.CARRIER, 1);
    }
}
