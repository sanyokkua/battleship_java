package ua.kostenko.battleship.app.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.web.SnapshotDtoAssembler;
import ua.kostenko.battleship.app.web.dto.StreamClosed;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.application.usecase.SubscribeUseCase;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * The live-update hub: owns the open streams and the streams permit, and turns each published view into a
 * {@code snapshot} event through the same assembler and wire mapper as the HTTP answers (R17). A subscriber is
 * installed before the game lock is taken, so a transition racing with registration is either in the captured first
 * snapshot or buffered and sent after it. At most one stream is kept per game and seat: a newer one closes the older
 * with {@code closed}/{@code REPLACED}, and a game that stops existing for a seat closes its stream with
 * {@code closed}/{@code GAME_UNAVAILABLE} (R29, R31). Every ended stream is reported to {@link SubscribeUseCase}, which
 * keeps the {@code connected} flags (R22). Every stream is bounded (R30): {@link HeartbeatScheduler} drives
 * {@link #bound(Instant)}, a subscriber holds at most its latest unsent snapshot, and a stream whose write fails is
 * dropped; every ended stream releases its permit exactly once (R39). The hub never holds its own lock while taking
 * the game lock, projecting, serializing or writing.
 */
@Component
public class SseHub implements SnapshotPublisher {
    private static final MediaType FRAME = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final int RETRY_AFTER_SECONDS = 1;
    private static final String KEEP_ALIVE = ": keep-alive\n\n";

    private final SubscribeUseCase subscribe;
    private final TimeSource time;
    private final ObjectMapper wireMapper;
    private final Semaphore streams;
    private final Duration heartbeat;
    private final Duration lifetime;
    private final ConcurrentHashMap<String, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /** Race seams for EventStreamIT only: run after the pending install and after the capture; no-ops otherwise. */
    volatile Runnable afterPendingInstalled = () -> {};

    volatile Runnable afterCapture = () -> {};

    SseHub(SubscribeUseCase subscribe, ObjectMapper wireMapper, TimeSource time, BattleshipProperties properties) {
        this.subscribe = subscribe;
        this.time = time;
        this.wireMapper = wireMapper;
        this.streams = new Semaphore(properties.maxConcurrentStreams());
        this.heartbeat = Duration.ofSeconds(properties.heartbeatSeconds());
        this.lifetime = Duration.ofSeconds(properties.streamMaxLifetimeSeconds());
    }

    /**
     * Opens the caller's stream. A refusal (over the streams ceiling, not a player, expired) is thrown before any
     * emitter is returned, so it is answered as the same problem document as every other operation.
     */
    public SseEmitter open(String gameId, String sessionValue) {
        if (!streams.tryAcquire()) throw new ApplicationFailure("service-unavailable", null, null, RETRY_AFTER_SECONDS);
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(gameId, emitter, time.now());
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
        Seat seat = subscription.seat();
        long stream = subscription.stream();
        Subscriber[] replaced = {null};
        boolean[] active = {false};
        subscribers.computeIfPresent(gameId, (id, set) -> {
            active[0] = subscriber.activate(seat, stream, subscription.first());
            if (active[0]) {
                for (Subscriber other : set) {
                    if (other != subscriber && other.isOpenFor(seat))
                        replaced[0] = other.stream() < stream ? other : subscriber;
                }
            }
            return set;
        });
        if (!active[0]) {
            disconnected(gameId, seat, stream);
            return emitter;
        }
        if (subscription.opponent() != null) publish(gameId, seat.opponent(), subscription.opponent());
        drain(subscriber);
        if (replaced[0] != null && replaced[0].closeWith(StreamClosed.ReasonEnum.REPLACED)) drain(replaced[0]);
        return emitter;
    }

    @Override
    public void publish(String gameId, Seat seat, SnapshotView view) {
        for (Subscriber subscriber : subscribersOf(gameId)) {
            if (subscriber.offer(seat, view)) drain(subscriber);
        }
    }

    /** Closes the seat's stream with {@code GAME_UNAVAILABLE}, including one still registering. */
    @Override
    public void unavailable(String gameId, Seat seat) {
        for (Subscriber subscriber : subscribersOf(gameId)) {
            if (subscriber.unavailable(seat)) drain(subscriber);
        }
    }

    private Set<Subscriber> subscribersOf(String gameId) {
        Set<Subscriber> present = subscribers.get(gameId);
        return present == null ? Set.of() : present;
    }

    /**
     * Writes queued frames until none is left; a deliberate close is written last, after which the stream ends. A
     * concurrent caller leaves the work to the thread already draining, and a write that fails drops the subscriber.
     */
    private void drain(Subscriber subscriber) {
        if (!subscriber.startDrain()) return;
        while (true) {
            Subscriber.Frame next = subscriber.next();
            if (next == null) return;
            try {
                subscriber.emitter().send(Set.of(new DataWithMediaType(text(next), FRAME)));
            } catch (IOException | IllegalStateException failed) {
                drop(subscriber);
                return;
            }
            if (next.closing() != null) {
                subscriber.emitter().complete();
                drop(subscriber);
                return;
            }
        }
    }

    private String text(Subscriber.Frame next) throws JsonProcessingException {
        if (next.view() != null) return frame(next.view());
        if (next.closing() != null) return closedFrame(next.closing());
        return KEEP_ALIVE;
    }

    /**
     * One heartbeat round at {@code now}: a stream that reached its maximum lifetime ends with no {@code closed}
     * event, so the browser reconnects by itself; every other stream due a keep-alive gets one. A failed keep-alive
     * write is how a client that went away silently is noticed (R30).
     */
    void bound(Instant now) {
        for (Set<Subscriber> game : subscribers.values()) {
            for (Subscriber subscriber : game) {
                if (subscriber.outlived(now, lifetime)) {
                    drop(subscriber);
                    subscriber.emitter().complete();
                } else if (subscriber.beatDue(now, heartbeat)) {
                    drain(subscriber);
                }
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

    /** A deliberate close: {@code event: closed} and a {@code StreamClosed} document, with no {@code id}. */
    private String closedFrame(StreamClosed.ReasonEnum reason) throws JsonProcessingException {
        return "event: closed\ndata: " + wireMapper.writeValueAsString(new StreamClosed().reason(reason)) + "\n\n";
    }

    /** Ends a stream once: releases its permit and, for an activated stream, reports the disconnection. */
    private void drop(Subscriber subscriber) {
        if (!subscriber.close()) return;
        subscribers.computeIfPresent(subscriber.gameId(), (id, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
        streams.release();
        Seat seat = subscriber.seat();
        if (seat != null) disconnected(subscriber.gameId(), seat, subscriber.stream());
    }

    /** A closed stream may have been its seat's last: the opponent then sees the seat disconnected. */
    private void disconnected(String gameId, Seat seat, long stream) {
        SnapshotView opponent = subscribe.close(gameId, seat, stream);
        if (opponent != null) publish(gameId, seat.opponent(), opponent);
    }
}
