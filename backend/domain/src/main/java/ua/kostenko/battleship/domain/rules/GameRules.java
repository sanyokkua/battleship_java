package ua.kostenko.battleship.domain.rules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Board;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Outcome;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.PlayerState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.model.Ship;
import ua.kostenko.battleship.domain.model.Shot;
import ua.kostenko.battleship.domain.model.ShotResult;
import ua.kostenko.battleship.domain.model.Timeline;
import ua.kostenko.battleship.domain.transition.Rejection;
import ua.kostenko.battleship.domain.transition.Rejection.ProblemCode;
import ua.kostenko.battleship.domain.transition.Transition;

public final class GameRules {
    private GameRules() {}

    public static Transition apply(GameState state, Seat actor, GameCommand command, Instant now, RandomSource random) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(random, "random");
        AllowedActions.Action action =
                switch (command) {
                    case GameCommand.PlaceShip ignored -> AllowedActions.Action.PLACE_SHIP;
                    case GameCommand.RemoveShip ignored -> AllowedActions.Action.REMOVE_SHIP;
                    case GameCommand.PlaceFleetRandomly ignored -> AllowedActions.Action.PLACE_FLEET_RANDOMLY;
                    case GameCommand.ClearFleet ignored -> AllowedActions.Action.CLEAR_FLEET;
                    case GameCommand.Ready ignored -> AllowedActions.Action.READY;
                    case GameCommand.Fire ignored -> AllowedActions.Action.FIRE;
                    case GameCommand.Resign ignored -> AllowedActions.Action.RESIGN;
                };
        if (!AllowedActions.of(state, actor).contains(action)) {
            return refused(state, Rejection.problem(ProblemCode.ACTION_NOT_ALLOWED));
        }
        return switch (command) {
            case GameCommand.PlaceShip placeShip -> placeShip(state, actor, placeShip);
            case GameCommand.RemoveShip removeShip -> removeShip(state, actor, removeShip);
            case GameCommand.PlaceFleetRandomly arrange -> placeFleetRandomly(state, actor, arrange, random);
            case GameCommand.ClearFleet ignored -> clearFleet(state, actor);
            case GameCommand.Ready ignored -> ready(state, actor, now, random);
            case GameCommand.Fire fire -> fire(state, actor, fire, now);
            case GameCommand.Resign ignored -> resign(state, actor, now);
        };
    }

    private static Seat other(Seat seat) {
        return seat == Seat.HOST ? Seat.GUEST : Seat.HOST;
    }

    private static Transition fire(GameState state, Seat actor, GameCommand.Fire command, Instant now) {
        if (state.phase() != Phase.PLAYING || state.turn() != actor) {
            return refused(state, Rejection.problem(ProblemCode.ACTION_NOT_ALLOWED));
        }
        Ruleset ruleset = Rulesets.byId(state.rulesetId()).orElseThrow();
        Coordinate target = command.target();
        if (target.rowIndex() >= ruleset.rows()) {
            return refused(state, Rejection.validation("/command/target/rowIndex", "OUT_OF_RANGE"));
        }
        if (target.columnIndex() >= ruleset.columns()) {
            return refused(state, Rejection.validation("/command/target/columnIndex", "OUT_OF_RANGE"));
        }
        PlayerState defender = player(state, other(actor));
        Board board = defender.board();
        if (board.incomingShots().contains(target) || board.revealedWater().contains(target)) {
            return refused(state, Rejection.problem(ProblemCode.TARGET_ALREADY_FIRED));
        }
        List<Ship> fleet = new ArrayList<>(board.fleet());
        Set<Coordinate> incoming = new HashSet<>(board.incomingShots());
        incoming.add(target);
        Set<Coordinate> water = new HashSet<>(board.revealedWater());
        ShotResult result = ShotResult.MISS;
        String sunkId = null;
        for (int index = 0; index < fleet.size(); index++) {
            Ship ship = fleet.get(index);
            if (!ship.cells().contains(target)) continue;
            Set<Coordinate> hits = new HashSet<>(ship.hits());
            hits.add(target);
            Ship hit =
                    new Ship(ship.shipId(), ship.shipTypeId(), ship.length(), ship.anchor(), ship.orientation(), hits);
            fleet.set(index, hit);
            boolean sunk = hit.status() == Ship.ShipStatus.SUNK;
            result = sunk ? ShotResult.SUNK : ShotResult.HIT;
            if (sunk) {
                sunkId = ship.shipId();
                if (ruleset.revealWaterAroundSunk()) revealWater(ruleset, board, incoming, water, hit);
            }
            break;
        }
        Shot shot = new Shot(actor, target, result, sunkId);
        PlayerState attacker = player(state, actor);
        List<Shot> history = new ArrayList<>(attacker.shotsFired());
        history.add(shot);
        PlayerState updatedAttacker =
                new PlayerState(attacker.displayName(), attacker.ready(), attacker.board(), history);
        PlayerState updatedDefender = new PlayerState(
                defender.displayName(), defender.ready(), new Board(fleet, incoming, water), defender.shotsFired());
        boolean finished = fleet.stream().allMatch(ship -> ship.status() == Ship.ShipStatus.SUNK);
        Seat nextTurn = ruleset.extraTurnOnHit() && result != ShotResult.MISS ? actor : other(actor);
        Timeline timeline = state.timeline().withFire(actor, nextTurn, now, finished);
        GameState next = new GameState(
                state.rulesetId(),
                finished ? Phase.FINISHED : Phase.PLAYING,
                state.version() + 1,
                actor == Seat.HOST ? updatedAttacker : updatedDefender,
                actor == Seat.GUEST ? updatedAttacker : updatedDefender,
                finished ? null : nextTurn,
                shot,
                finished ? new Outcome(actor, Outcome.Reason.FLEET_DESTROYED) : null,
                timeline);
        return new Transition(next, true, Set.of(Seat.HOST, Seat.GUEST), null);
    }

    private static void revealWater(
            Ruleset ruleset, Board board, Set<Coordinate> incoming, Set<Coordinate> water, Ship sunk) {
        Set<Coordinate> occupied = new HashSet<>();
        board.fleet().forEach(ship -> occupied.addAll(ship.cells()));
        for (Coordinate cell : sunk.cells()) {
            for (int row = Math.max(0, cell.rowIndex() - 1);
                    row <= Math.min(ruleset.rows() - 1, cell.rowIndex() + 1);
                    row++) {
                for (int column = Math.max(0, cell.columnIndex() - 1);
                        column <= Math.min(ruleset.columns() - 1, cell.columnIndex() + 1);
                        column++) {
                    Coordinate neighbour = new Coordinate(row, column);
                    if (!occupied.contains(neighbour) && !incoming.contains(neighbour)) water.add(neighbour);
                }
            }
        }
    }

    private static Transition resign(GameState state, Seat actor, Instant now) {
        if (state.phase() != Phase.PLAYING) return refused(state, Rejection.problem(ProblemCode.ACTION_NOT_ALLOWED));
        GameState next = new GameState(
                state.rulesetId(),
                Phase.FINISHED,
                state.version() + 1,
                state.host(),
                state.guest(),
                null,
                state.lastShot(),
                new Outcome(other(actor), Outcome.Reason.RESIGNATION),
                state.timeline().withFinishedAt(now, state.turn()));
        return new Transition(next, true, Set.of(Seat.HOST, Seat.GUEST), null);
    }

    private static Transition ready(GameState state, Seat actor, Instant now, RandomSource random) {
        PlayerState player = player(state, actor);
        if (state.phase() != Phase.PLACEMENT
                || player == null
                || player.board().fleet().isEmpty()
                || player.board().fleet().stream().anyMatch(ship -> ship.anchor() == null)) {
            return refused(state, Rejection.problem(ProblemCode.ACTION_NOT_ALLOWED));
        }
        PlayerState readyPlayer = new PlayerState(player.displayName(), true, player.board(), player.shotsFired());
        PlayerState host = actor == Seat.HOST ? readyPlayer : state.host();
        PlayerState guest = actor == Seat.GUEST ? readyPlayer : state.guest();
        Timeline timeline = state.timeline().withReadyAt(actor, now);
        boolean playing = host.ready() && guest != null && guest.ready();
        Seat turn = null;
        if (playing) {
            int order = timeline.readyAt()
                    .get(Seat.HOST)
                    .compareTo(timeline.readyAt().get(Seat.GUEST));
            turn = order < 0 ? Seat.HOST : order > 0 ? Seat.GUEST : random.nextInt(2) == 0 ? Seat.HOST : Seat.GUEST;
            timeline = timeline.withPlayStartedAt(now, turn);
        }
        GameState next = new GameState(
                state.rulesetId(),
                playing ? Phase.PLAYING : Phase.PLACEMENT,
                state.version() + 1,
                host,
                guest,
                turn,
                state.lastShot(),
                state.outcome(),
                timeline);
        return new Transition(next, true, Set.of(Seat.HOST, Seat.GUEST), null);
    }

    private static Transition placeShip(GameState state, Seat actor, GameCommand.PlaceShip command) {
        Ruleset ruleset = Rulesets.byId(state.rulesetId()).orElseThrow();
        if (command.anchor().rowIndex() >= ruleset.rows()) {
            return refused(state, Rejection.validation("/command/anchor/rowIndex", "OUT_OF_RANGE"));
        }
        if (command.anchor().columnIndex() >= ruleset.columns()) {
            return refused(state, Rejection.validation("/command/anchor/columnIndex", "OUT_OF_RANGE"));
        }

        PlayerState player = player(state, actor);
        List<Ship> fleet = player.board().fleet();
        int shipIndex = indexOf(fleet, command.shipId());
        if (shipIndex < 0) {
            return refused(state, Rejection.validation("/command/shipId", "UNKNOWN_VALUE"));
        }

        Ship moving = fleet.get(shipIndex);
        if (command.anchor().equals(moving.anchor()) && command.orientation() == moving.orientation()) {
            return acceptedNoOp(state);
        }
        Ship placed = new Ship(
                moving.shipId(),
                moving.shipTypeId(),
                moving.length(),
                command.anchor(),
                command.orientation(),
                moving.hits());
        Rejection placementFailure = validatePlacement(ruleset, fleet, shipIndex, placed);
        if (placementFailure != null) {
            return refused(state, placementFailure);
        }

        List<Ship> updatedFleet = new ArrayList<>(fleet);
        updatedFleet.set(shipIndex, placed);
        return accepted(state, actor, playerWithFleet(player, updatedFleet));
    }

    private static Transition removeShip(GameState state, Seat actor, GameCommand.RemoveShip command) {
        PlayerState player = player(state, actor);
        List<Ship> fleet = player.board().fleet();
        int shipIndex = indexOf(fleet, command.shipId());
        if (shipIndex < 0) {
            return refused(state, Rejection.validation("/command/shipId", "UNKNOWN_VALUE"));
        }
        Ship existing = fleet.get(shipIndex);
        if (existing.anchor() == null) {
            return acceptedNoOp(state);
        }

        List<Ship> updatedFleet = new ArrayList<>(fleet);
        updatedFleet.set(
                shipIndex, new Ship(existing.shipId(), existing.shipTypeId(), existing.length(), null, null, Set.of()));
        return accepted(state, actor, playerWithFleet(player, updatedFleet));
    }

    private static Transition placeFleetRandomly(
            GameState state, Seat actor, GameCommand.PlaceFleetRandomly command, RandomSource random) {
        Ruleset ruleset = Rulesets.byId(state.rulesetId()).orElseThrow();
        PlayerState player = player(state, actor);
        List<Ship> replacement = new ArrayList<>(unplacedFleet(player.board().fleet()));
        List<Integer> placementOrder = new ArrayList<>();
        for (int index = 0; index < replacement.size(); index++) {
            placementOrder.add(index);
        }
        placementOrder.sort(Comparator.comparingInt(
                        (Integer index) -> replacement.get(index).length())
                .reversed());
        int failedCandidates = 0;
        for (int index : placementOrder) {
            Ship existing = replacement.get(index);
            while (true) {
                Orientation orientation = random.nextInt(2) == 0 ? Orientation.HORIZONTAL : Orientation.VERTICAL;
                Coordinate anchor = new Coordinate(random.nextInt(ruleset.rows()), random.nextInt(ruleset.columns()));
                Ship candidate = new Ship(
                        existing.shipId(), existing.shipTypeId(), existing.length(), anchor, orientation, Set.of());
                if (validatePlacement(ruleset, replacement, index, candidate) == null) {
                    replacement.set(index, candidate);
                    break;
                }
                failedCandidates++;
                if (failedCandidates >= command.attemptLimit()) {
                    return refused(state, Rejection.problem(ProblemCode.RANDOM_ARRANGEMENT_FAILED));
                }
            }
        }
        if (replacement.equals(player.board().fleet())) {
            return acceptedNoOp(state);
        }
        return accepted(state, actor, playerWithFleet(player, replacement));
    }

    private static Transition clearFleet(GameState state, Seat actor) {
        PlayerState player = player(state, actor);
        List<Ship> replacement = unplacedFleet(player.board().fleet());
        if (replacement.equals(player.board().fleet())) {
            return acceptedNoOp(state);
        }
        return accepted(state, actor, playerWithFleet(player, replacement));
    }

    private static List<Ship> unplacedFleet(List<Ship> fleet) {
        return fleet.stream()
                .map(ship -> new Ship(ship.shipId(), ship.shipTypeId(), ship.length(), null, null, Set.of()))
                .toList();
    }

    private static Rejection validatePlacement(Ruleset ruleset, List<Ship> fleet, int shipIndex, Ship placed) {
        if (placed.cells().stream()
                .anyMatch(cell -> cell.rowIndex() >= ruleset.rows() || cell.columnIndex() >= ruleset.columns())) {
            return Rejection.problem(ProblemCode.PLACEMENT_OUT_OF_BOUNDS);
        }
        for (int index = 0; index < fleet.size(); index++) {
            if (index == shipIndex || fleet.get(index).anchor() == null) {
                continue;
            }
            Ship other = fleet.get(index);
            if (overlaps(placed, other)) {
                return Rejection.problem(ProblemCode.PLACEMENT_OVERLAP);
            }
            if (!ruleset.shipsMayTouch() && touches(placed, other)) {
                return Rejection.problem(ProblemCode.PLACEMENT_TOUCHING);
            }
        }
        return null;
    }

    private static boolean overlaps(Ship first, Ship second) {
        return first.cells().stream().anyMatch(second.cells()::contains);
    }

    private static boolean touches(Ship first, Ship second) {
        return first.cells().stream().anyMatch(a -> second.cells().stream()
                .anyMatch(b -> Math.abs(a.rowIndex() - b.rowIndex()) <= 1
                        && Math.abs(a.columnIndex() - b.columnIndex()) <= 1));
    }

    private static int indexOf(List<Ship> fleet, String shipId) {
        for (int index = 0; index < fleet.size(); index++) {
            if (fleet.get(index).shipId().equals(shipId)) {
                return index;
            }
        }
        return -1;
    }

    private static PlayerState player(GameState state, Seat seat) {
        return switch (seat) {
            case HOST -> state.host();
            case GUEST -> state.guest() == null ? null : state.guest();
        };
    }

    private static PlayerState playerWithFleet(PlayerState player, List<Ship> fleet) {
        return new PlayerState(
                player.displayName(),
                player.ready(),
                new Board(fleet, player.board().incomingShots(), player.board().revealedWater()),
                player.shotsFired());
    }

    private static Transition accepted(GameState state, Seat actor, PlayerState replacement) {
        PlayerState host = actor == Seat.HOST ? replacement : state.host();
        PlayerState guest = actor == Seat.GUEST ? replacement : state.guest();
        GameState next = new GameState(
                state.rulesetId(),
                state.phase(),
                state.version() + 1,
                host,
                guest,
                state.turn(),
                state.lastShot(),
                state.outcome(),
                state.timeline());
        return new Transition(next, true, Set.of(actor), null);
    }

    private static Transition acceptedNoOp(GameState state) {
        return new Transition(state, false, Set.of(), null);
    }

    private static Transition refused(GameState state, Rejection rejection) {
        return new Transition(state, false, Set.of(), rejection);
    }
}
