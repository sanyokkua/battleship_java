package ua.kostenko.battleship.application.port;

import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * Hands live updates to whatever streams them. Use cases call it only after releasing the game lock, with views they
 * have already projected; it never receives a wire type.
 */
public interface SnapshotPublisher {
    /** The seat's view changed: {@code view} is its new caller-relative snapshot. */
    void publish(String gameId, Seat seat, SnapshotView view);

    /** The game stopped existing for the seat (it left, or the game expired or was removed). */
    void unavailable(String gameId, Seat seat);
}
