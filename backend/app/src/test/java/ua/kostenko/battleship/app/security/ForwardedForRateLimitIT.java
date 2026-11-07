package ua.kostenko.battleship.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/**
 * Behind one trusted reverse proxy every peer address is the proxy's, so the address-keyed limits would share a single
 * bucket. With {@code battleship.trust-forwarded-for} the key is the last X-Forwarded-For entry, the one the proxy
 * appended; earlier entries are the client's own words and never choose a bucket.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "battleship.trust-forwarded-for=true",
            "battleship.rate-limit.create-game-per-minute=1",
            "server.shutdown=immediate"
        })
class ForwardedForRateLimitIT {
    private static final String BODY = "{\"rulesetId\":\"sea-battle-10-ship.v1\",\"displayName\":\"A\"}";

    @LocalServerPort
    private int port;

    private Reply create(String forwardedFor) throws Exception {
        return new SecurityHttp(port).postJson("/api/v1/games", null, BODY, "X-Forwarded-For", forwardedFor);
    }

    @Test
    void eachProxyAppendedAddressHasItsOwnBucket() throws Exception {
        assertThat(create("198.51.100.1").status()).isEqualTo(201);
        assertThat(create("198.51.100.2").status()).isEqualTo(201);
        assertThat(create("198.51.100.1").status()).isEqualTo(429);
    }

    @Test
    void aClientSuppliedEarlierEntryCannotEscapeTheBucket() throws Exception {
        assertThat(create("203.0.113.1, 198.51.100.9").status()).isEqualTo(201);
        assertThat(create("203.0.113.2, 198.51.100.9").status()).isEqualTo(429);
    }
}
