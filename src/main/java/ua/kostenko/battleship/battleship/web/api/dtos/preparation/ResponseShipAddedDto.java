package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for indicating a ship has been added in the Battleship game.
 * <p>
 * The ResponseShipAddedDto class is used to transfer the ID of the ship that has been added.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseShipAddedDto {

    /**
     * The unique identifier of the added ship.
     */
    private String shipId;

    /**
     * Creates a ResponseShipAddedDto object from a ship ID.
     *
     * @param id the ship ID to convert to ResponseShipAddedDto
     * @return the created ResponseShipAddedDto object
     */
    public static ResponseShipAddedDto fromId(String id) {
        return new ResponseShipAddedDto(id);
    }
}
