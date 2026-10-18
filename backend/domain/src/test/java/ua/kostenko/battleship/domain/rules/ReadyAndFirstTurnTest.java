package ua.kostenko.battleship.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Outcome;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.model.Ship;
import ua.kostenko.battleship.domain.model.Timeline;
import ua.kostenko.battleship.domain.transition.Rejection.ProblemCode;
import ua.kostenko.battleship.domain.transition.Transition;

class ReadyAndFirstTurnTest {
    private static final Instant JOINED = Instant.parse("2026-09-29T12:00:00Z");
    private static final Instant EARLY = JOINED.plusSeconds(10);
    private static final Instant LATE = JOINED.plusSeconds(20);
    private static final RandomSource NO_RANDOM = bound -> {
        throw new AssertionError("Only an exact ready-time tie may draw randomness");
    };

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void incompleteFleetCannotReadyAndRefusalPreservesIdentity(String id) {
        GameState empty = state(id);
        GameState partial = GameRules.apply(
                        empty,
                        Seat.HOST,
                        new GameCommand.PlaceShip("s01", new Coordinate(0, 0), Orientation.HORIZONTAL),
                        EARLY,
                        NO_RANDOM)
                .next();
        assertRefused(ready(empty, Seat.HOST, EARLY, NO_RANDOM), empty);
        assertRefused(ready(partial, Seat.HOST, EARLY, NO_RANDOM), partial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void firstReadyLocksActorAndChangesBothViewsWithoutStartingPlay(String id) {
        GameState before = arranged(id);
        Transition result = ready(before, Seat.HOST, EARLY, NO_RANDOM);
        assertAccepted(result, before);
        GameState after = result.next();
        assertThat(after.phase()).isEqualTo(Phase.PLACEMENT);
        assertThat(after.host().ready()).isTrue();
        assertThat(after.host().displayName()).isEqualTo("Host");
        assertThat(after.host().board()).isSameAs(before.host().board());
        assertThat(after.host().shotsFired()).isEqualTo(before.host().shotsFired());
        assertThat(after.guest()).isSameAs(before.guest());
        assertThat(after.timeline().readyAt()).containsExactlyEntriesOf(Map.of(Seat.HOST, EARLY));
        assertThat(after.timeline().guestJoinedAt()).isEqualTo(JOINED);
        assertThat(after.timeline().playStartedAt()).isNull();
        assertThat(after.timeline().turnStartedAt()).isNull();
        assertThat(after.timeline().shotDecisionStartedAt()).isEmpty();
        assertThat(after.turn()).isNull();
        assertThat(after.lastShot()).isEqualTo(before.lastShot());
        assertThat(after.outcome()).isEqualTo(before.outcome());
        assertThat(before.host().ready()).isFalse();
        assertThat(before.timeline().readyAt()).isEmpty();
        assertThatThrownBy(() -> after.timeline().readyAt().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void readyActorCannotRepeatReadyOrEditIncludingNoOpsAndInvalidEdits(String id) {
        for (Seat actor : Seat.values()) {
            GameState before = ready(arranged(id), actor, EARLY, NO_RANDOM).next();
            Ship ship = player(before, actor).board().fleet().getFirst();
            List<GameCommand> commands = List.of(
                    new GameCommand.Ready(),
                    new GameCommand.PlaceShip(ship.shipId(), ship.anchor(), ship.orientation()),
                    new GameCommand.PlaceShip("unknown", new Coordinate(100, 100), Orientation.HORIZONTAL),
                    new GameCommand.RemoveShip(ship.shipId()),
                    new GameCommand.RemoveShip("unknown"),
                    new GameCommand.PlaceFleetRandomly(1000),
                    new GameCommand.ClearFleet());
            for (GameCommand command : commands) {
                assertRefused(GameRules.apply(before, actor, command, LATE, NO_RANDOM), before);
            }
            Seat other = actor == Seat.HOST ? Seat.GUEST : Seat.HOST;
            Transition edit = GameRules.apply(before, other, new GameCommand.RemoveShip("s01"), LATE, NO_RANDOM);
            assertThat(edit.rejection()).isNull();
            assertThat(player(edit.next(), actor).ready()).isTrue();
            assertThat(player(edit.next(), actor)).isSameAs(player(before, actor));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void earlierHostStartsEvenWhenHostReadiesSecond(String id) {
        GameState first = ready(arranged(id), Seat.GUEST, LATE, NO_RANDOM).next();
        assertStarted(ready(first, Seat.HOST, EARLY, NO_RANDOM), first, Seat.HOST, EARLY, EARLY, LATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void earlierGuestStartsEvenWhenGuestReadiesSecond(String id) {
        GameState first = ready(arranged(id), Seat.HOST, LATE, NO_RANDOM).next();
        assertStarted(ready(first, Seat.GUEST, EARLY, NO_RANDOM), first, Seat.GUEST, EARLY, LATE, EARLY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void earlierReadyStartsInOrdinaryInvocationOrder(String id) {
        for (Seat actor : Seat.values()) {
            Seat other = actor == Seat.HOST ? Seat.GUEST : Seat.HOST;
            GameState first = ready(arranged(id), actor, EARLY, NO_RANDOM).next();
            assertStarted(
                    ready(first, other, LATE, NO_RANDOM),
                    first,
                    actor,
                    LATE,
                    actor == Seat.HOST ? EARLY : LATE,
                    actor == Seat.GUEST ? EARLY : LATE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void exactTieUsesSeededRandomnessToSelectEitherSeatReproducibly(String id) {
        GameState first = ready(arranged(id), Seat.HOST, EARLY, NO_RANDOM).next();
        for (int repetition = 0; repetition < 2; repetition++) {
            assertStarted(
                    ready(first, Seat.GUEST, EARLY, new SeededRandomSource(4096)),
                    first,
                    Seat.HOST,
                    EARLY,
                    EARLY,
                    EARLY);
            assertStarted(
                    ready(first, Seat.GUEST, EARLY, new SeededRandomSource(0)), first, Seat.GUEST, EARLY, EARLY, EARLY);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sea-battle-10-ship.v1", "hasbro-classic-2002.v1"})
    void readyOutsidePlacementCannotChangeExistingPhaseOrTurn(String id) {
        GameState waiting = GameState.create(Rulesets.byId(id).orElseThrow(), "Host");
        assertThat(waiting.turn()).isNull();
        for (Seat actor : Seat.values()) assertRefused(ready(waiting, actor, EARLY, NO_RANDOM), waiting);
        GameState first = ready(arranged(id), Seat.HOST, EARLY, NO_RANDOM).next();
        GameState playing = ready(first, Seat.GUEST, LATE, NO_RANDOM).next();
        GameState finished = new GameState(
                id,
                Phase.FINISHED,
                playing.version() + 1,
                playing.host(),
                playing.guest(),
                null,
                null,
                new Outcome(Seat.HOST, Outcome.Reason.RESIGNATION),
                playing.timeline());
        GameState abandoned = new GameState(
                id, Phase.ABANDONED, 2, state(id).host(), state(id).guest(), null, null, null, Timeline.empty());
        for (GameState before : List.of(playing, finished, abandoned)) {
            for (Seat actor : Seat.values()) assertRefused(ready(before, actor, LATE, NO_RANDOM), before);
            assertThat(before.turn()).isEqualTo(before.phase() == Phase.PLAYING ? Seat.HOST : null);
        }
    }

    private static void assertStarted(
            Transition result,
            GameState before,
            Seat expected,
            Instant started,
            Instant hostReady,
            Instant guestReady) {
        assertAccepted(result, before);
        GameState after = result.next();
        assertThat(after.phase()).isEqualTo(Phase.PLAYING);
        assertThat(after.turn()).isEqualTo(expected);
        assertThat(after.host().ready()).isTrue();
        assertThat(after.guest().ready()).isTrue();
        assertThat(after.host().board()).isSameAs(before.host().board());
        assertThat(after.guest().board()).isSameAs(before.guest().board());
        assertThat(after.timeline().readyAt())
                .containsExactlyInAnyOrderEntriesOf(Map.of(Seat.HOST, hostReady, Seat.GUEST, guestReady));
        assertThat(after.timeline().guestJoinedAt()).isEqualTo(JOINED);
        assertThat(after.timeline().playStartedAt()).isEqualTo(started);
        assertThat(after.timeline().turnStartedAt()).isEqualTo(started);
        assertThat(after.timeline().shotDecisionStartedAt()).containsExactlyEntriesOf(Map.of(expected, started));
        assertThat(after.timeline().turnDurationsMs()).isEmpty();
        assertThat(after.timeline().shotDecisionDurationsMs()).isEmpty();
        assertThat(after.timeline().finishedAt()).isNull();
        assertThat(before.phase()).isEqualTo(Phase.PLACEMENT);
        assertThat(before.turn()).isNull();
        assertThat(before.timeline().readyAt()).hasSize(1);
        assertThat(before.timeline().playStartedAt()).isNull();
        assertThatThrownBy(() -> after.timeline().shotDecisionStartedAt().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertAccepted(Transition result, GameState before) {
        assertThat(result.rejection()).isNull();
        assertThat(result.versionBumped()).isTrue();
        assertThat(result.next().version()).isEqualTo(before.version() + 1);
        assertThat(result.viewChanged()).containsExactlyInAnyOrder(Seat.HOST, Seat.GUEST);
    }

    private static void assertRefused(Transition result, GameState before) {
        assertThat(result.rejection()).isNotNull();
        assertThat(result.rejection().code()).isEqualTo(ProblemCode.ACTION_NOT_ALLOWED);
        assertThat(result.next()).isSameAs(before);
        assertThat(result.next().timeline()).isSameAs(before.timeline());
        assertThat(result.next().version()).isEqualTo(before.version());
        assertThat(result.versionBumped()).isFalse();
        assertThat(result.viewChanged()).isEmpty();
    }

    private static Transition ready(GameState state, Seat actor, Instant now, RandomSource random) {
        return GameRules.apply(state, actor, new GameCommand.Ready(), now, random);
    }

    private static PlayerState player(GameState state, Seat seat) {
        return seat == Seat.HOST ? state.host() : state.guest();
    }

    private static GameState state(String id) {
        return GameState.create(Rulesets.byId(id).orElseThrow(), "Host").withGuest("Guest", JOINED);
    }

    private static GameState arranged(String id) {
        GameState state = state(id);
        for (Seat seat : Seat.values()) {
            Transition arranged = GameRules.apply(
                    state,
                    seat,
                    new GameCommand.PlaceFleetRandomly(10000),
                    JOINED,
                    new SeededRandomSource(seat == Seat.HOST ? 42 : 43));
            assertThat(arranged.rejection()).isNull();
            state = arranged.next();
        }
        return state;
    }
}
