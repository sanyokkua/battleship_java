package ua.kostenko.battleship.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.application.registry.SessionRegistry;

/**
 * Parts 1 and 2 of 4: what the session filter and the chain decide before any controller is reached, and how a session
 * is issued and reused (R32, R33). Two live games per browser let one browser create twice.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "battleship.max-live-games-per-browser=2")
class AuthorizationIT {
    private static final String CREATE_BODY = "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Captain\"}";
    private static final String UNKNOWN_VALUE = "dW5rbm93bi1zZXNzaW9uLXZhbHVlLXRoYXQtd2FzLW5ldmVyLWlzc3VlZA";

    @LocalServerPort
    private int port;

    @Autowired
    private SessionRegistry sessions;

    private SecurityHttp http;

    @BeforeEach
    void connect() {
        http = new SecurityHttp(port);
    }

    private static Stream<Arguments> protectedOperations() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/games/g1"),
                Arguments.of("POST", "/api/v1/games/g1/commands"),
                Arguments.of("GET", "/api/v1/games/g1/events"),
                Arguments.of("POST", "/api/v1/games/g1/invitation"),
                Arguments.of("POST", "/api/v1/games/g1/presence"),
                Arguments.of("POST", "/api/v1/games/g1/leave"));
    }

    private Reply send(String method, String path, String cookie) throws Exception {
        if (method.equals("GET")) {
            return http.call("GET", path, cookie);
        }
        String token = http.freshToken();
        return http.postWithToken(path, token, cookie);
    }

    private static void assertSessionRequiredAndCleared(Reply reply) {
        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
        assertThat(reply.json().get("code").asText()).isEqualTo("session-required");
        List<String> cleared = reply.setCookies(SESSION_COOKIE);
        assertThat(cleared).hasSize(1);
        assertThat(cleared.getFirst())
                .startsWith(SESSION_COOKIE + "=;")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Domain");
    }

    @ParameterizedTest
    @MethodSource("protectedOperations")
    void aMissingSessionCookieIsRefusedAndClearedOnEveryProtectedPath(String method, String path) throws Exception {
        assertSessionRequiredAndCleared(send(method, path, null));
    }

    @ParameterizedTest
    @MethodSource("protectedOperations")
    void anUnknownSessionIsRefusedAndClearedOnEveryProtectedPath(String method, String path) throws Exception {
        assertSessionRequiredAndCleared(send(method, path, SESSION_COOKIE + "=" + UNKNOWN_VALUE));
    }

    @ParameterizedTest
    @MethodSource("protectedOperations")
    void aMalformedSessionValueIsRefusedAndClearedOnEveryProtectedPath(String method, String path) throws Exception {
        assertSessionRequiredAndCleared(send(method, path, SESSION_COOKIE + "=%%not a session%%"));
        assertSessionRequiredAndCleared(send(method, path, SESSION_COOKIE + "="));
    }

    @ParameterizedTest
    @MethodSource("encodedProtectedPaths")
    void aPercentEncodedProtectedPathIsStillRefusedAndCleared(String method, String path) throws Exception {
        assertSessionRequiredAndCleared(send(method, path, null));
    }

    private static Stream<Arguments> encodedProtectedPaths() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/games/g1/%65vents"),
                Arguments.of("POST", "/api/v1/games/g1/%6Ceave"),
                Arguments.of("GET", "/api/v1/%67ames/g1"));
    }

    @Test
    void aKnownSessionPassesTheFilterAndReachesDispatch() throws Exception {
        String value = "a-session-value-the-registry-knows-0123456789abcdef";
        sessions.register(value, Instant.now());

        Reply reply = http.call("GET", "/api/v1/games/g1", SESSION_COOKIE + "=" + value);

        assertThat(reply.status()).isNotEqualTo(401);
        assertThat(reply.setCookies(SESSION_COOKIE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/games", "/api/v1/games/g1/join"})
    void createAndJoinAreNeverRefusedForAMissingSession(String path) throws Exception {
        assertThat(http.postWithToken(path, http.freshToken(), null).status()).isNotEqualTo(401);
        assertThat(http.postWithToken(path, http.freshToken(), SESSION_COOKIE + "=" + UNKNOWN_VALUE)
                        .status())
                .isNotEqualTo(401);
        assertThat(http.postWithToken(path, http.freshToken(), SESSION_COOKIE + "=%%bad%%")
                        .status())
                .isNotEqualTo(401);
    }

    @Test
    void authenticationComesBeforeAuthorizationSoANeverExistingGameIsStill401() throws Exception {
        Reply noSession = http.call("GET", "/api/v1/games/never-existed", null);
        Reply unknownSession = http.call("GET", "/api/v1/games/never-existed", SESSION_COOKIE + "=" + UNKNOWN_VALUE);

        assertThat(noSession.status()).isEqualTo(401);
        assertThat(unknownSession.status()).isEqualTo(401);
        assertThat(noSession.json().get("code").asText()).isEqualTo("session-required");
    }

    @Test
    void theAntiForgeryCookieAndItsAttributesArePresentOnMeta() throws Exception {
        Reply meta = http.call("GET", "/api/v1/meta", null);

        List<String> cookies = meta.setCookies("XSRF-TOKEN");
        assertThat(cookies).hasSize(1);
        assertThat(cookies.getFirst()).contains("Path=/", "Secure", "SameSite=Strict");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/meta", "/api/v1/rulesets", "/api/v1/health"})
    void theUnauthenticatedOperationsAnswer200WithNoCookieAndNoToken(String path) throws Exception {
        assertThat(http.call("GET", path, null).status()).isEqualTo(200);
    }

    @Test
    void createGameIssuesTheSessionCookieWithExactlyTheContractAttributes() throws Exception {
        Reply created = http.postJson("/api/v1/games", null, CREATE_BODY);

        assertThat(created.status()).isEqualTo(201);
        List<String> issued = created.setCookies(SESSION_COOKIE);
        assertThat(issued).hasSize(1);
        String[] parts = issued.getFirst().split("; ");
        assertThat(parts[0]).matches(SESSION_COOKIE + "=[A-Za-z0-9_-]{43}");
        assertThat(List.of(parts).subList(1, parts.length))
                .containsExactlyInAnyOrder("Path=/", "Secure", "HttpOnly", "SameSite=Strict");
    }

    @Test
    void oneBrowserIsRecognisedOnEveryGameItCreatesWithoutANewCookie() throws Exception {
        Reply first = http.postJson("/api/v1/games", null, CREATE_BODY);
        String cookie = SecurityHttp.issuedSession(first);

        Reply second = http.postJson("/api/v1/games", cookie, CREATE_BODY);

        assertThat(second.status()).isEqualTo(201);
        assertThat(second.setCookies(SESSION_COOKIE)).isEmpty();
        for (Reply created : List.of(first, second)) {
            Reply read = http.call(
                    "GET", "/api/v1/games/" + created.json().get("gameId").asText(), cookie);
            assertThat(read.status()).isEqualTo(200);
            assertThat(read.setCookies(SESSION_COOKIE)).isEmpty();
        }
    }
}
