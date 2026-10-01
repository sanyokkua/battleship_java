package ua.kostenko.battleship.app.config;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.usecase.ExpireGamesUseCase;

/** Scheduling adapter: reclaims memory on a timer; what requests are told never depends on it. */
@Component
public final class ExpirySweeper {
    private final ExpireGamesUseCase expire;
    private final TimeSource time;

    public ExpirySweeper(ExpireGamesUseCase expire, TimeSource time) {
        this.expire = Objects.requireNonNull(expire);
        this.time = Objects.requireNonNull(time);
    }

    @Scheduled(
            fixedDelayString = "${battleship.sweep-interval-seconds}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    void scheduled() {
        tick();
    }

    void tick() {
        expire.sweep(time.now());
    }
}
