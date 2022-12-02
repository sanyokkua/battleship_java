package ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseShipAddedDto {
    private String shipId;

    public static ResponseShipAddedDto fromId(String id) {
        return new ResponseShipAddedDto(id);
    }
}
