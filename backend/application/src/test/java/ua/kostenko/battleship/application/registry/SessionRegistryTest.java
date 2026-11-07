package ua.kostenko.battleship.application.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.SessionFixtures;

class SessionRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private static void idle(SessionRegistry sessions, String value, Instant at) {
        SessionFixtures.register(sessions, value, at);
    }

    @Test
    void evictsTheLongestIdleSessionAndAuthorizedAccessCountsAsActivity() {
        SessionRegistry sessions = new SessionRegistry(2);
        idle(sessions, "first-secret", NOW);
        idle(sessions, "second-secret", NOW.plusSeconds(1));

        assertThat(sessions.authenticate("first-secret", NOW.plusSeconds(2))).isPresent();
        idle(sessions, "third-secret", NOW.plusSeconds(3));

        assertThat(sessions.find("first-secret")).isPresent();
        assertThat(sessions.find("second-secret")).isEmpty();
        assertThat(sessions.find("third-secret")).isPresent();
    }

    @Test
    void withoutActivityTheOldestRegistrationIsTheVictim() {
        SessionRegistry sessions = new SessionRegistry(2);
        idle(sessions, "first-secret", NOW);
        idle(sessions, "second-secret", NOW.plusSeconds(1));
        idle(sessions, "third-secret", NOW.plusSeconds(3));

        assertThat(sessions.find("first-secret")).isEmpty();
        assertThat(sessions.find("second-secret")).isPresent();
    }

    @Test
    void authenticatingAnUnknownSessionRegistersNothing() {
        SessionRegistry sessions = new SessionRegistry(1);

        assertThat(sessions.authenticate("invented-secret", NOW)).isEmpty();
        assertThat(sessions.find("invented-secret")).isEmpty();
    }

    @Test
    void sessionsHoldingALiveGameAreNeverEvicted() {
        SessionRegistry sessions = new SessionRegistry(2);
        sessions.admitGame("active-secret", "game-one", 1, () -> NOW);
        idle(sessions, "inactive-secret", NOW.plusSeconds(1));
        for (int i = 0; i < 100; i++) idle(sessions, "invented-" + i, NOW.plusSeconds(i + 2));

        assertThat(sessions.find("active-secret")).isPresent();
        assertThat(sessions.find("inactive-secret")).isEmpty();
    }

    @Test
    void whenEverySessionHoldsAGameANewOneIsRefusedWithoutRunningAdmission() {
        SessionRegistry sessions = new SessionRegistry(1);
        sessions.admitGame("host-secret", "game-one", 1, () -> NOW);
        AtomicBoolean admitted = new AtomicBoolean();

        assertThatThrownBy(() -> sessions.admitGame("invented-secret", "game-two", 1, () -> {
                    admitted.set(true);
                    return NOW;
                }))
                .isInstanceOf(CapacityExceededException.class);

        assertThat(admitted).isFalse();
        assertThat(sessions.find("invented-secret")).isEmpty();
        assertThat(sessions.find("host-secret")).get().satisfies(session -> assertThat(session.liveGames())
                .containsExactly("game-one"));
    }

    @Test
    void releasingTheGameLiftsThePerBrowserCapAndMakesTheSessionEvictable() {
        SessionRegistry sessions = new SessionRegistry(1);
        sessions.admitGame("host-secret", "game-one", 1, () -> NOW);
        assertThatThrownBy(() -> sessions.admitGame("host-secret", "game-two", 1, () -> NOW))
                .isInstanceOf(BrowserGameLimitException.class);

        sessions.releaseGame(SessionRegistry.digest("host-secret"), "game-one");

        sessions.admitGame("other-secret", "game-three", 1, () -> NOW.plusSeconds(1));
        assertThat(sessions.find("host-secret")).isEmpty();
        assertThat(sessions.find("other-secret")).isPresent();
    }

    @Test
    void retainsNoRawSessionValueInItsObjectGraph() throws IllegalAccessException {
        SessionRegistry sessions = new SessionRegistry(2);
        String raw = "raw-session-canary-7b9f6e";
        sessions.admitGame(raw, "game-one", 1, () -> NOW);

        assertThat(retainedStrings(sessions))
                .contains(SessionRegistry.digest(raw))
                .doesNotContain(raw);
    }

    @Test
    void callersCannotMutateSessionOwnershipOutsideTheRegistryLock() {
        SessionRegistry sessions = new SessionRegistry(1);
        sessions.admitGame("host-secret", "game-one", 1, () -> NOW);

        var liveGames = sessions.find("host-secret").orElseThrow().liveGames();

        assertThatThrownBy(liveGames::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void concurrentInventedSessionsRespectTheHardCap() throws Exception {
        SessionRegistry sessions = new SessionRegistry(5);
        ExecutorService workers = Executors.newFixedThreadPool(16);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++) {
                String value = "invented-" + i;
                jobs.add(workers.submit(() -> {
                    try {
                        idle(sessions, value, NOW);
                    } catch (CapacityExceededException _) {
                        // Another caller's setup game was momentarily live, so nothing was evictable.
                    }
                }));
            }
            for (Future<?> job : jobs) job.get(5, TimeUnit.SECONDS);

            long known = IntStream.range(0, 100)
                    .filter(i -> sessions.find("invented-" + i).isPresent())
                    .count();
            assertThat(known).isBetween(1L, 5L);
        } finally {
            workers.shutdownNow();
        }
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
