package ua.kostenko.battleship.battleship.logic.engine.models.enums;

/**
 * Enumeration representing the possible results of a shot in the Battleship game.
 * <p>
 * The result of each shot can be one of the following:
 * <ul>
 *     <li>{@link #MISS} - The shot did not hit any part of a ship.</li>
 *     <li>{@link #HIT} - The shot hit a ship but did not destroy it completely.</li>
 *     <li>{@link #DESTROYED} - The shot resulted in the destruction of a ship.</li>
 * </ul>
 * </p>
 *
 * @see ua.kostenko.battleship.battleship.logic.engine.models.records.Ship
 */
public enum ShotResult {

    /**
     * Indicates that the shot missed and did not hit any part of a ship.
     */
    MISS,

    /**
     * Indicates that the shot hit a part of a ship but did not destroy it completely.
     */
    HIT,

    /**
     * Indicates that the shot destroyed a ship completely.
     */
    DESTROYED
}
