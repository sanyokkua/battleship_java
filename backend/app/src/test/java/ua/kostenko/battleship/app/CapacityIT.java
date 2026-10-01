package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.app.web.SseStream;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.usecase.ExpireGamesUseCase;

/**
 * Owner of S11: every ceiling (games, streams, live games per browser) refuses with 503 and a matching
 * {@code Retry-After}, never evicts a running game or an open stream, keeps the service ready, and gives each
 * allocation back exactly once (R04, R39, R62). A fresh context per test keeps each ceiling's count exact; time is a
 * {@link MutableTimeSource}, so nothing sleeps (R60).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "battleship.max-concurrent-games=4",
            "battleship.max-concurrent-streams=2",
            "battleship.max-live-games-per-browser=1",
            "battleship.rate-limit.create-game-per-minute=100000",
            "battleship.rate-limit.join-per-minute=100000",
            "battleship.rate-limit.commands-per-minute=100000",
            "battleship.rate-limit.read-game-per-minute=100000",
            "battleship.rate-limit.stream-open-per-minute=100000",
            "battleship.rate-limit.leave-per-minute=100000",
            "server.shutdown=immediate"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(CapacityIT.Determinism.class)
class CapacityIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";
    private static final int GAMES_CEILING = 4;
    private static final int CYCLES = 500;

    @TestConfiguration(proxyBeanMethods = false)
    static class Determinism {
        @Bean
        @Primary
        MutableTimeSource mutableTime() {
            return new MutableTimeSource(START);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableTimeSource time;

    @Autowired
    private ExpireGamesUseCase expire;

    private Browser host(String name) throws Exception {
        Browser browser = new Browser(port);
        browser.create(SEA_BATTLE, name);
        return browser;
    }

    /** A createGame call that does not assert success; {@code browser} supplies the session it already holds. */
    private Reply tryCreate(Browser browser) throws Exception {
        return browser.http()
                .postJson(
                        "/api/v1/games",
                        SESSION_COOKIE + "=" + browser.sessionValue(),
                        "{\"rulesetId\":\"" + SEA_BATTLE + "\",\"displayName\":\"Again\"}");
    }

    private Reply tryCreateAnonymously() throws Exception {
        return new Browser(port)
                .http()
                .postJson("/api/v1/games", null, "{\"rulesetId\":\"" + SEA_BATTLE + "\",\"displayName\":\"Late\"}");
    }

    private Reply tryJoin(Browser joiner, Browser target) throws Exception {
        JsonNode invitation = target.snapshot();
        return joiner.http()
                .postJson(
                        "/api/v1/games/" + target.gameId() + "/join",
                        SESSION_COOKIE + "=" + joiner.sessionValue(),
                        "{\"invitationSecret\":\"" + Browser.invitationSecret(invitation)
                                + "\",\"displayName\":\"Joiner\"}");
    }

    private static void assertServiceUnavailable(Reply refused) {
        assertThat(refused.status())
                .as(String.valueOf(refused.response().body()))
                .isEqualTo(503);
        assertThat(refused.json().get("code").asText()).isEqualTo("service-unavailable");
        int retryAfter = refused.json().get("retryAfterSeconds").asInt();
        assertThat(retryAfter).isGreaterThanOrEqualTo(1);
        assertThat(refused.header("Retry-After")).isEqualTo(String.valueOf(retryAfter));
    }

    private void assertReady() throws Exception {
        Reply health = new Browser(port).http().call("GET", "/api/v1/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.json().get("ready").asBoolean()).isTrue();
    }

    private static JsonNode stable(JsonNode snapshot) {
        return ((ObjectNode) snapshot.deepCopy()).without("serverTime");
    }

    /** Both seats ready, then the host resigns: the game is FINISHED and no longer a live game for either seat. */
    private void finish(Browser host, Browser guest) throws Exception {
        guest.join(host.gameId(), Browser.invitationSecret(host.snapshot()), "Guest");
        for (Browser player : List.of(host, guest)) {
            player.accept(Browser.simple("PLACE_FLEET_RANDOMLY"));
            player.accept(Browser.simple("READY"));
        }
        assertThat(host.accept(Browser.simple("RESIGN")).get("phase").asText()).isEqualTo("FINISHED");
    }

    // (1), (2), (4) the games ceiling

    @Test
    void atTheGamesCeilingANewGameIsRefusedAndNoRunningGameIsEvicted() throws Exception {
        List<Browser> running = new ArrayList<>();
        List<JsonNode> before = new ArrayList<>();
        for (int i = 0; i < GAMES_CEILING; i++) {
            Browser browser = host("Host" + i);
            running.add(browser);
            before.add(stable(browser.snapshot()));
        }
        assertReady();

        assertServiceUnavailable(tryCreateAnonymously());
        assertServiceUnavailable(tryCreateAnonymously());

        for (int i = 0; i < GAMES_CEILING; i++) {
            Reply read = running.get(i).read();
            assertThat(read.status())
                    .as("running game " + i + " after the refusal")
                    .isEqualTo(200);
            assertThat(stable(read.json()))
                    .as("running game " + i + " unchanged")
                    .isEqualTo(before.get(i));
        }
        assertReady();
    }

    // (3), (4) the streams ceiling

    @Test
    void atTheStreamsCeilingANewStreamIsRefusedAndEveryOpenStreamKeepsDelivering() throws Exception {
        Browser host = host("Host");
        Browser guest = new Browser(port);
        guest.join(host.gameId(), Browser.invitationSecret(host.snapshot()), "Guest");
        Browser outsider = host("Outsider");
        try (SseStream hostStream = host.events();
                SseStream guestStream = guest.events()) {
            assertThat(hostStream.next().name()).isEqualTo("snapshot");
            assertThat(guestStream.next().name()).isEqualTo("snapshot");
            assertReady();

            try (SseStream refused = outsider.events()) {
                assertThat(refused.status()).isEqualTo(503);
                assertThat(refused.header("Retry-After")).isEqualTo("1");
            }

            // each player's READY reaches the opponent's stream, so both open streams are shown to still deliver
            host.accept(Browser.simple("PLACE_FLEET_RANDOMLY"));
            guest.accept(Browser.simple("PLACE_FLEET_RANDOMLY"));
            host.accept(Browser.simple("READY"));
            assertThat(guestStream.next().name()).isEqualTo("snapshot");
            guest.accept(Browser.simple("READY"));
            assertThat(hostStream.next().name()).isEqualTo("snapshot");
            assertReady();
        }
    }

    // (5) the per-browser cap

    @Test
    void aBrowserAtItsLiveGameCapIsRefusedOnCreateAndOnJoin() throws Exception {
        Browser holder = host("Holder");
        Browser other = host("Other");

        assertServiceUnavailable(tryCreate(holder));
        assertServiceUnavailable(tryJoin(holder, other));

        assertThat(holder.read().status()).isEqualTo(200);
        assertThat(other.read().status()).isEqualTo(200);
        assertReady();
    }

    // (6) a finished game and a left game free the allocation at once

    @Test
    void aFinishedGameNoLongerCountsAgainstTheBrowserCap() throws Exception {
        Browser host = host("Host");
        Browser guest = new Browser(port);
        assertServiceUnavailable(tryCreate(host));

        finish(host, guest);

        assertThat(tryCreate(host).status()).as("host, after FINISHED").isEqualTo(201);
        assertThat(tryCreate(guest).status()).as("guest, after FINISHED").isEqualTo(201);
    }

    @Test
    void leavingFreesTheAllocationAtOnce() throws Exception {
        Browser host = host("Host");
        assertServiceUnavailable(tryCreate(host));

        assertThat(host.leave().status()).isEqualTo(204);

        assertThat(tryCreate(host).status()).isEqualTo(201);
    }

    // (7) no permit leak, no over-release

    @Test
    void permitsSurviveFiveHundredCreateAndRemoveCycles() throws Exception {
        List<Browser> holders = new ArrayList<>();
        for (int i = 0; i < GAMES_CEILING - 1; i++) {
            holders.add(host("Holder" + i)); // only one permit stays free
        }
        Browser cycler = new Browser(port);
        for (int cycle = 1; cycle <= CYCLES; cycle++) {
            assertThat(cycler.create(SEA_BATTLE, "Cycler").get("gameId"))
                    .as("create " + cycle)
                    .isNotNull();
            assertThat(cycler.leave().status()).as("leave " + cycle).isEqualTo(204);
        }

        // exactly one permit is free: it is not lost, and it is not duplicated
        Browser last = host("Last");
        assertServiceUnavailable(tryCreateAnonymously());

        // time reclaims every game too, and gives every permit back
        time.advance(Duration.ofSeconds(900 + 300 + 1));
        expire.sweep(time.now());
        for (int i = 0; i < GAMES_CEILING; i++) {
            host("Fresh" + i);
        }
        assertServiceUnavailable(tryCreateAnonymously());
        assertThat(last.gameId()).isNotNull();
    }
}
