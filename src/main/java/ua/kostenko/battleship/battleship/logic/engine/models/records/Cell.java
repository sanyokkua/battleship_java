package ua.kostenko.battleship.battleship.logic.engine.models.records;

import lombok.Builder;

import java.util.Optional;

@Builder
public record Cell(Coordinate coordinate, Ship ship, boolean hasShot, boolean isAvailable) {

    public Optional<String> optionalShipId() {
        return Optional.ofNullable(this.ship)
                       .map(Ship::shipId);
    }

    public boolean hasShip() {
        return optionalShipId().isPresent();
    }
}
