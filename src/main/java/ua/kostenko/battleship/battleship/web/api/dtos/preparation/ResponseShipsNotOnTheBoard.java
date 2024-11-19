package ua.kostenko.battleship.battleship.web.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.web.api.dtos.entities.ShipDto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data Transfer Object (DTO) for the ships not placed on the board in the Battleship game.
 * <p>
 * The ResponseShipsNotOnTheBoard class is used to transfer data related to the ships that are not yet placed on the game board.
 * </p>
 *
 * @see Ship
 * @see ShipDto
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResponseShipsNotOnTheBoard {

    /**
     * The set of ships not placed on the board.
     */
    private Set<ShipDto> ships;

    /**
     * Creates a ResponseShipsNotOnTheBoard object from a list of Ship objects.
     *
     * @param ships the list of Ship objects to convert to ResponseShipsNotOnTheBoard
     * @return the created ResponseShipsNotOnTheBoard object
     */
    public static ResponseShipsNotOnTheBoard fromList(List<Ship> ships) {
        return new ResponseShipsNotOnTheBoard(ships.stream()
                .map(ShipDto::of)
                .collect(Collectors.toSet()));
    }
}
