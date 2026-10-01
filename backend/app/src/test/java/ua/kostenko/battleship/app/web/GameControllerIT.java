package ua.kostenko.battleship.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.issuedSession;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.projection.PrivacyFixtures;
import ua.kostenko.battleship.application.projection.SnapshotContext;
import ua.kostenko.battleship.application.projection.SnapshotProjector;
import ua.kostenko.battleship.application.registry.GameRegistry;
import ua.kostenko.battleship.application.registry.SessionRegistry;
import ua.kostenko.battleship.domain.model.GameState;
import ua.kostenko.battleship.domain.model.Phase;
import ua.kostenko.battleship.domain.model.Seat;

/** Create and read over HTTP, and the one wire document assembled from the projector's view (R17, R18, R56). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "battleship.rate-limit.create-game-per-minute=100000",
            "battleship.rate-limit.join-per-minute=100000",
            "battleship.rate-limit.commands-per-minute=100000",
            "battleship.rate-limit.read-game-per-minute=100000"
        })
@Import(GameControllerIT.Clock.class)
class GameControllerIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";
    private static final String HASBRO = "hasbro-classic-2002.v1";
    private static final String GAME_PATH = "/api/v1/games/";

    @TestConfiguration(proxyBeanMethods = false)
    static class Clock {
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
    private BattleshipProperties properties;

    @Autowired
    private GameRegistry games;

    @Autowired
    private SessionRegistry sessions;

    private SecurityHttp http() {
        return new SecurityHttp(port);
    }

    private static String createBody(String rulesetId) {
        return "{\"rulesetId\":\"" + rulesetId + "\",\"displayName\":\"Captain\"}";
    }

    /** Makes the game a two-player PLAYING game and returns the guest's session cookie. */
    private String seatAGuest(String gameId) {
        String guestValue = "guest-session-" + gameId + "-0123456789abcdefghijklmnop";
        sessions.register(guestValue, time.now());
        games.withSlot(gameId, slot -> {
            slot.replace(PrivacyFixtures.fixture(Phase.PLAYING, SEA_BATTLE));
            slot.guestSessionDigest(SessionRegistry.digest(guestValue));
            return null;
        });
        return SecurityHttp.SESSION_COOKIE + "=" + guestValue;
    }

    @ParameterizedTest
    @ValueSource(strings = {SEA_BATTLE, HASBRO})
    void createGameAnswers201WithLocationSessionCookieAndTheWaitingSnapshot(String rulesetId) throws Exception {
        Reply reply = http().postJson("/api/v1/games", null, createBody(rulesetId));

        assertThat(reply.status()).isEqualTo(201);
        JsonNode snapshot = reply.json();
        String gameId = snapshot.get("gameId").asText();
        assertThat(reply.header("Location")).matches("/api/v1/games/[A-Za-z0-9_-]{22}");
        assertThat(reply.header("Location")).isEqualTo("/api/v1/games/" + gameId);
        assertThat(issuedSession(reply)).startsWith(SecurityHttp.SESSION_COOKIE + "=");
        assertThat(snapshot.get("phase").asText()).isEqualTo("WAITING");
        assertThat(snapshot.get("rulesetId").asText()).isEqualTo(rulesetId);
        assertThat(snapshot.get("version").asInt()).isZero();
        assertThat(snapshot.get("invitationUrl").asText())
                .matches(java.util.regex.Pattern.quote(properties.publicBaseUrl() + "/join/" + gameId + "#invite=")
                        + "[A-Za-z0-9_-]{43}");
        assertThat(snapshot.has("invitationExpiresAt")).isTrue();
        assertThat(snapshot.has("opponent")).isFalse();
    }

    @Test
    void theInvitationUrlIgnoresHostForwardedHostAndOriginHeaders() throws Exception {
        Reply spoofed = http().postJson(
                        "/api/v1/games",
                        null,
                        createBody(SEA_BATTLE),
                        "Host",
                        "evil.example",
                        "X-Forwarded-Host",
                        "evil.example",
                        "Origin",
                        properties.publicBaseUrl());

        assertThat(spoofed.status()).isEqualTo(201);
        assertThat(spoofed.json().get("invitationUrl").asText())
                .startsWith(properties.publicBaseUrl() + "/join/"
                        + spoofed.json().get("gameId").asText() + "#invite=")
                .doesNotContain("evil");
    }

    @Test
    void getGameReturnsTheCallerRelativeSnapshotForEachSeat() throws Exception {
        SecurityHttp http = http();
        Reply created = http.postJson("/api/v1/games", null, createBody(SEA_BATTLE));
        String gameId = created.json().get("gameId").asText();
        String hostCookie = issuedSession(created);
        String guestCookie = seatAGuest(gameId);

        JsonNode host = http.call("GET", GAME_PATH + gameId, hostCookie).json();
        JsonNode guest = http.call("GET", GAME_PATH + gameId, guestCookie).json();

        assertThat(host.get("turn").asText()).isEqualTo("OPPONENT");
        assertThat(guest.get("turn").asText()).isEqualTo("YOU");
        assertThat(host.get("you").get("displayName").asText()).isEqualTo("Host");
        assertThat(guest.get("you").get("displayName").asText()).isEqualTo("Guest");
        assertThat(host.get("opponent")).isEqualTo(guest.get("you"));
        assertThat(guest.get("opponent")).isEqualTo(host.get("you"));
        assertThat(host.get("lastShot").get("by").asText()).isEqualTo("OPPONENT");
        assertThat(guest.get("lastShot").get("by").asText()).isEqualTo("YOU");
        assertThat(host.get("yourBoard")).isNotEqualTo(guest.get("yourBoard"));
    }

    @Test
    void oneReadCarriesEverythingNeededToContinueAMidGame() throws Exception {
        SecurityHttp http = http();
        Reply created = http.postJson("/api/v1/games", null, createBody(SEA_BATTLE));
        String gameId = created.json().get("gameId").asText();
        String hostCookie = issuedSession(created);
        seatAGuest(gameId);

        JsonNode snapshot = http.call("GET", GAME_PATH + gameId, hostCookie).json();

        assertThat(snapshot.get("phase").asText()).isEqualTo("PLAYING");
        assertThat(snapshot.get("turn").asText()).isEqualTo("OPPONENT");
        assertThat(snapshot.get("allowedActions")).isNotEmpty();
        assertThat(snapshot.get("yourBoard").get("grid")).hasSize(10);
        assertThat(snapshot.get("yourBoard").get("ships")).hasSize(10);
        assertThat(snapshot.get("opponentBoard").get("grid")).hasSize(10);
        assertThat(snapshot.get("lastShot").get("result").asText()).isEqualTo("SUNK");
        assertThat(snapshot.get("you").get("connected").isBoolean()).isTrue();
        assertThat(snapshot.get("opponent").get("connected").isBoolean()).isTrue();
        assertThat(snapshot.get("you").get("shipsRemaining").asInt()).isEqualTo(9);
        assertThat(snapshot.get("opponent").get("shipsRemaining").asInt()).isEqualTo(10);
        assertThat(snapshot.has("expiresAt")).isTrue();
    }

    @Test
    void twoConsecutiveReadsShareTheVersionAndTheDeadlineButNotTheClock() throws Exception {
        SecurityHttp http = http();
        Reply created = http.postJson("/api/v1/games", null, createBody(SEA_BATTLE));
        String gameId = created.json().get("gameId").asText();
        String hostCookie = issuedSession(created);

        JsonNode first = http.call("GET", GAME_PATH + gameId, hostCookie).json();
        time.advance(java.time.Duration.ofSeconds(7));
        JsonNode second = http.call("GET", GAME_PATH + gameId, hostCookie).json();

        assertThat(second.get("version")).isEqualTo(first.get("version"));
        assertThat(second.get("expiresAt")).isEqualTo(first.get("expiresAt"));
        assertThat(OffsetDateTime.parse(second.get("serverTime").asText()))
                .isAfter(OffsetDateTime.parse(first.get("serverTime").asText()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"g1", "x", "AAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAA", "!!!!!!!!!!!!!!!!!!!!!!", "%20"})
    void anUnknownGameIdShapeAnswers404NotA500(String gameId) throws Exception {
        SecurityHttp http = http();
        String cookie = issuedSession(http.postJson("/api/v1/games", null, createBody(SEA_BATTLE)));

        Reply reply = http.call("GET", GAME_PATH + gameId, cookie);

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.json().get("code").asText()).isEqualTo("game-unavailable");
    }

    @Test
    void aWellFormedUnknownGameIdAnswers404() throws Exception {
        SecurityHttp http = http();
        String cookie = issuedSession(http.postJson("/api/v1/games", null, createBody(SEA_BATTLE)));

        assertThat(http.call("GET", GAME_PATH + "AAAAAAAAAAAAAAAAAAAAAA", cookie)
                        .status())
                .isEqualTo(404);
    }

    @Test
    void theAssembledDocumentIsByteIdenticalForTheTwoPrivacyFixtures() throws Exception {
        SnapshotProjector projector = new SnapshotProjector();
        SnapshotContext context = new SnapshotContext(
                "game-1", PrivacyFixtures.NOW, PrivacyFixtures.NOW.plusSeconds(60), null, null, true, false);
        var mapper = JacksonConfig.wireMapper();
        for (Seat caller : Seat.values()) {
            for (Phase phase : List.of(Phase.PLACEMENT, Phase.PLAYING, Phase.ABANDONED)) {
                for (boolean hit : new boolean[] {false, true}) {
                    if (hit && phase == Phase.PLACEMENT) continue;
                    GameState first = PrivacyFixtures.pair(phase, caller, false, hit);
                    GameState second = PrivacyFixtures.pair(phase, caller, true, hit);
                    byte[] a = mapper.writeValueAsBytes(
                            SnapshotDtoAssembler.assemble(projector.project(first, caller, context)));
                    byte[] b = mapper.writeValueAsBytes(
                            SnapshotDtoAssembler.assemble(projector.project(second, caller, context)));
                    assertThat(a).as("%s %s hit=%s", caller, phase, hit).isEqualTo(b);
                }
            }
        }
    }
}
