package ua.kostenko.battleship.battleship.web.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object (DTO) for the available game editions in the Battleship game.
 * <p>
 * The ResponseAvailableGameEditionsDto class is used to transfer data related to the available game editions.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseAvailableGameEditionsDto {

    /**
     * The list of available game editions.
     */
    private List<String> gameEditions;
}
