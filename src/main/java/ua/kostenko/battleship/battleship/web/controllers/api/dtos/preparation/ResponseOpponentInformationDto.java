package ua.kostenko.battleship.battleship.web.controllers.api.dtos.preparation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.OpponentInfo;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseOpponentInformationDto {
    private String playerName;
    private boolean ready;

    public static ResponseOpponentInformationDto from(OpponentInfo opponentInfo) {
        return ResponseOpponentInformationDto.builder()
                                             .playerName(opponentInfo.playerName())
                                             .ready(opponentInfo.isReady())
                                             .build();
    }
}
