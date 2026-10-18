package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

/** Reclaims memory only: what a request is told is decided by {@link ExpiryPolicy}, never by this sweep. */
public final class ExpireGamesUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final SnapshotPublisher publisher;
    private final Duration resultRetention;

    public ExpireGamesUseCase(
            GameRegistry games, SessionRegistry sessions, SnapshotPublisher publisher, Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.publisher = Objects.requireNonNull(publisher);
        if (resultRetention.isZero() || resultRetention.isNegative())
            throw new IllegalArgumentException("resultRetention must be positive");
        this.resultRetention = resultRetention;
    }

    public void sweep(Instant now) {
        sweep(now, forgotten -> {});
    }

    /**
     * Reclaims what is due. {@code expired} is told the id of each game forgotten while still unfinished, i.e. one that
     * expired, the moment it is forgotten, so a later failure in the sweep cannot lose it.
     */
    public void sweep(Instant now, Consumer<String> expired) {
        RuntimeException first = null;
        for (String gameId : games.gameIds()) {
            try {
                boolean[] unfinished = {false};
                var status = games.withSlot(gameId, slot -> {
                    var current = ExpiryPolicy.status(slot, now, resultRetention);
                    if (current != ExpiryPolicy.Status.LIVE) ExpiryPolicy.settle(slot, sessions, resultRetention);
                    Phase phase = slot.state().phase();
                    unfinished[0] = phase != Phase.FINISHED && phase != Phase.ABANDONED;
                    return current;
                });
                if (status == ExpiryPolicy.Status.FORGOTTEN) {
                    games.remove(gameId);
                    if (unfinished[0]) expired.accept(gameId);
                }
                if (status != ExpiryPolicy.Status.LIVE)
                    for (Seat seat : Seat.values()) publisher.unavailable(gameId, seat);
            } catch (UnknownGameException reclaimedMeanwhile) {
                // already removed by another path; nothing left to do
            } catch (RuntimeException failed) {
                if (first == null) first = failed;
            }
        }
        if (first != null) throw first;
    }
}
