package ua.kostenko.battleship.battleship.web.controllers.api.dtos.gameplay;

import lombok.*;
import ua.kostenko.battleship.battleship.logic.api.ControllerUtils;
import ua.kostenko.battleship.battleship.logic.api.dtos.CellDto;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseGameplayStateDto {
    private String playerName;
    private boolean isPlayerActive;
    private boolean isPlayerWinner;
    private int playerNumberOfAliveCells;
    private int playerNumberOfAliveShips;
    private CellDto[][] playerField;

    private String opponentName;
    private boolean isOpponentReady;
    private int opponentNumberOfAliveCells;
    private int opponentNumberOfAliveShips;
    private CellDto[][] opponentField;

    private boolean hasWinner;
    private String winnerPlayerName;

    public static ResponseGameplayStateDto from(GameplayState gameplayState) {
        val playerField = ControllerUtils.mapFieldToFieldDto(gameplayState.getPlayerField());
        val opponentField = ControllerUtils.mapFieldToFieldDto(gameplayState.getOpponentField());
        return ResponseGameplayStateDto.builder()
                                       .playerName(gameplayState.getPlayerName())
                                       .isPlayerActive(gameplayState.isPlayerActive())
                                       .isPlayerWinner(gameplayState.isPlayerWinner())
                                       .playerNumberOfAliveCells(gameplayState.getPlayerNumberOfAliveCells())
                                       .playerNumberOfAliveShips(gameplayState.getPlayerNumberOfAliveShips())
                                       .playerField(playerField)
                                       .opponentName(gameplayState.getOpponentName())
                                       .isOpponentReady(gameplayState.isOpponentReady())
                                       .opponentNumberOfAliveCells(gameplayState.getOpponentNumberOfAliveCells())
                                       .opponentNumberOfAliveShips(gameplayState.getOpponentNumberOfAliveShips())
                                       .opponentField(opponentField)
                                       .hasWinner(gameplayState.isHasWinner())
                                       .winnerPlayerName(gameplayState.getWinnerPlayerName())
                                       .build();
    }
}
