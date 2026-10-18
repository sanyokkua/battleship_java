package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.registry.UnknownGameException;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

public final class ReplaceInvitationUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final SecretGenerator secrets;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final SnapshotPublisher publisher;
    private final Duration invitationLifetime;
    private final Duration resultRetention;

    public ReplaceInvitationUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            SnapshotPublisher publisher,
            Duration invitationLifetime,
            Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.secrets = Objects.requireNonNull(secrets);
        this.time = Objects.requireNonNull(time);
        this.projector = Objects.requireNonNull(projector);
        this.publisher = Objects.requireNonNull(publisher);
        if (invitationLifetime.isZero()
                || invitationLifetime.isNegative()
                || resultRetention.isZero()
                || resultRetention.isNegative()) throw new IllegalArgumentException("lifetimes must be positive");
        this.invitationLifetime = invitationLifetime;
        this.resultRetention = resultRetention;
    }

    public SnapshotView execute(String gameId, String sessionValue) {
        if (sessionValue == null || sessions.find(sessionValue).isEmpty())
            throw new ApplicationFailure("session-required", null, null, null);
        Captured captured;
        try {
            captured = games.withSlot(gameId, slot -> {
                Instant now = time.now();
                String callerDigest = SessionRegistry.digest(sessionValue);
                if (ExpiryPolicy.authorize(slot, callerDigest, now, resultRetention, sessions) != Seat.HOST)
                    throw new ApplicationFailure("action-not-allowed", null, null, null);
                if (slot.state().phase() != Phase.WAITING)
                    throw new ApplicationFailure("action-not-allowed", null, null, null);
                String secret = secrets.invitationSecret();
                slot.invitationDigest(SessionRegistry.digest(secret));
                slot.unusedInvitationSecret(secret);
                slot.invitationDeadline(now.plus(invitationLifetime));
                slot.replace(slot.state().withBumpedVersion());
                return new Captured(slot.state(), slot.contextFor(Seat.HOST, now));
            });
        } catch (UnknownGameException unknown) {
            throw ExpiryPolicy.unavailable();
        }
        SnapshotView host = projector.project(captured.state(), Seat.HOST, captured.context());
        publisher.publish(gameId, Seat.HOST, host);
        return host;
    }

    private record Captured(GameState state, SnapshotContext context) {}
}
