package ua.kostenko.battleship.app.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.web.SnapshotDtoAssembler;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.application.usecase.SubscribeUseCase;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * The live-update hub: owns the open streams and the streams permit, and turns each published view into a
 * {@code snapshot} event through the same assembler and wire mapper as the HTTP answers (R17). A subscriber is
 * installed before the game lock is taken, so a transition racing with registration is either in the captured first
 * snapshot or buffered and sent after it. The hub never holds its own lock while taking the game lock, projecting,
 * serializing or writing.
 */
@Component
public class SseHub implements SnapshotPublisher {
    private static final MediaType FRAME = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final int RETRY_AFTER_SECONDS = 1;

    private final SubscribeUseCase subscribe;
    private final ObjectMapper wireMapper;
    private final Semaphore streams;
    private final ConcurrentHashMap<String, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /** Race seams for EventStreamIT only: run after the pending install and after the capture; no-ops otherwise. */
    volatile Runnable afterPendingInstalled = () -> {};

    volatile Runnable afterCapture = () -> {};

    SseHub(SubscribeUseCase subscribe, ObjectMapper wireMapper, BattleshipProperties properties) {
        this.subscribe = subscribe;
        this.wireMapper = wireMapper;
        this.streams = new Semaphore(properties.maxConcurrentStreams());
    }

    /**
     * Opens the caller's stream. A refusal (over the streams ceiling, not a player, expired) is thrown before any
     * emitter is returned, so it is answered as the same problem document as every other operation.
     */
    public SseEmitter open(String gameId, String sessionValue) {
        if (!streams.tryAcquire()) throw new ApplicationFailure("service-unavailable", null, null, RETRY_AFTER_SECONDS);
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(gameId, emitter);
        subscribers.compute(gameId, (id, present) -> {
            Set<Subscriber> set = present == null ? ConcurrentHashMap.newKeySet() : present;
            set.add(subscriber);
            return set;
        });
        emitter.onCompletion(() -> drop(subscriber));
        emitter.onTimeout(() -> drop(subscriber));
        emitter.onError(failure -> drop(subscriber));
        afterPendingInstalled.run();
        SubscribeUseCase.Subscription subscription;
        try {
            subscription = subscribe.open(gameId, sessionValue);
        } catch (RuntimeException refused) {
            drop(subscriber);
            throw refused;
        }
        afterCapture.run();
        subscriber.activate(subscription.seat(), subscription.first());
        drain(subscriber);
        return emitter;
    }

    @Override
    public void publish(String gameId, Seat seat, SnapshotView view) {
        for (Subscriber subscriber : subscribersOf(gameId)) {
            if (subscriber.offer(seat, view)) drain(subscriber);
        }
    }

    @Override
    public void unavailable(String gameId, Seat seat) {
        for (Subscriber subscriber : subscribersOf(gameId)) {
            if (subscriber.isFor(seat)) {
                drop(subscriber);
                subscriber.emitter().complete();
            }
        }
    }

    private Set<Subscriber> subscribersOf(String gameId) {
        Set<Subscriber> present = subscribers.get(gameId);
        return present == null ? Set.of() : present;
    }

    /** Writes queued views until none is left; a concurrent caller leaves the work to the thread already draining. */
    private void drain(Subscriber subscriber) {
        if (!subscriber.startDrain()) return;
        for (SnapshotView view = subscriber.next(); view != null; view = subscriber.next()) {
            try {
                subscriber.emitter().send(Set.of(new DataWithMediaType(frame(view), FRAME)));
            } catch (IOException | IllegalStateException failed) {
                drop(subscriber);
                return;
            }
        }
    }

    /** The contract's framing, exactly: {@code event: snapshot}, {@code id: <version>}, {@code data: <document>}. */
    private String frame(SnapshotView view) {
        try {
            return "event: snapshot\nid: " + view.version() + "\ndata: "
                    + wireMapper.writeValueAsString(SnapshotDtoAssembler.assemble(view)) + "\n\n";
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("a generated snapshot could not be serialized", impossible);
        }
    }

    private void drop(Subscriber subscriber) {
        if (!subscriber.close()) return;
        subscribers.computeIfPresent(subscriber.gameId(), (id, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
        streams.release();
    }
}
