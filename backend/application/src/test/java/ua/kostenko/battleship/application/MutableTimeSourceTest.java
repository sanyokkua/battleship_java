package ua.kostenko.battleship.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.TimeSource;

class MutableTimeSourceTest {
    @Test
    void initialInstantIsReturnedWithoutLosingPrecision() {
        TimeSource time = new MutableTimeSource(Instant.parse("2026-09-29T12:34:56.123456789Z"));
        assertEquals(Instant.parse("2026-09-29T12:34:56.123456789Z"), time.now());
    }

    @Test
    void setReplacesTheCurrentInstant() {
        var time = new MutableTimeSource(Instant.EPOCH);
        time.set(Instant.parse("2026-09-29T12:34:56.987654321Z"));
        assertEquals(Instant.parse("2026-09-29T12:34:56.987654321Z"), time.now());
    }

    @Test
    void advancePreservesSubsecondPrecision() {
        var time = new MutableTimeSource(Instant.parse("2026-09-29T12:34:56.123456789Z"));
        time.advance(Duration.ofSeconds(2).plusNanos(333));
        assertEquals(Instant.parse("2026-09-29T12:34:58.123457122Z"), time.now());
    }

    @Test
    void fixedGeneratorReturnsTheSuppliedValuesOnRepeatedCalls() {
        SecretGenerator secrets = new FixedSecretGenerator("game", "session", "invitation");
        for (int call = 0; call < 2; call++) {
            assertEquals("game", secrets.gameId());
            assertEquals("session", secrets.sessionValue());
            assertEquals("invitation", secrets.invitationSecret());
        }
    }
}
