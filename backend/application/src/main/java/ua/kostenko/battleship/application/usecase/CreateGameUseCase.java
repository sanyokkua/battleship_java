package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.CapacityExceededException;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.DisplayNameNormalizer;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.rules.Rulesets;

public final class CreateGameUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final SecretGenerator secrets;
    private final TimeSource time;
    private final SnapshotProjector projector;
    private final int maxLiveGames;
    private final Duration idleTimeout;
    private final Duration absoluteLifetime;
    private final Duration invitationLifetime;
    private final String publicBaseUrl;

    public CreateGameUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            SecretGenerator secrets,
            TimeSource time,
            SnapshotProjector projector,
            int maxLiveGames,
            Duration idleTimeout,
            Duration absoluteLifetime,
            Duration invitationLifetime,
            String publicBaseUrl) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.secrets = Objects.requireNonNull(secrets);
        this.time = Objects.requireNonNull(time);
        this.projector = Objects.requireNonNull(projector);
        if (maxLiveGames < 1
                || idleTimeout.isNegative()
                || idleTimeout.isZero()
                || absoluteLifetime.isNegative()
                || absoluteLifetime.isZero()
                || invitationLifetime.isNegative()
                || invitationLifetime.isZero()) throw new IllegalArgumentException("invalid game limits");
        this.maxLiveGames = maxLiveGames;
        this.idleTimeout = idleTimeout;
        this.absoluteLifetime = absoluteLifetime;
        this.invitationLifetime = invitationLifetime;
        this.publicBaseUrl = Objects.requireNonNull(publicBaseUrl);
    }

    public CreatedGame execute(String rulesetId, String displayName, String presentedSessionValue) {
        String name;
        try {
            name = DisplayNameNormalizer.normalize(displayName);
        } catch (DisplayNameNormalizer.InvalidNameException invalid) {
            throw new ApplicationFailure(
                    "validation-failed", "/displayName", invalid.reason().name(), null);
        }
        var ruleset = Rulesets.byId(rulesetId)
                .orElseThrow(() -> new ApplicationFailure("validation-failed", "/rulesetId", "UNKNOWN_VALUE", null));
        String sessionValue = presentedSessionValue != null
                        && sessions.find(presentedSessionValue).isPresent()
                ? presentedSessionValue
                : secrets.sessionValue();
        String gameId = secrets.gameId();
        String invitation = secrets.invitationSecret();
        Instant now;
        try {
            now = sessions.admitGame(sessionValue, gameId, maxLiveGames, () -> {
                Instant admittedAt = time.now();
                GameSlot slot = new GameSlot(
                        gameId,
                        GameState.create(ruleset, name),
                        SessionRegistry.digest(sessionValue),
                        admittedAt.plus(idleTimeout),
                        admittedAt.plus(absoluteLifetime),
                        admittedAt.plus(invitationLifetime),
                        publicBaseUrl);
                slot.invitationDigest(SessionRegistry.digest(invitation));
                slot.unusedInvitationSecret(invitation);
                games.insert(gameId, slot);
                return admittedAt;
            });
        } catch (CapacityExceededException full) {
            throw new ApplicationFailure("service-unavailable", null, null, full.retryAfterSeconds());
        }
        var captured =
                games.withSlot(gameId, current -> new Captured(current.state(), current.contextFor(Seat.HOST, now)));
        return new CreatedGame(sessionValue, projector.project(captured.state(), Seat.HOST, captured.context()));
    }

    public record CreatedGame(String sessionValue, SnapshotView snapshot) {}

    private record Captured(GameState state, ua.kostenko.battleship.application.projection.SnapshotContext context) {}
}
