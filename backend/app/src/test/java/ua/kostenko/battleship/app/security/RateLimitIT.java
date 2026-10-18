package ua.kostenko.battleship.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.app.web.SseStream;
import ua.kostenko.battleship.application.MutableTimeSource;

/**
 * Rate limits (R38, R41, R52, R61) over a real server. Time is a {@link MutableTimeSource}, so the 60-second window is
 * exact and nothing sleeps (R60). Every class gets its own small limit so a class reading another's limit fails.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "battleship.rate-limit.create-game-per-minute=2",
            "battleship.rate-limit.join-per-minute=3",
            "battleship.rate-limit.commands-per-minute=4",
            "battleship.rate-limit.read-game-per-minute=40",
            "battleship.rate-limit.presence-per-minute=6",
            "battleship.rate-limit.stream-open-per-minute=7",
            "battleship.rate-limit.replace-invitation-per-minute=8",
            "battleship.rate-limit.leave-per-minute=9",
            "server.shutdown=immediate"
        })
@Import(RateLimitIT.Determinism.class)
class RateLimitIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final int CAP = 20;
    private static final String GAMES = "/api/v1/games/";

    @TestConfiguration(proxyBeanMethods = false)
    static class Determinism {
        @Bean
        @Primary
        MutableTimeSource mutableTime() {
            return new MutableTimeSource(START);
        }

        @Bean
        @Primary
        FixedWindowBuckets smallBuckets(MutableTimeSource time) {
            return new FixedWindowBuckets(time, CAP);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableTimeSource time;

    @Autowired
    private FixedWindowBuckets buckets;

    @BeforeEach
    void freshWindows() {
        time.advance(WINDOW.plusSeconds(1));
    }

    /** A browser holding a live session; the create it spent is forgotten by moving past the window. */
    private Browser player() throws Exception {
        Browser browser = new Browser(port);
        browser.create("sea-battle-10-ship.v1", "Captain");
        time.advance(WINDOW.plusSeconds(1));
        return browser;
    }

    private static String cookie(Browser browser) {
        return SESSION_COOKIE + "=" + browser.sessionValue();
    }

    private Reply post(Browser browser, String suffix) throws Exception {
        SecurityHttp http = browser.http();
        return http.postWithToken(GAMES + browser.gameId() + suffix, http.freshToken(), cookie(browser));
    }

    /** Admits exactly {@code limit} calls, then refuses the next with a well-formed 429. */
    private void assertRefusesAfter(String operation, int limit, Supplier<Reply> call) {
        for (int i = 1; i <= limit; i++) {
            assertThat(call.get().status())
                    .as(operation + " call " + i + " of " + limit)
                    .isNotEqualTo(429);
        }
        Reply refused = call.get();
        assertThat(refused.status()).as(operation + " call " + (limit + 1)).isEqualTo(429);
        assertThat(refused.json().get("code").asText()).isEqualTo("rate-limit-exceeded");
    }

    private static Supplier<Reply> unchecked(ThrowingSupplier call) {
        return () -> {
            try {
                return call.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Reply get() throws Exception;
    }

    // (1) the enumerated manifest: one test per class

    @Test
    void createGameRefusesAtItsLimit() {
        SecurityHttp http = new SecurityHttp(port);
        assertRefusesAfter(
                "createGame",
                2,
                unchecked(() -> http.postJson(
                        "/api/v1/games", null, "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"A\"}")));
    }

    @Test
    void joinGameRefusesAtItsLimit() {
        SecurityHttp http = new SecurityHttp(port);
        assertRefusesAfter(
                "joinGame",
                3,
                unchecked(() -> http.postJson(
                        GAMES + "g1/join", null, "{\"invitationSecret\":\"nope\",\"displayName\":\"B\"}")));
    }

    @Test
    void sendCommandRefusesAtItsLimit() throws Exception {
        Browser browser = player();
        assertRefusesAfter("sendCommand", 4, unchecked(() -> browser.send(Browser.simple("PLACE_FLEET_RANDOMLY"))));
    }

    @Test
    void getGameRefusesAtItsLimit() throws Exception {
        Browser browser = player();
        assertRefusesAfter("getGame", 40, unchecked(browser::read));
    }

    @Test
    void aHeadRequestIsCountedAgainstTheLimitOfTheGetItMirrors() throws Exception {
        Browser browser = player();
        for (int i = 1; i <= 40; i++) {
            assertThat(browser.http()
                            .call("HEAD", GAMES + browser.gameId(), cookie(browser))
                            .status())
                    .as("HEAD call " + i)
                    .isNotEqualTo(429);
        }
        assertThat(browser.http()
                        .call("HEAD", GAMES + browser.gameId(), cookie(browser))
                        .status())
                .as("HEAD call 41")
                .isEqualTo(429);
        assertThat(browser.read().status())
                .as("the GET shares the exhausted bucket")
                .isEqualTo(429);
    }

    @Test
    void sendPresenceRefusesAtItsLimit() throws Exception {
        Browser browser = player();
        assertRefusesAfter("sendPresence", 6, unchecked(() -> post(browser, "/presence")));
    }

    @Test
    void streamGameEventsOpenRefusesAtItsLimit() throws Exception {
        Browser browser = player();
        for (int i = 1; i <= 7; i++) {
            try (SseStream stream = browser.events()) {
                assertThat(stream.status())
                        .as("streamGameEvents call " + i + " of 7")
                        .isEqualTo(200);
                // Read the first snapshot before closing: a client that hangs up the instant the headers arrive
                // races the container thread still unwinding the request, and the JDK client then retries the GET,
                // counting a second open the test never made.
                stream.next();
            }
        }
        try (SseStream refused = browser.events()) {
            assertThat(refused.status()).as("streamGameEvents call 8").isEqualTo(429);
        }
    }

    @Test
    void replaceInvitationRefusesAtItsLimit() throws Exception {
        Browser browser = player();
        assertRefusesAfter("replaceInvitation", 8, unchecked(() -> post(browser, "/invitation")));
    }

    @Test
    void leaveGameRefusesAtItsLimit() throws Exception {
        Browser browser = player();
        assertRefusesAfter("leaveGame", 9, unchecked(() -> post(browser, "/leave")));
    }

    // (2) Retry-After equals retryAfterSeconds, delta-seconds, at least 1

    @Test
    void retryAfterHeaderEqualsTheBodyValueInDeltaSeconds() throws Exception {
        Browser browser = player();
        for (int i = 0; i < 40; i++) {
            browser.read();
        }
        time.advance(Duration.ofMillis(20_500));

        Reply refused = browser.read();

        assertThat(refused.status()).isEqualTo(429);
        assertThat(refused.header("Retry-After")).matches("[0-9]+");
        assertThat(refused.header("Content-Type")).startsWith("application/problem+json");
        assertThat(refused.json().get("retryAfterSeconds").asInt()).isEqualTo(40);
        assertThat(Integer.parseInt(refused.header("Retry-After"))).isEqualTo(40);

        time.advance(Duration.ofMillis(39_400));
        Reply last = browser.read();
        assertThat(last.status()).isEqualTo(429);
        assertThat(last.json().get("retryAfterSeconds").asInt()).isEqualTo(1);
        assertThat(last.header("Retry-After")).isEqualTo("1");
    }

    // (3) the boundary is inclusive: at the window end the counter resets

    @Test
    void theCounterResetsExactlyAtTheWindowEnd() throws Exception {
        Browser browser = player();
        for (int i = 0; i < 40; i++) {
            assertThat(browser.read().status()).isEqualTo(200);
        }

        time.advance(WINDOW.minusMillis(1));
        assertThat(browser.read().status()).as("one millisecond before the end").isEqualTo(429);

        time.advance(Duration.ofMillis(1));
        assertThat(browser.read().status()).as("at the window end").isEqualTo(200);
    }

    // (4) address-keyed versus session-keyed

    @Test
    void createAndJoinShareOneBucketPerAddressWhileSessionClassesThrottleIndependently() throws Exception {
        Browser first = player();
        Browser second = player();

        for (int i = 1; i <= 4; i++) {
            assertThat(first.send(Browser.simple("PLACE_FLEET_RANDOMLY")).status())
                    .as("first session command " + i)
                    .isNotEqualTo(429);
        }
        assertThat(first.send(Browser.simple("PLACE_FLEET_RANDOMLY")).status()).isEqualTo(429);
        assertThat(second.send(Browser.simple("PLACE_FLEET_RANDOMLY")).status())
                .as("a second session from the same address is unaffected")
                .isNotEqualTo(429);

        String join = "{\"invitationSecret\":\"nope\",\"displayName\":\"B\"}";
        SecurityHttp anonymous = new SecurityHttp(port);
        assertThat(anonymous.postJson(GAMES + "g1/join", null, join).status()).isNotEqualTo(429);
        assertThat(first.http().postJson(GAMES + "g1/join", cookie(first), join).status())
                .isNotEqualTo(429);
        assertThat(second.http()
                        .postJson(GAMES + "g1/join", cookie(second), join)
                        .status())
                .isNotEqualTo(429);
        assertThat(anonymous.postJson(GAMES + "g1/join", cookie(first), join).status())
                .as("the fourth join from the address is refused whatever session it carries")
                .isEqualTo(429);

        time.advance(WINDOW.plusSeconds(1));
        assertThat(new Browser(port)
                        .http()
                        .postJson(
                                "/api/v1/games",
                                null,
                                "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"C\"}")
                        .status())
                .isEqualTo(201);
        Browser other = new Browser(port);
        other.create("sea-battle-10-ship.v1", "D");
        assertThat(new SecurityHttp(port)
                        .postJson(
                                "/api/v1/games",
                                null,
                                "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"E\"}")
                        .status())
                .as("the create limit is shared by every session at the address")
                .isEqualTo(429);
    }

    @Test
    void unlimitedOperationsAreNeverThrottled() throws Exception {
        SecurityHttp http = new SecurityHttp(port);
        for (int i = 0; i < 50; i++) {
            assertThat(http.call("GET", "/api/v1/meta", null).status()).isEqualTo(200);
            assertThat(http.call("GET", "/api/v1/rulesets", null).status()).isEqualTo(200);
            assertThat(http.call("GET", "/api/v1/health", null).status()).isNotEqualTo(429);
        }
    }

    // (5) R41: a flood of invented keys drops the least recently used; a real player's key still works

    @Test
    void aFloodOfInventedKeysNeverEvictsAPlayerWhoIsStillActive() throws Exception {
        Browser player = player();
        int admitted = 0;
        assertThat(player.read().status()).isEqualTo(200);
        admitted++;

        for (int i = 0; i < 10 * CAP; i++) {
            assertThat(buckets.acquire("getGame", "invented-" + i, 40))
                    .as("a key never seen before is admitted, not refused")
                    .isZero();
            if (i % (CAP / 2) == 0) {
                assertThat(player.read().status()).isEqualTo(200);
                admitted++;
            }
        }

        assertThat(buckets.size()).isLessThanOrEqualTo(CAP);
        while (player.read().status() == 200) {
            admitted++;
        }
        assertThat(admitted)
                .as("the player's counter survived the flood, so exactly the configured 40 were admitted")
                .isEqualTo(40);
    }

    @Test
    void staleBucketsArePrunedAtTheirWindowEnd() {
        FixedWindowBuckets own = new FixedWindowBuckets(time, 5);
        own.acquire("getGame", "stale", 40);

        own.prune(time.now().plus(WINDOW).minusMillis(1));
        assertThat(own.size()).as("still inside its window").isEqualTo(1);

        own.prune(time.now().plus(WINDOW));
        assertThat(own.size()).as("at the window end").isZero();
    }

    private static JsonNode withoutServerTime(JsonNode snapshot) {
        return ((ObjectNode) snapshot.deepCopy()).without("serverTime");
    }

    // (6) a refused request changes nothing

    @Test
    void aRefusedRequestLeavesTheGameUntouched() throws Exception {
        Browser browser = player();
        JsonNode last = null;
        for (int i = 0; i < 8; i++) {
            Reply replaced = post(browser, "/invitation");
            assertThat(replaced.status()).isEqualTo(200);
            last = replaced.json();
        }

        Reply refused = post(browser, "/invitation");

        assertThat(refused.status()).isEqualTo(429);
        time.advance(WINDOW.plusSeconds(1));
        JsonNode after = browser.snapshot();
        assertThat(after.get("version")).isEqualTo(last.get("version"));
        assertThat(after.get("invitationUrl"))
                .as("a refused replacement must not issue a new invitation")
                .isEqualTo(last.get("invitationUrl"));
        assertThat(withoutServerTime(after)).isEqualTo(withoutServerTime(last));
    }
}
