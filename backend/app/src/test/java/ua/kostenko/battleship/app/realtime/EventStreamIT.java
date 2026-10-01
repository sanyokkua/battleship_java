package ua.kostenko.battleship.app.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.kostenko.battleship.app.web.Browser.invitationSecret;
import static ua.kostenko.battleship.app.web.Browser.simple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
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
import ua.kostenko.battleship.app.web.SseStream.Event;
import ua.kostenko.battleship.application.MutableTimeSource;
import ua.kostenko.battleship.application.projection.SnapshotView;
import ua.kostenko.battleship.application.usecase.GetGameUseCase;
import ua.kostenko.battleship.domain.RandomSource;
import ua.kostenko.battleship.domain.SeededRandomSource;
import ua.kostenko.battleship.domain.model.Seat;

/**
 * {@code streamGameEvents} over a real server. The three parts are assertion groups of this one class: part 1 (T029)
 * is the first event, framing, byte identity with {@code getGame}, per-seat delivery and registration races. Time is
 * a {@link MutableTimeSource}, so nothing here waits on wall time except for events to arrive (R60). A stream its
 * client abandoned stays open until a write fails, and draining streams on shutdown is T036's (R59), so this context
 * stops without waiting for them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.shutdown=immediate")
@Import(EventStreamIT.Determinism.class)
class EventStreamIT {
    private static final Instant START = Instant.parse("2031-05-06T07:08:09.123Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEA_BATTLE = "sea-battle-10-ship.v1";

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
            return new SeededRandomSource(2029L);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableTimeSource time;

    @Autowired
    private ObjectMapper wireMapper;

    @Autowired
    private SseHub hub;

    @Autowired
    private GetGameUseCase getGame;

    private record Game(Browser host, Browser guest) {}

    private Browser hostOnly(String hostName) throws Exception {
        Browser host = new Browser(port);
        host.create(SEA_BATTLE, hostName);
        return host;
    }

    private Game game() throws Exception {
        Browser host = hostOnly("Captain");
        Browser guest = new Browser(port);
        guest.join(host.gameId(), invitationSecret(host.snapshot()), "Rival");
        return new Game(host, guest);
    }

    private static JsonNode json(Event event) throws Exception {
        return JSON.readTree(event.data());
    }

    private static long version(JsonNode snapshot) {
        return snapshot.get("version").asLong();
    }

    /** The first event must be the snapshot a {@code getGame} at the same instant answers, byte for byte. */
    private static Event assertFirstIsCurrent(SseStream stream, Browser browser) throws Exception {
        assertThat(stream.status()).isEqualTo(200);
        assertThat(stream.header("Content-Type")).startsWith("text/event-stream");
        Event first = stream.next();
        Reply read = browser.read();
        assertThat(read.status()).isEqualTo(200);
        assertThat(first.name()).isEqualTo("snapshot");
        assertThat(first.data()).isEqualTo(read.response().body());
        assertThat(first.id()).isEqualTo(String.valueOf(version(read.json())));
        return first;
    }

    // ------------------------------------------------------------------ part 1 (T029)

    @Test
    void theFirstEventIsTheCurrentSnapshotAtFivePointsOfAGame() throws Exception {
        Browser host = hostOnly("Captain");
        try (SseStream waiting = host.events()) {
            assertThat(json(assertFirstIsCurrent(waiting, host)).get("phase").asText())
                    .isEqualTo("WAITING");

            Browser guest = new Browser(port);
            guest.join(host.gameId(), invitationSecret(host.snapshot()), "Rival");
            assertThat(json(waiting.next()).get("phase").asText())
                    .as("the guest joining changes the host's view")
                    .isEqualTo("PLACEMENT");

            try (SseStream placement = guest.events()) {
                assertThat(json(assertFirstIsCurrent(placement, guest))
                                .get("phase")
                                .asText())
                        .isEqualTo("PLACEMENT");
            }

            host.accept(simple("PLACE_FLEET_RANDOMLY"));
            guest.accept(simple("PLACE_FLEET_RANDOMLY"));
            host.accept(simple("READY"));
            guest.accept(simple("READY"));
            try (SseStream playing = guest.events()) {
                assertThat(json(assertFirstIsCurrent(playing, guest))
                                .get("phase")
                                .asText())
                        .isEqualTo("PLAYING");
            }

            guest.accept(simple("RESIGN"));
            try (SseStream finished = host.events()) {
                JsonNode result = json(assertFirstIsCurrent(finished, host));
                assertThat(result.get("phase").asText()).isEqualTo("FINISHED");
                assertThat(result.has("statistics")).isTrue();
            }
        }

        Game abandoned = game();
        try (SseStream stayer = abandoned.host().events();
                SseStream leaver = abandoned.guest().events()) {
            assertFirstIsCurrent(stayer, abandoned.host());
            assertFirstIsCurrent(leaver, abandoned.guest());
            assertThat(abandoned.guest().leave().status()).isEqualTo(204);
            assertThat(json(stayer.next()).get("phase").asText()).isEqualTo("ABANDONED");
            assertThat(leaver.ended())
                    .as("the game stopped existing for the leaver")
                    .isTrue();
        }
        try (SseStream reopened = abandoned.host().events()) {
            assertThat(json(assertFirstIsCurrent(reopened, abandoned.host()))
                            .get("phase")
                            .asText())
                    .isEqualTo("ABANDONED");
        }
    }

    @Test
    void theFramingIsTheContractsExample() throws Exception {
        Browser host = hostOnly("Captain");
        try (SseStream stream = host.events()) {
            Event first = stream.next();
            long version = version(host.snapshot());
            assertThat(first.lines()).hasSize(3);
            assertThat(first.lines().get(0)).isEqualTo("event: snapshot");
            assertThat(first.lines().get(1)).isEqualTo("id: " + version);
            assertThat(first.lines().get(2)).startsWith("data: {").endsWith("}");
            assertThat(json(first).get("gameId").asText()).isEqualTo(host.gameId());
            assertThat(version(json(first))).isEqualTo(version);
        }
    }

    @Test
    void theStreamedDocumentIsByteIdenticalToGetGameAndOnlyTheClockMayDiffer() throws Exception {
        Browser host = hostOnly("Капітан ⚓");
        try (SseStream stream = host.events()) {
            Event first = stream.next();
            Reply sameInstant = host.read();
            assertThat(first.data().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(sameInstant.response().body().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            time.advance(Duration.ofMillis(1));
            Reply later = host.read();
            assertThat(later.response().body()).isNotEqualTo(first.data());
            assertThat(version(later.json())).isEqualTo(version(json(first)));
        }
    }

    @Test
    void aPlayerReceivesOnlyChangesToTheirOwnViewSoVersionsSkip() throws Exception {
        Game game = game();
        try (SseStream host = game.host().events();
                SseStream guest = game.guest().events()) {
            long start = version(json(assertFirstIsCurrent(host, game.host())));
            assertFirstIsCurrent(guest, game.guest());

            game.host().accept(simple("PLACE_FLEET_RANDOMLY"));
            game.host().accept(simple("READY"));

            assertThat(host.next().id()).isEqualTo(String.valueOf(start + 1));
            assertThat(host.next().id()).isEqualTo(String.valueOf(start + 2));
            Event ready = guest.next();
            assertThat(ready.id())
                    .as("arranging the host's fleet is invisible to the guest, so its version is skipped")
                    .isEqualTo(String.valueOf(start + 2));
            assertThat(json(ready).at("/opponent/ready").asBoolean()).isTrue();
        }
    }

    @Test
    void aLastEventIdHeaderChangesNothing() throws Exception {
        Game game = game();
        game.host().accept(simple("PLACE_FLEET_RANDOMLY"));
        try (SseStream stream = game.host().events("Last-Event-ID", "1")) {
            Event first = assertFirstIsCurrent(stream, game.host());
            assertThat(Long.parseLong(first.id())).isGreaterThan(1);
        }
    }

    @Test
    void openingAStreamNeitherMovesTheIdleDeadlineNorKeepsTheGameAlive() throws Exception {
        Browser host = hostOnly("Captain");
        String expiresAt = host.snapshot().get("expiresAt").asText();
        time.advance(Duration.ofSeconds(10));
        try (SseStream stream = host.events()) {
            assertThat(json(stream.next()).get("expiresAt").asText()).isEqualTo(expiresAt);
            assertThat(host.snapshot().get("expiresAt").asText()).isEqualTo(expiresAt);

            time.set(Instant.parse(expiresAt));
            Reply expired = host.read();
            assertThat(expired.status()).isEqualTo(410);
            assertThat(expired.json().get("code").asText()).isEqualTo("game-expired");
        } finally {
            time.set(START);
        }
    }

    @Test
    void theStreamAndGetGameSerializeThroughTheOneInjectedMapper() throws Exception {
        Browser host = hostOnly("Captain");
        try (SseStream before = host.events()) {
            assertThat(json(before.next()).get("serverTime").isTextual()).isTrue();
        }
        wireMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        try (SseStream after = host.events()) {
            Event first = after.next();
            Reply read = host.read();
            assertThat(json(first).get("serverTime").isNumber()).isTrue();
            assertThat(read.json().get("serverTime").isNumber()).isTrue();
            assertThat(first.data()).isEqualTo(read.response().body());
        } finally {
            wireMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        }
    }

    @Test
    void aCommandRacingBeforeTheLockedCaptureIsInTheFirstSnapshot() throws Exception {
        Game game = game();
        long before = version(game.host().snapshot());
        hub.afterPendingInstalled = once(() -> game.host().accept(simple("PLACE_FLEET_RANDOMLY")));
        try (SseStream stream = game.host().events()) {
            Event first = stream.next();
            assertThat(first.id()).isEqualTo(String.valueOf(before + 1));
            assertThat(json(first).at("/yourBoard/ships/0/anchor").isMissingNode())
                    .as("the racing arrangement is in the captured snapshot")
                    .isFalse();

            game.host().accept(simple("CLEAR_FLEET"));
            assertThat(stream.next().id())
                    .as("the racing transition is not delivered a second time")
                    .isEqualTo(String.valueOf(before + 2));
        }
    }

    @Test
    void commandsRacingAfterTheCaptureArriveAfterTheFirstSnapshotAndIdsOnlyIncrease() throws Exception {
        Game game = game();
        long captured = version(game.host().snapshot());
        hub.afterCapture = once(() -> {
            game.host().accept(simple("PLACE_FLEET_RANDOMLY"));
            game.host().accept(simple("PLACE_FLEET_RANDOMLY"));
        });
        try (SseStream stream = game.host().events()) {
            assertThat(stream.next().id()).isEqualTo(String.valueOf(captured));
            Event raced = stream.next();
            assertThat(raced.id())
                    .as("the skipped intermediate state is represented by the later full snapshot")
                    .isEqualTo(String.valueOf(captured + 2));
            assertThat(raced.data()).isEqualTo(game.host().read().response().body());

            // Two completed transitions reach the hub in reverse order: the older view arrives after the newer one.
            SnapshotView older =
                    getGame.execute(game.host().gameId(), game.host().sessionValue());
            game.host().accept(simple("CLEAR_FLEET"));
            hub.publish(game.host().gameId(), Seat.HOST, older);
            game.host().accept(simple("PLACE_FLEET_RANDOMLY"));
            assertThat(List.of(stream.next().id(), stream.next().id()))
                    .containsExactly(String.valueOf(captured + 3), String.valueOf(captured + 4));
        }
    }

    @AfterEach
    void removeRaceSeams() {
        hub.afterPendingInstalled = () -> {};
        hub.afterCapture = () -> {};
    }

    private interface Step {
        void run() throws Exception;
    }

    /** Runs the racing step at the first stream opening only. */
    private static Runnable once(Step step) {
        AtomicBoolean done = new AtomicBoolean();
        return () -> {
            if (done.compareAndSet(false, true)) {
                try {
                    step.run();
                } catch (Exception failed) {
                    throw new IllegalStateException(failed);
                }
            }
        };
    }
}
