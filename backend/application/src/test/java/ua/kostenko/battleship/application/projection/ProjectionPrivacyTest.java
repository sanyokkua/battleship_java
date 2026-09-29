package ua.kostenko.battleship.application.projection;

import static org.assertj.core.api.Assertions.*;
import static ua.kostenko.battleship.application.projection.BoardView.CellState.*;
import static ua.kostenko.battleship.application.projection.SnapshotView.Side.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.model.*;
import ua.kostenko.battleship.domain.rules.*;

class ProjectionPrivacyTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final SnapshotContext CONTEXT = new SnapshotContext(
            "game-1",
            NOW,
            NOW.plusSeconds(60),
            "https://example.test/join/game-1#invite=secret",
            NOW.plusSeconds(30),
            true,
            false);
    private final SnapshotProjector projector = new SnapshotProjector();

    // Break: include a hidden anchor, orientation, unsunk fleet or SHIP cell.
    @Test
    void hiddenPlacementsDoNotChangeEitherCallersView() {
        for (Seat caller : Seat.values())
            for (Phase phase : List.of(Phase.PLACEMENT, Phase.PLAYING, Phase.ABANDONED)) {
                GameState first = pair(phase, caller, false, false);
                GameState second = pair(phase, caller, true, false);
                assertThat(view(first, caller)).isNotNull().isEqualTo(view(second, caller));
            }
    }

    // Break: disclose the unhit tail/type/anchor of a damaged opponent ship.
    @Test
    void sharedNonSinkingHitDoesNotDiscloseTheRestOfTheShip() {
        for (Seat caller : Seat.values()) {
            SnapshotView first = view(pair(Phase.PLAYING, caller, false, true), caller);
            SnapshotView second = view(pair(Phase.PLAYING, caller, true, true), caller);
            assertThat(first).isNotNull().isEqualTo(second);
            assertThat(first.opponentBoard().grid().get(2).get(5)).isEqualTo(HIT);
            assertThat(first.opponentBoard().ships()).isEmpty();
        }
    }

    // Break: keep hiding fleet at FINISHED.
    @Test
    void finishedGamesDeliberatelyDiscloseDifferentPlacements() {
        for (Seat caller : Seat.values()) {
            SnapshotView first = view(pair(Phase.FINISHED, caller, false, false), caller);
            SnapshotView second = view(pair(Phase.FINISHED, caller, true, false), caller);
            assertThat(first.opponentBoard()).isNotEqualTo(second.opponentBoard());
            assertThat(first.opponentBoard().ships()).hasSize(10);
            assertThat(first.opponentBoard().grid().stream().flatMap(List::stream))
                    .doesNotContain(UNKNOWN);
        }
    }

    // Break: hide own unplaced ships or omit any of the six drawable cell states.
    @Test
    void ownBoardIsCompleteAcrossPhasesAndRetainsUnplacedFleet() {
        for (Phase phase : Phase.values()) {
            SnapshotView snapshot = view(pair(phase, Seat.HOST, false, false), Seat.HOST);
            assertThat(snapshot.yourBoard().grid()).hasSize(10).allSatisfy(row -> assertThat(row)
                    .hasSize(10));
            assertThat(snapshot.yourBoard().grid().stream().flatMap(List::stream))
                    .doesNotContain(UNKNOWN);
            assertThat(snapshot.yourBoard().ships()).hasSize(10);
        }
        GameState unplaced = GameState.create(
                        Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host")
                .withGuest("Guest", NOW);
        BoardView start = view(unplaced, Seat.HOST).yourBoard();
        assertThat(start.ships()).hasSize(10);
        assertThat(start.ships()).allSatisfy(ship -> {
            assertThat(ship.cells()).isEmpty();
            assertThat(ship.anchor()).isNull();
            assertThat(ship.orientation()).isNull();
        });
        BoardView own =
                view(pair(Phase.PLAYING, Seat.HOST, false, false), Seat.HOST).yourBoard();
        assertThat(own.grid().get(0).subList(0, 4)).containsExactly(HIT, SHIP, SHIP, SHIP);
        assertThat(own.grid().get(2).get(0)).isEqualTo(SUNK);
        assertThat(own.grid().get(9).get(9)).isEqualTo(MISS);
        assertThat(own.grid().get(1).get(0)).isEqualTo(REVEALED_WATER);
        assertThat(own.grid().get(9).get(0)).isEqualTo(WATER);
        assertThat(own.ships().getFirst().cells()).containsExactly(c(0, 0), c(0, 1), c(0, 2), c(0, 3));
    }

    // Break: include unsunk fleet or undiscovered SHIP/WATER.
    @Test
    void opponentShowsOnlyDiscoveredCellsAndSunkShips() {
        for (String id : List.of("sea-battle-10-ship.v1", "hasbro-classic-2002.v1")) {
            GameState state = fixture(Phase.PLAYING, id);
            SnapshotView snapshot = view(state, Seat.GUEST);
            assertThat(snapshot.opponentBoard().grid().stream().flatMap(List::stream))
                    .allMatch(cell ->
                            Set.of(UNKNOWN, MISS, HIT, SUNK, REVEALED_WATER).contains(cell));
            assertThat(snapshot.opponentBoard().ships()).hasSize(1).allSatisfy(ship -> assertThat(ship.status())
                    .isEqualTo(Ship.ShipStatus.SUNK));
            assertThat(snapshot.opponentBoard().grid().get(1).get(0))
                    .isEqualTo(id.equals("sea-battle-10-ship.v1") ? REVEALED_WATER : UNKNOWN);
            assertThat(snapshot.opponentBoard().grid().get(9).get(9)).isEqualTo(MISS);
            assertThat(snapshot.lastShot()).isEqualTo(new ShotView(YOU, c(2, 0), ShotResult.SUNK, "s02"));
        }
    }

    // Break: reveal even previously hit/sunk cells after abandonment.
    @Test
    void abandonmentHidesOpponentButPreservesOwnBoard() {
        for (Seat caller : Seat.values()) {
            SnapshotView snapshot = view(pair(Phase.ABANDONED, caller, false, true), caller);
            assertThat(snapshot.opponentBoard().grid().stream().flatMap(List::stream))
                    .containsOnly(UNKNOWN);
            assertThat(snapshot.opponentBoard().ships()).isEmpty();
            assertThat(snapshot.yourBoard().grid().stream().flatMap(List::stream))
                    .doesNotContain(UNKNOWN);
        }
    }

    // Break: carry HOST/GUEST or resolve any side from a fixed perspective.
    @Test
    void allSidesNamesAndConnectionsAreCallerRelative() throws Exception {
        GameState playing = fixture(Phase.PLAYING, "sea-battle-10-ship.v1");
        SnapshotView host = view(playing, Seat.HOST), guest = view(playing, Seat.GUEST);
        assertThat(host.turn()).isEqualTo(OPPONENT);
        assertThat(guest.turn()).isEqualTo(YOU);
        assertThat(host.lastShot()).isNotNull();
        assertThat(guest.lastShot()).isNotNull();
        assertThat(host.lastShot().by()).isEqualTo(OPPONENT);
        assertThat(guest.lastShot().by()).isEqualTo(YOU);
        assertThat(host.you()).isEqualTo(new PlayerView("Host", true, true, 9));
        assertThat(guest.opponent()).isEqualTo(host.you());
        assertThat(host.opponent()).isEqualTo(guest.you());
        assertThat(guest.you().displayName()).isEqualTo("Guest");
        assertThat(guest.you().connected()).isFalse();
        GameState finished = withPhase(playing, Phase.FINISHED);
        assertThat(view(finished, Seat.HOST).outcome())
                .isEqualTo(new SnapshotView.OutcomeView(YOU, Outcome.Reason.RESIGNATION));
        assertThat(view(finished, Seat.GUEST).outcome().winner()).isEqualTo(OPPONENT);
        assertNoSeat(host);
        assertNoSeat(view(finished, Seat.GUEST));
    }

    // Break: count only placed ships or fail to subtract sunk ships.
    @Test
    void remainingShipsCountFleetAtThreeMoments() {
        GameState joined = GameState.create(
                        Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host")
                .withGuest("Guest", NOW);
        assertThat(view(joined, Seat.HOST).you().shipsRemaining()).isEqualTo(10);
        assertThat(view(joined, Seat.HOST).opponent().shipsRemaining()).isEqualTo(10);
        GameState sunk = fixture(Phase.PLAYING, "sea-battle-10-ship.v1");
        assertThat(view(sunk, Seat.HOST).you().shipsRemaining()).isEqualTo(9);
        assertThat(view(sunk, Seat.HOST).opponent().shipsRemaining()).isEqualTo(10);
        GameState finished = withPhase(sunk, Phase.FINISHED);
        assertThat(view(finished, Seat.GUEST).you().shipsRemaining()).isEqualTo(10);
        assertThat(view(finished, Seat.GUEST).opponent().shipsRemaining()).isEqualTo(9);
    }

    // Break: report zero remaining ships before the guest joins even though the selected ruleset has a fleet.
    @Test
    void waitingHostHasAllRulesetShipsRemaining() {
        for (String id : List.of("sea-battle-10-ship.v1", "hasbro-classic-2002.v1")) {
            GameState waiting = GameState.create(Rulesets.byId(id).orElseThrow(), "Host");
            assertThat(waiting.host().board().fleet()).isEmpty();
            assertThat(view(waiting, Seat.HOST).you().shipsRemaining())
                    .isEqualTo(id.equals("sea-battle-10-ship.v1") ? 10 : 5);
        }
    }

    // Break: show invitation after join or to guest.
    @Test
    void invitationBelongsOnlyToWaitingHost() {
        GameState waiting =
                GameState.create(Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host");
        SnapshotView snapshot = view(waiting, Seat.HOST);
        assertThat(snapshot.invitationUrl()).isEqualTo(CONTEXT.invitationUrl());
        assertThat(snapshot.invitationExpiresAt()).isEqualTo(CONTEXT.invitationExpiresAt());
        assertThatThrownBy(() -> view(waiting, Seat.GUEST)).isInstanceOf(IllegalArgumentException.class);
        for (Phase phase : List.of(Phase.PLACEMENT, Phase.PLAYING, Phase.FINISHED, Phase.ABANDONED))
            for (Seat caller : Seat.values()) {
                SnapshotView joined = view(pair(phase, caller, false, false), caller);
                assertThat(joined.invitationUrl()).isNull();
                assertThat(joined.invitationExpiresAt()).isNull();
            }
    }

    // Break: rederive a different permission set.
    @Test
    void actionsExactlyReuseDomainPolicy() {
        for (Phase phase : Phase.values())
            for (Seat caller : Seat.values()) {
                GameState state = pair(phase, caller, false, false);
                assertThat(view(state, caller).allowedActions()).isEqualTo(AllowedActions.of(state, caller));
            }
    }

    // Break: carry optional fields outside their phase or change external time/version.
    @Test
    void optionalFieldsAndExternalContextArePreserved() {
        GameState waiting =
                GameState.create(Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host");
        assertThat(view(waiting, Seat.HOST).opponent()).isNull();
        assertThat(view(waiting, Seat.HOST).lastShot()).isNull();
        for (Phase phase : Phase.values()) {
            SnapshotView snapshot = view(pair(phase, Seat.HOST, false, false), Seat.HOST);
            assertThat(snapshot.opponent()).isNotNull();
            assertThat(snapshot.turn() != null).isEqualTo(phase == Phase.PLAYING);
            assertThat(snapshot.outcome() != null).isEqualTo(phase == Phase.FINISHED);
            assertThat(snapshot.gameId()).isEqualTo("game-1");
            assertThat(snapshot.version()).isEqualTo(12);
            assertThat(snapshot.serverTime()).isEqualTo(NOW);
            assertThat(snapshot.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        }
    }

    // Break: retain mutable collection aliases or alter domain input.
    @Test
    void viewsDeeplyFreezeCollectionsAndLeaveStateUnchanged() {
        GameState state = fixture(Phase.PLAYING, "sea-battle-10-ship.v1");
        GameState before = fixture(Phase.PLAYING, "sea-battle-10-ship.v1");
        SnapshotView snapshot = view(state, Seat.HOST);
        assertThatThrownBy(() -> snapshot.yourBoard().grid().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.yourBoard().grid().getFirst().set(0, WATER))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.yourBoard().ships().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(snapshot.yourBoard().ships()).isNotEmpty();
        assertThatThrownBy(() -> snapshot.yourBoard().ships().getFirst().cells().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.allowedActions().clear()).isInstanceOf(UnsupportedOperationException.class);
        List<BoardView.CellState> row = new ArrayList<>(List.of(WATER));
        List<List<BoardView.CellState>> grid = new ArrayList<>(List.of(row));
        List<Coordinate> cells = new ArrayList<>(List.of(c(0, 0)));
        BoardView.ShipView ship =
                new BoardView.ShipView("s", "ship", 1, cells, c(0, 0), Orientation.HORIZONTAL, Ship.ShipStatus.INTACT);
        List<BoardView.ShipView> ships = new ArrayList<>(List.of(ship));
        BoardView board = new BoardView(grid, ships);
        row.set(0, SHIP);
        grid.clear();
        cells.clear();
        ships.clear();
        assertThat(board.grid()).containsExactly(List.of(WATER));
        assertThat(board.ships()).hasSize(1);
        assertThat(board.ships().getFirst().cells()).containsExactly(c(0, 0));
        assertThat(state).isEqualTo(before);
    }

    private SnapshotView view(GameState state, Seat seat) {
        return projector.project(state, seat, CONTEXT);
    }

    private static Coordinate c(int row, int col) {
        return new Coordinate(row, col);
    }

    private static GameState fixture(Phase phase, String id) {
        GameState joined =
                GameState.create(Rulesets.byId(id).orElseThrow(), "Host").withGuest("Guest", NOW);
        List<Ship> fleet = new ArrayList<>(joined.host().board().fleet());
        int[][] anchors = id.equals("sea-battle-10-ship.v1")
                ? new int[][] {{0, 0}, {2, 0}, {2, 4}, {4, 0}, {4, 3}, {4, 6}, {6, 0}, {6, 2}, {6, 4}, {6, 6}}
                : new int[][] {{0, 0}, {2, 0}, {4, 0}, {6, 0}, {8, 0}};
        for (int i = 0; i < fleet.size(); i++) {
            Ship ship = fleet.get(i);
            Set<Coordinate> shipHits = new LinkedHashSet<>();
            if (i == 0) shipHits.add(c(0, 0));
            if (i == 1) for (int col = 0; col < ship.length(); col++) shipHits.add(c(2, col));
            fleet.set(
                    i,
                    new Ship(
                            ship.shipId(),
                            ship.shipTypeId(),
                            ship.length(),
                            c(anchors[i][0], anchors[i][1]),
                            Orientation.HORIZONTAL,
                            shipHits));
        }
        Ship second = fleet.get(1);
        Set<Coordinate> hits = second.hits();
        Set<Coordinate> incoming = new HashSet<>(hits);
        incoming.add(c(0, 0));
        incoming.add(c(9, 9));
        Board board = new Board(fleet, incoming, id.equals("sea-battle-10-ship.v1") ? Set.of(c(1, 0)) : Set.of());
        Shot shot = new Shot(Seat.GUEST, c(2, 0), ShotResult.SUNK, "s02");
        return new GameState(
                id,
                phase,
                12,
                new PlayerState("Host", true, board, List.of()),
                new PlayerState(
                        "Guest",
                        true,
                        new Board(
                                fleet.stream()
                                        .map(ship -> new Ship(
                                                ship.shipId(),
                                                ship.shipTypeId(),
                                                ship.length(),
                                                ship.anchor(),
                                                ship.orientation(),
                                                Set.of()))
                                        .toList(),
                                Set.of(),
                                Set.of()),
                        List.of(shot)),
                id.equals("sea-battle-10-ship.v1") ? Seat.GUEST : Seat.HOST,
                shot,
                new Outcome(Seat.HOST, Outcome.Reason.RESIGNATION),
                phase == Phase.FINISHED ? finishedTimeline(joined.timeline()) : joined.timeline());
    }

    private static GameState pair(Phase phase, Seat caller, boolean alternate, boolean hit) {
        GameState state = fixture(phase, "sea-battle-10-ship.v1");
        PlayerState hidden = state.guest();
        List<Ship> fleet = new ArrayList<>(hidden.board().fleet());
        Ship ship = fleet.get(2);
        fleet.set(
                2,
                new Ship(
                        ship.shipId(),
                        ship.shipTypeId(),
                        ship.length(),
                        c(2, alternate ? 5 : 4),
                        Orientation.HORIZONTAL,
                        hit ? Set.of(c(2, 5)) : Set.of()));
        PlayerState opponent = new PlayerState(
                hidden.displayName(),
                hidden.ready(),
                new Board(fleet, hit ? Set.of(c(2, 5)) : Set.of(), Set.of()),
                List.of());
        PlayerState own = state.host();
        Shot shot = hit ? new Shot(caller, c(2, 5), ShotResult.HIT, null) : null;
        return new GameState(
                state.rulesetId(),
                phase,
                12,
                caller == Seat.HOST ? own : opponent,
                caller == Seat.HOST ? opponent : own,
                Seat.HOST,
                shot,
                state.outcome(),
                phase == Phase.FINISHED ? finishedTimeline(state.timeline()) : state.timeline());
    }

    private static GameState withPhase(GameState state, Phase phase) {
        return new GameState(
                state.rulesetId(),
                phase,
                state.version(),
                state.host(),
                state.guest(),
                state.turn(),
                state.lastShot(),
                state.outcome(),
                phase == Phase.FINISHED ? finishedTimeline(state.timeline()) : state.timeline());
    }

    private static Timeline finishedTimeline(Timeline timeline) {
        return timeline.withReadyAt(Seat.HOST, NOW)
                .withReadyAt(Seat.GUEST, NOW)
                .withPlayStartedAt(NOW, Seat.HOST)
                .withFinishedAt(NOW, Seat.HOST);
    }

    private static void assertNoSeat(Object value) throws Exception {
        if (value == null) return;
        assertThat(value).isNotInstanceOf(Seat.class);
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) assertNoSeat(item);
        } else if (value.getClass().isRecord()) {
            for (var component : value.getClass().getRecordComponents())
                assertNoSeat(component.getAccessor().invoke(value));
        }
    }
}
