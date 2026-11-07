package ua.kostenko.battleship.application.registry;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

public final class GameRegistry {
    private final ConcurrentHashMap<String, GameSlot> games = new ConcurrentHashMap<>();
    private final Semaphore gamePermits;

    public GameRegistry(int maxConcurrentGames) {
        if (maxConcurrentGames < 1) throw new IllegalArgumentException("capacity must be positive");
        gamePermits = new Semaphore(maxConcurrentGames);
    }

    public void insert(String gameId, GameSlot slot) {
        Objects.requireNonNull(gameId);
        Objects.requireNonNull(slot);
        if (!gamePermits.tryAcquire()) throw new CapacityExceededException("games", 1);
        boolean inserted = false;
        try {
            inserted = games.putIfAbsent(gameId, slot) == null;
            if (!inserted) throw new IllegalArgumentException("duplicate game id");
        } finally {
            if (!inserted) gamePermits.release();
        }
    }

    public <T> T withSlot(String gameId, Function<GameSlot, T> action) {
        GameSlot slot = games.get(gameId);
        if (slot == null) throw new UnknownGameException();
        slot.lock().lock();
        try {
            if (games.get(gameId) != slot) throw new UnknownGameException();
            return action.apply(slot);
        } finally {
            slot.lock().unlock();
        }
    }

    public Set<String> gameIds() {
        return Set.copyOf(games.keySet());
    }

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
}
