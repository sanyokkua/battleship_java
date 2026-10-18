package ua.kostenko.battleship.app.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.application.port.TimeSource;

/**
 * Counts calls per (operation class, key) in a fixed 60-second window that starts at the key's first call and ends
 * inclusively: at {@code start + 60 s} the counter has already reset (the same boundary as the game expiry, T020).
 *
 * <p>Bounded by construction (R41): the table has a hard cap and, when full, drops the key unused for longest instead
 * of refusing a caller it has not seen, so a flood of invented keys cannot lock a real player out. Time comes only
 * from the injected {@link TimeSource}.
 */
@Component
public final class FixedWindowBuckets {
    static final int DEFAULT_CAP = 10_000;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final TimeSource time;
    private final int cap;
    private final Map<String, Bucket> table = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public FixedWindowBuckets(TimeSource time) {
        this(time, DEFAULT_CAP);
    }

    FixedWindowBuckets(TimeSource time, int cap) {
        if (cap < 1) {
            throw new IllegalArgumentException("cap must be at least 1");
        }
        this.time = Objects.requireNonNull(time);
        this.cap = cap;
    }

    private static final class Bucket {
        private final Instant windowEnd;
        private int count;

        private Bucket(Instant windowEnd) {
            this.windowEnd = windowEnd;
        }
    }

    /**
     * Counts one call and answers 0 when it is within {@code limit}, or the whole seconds (at least 1, rounded up)
     * left in the window when it is not.
     */
    public synchronized long acquire(String operationClass, String key, int limit) {
        Instant now = time.now();
        String id = operationClass + '\u0000' + key;
        Bucket bucket = table.get(id);
        if (bucket == null || !now.isBefore(bucket.windowEnd)) {
            if (bucket == null && table.size() >= cap) {
                Iterator<Bucket> eldest = table.values().iterator();
                eldest.next();
                eldest.remove();
            }
            bucket = new Bucket(now.plus(WINDOW));
            table.put(id, bucket);
        }
        if (bucket.count < limit) {
            bucket.count++;
            return 0;
        }
        Duration left = Duration.between(now, bucket.windowEnd);
        return Math.max(1, left.getSeconds() + (left.getNano() > 0 ? 1 : 0));
    }

    /** Removes every bucket whose window has ended at {@code now}; a pruned key simply starts a fresh window. */
    public synchronized void prune(Instant now) {
        table.values().removeIf(bucket -> !now.isBefore(bucket.windowEnd));
    }

    synchronized int size() {
        return table.size();
    }
}
