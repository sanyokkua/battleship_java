package ua.kostenko.battleship.application.projection;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.Outcome;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.rules.AllowedActions.Action;

public record SnapshotView(
        String gameId,
        long version,
        Instant serverTime,
        String rulesetId,
        Phase phase,
        PlayerView you,
        PlayerView opponent,
        Side turn,
        Set<Action> allowedActions,
        Instant expiresAt,
        String invitationUrl,
        Instant invitationExpiresAt,
        BoardView yourBoard,
        List<Coordinate> placementBlockedCells,
        BoardView opponentBoard,
        ShotView lastShot,
        OutcomeView outcome,
        StatisticsView statistics) {
    public SnapshotView {
        allowedActions = Set.copyOf(allowedActions);
        if (placementBlockedCells != null) placementBlockedCells = List.copyOf(placementBlockedCells);
    }

    public enum Side {
        YOU,
        OPPONENT
    }

    public record OutcomeView(Side winner, Outcome.Reason reason) {}
}
