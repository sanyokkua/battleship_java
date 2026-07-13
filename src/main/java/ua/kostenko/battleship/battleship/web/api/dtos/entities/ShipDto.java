package ua.kostenko.battleship.battleship.web.api.dtos.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

/**
 * Data Transfer Object (DTO) for a ship in the Battleship game.
 * <p>
 * The ShipDto class is used to transfer data related to a ship, including its ID and size.
 * </p>
 *
 * @see Ship
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipDto {

    /**
     * The unique identifier of the ship.
     */
    @Schema(description = "Ship type identifier from the edition's ship catalog", example = "patrol_boat_1")
    private String shipId;

    /**
     * The size of the ship in grid spaces.
     */
    @Schema(description = "Size of the ship in grid spaces")
    private int shipSize;

    /**
     * Creates a ShipDto object from a Ship object.
     *
     * @param ship the Ship object to convert to ShipDto
     * @return the created ShipDto object
     */
    public static ShipDto of(Ship ship) {
        return ShipDto.builder()
                .shipId(ship.shipId())
                .shipSize(ship.shipSize())
                .build();
    }
}
