package ua.kostenko.battleship.battleship.logic.engine.models.records;

import lombok.Builder;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;

/**
 * Record representing a Ship in the Battleship game.
 * <p>
 * A Ship has a unique identifier, type, direction, and size. This record is used to
 * encapsulate all necessary information about a ship for game logic purposes.
 * </p>
 *
 * @param shipId        the unique identifier for the ship
 * @param shipType      the type of the ship, defined by {@link ShipType}
 * @param shipDirection the direction in which the ship is placed on the board, defined by {@link ShipDirection}
 * @param shipSize      the size of the ship in grid spaces
 * @see ShipType
 * @see ShipDirection
 */
@Builder
public record Ship(String shipId, ShipType shipType, ShipDirection shipDirection, int shipSize) {
}
