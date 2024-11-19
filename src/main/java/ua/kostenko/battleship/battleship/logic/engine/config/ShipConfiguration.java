package ua.kostenko.battleship.battleship.logic.engine.config;

import lombok.Builder;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

/**
 * Record representing the configuration of a ship in the Battleship game.
 * <p>
 * The ShipConfiguration record encapsulates the type, size, and amount of a ship,
 * which is used for setting up the game according to different game editions.
 * </p>
 *
 * @param shipType   the type of the ship, defined by {@link ShipType}
 * @param shipSize   the size of the ship in grid spaces
 * @param shipAmount the amount of this type of ship available in the game
 * @see ShipType
 */
@Builder
public record ShipConfiguration(ShipType shipType, int shipSize, int shipAmount) {
}
