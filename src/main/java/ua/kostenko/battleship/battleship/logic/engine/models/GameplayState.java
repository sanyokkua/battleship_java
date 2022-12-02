package ua.kostenko.battleship.battleship.logic.engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameplayState {
    private String playerName;
    private boolean isPlayerActive;
    private boolean isPlayerWinner;
    private int playerNumberOfAliveCells;
    private int playerNumberOfAliveShips;
    private Cell[][] playerField;

    private String opponentName;
    private boolean isOpponentReady;
    private int opponentNumberOfAliveCells;
    private int opponentNumberOfAliveShips;
    private Cell[][] opponentField;

    private boolean hasWinner;
    private String winnerPlayerName;
}
