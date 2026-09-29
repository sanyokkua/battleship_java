package ua.kostenko.battleship.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.*;
import ua.kostenko.battleship.domain.transition.*;

class RulesetRulesTest {
    private static final String SEA = "sea-battle-10-ship.v1";
    private static final String CLASSIC = "hasbro-classic-2002.v1";
    private static final Instant BASE = Instant.parse("2026-09-29T12:00:00Z");

    // Detects missing resolution/accounting or non-atomic ending.
    @ParameterizedTest
    @ValueSource(strings = {SEA, CLASSIC})
    void fullGameDestroysFleetAtomically(String id) {
        GameState state = playing(id);
        int shots = 0;
        int sinks = 0;
        for (Ship ship : state.guest().board().fleet()) {
            for (Coordinate target : ship.cells()) {
                if (state.turn() == Seat.GUEST)
                    state = fire(state, Seat.GUEST, c(9 - shots / 8, 2 + shots % 8), 21 + shots * 2)
                            .next();
                GameState before = state;
                Transition result = fire(state, Seat.HOST, target, 22 + shots * 2);
                accepted(result, before);
                state = result.next();
                shots++;
                if (state.lastShot().result() == ShotResult.SUNK) sinks++;
                assertThat(state.lastShot().sunkShipId())
                        .isEqualTo(state.lastShot().result() == ShotResult.SUNK ? ship.shipId() : null);
                assertThat(before.guest().board().incomingShots()).doesNotContain(target);
                assertThat(state.guest().board().incomingShots()).contains(target);
                assertThat(state.host().shotsFired()).hasSize(shots);
                assertThat(state.host().shotsFired().getLast()).isEqualTo(state.lastShot());
                assertThat(state.phase())
                        .isEqualTo(shots == (id.equals(SEA) ? 20 : 17) ? Phase.FINISHED : Phase.PLAYING);
            }
        }
        assertThat(shots).isEqualTo(id.equals(SEA) ? 20 : 17);
        assertThat(sinks).isEqualTo(id.equals(SEA) ? 10 : 5);
        assertThat(state.guest().board().fleet()).allMatch(s -> s.status() == Ship.ShipStatus.SUNK);
        assertThat(state.outcome()).isEqualTo(new Outcome(Seat.HOST, Outcome.Reason.FLEET_DESTROYED));
        assertThat(state.turn()).isNull();
        assertThat(state.timeline().finishedAt()).isEqualTo(BASE.plusSeconds(22 + (shots - 1) * 2));
        long total = state.timeline().turnDurationsMs().values().stream()
                .flatMap(List::stream)
                .mapToLong(Long::longValue)
                .sum();
        assertThat(total).isEqualTo((22 + (shots - 1) * 2 - 20) * 1000L);
        for (Seat seat : Seat.values())
            assertThat(state.timeline().shotDecisionDurationsMs().getOrDefault(seat, List.of()))
                    .hasSize(player(state, seat).shotsFired().size());
    }

    // Detects incorrect diagonal, overlap or edge rules.
    @ParameterizedTest
    @ValueSource(strings = {SEA, CLASSIC})
    void placementsRespectRulesetAdjacency(String id) {
        GameState state = apply(
                        joined(id), Seat.HOST, new GameCommand.PlaceShip("s01", c(0, 0), Orientation.HORIZONTAL), 1)
                .next();
        Transition diagonal = apply(
                state,
                Seat.HOST,
                new GameCommand.PlaceShip("s02", c(1, id.equals(SEA) ? 4 : 5), Orientation.VERTICAL),
                2);
        assertThat(diagonal.rejection() == null).isEqualTo(id.equals(CLASSIC));
        assertThat(apply(state, Seat.HOST, new GameCommand.PlaceShip("s02", c(0, 0), Orientation.VERTICAL), 2)
                        .rejection()
                        .code())
                .isEqualTo(Rejection.ProblemCode.PLACEMENT_OVERLAP);
        assertThat(apply(state, Seat.HOST, new GameCommand.PlaceShip("s02", c(9, 9), Orientation.HORIZONTAL), 2)
                        .rejection()
                        .code())
                .isEqualTo(Rejection.ProblemCode.PLACEMENT_OUT_OF_BOUNDS);
    }

