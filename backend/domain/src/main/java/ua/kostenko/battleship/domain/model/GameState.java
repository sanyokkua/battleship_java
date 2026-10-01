package ua.kostenko.battleship.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ua.kostenko.battleship.domain.rules.FleetEntry;
import ua.kostenko.battleship.domain.rules.Ruleset;

public record GameState(
        String rulesetId,
        Phase phase,
        long version,
        PlayerState host,
        PlayerState guest,
        Seat turn,
        Shot lastShot,
        Outcome outcome,
        Timeline timeline) {
    public GameState {
        Objects.requireNonNull(rulesetId, "rulesetId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(timeline, "timeline");
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
    }

    public static GameState create(Ruleset ruleset, String hostName) {
        Objects.requireNonNull(ruleset, "ruleset");
        return new GameState(
                ruleset.id(), Phase.WAITING, 0, PlayerState.empty(hostName), null, null, null, null, Timeline.empty());
    }

    public GameState withBumpedVersion() {
        return new GameState(rulesetId, phase, version + 1, host, guest, turn, lastShot, outcome, timeline);
    }

    public GameState abandoned() {
        return new GameState(rulesetId, Phase.ABANDONED, version + 1, host, guest, null, lastShot, null, timeline);
    }

    public GameState withGuest(String guestName, Instant now) {
        if (guest != null || phase != Phase.WAITING) {
            throw new IllegalStateException("guest can join only once while waiting");
        }
        List<Ship> fleet = createFleet();
        Board board = new Board(fleet, java.util.Set.of(), java.util.Set.of());
        PlayerState joinedGuest = new PlayerState(guestName, false, board, List.of());
        PlayerState joinedHost = new PlayerState(
                host.displayName(),
                host.ready(),
                new Board(fleet, java.util.Set.of(), java.util.Set.of()),
                host.shotsFired());
        return new GameState(
                rulesetId,
                Phase.PLACEMENT,
                version + 1,
                joinedHost,
                joinedGuest,
                null,
                null,
                null,
                timeline.withGuestJoinedAt(now));
    }

    private List<Ship> createFleet() {
        Ruleset ruleset = ua.kostenko.battleship.domain.rules.Rulesets.byId(rulesetId)
                .orElseThrow(() -> new IllegalStateException("unknown ruleset: " + rulesetId));
        List<Ship> fleet = new ArrayList<>();
        int shipNumber = 1;
        for (FleetEntry entry : ruleset.fleet()) {
            for (int index = 0; index < entry.count(); index++) {
                fleet.add(new Ship(
                        "s%02d".formatted(shipNumber++),
                        entry.shipTypeId(),
                        entry.length(),
                        null,
                        null,
                        java.util.Set.of()));
            }
        }
        return List.copyOf(fleet);
    }
}
