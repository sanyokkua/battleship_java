package ua.kostenko.battleship.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Board;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.model.Ship;
import ua.kostenko.battleship.domain.transition.Rejection.ProblemCode;
import ua.kostenko.battleship.domain.transition.Transition;

class RandomArrangementTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final String SEA = "sea-battle-10-ship.v1";
    private static final String HASBRO = "hasbro-classic-2002.v1";

    @Test
    void seededSeaBattleHasExactCellsAndLegalFleet() {
        GameState before = state(SEA);
        Transition result = random(before, Seat.HOST, 1000, new SeededRandomSource(42));
        assertChanged(result, before, Seat.HOST);
        assertThat(cells(result.next().host()))
                .containsExactly(
                        Set.of(at(3, 8), at(4, 8), at(5, 8), at(6, 8)),
                        Set.of(at(0, 5), at(0, 6), at(0, 7)),
                        Set.of(at(2, 2), at(2, 3), at(2, 4)),
                        Set.of(at(6, 0), at(7, 0)),
                        Set.of(at(6, 3), at(6, 4)),
                        Set.of(at(1, 0), at(2, 0)),
                        Set.of(at(7, 6)),
                        Set.of(at(9, 5)),
                        Set.of(at(0, 3)),
                        Set.of(at(9, 2)));
        assertLegalAndMetadata(
                before.host(), result.next().host(), Rulesets.byId(SEA).orElseThrow());
    }

    @Test
    void seededHasbroHasExactCellsAndLegalFleet() {
        GameState before = state(HASBRO);
        Transition result = random(before, Seat.GUEST, 1000, new SeededRandomSource(42));
        assertChanged(result, before, Seat.GUEST);
        assertThat(cells(result.next().guest()))
                .containsExactly(
                        Set.of(at(3, 8), at(4, 8), at(5, 8), at(6, 8), at(7, 8)),
                        Set.of(at(0, 5), at(0, 6), at(0, 7), at(0, 8)),
                        Set.of(at(2, 2), at(2, 3), at(2, 4)),
                        Set.of(at(2, 6), at(2, 7), at(2, 8)),
                        Set.of(at(6, 0), at(7, 0)));
        assertLegalAndMetadata(
                before.guest(), result.next().guest(), Rulesets.byId(HASBRO).orElseThrow());
    }

    @Test
    void rerandomisingReplacesOldCellsButRetainsFleetOrderAndIds() {
        GameState before =
                random(state(SEA), Seat.HOST, 1000, new SeededRandomSource(42)).next();
        Transition result = random(before, Seat.HOST, 1000, new SeededRandomSource(99));
        assertChanged(result, before, Seat.HOST);
        assertThat(cells(result.next().host())).isNotEqualTo(cells(before.host()));
        assertLegalAndMetadata(
                before.host(), result.next().host(), Rulesets.byId(SEA).orElseThrow());
    }

    @Test
    void identicalRandomLayoutIsAcceptedNoOp() {
        GameState before =
                random(state(SEA), Seat.HOST, 1000, new SeededRandomSource(42)).next();
        assertNoOp(random(before, Seat.HOST, 1000, new SeededRandomSource(42)), before);
    }

    @Test
    void exhaustedCollisionBudgetRefusesOriginalArrangedStateAtomically() {
        GameState before =
                random(state(SEA), Seat.HOST, 1000, new SeededRandomSource(42)).next();
        // Removing the failed-candidate bound must trip the timeout. The source lets the worker stop safely.
        RandomSource colliding = bound -> {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("interrupted");
            return 0;
        };
        Transition result =
                assertTimeoutPreemptively(Duration.ofSeconds(1), () -> random(before, Seat.HOST, 1, colliding));
        assertThat(result.next()).isSameAs(before);
        assertThat(result.next().version()).isEqualTo(before.version());
        assertThat(result.versionBumped()).isFalse();
        assertThat(result.viewChanged()).isEmpty();
        assertThat(result.rejection().code()).isEqualTo(ProblemCode.RANDOM_ARRANGEMENT_FAILED);
        assertThat(result.rejection().code().wireCode()).isEqualTo("random-arrangement-failed");
    }

    @Test
    void longerConfiguredBudgetAllowsSuccessAfterTwoFailedCandidates() {
        // Out of bounds twice, then a complete non-overlapping Hasbro layout.
        int[] draws = {0, 9, 9, 0, 9, 9, 0, 0, 0, 0, 2, 0, 0, 4, 0, 0, 6, 0, 0, 8, 0};
        GameState before = state(HASBRO);
        Transition tooShort = random(before, Seat.HOST, 2, scripted(draws));
        assertThat(tooShort.next()).isSameAs(before);
        assertThat(tooShort.rejection().code()).isEqualTo(ProblemCode.RANDOM_ARRANGEMENT_FAILED);
        Transition sufficient = random(before, Seat.HOST, 3, scripted(draws));
        assertChanged(sufficient, before, Seat.HOST);
        assertLegalAndMetadata(
                before.host(), sufficient.next().host(), Rulesets.byId(HASBRO).orElseThrow());
    }

    @Test
    void attemptLimitMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameCommand.PlaceFleetRandomly(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new GameCommand.PlaceFleetRandomly(-1));
    }

    @Test
    void clearEitherSeatRetainsIdsAndMetadataAndOnlyChangesActor() {
        for (Seat seat : Seat.values()) {
            GameState before =
                    random(state(SEA), seat, 1000, new SeededRandomSource(42)).next();
            Transition result = apply(before, seat, new GameCommand.ClearFleet(), new SeededRandomSource(1));
            assertChanged(result, before, seat);
            PlayerState cleared = player(result.next(), seat);
            assertThat(cleared.board().fleet()).allSatisfy(ship -> {
                assertThat(ship.anchor()).isNull();
                assertThat(ship.orientation()).isNull();
                assertThat(ship.cells()).isEmpty();
                assertThat(ship.hits()).isEmpty();
            });
            assertThat(metadata(cleared)).isEqualTo(metadata(player(before, seat)));
        }
    }

    @Test
    void clearingAnEmptyFleetDoesNotReadRandomnessOrChangeState() {
        GameState before = state(SEA);
        for (Seat seat : Seat.values()) {
            assertNoOp(
                    apply(before, seat, new GameCommand.ClearFleet(), bound -> {
                        throw new AssertionError("clear must not draw randomness");
                    }),
                    before);
        }
    }

    @Test
    void longestShipIsPlacedFirstEvenWhenFleetOrderIsReversed() {
        GameState initial = state(HASBRO);
        List<Ship> reversed = initial.host().board().fleet().reversed();
        PlayerState host = new PlayerState("Host", false, new Board(reversed, Set.of(), Set.of()), List.of());
        GameState before = new GameState(
                initial.rulesetId(),
                initial.phase(),
                initial.version(),
                host,
                initial.guest(),
                initial.turn(),
                initial.lastShot(),
                initial.outcome(),
                initial.timeline());
        Transition result = random(before, Seat.HOST, 1000, new SeededRandomSource(42));
        assertChanged(result, before, Seat.HOST);
        assertThat(result.next().host().board().fleet().getLast().cells())
                .containsExactlyInAnyOrder(at(3, 8), at(4, 8), at(5, 8), at(6, 8), at(7, 8));
        assertLegalAndMetadata(
                before.host(), result.next().host(), Rulesets.byId(HASBRO).orElseThrow());
    }

    private static void assertLegalAndMetadata(PlayerState before, PlayerState after, Ruleset ruleset) {
        assertThat(metadata(after)).isEqualTo(metadata(before));
        List<Ship> fleet = after.board().fleet();
        for (int i = 0; i < fleet.size(); i++) {
            Ship ship = fleet.get(i);
            assertThat(ship.cells()).hasSize(ship.length());
            assertThat(ship.cells()).allSatisfy(cell -> {
                assertThat(cell.rowIndex()).isBetween(0, ruleset.rows() - 1);
                assertThat(cell.columnIndex()).isBetween(0, ruleset.columns() - 1);
                if (ship.orientation() == Orientation.HORIZONTAL)
                    assertThat(cell.rowIndex()).isEqualTo(ship.anchor().rowIndex());
                else assertThat(cell.columnIndex()).isEqualTo(ship.anchor().columnIndex());
            });
            for (int j = i + 1; j < fleet.size(); j++) {
                assertThat(ship.cells())
                        .doesNotContainAnyElementsOf(fleet.get(j).cells());
                if (!ruleset.shipsMayTouch())
                    for (Coordinate a : ship.cells())
                        for (Coordinate b : fleet.get(j).cells()) {
                            assertThat(Math.abs(a.rowIndex() - b.rowIndex()) > 1
                                            || Math.abs(a.columnIndex() - b.columnIndex()) > 1)
                                    .isTrue();
                        }
            }
        }
    }

    private static List<String> metadata(PlayerState player) {
        return player.board().fleet().stream()
                .map(s -> s.shipId() + ":" + s.shipTypeId() + ":" + s.length())
                .toList();
    }

    private static List<Set<Coordinate>> cells(PlayerState player) {
        return player.board().fleet().stream().map(Ship::cells).toList();
    }

    private static void assertChanged(Transition result, GameState before, Seat actor) {
        assertThat(result.rejection()).isNull();
        assertThat(result.versionBumped()).isTrue();
        assertThat(result.viewChanged()).containsExactly(actor);
        assertThat(result.next().version()).isEqualTo(before.version() + 1);
        assertThat(player(result.next(), actor == Seat.HOST ? Seat.GUEST : Seat.HOST))
                .isSameAs(player(before, actor == Seat.HOST ? Seat.GUEST : Seat.HOST));
        assertThat(result.next().timeline()).isSameAs(before.timeline());
        assertThat(result.next().phase()).isEqualTo(before.phase());
        assertThat(result.next().turn()).isEqualTo(before.turn());
        assertThat(result.next().lastShot()).isSameAs(before.lastShot());
        assertThat(result.next().outcome()).isSameAs(before.outcome());
        PlayerState original = player(before, actor), changed = player(result.next(), actor);
        assertThat(changed.displayName()).isEqualTo(original.displayName());
        assertThat(changed.ready()).isEqualTo(original.ready());
        assertThat(changed.shotsFired()).isEqualTo(original.shotsFired());
        assertThat(changed.board().incomingShots()).isEqualTo(original.board().incomingShots());
        assertThat(changed.board().revealedWater()).isEqualTo(original.board().revealedWater());
    }

    private static void assertNoOp(Transition result, GameState before) {
        assertThat(result.next()).isSameAs(before);
        assertThat(result.rejection()).isNull();
        assertThat(result.versionBumped()).isFalse();
        assertThat(result.viewChanged()).isEmpty();
    }

    private static RandomSource scripted(int... values) {
        return new RandomSource() {
            private int index;

            public int nextInt(int bound) {
                return values[index++];
            }
        };
    }

    private static PlayerState player(GameState state, Seat actor) {
        return actor == Seat.HOST ? state.host() : state.guest();
    }

    private static Transition random(GameState state, Seat actor, int limit, RandomSource source) {
        return apply(state, actor, new GameCommand.PlaceFleetRandomly(limit), source);
    }

    private static Transition apply(GameState state, Seat actor, GameCommand command, RandomSource source) {
        return GameRules.apply(state, actor, command, NOW, source);
    }

    private static GameState state(String id) {
        return GameState.create(Rulesets.byId(id).orElseThrow(), "Host").withGuest("Guest", NOW);
    }

    private static Coordinate at(int row, int column) {
        return new Coordinate(row, column);
    }
}
