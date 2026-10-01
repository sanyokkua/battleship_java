package ua.kostenko.battleship.app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ProblemProbeController.class, ProblemMappingIT.PermitAllSecurity.class})
class ProblemMappingIT {
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String COOKIE = "battleship_session=cookie-value-1f9e4c";
    private static final String HEX16 = "[0-9a-f]{16}";

    /** T024 owns the real chain; this keeps Boot's default chain out of the way. */
    @TestConfiguration(proxyBeanMethods = false)
    static class PermitAllSecurity {
        @Bean
        SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .csrf(c -> c.disable())
                    .build();
        }
    }

    private record Reply(
            int status, String contentType, String cacheControl, String retryAfter, String raw, JsonNode json) {}

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger adviceLogger = (Logger) LoggerFactory.getLogger(ProblemAdvice.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        adviceLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        adviceLogger.detachAppender(logs);
    }

    private Reply get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET());
    }

    private Reply post(String path, String contentType, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return send(request);
    }

    private Reply postJson(String path, String body) throws Exception {
        return post(path, "application/json", body);
    }

    private Reply send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String raw = response.body();
        JsonNode json = raw.isBlank() ? null : mapper.readTree(raw);
        return new Reply(
                response.statusCode(),
                response.headers().firstValue("Content-Type").orElse(""),
                response.headers().firstValue("Cache-Control").orElse(""),
                response.headers().firstValue("Retry-After").orElse(""),
                raw,
                json);
    }

    @ParameterizedTest
    @CsvSource({
        "malformed-request,400",
        "session-required,401",
        "request-security-rejected,403",
        "game-unavailable,404",
        "invitation-unavailable,409",
        "action-not-allowed,409",
        "placement-out-of-bounds,409",
        "placement-overlap,409",
        "placement-touching,409",
        "random-arrangement-failed,409",
        "target-already-fired,409",
        "game-expired,410",
        "payload-too-large,413",
        "unsupported-media-type,415",
        "validation-failed,422",
        "rate-limit-exceeded,429",
        "internal-error,500",
        "service-unavailable,503"
    })
    void everyCodeAnswersWithItsTableStatusAsAProblemDocument(String code, int status) throws Exception {
        Reply reply = get("/probe/fail/" + code);

        assertEquals(status, reply.status());
        assertThat(reply.contentType()).startsWith("application/problem+json");
        assertEquals(code, reply.json().get("code").asText());
        assertEquals(status, reply.json().get("status").asInt());
        assertThat(reply.json().get("title").asText()).isNotBlank();
    }

    @Test
    void retryDelayIsCarriedAndAbsentOptionalFieldsAreOmitted() throws Exception {
        Reply reply = get("/probe/fail/rate-limit-exceeded?retryAfter=12");

        assertEquals(12, reply.json().get("retryAfterSeconds").asInt());
        assertEquals("12", reply.retryAfter());
        assertThat(reply.json().has("violations")).isFalse();
        assertThat(reply.json().has("detail")).isFalse();
    }

    @Test
    void anApplicationValidationFailureCarriesItsPointerAndRule() throws Exception {
        Reply reply = get("/probe/validation-failed");

        assertEquals(422, reply.status());
        assertEquals("/displayName", reply.json().at("/violations/0/field").asText());
        assertEquals("TOO_LONG", reply.json().at("/violations/0/rule").asText());
    }

    @Test
    void anUnknownFailureCodeAndAnUnexpectedExceptionBothBecomeInternalError() throws Exception {
        Reply unknownCode = get("/probe/fail/not-a-code");
        Reply unexpected = get("/probe/boom?leak=x");

        assertEquals(500, unknownCode.status());
        assertEquals("internal-error", unknownCode.json().get("code").asText());
        assertEquals(500, unexpected.status());
        assertEquals("internal-error", unexpected.json().get("code").asText());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/probe/create-game|{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"Ann\",\"seat\":\"HOST\"}|/seat",
                "/probe/join|{\"invitationSecret\":\"" + SECRET
                        + "\",\"displayName\":\"Bob\",\"winner\":\"YOU\"}|/winner",
                "/probe/command|{\"commandId\":\"7c0d3a1e-8f2b-4e55-9a31-0b6c1d2e3f40\",\"command\":{\"type\":\"READY\"},\"turn\":\"YOU\"}|/turn"
            })
    void anUnknownFieldInEachRequestTypeIsRefusedWithItsPointer(String path, String body, String pointer)
            throws Exception {
        Reply reply = postJson(path, body);

        assertEquals(422, reply.status());
        assertEquals("validation-failed", reply.json().get("code").asText());
        assertEquals(pointer, reply.json().at("/violations/0/field").asText());
        assertEquals("UNKNOWN_FIELD", reply.json().at("/violations/0/rule").asText());
    }

    private static String command(String command) {
        return "{\"commandId\":\"7c0d3a1e-8f2b-4e55-9a31-0b6c1d2e3f40\",\"command\":" + command + "}";
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "{\"type\":\"READY\",\"seat\":\"HOST\"}|/command/seat|UNKNOWN_FIELD",
                "{\"type\":\"FIRE\",\"target\":{\"rowIndex\":\"x\",\"columnIndex\":0}}|/command/target/rowIndex|INVALID_FORMAT",
                "{\"type\":\"DANCE\"}|/command/type|UNKNOWN_VALUE",
                "{\"shipId\":\"s01\"}|/command/type|UNKNOWN_VALUE",
                "{\"type\":\"PLACE_SHIP\",\"shipId\":\"s01\",\"anchor\":{\"rowIndex\":1,\"columnIndex\":1,\"z\":0},\"orientation\":\"HORIZONTAL\"}|/command/anchor/z|UNKNOWN_FIELD"
            })
    void aNestedCommandErrorKeepsItsPointer(String command, String pointer, String rule) throws Exception {
        Reply reply = postJson("/probe/command", command(command));

        assertEquals(422, reply.status());
        assertEquals("validation-failed", reply.json().get("code").asText());
        assertEquals(pointer, reply.json().at("/violations/0/field").asText());
        assertEquals(rule, reply.json().at("/violations/0/rule").asText());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "{\"type\":\"PLACE_SHIP\",\"shipId\":\"s01\",\"anchor\":{\"rowIndex\":100,\"columnIndex\":0},\"orientation\":\"HORIZONTAL\"}|/command/anchor/rowIndex|OUT_OF_RANGE",
                "{\"type\":\"FIRE\",\"target\":{\"rowIndex\":0,\"columnIndex\":100}}|/command/target/columnIndex|OUT_OF_RANGE",
                "{\"type\":\"FIRE\",\"target\":{\"rowIndex\":-1,\"columnIndex\":0}}|/command/target/rowIndex|OUT_OF_RANGE",
                "{\"type\":\"FIRE\"}|/command/target|REQUIRED",
                "{\"type\":\"FIRE\",\"target\":{\"rowIndex\":3}}|/command/target/columnIndex|REQUIRED",
                "{\"type\":\"REMOVE_SHIP\",\"shipId\":\"012345678901234567890123456789012\"}|/command/shipId|TOO_LONG"
            })
    void aConstraintViolationInACommandIsRefusedWithItsPointerAndRule(String command, String pointer, String rule)
            throws Exception {
        Reply reply = postJson("/probe/command", command(command));

        assertEquals(422, reply.status());
        assertEquals("validation-failed", reply.json().get("code").asText());
        assertEquals(pointer, reply.json().at("/violations/0/field").asText());
        assertEquals(rule, reply.json().at("/violations/0/rule").asText());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/probe/create-game|/rulesetId,/displayName",
                "/probe/join|/displayName,/invitationSecret",
                "/probe/command|/command,/commandId"
            })
    void anEmptyBodyReportsEveryRequiredField(String path, String fields) throws Exception {
        Reply reply = postJson(path, "{}");

        assertEquals(422, reply.status());
        assertThat(reply.json().get("violations").findValuesAsText("field"))
                .containsExactlyInAnyOrder(fields.split(","));
        assertThat(reply.json().get("violations").findValuesAsText("rule")).containsOnly("REQUIRED");
    }

    @Test
    void textLengthAndPatternViolationsMapToTheirRules() throws Exception {
        Reply tooLong =
                postJson("/probe/create-game", "{\"rulesetId\":\"r\",\"displayName\":\"" + "n".repeat(33) + "\"}");
        Reply tooShort = postJson("/probe/create-game", "{\"rulesetId\":\"r\",\"displayName\":\"\"}");
        Reply badSecret = postJson("/probe/join", "{\"invitationSecret\":\"short\",\"displayName\":\"Bob\"}");

        assertThat(tooLong.json().at("/violations/0/rule").asText()).isEqualTo("TOO_LONG");
        assertThat(tooLong.json().at("/violations/0/field").asText()).isEqualTo("/displayName");
        assertThat(tooShort.json().at("/violations/0/rule").asText()).isEqualTo("TOO_SHORT");
        assertThat(badSecret.json().at("/violations/0/rule").asText()).isEqualTo("INVALID_FORMAT");
        assertThat(badSecret.json().at("/violations/0/field").asText()).isEqualTo("/invitationSecret");
        assertThat(badSecret.raw()).doesNotContain("short");
    }

    @Test
    void anUnknownViolationRuleIsAnInternalErrorNotALeak() throws Exception {
        Reply reply = get("/probe/bad-rule");

        assertEquals(500, reply.status());
        assertEquals("internal-error", reply.json().get("code").asText());
        assertThat(reply.raw()).doesNotContain("NOT_A_RULE");
    }

    @Test
    void aValidRequestBindsAndSucceeds() throws Exception {
        Reply reply = postJson("/probe/command", """
                {"commandId":"7c0d3a1e-8f2b-4e55-9a31-0b6c1d2e3f40",
                 "command":{"type":"FIRE","target":{"rowIndex":99,"columnIndex":0}}}""");

        assertEquals(200, reply.status());
    }

    @Test
    void malformedJsonIsRefusedAsMalformedRequest() throws Exception {
        Reply reply = postJson("/probe/create-game", "{\"rulesetId\":");

        assertEquals(400, reply.status());
        assertEquals("malformed-request", reply.json().get("code").asText());
    }

    @Test
    void aWrongContentTypeIsRefusedAsUnsupportedMediaType() throws Exception {
        Reply reply = post("/probe/create-game", "text/plain", "hello");

        assertEquals(415, reply.status());
        assertEquals("unsupported-media-type", reply.json().get("code").asText());
    }

    @Test
    void everyProblemCarriesASixteenHexCorrelationIdThatIsLogged() throws Exception {
        Reply reply = get("/probe/fail/game-expired");

        String id = reply.json().get("correlationId").asText();
        assertThat(id).matches(HEX16);
        assertThat(logs.list)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains(id));
        assertThat(get("/probe/fail/game-expired").json().get("correlationId").asText())
                .isNotEqualTo(id);
    }

    @Test
    void secretsCookiesBoardsAndExceptionTextNeverReachAProblemBody() throws Exception {
        String board = "SHIPCELL-MARKER-" + "X".repeat(100);
        Reply refused = post(
                "/probe/join",
                "application/json",
                "{\"invitationSecret\":\"" + SECRET + "\",\"displayName\":\"Bob\",\"board\":\"" + board + "\"}",
                "Cookie",
                COOKIE);
        Reply thrown = post(
                "/probe/join-and-fail",
                "application/json",
                "{\"invitationSecret\":\"" + SECRET + "\",\"displayName\":\"Bob\"}",
                "Cookie",
                COOKIE);
        Reply unexpected =
                send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/probe/boom?leak=" + SECRET))
                        .header("Cookie", COOKIE)
                        .GET());

        assertThat(refused.status()).isEqualTo(422);
        assertThat(thrown.status()).isEqualTo(500);
        assertThat(unexpected.status()).isEqualTo(500);
        for (Reply reply : new Reply[] {refused, thrown, unexpected}) {
            assertThat(reply.raw())
                    .doesNotContain(SECRET, "cookie-value-1f9e4c", "SHIPCELL-MARKER", "leaked")
                    .doesNotContain("Exception", "java.lang", "ua.kostenko", "\tat ", "stackTrace");
        }
        assertThat(logs.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain(SECRET)
                .doesNotContain("cookie-value-1f9e4c")
                .doesNotContain("SHIPCELL-MARKER")
                .doesNotContain("leaked"));
    }

    @Test
    void everyResponseSuccessOrFailureIsNotStored() throws Exception {
        Map<String, Reply> replies = Map.of(
                "ok", get("/probe/ok"),
                "failure", get("/probe/fail/game-expired"),
                "refused body", postJson("/probe/create-game", "{\"rulesetId\":"),
                "unknown path", get("/probe/does-not-exist"));

        replies.forEach(
                (name, reply) -> assertThat(reply.cacheControl()).as(name).isEqualTo("no-store"));
    }
}
