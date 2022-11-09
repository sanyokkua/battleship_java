package ua.kostenko.battleship.battleship.engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import ua.kostenko.battleship.battleship.engine.Field;
import ua.kostenko.battleship.battleship.engine.models.records.Ship;

import java.util.Set;

@Builder
@Data
@AllArgsConstructor
public class Player {

    @NonNull
    private final String playerId;
    @NonNull
    private final String playerName;
    @NonNull
    private final Field field;
    @NonNull
    private final Set<Ship> shipsNotOnTheField;
    @NonNull
    private final Set<Ship> allPlayerShips;

    @Builder.Default
    private boolean isActive = false;
    @Builder.Default
    private boolean isWinner = false;
    @Builder.Default
    private boolean isReady = false;
}
