package ua.kostenko.battleship.battleship.logic.api.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameplayStateDto {
    private String playerName;
    private String opponentName;
    @JsonProperty("isPlayerActive")
    private boolean isPlayerActive;
    @JsonProperty("isOpponentReady")
    private boolean isOpponentReady;
    private int playerNumberOfAliveCells;
    private int playerNumberOfAliveShips;
    private int opponentNumberOfAliveCells;
    private int opponentNumberOfAliveShips;
    private CellDto[][] playerField;
    private CellDto[][] opponentField;
    private boolean hasWinner;
}
