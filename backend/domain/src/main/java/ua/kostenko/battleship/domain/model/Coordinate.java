package ua.kostenko.battleship.domain.model;

public record Coordinate(int rowIndex, int columnIndex) {
  public Coordinate {
    if (rowIndex < 0 || columnIndex < 0) {
      throw new IllegalArgumentException("Coordinate indices must be non-negative");
    }
  }
}
