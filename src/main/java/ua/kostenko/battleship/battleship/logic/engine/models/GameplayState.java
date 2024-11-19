package ua.kostenko.battleship.battleship.logic.engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Cell;

/**
 * Class representing the state of gameplay in the Battleship game.
 * <p>
 * The GameplayState class captures the current state of both the player and opponent, including their fields,
 * the number of alive cells and ships, and the active status. It also tracks if there's a winner and the name of the winning player.
 * </p>
 *
 * @see Cell
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameplayState {

    /**
     * The name of the player.
     */
    private String playerName;

    /**
     * Indicates if the player is currently active.
     */
    private boolean isPlayerActive;

    /**
     * Indicates if the player has won the game.
     */
    private boolean isPlayerWinner;

    /**
     * The number of alive cells for the player.
     */
    private int playerNumberOfAliveCells;

    /**
     * The number of alive ships for the player.
     */
    private int playerNumberOfAliveShips;

    /**
     * The player's field represented as a 2D array of cells.
     */
    private Cell[][] playerField;

    /**
     * The name of the opponent.
     */
    private String opponentName;

    /**
     * Indicates if the opponent is ready to play.
     */
    private boolean isOpponentReady;

    /**
     * The number of alive cells for the opponent.
     */
    private int opponentNumberOfAliveCells;

    /**
     * The number of alive ships for the opponent.
     */
    private int opponentNumberOfAliveShips;

    /**
     * The opponent's field represented as a 2D array of cells.
     */
    private Cell[][] opponentField;

    /**
     * Indicates if there is a winner in the game.
     */
    private boolean hasWinner;

    /**
     * The name of the winning player, if any.
     */
    private String winnerPlayerName;
}
