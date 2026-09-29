package ua.kostenko.battleship.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ua.kostenko.battleship.application.port.TimeSource;

public final class MutableTimeSource implements TimeSource {
    private Instant current;

    public MutableTimeSource(Instant initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    public void set(Instant instant) {
        current = Objects.requireNonNull(instant, "instant");
    }

    public void advance(Duration duration) {
        current = current.plus(Objects.requireNonNull(duration, "duration"));
    }

    @Override
    public Instant now() {
        return current;
    }
}
