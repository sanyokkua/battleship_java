package ua.kostenko.battleship.battleship.logic.api.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerUtils;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerDto {
    private String playerId;
    private String playerName;
    private CellDto[][] field;
    private Set<Ship> shipsNotOnTheField;
    private Set<Ship> allPlayerShips;
    @JsonProperty("isActive")
    private boolean isActive;
    @JsonProperty("isWinner")
    private boolean isWinner;
    @JsonProperty("isReady")
    private boolean isReady;

    public static PlayerDto of(Player player) {
        val playerField = player.getField();
        return PlayerDto.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getPlayerName())
                        .field(ControllerUtils.mapFieldToFieldDto(playerField.getField()))
                        .shipsNotOnTheField(player.getShipsNotOnTheField())
                        .allPlayerShips(player.getAllPlayerShips())
                        .isActive(player.isActive())
                        .isWinner(player.isWinner())
                        .isReady(player.isReady())
                        .build();
    }
}
