package ua.kostenko.battleship.battleship.web.api.dtos.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipDto {
    private String shipId;
    private int shipSize;

    public static ShipDto of(Ship ship) {
        return ShipDto.builder()
                      .shipId(ship.shipId())
                      .shipSize(ship.shipSize())
                      .build();
    }
}
