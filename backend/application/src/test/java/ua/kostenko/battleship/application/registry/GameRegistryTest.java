package ua.kostenko.battleship.application.registry;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.model.*;
import ua.kostenko.battleship.domain.rules.Rulesets;

class GameRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private GameSlot slot(String id) {
        return new GameSlot(
                id,
                GameState.create(Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host"),
                "host-digest",
                NOW.plusSeconds(60),
                NOW.plusSeconds(120),
                NOW.plusSeconds(30),
                "https://example.test");
    }

    @Test
    void gameCeilingRefusesWithoutEvictingFirstGame() {
        GameRegistry registry = new GameRegistry(1);
        GameSlot first = slot("first");
        registry.insert("first", first);
        GameSlot second = slot("second");
        assertThatThrownBy(() -> registry.insert("second", second)).isInstanceOf(CapacityExceededException.class);
        GameState retained = registry.withSlot("first", s -> s.state());
        assertThat(retained).isSameAs(first.state());
    }

    @Test
    void gamePermitIsReleasedExactlyOnceAcrossTenThousandCycles() {
        GameRegistry registry = new GameRegistry(1);
        for (int i = 0; i < 10_000; i++) {
            String id = "game-" + i;
            registry.insert(id, slot(id));
            registry.remove(id);
            registry.remove(id);
        }
        registry.insert("last", slot("last"));
        GameSlot over = slot("over");
        assertThatThrownBy(() -> registry.insert("over", over)).isInstanceOf(CapacityExceededException.class);
    }

    @Test
    void concurrentAdmissionNeverExceedsGameCeiling() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(32);
        try {
            for (int round = 0; round < 100; round++) {
                GameRegistry registry = new GameRegistry(1);
                CyclicBarrier start = new CyclicBarrier(32);
                var jobs = new ArrayList<Future<Boolean>>();
                for (int i = 0; i < 32; i++) {
                    String id = "round-" + round + "-game-" + i;
                    GameSlot candidate = slot(id);
                    jobs.add(workers.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        try {
                            registry.insert(id, candidate);
                            return true;
                        } catch (CapacityExceededException _) {
                            return false;
                        }
                    }));
                }
                int accepted = 0;
                for (Future<Boolean> job : jobs) if (job.get(10, TimeUnit.SECONDS)) accepted++;
                assertThat(accepted).as("admitted games in round %s", round).isEqualTo(1);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void withSlotReleasesLockWhenFunctionThrows() throws Exception {
        GameRegistry registry = new GameRegistry(1);
        registry.insert("one", slot("one"));
        assertThatThrownBy(() -> registry.withSlot("one", s -> {
                    throw new IllegalStateException("failure");
                }))
                .isInstanceOf(IllegalStateException.class);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            assertThat(other.submit(() -> registry.withSlot("one", s -> s.state()))
                            .get(5, TimeUnit.SECONDS))
                    .isNotNull();
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    void withSlotHoldsLockUntilFunctionReturnsAndRemovalWaits() throws Exception {
        GameRegistry registry = new GameRegistry(1);
        registry.insert("one", slot("one"));
        ExecutorService workers = Executors.newFixedThreadPool(3);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch contendersStarted = new CountDownLatch(2);
        CountDownLatch secondCallbackEntered = new CountDownLatch(1);
        try {
            Future<?> action = workers.submit(() -> registry.withSlot("one", s -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> removal = workers.submit(() -> {
                contendersStarted.countDown();
                registry.remove("one");
            });
            Future<?> competingRead = workers.submit(() -> {
                contendersStarted.countDown();
                try {
                    registry.withSlot("one", s -> {
                        secondCallbackEntered.countDown();
                        return null;
                    });
                } catch (IllegalArgumentException _) {
                    // Removing the slot first is an allowed outcome.
                }
            });
            assertThat(contendersStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> removal.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(secondCallbackEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();
            release.countDown();
            action.get(5, TimeUnit.SECONDS);
            removal.get(5, TimeUnit.SECONDS);
            competingRead.get(5, TimeUnit.SECONDS);
            registry.insert("two", slot("two"));
            GameState replacement = registry.withSlot("two", s -> s.state());
            assertThat(replacement).isNotNull();
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void registryHasNoSynchronizedMethod() {
        for (Class<?> type :
                new Class<?>[] {GameRegistry.class, GameSlot.class, SessionRegistry.class, SessionRecord.class}) {
            assertThat(Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isSynchronized(m.getModifiers())))
                    .isEmpty();
        }
    }

    @Test
    void slotContextUsesEarliestDeadlineAndHostOnlyInvitation() {
        GameSlot slot = slot("game-one");
        slot.unusedInvitationSecret("invitation");
        var host = slot.contextFor(Seat.HOST, NOW);
        assertThat(host.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(host.invitationUrl()).isEqualTo("https://example.test/join/game-one#invite=invitation");
        assertThat(host.invitationExpiresAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(slot.contextFor(Seat.GUEST, NOW).invitationUrl()).isNull();
    }

    @Test
    void invitationUrlRemovesOnlyTrailingSlashesFromBaseUrl() {
        for (String base :
                List.of("https://example.test/path", "https://example.test/path/", "https://example.test/path///")) {
            GameSlot slot = new GameSlot(
                    "game-one",
                    GameState.create(Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host"),
                    "host-digest",
                    NOW.plusSeconds(60),
                    NOW.plusSeconds(120),
                    NOW.plusSeconds(30),
                    base);
            slot.unusedInvitationSecret("invitation");
            assertThat(slot.contextFor(Seat.HOST, NOW).invitationUrl())
                    .isEqualTo("https://example.test/path/join/game-one#invite=invitation");
        }
    }
}
