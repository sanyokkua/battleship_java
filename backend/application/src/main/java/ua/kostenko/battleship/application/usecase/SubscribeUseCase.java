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

/**
 * Admits a live-update subscription: under the game lock it checks membership and expiry and captures the state, and
 * after unlocking it projects the caller's first snapshot. Opening a stream moves no deadline and bumps no version.
 */
public final class SubscribeUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final Duration resultRetention;

    public SubscribeUseCase(
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

    public Subscription open(String gameId, String sessionValue) {
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
        return new Subscription(
                captured.seat(), projector.project(captured.state(), captured.seat(), captured.context()));
    }

    /** The subscriber's seat and the snapshot captured under the lock, to be sent as the first event. */
    public record Subscription(Seat seat, SnapshotView first) {}

    private record Captured(GameState state, Seat seat, SnapshotContext context) {}
}