    // Detects incorrect revealed-neighbour union, diagonal/edge clipping or shot exclusion.
    @Test
    void seaSinkRevealsExactUnknownWater() {
        GameState state = playing(SEA);
        state = fire(state, Seat.HOST, c(1, 0), 21).next();
        assertThat(state.lastShot()).isNotNull();
        assertThat(state.lastShot().result()).isEqualTo(ShotResult.MISS);
        assertThat(state.turn()).isEqualTo(Seat.GUEST);
        state = fire(state, Seat.GUEST, c(9, 9), 22).next();
        for (int col = 0; col < 4; col++)
            state = fire(state, Seat.HOST, c(0, col), 23 + col).next();
        assertThat(state.lastShot().result()).isEqualTo(ShotResult.SUNK);
        assertThat(state.turn()).isEqualTo(Seat.HOST);
        assertThat(state.guest().board().revealedWater())
                .containsExactlyInAnyOrder(c(0, 4), c(1, 1), c(1, 2), c(1, 3), c(1, 4));
        assertThat(state.guest().board().incomingShots())
                .containsExactlyInAnyOrder(c(1, 0), c(0, 0), c(0, 1), c(0, 2), c(0, 3));
        state = fire(state, Seat.HOST, c(6, 2), 28).next();
        assertThat(state.guest().board().revealedWater())
                .contains(c(5, 1), c(5, 2), c(5, 3), c(6, 1), c(6, 3), c(7, 1), c(7, 2), c(7, 3));
        assertThat(state.guest().board().revealedWater()).doesNotContain(c(6, 0), c(6, 2), c(6, 4));
    }

    // Detects ignoring either classic flag.
    @Test
    void classicSinkPassesWithoutRevealing() {
        GameState state = playing(CLASSIC);
        for (int col = 0; col < 5; col++) {
            state = fire(state, Seat.HOST, c(0, col), 21 + col * 2).next();
            assertThat(state.turn()).isEqualTo(Seat.GUEST);
            assertThat(state.guest().board().revealedWater()).isEmpty();
            if (col < 4)
                state = fire(state, Seat.GUEST, c(9, col), 22 + col * 2).next();
        }
        assertThat(state.lastShot().result()).isEqualTo(ShotResult.SUNK);
    }

    // Detects consuming version/turn/time for disclosed cells.
    @Test
    void duplicatesCostNothingForMissHitSunkAndRevealedWater() {
        GameState state = playing(SEA);
        state = fire(state, Seat.HOST, c(9, 9), 21).next();
        state = fire(state, Seat.GUEST, c(9, 9), 22).next();
        refused(fire(state, Seat.HOST, c(9, 9), 23), state, Rejection.ProblemCode.TARGET_ALREADY_FIRED);
        state = fire(state, Seat.HOST, c(0, 0), 24).next();
        assertThat(state.lastShot().result()).isEqualTo(ShotResult.HIT);
        assertThat(state.turn()).isEqualTo(Seat.HOST);
        refused(fire(state, Seat.HOST, c(0, 0), 25), state, Rejection.ProblemCode.TARGET_ALREADY_FIRED);
        state = fire(state, Seat.HOST, c(6, 0), 26).next();
        refused(fire(state, Seat.HOST, c(6, 0), 27), state, Rejection.ProblemCode.TARGET_ALREADY_FIRED);
        refused(fire(state, Seat.HOST, c(5, 1), 28), state, Rejection.ProblemCode.TARGET_ALREADY_FIRED);
    }

    // Detects missing phase/turn guards and wrong validation pointer.
    @ParameterizedTest
    @ValueSource(strings = {SEA, CLASSIC})
    void fireGuardsTurnPhaseAndTargetBounds(String id) {
        GameState state = playing(id);
        refused(fire(state, Seat.GUEST, c(0, 0), 21), state, Rejection.ProblemCode.ACTION_NOT_ALLOWED);
        for (int axis = 0; axis < 2; axis++) {
            Transition result = fire(state, Seat.HOST, axis == 0 ? c(10, 0) : c(0, 10), 21);
            refused(result, state, Rejection.ProblemCode.VALIDATION_FAILED);
            assertThat(result.rejection().field())
                    .isEqualTo(axis == 0 ? "/command/target/rowIndex" : "/command/target/columnIndex");
            assertThat(result.rejection().rule()).isEqualTo("OUT_OF_RANGE");
        }
        for (GameState outside : List.of(
                GameState.create(Rulesets.byId(id).orElseThrow(), "Host"),
                joined(id),
                apply(state, Seat.HOST, new GameCommand.Resign(), 22).next())) {
            for (Seat seat : Seat.values()) {
                refused(fire(outside, seat, c(0, 0), 23), outside, Rejection.ProblemCode.ACTION_NOT_ALLOWED);
                refused(
                        apply(outside, seat, new GameCommand.Resign(), 23),
                        outside,
                        Rejection.ProblemCode.ACTION_NOT_ALLOWED);
            }
        }
    }

