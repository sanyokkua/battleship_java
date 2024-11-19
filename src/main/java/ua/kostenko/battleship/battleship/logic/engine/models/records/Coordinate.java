package ua.kostenko.battleship.battleship.logic.engine.models.records;

/**
 * Record representing a coordinate on the Battleship game board.
 * <p>
 * A Coordinate is defined by its row and column values, representing a specific location
 * on the game board.
 * </p>
 *
 * @param row    the row value of the coordinate
 * @param column the column value of the coordinate
 */
public record Coordinate(int row, int column) {

    /**
     * Creates a new Coordinate instance with the specified row and column values.
     * <p>
     * This is a convenience method for creating coordinates.
     * </p>
     *
     * @param row    the row value of the coordinate
     * @param column the column value of the coordinate
     * @return a new Coordinate instance
     */
    public static Coordinate of(int row, int column) {
        return new Coordinate(row, column);
    }
}
