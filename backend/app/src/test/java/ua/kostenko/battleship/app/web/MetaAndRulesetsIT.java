package ua.kostenko.battleship.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.application.port.TimeSource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MetaAndRulesetsIT.FixedTime.class)
class MetaAndRulesetsIT {
    private static final Instant NOW = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String HEX16 = "[0-9a-f]{16}";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean
        @Primary
        TimeSource fixedTime() {
            return () -> NOW;
        }
    }

    private record Reply(int status, HttpResponse<String> response, JsonNode json) {
        String header(String name) {
            return response.headers().firstValue(name).orElse("");
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private BattleshipProperties properties;

    @Autowired
    private ApplicationContext context;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private Reply call(String method, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String raw = response.body();
        return new Reply(response.statusCode(), response, raw.isBlank() ? null : mapper.readTree(raw));
    }

    /** Passes the anti-forgery check the way a client does, so the request reaches the unsupported-method path. */
    private Reply postWithFreshToken(String path) throws Exception {
        String cookie = call("GET", "/api/v1/meta").response().headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow();
        String token = cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String raw = response.body();
        return new Reply(response.statusCode(), response, raw.isBlank() ? null : mapper.readTree(raw));
    }

    @Test
    void metaPublishesTheContractVersionTheClockAndTheEnforcedLimits() throws Exception {
        Reply reply = call("GET", "/api/v1/meta");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("apiVersion").asText()).matches("^1\\.[0-9]+\\.[0-9]+$");
        assertThat(reply.json().get("apiVersion").asText()).isEqualTo("1.0.0");
        assertThat(Instant.parse(reply.json().get("serverTime").asText())).isEqualTo(NOW);
        JsonNode limits = reply.json().get("limits");
        assertThat(limits.get("idleTimeoutSeconds").asInt()).isEqualTo(properties.idleTimeoutSeconds());
        assertThat(limits.get("maxGameDurationSeconds").asInt()).isEqualTo(properties.maxGameDurationSeconds());
        assertThat(limits.get("resultRetentionSeconds").asInt()).isEqualTo(properties.resultRetentionSeconds());
        assertThat(limits.get("invitationLifetimeSeconds").asInt()).isEqualTo(properties.invitationLifetimeSeconds());
        assertThat(limits.get("presenceIntervalSeconds").asInt()).isEqualTo(properties.presenceIntervalSeconds());
        assertThat(limits.get("heartbeatSeconds").asInt()).isEqualTo(properties.heartbeatSeconds());
        assertThat(limits.size()).isEqualTo(6);
    }

    @Test
    void rulesetsMatchTheSpecifiedProductData() throws Exception {
        Reply reply = call("GET", "/api/v1/rulesets");

        assertThat(reply.status()).isEqualTo(200);
        JsonNode rulesets = reply.json().get("rulesets");
        assertThat(rulesets.size()).isEqualTo(2);
        assertRuleset(
                rulesets.get(0),
                "sea-battle-10-ship.v1",
                new String[] {"ship-4:4:1", "ship-3:3:2", "ship-2:2:3", "ship-1:1:4"},
                false,
                true,
                true);
        assertRuleset(
                rulesets.get(1),
                "hasbro-classic-2002.v1",
                new String[] {"carrier:5:1", "battleship:4:1", "destroyer:3:1", "submarine:3:1", "patrol-boat:2:1"},
                true,
                false,
                false);
    }

    private static void assertRuleset(
            JsonNode ruleset, String id, String[] fleet, boolean touch, boolean extraTurn, boolean reveal) {
        assertThat(ruleset.get("id").asText()).isEqualTo(id);
        assertThat(ruleset.at("/board/rows").asInt()).isEqualTo(10);
        assertThat(ruleset.at("/board/columns").asInt()).isEqualTo(10);
        assertThat(ruleset.get("fleet").size()).isEqualTo(fleet.length);
        for (int i = 0; i < fleet.length; i++) {
            JsonNode entry = ruleset.get("fleet").get(i);
            assertThat(entry.get("shipTypeId").asText() + ":"
                            + entry.get("length").asInt() + ":"
                            + entry.get("count").asInt())
                    .isEqualTo(fleet[i]);
        }
        assertThat(ruleset.get("shipsMayTouch").asBoolean()).isEqualTo(touch);
        assertThat(ruleset.get("extraTurnOnHit").asBoolean()).isEqualTo(extraTurn);
        assertThat(ruleset.get("revealWaterAroundSunk").asBoolean()).isEqualTo(reveal);
    }

    @Test
    void healthIsLiveAndReadyWithoutAReasonOnceStarted() throws Exception {
        Reply reply = call("GET", "/api/v1/health");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.header("Content-Type")).startsWith("application/json");
        assertThat(reply.json().toString()).isEqualTo("{\"live\":true,\"ready\":true}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/meta", "/api/v1/rulesets", "/api/v1/health"})
    void everyOperationNeedsNoSessionAndIsNotStorable(String path) throws Exception {
        Reply reply = call("GET", path);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/meta", "/api/v1/rulesets"})
    void anUnsupportedMethodAnswersWithTheAdvicesProblemDocument(String path) throws Exception {
        Reply reply = postWithFreshToken(path);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
        assertThat(reply.json().get("code").asText()).isEqualTo("malformed-request");
        assertThat(reply.json().get("correlationId").asText()).matches(HEX16);
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void healthIsNotReadyWithStartingAndA503HealthBodyWhileTrafficIsRefused() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            Reply reply = call("GET", "/api/v1/health");

            assertThat(reply.status()).isEqualTo(503);
            assertThat(reply.header("Content-Type")).startsWith("application/json");
            assertThat(reply.json().toString()).isEqualTo("{\"live\":true,\"ready\":false,\"reason\":\"STARTING\"}");
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    /**
     * The environment-variable pathway itself is proven by ConfigurationValidationIT; this shows the published value
     * follows the property with no code involved. Property name is the relaxed-binding equivalent of
     * BATTLESHIP_IDLETIMEOUTSECONDS.
     */
    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "battleship.idle-timeout-seconds=60")
    @Import(FixedTime.class)
    class WithIdleTimeoutOverride {
        @LocalServerPort
        private int overridePort;

        @Test
        void metaPublishesTheOverriddenIdleTimeout() throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + overridePort + "/api/v1/meta"))
                    .GET()
                    .build();
            var body = mapper.readTree(
                    client.send(request, HttpResponse.BodyHandlers.ofString()).body());

            assertThat(body.at("/limits/idleTimeoutSeconds").asInt()).isEqualTo(60);
        }
    }
}
