package ua.kostenko.battleship.application.projection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.model.Ship;
import ua.kostenko.battleship.domain.model.ShotResult;
import ua.kostenko.battleship.domain.model.Timeline;

public record StatisticsView(MatchStatistics match, PlayerStatistics you, PlayerStatistics opponent) {
    public static StatisticsView from(GameState state, Seat caller) {
        Timeline timeline = state.timeline();
        Instant joined = timeline.guestJoinedAt();
        Instant started = timeline.playStartedAt();
        Instant finished = timeline.finishedAt();
        long placementDurationMs = elapsed(joined, started);
        long gameplayDurationMs = elapsed(started, finished);
        MatchStatistics match =
                new MatchStatistics(placementDurationMs + gameplayDurationMs, placementDurationMs, gameplayDurationMs);
        return new StatisticsView(
                match,
                player(state, timeline, caller),
                player(state, timeline, caller == Seat.HOST ? Seat.GUEST : Seat.HOST));
    }

    private static PlayerStatistics player(GameState state, Timeline timeline, Seat seat) {
        PlayerState player = seat == Seat.HOST ? state.host() : state.guest();
        int shots = player.shotsFired().size();
        long hits = player.shotsFired().stream()
                .filter(shot -> shot.result() == ShotResult.HIT || shot.result() == ShotResult.SUNK)
                .count();
        Double accuracy = shots == 0
                ? null
                : BigDecimal.valueOf(hits)
                        .divide(BigDecimal.valueOf(shots), 4, RoundingMode.HALF_UP)
                        .doubleValue();
        List<Ship> fleet = player.board().fleet();
        int intact = 0, damaged = 0, sunk = 0;
        for (Ship ship : fleet) {
            switch (ship.status()) {
                case INTACT -> intact++;
                case DAMAGED -> damaged++;
                case SUNK -> sunk++;
            }
        }
        return new PlayerStatistics(
                elapsed(timeline.guestJoinedAt(), timeline.readyAt().get(seat)),
                shots,
                (int) hits,
                accuracy,
                aggregate(timeline.turnDurationsMs().getOrDefault(seat, List.of())),
                aggregate(timeline.shotDecisionDurationsMs().getOrDefault(seat, List.of())),
                new FleetSummary(fleet.size(), intact, damaged, sunk));
    }

    private static long elapsed(Instant start, Instant end) {
        return Duration.between(start, end).toMillis();
    }

    private static DurationAggregate aggregate(List<Long> samples) {
        int count = samples.size();
        long total = samples.stream().mapToLong(Long::longValue).sum();
        if (count == 0) return new DurationAggregate(0, 0, null, null, null);
        return new DurationAggregate(
                count,
                total,
                BigDecimal.valueOf(total)
                        .divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP)
                        .longValueExact(),
                samples.stream().mapToLong(Long::longValue).min().orElseThrow(),
                samples.stream().mapToLong(Long::longValue).max().orElseThrow());
    }

    public record MatchStatistics(long totalDurationMs, long placementDurationMs, long gameplayDurationMs) {}

    public record PlayerStatistics(
            long placementDurationMs,
            int shots,
            int hits,
            Double accuracy,
            DurationAggregate turns,
            DurationAggregate shotDecisions,
            FleetSummary fleet) {}

    public record DurationAggregate(int count, long totalMs, Long averageMs, Long fastestMs, Long slowestMs) {}

    public record FleetSummary(int total, int intact, int damaged, int sunk) {}
}
