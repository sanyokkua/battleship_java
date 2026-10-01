package ua.kostenko.battleship.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Orientation;
import ua.kostenko.battleship.domain.model.Seat;

final class CommandTestFixture {
    static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    static final String RULESET = "sea-battle-10-ship.v1";
    static final Duration IDLE = Duration.ofSeconds(60);
    static final Duration ABSOLUTE = Duration.ofSeconds(120);
    static final Duration INVITATION = Duration.ofSeconds(30);
    static final Duration RETENTION = Duration.ofSeconds(30);
    static final Duration PRESENCE = Duration.ofSeconds(10);
    static final int[][] ANCHORS = {{0, 0}, {2, 0}, {2, 4}, {4, 0}, {4, 3}, {4, 6}, {6, 0}, {6, 2}, {6, 4}, {6, 6}};

    final GameRegistry games;
    final SessionRegistry sessions = new SessionRegistry(30);
    final MutableTimeSource time = new MutableTimeSource(NOW);
    final SnapshotProjector projector;
    final AtomicInteger commandIds = new AtomicInteger();
    final SecretGenerator secrets = new SecretGenerator() {
        final AtomicInteger games = new AtomicInteger();
        final AtomicInteger sessions = new AtomicInteger();
        final AtomicInteger invitations = new AtomicInteger();

        public String gameId() {
            return "game-" + games.incrementAndGet();
        }

        public String sessionValue() {
            return "session-" + sessions.incrementAndGet();
        }

        public String invitationSecret() {
            return String.valueOf((char) ('A' + invitations.getAndIncrement())).repeat(43);
        }
    };
    final CreateGameUseCase create;
    final JoinGameUseCase join;
    final CommandUseCase commands;
    final PresenceUseCase presence;
    final ExpireGamesUseCase expire;
    final ReplaceInvitationUseCase replace;

    CommandTestFixture() {
        this(new SnapshotProjector());
    }

    CommandTestFixture(SnapshotProjector projector) {
        this(projector, 20, IDLE, ABSOLUTE, INVITATION, RETENTION, PRESENCE);
    }

    CommandTestFixture(
            int maxGames,
            Duration idle,
            Duration absolute,
            Duration invitation,
            Duration retention,
            Duration presenceInterval) {
        this(new SnapshotProjector(), maxGames, idle, absolute, invitation, retention, presenceInterval);
    }

    private CommandTestFixture(
            SnapshotProjector projector,
            int maxGames,
            Duration idle,
            Duration absolute,
            Duration invitation,
            Duration retention,
            Duration presenceInterval) {
        this.projector = projector;
        games = new GameRegistry(maxGames);
        create = new CreateGameUseCase(
                games,
                sessions,
                secrets,
                time,
                projector,
                1,
                idle,
                absolute,
                invitation,
                retention,
                "https://example.test");
        replace = new ReplaceInvitationUseCase(games, sessions, secrets, time, projector, invitation, retention);
        join = new JoinGameUseCase(games, sessions, secrets, time, projector, 1, idle, retention);
        commands = new CommandUseCase(games, sessions, time, new SeededRandomSource(42), projector, idle, retention);
        presence = new PresenceUseCase(games, sessions, time, projector, idle, presenceInterval, retention);
        expire = new ExpireGamesUseCase(games, sessions, retention);
    }

    /** A hosted game still waiting for its guest; {@code secret} is the unused invitation secret. */
    Waiting newWaitingGame() {
        var created = create.execute(RULESET, "Host", null);
        String url = created.snapshot().invitationUrl();
        return new Waiting(
                created.snapshot().gameId(), created.sessionValue(), url.substring(url.indexOf("#invite=") + 8));
    }

    record Waiting(String id, String host, String secret) {}

    Game newGame() {
        return newGame(null, null);
    }

    Game newGame(String hostSession, String guestSession) {
        var created = create.execute(RULESET, "Host", hostSession);
        String url = created.snapshot().invitationUrl();
        var joined = join.execute(
                created.snapshot().gameId(), url.substring(url.indexOf("#invite=") + 8), "Guest", guestSession);
        return new Game(created.snapshot().gameId(), created.sessionValue(), joined.sessionValue());
    }

    java.util.UUID id() {
        return new java.util.UUID(0, commandIds.incrementAndGet());
    }

    CommandUseCase.CommandResult send(Game game, Seat seat, GameCommand command) {
        return send(game, seat, id(), command);
    }

    CommandUseCase.CommandResult send(Game game, Seat seat, java.util.UUID id, GameCommand command) {
        return commands.execute(game.id(), seat == Seat.HOST ? game.host() : game.guest(), id, command);
    }

    GameState state(Game game) {
        return games.withSlot(game.id(), slot -> slot.state());
    }

    void placeFleet(Game game, Seat seat) {
        for (int i = 0; i < ANCHORS.length; i++) {
            int[] anchor = ANCHORS[i];
            send(
                    game,
                    seat,
                    new GameCommand.PlaceShip(
                            "s%02d".formatted(i + 1), new Coordinate(anchor[0], anchor[1]), Orientation.HORIZONTAL));
        }
    }

    void startPlaying(Game game) {
        placeFleet(game, Seat.HOST);
        placeFleet(game, Seat.GUEST);
        send(game, Seat.HOST, new GameCommand.Ready());
        time.advance(Duration.ofMillis(1));
        send(game, Seat.GUEST, new GameCommand.Ready());
    }

    static String failureCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call);
        org.assertj.core.api.Assertions.assertThat(thrown).isInstanceOf(ApplicationFailure.class);
        return ((ApplicationFailure) thrown).code();
    }

    /** An accepted, view-neutral action while the game is in placement: re-places the first ship where it is. */
    CommandUseCase.CommandResult probe(Game game, Seat seat) {
        return send(game, seat, new GameCommand.PlaceShip("s01", new Coordinate(0, 0), Orientation.HORIZONTAL));
    }

    Instant idleDeadline(String gameId) {
        return games.withSlot(gameId, slot -> slot.idleDeadline());
    }

    record Game(String id, String host, String guest) {}
}
