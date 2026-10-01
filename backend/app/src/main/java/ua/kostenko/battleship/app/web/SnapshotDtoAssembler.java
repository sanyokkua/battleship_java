package ua.kostenko.battleship.app.web;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import ua.kostenko.battleship.app.web.dto.Action;
import ua.kostenko.battleship.app.web.dto.Board;
import ua.kostenko.battleship.app.web.dto.CellState;
import ua.kostenko.battleship.app.web.dto.Coordinate;
import ua.kostenko.battleship.app.web.dto.DurationAggregate;
import ua.kostenko.battleship.app.web.dto.FleetSummary;
import ua.kostenko.battleship.app.web.dto.GameSnapshot;
import ua.kostenko.battleship.app.web.dto.GameStatistics;
import ua.kostenko.battleship.app.web.dto.MatchStatistics;
import ua.kostenko.battleship.app.web.dto.Orientation;
import ua.kostenko.battleship.app.web.dto.Outcome;
import ua.kostenko.battleship.app.web.dto.Phase;
import ua.kostenko.battleship.app.web.dto.Player;
import ua.kostenko.battleship.app.web.dto.PlayerStatistics;
import ua.kostenko.battleship.app.web.dto.Ship;
import ua.kostenko.battleship.app.web.dto.ShipStatus;
import ua.kostenko.battleship.app.web.dto.Shot;
import ua.kostenko.battleship.app.web.dto.Side;
import ua.kostenko.battleship.application.projection.BoardView;
import ua.kostenko.battleship.application.projection.PlayerView;
import ua.kostenko.battleship.application.projection.ShotView;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.projection.StatisticsView;

/**
 * A mechanical one-to-one copy of the projector's view into the generated wire document. It decides nothing: every
 * enum crosses by name, so a contract change that renames a value fails here, and the one document it builds is what
 * HTTP and the event stream both send (R17).
 */
public final class SnapshotDtoAssembler {
    private SnapshotDtoAssembler() {}

    public static GameSnapshot assemble(SnapshotView view) {
        return new GameSnapshot()
                .gameId(view.gameId())
                .version(Math.toIntExact(view.version()))
                .serverTime(utc(view.serverTime()))
                .rulesetId(view.rulesetId())
                .phase(Phase.valueOf(view.phase().name()))
                .you(player(view.you()))
                .opponent(view.opponent() == null ? null : player(view.opponent()))
                .turn(side(view.turn()))
                .allowedActions(view.allowedActions().stream()
                        .sorted(Comparator.naturalOrder())
                        .map(action -> Action.valueOf(action.name()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .expiresAt(utc(view.expiresAt()))
                .invitationUrl(view.invitationUrl() == null ? null : URI.create(view.invitationUrl()))
                .invitationExpiresAt(view.invitationExpiresAt() == null ? null : utc(view.invitationExpiresAt()))
                .yourBoard(board(view.yourBoard()))
                .opponentBoard(board(view.opponentBoard()))
                .lastShot(view.lastShot() == null ? null : shot(view.lastShot()))
                .outcome(view.outcome() == null ? null : outcome(view.outcome()))
                .statistics(view.statistics() == null ? null : statistics(view.statistics()));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Side side(SnapshotView.Side side) {
        return side == null ? null : Side.valueOf(side.name());
    }

    private static Player player(PlayerView player) {
        return new Player()
                .displayName(player.displayName())
                .ready(player.ready())
                .connected(player.connected())
                .shipsRemaining(player.shipsRemaining());
    }

    private static Board board(BoardView board) {
        return new Board()
                .grid(board.grid().stream()
                        .map(row -> row.stream()
                                .map(cell -> CellState.valueOf(cell.name()))
                                .toList())
                        .toList())
                .ships(board.ships().stream().map(SnapshotDtoAssembler::ship).toList());
    }

    private static Ship ship(BoardView.ShipView ship) {
        return new Ship()
                .shipId(ship.shipId())
                .shipTypeId(ship.shipTypeId())
                .length(ship.length())
                .cells(coordinates(ship.cells()))
                .anchor(ship.anchor() == null ? null : coordinate(ship.anchor()))
                .orientation(
                        ship.orientation() == null
                                ? null
                                : Orientation.valueOf(ship.orientation().name()))
                .status(ShipStatus.valueOf(ship.status().name()));
    }

    private static List<Coordinate> coordinates(List<ua.kostenko.battleship.domain.model.Coordinate> cells) {
        return cells.stream().map(SnapshotDtoAssembler::coordinate).toList();
    }

    private static Coordinate coordinate(ua.kostenko.battleship.domain.model.Coordinate coordinate) {
        return new Coordinate().rowIndex(coordinate.rowIndex()).columnIndex(coordinate.columnIndex());
    }

    private static Shot shot(ShotView shot) {
        return new Shot()
                .by(side(shot.by()))
                .target(coordinate(shot.target()))
                .result(Shot.ResultEnum.valueOf(shot.result().name()))
                .sunkShipId(shot.sunkShipId());
    }

    private static Outcome outcome(SnapshotView.OutcomeView outcome) {
        return new Outcome()
                .winner(side(outcome.winner()))
                .reason(Outcome.ReasonEnum.valueOf(outcome.reason().name()));
    }

    private static GameStatistics statistics(StatisticsView statistics) {
        StatisticsView.MatchStatistics match = statistics.match();
        return new GameStatistics()
                .match(new MatchStatistics()
                        .totalDurationMs(Math.toIntExact(match.totalDurationMs()))
                        .placementDurationMs(Math.toIntExact(match.placementDurationMs()))
                        .gameplayDurationMs(Math.toIntExact(match.gameplayDurationMs())))
                .you(playerStatistics(statistics.you()))
                .opponent(playerStatistics(statistics.opponent()));
    }

    private static PlayerStatistics playerStatistics(StatisticsView.PlayerStatistics player) {
        StatisticsView.FleetSummary fleet = player.fleet();
        return new PlayerStatistics()
                .placementDurationMs(Math.toIntExact(player.placementDurationMs()))
                .shots(player.shots())
                .hits(player.hits())
                .accuracy(player.accuracy() == null ? null : BigDecimal.valueOf(player.accuracy()))
                .turns(aggregate(player.turns()))
                .shotDecisions(aggregate(player.shotDecisions()))
                .fleet(new FleetSummary()
                        .total(fleet.total())
                        .intact(fleet.intact())
                        .damaged(fleet.damaged())
                        .sunk(fleet.sunk()));
    }

    private static DurationAggregate aggregate(StatisticsView.DurationAggregate aggregate) {
        return new DurationAggregate()
                .count(aggregate.count())
                .totalMs(Math.toIntExact(aggregate.totalMs()))
                .averageMs(milliseconds(aggregate.averageMs()))
                .fastestMs(milliseconds(aggregate.fastestMs()))
                .slowestMs(milliseconds(aggregate.slowestMs()));
    }

    private static Integer milliseconds(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }
}
