package ua.kostenko.battleship.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.XSRF_COOKIE;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/**
 * Part 1: the anti-forgery check (R35) and the absence of CORS (R36). Part 2: cross-site refusal (R36), the size
 * ceiling and the content type (R40).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "battleship.rate-limit.create-game-per-minute=1000")
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

    @Autowired
    private BattleshipProperties properties;

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

    // ---- part 2 ----

    private static final String CREATE_BODY = "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Captain\"}";
    private static final String[] BODY_LESS = {"invitation", "presence", "leave"};

    private void assertProblem(Reply reply, int status, String code, String title) {
        assertThat(reply.status()).isEqualTo(status);
        assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
        assertThat(reply.header("Cache-Control")).isEqualTo("no-store");
        assertThat(reply.json().get("code").asText()).isEqualTo(code);
        assertThat(reply.json().get("status").asInt()).isEqualTo(status);
        assertThat(reply.json().get("title").asText()).isEqualTo(title);
        assertThat(reply.json().get("correlationId").asText()).matches(HEX16);
        assertThat(reply.json().has("violations")).isFalse();
    }

    private void assertSecurityRejected(Reply reply) {
        assertProblem(reply, 403, "request-security-rejected", "Request rejected");
    }

    private void assertTooLarge(Reply reply) {
        assertProblem(reply, 413, "payload-too-large", "Payload too large");
    }

    private String max() {
        return "x".repeat(properties.maxRequestBodyBytes());
    }

    private String big() {
        return max() + "x";
    }

    private String publicOrigin() {
        return properties.publicBaseUrl();
    }

    @Test
    void aForeignOriginOrASiteFetchFromElsewhereIsRefused() throws Exception {
        assertSecurityRejected(http.postJson("/api/v1/games", null, CREATE_BODY, "Origin", "https://evil.example"));
        assertSecurityRejected(http.postJson("/api/v1/games", null, CREATE_BODY, "Sec-Fetch-Site", "cross-site"));
        assertSecurityRejected(http.postJson("/api/v1/games", null, CREATE_BODY, "Sec-Fetch-Site", "same-site"));
        assertSecurityRejected(http.postJson("/api/v1/games", null, CREATE_BODY, "Origin", "http://localhost:" + port));
        assertSecurityRejected(http.postJson("/api/v1/games", null, CREATE_BODY, "Origin", "null"));
        assertSecurityRejected(http.postJson(
                "/api/v1/games", null, CREATE_BODY, "Origin", publicOrigin().replace("http://", "https://")));
    }

    @Test
    void theOriginIsJudgedAgainstThePublicBaseUrlNotAgainstTheHostHeader() throws Exception {
        assertThat(http.postJson("/api/v1/games", null, CREATE_BODY, "Origin", publicOrigin(), "Host", "other.example")
                        .status())
                .isEqualTo(201);
        assertSecurityRejected(http.postJson(
                "/api/v1/games", null, CREATE_BODY, "Origin", "http://other.example", "Host", "other.example"));
    }

    @Test
    void anOversizedCrossSiteRequestIsAnsweredTooLargeBecauseTheSizeCheckRunsFirst() throws Exception {
        assertTooLarge(http.postJson("/api/v1/games", null, big(), "Origin", "https://evil.example"));
    }

    @Test
    void sameOriginAndDirectNavigationAreAdmitted() throws Exception {
        assertThat(http.postJson("/api/v1/games", null, CREATE_BODY, "Sec-Fetch-Site", "same-origin")
                        .status())
                .isEqualTo(201);
        assertThat(http.postJson("/api/v1/games", null, CREATE_BODY, "Sec-Fetch-Site", "none")
                        .status())
                .isEqualTo(201);
        assertThat(http.postJson(
                                "/api/v1/games",
                                null,
                                CREATE_BODY,
                                "Origin",
                                publicOrigin(),
                                "Sec-Fetch-Site",
                                "same-origin")
                        .status())
                .isEqualTo(201);
    }

    @Test
    void aCallerWithNoBrowserHeadersIsAdmittedAndStillNeedsTheToken() throws Exception {
        assertThat(http.postJson("/api/v1/games", null, CREATE_BODY).status()).isEqualTo(201);
        assertRejected(http.call("POST", "/api/v1/games", null));
    }

    @Test
    void aSafeMethodIsNeverRefusedForItsOrigin() throws Exception {
        assertThat(http.call(
                                "GET",
                                "/api/v1/meta",
                                null,
                                "Origin",
                                "https://evil.example",
                                "Sec-Fetch-Site",
                                "cross-site")
                        .status())
                .isEqualTo(200);
    }

    @Test
    void anOversizedBodyIsRefusedOnEveryPostOperationIncludingTheBodyLessOnes() throws Exception {
        assertTooLarge(http.postJson("/api/v1/games", null, big()));
        assertTooLarge(http.postJson("/api/v1/games/g1/join", null, big()));
        assertTooLarge(http.postJson("/api/v1/games/g1/commands", null, big()));
        for (String operation : BODY_LESS) {
            assertTooLarge(http.postJson("/api/v1/games/g1/" + operation, null, big()));
        }
    }

    @Test
    void aBodyOfExactlyTheCeilingIsNotRefusedForItsSize() throws Exception {
        assertThat(http.postJson("/api/v1/games/g1/presence", null, max()).status())
                .isNotEqualTo(413);
    }

    @Test
    void anOversizedChunkedBodyIsRefusedTheSameWay() throws Exception {
        byte[] big = big().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTooLarge(http.postChunked("/api/v1/games", null, "application/json", big));
        assertTooLarge(http.postChunked("/api/v1/games/g1/leave", null, "application/json", big));
        byte[] exact = max().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(http.postChunked("/api/v1/games/g1/presence", null, "application/json", exact)
                        .status())
                .isNotEqualTo(413);
        byte[] fine = CREATE_BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(http.postChunked("/api/v1/games", null, "application/json", fine)
                        .status())
                .isEqualTo(201);
    }

    @Test
    void aWrongContentTypeIsRefusedWhereABodyIsExpectedAndNeverOnTheBodyLessOperations() throws Exception {
        Reply created = http.postJson("/api/v1/games", null, CREATE_BODY);
        String session = SecurityHttp.issuedSession(created);
        String gameId = created.json().get("gameId").asText();

        assertProblem(
                http.postBody("/api/v1/games/" + gameId + "/commands", session, "text/plain", "{}"),
                415,
                "unsupported-media-type",
                "Unsupported media type");
        assertProblem(
                http.postBody("/api/v1/games", null, "text/plain", CREATE_BODY),
                415,
                "unsupported-media-type",
                "Unsupported media type");
        assertProblem(
                http.postBody("/api/v1/games/" + gameId + "/join", null, "text/plain", "{}"),
                415,
                "unsupported-media-type",
                "Unsupported media type");
        for (String type : new String[] {"text/plain", "application/xml", "application/json"}) {
            for (String operation : BODY_LESS) {
                assertThat(http.postBody("/api/v1/games/nogame/" + operation, session, type, "x")
                                .status())
                        .as(operation + " " + type)
                        .isNotEqualTo(415);
            }
        }
    }

    /**
     * Tomcat marks the response as errored once a read fails and replaces the body with its own error page, so only
     * the status (400) and the closed connection are the contract here; the filter's Problem is logged, not delivered.
     */
    @Test
    void aMalformedChunkedBodyIsAnsweredBadRequestNotPassedOn() throws Exception {
        try (java.net.Socket socket = new java.net.Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream()
                    .write(("POST /api/v1/games HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n"
                                    + "Content-Type: application/json\r\n\r\nZZZ\r\n")
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            String reply = new String(socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            assertThat(reply).startsWith("HTTP/1.1 400");
        }
    }
}
