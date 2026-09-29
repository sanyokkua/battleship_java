package ua.kostenko.battleship.app.registry;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.application.port.GameSlotStore;
import ua.kostenko.battleship.application.port.Slot;

public final class GameRegistry implements GameSlotStore {
    private final ConcurrentHashMap<String, GameSlot> games = new ConcurrentHashMap<>();
    private final Semaphore gamePermits;
    private final Semaphore streamPermits;

    public GameRegistry(BattleshipProperties properties) {
        this(properties.maxConcurrentGames(), properties.maxConcurrentStreams());
    }

    public GameRegistry(int maxConcurrentGames, int maxConcurrentStreams) {
        if (maxConcurrentGames < 1 || maxConcurrentStreams < 1)
            throw new IllegalArgumentException("capacities must be positive");
        gamePermits = new Semaphore(maxConcurrentGames);
        streamPermits = new Semaphore(maxConcurrentStreams);
    }

    @Override
    public void insert(String gameId, Slot slot) {
        Objects.requireNonNull(gameId);
        if (!(slot instanceof GameSlot gameSlot)) throw new IllegalArgumentException("registry requires GameSlot");
        if (!gamePermits.tryAcquire()) throw new CapacityExceededException("games", 1);
        boolean inserted = false;
        try {
            inserted = games.putIfAbsent(gameId, gameSlot) == null;
            if (!inserted) throw new IllegalArgumentException("duplicate game id");
        } finally {
            if (!inserted) gamePermits.release();
        }
    }

    @Override
    public <T> T withSlot(String gameId, Function<Slot, T> action) {
        GameSlot slot = games.get(gameId);
        if (slot == null) throw new IllegalArgumentException("unknown game id");
        slot.lock().lock();
        try {
            if (games.get(gameId) != slot) throw new IllegalArgumentException("unknown game id");
            return action.apply(slot);
        } finally {
            slot.lock().unlock();
        }
    }

    @Override
    public void remove(String gameId) {
        GameSlot slot = games.get(gameId);
        if (slot == null) return;
        slot.lock().lock();
        try {
            if (games.remove(gameId, slot)) gamePermits.release();
        } finally {
            slot.lock().unlock();
        }
    }

    public StreamLease reserveStream() {
        if (!streamPermits.tryAcquire()) throw new CapacityExceededException("streams", 1);
        return new StreamLease(streamPermits);
    }

    public static final class StreamLease implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean closed = new AtomicBoolean();

        private StreamLease(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) permits.release();
        }
    }
}
