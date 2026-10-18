package ua.kostenko.battleship.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ConfigurationValidationIT {
    @Test
    void defaultApplicationBindsAllSettings() {
        try (var context = new SpringApplicationBuilder(ua.kostenko.battleship.app.BattleshipApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            var properties = context.getBean(BattleshipProperties.class);

            assertEquals(900, properties.idleTimeoutSeconds());
            assertEquals(7200, properties.maxGameDurationSeconds());
            assertEquals(300, properties.resultRetentionSeconds());
            assertEquals(900, properties.invitationLifetimeSeconds());
            assertEquals(300, properties.presenceIntervalSeconds());
            assertEquals(15, properties.heartbeatSeconds());
            assertEquals(1200, properties.streamMaxLifetimeSeconds());
            assertEquals(100, properties.maxConcurrentGames());
            assertEquals(200, properties.maxConcurrentStreams());
            assertEquals(1, properties.maxLiveGamesPerBrowser());
            assertEquals(16384, properties.maxRequestBodyBytes());
            assertEquals(1000, properties.randomArrangementAttempts());
            assertEquals(30, properties.sweepIntervalSeconds());
            assertEquals(5, properties.shutdownDrainSeconds());
            assertEquals("http://localhost:5173", properties.publicBaseUrl());
            var rateLimit = properties.rateLimit();
            assertEquals(5, rateLimit.createGamePerMinute());
            assertEquals(20, rateLimit.joinPerMinute());
            assertEquals(60, rateLimit.commandsPerMinute());
            assertEquals(120, rateLimit.readGamePerMinute());
            assertEquals(30, rateLimit.presencePerMinute());
            assertEquals(30, rateLimit.streamOpenPerMinute());
            assertEquals(10, rateLimit.replaceInvitationPerMinute());
            assertEquals(10, rateLimit.leavePerMinute());
        }
    }

    @Test
    void zeroIdleTimeoutStopsStartupAndNamesSetting() {
        assertInvalidSetting("idle-timeout-seconds", "0");
    }

    @Test
    void negativeIdleTimeoutStopsStartupAndNamesSetting() {
        assertInvalidSetting("idle-timeout-seconds", "-1");
    }

    @Test
    void unparseableIdleTimeoutStopsStartupAndNamesSetting() {
        assertInvalidSetting("idle-timeout-seconds", "not-a-number");
    }

    @Test
    void absentIdleTimeoutStopsStartupAndNamesSetting() {
        var failure = assertThrows(RuntimeException.class, () -> new SpringApplicationBuilder(
                        ua.kostenko.battleship.app.BattleshipApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.name=configuration-validation-missing")
                .run());

        assertTrue(causeMessages(failure).contains("idle-timeout-seconds"), causeMessages(failure));
    }

    @Test
    void relativePublicBaseUrlStopsStartupAndNamesSetting() {
        assertInvalidSetting("public-base-url", "/join");
    }

    @Test
    void ftpPublicBaseUrlStopsStartupAndNamesSetting() {
        assertInvalidSetting("public-base-url", "ftp://host");
    }

    @Test
    void credentialedPublicBaseUrlStopsStartupAndNamesSetting() {
        assertInvalidSetting("public-base-url", "http://user:pw@host");
    }

    @Test
    void queriedPublicBaseUrlStopsStartupAndNamesSetting() {
        assertInvalidSetting("public-base-url", "http://host?a=b");
    }

    @Test
    void fragmentedPublicBaseUrlStopsStartupAndNamesSetting() {
        assertInvalidSetting("public-base-url", "http://host#frag");
    }

    @Test
    void environmentVariableOverridesYamlDefault() throws Exception {
        var javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java")
                .toString();
        var builder = new ProcessBuilder(
                        javaExecutable,
                        "-cp",
                        System.getProperty("java.class.path"),
                        EnvironmentOverrideProbe.class.getName())
                .redirectErrorStream(true);
        builder.environment().put("BATTLESHIP_IDLETIMEOUTSECONDS", "60");
        var process = builder.start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("IDLE_TIMEOUT_SECONDS=60"), output);
    }

    private static void assertInvalidSetting(String key, String value) {
        var failure = assertThrows(RuntimeException.class, () -> startWith(key, value));
        assertTrue(causeMessages(failure).contains(key), causeMessages(failure));
    }

    private static org.springframework.context.ConfigurableApplicationContext startWith(String key, String value) {
        var source =
                new org.springframework.core.env.MapPropertySource("test-override", Map.of("battleship." + key, value));
        return new SpringApplicationBuilder(ua.kostenko.battleship.app.BattleshipApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(
                        context -> context.getEnvironment().getPropertySources().addFirst(source))
                .run();
    }

    private static String causeMessages(Throwable failure) {
        var messages = new StringBuilder();
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
