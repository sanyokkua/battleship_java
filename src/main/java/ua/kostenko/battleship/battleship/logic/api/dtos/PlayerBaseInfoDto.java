package ua.kostenko.battleship.battleship.logic.api.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerBaseInfoDto {
    private String playerName;
    @JsonProperty("isActive")
    private boolean isActive;
    @JsonProperty("isWinner")
    private boolean isWinner;
    @JsonProperty("isReady")
    private boolean isReady;

    public static PlayerBaseInfoDto of(final Player player) {
        return PlayerBaseInfoDto.builder()
                                .playerName(player.getPlayerName())
                                .isActive(player.isActive())
                                .isReady(player.isReady())
                                .isWinner(player.isWinner())
                                .build();
    }
}
