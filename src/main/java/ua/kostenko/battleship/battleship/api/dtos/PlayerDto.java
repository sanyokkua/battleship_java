package ua.kostenko.battleship.battleship.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.engine.Field;
import ua.kostenko.battleship.battleship.engine.models.Player;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerDto {
    private String playerId;
    private String playerName;
    private Field field;
    private Set<Ship> shipsNotOnTheField;
    private Set<Ship> allPlayerShips;
    private boolean isActive;
    private boolean isWinner;
    private boolean isReady;

    public static PlayerDto of(Player player) {
        return PlayerDto.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getPlayerName())
                        .field(player.getField())
                        .shipsNotOnTheField(player.getShipsNotOnTheField())
                        .allPlayerShips(player.getAllPlayerShips())
                        .isActive(player.isActive())
                        .isWinner(player.isWinner())
                        .isReady(player.isReady())
                        .build();
    }
}
