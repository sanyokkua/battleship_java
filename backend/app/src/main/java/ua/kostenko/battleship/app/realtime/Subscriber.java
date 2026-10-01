package ua.kostenko.battleship.app.realtime;

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
 * strictly newer versions of its own seat's view, so event ids never regress. A deliberate close is written last,
 * after anything already queued. The monitor guards only these fields; no write happens while holding it.
 */
final class Subscriber {
    private final String gameId;
    private final SseEmitter emitter;
    private final Map<Seat, SnapshotView> pendingBySeat = new EnumMap<>(Seat.class);
    private final Set<Seat> unavailableWhilePending = EnumSet.noneOf(Seat.class);
    private Seat seat;
    private long stream;
    private long lastVersion = Long.MIN_VALUE;
    private SnapshotView first;
    private SnapshotView latest;
    private StreamClosed.ReasonEnum closing;
    private boolean draining;
    private boolean closed;

    Subscriber(String gameId, SseEmitter emitter) {
        this.gameId = gameId;
        this.emitter = emitter;
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

    /** The next view to send, or null when none is queued. */
    synchronized SnapshotView next() {
        SnapshotView next;
        if (first != null) {
            next = first;
            first = null;
        } else {
            next = latest;
            latest = null;
        }
        return next;
    }

    /**
     * Called when no view is queued: returns the deliberate-close reason to write last, keeping the claim, or null,
     * which ends the claim taken by {@link #startDrain()}.
     */
    synchronized StreamClosed.ReasonEnum endDrain() {
        if (closing == null) draining = false;
        return closing;
    }

    /** Marks the subscriber closed; true exactly once, so its permit is released exactly once. */
    synchronized boolean close() {
        if (closed) return false;
        closed = true;
        return true;
    }
}
