package ua.kostenko.battleship.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.app.observability.OperationalEvents;
import ua.kostenko.battleship.app.security.FixedWindowBuckets;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.port.SnapshotPublisher;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.CapacityExceededException;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.usecase.ExpireGamesUseCase;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Seat;
import ua.kostenko.battleship.domain.rules.Rulesets;

class ExpirySweeperTest {
    private static final Instant START = Instant.parse("2100-01-01T00:00:00Z");
    private static final Duration IDLE = Duration.ofSeconds(60);
    private static final Duration RETENTION = Duration.ofSeconds(30);

    private final MutableTimeSource time = new MutableTimeSource(START);
    private final GameRegistry games = new GameRegistry(1);
    private final SnapshotPublisher noStreams = new SnapshotPublisher() {
        @Override
        public void publish(String gameId, Seat seat, SnapshotView view) {}

        @Override
        public void unavailable(String gameId, Seat seat) {}
    };
    private final ExpirySweeper sweeper = new ExpirySweeper(
            new ExpireGamesUseCase(games, new SessionRegistry(4), noStreams, RETENTION),
            time,
            new FixedWindowBuckets(time),
            new OperationalEvents());

    private void insertGame(String id) {
        games.insert(
                id,
                new GameSlot(
                        id,
                        GameState.create(Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host"),
                        "digest",
                        time.now().plus(IDLE),
                        time.now().plus(Duration.ofSeconds(120)),
                        time.now().plusSeconds(30),
                        "https://example.test"));
    }

    @Test
    void tickReclaimsASlotOnceRetentionEndsUsingTheInjectedTimeAndReleasesItsPermit() {
        insertGame("g1");
        sweeper.tick();
        assertThatThrownBy(() -> insertGame("g2")).isInstanceOf(CapacityExceededException.class);
        time.advance(IDLE.plus(RETENTION));
        sweeper.tick();
        assertThat(games.gameIds()).isEmpty();
        assertThatCode(() -> insertGame("g2")).doesNotThrowAnyException();
    }

    @Test
    void tickKeepsASlotThroughExpiryUntilItsRetentionEnds() {
        insertGame("g1");
        time.advance(IDLE.minusMillis(1));
        sweeper.tick();
        assertThat(games.gameIds()).containsExactly("g1");
        time.advance(Duration.ofMillis(1));
        sweeper.tick();
        assertThat(games.gameIds()).containsExactly("g1");
    }

    @Test
    void sweepingTwiceReleasesThePermitOnlyOnce() {
        insertGame("g1");
        time.advance(IDLE.plus(RETENTION));
        sweeper.tick();
        sweeper.tick();
        insertGame("g2");
        assertThatThrownBy(() -> insertGame("g3")).isInstanceOf(CapacityExceededException.class);
    }

    @Test
    void aFailingTickStillReportsTheGamesItForgotAndThenFails() {
        insertGame("g1");
        List<String> reported = new ArrayList<>();
        ExpirySweeper failing = new ExpirySweeper(
                new ExpireGamesUseCase(
                        games,
                        new SessionRegistry(4),
                        new SnapshotPublisher() {
                            @Override
                            public void publish(String gameId, Seat seat, SnapshotView view) {}

                            @Override
                            public void unavailable(String gameId, Seat seat) {
                                throw new IllegalStateException("stream hub failed");
                            }
                        },
                        RETENTION),
                time,
                new FixedWindowBuckets(time),
                new OperationalEvents() {
                    @Override
                    public void gameExpired(String gameId) {
                        reported.add(gameId);
                    }
                });
        time.advance(IDLE.plus(RETENTION));
        assertThatThrownBy(failing::tick).isInstanceOf(IllegalStateException.class);
        assertThat(reported).containsExactly("g1");
    }
}
