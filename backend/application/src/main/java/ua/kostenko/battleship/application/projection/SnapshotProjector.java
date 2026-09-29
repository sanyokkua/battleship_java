package ua.kostenko.battleship.application.projection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ua.kostenko.battleship.application.projection.BoardView.CellState;
import ua.kostenko.battleship.application.projection.BoardView.ShipView;
import ua.kostenko.battleship.application.projection.SnapshotView.Side;
import ua.kostenko.battleship.domain.model.*;
import ua.kostenko.battleship.domain.rules.AllowedActions;
import ua.kostenko.battleship.domain.rules.Ruleset;
import ua.kostenko.battleship.domain.rules.Rulesets;

public final class SnapshotProjector {
    public SnapshotView project(GameState state, Seat seat, SnapshotContext context) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(seat, "seat");
        Objects.requireNonNull(context, "context");
        PlayerState own = seat == Seat.HOST ? state.host() : state.guest();
        if (own == null) throw new IllegalArgumentException("caller seat has not joined");
        PlayerState opponent = seat == Seat.HOST ? state.guest() : state.host();
        Ruleset ruleset =
                Rulesets.byId(state.rulesetId()).orElseThrow(() -> new IllegalArgumentException("unknown ruleset"));
        boolean invitation = seat == Seat.HOST && state.phase() == Phase.WAITING;
        Shot shot = state.lastShot();
        return new SnapshotView(
                context.gameId(),
                state.version(),
                context.serverTime(),
                state.rulesetId(),
                state.phase(),
                player(
                        own,
                        seat == Seat.HOST ? context.hostConnected() : context.guestConnected(),
                        ruleset,
                        state.phase()),
                opponent == null
                        ? null
                        : player(
                                opponent,
                                seat == Seat.HOST ? context.guestConnected() : context.hostConnected(),
                                ruleset,
                                state.phase()),
                state.phase() == Phase.PLAYING ? side(state.turn(), seat) : null,
                AllowedActions.of(state, seat),
                context.expiresAt(),
                invitation ? context.invitationUrl() : null,
                invitation ? context.invitationExpiresAt() : null,
                board(own.board(), ruleset, true, false),
                board(
                        opponent == null ? Board.empty() : opponent.board(),
                        ruleset,
                        state.phase() == Phase.FINISHED,
                        state.phase() == Phase.ABANDONED),
                shot == null
                        ? null
                        : new ShotView(side(shot.by(), seat), shot.target(), shot.result(), shot.sunkShipId()),
                state.phase() == Phase.FINISHED
                        ? new SnapshotView.OutcomeView(
                                side(state.outcome().winner(), seat),
                                state.outcome().reason())
                        : null);
    }

    private static Side side(Seat value, Seat caller) {
        return value == caller ? Side.YOU : Side.OPPONENT;
    }

    private static PlayerView player(PlayerState player, boolean connected, Ruleset ruleset, Phase phase) {
        int remaining = phase == Phase.WAITING
                ? ruleset.fleet().stream().mapToInt(entry -> entry.count()).sum()
                : (int) player.board().fleet().stream()
                        .filter(ship -> ship.status() != Ship.ShipStatus.SUNK)
                        .count();
        return new PlayerView(player.displayName(), player.ready(), connected, remaining);
    }

    private static BoardView board(Board board, Ruleset ruleset, boolean complete, boolean abandoned) {
        List<List<CellState>> grid = new ArrayList<>();
        for (int row = 0; row < ruleset.rows(); row++) {
            List<CellState> line = new ArrayList<>();
            for (int col = 0; col < ruleset.columns(); col++)
                line.add(cell(board, new Coordinate(row, col), complete, abandoned));
            grid.add(line);
        }
        List<ShipView> ships = board.fleet().stream()
                .filter(ship -> !abandoned && (complete || ship.status() == Ship.ShipStatus.SUNK))
                .map(ship -> new ShipView(
                        ship.shipId(),
                        ship.shipTypeId(),
                        ship.length(),
                        List.copyOf(ship.cells()),
                        ship.anchor(),
                        ship.orientation(),
                        ship.status()))
                .toList();
        return new BoardView(grid, ships);
    }

    private static CellState cell(Board board, Coordinate coordinate, boolean complete, boolean abandoned) {
        if (abandoned) return CellState.UNKNOWN;
        for (Ship ship : board.fleet())
            if (ship.cells().contains(coordinate)) {
                if (ship.status() == Ship.ShipStatus.SUNK) return CellState.SUNK;
                if (ship.hits().contains(coordinate)) return CellState.HIT;
                return complete ? CellState.SHIP : CellState.UNKNOWN;
            }
        if (board.incomingShots().contains(coordinate)) return CellState.MISS;
        if (board.revealedWater().contains(coordinate)) return CellState.REVEALED_WATER;
        return complete ? CellState.WATER : CellState.UNKNOWN;
    }
}
