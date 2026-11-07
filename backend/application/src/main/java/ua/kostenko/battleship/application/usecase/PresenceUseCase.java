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
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.rules.AllowedActions;

/**
 * A player's presence signal: extends the idle deadline at most once per interval, per seat. Only while the contract
 * allows it ({@code SEND_PRESENCE} in the caller's allowed actions); a finished or abandoned game answers
 * {@code action-not-allowed}.
 */
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
                if (!AllowedActions.of(slot.state(), seat).contains(AllowedActions.Action.SEND_PRESENCE))
                    throw new ApplicationFailure("action-not-allowed", null, null, null);
                Instant notBefore = slot.presenceNotBefore().get(seat);
                if (notBefore == null || ExpiryPolicy.reached(notBefore, now)) {
                    slot.idleDeadline(now.plus(idleTimeout));
                    slot.presenceNotBefore().put(seat, now.plus(presenceInterval));
                }
                return new Captured(slot.state(), seat, slot.contextFor(seat, now));
            });
        } catch (UnknownGameException _) {
            throw ExpiryPolicy.unavailable();
        }
        return projector.project(captured.state(), captured.seat(), captured.context());
    }

    private record Captured(GameState state, Seat seat, SnapshotContext context) {}
}
