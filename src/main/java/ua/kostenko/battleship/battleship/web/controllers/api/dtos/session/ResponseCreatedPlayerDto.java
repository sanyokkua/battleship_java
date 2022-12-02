package ua.kostenko.battleship.battleship.web.controllers.api.dtos.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseCreatedPlayerDto {
    private String playerId;
    private String playerName;
}
