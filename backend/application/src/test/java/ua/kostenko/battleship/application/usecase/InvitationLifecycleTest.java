package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.result.ApplicationFailure;

class InvitationLifecycleTest {
    @Test
    void queuedReplacementStartsNewLifetimeAfterAcquiringGameLock() throws Exception {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", "host")
                .snapshot()
                .gameId();
        AtomicReference<SnapshotView> replacement = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = fixture.games.withSlot(id, slot -> {
            ReentrantLock lock = slotLock(slot);
            Thread joiner = Thread.ofPlatform().start(() -> {
                try {
                    replacement.set(fixture.replace.execute(id, "host"));
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lock.hasQueuedThread(joiner) && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(lock.hasQueuedThread(joiner)).isTrue();
            fixture.time.advance(Duration.ofSeconds(10));
            return joiner;
        });
        worker.join(5_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(replacement.get()).isNotNull();
        assertThat(replacement.get().invitationExpiresAt()).isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(40));
        Instant deadline = fixture.games.withSlot(id, slot -> slot.invitationDeadline());
        assertThat(deadline).isEqualTo(CreateGameUseCaseTest.NOW.plusSeconds(40));
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
    void replacementImmediatelyInvalidatesOldSecretWithoutMovingIdleDeadline() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", "host")
                .snapshot()
                .gameId();
        long createdVersion = fixture.games.withSlot(id, slot -> slot.state().version());
        var idle = fixture.games.withSlot(id, slot -> slot.idleDeadline());
        fixture.time.advance(Duration.ofSeconds(5));
        var replaced = fixture.replace.execute(id, "host");
        assertThat(replaced.invitationUrl()).isNotNull();
        assertThat(replaced.version()).isEqualTo(createdVersion + 1);
        java.time.Instant afterReplacement = fixture.games.withSlot(id, slot -> slot.idleDeadline());
        assertThat(afterReplacement).isEqualTo(idle);
        assertThatThrownBy(() -> fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", "guest"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("invitation-unavailable"));
        String replacementSecret = fixture.games.withSlot(id, slot -> slot.unusedInvitationSecret());
        assertThat(replacementSecret).isEqualTo("B".repeat(43));
        var joined = fixture.join.execute(id, "B".repeat(43), "Guest", "guest");
        assertThat(joined.snapshot().version()).isEqualTo(replaced.version() + 1);
        String consumed = fixture.games.withSlot(id, slot -> slot.unusedInvitationSecret());
        assertThat(consumed).isNull();
    }

    @Test
    void inclusiveInvitationExpiryDropsUnusedSecret() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", "host")
                .snapshot()
                .gameId();
        fixture.time.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", "guest"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("invitation-unavailable"));
        String unusedSecret = fixture.games.withSlot(id, slot -> slot.unusedInvitationSecret());
        assertThat(unusedSecret).isNull();
    }

    @Test
    void browserCapRefusesSecondCreateAndSecondJoinWithoutClaimingSeat() {
        var fixture = CreateGameUseCaseTest.fixture(4, 1);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        fixture.sessions.register("other", CreateGameUseCaseTest.NOW);
        fixture.sessions.register("guest", CreateGameUseCaseTest.NOW);
        String first = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "First", "host")
                .snapshot()
                .gameId();
        String second = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Second", "other")
                .snapshot()
                .gameId();
        assertThatThrownBy(() -> fixture.create.execute(CreateGameUseCaseTest.RULESET, "Again", "host"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("service-unavailable"));
        fixture.join.execute(first, CreateGameUseCaseTest.INVITATION, "Guest", "guest");
        assertThatThrownBy(() -> fixture.join.execute(second, "B".repeat(43), "Guest", "guest"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("service-unavailable"));
        ua.kostenko.battleship.domain.model.PlayerState unclaimedGuest =
                fixture.games.withSlot(second, slot -> slot.state().guest());
        assertThat(unclaimedGuest).isNull();
        assertThat(fixture.sessions.find("guest")).get().satisfies(record -> assertThat(record.liveGames())
                .containsExactly(first));
    }

    @Test
    void knownGuestGetsActionNotAllowedAndOutsiderGetsGameUnavailableOnReplacement() {
        var fixture = CreateGameUseCaseTest.fixture(2, 2);
        fixture.sessions.register("host", CreateGameUseCaseTest.NOW);
        fixture.sessions.register("guest", CreateGameUseCaseTest.NOW);
        fixture.sessions.register("outsider", CreateGameUseCaseTest.NOW);
        String id = fixture.create
                .execute(CreateGameUseCaseTest.RULESET, "Host", "host")
                .snapshot()
                .gameId();
        fixture.join.execute(id, CreateGameUseCaseTest.INVITATION, "Guest", "guest");
        assertThatThrownBy(() -> fixture.replace.execute(id, "guest"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("action-not-allowed"));
        assertThatThrownBy(() -> fixture.replace.execute(id, "outsider"))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> assertThat(failure.code())
                        .isEqualTo("game-unavailable"));
    }
}
