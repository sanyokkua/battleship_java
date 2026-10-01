package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.rules.AllowedActions;

/** A player leaves: a waiting game is removed, a placement is abandoned, a finished one just forgets the seat. */
public final class LeaveGameUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final TimeSource time;
    private final Duration resultRetention;

    public LeaveGameUseCase(GameRegistry games, SessionRegistry sessions, TimeSource time, Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
        if (resultRetention.isZero() || resultRetention.isNegative())
            throw new IllegalArgumentException("resultRetention must be positive");
        this.resultRetention = resultRetention;
    }

    public void execute(String gameId, String sessionValue) {
        String digest = sessionValue == null ? null : SessionRegistry.digest(sessionValue);
        boolean remove;
        try {
            remove = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                Seat seat = ExpiryPolicy.authorize(slot, digest, now, resultRetention, sessions);
                GameState state = slot.state();
                if (!AllowedActions.of(state, seat).contains(AllowedActions.Action.LEAVE))
                    throw new ApplicationFailure("action-not-allowed", null, null, null);
                switch (state.phase()) {
                    case WAITING -> {
                        // Dead under the lock, so a join or a repeated leave arriving before the removal is refused.
                        slot.clearSeat(seat);
                        slot.invitationDigest(null);
                        slot.unusedInvitationSecret(null);
                        sessions.releaseGame(digest, gameId);
                        return true;
                    }
                    case PLACEMENT -> {
                        slot.replace(state.abandoned());
                        slot.terminalRetentionDeadline(now.plus(resultRetention));
                        sessions.releaseGame(slot.hostSessionDigest(), gameId);
                        sessions.releaseGame(slot.guestSessionDigest(), gameId);
                        slot.clearSeat(seat);
                    }
                    default -> slot.clearSeat(seat);
                }
                return false;
            });
        } catch (UnknownGameException unknown) {
            throw ExpiryPolicy.unavailable();
        }
        if (remove) games.remove(gameId);
    }
}
