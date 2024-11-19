package ua.kostenko.battleship.battleship.logic.engine.models;

/**
 * Record representing the opponent's information in the Battleship game.
 * <p>
 * The OpponentInfo record encapsulates the opponent's name and their readiness status.
 * </p>
 *
 * @param playerName the name of the opponent
 * @param isReady    indicates if the opponent is ready to play
 */
public record OpponentInfo(String playerName, boolean isReady) {
}
