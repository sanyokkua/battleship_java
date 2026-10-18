package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * The one place a deadline is compared (inclusive: reaching it means expired) and the one place a slot is judged
 * live, expired-but-retained or forgotten. The judgement uses deadlines only, never whether a sweep has run.
 */
public final class ExpiryPolicy {
    public enum Status {
        LIVE,
        EXPIRED_RETAINED,
        FORGOTTEN
    }

    private ExpiryPolicy() {}

    public static boolean reached(Instant deadline, Instant now) {
        return !now.isBefore(deadline);
    }

    /** The end of play: the earlier of the idle deadline and the absolute ceiling. */
    public static Instant playDeadline(GameSlot slot) {
        Instant idle = slot.idleDeadline();
        Instant absolute = slot.absoluteDeadline();
        return idle.isBefore(absolute) ? idle : absolute;
    }

    public static Status status(GameSlot slot, Instant now, Duration resultRetention) {
        Phase phase = slot.state().phase();
        if (phase == Phase.FINISHED || phase == Phase.ABANDONED) {
            Instant retained = slot.terminalRetentionDeadline();
            return retained != null && reached(retained, now) ? Status.FORGOTTEN : Status.LIVE;
        }
        Instant expiredAt = playDeadline(slot);
        if (!reached(expiredAt, now)) return Status.LIVE;
        Instant forgottenAt = slot.terminalRetentionDeadline() != null
                ? slot.terminalRetentionDeadline()
                : expiredAt.plus(resultRetention);
        return reached(forgottenAt, now) ? Status.FORGOTTEN : Status.EXPIRED_RETAINED;
    }

    /**
     * Records that an unfinished game has expired: retention counts from the expiry deadline, the invitation dies and
     * both players' live-game allocations are released. Repeating it changes nothing.
     */
    public static void settle(GameSlot slot, SessionRegistry sessions, Duration resultRetention) {
        if (slot.terminalRetentionDeadline() == null)
            slot.terminalRetentionDeadline(playDeadline(slot).plus(resultRetention));
        slot.invitationDigest(null);
        slot.unusedInvitationSecret(null);
        sessions.releaseGame(slot.hostSessionDigest(), slot.gameId());
        sessions.releaseGame(slot.guestSessionDigest(), slot.gameId());
    }

    /**
     * Settles the expired games of the presenting session, so the per-browser live-game cap never depends on the
     * sweep. Takes each game's slot lock on its own: call it before any slot or the session registry is locked.
     */
    static void settleExpiredGamesOf(
            GameRegistry games,
            SessionRegistry sessions,
            String sessionValue,
            TimeSource time,
            Duration resultRetention) {
        if (sessionValue == null) return;
        sessions.find(sessionValue).ifPresent(record -> {
            if (record.liveGames().isEmpty()) return;
            Instant now = time.now();
            for (String gameId : record.liveGames()) {
                try {
                    games.withSlot(gameId, slot -> {
                        if (status(slot, now, resultRetention) != Status.LIVE) settle(slot, sessions, resultRetention);
                        return null;
                    });
                } catch (UnknownGameException reclaimed) {
                    // the sweep settled it before removing it, so nothing is left to release
                }
            }
        });
    }

    /**
     * Admits a protected operation: a forgotten game and a stranger both read as unavailable, an expired game tells
     * only its own two seats, and a live game yields the caller's seat.
     */
    static Seat authorize(
            GameSlot slot, String digest, Instant now, Duration resultRetention, SessionRegistry sessions) {
        Seat seat = seatFor(slot, digest);
        Status status = status(slot, now, resultRetention);
        if (status == Status.EXPIRED_RETAINED) settle(slot, sessions, resultRetention);
        if (status == Status.FORGOTTEN || (status == Status.EXPIRED_RETAINED && seat == null)) throw unavailable();
        if (status == Status.EXPIRED_RETAINED) throw new ApplicationFailure("game-expired", null, null, null);
        if (seat == null || sessions.findDigest(digest).isEmpty()) throw unavailable();
        return seat;
    }

    static Seat seatFor(GameSlot slot, String digest) {
        if (digest == null) return null;
        if (digest.equals(slot.hostSessionDigest())) return Seat.HOST;
        if (digest.equals(slot.guestSessionDigest())) return Seat.GUEST;
        return null;
    }

    static ApplicationFailure unavailable() {
        return new ApplicationFailure("game-unavailable", null, null, null);
    }
}
