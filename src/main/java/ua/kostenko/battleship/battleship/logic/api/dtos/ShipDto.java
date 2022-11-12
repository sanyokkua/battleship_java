package ua.kostenko.battleship.battleship.logic.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipDirection;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.ShipType;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShipDto {
    private String shipId;
    private ShipType shipType;
    private ShipDirection shipDirection;
    private int shipSize;

    public static ShipDto of(Ship ship) {
        return ShipDto.builder()
                      .shipId(ship.shipId())
                      .shipType(ship.shipType())
                      .shipDirection(ship.shipDirection())
                      .shipSize(ship.shipSize())
                      .build();
    }
}
