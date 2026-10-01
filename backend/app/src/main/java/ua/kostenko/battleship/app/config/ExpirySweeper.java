package ua.kostenko.battleship.app.config;

import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.app.security.FixedWindowBuckets;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.usecase.ExpireGamesUseCase;

/** Scheduling adapter: reclaims memory (games and stale rate-limit buckets) on a timer; what requests are told never depends on it. */
@Component
public final class ExpirySweeper {
    private final ExpireGamesUseCase expire;
    private final TimeSource time;
    private final FixedWindowBuckets buckets;

    public ExpirySweeper(ExpireGamesUseCase expire, TimeSource time, FixedWindowBuckets buckets) {
        this.expire = Objects.requireNonNull(expire);
        this.time = Objects.requireNonNull(time);
        this.buckets = Objects.requireNonNull(buckets);
    }

    @Scheduled(
            fixedDelayString = "${battleship.sweep-interval-seconds}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    void scheduled() {
        tick();
    }

    void tick() {
        Instant now = time.now();
        expire.sweep(now);
        buckets.prune(now);
    }
}
