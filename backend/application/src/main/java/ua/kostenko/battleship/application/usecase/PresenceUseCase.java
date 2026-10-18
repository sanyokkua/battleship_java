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
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

/** A player's presence signal: extends the idle deadline at most once per interval, per seat. */
public final class PresenceUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final Duration idleTimeout;
    private final Duration presenceInterval;
    private final Duration resultRetention;

    public PresenceUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            TimeSource time,
            SnapshotProjector projector,
            Duration idleTimeout,
            Duration presenceInterval,
            Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
        this.projector = Objects.requireNonNull(projector);
        if (idleTimeout.isZero()
                || idleTimeout.isNegative()
                || presenceInterval.isZero()
                || presenceInterval.isNegative()
                || resultRetention.isZero()
                || resultRetention.isNegative()) throw new IllegalArgumentException("invalid presence lifetimes");
        this.idleTimeout = idleTimeout;
        this.presenceInterval = presenceInterval;
        this.resultRetention = resultRetention;
    }

    public SnapshotView execute(String gameId, String sessionValue) {
        String digest = sessionValue == null ? null : SessionRegistry.digest(sessionValue);
        Captured captured;
        try {
            captured = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                Seat seat = ExpiryPolicy.authorize(slot, digest, now, resultRetention, sessions);
                Phase phase = slot.state().phase();
                boolean over = phase == Phase.FINISHED || phase == Phase.ABANDONED;
                Instant notBefore = slot.presenceNotBefore().get(seat);
                if (!over && (notBefore == null || ExpiryPolicy.reached(notBefore, now))) {
                    slot.idleDeadline(now.plus(idleTimeout));
                    slot.presenceNotBefore().put(seat, now.plus(presenceInterval));
                }
                return new Captured(slot.state(), seat, slot.contextFor(seat, now));
            });
        } catch (UnknownGameException unknown) {
            throw ExpiryPolicy.unavailable();
        }
        return projector.project(captured.state(), captured.seat(), captured.context());
    }

    private record Captured(GameState state, Seat seat, SnapshotContext context) {}
}
