package ua.kostenko.battleship.application.projection;

import java.util.List;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Ship.ShipStatus;

public record BoardView(List<List<CellState>> grid, List<ShipView> ships) {
    public BoardView {
        grid = grid.stream().map(List::copyOf).toList();
        ships = List.copyOf(ships);
    }

    public enum CellState {
        UNKNOWN,
        WATER,
        SHIP,
        MISS,
        HIT,
        SUNK,
        REVEALED_WATER
    }

    public record ShipView(
            String shipId,
            String shipTypeId,
            int length,
            List<Coordinate> cells,
            Coordinate anchor,
            Orientation orientation,
            ShipStatus status) {
        public ShipView {
            cells = List.copyOf(cells);
        }
    }
}
