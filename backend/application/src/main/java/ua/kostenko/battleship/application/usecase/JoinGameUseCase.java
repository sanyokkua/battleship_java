package ua.kostenko.battleship.application.usecase;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.CapacityExceededException;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
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
    private final int maxLiveGames;
    private final Duration idleTimeout;

    public JoinGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            int maxLiveGames,
            Duration idleTimeout) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.secrets = Objects.requireNonNull(secrets);
        this.time = Objects.requireNonNull(time);
        this.projector = Objects.requireNonNull(projector);
        if (maxLiveGames < 1 || idleTimeout.isZero() || idleTimeout.isNegative())
            throw new IllegalArgumentException("invalid game limits");
        this.maxLiveGames = maxLiveGames;
        this.idleTimeout = idleTimeout;
    }

    public JoinedGame execute(
            String gameId, String invitationSecret, String displayName, String presentedSessionValue) {
        try {
            Captured captured = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                String presentedDigest =
                        presentedSessionValue == null ? null : SessionRegistry.digest(presentedSessionValue);
                if (presentedDigest != null && presentedDigest.equals(slot.guestSessionDigest()))
                    return new Captured(presentedSessionValue, slot.state(), slot.contextFor(Seat.GUEST, now));
                if (presentedDigest != null && presentedDigest.equals(slot.hostSessionDigest())) throw unavailable();
                if (slot.state().phase() != Phase.WAITING
                        || slot.state().guest() != null
                        || !now.isBefore(slot.invitationDeadline())
                        || !now.isBefore(slot.idleDeadline())
                        || !now.isBefore(slot.absoluteDeadline())) {
                    if (!now.isBefore(slot.invitationDeadline())
                            || !now.isBefore(slot.idleDeadline())
                            || !now.isBefore(slot.absoluteDeadline())) clearInvitation(slot);
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
                        if (!admissionNow.isBefore(slot.invitationDeadline())
                                || !admissionNow.isBefore(slot.idleDeadline())
                                || !admissionNow.isBefore(slot.absoluteDeadline())) {
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
                return new Captured(sessionValue, slot.state(), slot.contextFor(Seat.GUEST, admittedAt));
            });
            return new JoinedGame(
                    captured.sessionValue(), projector.project(captured.state(), Seat.GUEST, captured.context()));
        } catch (IllegalArgumentException unknown) {
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

    private record Captured(String sessionValue, GameState state, SnapshotContext context) {}
}
