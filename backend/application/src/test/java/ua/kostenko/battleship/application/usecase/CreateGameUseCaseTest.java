package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

class CreateGameUseCaseTest {
    static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    static final String RULESET = "sea-battle-10-ship.v1";
    static final String INVITATION = "A".repeat(43);

    static Fixture fixture(int gameCap, int browserCap) {
        return new Fixture(gameCap, browserCap);
    }

    @Test
    void creationIssuesAndReusesSessionWithHostOwnership() {
        Fixture fixture = fixture(3, 3);
        var first = fixture.create.execute(RULESET, "  Ada  ", null);
        assertThat(first.sessionValue()).isEqualTo("session-1");
        assertThat(first.snapshot().phase()).isEqualTo(Phase.WAITING);
        assertThat(first.snapshot().you().displayName()).isEqualTo("Ada");
        assertThat(first.snapshot().expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(first.snapshot().invitationExpiresAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(first.snapshot().invitationUrl()).isEqualTo("https://example.test/join/game-1#invite=" + INVITATION);
        assertThat(fixture.sessions.find("session-1")).get().satisfies(record -> assertThat(record.liveGames())
                .containsExactly("game-1"));

        var second = fixture.create.execute(RULESET, "Grace", first.sessionValue());
        assertThat(second.sessionValue()).isEqualTo(first.sessionValue());
        assertThat(fixture.sessions.find("session-1")).get().satisfies(record -> assertThat(record.liveGames())
                .containsExactlyInAnyOrder("game-1", "game-2"));
        assertThat(fixture.sessions.digests()).hasSize(1);
    }

    @Test
    void gameCeilingFailureDoesNotAttachOrphanedGameToSession() {
        Fixture fixture = fixture(1, 2);
        fixture.sessions.register("host-session", NOW);
        fixture.create.execute(RULESET, "Ada", "host-session");
        assertThatThrownBy(() -> fixture.create.execute(RULESET, "Grace", "host-session"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("service-unavailable"));
        assertThat(fixture.sessions.find("host-session")).get().satisfies(record -> assertThat(record.liveGames())
                .containsExactly("game-1"));
        fixture.games.remove("game-1");
        fixture.create.execute(RULESET, "Grace", "host-session");
        assertThat(fixture.sessions.find("host-session")).get().satisfies(record -> assertThat(record.liveGames())
                .containsExactlyInAnyOrder("game-1", "game-3"));
    }

    @Test
    void rejectedGameAdmissionDoesNotLeaveNewSession() {
        Fixture fixture = fixture(1, 2);
        fixture.create.execute(RULESET, "Ada", null);
        assertThatThrownBy(() -> fixture.create.execute(RULESET, "Grace", null))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("service-unavailable"));
        assertThat(fixture.sessions.digests()).containsExactly(SessionRegistry.digest("session-1"));
        assertThat(fixture.sessions.find("session-2")).isEmpty();
    }

    @Test
    void projectsCreatedGameAfterReleasingItsSlotLock() {
        Fixture fixture = fixture(1, 1);
        AtomicBoolean projected = new AtomicBoolean();
        SnapshotProjector checkingProjector = new SnapshotProjector() {
            @Override
            public SnapshotView project(GameState state, Seat seat, SnapshotContext context) {
                int holdCount = fixture.games.withSlot(
                        context.gameId(), slot -> slotLock(slot).getHoldCount());
                assertThat(holdCount).isEqualTo(1);
                projected.set(true);
                return super.project(state, seat, context);
            }
        };
        CreateGameUseCase create = new CreateGameUseCase(
                fixture.games,
                fixture.sessions,
                fixture.secrets,
                fixture.time,
                checkingProjector,
                1,
                Duration.ofSeconds(60),
                Duration.ofSeconds(120),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                "https://example.test");
        assertThat(create.execute(RULESET, "Host", null).snapshot().phase()).isEqualTo(Phase.WAITING);
        assertThat(projected.get()).isTrue();
    }

    private static ReentrantLock slotLock(GameSlot slot) {
        try {
            var field = GameSlot.class.getDeclaredField("lock");
            field.setAccessible(true);
            return (ReentrantLock) field.get(slot);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void queuedCreationStartsDeadlinesAndSessionAtAdmissionTime() throws Exception {
        Fixture fixture = fixture(2, 2);
        ReentrantLock lock = sessionLock(fixture.sessions);
        AtomicReference<CreateGameUseCase.CreatedGame> created = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        lock.lock();
        Thread worker;
        try {
            worker = Thread.ofPlatform().start(() -> {
                try {
                    created.set(fixture.create.execute(RULESET, "Host", null));
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lock.hasQueuedThread(worker) && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(lock.hasQueuedThread(worker)).isTrue();
            fixture.time.advance(Duration.ofSeconds(10));
        } finally {
            lock.unlock();
        }
        worker.join(5_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(created.get()).isNotNull();
        assertThat(created.get().snapshot().serverTime()).isEqualTo(NOW.plusSeconds(10));
        assertThat(created.get().snapshot().expiresAt()).isEqualTo(NOW.plusSeconds(70));
        assertThat(created.get().snapshot().invitationExpiresAt()).isEqualTo(NOW.plusSeconds(40));
        assertThat(fixture.sessions.find(created.get().sessionValue()))
                .get()
                .satisfies(record -> assertThat(record.createdAt()).isEqualTo(NOW.plusSeconds(10)));
    }

    private static ReentrantLock sessionLock(SessionRegistry sessions) {
        try {
            var field = SessionRegistry.class.getDeclaredField("lock");
            field.setAccessible(true);
            return (ReentrantLock) field.get(sessions);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void invalidDisplayNameDoesNotCreateSessionOrGame() {
        Fixture fixture = fixture(1, 1);
        assertThatThrownBy(() -> fixture.create.execute(RULESET, "   ", null))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo("validation-failed");
                    assertThat(failure.field()).isEqualTo("/displayName");
                    assertThat(failure.rule()).isEqualTo("TOO_SHORT");
                });
        assertThat(fixture.sessions.digests()).isEmpty();
        assertThatThrownBy(() -> fixture.games.withSlot("game-1", slot -> slot.state()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simultaneousCreatesByOneBrowserRespectItsCap() throws Exception {
        Fixture fixture = fixture(4, 1);
        fixture.sessions.register("host", NOW);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Boolean> first = workers.submit(() -> createAfterBarrier(fixture, barrier));
            Future<Boolean> second = workers.submit(() -> createAfterBarrier(fixture, barrier));
            assertThat((first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
            assertThat(fixture.sessions.find("host")).get().satisfies(record -> assertThat(record.liveGames())
                    .hasSize(1));
        } finally {
            workers.shutdownNow();
        }
    }

    private static boolean createAfterBarrier(Fixture fixture, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            fixture.create.execute(RULESET, "Host", "host");
            return true;
        } catch (ApplicationFailure refused) {
            assertThat(refused.code()).isEqualTo("service-unavailable");
            return false;
        }
    }

    static final class Fixture {
        final GameRegistry games;
        final SessionRegistry sessions = new SessionRegistry(20);
        final MutableTimeSource time = new MutableTimeSource(NOW);
        final SecretGenerator secrets = new SecretGenerator() {
            final AtomicInteger ids = new AtomicInteger();
            final AtomicInteger sessions = new AtomicInteger();
            final AtomicInteger invitations = new AtomicInteger();

            public String gameId() {
                return "game-" + ids.incrementAndGet();
            }

            public String sessionValue() {
                return "session-" + sessions.incrementAndGet();
            }

            public String invitationSecret() {
                return String.valueOf((char) ('A' + invitations.getAndIncrement()))
                        .repeat(43);
            }
        };
        final SnapshotProjector projector = new SnapshotProjector();
        final CreateGameUseCase create;
        final JoinGameUseCase join;
        final ReplaceInvitationUseCase replace;

        Fixture(int gameCap, int browserCap) {
            games = new GameRegistry(gameCap);
            create = new CreateGameUseCase(
                    games,
                    sessions,
                    secrets,
                    time,
                    projector,
                    browserCap,
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(120),
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(30),
                    "https://example.test");
            join = new JoinGameUseCase(
                    games,
                    sessions,
                    secrets,
                    time,
                    projector,
                    new RecordingPublisher(),
                    browserCap,
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(30));
            replace = new ReplaceInvitationUseCase(
                    games,
                    sessions,
                    secrets,
                    time,
                    projector,
                    new RecordingPublisher(),
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(30));
        }
    }
}
