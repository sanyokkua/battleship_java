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

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResponseShipsNotOnTheBoard {
    private Set<ShipDto> ships;

    public static ResponseShipsNotOnTheBoard fromList(List<Ship> ships) {
        return new ResponseShipsNotOnTheBoard(ships.stream()
                                                   .map(ShipDto::of)
                                                   .collect(Collectors.toSet()));
    }
}
