package ua.kostenko.battleship.battleship.engine.models.records;

public record Coordinate(int row, int column) {
    public static Coordinate of(int row, int column) {
        return new Coordinate(row, column);
    }
}
