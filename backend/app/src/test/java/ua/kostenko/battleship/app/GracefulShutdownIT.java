package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.security.SecurityHttp.SESSION_COOKIE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;
import ua.kostenko.battleship.app.web.Browser;
import ua.kostenko.battleship.app.web.SseStream;

/**
 * Shutting down honestly (T036; R59, R29, R48, US7). The test runs the real application as its own
 * {@link SpringApplication} on a random port, because it has to close the context itself, and closes it on another
 * thread while it holds two open event streams and one action that is still in flight. What the service does during
 * the shutdown is observed at the moment readiness is flipped, from inside that very publication: nothing else has
 * been done yet, so "readiness flips before anything else observable" is read directly, with no sleeping. The
 * scenario runs once; each test asserts one proof of T036.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GracefulShutdownIT {
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";
    private static final int DRAIN_SECONDS = 6;
    private static final Duration LONG = Duration.ofSeconds(30);

    /** Held while the scenario runs, so the test can block an action and observe the shutdown from inside it. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Seams {
        /** Blocks the first armed command inside the server until released, so it is in flight during shutdown. */
        static final class SlowCommand extends OncePerRequestFilter {
            final AtomicBoolean armed = new AtomicBoolean();
            final CountDownLatch entered = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws jakarta.servlet.ServletException, IOException {
                if (request.getRequestURI().endsWith("/commands") && armed.compareAndSet(true, false)) {
                    entered.countDown();
                    try {
                        release.await(LONG.toSeconds(), TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                chain.doFilter(request, response);
            }
        }

        /** What the service answered at each moment of interest, recorded where it happened. */
        static final class Observations {
            volatile Reply startupHealth;
            volatile Reply healthAtFlip;
            volatile boolean hostStreamOpenAtFlip;
            volatile boolean guestStreamOpenAtFlip;
            volatile Answer reconnectAtFlip;
            volatile Reply createAtFlip;
            volatile Reply joinAtFlip;
            volatile Reply commandAtFlip;
            volatile Reply readAtFlip;
            volatile Throwable failure;
            final CountDownLatch flipped = new CountDownLatch(1);
            volatile Browser host;
            volatile SseStream hostStream;
            volatile SseStream guestStream;
            volatile Browser guest;
            volatile int port;
        }

        static final Observations OBSERVED = new Observations();

        @Bean
        SlowCommand slowCommand() {
            return new SlowCommand();
        }

        /** Runs after the web server is up but before readiness is ACCEPTING_TRAFFIC: the start-up window. */
        @Bean
        ApplicationRunner startupProbe(Environment environment) {
            return arguments -> {
                int port = Integer.parseInt(environment.getRequiredProperty("local.server.port"));
                OBSERVED.startupHealth = new SecurityHttp(port).call("GET", "/api/v1/health", null);
            };
        }

        /** Observes, on the publishing thread, what is true at the instant readiness stops accepting traffic. */
        @Bean
        FlipProbe flipProbe() {
            return new FlipProbe();
        }

        static final class FlipProbe implements ApplicationListener<AvailabilityChangeEvent<ReadinessState>> {
            @Override
            public void onApplicationEvent(AvailabilityChangeEvent<ReadinessState> event) {
                if (event.getState() != ReadinessState.REFUSING_TRAFFIC || OBSERVED.host == null) return;
                Observations seen = OBSERVED;
                try {
                    seen.healthAtFlip = within(() -> new SecurityHttp(seen.port).call("GET", "/api/v1/health", null));
                    seen.hostStreamOpenAtFlip = stillOpen(seen.hostStream);
                    seen.guestStreamOpenAtFlip = stillOpen(seen.guestStream);
                    // an admitted reconnection answers 200 and streams on: only the head is read, then it is dropped
                    seen.reconnectAtFlip = reconnect(seen);
                    seen.createAtFlip = within(() -> new SecurityHttp(seen.port)
                            .postJson(
                                    "/api/v1/games",
                                    null,
                                    "{\"rulesetId\":\"" + SEA_BATTLE + "\",\"displayName\":\"Late\"}"));
                    seen.joinAtFlip = within(() -> seen.guest
                            .http()
                            .postJson(
                                    "/api/v1/games/" + seen.host.gameId() + "/join",
                                    null,
                                    "{\"invitationSecret\":\"" + "x".repeat(22) + "\",\"displayName\":\"Late\"}"));
                    seen.commandAtFlip = within(() -> seen.host.send(Browser.simple("CLEAR_FLEET")));
                    seen.readAtFlip = within(seen.host::read);
                } catch (Throwable failure) {
                    seen.failure = failure;
                } finally {
                    seen.flipped.countDown();
                }
            }
        }

        private static Answer reconnect(Observations seen) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "http://localhost:" + seen.port + "/api/v1/games/" + seen.host.gameId() + "/events"))
                    .header("Accept", "text/event-stream")
                    .header("Cookie", SESSION_COOKIE + "=" + seen.host.sessionValue())
                    .GET()
                    .build();
            HttpResponse<InputStream> head = HttpClient.newHttpClient()
                    .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .get(5, TimeUnit.SECONDS);
            try (InputStream body = head.body()) {
                if (head.statusCode() != 503) return new Answer(head.statusCode(), "", null, "");
                String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                return new Answer(
                        503,
                        head.headers().firstValue("Retry-After").orElse(""),
                        new ObjectMapper().readTree(text),
                        text);
            }
        }

        private static Reply within(java.util.concurrent.Callable<Reply> call) throws Exception {
            return CompletableFuture.supplyAsync(() -> {
                        try {
                            return call.call();
                        } catch (Exception failed) {
                            throw new IllegalStateException(failed);
                        }
                    })
                    .get(3, TimeUnit.SECONDS);
        }

        private static boolean stillOpen(SseStream stream) throws InterruptedException {
            try {
                // events still in flight (the opponent's presence) may arrive; only the end or a close matters
                for (SseStream.Event event = stream.poll(Duration.ofMillis(300)); event != null; ) {
                    if ("closed".equals(event.name())) return false;
                    event = stream.poll(Duration.ofMillis(300));
                }
                return true;
            } catch (AssertionError ended) {
                return false;
            }
        }
    }

    /** Keeps the scan to the application's own classes: no test class, probe or other test configuration. */
    private static final class ExcludeTestClasses extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws IOException {
            return reader.getResource().getURL().toString().contains("/test-classes/");
        }
    }

    private ConfigurableApplicationContext context;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;

    private Reply inFlightReply;
    private Throwable inFlightFailure;
    private Duration inFlightTook;
    private boolean closeStillWaitingWhenReleased;
    private boolean closedWithinWindow;
    private String hostStreamText;
    private boolean hostStreamEnded;
    private Duration streamsEndedAfter;
    private boolean guestStreamEnded;
    private String shutdownLog;

    @BeforeAll
    void runTheScenario() throws Exception {
        originalOut = System.out;
        System.setOut(new PrintStream(new Tee(originalOut, captured), true, StandardCharsets.UTF_8));
        SpringApplication application = new SpringApplication(BattleshipApplication.class, Seams.class);
        application.addInitializers(
                ctx -> ctx.getBeanFactory().registerSingleton("excludeTestClasses", new ExcludeTestClasses()));
        context = application.run(
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--battleship.shutdown-drain-seconds=" + DRAIN_SECONDS,
                "--battleship.rate-limit.create-game-per-minute=1000",
                "--battleship.rate-limit.join-per-minute=1000",
                "--battleship.rate-limit.commands-per-minute=1000",
                "--battleship.rate-limit.read-game-per-minute=1000",
                "--battleship.rate-limit.stream-open-per-minute=1000");
        Seams.Observations seen = Seams.OBSERVED;
        seen.port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));

        Browser host = new Browser(seen.port);
        host.create(SEA_BATTLE, "Host");
        Browser guest = new Browser(seen.port);
        guest.join(host.gameId(), Browser.invitationSecret(host.snapshot()), "Guest");
        SseStream hostStream = host.events();
        SseStream guestStream = guest.events();
        assertThat(hostStream.next().name()).isEqualTo("snapshot");
        assertThat(guestStream.next().name()).isEqualTo("snapshot");

        // an action admitted before the shutdown and still running when it begins
        Seams.SlowCommand slow = context.getBean(Seams.SlowCommand.class);
        slow.armed.set(true);
        CompletableFuture<Reply> inFlight = CompletableFuture.supplyAsync(() -> {
            try {
                return host.send(Browser.simple("PLACE_FLEET_RANDOMLY"));
            } catch (Exception failed) {
                throw new IllegalStateException(failed);
            }
        });
        assertThat(slow.entered.await(LONG.toSeconds(), TimeUnit.SECONDS)).isTrue();

        seen.host = host;
        seen.guest = guest;
        seen.hostStream = hostStream;
        seen.guestStream = guestStream;

        long closeStarted = System.nanoTime();
        CompletableFuture<Long> closed = CompletableFuture.supplyAsync(() -> {
            context.close();
            return System.nanoTime();
        });
        assertThat(seen.flipped.await(LONG.toSeconds(), TimeUnit.SECONDS))
                .as("readiness flip observed; listener failure " + seen.failure)
                .isTrue();

        // the streams end by themselves once the flip is done; whatever they carry until then is recorded
        hostStreamText = drain(hostStream);
        hostStreamEnded = true;
        drain(guestStream);
        guestStreamEnded = true;
        // the in-flight action is still held here: the streams ended because the shutdown closed them, not because
        // the window ran out
        streamsEndedAfter = Duration.ofNanos(System.nanoTime() - closeStarted);

        closeStillWaitingWhenReleased = !closed.isDone();
        slow.release.countDown();
        try {
            inFlightReply = inFlight.get(LONG.toSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException cut) {
            inFlightFailure = cut; // the shutdown cut the action: proof (5) reports it
        }
        inFlightTook = Duration.ofNanos(System.nanoTime() - closeStarted);
        long closedAt = closed.get(LONG.toSeconds(), TimeUnit.SECONDS);
        closedWithinWindow =
                Duration.ofNanos(closedAt - closeStarted).compareTo(Duration.ofSeconds(DRAIN_SECONDS)) <= 0;
        shutdownLog = captured.toString(StandardCharsets.UTF_8);
        hostStream.close();
        guestStream.close();
    }

    @AfterAll
    void restoreConsole() {
        System.setOut(originalOut);
        if (context != null && context.isActive()) context.close();
    }

    /** Everything a stream delivers up to its end, as text; the end itself is what this waits for. */
    private static String drain(SseStream stream) throws InterruptedException {
        StringBuilder text = new StringBuilder();
        while (true) {
            try {
                SseStream.Event frame = stream.pollFrame(Duration.ofSeconds(20));
                if (frame == null) throw new AssertionError("the stream neither ended nor sent anything; got " + text);
                text.append(String.join("\n", frame.lines())).append("\n\n");
            } catch (AssertionError ended) {
                if (ended.getMessage() != null && ended.getMessage().startsWith("the stream ended")) {
                    return text.toString();
                }
                throw ended;
            }
        }
    }

    // (1)

    @Test
    void duringStartUpHealthIsLiveNotReadyWithStarting() {
        Reply health = Seams.OBSERVED.startupHealth;

        assertThat(health.status()).isEqualTo(503);
        assertThat(health.json().toString()).isEqualTo("{\"live\":true,\"ready\":false,\"reason\":\"STARTING\"}");
    }

    // (2)

    @Test
    void readinessFlipsToDrainingBeforeAnythingElseIsObservable() {
        Seams.Observations seen = Seams.OBSERVED;

        assertThat(seen.failure).isNull();
        assertThat(seen.healthAtFlip.status()).isEqualTo(503);
        assertThat(seen.healthAtFlip.json().get("ready").asBoolean()).isFalse();
        assertThat(seen.healthAtFlip.json().get("reason").asText()).isEqualTo("DRAINING");
        assertThat(seen.hostStreamOpenAtFlip)
                .as("host stream still open when DRAINING is first seen")
                .isTrue();
        assertThat(seen.guestStreamOpenAtFlip)
                .as("guest stream still open when DRAINING is first seen")
                .isTrue();
    }

    // (3)

    @Test
    void openStreamsEndWithNoClosedEvent() {
        assertThat(streamsEndedAfter)
                .as("streams ended while the in-flight action was still held, well inside the drain window")
                .isLessThan(Duration.ofSeconds(DRAIN_SECONDS - 1));
        assertThat(hostStreamEnded).isTrue();
        assertThat(guestStreamEnded).isTrue();
        assertThat(hostStreamText).doesNotContain("event: closed").doesNotContain("GAME_UNAVAILABLE");
    }

    // (4) Proves the application-level refusal while DRAINING, observed before the streams are closed. A browser
    // reconnects only after the connector has paused, so what it sees then is a refused connection; that is not
    // proved here.

    @Test
    void aReconnectionWhileDrainingIsRefusedAtTheFilterWith503() {
        assertServiceUnavailable(Seams.OBSERVED.reconnectAtFlip);
    }

    // (5)

    @Test
    void anActionAlreadyInFlightCompletesWithinTheDrainWindow() {
        assertThat(closeStillWaitingWhenReleased)
                .as("the shutdown waits for the action instead of cutting it")
                .isTrue();
        assertThat(inFlightFailure).as("the in-flight action was not cut").isNull();
        assertThat(inFlightReply.status()).isEqualTo(200);
        assertThat(inFlightReply.json().get("phase").asText()).isEqualTo("PLACEMENT");
        assertThat(inFlightTook).isLessThanOrEqualTo(Duration.ofSeconds(DRAIN_SECONDS));
        assertThat(closedWithinWindow).isTrue();
    }

    // (6)

    @Test
    void newWorkAfterDrainingBeginsIsRefusedButReadsAreAnswered() {
        assertServiceUnavailable(Seams.OBSERVED.createAtFlip);
        assertServiceUnavailable(Seams.OBSERVED.joinAtFlip);
        assertServiceUnavailable(Seams.OBSERVED.commandAtFlip);
        assertThat(Seams.OBSERVED.readAtFlip.status())
                .as("reads stay answerable while draining")
                .isEqualTo(200);
    }

    // (7)

    @Test
    void nothingClaimsThatAnyGameSurvivesTheRestart() {
        Seams.Observations seen = Seams.OBSERVED;
        List<String> surfaces = new ArrayList<>();
        surfaces.add(shutdownLog);
        for (Reply reply :
                List.of(seen.healthAtFlip, seen.createAtFlip, seen.joinAtFlip, seen.commandAtFlip, inFlightReply)) {
            surfaces.add(String.valueOf(reply.response().body()));
        }
        if (inFlightReply != null)
            surfaces.add(String.valueOf(inFlightReply.response().body()));
        surfaces.add(hostStreamText);
        // the start-up record is the one that speaks about state; the shutdown part of the log starts after it
        String afterStartUp = shutdownLog.substring(shutdownLog.lastIndexOf("started with no state"));
        surfaces.set(0, afterStartUp.substring(afterStartUp.indexOf('\n') + 1));

        assertThat(String.join("\n", surfaces).toLowerCase(Locale.ROOT))
                .doesNotContain("surviv")
                .doesNotContain("resum")
                .doesNotContain("restor")
                .doesNotContain("recover")
                .doesNotContain("persist")
                .doesNotContain("continu");
        assertThat(surfaces.getFirst()).contains("draining");
    }

    // (8)

    @Test
    void theDrainingHealthAnswerIsAHealthDocumentNotAProblem() {
        Reply health = Seams.OBSERVED.healthAtFlip;

        assertThat(health.status()).isEqualTo(503);
        assertThat(health.header("Content-Type")).startsWith("application/json");
        assertThat(health.header("Content-Type")).doesNotContain("problem");
        JsonNode body = health.json();
        assertThat(body.has("code")).isFalse();
        assertThat(body.has("title")).isFalse();
        assertThat(body.has("status")).isFalse();
        assertThat(body.toString()).isEqualTo("{\"live\":true,\"ready\":false,\"reason\":\"DRAINING\"}");
    }

    // the drain window is one setting

    @Test
    void theDrainWindowAndGracefulShutdownComeFromTheOneSetting() {
        // read from the closed context's environment: the window is derived, never set a second time
        assertThat(context.getEnvironment().getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(context.getEnvironment().getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                .isEqualTo(DRAIN_SECONDS + "s");
    }

    /** What a probe saw: status, {@code Retry-After}, the parsed problem body and its text. */
    record Answer(int status, String retryAfter, JsonNode json, String body) {
        static Answer of(Reply reply) {
            return new Answer(
                    reply.status(),
                    reply.header("Retry-After"),
                    reply.json(),
                    String.valueOf(reply.response().body()));
        }
    }

    private static void assertServiceUnavailable(Reply refused) {
        assertServiceUnavailable(Answer.of(refused));
    }

    private static void assertServiceUnavailable(Answer refused) {
        assertThat(Seams.OBSERVED.failure).isNull();
        assertThat(refused.status()).as(refused.body()).isEqualTo(503);
        assertThat(refused.json().get("code").asText()).isEqualTo("service-unavailable");
        int retryAfter = refused.json().get("retryAfterSeconds").asInt();
        assertThat(retryAfter).isGreaterThanOrEqualTo(1);
        assertThat(refused.retryAfter()).isEqualTo(String.valueOf(retryAfter));
    }

    /** Copies every byte to the console as well, so the run stays readable. */
    private static final class Tee extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        Tee(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
