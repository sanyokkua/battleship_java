package ua.kostenko.battleship.battleship.logic.engine.models.enums;

/**
 * Enumeration representing the different types of ships in the Battleship game.
 * <p>
 * Each ship type has unique characteristics and plays a specific role in the game:
 * <ul>
 *     <li>{@link #PATROL_BOAT} - A small and nimble ship, usually occupying 2 grid spaces.</li>
 *     <li>{@link #SUBMARINE} - A stealthy vessel, typically occupying 3 grid spaces.</li>
 *     <li>{@link #DESTROYER} - A medium-sized warship, generally occupying 3 grid spaces.</li>
 *     <li>{@link #BATTLESHIP} - A large and powerful ship, often occupying 4 grid spaces.</li>
 *     <li>{@link #CARRIER} - The largest ship, occupying 5 grid spaces.</li>
 * </ul>
 * </p>
 *
 * @see ua.kostenko.battleship.battleship.logic.engine.models.records.Ship
 */
public enum ShipType {

    /**
     * A small and agile ship, typically occupying 2 grid spaces.
     */
    PATROL_BOAT,

    /**
     * A stealthy vessel, usually occupying 3 grid spaces.
     */
    SUBMARINE,

    /**
     * A medium-sized warship, generally occupying 3 grid spaces.
     */
    DESTROYER,

    /**
     * A large and powerful ship, often occupying 4 grid spaces.
     */
    BATTLESHIP,

    /**
     * The largest ship in the fleet, occupying 5 grid spaces.
     */
    CARRIER
}
