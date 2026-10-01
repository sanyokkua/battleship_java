package ua.kostenko.battleship.application.usecase;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.CapacityExceededException;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.DisplayNameNormalizer;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

public final class JoinGameUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final SecretGenerator secrets;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final SnapshotPublisher publisher;
    private final int maxLiveGames;
    private final Duration idleTimeout;
    private final Duration resultRetention;

    public JoinGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            SnapshotPublisher publisher,
            int maxLiveGames,
            Duration idleTimeout,
            Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.secrets = Objects.requireNonNull(secrets);
        this.time = Objects.requireNonNull(time);
        this.projector = Objects.requireNonNull(projector);
        this.publisher = Objects.requireNonNull(publisher);
        if (maxLiveGames < 1
                || idleTimeout.isZero()
                || idleTimeout.isNegative()
                || resultRetention.isZero()
                || resultRetention.isNegative()) throw new IllegalArgumentException("invalid game limits");
        this.maxLiveGames = maxLiveGames;
        this.idleTimeout = idleTimeout;
        this.resultRetention = resultRetention;
    }

    public JoinedGame execute(
            String gameId, String invitationSecret, String displayName, String presentedSessionValue) {
        ExpiryPolicy.settleExpiredGamesOf(games, sessions, presentedSessionValue, time, resultRetention);
        try {
            Captured captured = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                String presentedDigest =
                        presentedSessionValue == null ? null : SessionRegistry.digest(presentedSessionValue);
                var status = ExpiryPolicy.status(slot, now, resultRetention);
                if (status == ExpiryPolicy.Status.EXPIRED_RETAINED)
                    ExpiryPolicy.settle(slot, sessions, resultRetention);
                if (presentedDigest != null && presentedDigest.equals(slot.guestSessionDigest())) {
                    if (status == ExpiryPolicy.Status.EXPIRED_RETAINED)
                        throw new ApplicationFailure("game-expired", null, null, null);
                    if (status == ExpiryPolicy.Status.FORGOTTEN) throw unavailable();
                    return new Captured(presentedSessionValue, slot.state(), slot.contextFor(Seat.GUEST, now), null);
                }
                if (presentedDigest != null && presentedDigest.equals(slot.hostSessionDigest())) throw unavailable();
                if (status != ExpiryPolicy.Status.LIVE
                        || slot.state().phase() != Phase.WAITING
                        || slot.state().guest() != null
                        || ExpiryPolicy.reached(slot.invitationDeadline(), now)) {
                    if (ExpiryPolicy.reached(slot.invitationDeadline(), now)) clearInvitation(slot);
                    throw unavailable();
                }
                if (invitationSecret == null
                        || slot.invitationDigest() == null
                        || !matches(slot.invitationDigest(), invitationSecret)) throw unavailable();
                String name;
                try {
                    name = DisplayNameNormalizer.normalize(displayName);
                } catch (DisplayNameNormalizer.InvalidNameException invalid) {
                    throw new ApplicationFailure(
                            "validation-failed",
                            "/displayName",
                            invalid.reason().name(),
                            null);
                }
                String sessionValue = presentedSessionValue != null
                                && sessions.find(presentedSessionValue).isPresent()
                        ? presentedSessionValue
                        : secrets.sessionValue();
                Instant admittedAt;
                try {
                    admittedAt = sessions.admitGame(sessionValue, gameId, maxLiveGames, () -> {
                        Instant admissionNow = time.now();
                        if (ExpiryPolicy.status(slot, admissionNow, resultRetention) != ExpiryPolicy.Status.LIVE
                                || ExpiryPolicy.reached(slot.invitationDeadline(), admissionNow)) {
                            clearInvitation(slot);
                            throw unavailable();
                        }
                        slot.replace(slot.state().withGuest(name, admissionNow));
                        slot.guestSessionDigest(SessionRegistry.digest(sessionValue));
                        slot.idleDeadline(admissionNow.plus(idleTimeout));
                        clearInvitation(slot);
                        return admissionNow;
                    });
                } catch (CapacityExceededException full) {
                    throw new ApplicationFailure("service-unavailable", null, null, full.retryAfterSeconds());
                }
                return new Captured(
                        sessionValue,
                        slot.state(),
                        slot.contextFor(Seat.GUEST, admittedAt),
                        slot.contextFor(Seat.HOST, admittedAt));
            });
            if (captured.hostContext() != null)
                publisher.publish(
                        gameId, Seat.HOST, projector.project(captured.state(), Seat.HOST, captured.hostContext()));
            return new JoinedGame(
                    captured.sessionValue(), projector.project(captured.state(), Seat.GUEST, captured.context()));
        } catch (UnknownGameException unknown) {
            throw unavailable();
        }
    }

    private static boolean matches(String storedDigest, String presentedSecret) {
        byte[] expected = HexFormat.of().parseHex(storedDigest);
        byte[] actual = HexFormat.of().parseHex(SessionRegistry.digest(presentedSecret));
        return MessageDigest.isEqual(expected, actual);
    }

    private static void clearInvitation(ua.kostenko.battleship.application.registry.GameSlot slot) {
        slot.invitationDigest(null);
        slot.unusedInvitationSecret(null);
    }

    private static ApplicationFailure unavailable() {
        return new ApplicationFailure("invitation-unavailable", null, null, null);
    }

    public record JoinedGame(String sessionValue, SnapshotView snapshot) {}

    /** {@code hostContext} is present only when this call seated the guest, which changed the host's view. */
    private record Captured(
            String sessionValue, GameState state, SnapshotContext context, SnapshotContext hostContext) {}
}
