package ua.kostenko.battleship.battleship.logic.engine.models.enums;

/**
 * Enumeration representing the direction in which a ship can be placed on the game board.
 * <p>
 * Ships in the Battleship game can be placed either horizontally or vertically.
 * This enumeration helps to clearly define and use these directions in the game logic.
 * </p>
 *
 * @see ua.kostenko.battleship.battleship.logic.engine.models.records.Ship
 */
public enum ShipDirection {

    /**
     * Represents a ship placed horizontally on the game board.
     * <p>
     * This direction means the ship spans across multiple columns in the same row.
     * </p>
     */
    HORIZONTAL,

    /**
     * Represents a ship placed vertically on the game board.
     * <p>
     * This direction means the ship spans across multiple rows in the same column.
     * </p>
     */
    VERTICAL
}
