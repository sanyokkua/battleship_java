package ua.kostenko.battleship.app.realtime;

import org.springframework.context.ApplicationContext;

/** Lets a test in another package drive one heartbeat round, the way {@code EventStreamIT} does from inside the package. */
public final class StreamProbe {
    private StreamProbe() {}

    public static void heartbeat(ApplicationContext context) {
        context.getBean(HeartbeatScheduler.class).tick();
    }
}
