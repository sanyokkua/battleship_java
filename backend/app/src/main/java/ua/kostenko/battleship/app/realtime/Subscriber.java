package ua.kostenko.battleship.app.realtime;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * One open stream: transport delivery state only, never game authority. It is installed before its seat is known
 * (pending), buffers what is published meanwhile, and once activated sends the captured view first and then only
 * strictly newer versions of its own seat's view, so event ids never regress. The monitor guards only these fields;
 * no write happens while holding it.
 */
final class Subscriber {
    private final String gameId;
    private final SseEmitter emitter;
    private final Map<Seat, SnapshotView> pendingBySeat = new EnumMap<>(Seat.class);
    private Seat seat;
    private long lastVersion = Long.MIN_VALUE;
    private SnapshotView first;
    private SnapshotView latest;
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

    synchronized boolean isFor(Seat candidate) {
        return seat == candidate;
    }

    /** Takes a published view; true when it is now waiting to be sent. */
    synchronized boolean offer(Seat target, SnapshotView view) {
        if (closed) return false;
        if (seat == null) {
            pendingBySeat.merge(target, view, (kept, offered) -> offered.version() > kept.version() ? offered : kept);
            return false;
        }
        if (target != seat || view.version() <= lastVersion) return false;
        latest = view;
        lastVersion = view.version();
        return true;
    }

    /** Learns the seat and queues the captured view first, keeping a buffered view only if it is newer. */
    synchronized void activate(Seat admitted, SnapshotView captured) {
        seat = admitted;
        first = captured;
        lastVersion = captured.version();
        SnapshotView buffered = pendingBySeat.get(admitted);
        pendingBySeat.clear();
        if (buffered != null && buffered.version() > lastVersion) {
            latest = buffered;
            lastVersion = buffered.version();
        }
    }

    /** Claims the right to write; only one thread drains a subscriber at a time, so sends never interleave. */
    synchronized boolean startDrain() {
        if (draining || closed || seat == null) return false;
        draining = true;
        return true;
    }

    /** The next view to send, or null, which also ends the claim taken by {@link #startDrain()}. */
    synchronized SnapshotView next() {
        SnapshotView next;
        if (first != null) {
            next = first;
            first = null;
        } else {
            next = latest;
            latest = null;
        }
        if (next == null) draining = false;
        return next;
    }

    /** Marks the subscriber closed; true exactly once, so its permit is released exactly once. */
    synchronized boolean close() {
        if (closed) return false;
        closed = true;
        return true;
    }
}
