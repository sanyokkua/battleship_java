package ua.kostenko.battleship.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Timeline(
        Instant guestJoinedAt,
        Map<Seat, Instant> readyAt,
        Instant playStartedAt,
        Instant finishedAt,
        Instant turnStartedAt,
        Map<Seat, Instant> shotDecisionStartedAt,
        Map<Seat, List<Long>> turnDurationsMs,
        Map<Seat, List<Long>> shotDecisionDurationsMs) {
    public Timeline {
        readyAt = Map.copyOf(Objects.requireNonNull(readyAt, "readyAt"));
        shotDecisionStartedAt = Map.copyOf(Objects.requireNonNull(shotDecisionStartedAt, "shotDecisionStartedAt"));
        turnDurationsMs = immutableSamples(turnDurationsMs, "turnDurationsMs");
        shotDecisionDurationsMs = immutableSamples(shotDecisionDurationsMs, "shotDecisionDurationsMs");
    }

    public static Timeline empty() {
        return new Timeline(null, Map.of(), null, null, null, Map.of(), Map.of(), Map.of());
    }

    public Timeline withGuestJoinedAt(Instant now) {
        return new Timeline(
                Objects.requireNonNull(now, "now"),
                readyAt,
                playStartedAt,
                finishedAt,
                turnStartedAt,
                shotDecisionStartedAt,
                turnDurationsMs,
                shotDecisionDurationsMs);
    }

    public Timeline withReadyAt(Seat seat, Instant now) {
        Map<Seat, Instant> times = new EnumMap<>(Seat.class);
        times.putAll(readyAt);
        times.put(Objects.requireNonNull(seat, "seat"), Objects.requireNonNull(now, "now"));
        return new Timeline(
                guestJoinedAt,
                times,
                playStartedAt,
                finishedAt,
                turnStartedAt,
                shotDecisionStartedAt,
                turnDurationsMs,
                shotDecisionDurationsMs);
    }

    public Timeline withPlayStartedAt(Instant now, Seat firstTurn) {
        Objects.requireNonNull(now, "now");
        return new Timeline(
                guestJoinedAt,
                readyAt,
                now,
                finishedAt,
                now,
                Map.of(Objects.requireNonNull(firstTurn, "firstTurn"), now),
                turnDurationsMs,
                shotDecisionDurationsMs);
    }

    public Timeline withFire(Seat actor, Seat nextTurn, Instant now, boolean finished) {
        Map<Seat, List<Long>> decisions = append(
                shotDecisionDurationsMs,
                actor,
                Duration.between(shotDecisionStartedAt.get(actor), now).toMillis());
        boolean closeTurn = finished || actor != nextTurn;
        Map<Seat, List<Long>> turns = closeTurn ? append(turnDurationsMs, actor, turnDurationMs(now)) : turnDurationsMs;
        return new Timeline(
                guestJoinedAt,
                readyAt,
                playStartedAt,
                finished ? now : finishedAt,
                closeTurn ? (finished ? null : now) : turnStartedAt,
                finished ? Map.of() : Map.of(nextTurn, now),
                turns,
                decisions);
    }

    public Timeline withFinishedAt(Instant now, Seat holder) {
        return new Timeline(
                guestJoinedAt,
                readyAt,
                playStartedAt,
                now,
                null,
                Map.of(),
                append(turnDurationsMs, holder, turnDurationMs(now)),
                shotDecisionDurationsMs);
    }

    private long turnDurationMs(Instant end) {
        return Duration.between(playStartedAt, end).toMillis()
                - Duration.between(playStartedAt, turnStartedAt).toMillis();
    }

    private static Map<Seat, List<Long>> append(Map<Seat, List<Long>> existing, Seat seat, long duration) {
        Map<Seat, List<Long>> samples = new EnumMap<>(Seat.class);
        samples.putAll(existing);
        List<Long> values = new ArrayList<>(samples.getOrDefault(seat, List.of()));
        values.add(duration);
        samples.put(seat, values);
        return samples;
    }

    private static Map<Seat, List<Long>> immutableSamples(Map<Seat, List<Long>> samples, String name) {
        Objects.requireNonNull(samples, name);
        return samples.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
