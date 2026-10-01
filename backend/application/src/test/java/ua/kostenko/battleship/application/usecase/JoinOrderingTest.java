package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.application.result.ApplicationFailure;
import ua.kostenko.battleship.domain.model.Phase;

class JoinOrderingTest {
    @Test
    void freshInvitationIsRedeemedOnceAndThirdBrowserMatchesWrongSecretRefusal() {
        var fixture = CreateGameUseCaseTest.fixture(3, 3);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        fixture.sessions.register("guest", CreateGameUseCaseTest.NOW);
        var created = fixture.create.execute(CreateGameUseCaseTest.RULESET, "Host", "host");
        var joined =
                fixture.join.execute(created.snapshot().gameId(), CreateGameUseCaseTest.INVITATION, "Guest", "guest");
        assertThat(joined.snapshot().phase()).isEqualTo(Phase.PLACEMENT);
        assertThat(joined.snapshot().you().displayName()).isEqualTo("Guest");
        assertThat(joined.snapshot().opponent().displayName()).isEqualTo("Host");
        assertThat(joined.snapshot().expiresAt()).isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(60));
        java.time.Instant joinedAt = fixture.games.withSlot(
                created.snapshot().gameId(), slot -> slot.state().timeline().guestJoinedAt());
        assertThat(joinedAt).isEqualTo(CreateGameUseCaseTest.NOW);
        ApplicationFailure used = catchThrowableOfType(
                () -> fixture.join.execute(
                        created.snapshot().gameId(), CreateGameUseCaseTest.INVITATION, "Third", "third"),
                ApplicationFailure.class);
        ApplicationFailure wrong = catchThrowableOfType(
                () -> fixture.join.execute(created.snapshot().gameId(), "B".repeat(43), "Third", "third"),
                ApplicationFailure.class);
        assertSameRefusal(used, wrong);
        assertThat(fixture.sessions.find("third")).isEmpty();
    }

    @Test
    void openingLinkWithoutConfirmingNameLeavesSeatAndInvitationAvailable() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        var created = fixture.create.execute(CreateGameUseCaseTest.RULESET, "Host", null);
        String id = created.snapshot().gameId();
        assertThat(created.snapshot().phase()).isEqualTo(Phase.WAITING);
        ua.kostenko.battleship.domain.model.PlayerState unclaimed =
                fixture.games.withSlot(id, slot -> slot.state().guest());
        assertThat(unclaimed).isNull();
        assertThat(created.snapshot().invitationUrl()).endsWith("#invite=" + CreateGameUseCaseTest.INVITATION);
        var joined = fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", null);
        assertThat(joined.snapshot().phase()).isEqualTo(Phase.PLACEMENT);
        assertThat(joined.sessionValue()).isEqualTo("session-2");
        assertThat(fixture.sessions.find(joined.sessionValue()))
                .get()
                .satisfies(record -> assertThat(record.liveGames()).containsExactly(id));
    }

    @Test
    void invalidJoinNameReturnsFieldRuleAndLeavesInvitationUsable() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", null)
                .snapshot()
                .gameId();
        Object[][] invalid = {
            {null, "REQUIRED"}, {"Ada\u0000", "INVALID_FORMAT"}, {" ", "TOO_SHORT"}, {"x".repeat(33), "TOO_LONG"}
        };
        for (Object[] candidate : invalid) {
            String name = (String) candidate[0];
            assertThatThrownBy(() -> fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, name, null))
                    .isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
                        assertThat(failure.code()).isEqualTo("validation-failed");
                        assertThat(failure.field()).isEqualTo("/displayName");
                        assertThat(failure.rule()).isEqualTo(candidate[1]);
                    });
        }
        ua.kostenko.battleship.domain.model.PlayerState unclaimed =
                fixture.games.withSlot(id, slot -> slot.state().guest());
        assertThat(unclaimed).isNull();
        assertThat(fixture.join
                        .execute(id, CreateGameUseCaseTest.INVITATION, "Valid", null)
                        .snapshot()
                        .phase())
                .isEqualTo(Phase.PLACEMENT);
    }

    @Test
    void guestMembershipPrecedesWrongOrStaleSecretAndDoesNotChangeState() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        fixture.sessions.register("guest", CreateGameUseCaseTest.NOW);
        var created = fixture.create.execute(CreateGameUseCaseTest.RULESET, "Host", "host");
        fixture.join.execute(created.snapshot().gameId(), CreateGameUseCaseTest.INVITATION, "Guest", "guest");
        var before = fixture.games.withSlot(created.snapshot().gameId(), slot -> slot.state());
        var rejoined = fixture.join.execute(created.snapshot().gameId(), "B".repeat(43), "Ignored", "guest");
        assertThat(rejoined.snapshot().version()).isEqualTo(1);
        assertThat(rejoined.snapshot().you().displayName()).isEqualTo("Guest");
        ua.kostenko.battleship.domain.model.GameState after =
                fixture.games.withSlot(created.snapshot().gameId(), slot -> slot.state());
        assertThat(after).isSameAs(before);
    }

    @Test
    void refusalPathsShareOneFailureShape() {
        var fixture = CreateGameUseCaseTest.fixture(3, 3);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        var created = fixture.create.execute(CreateGameUseCaseTest.RULESET, "Host", "host");
        String id = created.snapshot().gameId();
        List<ApplicationFailure> failures = new ArrayList<>();
        failures.add(catchThrowableOfType(
                () -> fixture.join.execute("missing", CreateGameUseCaseTest.INVITATION, "Guest", "guest"),
                ApplicationFailure.class));
        failures.add(catchThrowableOfType(
                () -> fixture.join.execute(id, "B".repeat(43), "Guest", "guest"), ApplicationFailure.class));
        failures.add(catchThrowableOfType(
                () -> fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Host", "host"),
                ApplicationFailure.class));
        var usedFixture = CreateGameUseCaseTest.fixture(2, 2);
        String usedId = usedFixture
                .create
                .execute(CreateGameUseCaseTest.RULESET, "Host", "host")
                .snapshot()
                .gameId();
        usedFixture.join.execute(usedId, CreateGameUseCaseTest.INVITATION, "Guest", "guest");
        failures.add(catchThrowableOfType(
                () -> usedFixture.join.execute(usedId, CreateGameUseCaseTest.INVITATION, "Third", "third"),
                ApplicationFailure.class));
        fixture.replace.execute(id, "host");
        failures.add(catchThrowableOfType(
                () -> fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", "guest"),
                ApplicationFailure.class));
        fixture.time.advance(Duration.ofSeconds(30));
        failures.add(catchThrowableOfType(
                () -> fixture.join.execute(id, "B".repeat(43), "Guest", "guest"), ApplicationFailure.class));
        for (ApplicationFailure failure : failures) assertSameRefusal(failures.getFirst(), failure);
    }

    @Test
    void concurrentRedemptionClaimsExactlyOneSeat() throws Exception {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", "host")
                .snapshot()
                .gameId();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String session = "guest-" + i;
                results.add(workers.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, session, session);
                        return true;
                    } catch (ApplicationFailure refused) {
                        assertThat(refused.code()).isEqualTo("invitation-unavailable");
                        return false;
                    }
                }));
            }
            assertThat((results.get(0).get(5, TimeUnit.SECONDS) ? 1 : 0)
                            + (results.get(1).get(5, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
            ua.kostenko.battleship.domain.model.PlayerState guest =
                    fixture.games.withSlot(id, slot -> slot.state().guest());
            assertThat(guest).isNotNull();
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void queuedJoinChecksInvitationDeadlineAfterAcquiringGameLock() throws Exception {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", null)
                .snapshot()
                .gameId();
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        fixture.games
                .withSlot(id, slot -> {
                    ReentrantLock lock;
                    try {
                        var method = slot.getClass().getDeclaredMethod("lock");
                        method.setAccessible(true);
                        lock = (ReentrantLock) method.invoke(slot);
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError(exception);
                    }
                    Thread joiner = Thread.ofPlatform().start(() -> {
                        try {
                            fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", null);
                        } catch (Throwable failure) {
                            outcome.set(failure);
                        }
                    });
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (!lock.hasQueuedThread(joiner) && System.nanoTime() < deadline) Thread.onSpinWait();
                    assertThat(lock.hasQueuedThread(joiner)).isTrue();
                    fixture.time.advance(Duration.ofSeconds(30));
                    return joiner;
                })
                .join(5_000);
        assertThat(outcome.get()).isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                .isEqualTo("invitation-unavailable"));
        ua.kostenko.battleship.domain.model.PlayerState guest =
                fixture.games.withSlot(id, slot -> slot.state().guest());
        assertThat(guest).isNull();
    }

    @Test
    void joinQueuedOnSessionAdmissionRechecksInvitationExpiryBeforeClaimingSeat() throws Exception {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", null)
                .snapshot()
                .gameId();
        CountDownLatch admissionEntered = new CountDownLatch(1);
        CountDownLatch releaseAdmission = new CountDownLatch(1);
        AtomicReference<Throwable> blockerFailure = new AtomicReference<>();
        Thread blocker = Thread.ofPlatform().start(() -> {
            try {
                fixture.sessions.admitGame("blocker", "blocker-game", 2, () -> {
                    admissionEntered.countDown();
                    try {
                        assertThat(releaseAdmission.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    return CreateGameUseCaseTest.NOW;
                });
            } catch (Throwable thrown) {
                blockerFailure.set(thrown);
            }
        });
        AtomicReference<Throwable> joinFailure = new AtomicReference<>();
        Thread joiner = null;
        try {
            assertThat(admissionEntered.await(5, TimeUnit.SECONDS)).isTrue();
            joiner = Thread.ofPlatform().start(() -> {
                try {
                    fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", null);
                } catch (Throwable thrown) {
                    joinFailure.set(thrown);
                }
            });
            ReentrantLock sessionLock = sessionLock(fixture.sessions);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!sessionLock.hasQueuedThread(joiner) && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(sessionLock.hasQueuedThread(joiner)).isTrue();
            fixture.time.advance(Duration.ofSeconds(30));
        } finally {
            releaseAdmission.countDown();
        }
        blocker.join(5_000);
        joiner.join(5_000);
        assertThat(blocker.isAlive()).isFalse();
        assertThat(joiner.isAlive()).isFalse();
        assertThat(blockerFailure.get()).isNull();
        assertThat(joinFailure.get()).isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
            assertThat(failure.code()).isEqualTo("invitation-unavailable");
            assertThat(failure.field()).isNull();
            assertThat(failure.rule()).isNull();
            assertThat(failure.retryAfterSeconds()).isNull();
        });
        ua.kostenko.battleship.domain.model.PlayerState guest =
                fixture.games.withSlot(id, slot -> slot.state().guest());
        assertThat(guest).isNull();
        assertThat(fixture.sessions.find("session-2")).isEmpty();
        String unusedSecret = fixture.games.withSlot(id, slot -> slot.unusedInvitationSecret());
        assertThat(unusedSecret).isNull();
    }

    @Test
    void successfulJoinUsesAdmissionInstantForGuestAndIdleDeadline() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", null)
                .snapshot()
                .gameId();
        AtomicInteger samples = new AtomicInteger();
        TimeSource advancingAtAdmission = () ->
                samples.getAndIncrement() == 0 ? CreateGameUseCaseTest.NOW : CreateGameUseCaseTest.NOW.plusSeconds(10);
        JoinGameUseCase join = new JoinGameUseCase(
                fixture.games,
                fixture.sessions,
                fixture.secrets,
                advancingAtAdmission,
                fixture.projector,
                2,
                Duration.ofSeconds(60),
                Duration.ofSeconds(30));
        var joined = join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", null);
        assertThat(samples.get()).isEqualTo(2);
        assertThat(joined.snapshot().serverTime()).isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(10));
        assertThat(joined.snapshot().expiresAt()).isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(70));
        java.time.Instant joinedAt =
                fixture.games.withSlot(id, slot -> slot.state().timeline().guestJoinedAt());
        assertThat(joinedAt).isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(10));
        assertThat(fixture.sessions.find(joined.sessionValue())).get().satisfies(record -> assertThat(
                        record.createdAt())
                .isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(10)));
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

    static void assertSameRefusal(ApplicationFailure first, ApplicationFailure other) {
        assertThat(first.code()).isEqualTo("invitation-unavailable");
        assertThat(other.code()).isEqualTo(first.code());
        assertThat(other.field()).isEqualTo(first.field());
        assertThat(other.rule()).isEqualTo(first.rule());
        assertThat(other.retryAfterSeconds()).isEqualTo(first.retryAfterSeconds());
    }
}
