package ua.kostenko.battleship.battleship.logic.engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import ua.kostenko.battleship.battleship.logic.engine.FieldManagement;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;

import java.util.Set;

/**
 * Class representing a player in the Battleship game.
 * <p>
 * The Player class contains all the necessary information about a player, including their ID, name,
 * field management details, and the ships they possess.
 * </p>
 *
 * @see FieldManagement
 * @see Ship
 */
@Builder
@Data
@AllArgsConstructor
public class Player {

    /**
     * The unique identifier for the player.
     */
    @NonNull
    private final String playerId;

    /**
     * The name of the player.
     */
    @NonNull
    private final String playerName;

    /**
     * The field management for the player's game field.
     */
    @NonNull
    private final FieldManagement fieldManagement;

    /**
     * The set of ships that the player has not yet placed on the field.
     */
    @NonNull
    private final Set<Ship> shipsNotOnTheField;

    /**
     * The set of all ships that the player owns.
     */
    @NonNull
    private final Set<Ship> allPlayerShips;

    /**
     * Indicates if the player is currently active.
     */
    @Builder.Default
    private boolean isActive = false;

    /**
     * Indicates if the player is the winner of the game.
     */
    @Builder.Default
    private boolean isWinner = false;

    /**
     * Indicates if the player is ready to start the game.
     */
    @Builder.Default
    private boolean isReady = false;
}
