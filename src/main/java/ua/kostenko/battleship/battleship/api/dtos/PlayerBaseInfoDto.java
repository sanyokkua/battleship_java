package ua.kostenko.battleship.battleship.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.engine.models.Player;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerBaseInfoDto {
    private String playerName;
    private boolean isActive;
    private boolean isWinner;
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
