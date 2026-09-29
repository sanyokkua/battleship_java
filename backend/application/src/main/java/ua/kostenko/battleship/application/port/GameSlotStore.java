package ua.kostenko.battleship.application.port;

import java.util.function.Function;

public interface GameSlotStore {
    /** Runs the action under that game's lock; throws if the game is absent or removed before entry. */
    <T> T withSlot(String gameId, Function<Slot, T> action);

    /** Inserts a new slot, refusing duplicate ids without replacement. */
    void insert(String gameId, Slot slot);

    /** Removes the slot after in-flight actions finish; absent ids have no effect. */
    void remove(String gameId);
}
