package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Data Transfer Object (DTO) for indicating a ship has been removed in the Battleship game.
 * <p>
 * The ResponseShipRemovedDto class is used to transfer the status of a ship removal operation.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseShipRemovedDto {

    /**
     * Indicates whether the ship has been deleted.
     */
    private boolean deleted;

    /**
     * Creates a ResponseShipRemovedDto object from a string value.
     *
     * @param value the string value to convert to ResponseShipRemovedDto
     * @return the created ResponseShipRemovedDto object indicating whether the ship has been deleted
     */
    public static ResponseShipRemovedDto fromString(String value) {
        return new ResponseShipRemovedDto(StringUtils.isNotBlank(value));
    }
}
