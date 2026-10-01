package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * Admits a live-update subscription and keeps each seat's {@code connected} flag (R22, R31). Under the game lock it
 * checks membership and expiry, records the stream and captures the state; after unlocking it projects. Opening a
 * seat's first stream and closing its last each bump the version once, and the opponent's changed view is returned
 * for delivery; a newer stream replacing an older one changes neither. Opening a stream moves no deadline.
 */
public final class SubscribeUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final Duration resultRetention;
    private final AtomicLong streamNumbers = new AtomicLong();

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

    /** Opens a stream; the number is taken under the lock, so for one seat a higher number is a newer stream. */
    public Subscription open(String gameId, String sessionValue) {
        String digest = sessionValue == null ? null : SessionRegistry.digest(sessionValue);
        Captured captured;
        try {
            captured = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                Seat seat = ExpiryPolicy.authorize(slot, digest, now, resultRetention, sessions);
                long stream = streamNumbers.incrementAndGet();
                boolean connecting = slot.connectedStreams().put(seat, stream) == null;
                if (connecting) slot.replace(slot.state().withBumpedVersion());
                return new Captured(
                        slot.state(),
                        seat,
                        stream,
                        slot.contextFor(seat, now),
                        connecting ? opponentContext(slot, seat, now) : null);
            });
        } catch (UnknownGameException unknown) {
            throw ExpiryPolicy.unavailable();
        }
        return new Subscription(
                captured.seat(),
                captured.stream(),
                projector.project(captured.state(), captured.seat(), captured.context()),
                opponentView(captured));
    }

    /**
     * Closes the seat's stream {@code stream}. Only the stream that marks the seat connected disconnects it, so closing
     * a replaced stream changes nothing; a game that is gone or no longer live is not bumped.
     *
     * @return the opponent's changed view, or null when there is none to deliver
     */
    public SnapshotView close(String gameId, Seat seat, long stream) {
        Captured captured;
        try {
            captured = games.withSlot(gameId, slot -> {
                if (!slot.connectedStreams().remove(seat, stream)) return null;
                Instant now = time.now();
                if (ExpiryPolicy.status(slot, now, resultRetention) != ExpiryPolicy.Status.LIVE) return null;
                slot.replace(slot.state().withBumpedVersion());
                return new Captured(slot.state(), seat, stream, null, opponentContext(slot, seat, now));
            });
        } catch (UnknownGameException removed) {
            return null;
        }
        return captured == null ? null : opponentView(captured);
    }

    /** The opponent's context when it is still a player of this game, else null. */
    private static SnapshotContext opponentContext(GameSlot slot, Seat seat, Instant now) {
        Seat opponent = seat.opponent();
        String digest = opponent == Seat.HOST ? slot.hostSessionDigest() : slot.guestSessionDigest();
        return digest == null ? null : slot.contextFor(opponent, now);
    }

    private SnapshotView opponentView(Captured captured) {
        return captured.opponentContext() == null
                ? null
                : projector.project(captured.state(), captured.seat().opponent(), captured.opponentContext());
    }

    /**
     * The subscriber's seat and stream number, the snapshot captured under the lock to be sent first, and the
     * opponent's changed view when this stream connected the seat (else null).
     */
    public record Subscription(Seat seat, long stream, SnapshotView first, SnapshotView opponent) {}

    private record Captured(
            GameState state, Seat seat, long stream, SnapshotContext context, SnapshotContext opponentContext) {}
}
