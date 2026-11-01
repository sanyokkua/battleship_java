package ua.kostenko.battleship.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.rules.FleetEntry;
import ua.kostenko.battleship.domain.rules.Ruleset;
import ua.kostenko.battleship.domain.rules.Rulesets;

class GameCreationTest {
    private static final Pattern SHIP_ID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    @Test
    void createStartsWaitingWithoutGuestOrFleets() {
        GameState state = GameState.create(ruleset("sea-battle-10-ship.v1"), "Ada");

        assertThat(state.rulesetId()).isEqualTo("sea-battle-10-ship.v1");
        assertThat(state.phase()).isEqualTo(Phase.WAITING);
        assertThat(state.version()).isZero();
        assertThat(state.host().displayName()).isEqualTo("Ada");
        assertThat(state.host().board().fleet()).isEmpty();
        assertThat(state.guest()).isNull();
        assertThat(state.turn()).isNull();
        assertThat(state.lastShot()).isNull();
        assertThat(state.outcome()).isNull();
        assertThat(state.timeline().guestJoinedAt()).isNull();
        assertThat(state.timeline().turnStartedAt()).isNull();
        assertThat(state.timeline().shotDecisionStartedAt()).isEmpty();
    }

    @Test
    void joiningCreatesPlacementStateAndRecordsThePassedInstant() {
        Instant now = Instant.parse("2026-09-29T12:34:56Z");
        GameState state =
                GameState.create(ruleset("hasbro-classic-2002.v1"), "Ada").withGuest("Grace", now);

        assertThat(state.phase()).isEqualTo(Phase.PLACEMENT);
        assertThat(state.version()).isEqualTo(1);
        assertThat(state.guest().displayName()).isEqualTo("Grace");
        assertThat(state.timeline().guestJoinedAt()).isSameAs(now);
        assertThat(state.timeline().turnStartedAt()).isNull();
        assertThat(state.timeline().shotDecisionStartedAt()).isEmpty();
        assertThat(state.timeline().readyAt()).isEmpty();
        assertThat(state.timeline().playStartedAt()).isNull();
        assertThat(state.timeline().finishedAt()).isNull();
        assertThat(state.timeline().turnDurationsMs()).isEmpty();
        assertThat(state.timeline().shotDecisionDurationsMs()).isEmpty();
    }

    @Test
    void eachRulesetCreatesCompleteFleetInOrderWithIdsResetOnEachBoard() {
        for (Ruleset ruleset : Rulesets.all()) {
            GameState state = GameState.create(ruleset, "Host").withGuest("Guest", Instant.EPOCH);
            List<Ship> hostFleet = state.host().board().fleet();
            List<Ship> guestFleet = state.guest().board().fleet();
            int expectedSize =
                    ruleset.fleet().stream().mapToInt(FleetEntry::count).sum();

            assertThat(hostFleet).hasSize(expectedSize);
            assertThat(guestFleet).hasSize(expectedSize);
            assertThat(hostFleet).extracting(Ship::shipId).containsExactlyElementsOf(ids(expectedSize));
            assertThat(guestFleet).extracting(Ship::shipId).containsExactlyElementsOf(ids(expectedSize));
            assertThat(hostFleet).extracting(Ship::shipId).allMatch(id -> SHIP_ID.matcher(id)
                    .matches());
            assertThat(guestFleet).extracting(Ship::shipId).allMatch(id -> SHIP_ID.matcher(id)
                    .matches());
            assertThat(hostFleet)
                    .extracting(Ship::shipTypeId, Ship::length)
                    .containsExactlyElementsOf(fleetTypesAndLengths(ruleset));
            assertThat(guestFleet)
                    .extracting(Ship::shipTypeId, Ship::length)
                    .containsExactlyElementsOf(fleetTypesAndLengths(ruleset));
            assertThat(hostFleet).allSatisfy(ship -> assertThat(ship.cells()).isEmpty());
            assertThat(guestFleet).allSatisfy(ship -> assertThat(ship.cells()).isEmpty());
        }
    }

