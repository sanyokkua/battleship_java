package ua.kostenko.battleship.application.usecase;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * The second {@link SnapshotPublisher}: records every call and whether the game's lock was free at that moment, probed
 * from another thread, since a reentrant lock would let the publishing thread itself re-enter.
 */
final class RecordingPublisher implements SnapshotPublisher {
    record Call(String gameId, Seat seat, SnapshotView view, boolean lockFree) {
        boolean unavailable() {
            return view == null;
        }
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private GameRegistry games;

    void watch(GameRegistry games) {
        this.games = games;
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    void clear() {
        calls.clear();
    }

    @Override
    public void publish(String gameId, Seat seat, SnapshotView view) {
        calls.add(new Call(gameId, seat, view, lockFree(gameId)));
    }

    @Override
    public void unavailable(String gameId, Seat seat) {
        calls.add(new Call(gameId, seat, null, lockFree(gameId)));
    }

    private boolean lockFree(String gameId) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                        try {
                            return games.withSlot(gameId, slot -> true);
                        } catch (UnknownGameException removed) {
                            return true;
                        }
                    })
                    .get(2, TimeUnit.SECONDS);
        } catch (Exception heldOrFailed) {
            return false;
        }
    }
}
