package ua.kostenko.battleship.app.realtime;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.application.port.TimeSource;

/**
 * Scheduling adapter: the one scheduler shared by every stream. Each round sends the keep-alive comment to streams
 * due one and ends streams that reached their maximum lifetime (R30); what is due is decided by the injected time.
 */
@Component
final class HeartbeatScheduler {
    private final SseHub hub;
    private final TimeSource time;

    HeartbeatScheduler(SseHub hub, TimeSource time) {
        this.hub = Objects.requireNonNull(hub);
        this.time = Objects.requireNonNull(time);
    }

    @Scheduled(fixedDelayString = "${battleship.heartbeat-seconds}", timeUnit = TimeUnit.SECONDS)
    void scheduled() {
        tick();
    }

    void tick() {
        hub.bound(time.now());
    }
}