    @Test
    void shipCellsAreDerivedForBothOrientationsAndEmptyWhenUnplaced() {
        Ship horizontal = new Ship("s01", "ship-3", 3, new Coordinate(2, 4), Orientation.HORIZONTAL, Set.of());
        Ship vertical = new Ship("s02", "ship-3", 3, new Coordinate(2, 4), Orientation.VERTICAL, Set.of());
        Ship unplaced = new Ship("s03", "ship-3", 3, null, null, Set.of());

        assertThat(horizontal.cells())
                .containsExactly(new Coordinate(2, 4), new Coordinate(2, 5), new Coordinate(2, 6));
        assertThat(vertical.cells()).containsExactly(new Coordinate(2, 4), new Coordinate(3, 4), new Coordinate(4, 4));
        assertThat(unplaced.cells()).isEmpty();
        assertThat(unplaced.status()).isEqualTo(Ship.ShipStatus.INTACT);
        var horizontalCells = horizontal.cells();
        var extraCell = new Coordinate(3, 4);
        assertThatThrownBy(() -> horizontalCells.add(extraCell)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shipStatusReflectsZeroSomeAndAllCellHits() {
        Ship intact = new Ship("s01", "ship-3", 3, new Coordinate(0, 0), Orientation.HORIZONTAL, Set.of());
        Ship damaged = new Ship(
                "s02", "ship-3", 3, new Coordinate(0, 0), Orientation.HORIZONTAL, Set.of(new Coordinate(0, 1)));
        Ship sunk = new Ship(
                "s03",
                "ship-3",
                3,
                new Coordinate(0, 0),
                Orientation.HORIZONTAL,
                Set.of(new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(0, 2)));

        assertThat(intact.status()).isEqualTo(Ship.ShipStatus.INTACT);
        assertThat(damaged.status()).isEqualTo(Ship.ShipStatus.DAMAGED);
        assertThat(sunk.status()).isEqualTo(Ship.ShipStatus.SUNK);
    }

    @Test
    void recordsDefensivelyCopyStoredAndReturnedCollections() {
        List<Ship> fleet = new ArrayList<>();
        Set<Coordinate> incoming = new HashSet<>();
        Set<Coordinate> revealed = new HashSet<>();
        Board board = new Board(fleet, incoming, revealed);
        List<Shot> shots = new ArrayList<>();
        PlayerState player = new PlayerState("Ada", false, board, shots);
        Set<Coordinate> hits = new HashSet<>();
        Ship ship = new Ship("s01", "ship-1", 1, null, null, hits);
        List<Long> turnSamples = new ArrayList<>(List.of(12L));
        Timeline timeline = new Timeline(
                null,
                java.util.Map.of(),
                null,
                null,
                null,
                java.util.Map.of(),
                java.util.Map.of(Seat.HOST, turnSamples),
                java.util.Map.of(Seat.GUEST, List.of(7L)));
        GameState state = GameState.create(ruleset("hasbro-classic-2002.v1"), "Ada");

        fleet.add(ship);
        incoming.add(new Coordinate(0, 0));
        revealed.add(new Coordinate(0, 1));
        shots.add(new Shot(Seat.HOST, new Coordinate(0, 0), ShotResult.MISS, null));
        hits.add(new Coordinate(0, 0));
        turnSamples.add(13L);

        assertThat(board.fleet()).isEmpty();
        assertThat(board.incomingShots()).isEmpty();
        assertThat(board.revealedWater()).isEmpty();
        assertThat(player.shotsFired()).isEmpty();
        assertThat(ship.hits()).isEmpty();
        assertThat(timeline.turnDurationsMs().get(Seat.HOST)).containsExactly(12L);
        var immutableFleet = board.fleet();
        var immutableIncoming = board.incomingShots();
        var immutableWater = board.revealedWater();
        var immutableShots = player.shotsFired();
        var immutableHits = ship.hits();
        var readyTimes = timeline.readyAt();
        var decisionStarts = timeline.shotDecisionStartedAt();
        var turnDurations = timeline.turnDurationsMs();
        var hostTurnDurations = turnDurations.get(Seat.HOST);
        var decisionDurations = timeline.shotDecisionDurationsMs();
        var sampleCoordinate = new Coordinate(1, 1);
        var sampleShot = new Shot(Seat.HOST, new Coordinate(0, 0), ShotResult.MISS, null);
        var sampleDuration = List.of(1L);
        assertThatThrownBy(() -> immutableFleet.add(ship)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> immutableIncoming.add(sampleCoordinate))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> immutableWater.add(sampleCoordinate))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> immutableShots.add(sampleShot)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> immutableHits.add(sampleCoordinate)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> readyTimes.put(Seat.HOST, Instant.EPOCH))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> decisionStarts.put(Seat.HOST, Instant.EPOCH))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> turnDurations.put(Seat.GUEST, sampleDuration))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> hostTurnDurations.add(2L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> decisionDurations.put(Seat.HOST, sampleDuration))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(state.host().board().fleet()).isEmpty();
    }

    @Test
    void shotRequiresSunkShipIdExactlyForSunkResult() {
        Coordinate target = new Coordinate(0, 0);
        assertThatThrownBy(() -> new Shot(Seat.HOST, target, ShotResult.SUNK, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Shot(Seat.HOST, target, ShotResult.HIT, "s01"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Ruleset ruleset(String id) {
        return Rulesets.byId(id).orElseThrow();
    }

    private static List<String> ids(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> "s%02d".formatted(index))
                .toList();
    }

    private static List<org.assertj.core.groups.Tuple> fleetTypesAndLengths(Ruleset ruleset) {
        List<org.assertj.core.groups.Tuple> result = new ArrayList<>();
        ruleset.fleet().forEach(entry -> {
            for (int index = 0; index < entry.count(); index++) {
                result.add(org.assertj.core.api.Assertions.tuple(entry.shipTypeId(), entry.length()));
            }
        });
        return result;
    }
}
