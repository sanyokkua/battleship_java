package ua.kostenko.battleship.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.domain.model.Seat;

class RecordingPublisherTest {
    @Test
    void interruptedLockProbePreservesTheCallingThreadsInterrupt() {
        var fixture = new CommandTestFixture();
        var game = fixture.newGame();
        try {
            fixture.games.withSlot(game.id(), slot -> {
                Thread.currentThread().interrupt();
                fixture.published.unavailable(game.id(), Seat.HOST);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                return null;
            });
        } finally {
            Thread.interrupted();
        }
    }
}
