package ua.kostenko.battleship.app.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.app.web.dto.StreamClosed;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * One open stream: transport delivery state only, never game authority. It is installed before its seat is known
 * (pending), buffers what is published meanwhile, and once activated sends the captured view first and then only
 * strictly newer versions of its own seat's view, so event ids never regress. Unsent work is bounded: one slot holds
 * the latest unsent view, a newer one replacing it (R30, R41). A deliberate close is written last, after anything
 * already queued. The monitor guards only these fields; no write happens while holding it.
 */
final class Subscriber {
    private final String gameId;
    private final SseEmitter emitter;
    private final Instant openedAt;
    private final Map<Seat, SnapshotView> pendingBySeat = new EnumMap<>(Seat.class);
    private final Set<Seat> unavailableWhilePending = EnumSet.noneOf(Seat.class);
    private Seat seat;
    private long stream;
    private long lastVersion = Long.MIN_VALUE;
    private SnapshotView first;
    private SnapshotView latest;
    private Instant lastBeat;
    private boolean beat;
    private StreamClosed.ReasonEnum closing;
    private boolean draining;
    private boolean closed;

    /** What the drainer writes next: a snapshot view, a deliberate close, or, with neither, a keep-alive. */
    record Frame(SnapshotView view, StreamClosed.ReasonEnum closing) {}

    private static final Frame KEEP_ALIVE = new Frame(null, null);

    Subscriber(String gameId, SseEmitter emitter, Instant openedAt) {
        this.gameId = gameId;
        this.emitter = emitter;
        this.openedAt = openedAt;
        this.lastBeat = openedAt;
    }

    String gameId() {
        return gameId;
    }

    SseEmitter emitter() {
        return emitter;
    }

    synchronized Seat seat() {
        return seat;
    }

    synchronized long stream() {
        return stream;
    }

    /** True for an activated stream of {@code candidate} that is not already being closed. */
    synchronized boolean isOpenFor(Seat candidate) {
        return seat == candidate && closing == null && !closed;
    }

    /** Takes a published view; true when it is now waiting to be sent. */
    synchronized boolean offer(Seat target, SnapshotView view) {
        if (closed || closing != null) return false;
        if (seat == null) {
            pendingBySeat.merge(target, view, (kept, offered) -> offered.version() > kept.version() ? offered : kept);
            return false;
        }
        if (target != seat || view.version() <= lastVersion) return false;
        latest = view;
        lastVersion = view.version();
        return true;
    }

    /**
     * Learns the seat and stream and queues the captured view first, keeping a buffered view only if it is newer. A
     * game that stopped existing for this seat meanwhile closes the stream after that view. False, and nothing is
     * learnt, when the subscriber was already closed: the caller then owns the disconnection.
     */
    synchronized boolean activate(Seat admitted, long number, SnapshotView captured) {
        if (closed) return false;
        seat = admitted;
        stream = number;
        first = captured;
        lastVersion = captured.version();
        SnapshotView buffered = pendingBySeat.get(admitted);
        pendingBySeat.clear();
        if (buffered != null && buffered.version() > lastVersion) {
            latest = buffered;
            lastVersion = buffered.version();
        }
        if (unavailableWhilePending.contains(admitted)) closing = StreamClosed.ReasonEnum.GAME_UNAVAILABLE;
        return true;
    }

    /** The game stopped existing for {@code target}; true when this stream must now be closed for that reason. */
    synchronized boolean unavailable(Seat target) {
        if (closed || closing != null) return false;
        if (seat == null) {
            unavailableWhilePending.add(target);
            return false;
        }
        return target == seat && closeWith(StreamClosed.ReasonEnum.GAME_UNAVAILABLE);
    }

    /** Asks for a deliberate close; true the first time, when the caller must drain to write it. */
    synchronized boolean closeWith(StreamClosed.ReasonEnum reason) {
        if (closed || closing != null) return false;
        closing = reason;
        return true;
    }

    /** Claims the right to write; only one thread drains a subscriber at a time, so sends never interleave. */
    synchronized boolean startDrain() {
        if (draining || closed || seat == null) return false;
        draining = true;
        return true;
    }

    /**
     * The next frame to write: the captured view, then the latest unsent view, then a deliberate close, then a
     * keep-alive. Null when nothing is left, which ends the claim taken by {@link #startDrain()} in the same step, so
     * nothing offered meanwhile can be stranded.
     */
    synchronized Frame next() {
        if (first != null) {
            Frame next = new Frame(first, null);
            first = null;
            return next;
        }
        if (latest != null) {
            Frame next = new Frame(latest, null);
            latest = null;
            return next;
        }
        if (closing != null) return new Frame(null, closing);
        if (beat) {
            beat = false;
            return KEEP_ALIVE;
        }
        draining = false;
        return null;
    }

    /**
     * Asks for a keep-alive once {@code interval} has passed since the last one, or since opening; true when the
     * caller must drain to write it. A stream being closed gets none.
     */
    synchronized boolean beatDue(Instant now, Duration interval) {
        if (closed || closing != null || now.isBefore(lastBeat.plus(interval))) return false;
        lastBeat = now;
        beat = true;
        return true;
    }

    /** True from {@code openedAt + lifetime} on: the stream has reached its maximum lifetime. */
    boolean outlived(Instant now, Duration lifetime) {
        return !now.isBefore(openedAt.plus(lifetime));
    }

    /** Marks the subscriber closed; true exactly once, so its permit is released exactly once. */
    synchronized boolean close() {
        if (closed) return false;
        closed = true;
        return true;
    }
}
