package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.rules.GameRules;
import ua.kostenko.battleship.domain.transition.Transition;

/** Serializes an action per game and projects its immutable result after unlocking. */
public final class CommandUseCase {
    private final GameRegistry games;
    private final SessionRegistry sessions;
    private final TimeSource time;
    private final RandomSource random;
    private final SnapshotProjector projector;
    private final Duration idleTimeout;
    private final Duration resultRetention;

    public CommandUseCase(
            GameRegistry games,
            SessionRegistry sessions,
            TimeSource time,
            RandomSource random,
            SnapshotProjector projector,
            Duration idleTimeout,
            Duration resultRetention) {
        this.games = Objects.requireNonNull(games);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
        this.random = Objects.requireNonNull(random);
        this.projector = Objects.requireNonNull(projector);
        if (idleTimeout.isZero()
                || idleTimeout.isNegative()
                || resultRetention.isZero()
                || resultRetention.isNegative()) throw new IllegalArgumentException("invalid game lifetimes");
        this.idleTimeout = idleTimeout;
        this.resultRetention = resultRetention;
    }

    public CommandResult execute(String gameId, String sessionValue, UUID commandId, GameCommand command) {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(command, "command");
        String digest = sessionValue == null ? null : SessionRegistry.digest(sessionValue);
        Captured captured;
        try {
            captured = games.withSlot(gameId, slot -> apply(slot, gameId, digest, commandId, command));
        } catch (IllegalArgumentException unknown) {
            if (!"unknown game id".equals(unknown.getMessage())) throw unknown;
            throw unavailable();
        }
        SnapshotView caller = projector.project(captured.state(), captured.actor(), captured.callerContext());
        Map<Seat, SnapshotView> deliveries = new EnumMap<>(Seat.class);
        for (var entry : captured.deliveryContexts().entrySet()) {
            Seat seat = entry.getKey();
            deliveries.put(
                    seat,
                    seat == captured.actor() ? caller : projector.project(captured.state(), seat, entry.getValue()));
        }
        return new CommandResult(caller, deliveries);
    }

    private Captured apply(GameSlot slot, String gameId, String digest, UUID commandId, GameCommand command) {
        Instant now = time.now();
        Seat actor = seatFor(slot, digest);
        Phase phase = slot.state().phase();
        if (phase != Phase.FINISHED
                && phase != Phase.ABANDONED
                && (!now.isBefore(slot.idleDeadline()) || !now.isBefore(slot.absoluteDeadline())))
            throw actor == null ? unavailable() : new ApplicationFailure("game-expired", null, null, null);
        if (actor == null || sessions.findDigest(digest).isEmpty()) throw unavailable();

        Set<Seat> changed = Set.of();
        if (!slot.acceptedCommandIds().contains(commandId)) {
            GameState previous = slot.state();
            Transition transition = GameRules.apply(previous, actor, command, now, random);
            if (transition.rejection() != null) {
                var rejection = transition.rejection();
                throw new ApplicationFailure(rejection.code().wireCode(), rejection.field(), rejection.rule(), null);
            }
            long expected = previous.version() + (transition.versionBumped() ? 1 : 0);
            if (transition.next().version() != expected)
                throw new IllegalStateException("domain transition version mismatch");
            slot.replace(transition.next());
            slot.acceptedCommandIds().add(commandId);
            slot.idleDeadline(now.plus(idleTimeout));
            changed = transition.viewChanged();
            if (previous.phase() != Phase.FINISHED && transition.next().phase() == Phase.FINISHED) {
                slot.terminalRetentionDeadline(now.plus(resultRetention));
                sessions.releaseGame(slot.hostSessionDigest(), gameId);
                sessions.releaseGame(slot.guestSessionDigest(), gameId);
            }
        }
        Map<Seat, SnapshotContext> deliveryContexts = new EnumMap<>(Seat.class);
        for (Seat seat : changed) deliveryContexts.put(seat, slot.contextFor(seat, now));
        return new Captured(slot.state(), actor, slot.contextFor(actor, now), Map.copyOf(deliveryContexts));
    }

    private static Seat seatFor(GameSlot slot, String digest) {
        if (digest == null) return null;
        if (digest.equals(slot.hostSessionDigest())) return Seat.HOST;
        if (digest.equals(slot.guestSessionDigest())) return Seat.GUEST;
        return null;
    }

    private static ApplicationFailure unavailable() {
        return new ApplicationFailure("game-unavailable", null, null, null);
    }

    public record CommandResult(SnapshotView snapshot, Map<Seat, SnapshotView> deliveries) {
        public CommandResult {
            Objects.requireNonNull(snapshot);
            deliveries = Map.copyOf(Objects.requireNonNull(deliveries));
        }
    }

    private record Captured(
            GameState state, Seat actor, SnapshotContext callerContext, Map<Seat, SnapshotContext> deliveryContexts) {}
}
