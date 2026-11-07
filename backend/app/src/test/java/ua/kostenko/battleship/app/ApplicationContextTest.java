package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.config.SecureRandomSecretGenerator;
import ua.kostenko.battleship.app.config.SystemTimeSource;
import ua.kostenko.battleship.application.FixedSecretGenerator;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.port.SecretGenerator;
import ua.kostenko.battleship.application.port.TimeSource;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.GameSlot;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.rules.Rulesets;

@SpringBootTest(properties = "battleship.max-concurrent-games=1")
class ApplicationContextTest {
    @Autowired
    private Environment environment;

    @Autowired
    private TimeSource timeSource;

    @Autowired
    private SecretGenerator secretGenerator;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private BattleshipProperties properties;

    @Test
    void contextProvidesProductionTimeAndSecretPorts() {
        assertInstanceOf(SystemTimeSource.class, timeSource);
        assertInstanceOf(SecureRandomSecretGenerator.class, secretGenerator);
    }

    @Test
    void applicationTestDoublesAreUsableFromTheDownstreamTestJar() {
        var time = new MutableTimeSource(Instant.EPOCH);
        time.advance(Duration.ofNanos(123));
        assertEquals(Instant.parse("1970-01-01T00:00:00.000000123Z"), time.now());
        SecretGenerator secrets = new FixedSecretGenerator("game", "session", "invitation");
        assertEquals("game", secrets.gameId());
        assertEquals("session", secrets.sessionValue());
        assertEquals("invitation", secrets.invitationSecret());
    }

    @Test
    void contextEnablesVirtualThreads() {
        assertEquals(Boolean.TRUE, environment.getProperty("spring.threads.virtual.enabled", Boolean.class));
    }

    @Test
    void gameRegistryUsesConfiguredCapacity() {
        Instant now = Instant.EPOCH;
        for (int i = 0; i < properties.maxConcurrentGames(); i++) {
            String id = "configured-" + i;
            gameRegistry.insert(
                    id,
                    new GameSlot(
                            id,
                            GameState.create(
                                    Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host"),
                            "digest",
                            now.plusSeconds(60),
                            now.plusSeconds(120),
                            now.plusSeconds(30),
                            "https://example.test"));
        }
        assertThatThrownBy(() -> gameRegistry.insert(
                        "over-capacity",
                        new GameSlot(
                                "over-capacity",
                                GameState.create(
                                        Rulesets.byId("sea-battle-10-ship.v1").orElseThrow(), "Host"),
                                "digest",
                                now.plusSeconds(60),
                                now.plusSeconds(120),
                                now.plusSeconds(30),
                                "https://example.test")))
                .isInstanceOf(ua.kostenko.battleship.application.registry.CapacityExceededException.class);
    }
}
