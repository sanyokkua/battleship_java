package ua.kostenko.battleship.battleship.logic.engine.config;

import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

import java.util.Map;

/**
 * Class representing the configuration specific to the Milton Bradley edition of the Battleship game.
 * <p>
 * This class extends {@link GameEditionConfiguration} and provides specific mappings for ship sizes and amounts
 * according to the rules of the Milton Bradley edition.
 * </p>
 *
 * @see GameEditionConfiguration
 * @see ShipType
 */
class MiltonBradleyGameEditionConfiguration extends GameEditionConfiguration {

    /**
     * Provides the size mapping for ships in the Milton Bradley edition.
     *
     * @return a map with ship types as keys and their corresponding sizes as values
     */
    protected Map<ShipType, Integer> getSizeMapping() {
        return Map.of(ShipType.SUBMARINE, 2, ShipType.DESTROYER, 3, ShipType.BATTLESHIP, 4, ShipType.CARRIER, 5);
    }

    /**
     * Provides the amount mapping for ships in the Milton Bradley edition.
     *
     * @return a map with ship types as keys and their corresponding amounts as values
     */
    protected Map<ShipType, Integer> getAmountMapping() {
        return Map.of(ShipType.SUBMARINE, 4, ShipType.DESTROYER, 3, ShipType.BATTLESHIP, 2, ShipType.CARRIER, 1);
    }
}
