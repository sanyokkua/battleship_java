package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ua.kostenko.battleship.app.realtime.StreamProbe;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.app.web.SseStream;
import ua.kostenko.battleship.application.MutableTimeSource;

/**
 * Owner of S12: a limit changed by configuration alone is both published by {@code getMeta} and enforced (R55).
 * Publication from the environment alone is proved on the packaged JAR, one child process per limit. Enforcement is
 * proved in-process with a {@link MutableTimeSource}, because waiting out a real 60 s per limit in a child JVM would
 * make the suite slow without proving more: both runs bind {@code battleship.*} through the same
 * {@code BattleshipProperties}. A limit that is not published ({@code max-concurrent-games}) is enforced too.
 */
@Import(PublishedLimitsIT.Determinism.class)
@TestPropertySource(
        properties = {
            "battleship.rate-limit.create-game-per-minute=100000",
            "battleship.rate-limit.join-per-minute=100000",
            "battleship.rate-limit.commands-per-minute=100000",
            "battleship.rate-limit.read-game-per-minute=100000",
            "battleship.rate-limit.presence-per-minute=100000",
            "battleship.rate-limit.stream-open-per-minute=100000",
            "battleship.rate-limit.leave-per-minute=100000",
            "server.shutdown=immediate"
        })
class PublishedLimitsIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";
    private static final Map<String, Integer> DEFAULTS = Map.of(
            "idleTimeoutSeconds", 900,
            "maxGameDurationSeconds", 7200,
            "resultRetentionSeconds", 300,
            "invitationLifetimeSeconds", 900,
            "presenceIntervalSeconds", 300,
            "heartbeatSeconds", 15);

    @TestConfiguration(proxyBeanMethods = false)
    static class Determinism {
        @Bean
        @Primary
        MutableTimeSource mutableTime() {
            return new MutableTimeSource(START);
        }
    }

    // (a) published from the environment alone, on the packaged JAR

    @ParameterizedTest
    @CsvSource({
        "BATTLESHIP_IDLETIMEOUTSECONDS,idleTimeoutSeconds,60",
        "BATTLESHIP_INVITATIONLIFETIMESECONDS,invitationLifetimeSeconds,120",
        "BATTLESHIP_PRESENCEINTERVALSECONDS,presenceIntervalSeconds,45",
        "BATTLESHIP_HEARTBEATSECONDS,heartbeatSeconds,7",
        "BATTLESHIP_RESULTRETENTIONSECONDS,resultRetentionSeconds,100",
        "BATTLESHIP_MAXGAMEDURATIONSECONDS,maxGameDurationSeconds,3600"
    })
    void aLimitSetByEnvironmentVariableAloneIsPublishedByTheRunningJar(String variable, String limit, int value)
            throws Exception {
        try (PackagedService jar = PackagedService.start(Map.of(variable, String.valueOf(value)))) {
            Map<String, Integer> expected = new LinkedHashMap<>(DEFAULTS);
            expected.put(limit, value);

            JsonNode limits = jar.getJson("/api/v1/meta").get("limits");

            Map<String, Integer> published = new LinkedHashMap<>();
            limits.fieldNames()
                    .forEachRemaining(
                            name -> published.put(name, limits.get(name).asInt()));
            assertThat(published).as(variable).containsExactlyInAnyOrderEntriesOf(expected);
        }
    }

    // (b) published and enforced, one context per limit

    private static Browser host(int port) throws Exception {
        Browser browser = new Browser(port);
        browser.create(SEA_BATTLE, "Host");
        return browser;
    }

    private static JsonNode publishedLimits(int port) throws Exception {
        return new Browser(port).http().call("GET", "/api/v1/meta", null).json().get("limits");
    }

    private static Instant expiresAt(JsonNode snapshot) {
        return Instant.parse(snapshot.get("expiresAt").asText());
    }

    private static void assertExpired(Reply read) {
        assertThat(read.status()).as(String.valueOf(read.response().body())).isEqualTo(410);
        assertThat(read.json().get("code").asText()).isEqualTo("game-expired");
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.idle-timeout-seconds=60")
    @DirtiesContext
    class IdleTimeout {
        @LocalServerPort
        int port;

        @Autowired
        MutableTimeSource time;

        @Test
        void sixtySecondsIsPublishedAndExpiresAnIdleGameAtSixtySeconds() throws Exception {
            assertThat(publishedLimits(port).get("idleTimeoutSeconds").asInt()).isEqualTo(60);
            Browser host = host(port);
            assertThat(expiresAt(host.snapshot())).isEqualTo(START.plusSeconds(60));

            time.advance(Duration.ofSeconds(59));
            assertThat(host.read().status()).as("one second before").isEqualTo(200);

            time.advance(Duration.ofSeconds(1));
            assertExpired(host.read());
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.invitation-lifetime-seconds=120")
    @DirtiesContext
    class InvitationLifetime {
        @LocalServerPort
        int port;

        @Autowired
        MutableTimeSource time;

        @Test
        void oneHundredTwentySecondsIsPublishedAndExpiresTheInvitationAtThatInstant() throws Exception {
            assertThat(publishedLimits(port).get("invitationLifetimeSeconds").asInt())
                    .isEqualTo(120);
            Browser host = host(port);
            String secret = Browser.invitationSecret(host.snapshot());

            time.advance(Duration.ofSeconds(119));
            assertThat(host.snapshot().has("invitationUrl"))
                    .as("one second before")
                    .isTrue();

            time.advance(Duration.ofSeconds(1));
            assertThat(host.snapshot().has("invitationUrl")).as("at expiry").isFalse();
            Reply join = new Browser(port)
                    .http()
                    .postJson(
                            "/api/v1/games/" + host.gameId() + "/join",
                            null,
                            "{\"invitationSecret\":\"" + secret + "\",\"displayName\":\"Late\"}");
            assertThat(join.json().get("code").asText()).isEqualTo("invitation-unavailable");
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"battleship.presence-interval-seconds=60", "battleship.idle-timeout-seconds=600"})
    @DirtiesContext
    class PresenceInterval {
        @LocalServerPort
        int port;

        @Autowired
        MutableTimeSource time;

        private JsonNode presence(Browser host) throws Exception {
            Reply reply = host.http()
                    .postWithToken(
                            "/api/v1/games/" + host.gameId() + "/presence",
                            host.http().freshToken(),
                            SESSION_COOKIE + "=" + host.sessionValue());
            assertThat(reply.status()).isEqualTo(200);
            return reply.json();
        }

        @Test
        void sixtySecondsIsPublishedAndMovesTheThrottleOfPresence() throws Exception {
            assertThat(publishedLimits(port).get("presenceIntervalSeconds").asInt())
                    .isEqualTo(60);
            Browser host = host(port);

            time.advance(Duration.ofSeconds(10));
            Instant first = expiresAt(presence(host));
            assertThat(first).as("the first signal extends").isEqualTo(START.plusSeconds(10 + 600));

            time.advance(Duration.ofSeconds(59));
            assertThat(expiresAt(presence(host)))
                    .as("59 s after the last extension")
                    .isEqualTo(first);

            time.advance(Duration.ofSeconds(1));
            assertThat(expiresAt(presence(host)))
                    .as("60 s after the last extension")
                    .isEqualTo(START.plusSeconds(70 + 600));
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.heartbeat-seconds=7")
    @DirtiesContext
    class Heartbeat {
        @LocalServerPort
        int port;

        @Autowired
        MutableTimeSource time;

        @Autowired
        ApplicationContext context;

        @Test
        void sevenSecondsIsPublishedAndSetsTheKeepAliveInterval() throws Exception {
            assertThat(publishedLimits(port).get("heartbeatSeconds").asInt()).isEqualTo(7);
            Browser host = host(port);
            try (SseStream stream = host.events()) {
                stream.next();

                time.advance(Duration.ofSeconds(6));
                StreamProbe.heartbeat(context);
                assertThat(stream.pollFrame(Duration.ofMillis(300)))
                        .as("one second before")
                        .isNull();

                time.advance(Duration.ofSeconds(1));
                StreamProbe.heartbeat(context);
                assertThat(stream.nextFrame().lines()).containsExactly(": keep-alive");
            }
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.result-retention-seconds=100")
    @DirtiesContext
    class ResultRetention {
        @LocalServerPort
        int port;

        @Autowired
        MutableTimeSource time;

        @Test
        void oneHundredSecondsIsPublishedAndForgetsTheResultAtThatInstant() throws Exception {
            assertThat(publishedLimits(port).get("resultRetentionSeconds").asInt())
                    .isEqualTo(100);
            Browser host = host(port);
            Browser guest = new Browser(port);
            guest.join(host.gameId(), Browser.invitationSecret(host.snapshot()), "Guest");
            for (Browser player : new Browser[] {host, guest}) {
                player.accept(Browser.simple("PLACE_FLEET_RANDOMLY"));
                player.accept(Browser.simple("READY"));
            }
            assertThat(host.accept(Browser.simple("RESIGN")).get("phase").asText())
                    .isEqualTo("FINISHED");

            time.advance(Duration.ofSeconds(99));
            assertThat(guest.read().status()).as("one second before").isEqualTo(200);

            time.advance(Duration.ofSeconds(1));
            Reply gone = guest.read();
            assertThat(gone.status()).as(String.valueOf(gone.response().body())).isEqualTo(404);
            assertThat(gone.json().get("code").asText()).isEqualTo("game-unavailable");
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.max-game-duration-seconds=120")
    @DirtiesContext
    class MaxGameDuration {
        @LocalServerPort
        int port;

        @Autowired
        MutableTimeSource time;

        @Test
        void oneHundredTwentySecondsIsPublishedAndIsTheAbsoluteCeiling() throws Exception {
            assertThat(publishedLimits(port).get("maxGameDurationSeconds").asInt())
                    .isEqualTo(120);
            Browser host = host(port);
            assertThat(expiresAt(host.snapshot())).isEqualTo(START.plusSeconds(120));

            time.advance(Duration.ofSeconds(119));
            assertThat(host.read().status()).as("one second before").isEqualTo(200);

            time.advance(Duration.ofSeconds(1));
            assertExpired(host.read());
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.max-concurrent-games=2")
    @DirtiesContext
    class UnpublishedCeiling {
        @LocalServerPort
        int port;

        @Test
        void aChangedLimitThatIsNotPublishedIsStillEnforced() throws Exception {
            assertThat(publishedLimits(port).size()).as("published limits").isEqualTo(6);
            host(port);
            host(port);

            Reply third = new Browser(port)
                    .http()
                    .postJson(
                            "/api/v1/games", null, "{\"rulesetId\":\"" + SEA_BATTLE + "\",\"displayName\":\"Third\"}");

            assertThat(third.status()).isEqualTo(503);
            assertThat(third.json().get("code").asText()).isEqualTo("service-unavailable");
        }
    }
}
