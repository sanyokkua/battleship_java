package ua.kostenko.battleship.battleship.logic.engine.models.records;

import lombok.Builder;

import java.util.Optional;

/**
 * Record representing a cell on the Battleship game board.
 * <p>
 * A Cell is defined by its coordinate, the ship (if any) that occupies it, whether it has been shot at,
 * and whether it is available for placing a ship.
 * </p>
 *
 * @param coordinate  the coordinate of the cell on the game board
 * @param ship        the ship occupying the cell, if any
 * @param hasShot     indicates whether the cell has been shot at
 * @param isAvailable indicates whether the cell is available for placing a ship
 * @see Coordinate
 * @see Ship
 */
@Builder
public record Cell(Coordinate coordinate, Ship ship, boolean hasShot, boolean isAvailable) {

    /**
     * Retrieves the optional ship ID occupying the cell.
     *
     * @return an {@link Optional} containing the ship ID if a ship is present, or an empty {@link Optional} if not
     */
    public Optional<String> optionalShipId() {
        return Optional.ofNullable(this.ship).map(Ship::shipId);
    }

    /**
     * Checks if the cell has a ship occupying it.
     *
     * @return {@code true} if a ship is present, {@code false} otherwise
     */
    public boolean hasShip() {
        return optionalShipId().isPresent();
    }
}
