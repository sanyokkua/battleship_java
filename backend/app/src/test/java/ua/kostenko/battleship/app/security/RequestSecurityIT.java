package ua.kostenko.battleship.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.XSRF_COOKIE;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/** Part 1 of 2: the anti-forgery check (R35) and the absence of CORS (R36). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestSecurityIT {
    private static final String HEX16 = "[0-9a-f]{16}";
    private static final String[] POST_PATHS = {
        "/api/v1/games",
        "/api/v1/games/g1/join",
        "/api/v1/games/g1/commands",
        "/api/v1/games/g1/invitation",
        "/api/v1/games/g1/presence",
        "/api/v1/games/g1/leave"
    };

    @LocalServerPort
    private int port;

    private SecurityHttp http;

    @BeforeEach
    void connect() {
        http = new SecurityHttp(port);
    }

    @Test
    void metaAlwaysEmitsTheReadableCookieWithItsAttributes() throws Exception {
        for (int i = 0; i < 2; i++) {
            Reply meta = http.call("GET", "/api/v1/meta", null);

            List<String> cookies = meta.setCookies(XSRF_COOKIE);
            assertThat(cookies).hasSize(1);
            assertThat(cookies.getFirst()).contains("Path=/", "Secure", "SameSite=Strict");
            assertThat(cookies.getFirst()).doesNotContain("HttpOnly");
        }
    }

    @Test
    void echoingTheRawCookieValueIsAcceptedAndCreatesAGame() throws Exception {
        Reply reply = http.postJson(
                "/api/v1/games", null, "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Captain\"}");

        assertThat(reply.status()).isEqualTo(201);
    }

    @Test
    void echoingTheRawCookieValueIsNotRefusedOnAProtectedPathEither() throws Exception {
        Reply reply = http.postWithToken("/api/v1/games/g1/leave", http.freshToken(), null);

        assertThat(reply.status()).isNotEqualTo(403);
    }

    private void assertRejected(Reply reply) {
        assertThat(reply.status()).isEqualTo(403);
        assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
        assertThat(reply.json().get("code").asText()).isEqualTo("request-security-rejected");
        assertThat(reply.json().get("status").asInt()).isEqualTo(403);
        assertThat(reply.json().get("title").asText()).isEqualTo("Request rejected");
        assertThat(reply.json().get("correlationId").asText()).matches(HEX16);
        assertThat(reply.json().has("violations")).isFalse();
    }

    @Test
    void aMissingEmptyOrMismatchedTokenIsRejectedOnEveryPostPath() throws Exception {
        for (String path : POST_PATHS) {
            String cookie = XSRF_COOKIE + "=" + http.freshToken();
            String other = http.freshToken();

            assertRejected(http.call("POST", path, cookie));
            assertRejected(http.call("POST", path, cookie, "X-XSRF-TOKEN", ""));
            assertRejected(http.call("POST", path, cookie, "X-XSRF-TOKEN", other));
            assertRejected(http.call("POST", path, null, "X-XSRF-TOKEN", other));
        }
    }

    @Test
    void everyPostPathAcceptsAMatchingTokenSoTheCheckIsNotABlanketRefusal() throws Exception {
        for (String path : POST_PATHS) {
            assertThat(http.postWithToken(path, http.freshToken(), null).status())
                    .as(path)
                    .isNotEqualTo(403);
        }
    }

    @Test
    void noResponseEverCarriesACrossOriginAllowance() throws Exception {
        String token = http.freshToken();
        http.call("GET", "/api/v1/meta", null, "Origin", "https://evil.example");
        http.call("GET", "/api/v1/games/g1", null, "Origin", "https://evil.example");
        http.call("POST", "/api/v1/games", XSRF_COOKIE + "=" + token, "Origin", "https://evil.example");
        http.call(
                "OPTIONS",
                "/api/v1/games",
                null,
                "Origin",
                "https://evil.example",
                "Access-Control-Request-Method",
                "POST");

        assertThat(http.seen()).hasSizeGreaterThan(3);
        assertThat(http.seen())
                .allSatisfy(reply -> assertThat(reply.response().headers().firstValue("Access-Control-Allow-Origin"))
                        .isEmpty());
    }
}
