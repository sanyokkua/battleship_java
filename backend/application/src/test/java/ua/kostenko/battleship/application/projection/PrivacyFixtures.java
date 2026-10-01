package ua.kostenko.battleship.application.projection;

import java.time.Instant;
import java.util.*;
import ua.kostenko.battleship.domain.model.*;
import ua.kostenko.battleship.domain.rules.*;

/** The two privacy fixtures shared by the projection tests and the wire-assembly test of the app module. */
public final class PrivacyFixtures {
    public static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private PrivacyFixtures() {}

    public static Coordinate c(int row, int col) {
        return new Coordinate(row, col);
    }

    public static GameState fixture(Phase phase, String id) {
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

    public static GameState pair(Phase phase, Seat caller, boolean alternate, boolean hit) {
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

    public static GameState withPhase(GameState state, Phase phase) {
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

    public static Timeline finishedTimeline(Timeline timeline) {
        return timeline.withReadyAt(Seat.HOST, NOW)
                .withReadyAt(Seat.GUEST, NOW)
                .withPlayStartedAt(NOW, Seat.HOST)
                .withFinishedAt(NOW, Seat.HOST);
    }
}
