package ua.kostenko.battleship.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.kostenko.battleship.app.realtime.SseHub;

/**
 * The service's own part of an orderly shutdown (R59), in the order it happens:
 * <ol>
 *   <li>Closing the context makes Boot publish readiness {@code REFUSING_TRAFFIC} before anything else is stopped;
 *       {@link #onReadiness} mirrors it at once, ahead of every other listener, so the health probe reports
 *       {@code DRAINING} and {@code DrainingFilter} refuses new work from that instant. Readiness is the one source:
 *       {@code DRAINING} is "refusing traffic after traffic was once accepted", {@code STARTING} is "not yet
 *       accepted".</li>
 *   <li>This lifecycle then stops, in a phase above the web server's graceful phase (Spring stops higher phases
 *       first), and ends every event stream with no {@code closed} event.</li>
 *   <li>Only then does Boot's graceful phase stop the connector accepting and wait for actions already admitted, up to
 *       {@code spring.lifecycle.timeout-per-shutdown-phase}, derived from {@code battleship.shutdown-drain-seconds}.
 *       The connector refuses connections from here on, which is why draining is answered by the application above.</li>
 * </ol>
 * It says nothing about any game outliving the process: a restart ends every game and session (R48).
 */
@Component
public class GracefulShutdownConfig implements SmartLifecycle {
    /** One above the web server's graceful phase: stopped before it. */
    public static final int PHASE = WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE + 1;

    private static final Logger LOG = LoggerFactory.getLogger(GracefulShutdownConfig.class);

    private final SseHub hub;
    private volatile ReadinessState readiness = ReadinessState.REFUSING_TRAFFIC;
    private volatile boolean everAccepted;
    private volatile boolean running;

    GracefulShutdownConfig(SseHub hub) {
        this.hub = hub;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void onReadiness(AvailabilityChangeEvent<ReadinessState> event) {
        if (event.getState() == ReadinessState.ACCEPTING_TRAFFIC) everAccepted = true;
        readiness = event.getState();
    }

    /** True while the service accepts traffic. */
    public boolean ready() {
        return readiness == ReadinessState.ACCEPTING_TRAFFIC;
    }

    /** True once traffic was accepted and is now refused: the service is shutting down, not starting. */
    public boolean draining() {
        return everAccepted && readiness == ReadinessState.REFUSING_TRAFFIC;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        hub.closeAll();
        running = false;
        LOG.info(
                "draining: new work is refused and event streams are closed; every game and session ends with this process");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
