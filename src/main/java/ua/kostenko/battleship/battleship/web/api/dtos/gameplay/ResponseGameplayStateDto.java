package ua.kostenko.battleship.battleship.web.api.dtos.gameplay;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Requesting player's display name")
    private String playerName;

    /**
     * Indicates whether the player is currently active.
     */
    @JsonProperty("isPlayerActive")
    @Schema(description = "Whether it is currently this player's turn")
    private boolean playerActive;

    /**
     * Indicates whether the player is the winner.
     */
    @JsonProperty("isPlayerWinner")
    @Schema(description = "Whether this player has won the game")
    private boolean playerWinner;

    /**
     * The number of alive cells for the player.
     */
    @Schema(description = "Number of the player's board cells still occupied by unsunk ships")
    private int playerNumberOfAliveCells;

    /**
     * The number of alive ships for the player.
     */
    @Schema(description = "Number of the player's ships not yet fully sunk")
    private int playerNumberOfAliveShips;

    /**
     * The field of the player.
     */
    @Schema(description = "Player's own board, as a 2D array of cells")
    private CellDto[][] playerField;

    /**
     * The name of the opponent.
     */
    @Schema(description = "Opponent's display name")
    private String opponentName;

    /**
     * Indicates whether the opponent is ready.
     */
    @JsonProperty("isOpponentReady")
    @Schema(description = "Whether the opponent has readied up")
    private boolean opponentReady;

    /**
     * The number of alive cells for the opponent.
     */
    @Schema(description = "Number of the opponent's board cells still occupied by unsunk ships")
    private int opponentNumberOfAliveCells;

    /**
     * The number of alive ships for the opponent.
     */
    @Schema(description = "Number of the opponent's ships not yet fully sunk")
    private int opponentNumberOfAliveShips;

    /**
     * The field of the opponent.
     */
    @Schema(description = "Opponent's board as visible to the requesting player, as a 2D array of cells")
    private CellDto[][] opponentField;

    /**
     * Indicates whether there is a winner.
     */
    @Schema(description = "Whether the game has a winner")
    private boolean hasWinner;

    /**
     * The name of the winner player, if any.
     */
    @Schema(description = "Winning player's display name, if any")
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
