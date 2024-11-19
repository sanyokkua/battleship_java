package ua.kostenko.battleship.battleship.logic.engine.models.enums;

/**
 * Enumeration representing the various stages of the Battleship game.
 * <p>
 * The game progresses through these stages sequentially:
 * <ol>
 *     <li>{@link #INITIALIZED} - The game engine is set up but no players have joined yet.</li>
 *     <li>{@link #WAITING_FOR_PLAYERS} - The game is waiting for players to join.</li>
 *     <li>{@link #PREPARATION} - Players are setting up their boards and placing their ships.</li>
 *     <li>{@link #IN_GAME} - The game is actively being played.</li>
 *     <li>{@link #FINISHED} - The game has ended, either because of a win, loss, or draw.</li>
 * </ol>
 * </p>
 *
 * @see ua.kostenko.battleship.battleship.logic.engine.models.records.GameState
 */
public enum GameStage {

    /**
     * The initial stage of the game engine, before any players have joined.
     */
    INITIALIZED,

    /**
     * The stage where the game is waiting for players to join.
     */
    WAITING_FOR_PLAYERS,

    /**
     * The preparation stage where players place their ships on the board.
     */
    PREPARATION,

    /**
     * The active stage where the game is being played.
     */
    IN_GAME,

    /**
     * The final stage of the game, after it has concluded.
     */
    FINISHED
}
