package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;

/** A member's read of a game: the caller-relative snapshot, with no change to any deadline or to the version. */
public final class GetGameUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final Duration resultRetention;

    public GetGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            TimeSource time,
            SnapshotProjector projector,
            Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
        this.projector = Objects.requireNonNull(projector);
        if (resultRetention.isZero() || resultRetention.isNegative())
            throw new IllegalArgumentException("invalid result retention");
        this.resultRetention = resultRetention;
    }

    public SnapshotView execute(String gameId, String sessionValue) {
        String digest = sessionValue == null ? null : SessionRegistry.digest(sessionValue);
        Captured captured;
        try {
            captured = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                Seat seat = ExpiryPolicy.authorize(slot, digest, now, resultRetention, sessions);
                return new Captured(slot.state(), seat, slot.contextFor(seat, now));
            });
        } catch (UnknownGameException unknown) {
            throw ExpiryPolicy.unavailable();
        }
        return projector.project(captured.state(), captured.seat(), captured.context());
    }

    private record Captured(GameState state, Seat seat, SnapshotContext context) {}
}
