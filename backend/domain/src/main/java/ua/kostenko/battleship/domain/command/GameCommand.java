package ua.kostenko.battleship.domain.command;

import java.util.Objects;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;

public sealed interface GameCommand permits GameCommand.PlaceShip, GameCommand.RemoveShip {
    record PlaceShip(String shipId, Coordinate anchor, Orientation orientation) implements GameCommand {
        public PlaceShip {
            Objects.requireNonNull(shipId, "shipId");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(orientation, "orientation");
        }
    }

    record RemoveShip(String shipId) implements GameCommand {
        public RemoveShip {
            Objects.requireNonNull(shipId, "shipId");
        }
    }
}