    // Detects holder-only resignation or closing resigning seat rather than holder.
    @ParameterizedTest
    @ValueSource(strings = {SEA, CLASSIC})
    void eitherSeatResignsAtomically(String id) {
        for (Seat seat : Seat.values()) {
            GameState before = playing(id);
            Transition result = apply(before, seat, new GameCommand.Resign(), 27);
            accepted(result, before);
            GameState after = result.next();
            assertThat(after.phase()).isEqualTo(Phase.FINISHED);
            assertThat(after.outcome()).isEqualTo(new Outcome(other(seat), Outcome.Reason.RESIGNATION));
            assertThat(after.turn()).isNull();
            assertThat(after.host()).isSameAs(before.host());
            assertThat(after.guest()).isSameAs(before.guest());
            assertThat(after.lastShot()).isSameAs(before.lastShot());
            assertThat(after.timeline().finishedAt()).isEqualTo(BASE.plusSeconds(27));
            assertThat(after.timeline().turnDurationsMs()).containsExactlyEntriesOf(Map.of(Seat.HOST, List.of(7000L)));
            assertThat(after.timeline().shotDecisionDurationsMs()).isEmpty();
        }
    }

    // Detects resetting turn on retained hit or resetting decision on refusal.
    @Test
    void exactTimingSeparatesDecisionsAndTurns() {
        GameState state = fire(playing(SEA), Seat.HOST, c(0, 0), 22).next();
        assertThat(state.timeline().turnStartedAt()).isEqualTo(BASE.plusSeconds(20));
        assertThat(state.timeline().shotDecisionStartedAt())
                .containsExactlyEntriesOf(Map.of(Seat.HOST, BASE.plusSeconds(22)));
        refused(fire(state, Seat.HOST, c(0, 0), 25), state, Rejection.ProblemCode.TARGET_ALREADY_FIRED);
        state = fire(state, Seat.HOST, c(0, 1), 27).next();
        state = fire(state, Seat.HOST, c(9, 9), 30).next();
        assertThat(state.timeline().turnStartedAt()).isEqualTo(BASE.plusSeconds(30));
        assertThat(state.timeline().shotDecisionStartedAt())
                .containsExactlyEntriesOf(Map.of(Seat.GUEST, BASE.plusSeconds(30)));
        state = fire(state, Seat.GUEST, c(9, 8), 34).next();
        state = apply(state, Seat.GUEST, new GameCommand.Resign(), 40).next();
        assertThat(state.timeline().turnDurationsMs())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(Seat.HOST, List.of(10000L, 6000L), Seat.GUEST, List.of(4000L)));
        assertThat(state.timeline().shotDecisionDurationsMs())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(Seat.HOST, List.of(2000L, 5000L, 3000L), Seat.GUEST, List.of(4000L)));
        assertThat(state.timeline().finishedAt()).isEqualTo(BASE.plusSeconds(40));
    }

    // Detects independently truncated turn intervals losing milliseconds at pass/resignation.
    @Test
    void fractionalTurnBoundariesTelescopeThroughResignation() {
        Instant started = BASE.plusSeconds(20).plusNanos(250_000);
        GameState state = playing(SEA, started);
        state = at(state, Seat.HOST, new GameCommand.Fire(c(9, 9)), started.plusNanos(1_500_000));
        state = at(state, Seat.HOST, new GameCommand.Resign(), started.plusNanos(3_000_000));
        assertThat(state.timeline().turnDurationsMs())
                .containsExactlyInAnyOrderEntriesOf(Map.of(Seat.HOST, List.of(1L), Seat.GUEST, List.of(2L)));
        assertThat(state.timeline().shotDecisionDurationsMs()).containsExactlyEntriesOf(Map.of(Seat.HOST, List.of(1L)));
        assertThat(state.timeline().playStartedAt()).isEqualTo(started);
        assertThat(state.timeline().finishedAt()).isEqualTo(started.plusNanos(3_000_000));
        assertThat(turnTotal(state)).isEqualTo(3L);
    }

    // Detects losing a sub-millisecond remainder at a pass or final sink.
    @Test
    void fractionalTurnBoundariesTelescopeThroughFleetDestruction() {
        Instant started = BASE.plusSeconds(20).plusNanos(750_000);
        GameState state = playing(SEA, started);
        state = at(state, Seat.HOST, new GameCommand.Fire(c(9, 9)), started.plusNanos(1_500_000));
        state = at(state, Seat.GUEST, new GameCommand.Fire(c(9, 9)), started.plusNanos(2_000_000));
        int fired = 0;
        for (Ship ship : state.guest().board().fleet()) {
            for (Coordinate target : ship.cells()) {
                fired++;
                state = at(
                        state,
                        Seat.HOST,
                        new GameCommand.Fire(target),
                        started.plusNanos(fired == 20 ? 3_000_000 : 2_000_000));
            }
        }
        assertThat(state.phase()).isEqualTo(Phase.FINISHED);
        assertThat(state.outcome()).isEqualTo(new Outcome(Seat.HOST, Outcome.Reason.FLEET_DESTROYED));
        assertThat(state.timeline().turnDurationsMs())
                .containsExactlyInAnyOrderEntriesOf(Map.of(Seat.HOST, List.of(1L, 1L), Seat.GUEST, List.of(1L)));
        assertThat(state.timeline().finishedAt()).isEqualTo(started.plusNanos(3_000_000));
        assertThat(turnTotal(state)).isEqualTo(3L);
    }

    private static long turnTotal(GameState state) {
        return state.timeline().turnDurationsMs().values().stream()
                .flatMap(List::stream)
                .mapToLong(Long::longValue)
                .sum();
    }

    private static GameState at(GameState state, Seat seat, GameCommand command, Instant now) {
        Transition result = GameRules.apply(state, seat, command, now, new SeededRandomSource(42));
        assertThat(result.rejection()).isNull();
        return result.next();
    }

    private static Coordinate c(int row, int col) {
        return new Coordinate(row, col);
    }

    private static Seat other(Seat seat) {
        return seat == Seat.HOST ? Seat.GUEST : Seat.HOST;
    }

    private static PlayerState player(GameState state, Seat seat) {
        return seat == Seat.HOST ? state.host() : state.guest();
    }

    private static GameState joined(String id) {
        return GameState.create(Rulesets.byId(id).orElseThrow(), "Host").withGuest("Guest", BASE);
    }

    private static Transition apply(GameState state, Seat seat, GameCommand command, int seconds) {
        return GameRules.apply(state, seat, command, BASE.plusSeconds(seconds), new SeededRandomSource(42));
    }

    private static Transition fire(GameState state, Seat seat, Coordinate target, int seconds) {
        Transition result = apply(state, seat, new GameCommand.Fire(target), seconds);
        if (result.rejection() == null) assertThat(result.next().lastShot()).isNotNull();
        return result;
    }

    private static GameState playing(String id) {
        return playing(id, BASE.plusSeconds(20));
    }

    private static GameState playing(String id, Instant started) {
        GameState state = joined(id);
        int[][] anchors = id.equals(SEA)
                ? new int[][] {{0, 0}, {2, 0}, {2, 4}, {4, 0}, {4, 3}, {4, 6}, {6, 0}, {6, 2}, {6, 4}, {6, 6}}
                : new int[][] {{0, 0}, {2, 0}, {4, 0}, {6, 0}, {8, 0}};
        for (Seat seat : Seat.values())
            for (int i = 0; i < anchors.length; i++) {
                Transition placed = apply(
                        state,
                        seat,
                        new GameCommand.PlaceShip(
                                "s%02d".formatted(i + 1), c(anchors[i][0], anchors[i][1]), Orientation.HORIZONTAL),
                        1);
                assertThat(placed.rejection()).isNull();
                state = placed.next();
            }
        state = apply(state, Seat.HOST, new GameCommand.Ready(), 10).next();
        return at(state, Seat.GUEST, new GameCommand.Ready(), started);
    }

    private static void accepted(Transition result, GameState before) {
        assertThat(result.rejection()).isNull();
        assertThat(result.versionBumped()).isTrue();
        assertThat(result.next().version()).isEqualTo(before.version() + 1);
        assertThat(result.viewChanged()).containsExactlyInAnyOrder(Seat.HOST, Seat.GUEST);
    }

    private static void refused(Transition result, GameState before, Rejection.ProblemCode code) {
        assertThat(result.rejection()).isNotNull();
        assertThat(result.rejection().code()).isEqualTo(code);
        assertThat(result.next()).isSameAs(before);
        assertThat(result.next().timeline()).isSameAs(before.timeline());
        assertThat(result.versionBumped()).isFalse();
        assertThat(result.viewChanged()).isEmpty();
    }
}
