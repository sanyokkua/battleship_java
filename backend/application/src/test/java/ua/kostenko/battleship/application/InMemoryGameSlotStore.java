package ua.kostenko.battleship.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import ua.kostenko.battleship.application.port.GameSlotStore;
import ua.kostenko.battleship.application.port.Slot;

public final class InMemoryGameSlotStore implements GameSlotStore {
    private record Entry(Slot slot, ReentrantLock lock) {}

    private final Map<String, Entry> slots = new ConcurrentHashMap<>();

    @Override
    public void insert(String gameId, Slot slot) {
        if (slots.putIfAbsent(gameId, new Entry(slot, new ReentrantLock())) != null)
            throw new IllegalArgumentException("duplicate game id");
    }

    @Override
    public <T> T withSlot(String gameId, Function<Slot, T> action) {
        Entry entry = slots.get(gameId);
        if (entry == null) throw new IllegalArgumentException("unknown game id");
        entry.lock().lock();
        try {
            if (slots.get(gameId) != entry) throw new IllegalArgumentException("unknown game id");
            return action.apply(entry.slot());
        } finally {
            entry.lock().unlock();
        }
    }

    @Override
    public void remove(String gameId) {
        Entry entry = slots.get(gameId);
        if (entry == null) return;
        entry.lock().lock();
        try {
            slots.remove(gameId, entry);
        } finally {
            entry.lock().unlock();
        }
    }
}
