package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.domain.command.GameCommand;
import ua.kostenko.battleship.domain.model.Coordinate;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

class GameSerializationTest {
    @Test
    void simultaneousFiresLinearizeWithoutLostUpdateOrExtraVersion() throws Exception {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.startPlaying(game);
        long before = f.state(game).version();
        List<Coordinate> targets = f.state(game).guest().board().fleet().getFirst().cells().stream()
                .toList();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<CommandUseCase.CommandResult> first = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return f.send(game, Seat.HOST, new GameCommand.Fire(targets.get(0)));
            });
            Future<CommandUseCase.CommandResult> second = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return f.send(game, Seat.HOST, new GameCommand.Fire(targets.get(1)));
            });
            var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.snapshot().version()).toList())
                    .containsExactlyInAnyOrder(before + 1, before + 2);
            assertThat(results.stream()
                            .map(r -> r.snapshot().lastShot().target())
                            .toList())
                    .containsExactlyInAnyOrderElementsOf(targets.subList(0, 2));
            assertThat(f.state(game).version()).isEqualTo(before + 2);
            assertThat(f.state(game).host().shotsFired()).hasSize(2);
            assertThat(f.state(game).guest().board().incomingShots())
                    .containsExactlyInAnyOrderElementsOf(targets.subList(0, 2));
            for (var result : results) {
                assertThat(result.deliveries()).containsOnlyKeys(Seat.HOST, Seat.GUEST);
                assertThat(result.deliveries().values()).allSatisfy(view -> assertThat(view.version())
                        .isEqualTo(result.snapshot().version()));
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void bothReadyAtOnceStartsPlayExactlyOnceWithTwoViewChanges() throws Exception {
        var f = new CommandTestFixture();
        var game = f.newGame();
        f.placeFleet(game, Seat.HOST);
        f.placeFleet(game, Seat.GUEST);
        long before = f.state(game).version();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<CommandUseCase.CommandResult> host = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return f.send(game, Seat.HOST, new GameCommand.Ready());
            });
            Future<CommandUseCase.CommandResult> guest = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return f.send(game, Seat.GUEST, new GameCommand.Ready());
            });
            var results = List.of(host.get(5, TimeUnit.SECONDS), guest.get(5, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.snapshot().version()).toList())
                    .containsExactlyInAnyOrder(before + 1, before + 2);
            assertThat(results.stream().map(r -> r.snapshot().phase()).toList())
                    .containsExactlyInAnyOrder(Phase.PLACEMENT, Phase.PLAYING);
            GameState state = f.state(game);
            assertThat(state.phase()).isEqualTo(Phase.PLAYING);
            assertThat(state.version()).isEqualTo(before + 2);
            assertThat(state.timeline().playStartedAt()).isEqualTo(f.time.now());
            assertThat(state.timeline().readyAt()).containsOnlyKeys(Seat.HOST, Seat.GUEST);
            assertThat(state.turn()).isIn(Seat.HOST, Seat.GUEST);
            assertThat(state.timeline().shotDecisionStartedAt()).containsOnlyKeys(state.turn());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void differentGamesAndTerminalReleaseDoNotWaitForGlobalAdmission() throws Exception {
        var f = new CommandTestFixture();
        var a = f.newGame();
        var b = f.newGame();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> blocker = workers.submit(() -> f.games.withSlot(a.id(), slot -> {
                held.countDown();
                await(release);
                return null;
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var other = workers.submit(() -> f.send(
                    b,
                    Seat.HOST,
                    new GameCommand.PlaceShip(
                            "s01", new Coordinate(0, 0), ua.kostenko.battleship.domain.model.Orientation.HORIZONTAL)));
            assertThat(other.get(5, TimeUnit.SECONDS).snapshot().version()).isEqualTo(2);
            assertThat(f.state(b).version()).isEqualTo(2);
            release.countDown();
            blocker.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            workers.shutdownNow();
        }

        f.startPlaying(a);
        ReentrantLock admission = sessionLock(f.sessions);
        ExecutorService terminalWorker = Executors.newSingleThreadExecutor();
        admission.lock();
        try {
            var terminal = terminalWorker.submit(() -> f.send(a, Seat.HOST, new GameCommand.Resign()));
            assertThat(terminal.get(5, TimeUnit.SECONDS).snapshot().phase()).isEqualTo(Phase.FINISHED);
        } finally {
            admission.unlock();
            terminalWorker.shutdownNow();
        }
    }

    @Test
    void projectorRunsOutsideSlotAndDeliveryUsesCapturedTransition() throws Exception {
        AtomicReference<CommandTestFixture> fixtureRef = new AtomicReference<>();
        AtomicBoolean armed = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SnapshotProjector checking = new SnapshotProjector() {
            @Override
            public SnapshotView project(GameState state, Seat seat, SnapshotContext context) {
                var fixture = fixtureRef.get();
                int holdCount = fixture.games.withSlot(
                        context.gameId(), slot -> slotLock(slot).getHoldCount());
                assertThat(holdCount).isEqualTo(1);
                if (armed.compareAndSet(true, false)) {
                    entered.countDown();
                    await(release);
                }
                return super.project(state, seat, context);
            }
        };
        var f = new CommandTestFixture(checking);
        fixtureRef.set(f);
        var game = f.newGame();
        f.startPlaying(game);
        long before = f.state(game).version();
        List<Coordinate> targets = f.state(game).guest().board().fleet().getFirst().cells().stream()
                .toList();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            armed.set(true);
            var first = workers.submit(() -> f.send(game, Seat.HOST, new GameCommand.Fire(targets.get(0))));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> f.send(game, Seat.HOST, new GameCommand.Fire(targets.get(1))));
            assertThat(second.get(5, TimeUnit.SECONDS).snapshot().version()).isEqualTo(before + 2);
            release.countDown();
            var captured = first.get(5, TimeUnit.SECONDS);
            assertThat(captured.snapshot().version()).isEqualTo(before + 1);
            assertThat(captured.snapshot().lastShot().target()).isEqualTo(targets.get(0));
            assertThat(captured.deliveries()).containsOnlyKeys(Seat.HOST, Seat.GUEST);
            assertThat(captured.deliveries().get(Seat.HOST)).isSameAs(captured.snapshot());
            assertThat(captured.deliveries().get(Seat.GUEST).version()).isEqualTo(before + 1);
            assertThat(captured.deliveries().get(Seat.GUEST).lastShot().target())
                    .isEqualTo(targets.get(0));
            assertThatThrownBy(() -> captured.deliveries().clear()).isInstanceOf(UnsupportedOperationException.class);
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static ReentrantLock slotLock(GameSlot slot) {
        try {
            Field field = GameSlot.class.getDeclaredField("lock");
            field.setAccessible(true);
            return (ReentrantLock) field.get(slot);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ReentrantLock sessionLock(SessionRegistry sessions) {
        try {
            Field field = SessionRegistry.class.getDeclaredField("lock");
            field.setAccessible(true);
            return (ReentrantLock) field.get(sessions);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
