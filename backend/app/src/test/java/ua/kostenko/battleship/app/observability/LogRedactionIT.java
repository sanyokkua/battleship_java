package ua.kostenko.battleship.app.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;
import static ua.kostenko.battleship.app.web.Browser.fire;
import static ua.kostenko.battleship.app.web.Browser.invitationSecret;
import static ua.kostenko.battleship.app.web.Browser.simple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ua.kostenko.battleship.app.config.ExpirySweeper;
import ua.kostenko.battleship.app.realtime.StreamProbe;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.app.web.SseStream;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;

/**
 * What the service writes to its logs (R58, S9, Constitution V), proved over one real journey: two browsers create,
 * join, arrange, play and resign a game while every failure class (401, 403, 404, 409, 410, 413, 415, 422, 429, 503)
 * and the four operational events (a rejected command, a game expiring, state lost on restart, a realtime delivery
 * failure) are provoked. The capture scope is the <b>real console output of the whole JVM</b> from before the context
 * starts to the end of the journey: {@link OutputCaptureExtension} records exactly what the structured console
 * appender printed for every logger, the framework's and Tomcat's startup records included, not only those under
 * {@code ua.kostenko.battleship}. So "no caller address" is proved for every record the process emitted, and the same
 * text is what is parsed as JSON for the structure proofs. The journey runs at DEBUG for the application's own
 * loggers, so a debug line that logged a body would be captured too. Time is a {@link MutableTimeSource} and nothing
 * sleeps (R60).
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.main.banner-mode=off",
            "logging.level.ua.kostenko.battleship=DEBUG",
            "battleship.rate-limit.create-game-per-minute=1000",
            "battleship.rate-limit.join-per-minute=1000",
            "battleship.rate-limit.commands-per-minute=1000",
            "battleship.rate-limit.read-game-per-minute=1000",
            "battleship.rate-limit.presence-per-minute=1",
            "battleship.rate-limit.stream-open-per-minute=1000",
            "battleship.rate-limit.replace-invitation-per-minute=1000",
            "battleship.rate-limit.leave-per-minute=1000",
            "server.shutdown=immediate"
        })
@Import(LogRedactionIT.Determinism.class)
class LogRedactionIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";
    private static final String GAMES = "/api/v1/games/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern HEX16 = Pattern.compile("[0-9a-f]{16}");
    private static final String APPLICATION = "ua.kostenko.battleship";

    private static final String HOST_NAME = "Zephyrine-Quokka";
    private static final String GUEST_NAME = "Brontosaur-Lime";
    private static final String BODY_MARKER = "RAWBODYMARKER-5f3c9a";
    private static final UUID REJECTED_COMMAND_ID = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");
    private static final String REJECTED_BODY =
            "{\"commandId\":\"" + REJECTED_COMMAND_ID + "\",\"command\":" + fire(3, 4) + "}";

    @TestConfiguration(proxyBeanMethods = false)
    static class Determinism {
        @Bean
        @Primary
        MutableTimeSource mutableTime() {
            return new MutableTimeSource(START);
        }

        @Bean
        @Primary
        RandomSource seededRandom() {
            return new SeededRandomSource(2026L);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableTimeSource time;

    @Autowired
    private ExpirySweeper sweeper;

    @Autowired
    private ApplicationContext context;

    /** One captured record, parsed from the console line the appender wrote. */
    private record Record(String line, JsonNode json) {
        String logger() {
            return json.at("/log/logger").asText();
        }

        String message() {
            return json.path("message").asText();
        }

        String correlationId() {
            return json.path("correlationId").asText();
        }

        String event() {
            return json.path("event").asText();
        }

        boolean ours() {
            return logger().startsWith(APPLICATION) && !logger().equals(LogRedactionIT.class.getName());
        }
    }

    private static String output;
    private static final List<String> unstructured = new ArrayList<>();
    private static List<Record> records = new ArrayList<>();
    private static Map<Integer, Reply> firstReplyByStatus = new LinkedHashMap<>();
    private static List<Reply> replies = new ArrayList<>();

    // what the journey learned, to assert the safe outcomes and to know what must never appear
    private static String hostCookieValue;
    private static String guestCookieValue;
    private static String invitationSecret;
    private static String invitationUrl;
    private static String expiredGameId;
    private static int expiredGameStatusBeforeSweep;
    private static int expiredGameStatusAfterSweep;
    private static int earlierGameStatus;
    private static JsonNode healthAfterStart;
    private static int rejectedCommandStatus;
    private static int hostReadAfterDeliveryFailure;
    private static int reopenedStreamStatus;
    private static boolean guestSawHostDisconnected;

    /**
     * Runs the journey once, before the first test. The test instance (and with it the context) is created after the
     * output capture starts, so start-up records are part of the captured text; that is why the lifecycle is
     * per-method and the findings are static.
     */
    @BeforeEach
    void theJourney(CapturedOutput captured) throws Exception {
        if (output != null) return;
        Browser host = new Browser(port);
        Browser guest = new Browser(port);
        SecurityHttp anonymous = new SecurityHttp(port);

        healthAfterStart = track(anonymous.call("GET", "/api/v1/health", null)).json();

        JsonNode created = host.create(SEA_BATTLE, HOST_NAME);
        invitationUrl = created.get("invitationUrl").asText();
        invitationSecret = invitationSecret(host.snapshot());
        hostCookieValue = host.sessionValue();
        guest.join(host.gameId(), invitationSecret, GUEST_NAME);
        guestCookieValue = guest.sessionValue();
        String game = host.gameId();
        String hostCookie = SESSION_COOKIE + "=" + hostCookieValue;
        time.advance(Duration.ofSeconds(1));

        // 401, 404, 403, 415, 413, 422: the refusals that need nothing from the game
        track(anonymous.call("GET", GAMES + game, null));
        // 404, and what the restart left of an earlier game: a well-formed id the service has never heard of
        earlierGameStatus = track(host.http().call("GET", GAMES + "AAAAAAAAAAAAAAAAAAAAAA", hostCookie))
                .status();
        track(host.http()
                .postJson(
                        GAMES + game + "/commands",
                        hostCookie,
                        "{\"commandId\":\"" + UUID.randomUUID() + "\",\"command\":" + simple("READY") + "}",
                        "Origin",
                        "https://evil.example"));
        track(host.http().postBody(GAMES + game + "/commands", hostCookie, "text/plain", BODY_MARKER));
        track(host.http()
                .postBody(GAMES + game + "/commands", hostCookie, "application/json", BODY_MARKER.repeat(2000)));
        track(host.http()
                .postJson(
                        GAMES + game + "/commands",
                        hostCookie,
                        "{\"commandId\":\"" + UUID.randomUUID() + "\",\"command\":" + simple("READY") + ",\"extra\":\""
                                + BODY_MARKER + "\"}"));
        // 503: a browser that already holds a live game asks for a second one
        track(host.http()
                .postJson(
                        "/api/v1/games",
                        hostCookie,
                        "{\"rulesetId\":\"" + SEA_BATTLE + "\",\"displayName\":\"" + HOST_NAME + "\"}"));
        // 429: the presence limit is one a minute
        track(host.http().postWithToken(GAMES + game + "/presence", host.http().freshToken(), hostCookie));
        track(host.http().postWithToken(GAMES + game + "/presence", host.http().freshToken(), hostCookie));

        // the realtime delivery failure: the host's client goes away without a word and a heartbeat write fails
        try (SseStream hostStream = host.events();
                SseStream guestStream = guest.events()) {
            hostStream.next();
            guestStream.next();
            hostStream.close();
            for (int beat = 0; beat < 50 && !guestSawHostDisconnected; beat++) {
                time.advance(Duration.ofSeconds(15));
                StreamProbe.heartbeat(context);
                guestSawHostDisconnected =
                        !guest.snapshot().at("/opponent/connected").asBoolean();
            }
            hostReadAfterDeliveryFailure = host.read().status();
            try (SseStream reopened = host.events()) {
                reopenedStreamStatus = reopened.status();
                reopened.next();

                // the journey proper, on the reopened stream
                host.accept(simple("PLACE_FLEET_RANDOMLY"));
                guest.accept(simple("PLACE_FLEET_RANDOMLY"));
                host.accept(simple("READY"));
                guest.accept(simple("READY"));
                JsonNode view = host.snapshot();
                Browser shooter = view.get("turn").asText().equals("YOU") ? host : guest;
                Browser other = shooter == host ? guest : host;
                shooter.accept(fire(0, 0));
                // a rejected command: the other player is not on turn (or the shot just hit and kept the turn)
                JsonNode after = host.snapshot();
                Browser offTurn = after.get("turn").asText().equals("YOU") ? guest : host;
                String offTurnCookie = SESSION_COOKIE + "=" + (offTurn == host ? hostCookieValue : guestCookieValue);
                rejectedCommandStatus = track(offTurn.http()
                                .postBody(GAMES + game + "/commands", offTurnCookie, "application/json", REJECTED_BODY))
                        .status();
                other.accept(simple("RESIGN"));
                host.leave();
                guest.leave();
            }
        }

        // a game expiring: seen as 410 when asked, then forgotten and reported by the sweep
        Browser lonely = new Browser(port);
        lonely.create(SEA_BATTLE, "Solitary-Narwhal");
        expiredGameId = lonely.gameId();
        time.advance(Duration.ofSeconds(901));
        expiredGameStatusBeforeSweep = track(lonely.read()).status();
        time.advance(Duration.ofSeconds(300));
        sweeper.tick();
        expiredGameStatusAfterSweep = track(lonely.read()).status();

        output = captured.getAll();
        for (String line : captured.getOut().split("\\R")) {
            if (line.isBlank()) continue;
            if (line.startsWith("{")) {
                records.add(new Record(line, JSON.readTree(line)));
            } else {
                unstructured.add(records.isEmpty() ? "before-logging-init" : line);
            }
        }
    }

    private Reply track(Reply reply) {
        replies.add(reply);
        firstReplyByStatus.putIfAbsent(reply.status(), reply);
        return reply;
    }

    /** The application's own records from the start-up fact onward: what it wrote once it was running. */
    private List<Record> applicationRecords() {
        int started = records.indexOf(events("state-lost-on-restart").getFirst());
        return records.subList(started, records.size()).stream()
                .filter(Record::ours)
                .toList();
    }

    private List<Record> events(String event) {
        return records.stream().filter(r -> event.equals(r.event())).toList();
    }

    // ---------------------------------------------------------------- (1)-(7) nothing sensitive is ever written

    @Test
    void theJourneyProvokedEveryFailureClass() {
        assertThat(firstReplyByStatus.keySet()).contains(401, 403, 404, 409, 410, 413, 415, 422, 429, 503);
        assertThat(rejectedCommandStatus).isEqualTo(409);
    }

    @Test
    void noSessionCookieValueOrRawSessionStringIsLogged() {
        assertThat(output).as("(1) host session value").doesNotContain(hostCookieValue);
        assertThat(output).as("(1) guest session value").doesNotContain(guestCookieValue);
        assertThat(output).as("(1) cookie name").doesNotContain(SESSION_COOKIE);
        assertThat(output).as("(1) cookie header").doesNotContainIgnoringCase("set-cookie");
    }

    @Test
    void noInvitationSecretFragmentOrSecretBearingUrlIsLogged() {
        assertThat(output).as("(2) invitation secret").doesNotContain(invitationSecret);
        assertThat(output).as("(3) fragment").doesNotContain("#invite=").doesNotContain("invite=");
        assertThat(output).as("(3) invitation URL").doesNotContain(invitationUrl);
    }

    @Test
    void noBoardContentIsLogged() {
        assertThat(output).as("(4) ship word").doesNotContain("SHIP");
        assertThat(output)
                .as("(4) board cells and coordinates")
                .doesNotContain("rowIndex")
                .doesNotContain("columnIndex")
                .doesNotContain("yourBoard")
                .doesNotContain("opponentBoard")
                .doesNotContain("\"grid\"");
    }

    @Test
    void noRawRequestBodyIsLogged() {
        assertThat(output).as("(5) marker bodies").doesNotContain(BODY_MARKER);
        assertThat(output).as("(5) the rejected command's body").doesNotContain(REJECTED_BODY);
        assertThat(output).as("(5) its command id").doesNotContain(REJECTED_COMMAND_ID.toString());
        assertThat(output)
                .as("(5) body field names")
                .doesNotContain("commandId")
                .doesNotContain("displayName");
    }

    @Test
    void noPlayerDisplayNameIsLogged() {
        assertThat(output).as("(6) host name").doesNotContain(HOST_NAME);
        assertThat(output).as("(6) guest name").doesNotContain(GUEST_NAME);
        assertThat(output).as("(6) third name").doesNotContain("Solitary-Narwhal");
    }

    @Test
    void noCallerAddressIsLogged() {
        assertThat(output)
                .as("(7) the test client's address, IPv4 and IPv6 spellings")
                .doesNotContain("127.0.0.1")
                .doesNotContain("0:0:0:0:0:0:0:1")
                .doesNotContain("::1");
    }

    // ---------------------------------------------------------------- (8) correlation

    @Test
    void everyApplicationRecordCarriesACorrelationIdAndAProblemBodyMatchesItsRequestsRecords() {
        List<Record> ours = applicationRecords();
        assertThat(ours).as("application records were captured").isNotEmpty();
        assertThat(ours)
                .as("(8) every application record after start-up has a 16-hex correlation id")
                .allSatisfy(record ->
                        assertThat(record.correlationId()).as(record.line()).matches(HEX16));
        for (Reply problem : replies.stream()
                .filter(r -> r.status() >= 400 && r.json() != null && r.json().has("correlationId"))
                .toList()) {
            String id = problem.json().get("correlationId").asText();
            assertThat(records)
                    .as("(8) a record of the request that answered %s carries its id %s"
                            .formatted(problem.status(), id))
                    .anySatisfy(record -> {
                        assertThat(record.correlationId()).isEqualTo(id);
                        assertThat(record.message()).contains("status=" + problem.status());
                    });
        }
    }

    // ---------------------------------------------------------------- (9) structure

    @Test
    void everyRecordIsOneJsonObjectWithStableFieldNames() {
        assertThat(records).isNotEmpty();
        assertThat(unstructured)
                .as(
                        "(9) the only lines that are not JSON are the test bootstrap's, written before logging is configured")
                .allMatch("before-logging-init"::equals);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.json().isObject()).as(record.line()).isTrue();
            assertThat(record.json().has("@timestamp")).as(record.line()).isTrue();
            assertThat(record.json().at("/log/level").asText())
                    .as(record.line())
                    .isNotEmpty();
            assertThat(record.logger()).as(record.line()).isNotEmpty();
            assertThat(record.json().has("message")).as(record.line()).isTrue();
        });
        assertThat(applicationRecords())
                .as("application records of one kind keep the same field names")
                .allSatisfy(record -> assertThat(record.json().fieldNames())
                        .toIterable()
                        .contains("@timestamp", "log", "message", "correlationId"));
    }

    @Test
    void everyRecordOfARequestThatNamesAGameCarriesItsGameId() {
        for (int status : new int[] {404, 409}) {
            String id =
                    firstReplyByStatus.get(status).json().get("correlationId").asText();
            List<Record> ofRequest =
                    records.stream().filter(r -> id.equals(r.correlationId())).toList();
            assertThat(ofRequest)
                    .as("records of the request answered %s".formatted(status))
                    .isNotEmpty();
            assertThat(ofRequest).allSatisfy(record -> assertThat(
                            record.json().path("gameId").asText())
                    .as(record.line())
                    .matches("[A-Za-z0-9_-]{22}"));
        }
    }

    // ---------------------------------------------------------------- (10) the four operational events

    @Test
    void aRejectedCommandIsOneDiagnosticRecordAndTheCallerGetsA409() {
        assertThat(events("command-rejected")).hasSize(1).allSatisfy(record -> {
            assertThat(record.correlationId()).matches(HEX16);
            assertThat(record.json().path("gameId").asText()).matches("[A-Za-z0-9_-]{22}");
        });
        assertThat(rejectedCommandStatus).isEqualTo(409);
    }

    @Test
    void anExpiringGameIsOneDiagnosticRecordAndAskersAreTold410ThenNothing() {
        assertThat(events("game-expired")).hasSize(1).allSatisfy(record -> {
            assertThat(record.json().path("gameId").asText()).isEqualTo(expiredGameId);
            assertThat(record.correlationId()).matches(HEX16);
        });
        assertThat(expiredGameStatusBeforeSweep).isEqualTo(410);
        assertThat(expiredGameStatusAfterSweep).isEqualTo(404);
    }

    @Test
    void lostStateOnRestartIsOneStartupRecordThatNeverImpliesAGameSurvives() {
        assertThat(events("state-lost-on-restart")).hasSize(1).allSatisfy(record -> {
            assertThat(record.correlationId()).matches(HEX16);
            assertThat(record.message())
                    .containsIgnoringCase("no state")
                    .containsIgnoringCase("gone")
                    .doesNotContainIgnoringCase("recovered from")
                    .doesNotContainIgnoringCase("restored")
                    .doesNotContainIgnoringCase("resum");
        });
        assertThat(healthAfterStart.get("ready").asBoolean()).isTrue();
        assertThat(earlierGameStatus).as("an earlier game is simply not there").isEqualTo(404);
    }

    @Test
    void aFailedRealtimeDeliveryIsOneDiagnosticRecordAndTheGameCarriesOn() {
        assertThat(guestSawHostDisconnected).isTrue();
        assertThat(events("realtime-delivery-failed")).hasSize(1).allSatisfy(record -> {
            assertThat(record.correlationId()).matches(HEX16);
            assertThat(record.json().path("gameId").asText()).matches("[A-Za-z0-9_-]{22}");
        });
        assertThat(hostReadAfterDeliveryFailure).isEqualTo(200);
        assertThat(reopenedStreamStatus).isEqualTo(200);
    }
}
