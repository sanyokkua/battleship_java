package ua.kostenko.battleship.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.issuedSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
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
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.application.MutableTimeSource;

/** Joining a game and replacing its invitation over HTTP, with two browsers that share nothing (R34, R40, R45, R63). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "battleship.invitation-lifetime-seconds=300")
@Import(JoinIT.Clock.class)
class JoinIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String GAMES = "/api/v1/games/";
    private static final String WRONG_SECRET = "A".repeat(43);
    private static final String UNKNOWN_GAME = "AAAAAAAAAAAAAAAAAAAAAA";

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

    private SecurityHttp browser() {
        return new SecurityHttp(port);
    }

    private record Hosted(SecurityHttp http, String gameId, String cookie, String secret) {}

    private Hosted createGame() throws Exception {
        SecurityHttp http = browser();
        Reply created = http.postJson(
                "/api/v1/games", null, "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Captain\"}");
        assertThat(created.status()).isEqualTo(201);
        return new Hosted(
                http, created.json().get("gameId").asText(), issuedSession(created), secretOf(created.json()));
    }

    private static String secretOf(JsonNode snapshot) {
        String url = snapshot.get("invitationUrl").asText();
        return url.substring(url.indexOf("#invite=") + "#invite=".length());
    }

    private static String joinBody(String secret) {
        return "{\"invitationSecret\":\"" + secret + "\",\"displayName\":\"Rival\"}";
    }

    private Reply join(SecurityHttp http, String cookie, String gameId, String secret) throws Exception {
        return http.postJson(GAMES + gameId + "/join", cookie, joinBody(secret));
    }

    private JsonNode read(SecurityHttp http, String cookie, String gameId) throws Exception {
        Reply reply = http.call("GET", GAMES + gameId, cookie);
        assertThat(reply.status()).isEqualTo(200);
        return reply.json();
    }

    private static JsonNode withoutCorrelation(Reply reply) {
        ObjectNode problem = reply.json().deepCopy();
        problem.remove("correlationId");
        return problem;
    }

    private void assertInvitationUnavailable(Reply reply) {
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json().get("code").asText()).isEqualTo("invitation-unavailable");
    }

    @Test
    void aSecondBrowserRedeemsAFreshLinkAndBothPlayersSeeEachOthersNames() throws Exception {
        Hosted game = createGame();
        SecurityHttp guestBrowser = browser();

        Reply joined = join(guestBrowser, null, game.gameId(), game.secret());

        assertThat(joined.status()).isEqualTo(200);
        assertThat(issuedSession(joined)).startsWith(SecurityHttp.SESSION_COOKIE + "=");
        assertThat(joined.json().get("phase").asText()).isEqualTo("PLACEMENT");
        assertThat(joined.json().get("you").get("displayName").asText()).isEqualTo("Rival");
        assertThat(joined.json().get("opponent").get("displayName").asText()).isEqualTo("Captain");
        JsonNode hostView = read(game.http(), game.cookie(), game.gameId());
        assertThat(hostView.get("phase").asText()).isEqualTo("PLACEMENT");
        assertThat(hostView.get("opponent").get("displayName").asText()).isEqualTo("Rival");
        assertThat(hostView.has("invitationUrl")).isFalse();
    }

    @Test
    void theSameBrowserJoiningAgainGetsTheCurrentSnapshotAndNothingChanges() throws Exception {
        Hosted game = createGame();
        SecurityHttp guestBrowser = browser();
        Reply first = join(guestBrowser, null, game.gameId(), game.secret());
        String guestCookie = issuedSession(first);
        JsonNode hostBefore = read(game.http(), game.cookie(), game.gameId());

        Reply again = join(guestBrowser, guestCookie, game.gameId(), game.secret());

        assertThat(again.status()).isEqualTo(200);
        assertThat(again.setCookies(SecurityHttp.SESSION_COOKIE)).isEmpty();
        assertThat(again.json().get("version")).isEqualTo(first.json().get("version"));
        assertThat(again.json().get("phase").asText()).isEqualTo("PLACEMENT");
        assertThat(read(game.http(), game.cookie(), game.gameId())).isEqualTo(hostBefore);
    }

    @Test
    void aLinkOpenedButNotConfirmedClaimsNoSeatAndStaysUsableUntilItExpires() throws Exception {
        Hosted game = createGame();
        time.advance(Duration.ofSeconds(299));

        JsonNode waiting = read(game.http(), game.cookie(), game.gameId());
        Reply joined = join(browser(), null, game.gameId(), game.secret());

        assertThat(waiting.get("phase").asText()).isEqualTo("WAITING");
        assertThat(waiting.has("opponent")).isFalse();
        assertThat(joined.status()).isEqualTo(200);
    }

    @Test
    void everyRefusalOfAnUnredeemableSecretIsTheSameProblemExceptTheCorrelationId() throws Exception {
        Hosted game = createGame();
        Reply guestJoin = join(browser(), null, game.gameId(), game.secret());
        assertThat(guestJoin.status()).isEqualTo(200);
        Reply used = join(browser(), null, game.gameId(), game.secret());
        Reply wrong = join(browser(), null, game.gameId(), WRONG_SECRET);

        Hosted replaced = createGame();
        String oldSecret = replaced.secret();
        assertThat(replaced.http()
                        .postBody(GAMES + replaced.gameId() + "/invitation", replaced.cookie(), "text/plain", "")
                        .status())
                .isEqualTo(200);
        Reply replacedSecret = join(browser(), null, replaced.gameId(), oldSecret);

        Hosted aging = createGame();
        time.advance(Duration.ofSeconds(300));
        Reply expired = join(browser(), null, aging.gameId(), aging.secret());

        assertInvitationUnavailable(used);
        JsonNode expectedProblem = withoutCorrelation(used);
        assertThat(withoutCorrelation(wrong)).isEqualTo(expectedProblem);
        assertThat(withoutCorrelation(replacedSecret)).isEqualTo(expectedProblem);
        assertThat(withoutCorrelation(expired)).isEqualTo(expectedProblem);
        assertThat(used.json().get("correlationId")).isNotEqualTo(wrong.json().get("correlationId"));
    }

    @Test
    void theHostReplacesTheInvitationAndThePreviousLinkStopsWorkingAtOnce() throws Exception {
        Hosted game = createGame();
        Reply replaced = game.http().postBody(GAMES + game.gameId() + "/invitation", game.cookie(), "text/plain", "");

        assertThat(replaced.status()).isEqualTo(200);
        String newSecret = secretOf(replaced.json());
        assertThat(newSecret).isNotEqualTo(game.secret());
        assertThat(secretOf(read(game.http(), game.cookie(), game.gameId()))).isEqualTo(newSecret);
        assertInvitationUnavailable(join(browser(), null, game.gameId(), game.secret()));
        assertThat(join(browser(), null, game.gameId(), newSecret).status()).isEqualTo(200);
    }

    @Test
    void theGuestReloadingAfterAReplacementStillReadsItsOwnGame() throws Exception {
        Hosted game = createGame();
        game.http().postBody(GAMES + game.gameId() + "/invitation", game.cookie(), "text/plain", "");
        String newSecret = secretOf(read(game.http(), game.cookie(), game.gameId()));
        SecurityHttp guestBrowser = browser();
        String guestCookie = issuedSession(join(guestBrowser, null, game.gameId(), newSecret));

        Reply staleReload = join(guestBrowser, guestCookie, game.gameId(), game.secret());
        Reply wrongReload = join(guestBrowser, guestCookie, game.gameId(), WRONG_SECRET);

        assertThat(staleReload.status()).isEqualTo(200);
        assertThat(staleReload.json().get("phase").asText()).isEqualTo("PLACEMENT");
        assertThat(staleReload.json().get("you").get("displayName").asText()).isEqualTo("Rival");
        assertThat(wrongReload.status()).isEqualTo(200);
        assertThat(read(guestBrowser, guestCookie, game.gameId()).get("phase").asText())
                .isEqualTo("PLACEMENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"anything\":true}", "not json at all"})
    void replaceInvitationAcceptsAnyBodyWithAnyContentTypeAndNeverAnswers415(String body) throws Exception {
        for (String contentType :
                new String[] {"application/json", "text/plain", "application/xml", "application/x-www-form-urlencoded"
                }) {
            Hosted game = createGame();

            Reply reply = game.http().postBody(GAMES + game.gameId() + "/invitation", game.cookie(), contentType, body);

            assertThat(reply.status())
                    .as("%s with body '%s'", contentType, body)
                    .isEqualTo(200);
            assertThat(reply.json().get("invitationUrl").asText()).doesNotContain(game.secret());
        }
    }

    @Test
    void replaceInvitationAcceptsARequestWithNoBodyAtAll() throws Exception {
        Hosted game = createGame();
        String token = game.http().freshToken();

        Reply reply = game.http()
                .call(
                        "POST",
                        GAMES + game.gameId() + "/invitation",
                        SecurityHttp.XSRF_COOKIE + "=" + token + "; " + game.cookie(),
                        "X-XSRF-TOKEN",
                        token);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(secretOf(reply.json())).isNotEqualTo(game.secret());
    }

    @Test
    void atTheInclusiveExpiryInstantOnlyTheRetainedGuestLearnsTheGameExpired() throws Exception {
        Hosted game = createGame();
        SecurityHttp guestBrowser = browser();
        String guestCookie = issuedSession(join(guestBrowser, null, game.gameId(), game.secret()));
        time.advance(Duration.ofSeconds(900));

        Reply guestStale = join(guestBrowser, guestCookie, game.gameId(), game.secret());
        Reply guestWrong = join(guestBrowser, guestCookie, game.gameId(), WRONG_SECRET);
        Reply hostJoin = join(game.http(), game.cookie(), game.gameId(), game.secret());
        Reply stranger = join(browser(), null, game.gameId(), game.secret());
        Reply unknown = join(browser(), null, UNKNOWN_GAME, game.secret());

        assertThat(guestStale.status()).isEqualTo(410);
        assertThat(guestStale.json().get("code").asText()).isEqualTo("game-expired");
        assertThat(guestWrong.status()).isEqualTo(410);
        assertThat(guestWrong.json().get("code").asText()).isEqualTo("game-expired");
        assertInvitationUnavailable(unknown);
        JsonNode unknownProblem = withoutCorrelation(unknown);
        assertThat(withoutCorrelation(hostJoin)).isEqualTo(unknownProblem);
        assertThat(withoutCorrelation(stranger)).isEqualTo(unknownProblem);

        time.advance(Duration.ofSeconds(300));

        assertInvitationUnavailable(join(guestBrowser, guestCookie, game.gameId(), game.secret()));
    }
}
