package ua.kostenko.battleship.app.realtime;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.app.observability.CorrelationIdFilter;
import ua.kostenko.battleship.application.port.TimeSource;

/**
 * Scheduling adapter: the one scheduler shared by every stream. A round every second sends the keep-alive comment to
 * the streams due one at their own interval from opening and ends streams that reached their maximum lifetime (R30); what is due is decided by the injected time.
 */
@Component
final class HeartbeatScheduler {
    /** Coarse enough to be cheap, fine enough that a beat is at most this late; each stream keeps its own deadline. */
    static final long TICK_SECONDS = 1;

    private final SseHub hub;
    private final TimeSource time;

    HeartbeatScheduler(SseHub hub, TimeSource time) {
        this.hub = Objects.requireNonNull(hub);
        this.time = Objects.requireNonNull(time);
    }

    @Scheduled(fixedDelay = TICK_SECONDS, timeUnit = TimeUnit.SECONDS)
    void scheduled() {
        tick();
    }

    void tick() {
        CorrelationIdFilter.scoped(() -> hub.bound(time.now()));
    }
}
