package ua.kostenko.battleship.domain.command;

import java.util.Objects;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Orientation;

public sealed interface GameCommand
        permits GameCommand.PlaceShip,
                GameCommand.RemoveShip,
                GameCommand.PlaceFleetRandomly,
                GameCommand.ClearFleet,
                GameCommand.Ready,
                GameCommand.Fire,
                GameCommand.Resign {
    record PlaceShip(String shipId, Coordinate anchor, Orientation orientation) implements GameCommand {
        public PlaceShip {
            Objects.requireNonNull(shipId, "shipId");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(orientation, "orientation");
        }
    }

    record PlaceFleetRandomly(int attemptLimit) implements GameCommand {
        public PlaceFleetRandomly {
            if (attemptLimit < 1) throw new IllegalArgumentException("attemptLimit must be positive");
        }
    }

    record Fire(Coordinate target) implements GameCommand {
        public Fire {
            Objects.requireNonNull(target, "target");
        }
    }

    record Resign() implements GameCommand {}

    record Ready() implements GameCommand {}

    record ClearFleet() implements GameCommand {}

    record RemoveShip(String shipId) implements GameCommand {
        public RemoveShip {
            Objects.requireNonNull(shipId, "shipId");
        }
    }
}
