package ua.kostenko.battleship.application.port;

import java.time.Instant;

public interface TimeSource {
    Instant now();
}
