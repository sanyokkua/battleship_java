package ua.kostenko.battleship.app.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.application.port.TimeSource;

@Component
public final class SystemTimeSource implements TimeSource {
    private final Clock clock;

    @Autowired
    public SystemTimeSource() {
        this(Clock.systemUTC());
    }

    SystemTimeSource(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
