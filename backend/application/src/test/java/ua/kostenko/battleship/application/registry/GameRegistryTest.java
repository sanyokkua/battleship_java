package ua.kostenko.battleship.application.registry;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
        assertThatThrownBy(() -> registry.insert("second", slot("second")))
                .isInstanceOf(CapacityExceededException.class);
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
        assertThatThrownBy(() -> registry.insert("over", slot("over"))).isInstanceOf(CapacityExceededException.class);
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
                        } catch (CapacityExceededException refused) {
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
                } catch (InterruptedException e) {
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
                } catch (IllegalArgumentException removedFirst) {
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
    void sessionRegistryStoresDigestAndDropsLeastRecentlySeen() {
        SessionRegistry sessions = new SessionRegistry(2);
        String first = "first-secret-session";
        String second = "second-secret-session";
        String third = "third-secret-session";
        sessions.register(first, NOW);
        sessions.register(second, NOW.plusSeconds(1));
        sessions.touch(first, NOW.plusSeconds(2));
        sessions.register(third, NOW.plusSeconds(3));
        assertThat(sessions.find(first)).isPresent();
        assertThat(sessions.find(second)).isEmpty();
        assertThat(sessions.find(third)).isPresent();
        assertThat(sessions.digests()).contains(SessionRegistry.digest(first)).doesNotContain(first, second, third);
    }

    @Test
    void sessionRegistryRetainsNoRawSessionValueInItsObjectGraph() throws IllegalAccessException {
        SessionRegistry sessions = new SessionRegistry(2);
        String raw = "raw-session-canary-7b9f6e";
        sessions.registerForGame(raw, "game-one", NOW);

        assertThat(retainedStrings(sessions))
                .contains(SessionRegistry.digest(raw))
                .doesNotContain(raw);
    }

    @Test
    void inventedSessionsCannotEvictLiveGameSession() {
        SessionRegistry sessions = new SessionRegistry(2);
        sessions.registerForGame("active-secret", "game-one", NOW);
        sessions.register("inactive-secret", NOW.plusSeconds(1));
        for (int i = 0; i < 100; i++) sessions.register("invented-" + i, NOW.plusSeconds(i + 2));
        assertThat(sessions.find("active-secret")).isPresent();
        assertThat(sessions.digests()).hasSize(2).doesNotContain("active-secret");
    }

    @Test
    void registrationAttachesGameBeforeAnotherSessionCanEvictIt() {
        SessionRegistry sessions = new SessionRegistry(1);
        sessions.registerForGame("host-secret", "game-one", NOW);
        assertThatThrownBy(() -> sessions.register("invented-secret", NOW.plusSeconds(1)))
                .isInstanceOf(CapacityExceededException.class);
        assertThat(sessions.find("host-secret")).get().satisfies(record -> assertThat(record.liveGames())
                .containsExactly("game-one"));
        assertThat(sessions.find("invented-secret")).isEmpty();
    }

    @Test
    void concurrentAssociationCannotLoseItsSessionToAnInventedCaller() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 100; round++) {
                SessionRegistry sessions = new SessionRegistry(1);
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> owner = workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    sessions.registerForGame("host-secret", "game-one", NOW);
                    return null;
                });
                Future<?> invented = workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        sessions.register("invented-secret", NOW);
                    } catch (CapacityExceededException refused) {
                        // The owner associated first; refusing an invented caller is expected.
                    }
                    return null;
                });
                owner.get(5, TimeUnit.SECONDS);
                invented.get(5, TimeUnit.SECONDS);
                assertThat(sessions.find("host-secret")).get().satisfies(record -> assertThat(record.liveGames())
                        .containsExactly("game-one"));
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void callersCannotMutateSessionOwnershipOutsideRegistryLock() {
        SessionRegistry sessions = new SessionRegistry(1);
        SessionRecord record = sessions.registerForGame("host-secret", "game-one", NOW);
        assertThatThrownBy(() -> record.liveGames().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void publicSessionApiCannotInsertAnUndigestedKey() {
        assertThat(Arrays.stream(SessionRegistry.class.getMethods())
                        .filter(method -> method.getDeclaringClass() == SessionRegistry.class)
                        .map(java.lang.reflect.Method::getName))
                .doesNotContain("registerDigest");
    }

    @Test
    void concurrentInventedSessionsRespectHardCap() throws Exception {
        SessionRegistry sessions = new SessionRegistry(5);
        ExecutorService workers = Executors.newFixedThreadPool(16);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++) {
                String value = "invented-" + i;
                jobs.add(workers.submit(() -> sessions.register(value, NOW)));
            }
            for (Future<?> job : jobs) job.get(5, TimeUnit.SECONDS);
            assertThat(sessions.digests()).hasSize(5);
        } finally {
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

    private static List<String> retainedStrings(Object root) throws IllegalAccessException {
        List<String> strings = new ArrayList<>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var pending = new ArrayDeque<Object>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Object value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (value instanceof String string) {
                strings.add(string);
            } else if (value instanceof Map<?, ?> map) {
                map.forEach((key, entry) -> {
                    pending.add(key);
                    pending.add(entry);
                });
            } else if (value instanceof Iterable<?> iterable) {
                iterable.forEach(pending::add);
            } else if (value.getClass().getPackageName().equals("ua.kostenko.battleship.application.registry")) {
                for (Field field : value.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object held = field.get(value);
                    if (held != null) pending.add(held);
                }
            }
        }
        return strings;
    }
}
