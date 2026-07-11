package ua.kostenko.battleship.battleship.web.api.dtos.gameplay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import ua.kostenko.battleship.battleship.logic.engine.models.GameplayState;
import ua.kostenko.battleship.battleship.web.api.ControllerUtils;
import ua.kostenko.battleship.battleship.web.api.dtos.entities.CellDto;

/**
 * Data Transfer Object (DTO) for the gameplay state in the Battleship game.
 * <p>
 * The ResponseGameplayStateDto class is used to transfer data related to the gameplay state,
 * including player and opponent details, field status, and winner information.
 * </p>
 *
 * @see GameplayState
 * @see CellDto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseGameplayStateDto {

    /**
     * The name of the player.
     */
    private String playerName;

    /**
     * Indicates whether the player is currently active.
     */
    @JsonProperty("isPlayerActive")
    private boolean playerActive;

    /**
     * Indicates whether the player is the winner.
     */
    @JsonProperty("isPlayerWinner")
    private boolean playerWinner;

    /**
     * The number of alive cells for the player.
     */
    private int playerNumberOfAliveCells;

    /**
     * The number of alive ships for the player.
     */
    private int playerNumberOfAliveShips;

    /**
     * The field of the player.
     */
    private CellDto[][] playerField;

    /**
     * The name of the opponent.
     */
    private String opponentName;

    /**
     * Indicates whether the opponent is ready.
     */
    @JsonProperty("isOpponentReady")
    private boolean opponentReady;

    /**
     * The number of alive cells for the opponent.
     */
    private int opponentNumberOfAliveCells;

    /**
     * The number of alive ships for the opponent.
     */
    private int opponentNumberOfAliveShips;

    /**
     * The field of the opponent.
     */
    private CellDto[][] opponentField;

    /**
     * Indicates whether there is a winner.
     */
    private boolean hasWinner;

    /**
     * The name of the winner player, if any.
     */
    private String winnerPlayerName;

    /**
     * Creates a ResponseGameplayStateDto object from a GameplayState object.
     *
     * @param gameplayState the GameplayState object to convert to ResponseGameplayStateDto
     * @return the created ResponseGameplayStateDto object
     */
    public static ResponseGameplayStateDto from(GameplayState gameplayState) {
        val playerField = ControllerUtils.mapFieldToFieldDto(gameplayState.getPlayerField());
        val opponentField = ControllerUtils.mapFieldToFieldDto(gameplayState.getOpponentField());
        return ResponseGameplayStateDto.builder()
                .playerName(gameplayState.getPlayerName())
                .playerActive(gameplayState.isPlayerActive())
                .playerWinner(gameplayState.isPlayerWinner())
                .playerNumberOfAliveCells(gameplayState.getPlayerNumberOfAliveCells())
                .playerNumberOfAliveShips(gameplayState.getPlayerNumberOfAliveShips())
                .playerField(playerField)
                .opponentName(gameplayState.getOpponentName())
                .opponentReady(gameplayState.isOpponentReady())
                .opponentNumberOfAliveCells(gameplayState.getOpponentNumberOfAliveCells())
                .opponentNumberOfAliveShips(gameplayState.getOpponentNumberOfAliveShips())
                .opponentField(opponentField)
                .hasWinner(gameplayState.isHasWinner())
                .winnerPlayerName(gameplayState.getWinnerPlayerName())
                .build();
    }
}
