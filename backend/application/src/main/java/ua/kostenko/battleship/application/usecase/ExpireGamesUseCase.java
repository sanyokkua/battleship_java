package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;

/** Reclaims memory only: what a request is told is decided by {@link ExpiryPolicy}, never by this sweep. */
public final class ExpireGamesUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final Duration resultRetention;

    public ExpireGamesUseCase(GameRegistry games, SessionRegistry sessions, Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        if (resultRetention.isZero() || resultRetention.isNegative())
            throw new IllegalArgumentException("resultRetention must be positive");
        this.resultRetention = resultRetention;
    }

    public void sweep(Instant now) {
        RuntimeException first = null;
        for (String gameId : games.gameIds()) {
            try {
                boolean forgotten = games.withSlot(gameId, slot -> {
                    var status = ExpiryPolicy.status(slot, now, resultRetention);
                    if (status != ExpiryPolicy.Status.LIVE) ExpiryPolicy.settle(slot, sessions, resultRetention);
                    return status == ExpiryPolicy.Status.FORGOTTEN;
                });
                if (forgotten) games.remove(gameId);
            } catch (UnknownGameException reclaimedMeanwhile) {
                // already removed by another path; nothing left to do
            } catch (RuntimeException failed) {
                if (first == null) first = failed;
            }
        }
        if (first != null) throw first;
    }
}
