package ua.kostenko.battleship.battleship.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.engine.models.enums.Direction;
import ua.kostenko.battleship.battleship.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShipDto {
    private String shipId;
    private ShipType shipType;
    private Direction direction;
    private int shipSize;

    public static ShipDto of(Ship ship) {
        return ShipDto.builder()
                      .shipId(ship.shipId())
                      .shipType(ship.shipType())
                      .direction(ship.direction())
                      .shipSize(ship.shipSize())
                      .build();
    }
}
