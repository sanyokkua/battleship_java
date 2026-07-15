package ua.kostenko.battleship.battleship.logic.api.impl;

import lombok.val;
import org.junit.jupiter.api.RepeatedTest;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEditionConfiguration;
import ua.kostenko.battleship.battleship.logic.engine.models.Player;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Coordinate;
import ua.kostenko.battleship.battleship.logic.engine.models.records.Ship;
import ua.kostenko.battleship.battleship.logic.persistence.InMemoryPersistence;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link GameControllerApiImpl} wired to a real {@link InMemoryPersistence} and
 * {@link IdGeneratorImpl} (no mocks) under genuine thread contention, proving that the
 * per-session lock added around each mutating method's load-mutate-save sequence actually
 * serializes concurrent requests against the same session instead of losing updates.
 */
class GameControllerApiImplConcurrencyTest {

    private GameControllerApiImpl newApi() {
        return new GameControllerApiImpl(new InMemoryPersistence(), new IdGeneratorImpl());
    }

    private void placeAllShips(final GameControllerApiImpl api, final String sessionId, final String playerId) {
        val ships = api.getShipsNotOnTheBoard(sessionId, playerId);
        var placed = 0;
        for (int row = 0; row < GameEditionConfiguration.NUMBER_OF_ROWS && placed < ships.size(); row += 2) {
            for (int col = 0; col < GameEditionConfiguration.NUMBER_OF_COLUMNS && placed < ships.size(); col += 5) {
                final Ship ship = ships.get(placed++);
                api.addShipToField(sessionId, playerId, ship.shipId(), Coordinate.of(row, col),
                        ship.shipDirection().name());
            }
        }
    }

    private <T> List<T> runConcurrently(final CountDownLatch startLatch, final List<Callable<T>> tasks)
            throws Exception {
        val pool = Executors.newFixedThreadPool(tasks.size());
        try {
            val futures = tasks.stream()
                    .map(task -> pool.submit(() -> {
                        startLatch.await();
                        return task.call();
                    }))
                    .toList();
            startLatch.countDown();

            val results = new java.util.ArrayList<T>();
            for (val future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    @RepeatedTest(100)
    void concurrentCreatePlayerInSession_bothPlayersEndUpPresentNoLostUpdate() throws Exception {
        val api = newApi();
        val sessionId = api.createGameSession(GameEdition.UKRAINIAN.name());

        val results = runConcurrently(new CountDownLatch(1), List.of(
                (Callable<Player>) () -> api.createPlayerInSession(sessionId, "Alice"),
                (Callable<Player>) () -> api.createPlayerInSession(sessionId, "Bob")
        ));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Player::getPlayerName)
                .containsExactlyInAnyOrder("Alice", "Bob");
        assertThat(api.getCurrentGameStage(sessionId)).isEqualTo(GameStage.PREPARATION);
    }

    @RepeatedTest(100)
    void concurrentStartGame_bothPlayersReadyingUpAtOnceLeavesConsistentState() throws Exception {
        val api = newApi();
        val sessionId = api.createGameSession(GameEdition.UKRAINIAN.name());
        val alice = api.createPlayerInSession(sessionId, "Alice");
        val bob = api.createPlayerInSession(sessionId, "Bob");
        placeAllShips(api, sessionId, alice.getPlayerId());
        placeAllShips(api, sessionId, bob.getPlayerId());

        val results = runConcurrently(new CountDownLatch(1), List.of(
                (Callable<Player>) () -> api.startGame(sessionId, alice.getPlayerId()),
                (Callable<Player>) () -> api.startGame(sessionId, bob.getPlayerId())
        ));

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(player -> assertThat(player.isReady()).isTrue());

        assertThat(api.getCurrentGameStage(sessionId)).isEqualTo(GameStage.IN_GAME);

        val aliceState = api.getGameState(sessionId, alice.getPlayerId());
        val bobState = api.getGameState(sessionId, bob.getPlayerId());
        // Exactly one player is active (the engine's deterministic first-ready-becomes-active
        // rule) - never both, and never neither, even under a genuine concurrent race.
        assertThat(aliceState.isPlayerActive() ^ bobState.isPlayerActive()).isTrue();
    }
}
