package ua.kostenko.battleship.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.TimeSource;

class SecretGeneratorTest {
    @Test
    void thousandGameIdsHaveContractFormatAnd128BitsAndAreDistinct() {
        SecretGenerator secrets = new SecureRandomSecretGenerator();
        var ids = new HashSet<String>();
        for (int index = 0; index < 1000; index++) {
            String id = secrets.gameId();
            assertTrue(id.matches("^[A-Za-z0-9_-]{22}$"));
            assertEquals(16, Base64.getUrlDecoder().decode(id).length);
            assertTrue(ids.add(id), "Game IDs must be distinct");
        }
    }

    @Test
    void thousandInvitationSecretsHaveContractFormatAnd256BitsAndAreDistinct() {
        SecretGenerator secrets = new SecureRandomSecretGenerator();
        var invitations = new HashSet<String>();
        for (int index = 0; index < 1000; index++) {
            String invitation = secrets.invitationSecret();
            assertTrue(invitation.matches("^[A-Za-z0-9_-]{43}$"));
            assertEquals(32, Base64.getUrlDecoder().decode(invitation).length);
            assertTrue(invitations.add(invitation), "Invitation secrets must be distinct");
        }
    }

    @Test
    void sessionValuesContain256Bits() {
        SecretGenerator secrets = new SecureRandomSecretGenerator();
        for (int index = 0; index < 1000; index++) {
            String session = secrets.sessionValue();
            assertTrue(session.matches("^[A-Za-z0-9_-]{43}$"));
            assertEquals(32, Base64.getUrlDecoder().decode(session).length);
        }
    }

    @Test
    void independentlyConstructedGeneratorsProduceDifferentGameIdStreams() {
        SecretGenerator first = new SecureRandomSecretGenerator();
        SecretGenerator second = new SecureRandomSecretGenerator();
        for (int index = 0; index < 10; index++) {
            assertNotEquals(first.gameId(), second.gameId());
        }
    }

    @Test
    void independentlyConstructedGeneratorsProduceDifferentSessionValues() {
        assertNotEquals(
                new SecureRandomSecretGenerator().sessionValue(), new SecureRandomSecretGenerator().sessionValue());
    }

    @Test
    void independentlyConstructedGeneratorsProduceDifferentInvitationSecrets() {
        assertNotEquals(
                new SecureRandomSecretGenerator().invitationSecret(),
                new SecureRandomSecretGenerator().invitationSecret());
    }

    @Test
    void systemTimeSourceReadsItsClockWithoutLosingPrecision() {
        TimeSource time =
                new SystemTimeSource(Clock.fixed(Instant.parse("2026-09-29T12:34:56.123456789Z"), ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-09-29T12:34:56.123456789Z"), time.now());
    }
}
