package ua.kostenko.battleship.domain.model;

import java.time.Instant;
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

    private static Map<Seat, List<Long>> immutableSamples(Map<Seat, List<Long>> samples, String name) {
        Objects.requireNonNull(samples, name);
        return samples.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
